package com.ghostdisplay.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import android.os.Handler
import android.os.Looper
import com.ghostdisplay.app.GhostApp
import com.ghostdisplay.app.audio.AudioRouter
import com.ghostdisplay.app.browser.BrowserRuntime
import com.ghostdisplay.app.browser.MemoryBudgetMonitor
import com.ghostdisplay.app.browser.PersistentPresentation
import com.ghostdisplay.app.browser.PersistentWebViewHost
import com.ghostdisplay.app.browser.SessionRestorer
import com.ghostdisplay.app.browser.TabManager
import com.ghostdisplay.app.control.AccessibilityController
import com.ghostdisplay.app.data.AppProfile
import com.ghostdisplay.app.data.CapabilityReport
import com.ghostdisplay.app.data.Compatibility
import com.ghostdisplay.app.data.GhostSession
import com.ghostdisplay.app.data.SessionStatus
import com.ghostdisplay.app.display.DisplayConfig
import com.ghostdisplay.app.display.DisplayConfigResolver
import com.ghostdisplay.app.display.PhysicalMetrics
import com.ghostdisplay.app.display.VirtualDisplayEngine
import com.ghostdisplay.app.launch.AppCatalog
import com.ghostdisplay.app.launch.AppLauncher
import com.ghostdisplay.app.launch.CompatibilityDetector
import com.ghostdisplay.app.indicator.OverlayDotService
import com.ghostdisplay.app.indicator.ReturnNotifications
import com.ghostdisplay.app.profile.AppCategoryClassifier
import com.ghostdisplay.app.profile.CompatibilityProbe
import com.ghostdisplay.app.profile.PerAppProfileManager
import com.ghostdisplay.app.safety.Blocklist
import com.ghostdisplay.app.session.SessionManager
import com.ghostdisplay.app.session.SessionPolicy
import com.ghostdisplay.app.session.SessionTier
import com.ghostdisplay.app.safety.GhostNotificationListenerService
import com.ghostdisplay.app.util.VirtualDisplayFlags
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Foreground service that owns the virtual display, wake lock, audio router
 * and MediaProjection. Stopping a session here fully releases all of them.
 */
