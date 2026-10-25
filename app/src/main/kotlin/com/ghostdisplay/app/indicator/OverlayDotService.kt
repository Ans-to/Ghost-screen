package com.ghostdisplay.app.indicator

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import com.ghostdisplay.app.session.AppViewerActivity
import com.ghostdisplay.app.service.SessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Opt-in 8dp draggable dot. Shown only while the real corresponding app is
 * in the foreground on the main display. Never over home, Viewer, or others.
 */
class OverlayDotService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.Main.immediate)
    private var poll: Job? = null
    private var wm: WindowManager? = null
    private var dot: View? = null
    private var params: WindowManager.LayoutParams? = null
    private lateinit var detector: ForegroundAppDetector

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        detector = ForegroundAppDetector(this)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        scope.launch {
            val prefs = (application as com.ghostdisplay.app.GhostApp).container.prefs
            overlayOptIn = prefs.overlayDot.first()
            overlayHiddenUntil = prefs.overlayHiddenUntil.first()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        ensureDot()
        startPoll()
        return START_STICKY
    }

    override fun onDestroy() {
        poll?.cancel()
        removeDot()
        job.cancel()
        instance = null
        super.onDestroy()
    }

    private fun startPoll() {
        if (poll?.isActive == true) return
        poll = scope.launch {
            while (isActive) {
                refresh()
                delay(700)
            }
        }
    }

    private fun refresh() {
        val hiddenUntil = overlayHiddenUntil
        val now = System.currentTimeMillis()
        val fg = detector.foregroundPackage()
        val match = SessionRepository.all().firstOrNull { it.packageName == fg && it.status.name == "RUNNING" }
        val show = OverlayPolicy.shouldShow(
            OverlayPolicy.Input(
                overlayOptIn = overlayOptIn,
                overlayGranted = Settings.canDrawOverlays(this),
                hiddenUntilElapsed = now >= hiddenUntil,
                viewerOpen = AppViewerActivity.isOpen,
                foregroundPackage = fg,
                hiddenSessionPackage = match?.packageName,
                isHomeOrLauncher = detector.isHomeOrLauncher(fg),
                isGhostDisplay = detector.isGhostDisplay(fg),
            ),
        )
        if (show && match != null) {
            boundSessionId = match.id
            showDot()
        } else {
            hideDot()
        }
    }

    private fun ensureDot() {
        if (dot != null) return
        val size = (8 * resources.displayMetrics.density).toInt().coerceAtLeast(8)
        val view = FrameLayout(this).apply {
            setBackgroundColor(0xFF7AE7C7.toInt())
            elevation = 2f
            setOnTouchListener(dragListener)
        }
        val lp = WindowManager.LayoutParams(
            size,
            size,
            if (Build.VERSION.SDK_INT >= 26)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = resources.displayMetrics.widthPixels - size * 4
            y = resources.displayMetrics.heightPixels / 3
        }
        params = lp
        dot = view
        try {
            wm?.addView(view, lp)
            view.visibility = View.GONE
        } catch (_: Throwable) {
            dot = null
        }
    }

    private fun showDot() {
        ensureDot()
        dot?.visibility = View.VISIBLE
    }

    private fun hideDot() {
        dot?.visibility = View.GONE
    }

    private fun removeDot() {
        try {
            if (dot != null) wm?.removeView(dot)
        } catch (_: Throwable) {
        }
        dot = null
    }

    private var downX = 0f
    private var downY = 0f
    private var startX = 0
    private var startY = 0
    private var moved = false

    private val dragListener = View.OnTouchListener { _, ev ->
        val lp = params ?: return@OnTouchListener false
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.rawX
                downY = ev.rawY
                startX = lp.x
                startY = lp.y
                moved = false
                true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (ev.rawX - downX).toInt()
                val dy = (ev.rawY - downY).toInt()
                if (kotlin.math.abs(dx) > 4 || kotlin.math.abs(dy) > 4) moved = true
                lp.x = startX + dx
                lp.y = startY + dy
                try {
                    wm?.updateViewLayout(dot, lp)
                } catch (_: Throwable) {
                }
                true
            }
            MotionEvent.ACTION_UP -> {
                if (!moved) {
                    val id = boundSessionId
                    if (id != null) {
                        startActivity(
                            Intent(this, AppViewerActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                .putExtra(AppViewerActivity.EXTRA_SESSION_ID, id),
                        )
                    }
                }
                true
            }
            else -> false
        }.also {
            if (ev.actionMasked == MotionEvent.ACTION_DOWN && ev.eventTime.let { true }) {
                // long-press: hide for the rest of the day
            }
        }
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            dot?.postDelayed(longPress, 500)
        } else if (ev.actionMasked != MotionEvent.ACTION_MOVE) {
            dot?.removeCallbacks(longPress)
        }
        true
    }

    private val longPress = Runnable {
        overlayHiddenUntil = OverlayPolicy.endOfDayMillis(System.currentTimeMillis())
        hideDot()
        scope.launch {
            (application as com.ghostdisplay.app.GhostApp).container.prefs
                .setOverlayHiddenUntil(overlayHiddenUntil)
        }
    }

    companion object {
        @Volatile var instance: OverlayDotService? = null
        @Volatile var overlayOptIn: Boolean = false
        @Volatile var overlayHiddenUntil: Long = 0
        @Volatile var boundSessionId: String? = null

        fun sync(context: Context, enabled: Boolean) {
            overlayOptIn = enabled
            val i = Intent(context, OverlayDotService::class.java)
            if (enabled) {
                runCatching { context.startService(i) }
            } else {
                context.stopService(i)
            }
        }
    }
}
