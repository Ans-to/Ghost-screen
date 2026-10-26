package com.ghostdisplay.app.launch

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import com.ghostdisplay.app.BuildConfig
import com.ghostdisplay.app.shell.GhostShellService
import com.ghostdisplay.app.shell.IGhostShell
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku

/**
 * Starts a target package on a virtual display using Shizuku’s shell uid.
 * That uid holds INTERNAL_SYSTEM_WINDOW, so AOSP allows launchDisplayId
 * where GhostDisplay’s own uid is denied.
 */
object ShizukuAppStarter {

    private val lock = Mutex()
    private val args = Shizuku.UserServiceArgs(
        ComponentName(BuildConfig.APPLICATION_ID, GhostShellService::class.java.name),
    )
        .daemon(false)
        .processNameSuffix("shell")
        .debuggable(BuildConfig.DEBUG)
        .version(BuildConfig.VERSION_CODE)

    @Volatile
    private var shell: IGhostShell? = null

    suspend fun startOnDisplay(
        context: Context,
        packageName: String,
        activityName: String,
        displayId: Int,
    ): Result<String> {
        if (!ShizukuStatus.isReady(context)) {
            return Result.failure(IllegalStateException(ShizukuStatus.notReadyMessage(context)))
        }
        val service = bind()
            ?: return Result.failure(
                IllegalStateException("Shizuku started but GhostDisplay could not attach its shell helper. Open Shizuku, tap Start, then try again."),
            )
        return withContext(Dispatchers.IO) {
            runCatching {
                val out = service.startOnDisplay(packageName, activityName, displayId)
                if (out.startsWith("ERROR:")) error(out.removePrefix("ERROR:").trim())
                out
            }
        }
    }

    private suspend fun bind(): IGhostShell? = lock.withLock {
        val existing = shell
        if (existing != null && existing.asBinder().pingBinder()) return existing
        shell = null
        val ready = CompletableDeferred<IGhostShell>()
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                if (binder == null || !binder.pingBinder()) {
                    ready.completeExceptionally(IllegalStateException("Shizuku shell helper returned no binder"))
                    return
                }
                val svc = IGhostShell.Stub.asInterface(binder)
                shell = svc
                ready.complete(svc)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                shell = null
            }
        }
        return try {
            Shizuku.bindUserService(args, connection)
            withTimeoutOrNull(8_000) { ready.await() }
        } catch (t: Throwable) {
            null
        }
    }
}
