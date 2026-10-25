package com.ghostdisplay.app

import android.app.Application
import android.webkit.CookieManager
import com.ghostdisplay.app.browser.TabPersistenceRepository
import com.ghostdisplay.app.data.BlockedApp
import com.ghostdisplay.app.session.RoomSessionStore
import com.ghostdisplay.app.session.SessionManager
import com.ghostdisplay.app.data.GhostDatabase
import com.ghostdisplay.app.data.PreferencesRepository
import com.ghostdisplay.app.ocr.MlKitOcrFallback
import com.ghostdisplay.app.ocr.OcrFallback
import com.ghostdisplay.app.safety.Blocklist
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppContainer(app: Application) {
    val db: GhostDatabase = GhostDatabase.create(app)
    val prefs = PreferencesRepository(app)
    val ocr: OcrFallback = MlKitOcrFallback()
    val tabs = TabPersistenceRepository(db.browserSessions(), db.browserTabs(), db.tabHistory())
    val sessionStore = RoomSessionStore(db.sessions(), db.sessionLog())
    val sessionManager = SessionManager(sessionStore)
}

class GhostApp : Application() {
    lateinit var container: AppContainer
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        CookieManager.getInstance().setAcceptCookie(true)
        scope.launch { seedBlocklist() }
    }

    private suspend fun seedBlocklist() {
        val dao = container.db.blocklist()
        Blocklist.DEFAULT_PACKAGES.forEach { pkg ->
            dao.upsert(
                BlockedApp(
                    packageName = pkg,
                    reason = "Default banking / payments blocklist",
                    isDefault = true,
                ),
            )
        }
    }
}