class GhostService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.Main.immediate)

    private lateinit var engine: VirtualDisplayEngine
    private lateinit var launcher: AppLauncher
    private lateinit var catalog: AppCatalog
    private val audio = AudioRouter()
    private val a11y = AccessibilityController()
    private val gson = Gson()

    private var wakeLock: PowerManager.WakeLock? = null
    private var audioBoundSession: String? = null
    private var browserDisplaySessionId: String? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        instance = this
        engine = VirtualDisplayEngine(getSystemService(DisplayManager::class.java))
        launcher = AppLauncher(this)
        catalog = AppCatalog(packageManager)
        GhostNotifications.ensureChannels(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground(
            intent?.getStringExtra(EXTRA_LABEL) ?: "GhostDisplay",
            intent?.getStringExtra(EXTRA_SESSION_ID),
        )
        when (intent?.action) {
            ACTION_START -> scope.launch { handleStart(intent) }
            ACTION_RESURRECT -> scope.launch { handleResurrect() }
            ACTION_STOP_SESSION_DIRECT, GhostStopReceiver.ACTION_STOP_SESSION -> {
                val id = intent.getStringExtra(EXTRA_SESSION_ID)
                scope.launch { if (id != null) stopSession(id) else stopAll() }
            }
            ACTION_START_BROWSER, ACTION_RESTORE_BROWSER -> scope.launch {
                handleStartBrowser(if (intent.action == ACTION_RESTORE_BROWSER) "process-restore" else "user-start")
            }
            ACTION_STOP_BROWSER -> scope.launch { stopBrowserEngine() }
            GhostStopReceiver.ACTION_STOP_ALL, ACTION_STOP_ALL -> scope.launch { stopAll() }
            ACTION_MUTE -> {
                val id = intent.getStringExtra(EXTRA_SESSION_ID)
                val muted = intent.getBooleanExtra(EXTRA_MUTED, false)
                if (id != null) setMuted(id, muted)
            }
            else -> {
                // Sticky restart after process death: stay in foreground with a
                // generic notification; actual VD cannot be rebuilt without MP consent.
                startAsForeground("GhostDisplay", null)
                scope.launch {
                    handleResurrect()
                    offerRestoreIfNeeded()
                }
            }
        }
        return START_STICKY
    }

    private suspend fun handleStart(intent: Intent) {
        val pkg = intent.getStringExtra(EXTRA_PACKAGE) ?: return
        val activity = intent.getStringExtra(EXTRA_ACTIVITY) ?: return
        val label = intent.getStringExtra(EXTRA_LABEL) ?: pkg
        val additional = intent.getBooleanExtra(EXTRA_ADDITIONAL_WINDOW, false)
        val requestedTier = intent.getStringExtra(EXTRA_TIER)
        val financeConfirmed = intent.getBooleanExtra(EXTRA_FINANCE_CONFIRMED, false)

        var reuseId = intent.getStringExtra(EXTRA_SESSION_ID)
        var effectiveTier = requestedTier
        if (!additional) {
            val runningSameId = reuseId?.let { SessionRepository.get(it) }
                ?.takeIf { it.status == SessionStatus.RUNNING }
            if (runningSameId != null) {
                refreshIndicators()
                return
            }
            if (reuseId == null) {
                val runningSamePkg = SessionRepository.all().firstOrNull {
                    it.packageName == pkg && it.status == SessionStatus.RUNNING
                }
                if (runningSamePkg != null) {
                    refreshIndicators()
                    return
                }
                val persisted = app().container.sessionStore.active()
                    .firstOrNull { it.packageName == pkg }
                if (persisted != null) {
                    reuseId = persisted.id
                    if (effectiveTier == null) {
                        effectiveTier = SessionPolicy.tierAfterGhostProcessDeath().name
                    }
                }
            }
        }

        val launchable = catalog.find(pkg, packageName)
        val category = AppCategoryClassifier.classify(
            pkg, label, launchable?.requestedOrientation ?: 0, isSystemApp(pkg),
        )
        val financeOn = app().container.prefs.financeWarn.first()
        if (category == com.ghostdisplay.app.profile.AppCategory.FINANCE && financeOn && !financeConfirmed) {
            publishError(
                pkg, activity, label,
                "Finance warning: a hidden display is not isolation. Confirm in GhostDisplay to start a hidden window of this real app.",
            )
            if (SessionRepository.all().isEmpty()) stopSelfSafely()
            return
        }

        val blocked = isBlocked(pkg, label)
        if (blocked != null) {
            publishError(pkg, activity, label, blocked)
            if (SessionRepository.all().isEmpty()) stopSelfSafely()
            return
        }

        startAsForeground(label, null)
        acquireWakeLock()

        val physical = physicalMetrics()
        val generated = launchable?.let { PerAppProfileManager.defaultProfile(it, physical, isSystemApp(pkg)) }
        val profile = PerAppProfileManager.mergeOverride(
            app().container.db.profiles().get(pkg),
            generated ?: AppProfile(pkg),
        )
        val requested = catalog.requestedOrientation(pkg, activity)
        val config = DisplayConfigResolver.resolve(
            physical = physical,
            requestedOrientation = requested,
            overrideWidth = profile.widthPx,
            overrideHeight = profile.heightPx,
            overrideDpi = profile.densityDpi,
            orientation = profile.orientationEnum(),
            surfaceMode = profile.surfaceModeEnum(),
            secure = profile.secure,
            audioEnabled = profile.audioEnabled,
            name = "Ghost-$label",
        )

        val mp = MediaProjectionHolder.get(this)
        val created = engine.create(config, mp, sessionId = reuseId ?: UUID.randomUUID().toString())
        if (created is VirtualDisplayEngine.CreateResult.Failed) {
            val report = CapabilityReport.failed(
                created.message + " Attempts: " + created.attempts.joinToString(" | "),
            )
            persistCompat(pkg, report)
            publishSession(
                GhostSession(
                    id = UUID.randomUUID().toString(),
                    packageName = pkg,
                    activityName = activity,
                    appLabel = label,
                    displayId = -1,
                    taskId = null,
                    status = SessionStatus.UNSUPPORTED,
                    startedAt = System.currentTimeMillis(),
                    config = config,
                    capability = report,
                ),
            )
            if (engine.all().isEmpty()) stopAll()
            return
        }
        val handle = (created as VirtualDisplayEngine.CreateResult.Created).handle
        val sessionId = handle.sessionId

        val launchResult = launcher.launch(pkg, activity, handle.displayId)

        // Give windows a moment to appear so Accessibility can verify placement.
        delay(700)
        val onVd = a11y.packageOnDisplay(handle.displayId, pkg)
        val onDefault = a11y.packageOnDefaultDisplay(pkg)
        val probe = a11y.probe(handle.displayId, pkg)

        var audioOk = false
        var audioNotice: String? = null
        if (config.audioEnabled) {
            val audioResult = audio.start(mp)
            audioOk = audioResult.started
            audioNotice = audioResult.notice
            if (audioOk) audioBoundSession = sessionId
        }

        val flagsRequested = VirtualDisplayFlags.attempts(config.secure).first().name
        val report = CompatibilityDetector.classify(
            CompatibilityDetector.Input(
                app = catalog.find(pkg, packageName),
                virtualDisplayCreated = true,
                flagsUsed = handle.flagsLabel,
                flagsRequested = flagsRequested,
                launch = launchResult,
                verifiedOnVirtualDisplay = when {
                    !a11y.isBound() -> null
                    onVd -> true
                    else -> false
                },
                appearedOnDefaultDisplay = if (a11y.isBound()) onDefault && !onVd else null,
                nodeControlAvailable = probe.nodeControlAvailable,
                gestureControlAvailable = probe.gestureControlAvailable,
                audioCaptureAvailable = audioOk,
                audioRequested = config.audioEnabled,
                secureRequested = config.secure,
                knownLimit = CompatibilityProbe.caveatFor(category),
                extraNotices = listOfNotNull(audioNotice) + probe.notices +
                    (if (launchResult is AppLauncher.LaunchResult.Failed) launchResult.errors else emptyList()),
            ),
        )
        val after = CompatibilityProbe.afterLaunch(
            category = category,
            launchedOnVirtualDisplay = report.launchedOnVirtualDisplay,
            verifiedOnVirtualDisplay = report.verifiedOnVirtualDisplay,
            appearedOnDefaultDisplay = if (a11y.isBound()) onDefault && !onVd else null,
            drmStillRefused = category == com.ghostdisplay.app.profile.AppCategory.STREAMING_DRM &&
                report.launchedOnVirtualDisplay && report.verifiedOnVirtualDisplay == false,
        )
        val mergedReport = report.copy(
            compatibility = after.compatibility,
            notices = (report.notices + listOfNotNull(after.caveat)).distinct(),
        )

        persistCompat(pkg, mergedReport)
        val status = when (mergedReport.compatibility) {
            Compatibility.UNSUPPORTED -> SessionStatus.UNSUPPORTED
            else -> SessionStatus.RUNNING
        }
        val tier = effectiveTier ?: if (status == SessionStatus.RUNNING) {
            SessionPolicy.tierAfterNormalLaunch().name
        } else SessionTier.FRESH.name
        val outcome = if (effectiveTier == SessionTier.RESURRECTED.name) {
            SessionPolicy.classifyRestoreOutcome(
                accessibilitySawContent = onVd,
                looksLikeColdStart = onVd && probe.nodesOnVirtualDisplay < 3,
            ).name
        } else null
        val session = GhostSession(
            id = sessionId,
            packageName = pkg,
            activityName = activity,
            appLabel = label,
            displayId = handle.displayId,
            taskId = (launchResult as? AppLauncher.LaunchResult.Launched)?.taskId,
            status = status,
            startedAt = System.currentTimeMillis(),
            config = config,
            capability = mergedReport,
            tier = tier,
            restoreOutcome = outcome,
            categoryCaveat = after.caveat,
        )
        publishSession(session)
        persistSession(session, active = status == SessionStatus.RUNNING)
        updateNotificationFilter()
        refreshIndicators()
        startAsForeground(label, sessionId)

        if (onDefault && !onVd && a11y.isBound()) {
            // Do not leave the user staring at an app that leaked onto the phone
            // screen without saying so — already in notices. We keep the VD in
            // case windows appear late; user can Stop.
            Log.w(TAG, "App $pkg visible on default display; hidden launch unverified")
        }
    }

    private suspend fun handleStartBrowser(reason: String) {
        startAsForeground("Persistent Browser", browserDisplaySessionId)
        acquireWakeLock()
        PersistentWebViewHost.prepareProcess(this)

        if (BrowserRuntime.running && BrowserRuntime.tabManager != null && BrowserRuntime.host != null) {
            refreshBrowserNotification()
            return
        }

        val mem = MemoryBudgetMonitor(this)
        val host = PersistentWebViewHost(
            appContext = applicationContext,
            onTabInfo = { rec ->
                mainHandler.post {
                    BrowserRuntime.tabManager?.updateFromHost(rec)
                    BrowserRuntime.pulse()
                }
            },
            onNewWindowUrl = { url ->
                scope.launch { BrowserRuntime.tabManager?.newTab(url) }
            },
        )
        val tm = TabManager(
            store = app().container.tabs,
            host = host,
            ramGbProvider = { mem.ramGb() },
        )
        tm.attachExisting()

        val physical = physicalMetrics()
        val config = DisplayConfigResolver.resolve(
            physical = physical,
            requestedOrientation = 1,
            surfaceMode = if (MediaProjectionHolder.hasConsent()) {
                DisplayConfig.SurfaceMode.MEDIA_PROJECTION
            } else {
                DisplayConfig.SurfaceMode.IMAGE_READER
            },
            audioEnabled = true,
            name = "Ghost-PersistentBrowser",
        )
        val mp = MediaProjectionHolder.get(this)
        val created = engine.create(config, mp)
        if (created is VirtualDisplayEngine.CreateResult.Failed) {
            BrowserRuntime.presentationNotice = created.message
            // Still host WebViews in-process so tabs are not lost.
        } else {
            val handle = (created as VirtualDisplayEngine.CreateResult.Created).handle
            browserDisplaySessionId = handle.sessionId
            BrowserRuntime.displaySessionId = handle.sessionId
            tm.setDisplaySessionId(handle.sessionId)
            val (pres, notice) = PersistentPresentation.tryShow(this, handle.virtualDisplay.display)
            BrowserRuntime.presentationNotice = notice
            if (pres != null) {
                host.attach(pres, pres.webContainer)
                pres.setStatus("Persistent Browser · hidden display ${handle.displayId}")
            }
            if (config.audioEnabled) {
                val audioResult = audio.start(mp)
                if (audioResult.started) audioBoundSession = handle.sessionId
            }
        }

        BrowserRuntime.host = host
        BrowserRuntime.tabManager = tm
        BrowserRuntime.memory = mem
        BrowserRuntime.restorer = SessionRestorer(tm) { mem.read(tm.tabs.value) }
        val report = BrowserRuntime.restorer!!.restore(reason)
        BrowserRuntime.publishRestore(report)
        if (tm.tabs.value.isEmpty()) {
            tm.newTab("https://www.google.com")
        }
        BrowserRuntime.running = true
        BrowserRuntime.pulse()
        refreshBrowserNotification()
    }

    private suspend fun stopBrowserEngine() {
        runCatching { BrowserRuntime.tabManager?.snapshotAll() }
        BrowserRuntime.host?.releaseAllViews()
        browserDisplaySessionId?.let { engine.release(it) }
        if (audioBoundSession == browserDisplaySessionId) {
            audio.stop()
            audioBoundSession = null
        }
        browserDisplaySessionId = null
        BrowserRuntime.reset()
        if (engine.all().isEmpty() && SessionRepository.all().isEmpty()) {
            releaseWakeLock()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else {
            val next = SessionRepository.all().firstOrNull()
            startAsForeground(next?.appLabel ?: "GhostDisplay", next?.id)
        }
    }

    private fun refreshBrowserNotification() {
        val n = BrowserRuntime.tabManager?.tabs?.value?.size ?: 0
        startAsForeground("Persistent Browser · $n tabs", browserDisplaySessionId)
    }

    private fun setMuted(sessionId: String, muted: Boolean) {
        audio.setMuted(muted)
        SessionRepository.get(sessionId)?.let {
            SessionRepository.upsert(it.copy(muted = muted))
        }
    }

    private suspend fun stopSession(sessionId: String) {
        engine.release(sessionId)
        if (audioBoundSession == sessionId) {
            audio.stop()
            audioBoundSession = null
        }
        SessionRepository.remove(sessionId)
        app().container.sessionManager.deactivate(sessionId)
        updateNotificationFilter()
        refreshIndicators()
        val remaining = engine.all()
        if (remaining.isEmpty()) {
            stopAll()
        } else {
            val next = SessionRepository.all().firstOrNull()
            startAsForeground(next?.appLabel ?: "GhostDisplay", next?.id)
        }
    }

    private suspend fun stopAll() {
        runCatching { BrowserRuntime.tabManager?.snapshotAll() }
        BrowserRuntime.host?.releaseAllViews()
        BrowserRuntime.reset()
        browserDisplaySessionId = null
        engine.releaseAll()
        audio.stop()
        audioBoundSession = null
        MediaProjectionHolder.clear()
        SessionRepository.clear()
        app().container.db.sessions().deactivateAll()
        GhostNotificationListenerService.filteredPackages = emptySet()
        ReturnNotifications.clear(this)
        OverlayDotService.sync(this, false)
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun offerRestoreIfNeeded() {
        val want = app().container.prefs.restoreOnProcessDeath.first()
        if (!want) return
        val active = app().container.db.sessions().active()
        if (active.isEmpty()) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(
            GhostNotifications.ID_RESTORE,
            GhostNotifications.restoreNotification(this, active.map { it.appLabel }),
        )
    }

    private fun startAsForeground(label: String, sessionId: String?) {
        val notification = GhostNotifications.sessionNotification(this, label, sessionId)
        val hasMp = MediaProjectionHolder.hasConsent()
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                if (hasMp) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                startForeground(GhostNotifications.ID_FOREGROUND, notification, type)
            } else if (Build.VERSION.SDK_INT >= 29) {
                if (hasMp) {
                    startForeground(
                        GhostNotifications.ID_FOREGROUND,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
                    )
                } else {
                    startForeground(GhostNotifications.ID_FOREGROUND, notification)
                }
            } else {
                startForeground(GhostNotifications.ID_FOREGROUND, notification)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "startForeground failed, retrying without type", t)
            try {
                startForeground(GhostNotifications.ID_FOREGROUND, notification)
            } catch (t2: Throwable) {
                Log.e(TAG, "startForeground retry failed", t2)
            }
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ghostdisplay:engine").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseWakeLock() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
    }

    private fun physicalMetrics(): PhysicalMetrics {
        val dm = resources.displayMetrics
        return PhysicalMetrics(dm.widthPixels, dm.heightPixels, dm.densityDpi)
    }

    private suspend fun isBlocked(pkg: String, label: String): String? {
        val db = app().container.db
        val row = db.blocklist().get(pkg) ?: return null
        if (!row.isDefault) return row.reason.ifBlank { Blocklist.reason(pkg, label) }
        return null
    }

    private suspend fun persistCompat(pkg: String, report: CapabilityReport) {
        val db = app().container.db
        val existing = db.profiles().get(pkg) ?: AppProfile(pkg)
        db.profiles().upsert(
            existing.copy(
                compatibility = report.compatibility.name,
                lastError = report.notices.firstOrNull(),
                lastNoticesJson = gson.toJson(report.notices),
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    private suspend fun persistSession(session: GhostSession, active: Boolean) {
        app().container.sessionManager.persist(session, active)
    }

    private suspend fun handleResurrect() {
        val rows = app().container.sessionStore.active()
        if (rows.isEmpty()) return
        startAsForeground("GhostDisplay", null)
        acquireWakeLock()
        for (row in rows) {
            if (SessionRepository.get(row.id)?.status == SessionStatus.RUNNING) continue
            val activity = row.activityName.ifBlank {
                catalog.find(row.packageName, packageName)?.activityName
            } ?: continue
            app().container.sessionManager.log(
                row.id, row.packageName, SessionManager.EVENT_RESURRECTED, SessionTier.RESURRECTED.name,
            )
            val intent = Intent(this, GhostService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PACKAGE, row.packageName)
                putExtra(EXTRA_ACTIVITY, activity)
                putExtra(EXTRA_LABEL, row.appLabel)
                putExtra(EXTRA_SESSION_ID, row.id)
                putExtra(EXTRA_TIER, SessionPolicy.tierAfterGhostProcessDeath().name)
            }
            handleStart(intent)
        }
        refreshIndicators()
    }

    private suspend fun refreshIndicators() {
        val live = SessionRepository.all().filter { it.status == SessionStatus.RUNNING }
        ReturnNotifications.publish(this, live)
        val overlayOn = app().container.prefs.overlayDot.first()
        OverlayDotService.overlayHiddenUntil = app().container.prefs.overlayHiddenUntil.first()
        OverlayDotService.sync(this, overlayOn && live.isNotEmpty())
    }

    fun onViewerOpened(sessionId: String) {
        app().container.sessionManager.markViewed(sessionId)
        scope.launch {
            val session = SessionRepository.get(sessionId)
            if (session != null) {
                app().container.sessionManager.persist(
                    session,
                    active = true,
                    logEvent = SessionManager.EVENT_VIEWED,
                )
            }
        }
    }

    private fun isSystemApp(pkg: String): Boolean {
        return runCatching {
            val info = packageManager.getApplicationInfo(pkg, 0)
            (info.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
        }.getOrDefault(false)
    }

    private fun publishSession(session: GhostSession) {
        SessionRepository.upsert(session)
    }

    private fun publishError(pkg: String, activity: String, label: String, reason: String) {
        val report = CapabilityReport.failed(reason)
        SessionRepository.upsert(
            GhostSession(
                id = UUID.randomUUID().toString(),
                packageName = pkg,
                activityName = activity,
                appLabel = label,
                displayId = -1,
                taskId = null,
                status = SessionStatus.UNSUPPORTED,
                startedAt = System.currentTimeMillis(),
                config = DisplayConfig(
                    widthPx = 1, heightPx = 1, densityDpi = 160,
                    orientation = DisplayConfig.Orientation.AUTO,
                    surfaceMode = DisplayConfig.SurfaceMode.IMAGE_READER,
                    secure = false, audioEnabled = false,
                ),
                capability = report,
            ),
        )
        scope.launch { persistCompat(pkg, report) }
    }

    private fun updateNotificationFilter() {
        scope.launch {
            val db = app().container.db
            val pkgs = SessionRepository.all().mapNotNull { s ->
                val p = db.profiles().get(s.packageName)
                if (p?.blockNotifications == true) s.packageName else null
            }.toSet()
            GhostNotificationListenerService.filteredPackages = pkgs
        }
    }

    private fun app(): GhostApp = application as GhostApp

    private fun stopSelfSafely() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        runCatching { BrowserRuntime.host?.releaseAllViews() }
        BrowserRuntime.reset()
        runCatching { engine.releaseAll() }
        runCatching { audio.stop() }
        releaseWakeLock()
        MediaProjectionHolder.clear()
        SessionRepository.clear()
        instance = null
        scope.cancel()
        super.onDestroy()
    }

    fun engine(): VirtualDisplayEngine = engine

    companion object {
        private const val TAG = "GhostService"
        const val ACTION_START = "com.ghostdisplay.app.action.START"
        const val ACTION_START_BROWSER = "com.ghostdisplay.app.action.START_BROWSER"
        const val ACTION_STOP_BROWSER = "com.ghostdisplay.app.action.STOP_BROWSER"
        const val ACTION_RESTORE_BROWSER = "com.ghostdisplay.app.action.RESTORE_BROWSER"
        const val ACTION_STOP_ALL = "com.ghostdisplay.app.action.STOP_ALL"
        const val ACTION_MUTE = "com.ghostdisplay.app.action.MUTE"
        const val ACTION_RESURRECT = "com.ghostdisplay.app.action.RESURRECT"
        const val ACTION_STOP_SESSION_DIRECT = "com.ghostdisplay.app.action.STOP_SESSION_DIRECT"
        const val EXTRA_PACKAGE = "package"
        const val EXTRA_ACTIVITY = "activity"
        const val EXTRA_LABEL = "label"
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_MUTED = "muted"
        const val EXTRA_ADDITIONAL_WINDOW = "additional_window"
        const val EXTRA_TIER = "tier"
        const val EXTRA_FINANCE_CONFIRMED = "finance_confirmed"

        @Volatile
        var instance: GhostService? = null
            private set

        fun startHidden(
            context: Context,
            packageName: String,
            activityName: String,
            label: String,
            projectionResultCode: Int? = null,
            projectionData: Intent? = null,
            additionalWindow: Boolean = false,
            financeConfirmed: Boolean = false,
            tier: String? = null,
        ) {
            if (projectionResultCode != null && projectionData != null) {
                MediaProjectionHolder.store(projectionResultCode, projectionData)
            }
            val intent = Intent(context, GhostService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PACKAGE, packageName)
                putExtra(EXTRA_ACTIVITY, activityName)
                putExtra(EXTRA_LABEL, label)
                putExtra(EXTRA_ADDITIONAL_WINDOW, additionalWindow)
                putExtra(EXTRA_FINANCE_CONFIRMED, financeConfirmed)
                if (tier != null) putExtra(EXTRA_TIER, tier)
            }
            startForegroundOrService(context, intent)
        }

        fun stopAll(context: Context) {
            val intent = Intent(context, GhostService::class.java).setAction(ACTION_STOP_ALL)
            startForegroundOrService(context, intent)
        }

        fun startBrowser(
            context: Context,
            projectionResultCode: Int? = null,
            projectionData: Intent? = null,
        ) {
            if (projectionResultCode != null && projectionData != null) {
                MediaProjectionHolder.store(projectionResultCode, projectionData)
            }
            val intent = Intent(context, GhostService::class.java).setAction(ACTION_START_BROWSER)
            startForegroundOrService(context, intent)
        }

        fun stopBrowser(context: Context) {
            val intent = Intent(context, GhostService::class.java).setAction(ACTION_STOP_BROWSER)
            startForegroundOrService(context, intent)
        }

        private fun startForegroundOrService(context: Context, intent: Intent) {
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (t: Throwable) {
                Log.e(TAG, "startForegroundService failed, falling back to startService", t)
                try {
                    context.startService(intent)
                } catch (t2: Throwable) {
                    Log.e(TAG, "startService failed", t2)
                }
            }
        }
    }
}
