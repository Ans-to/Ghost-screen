package com.ghostdisplay.app.launch

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import rikka.shizuku.Shizuku

enum class ShizukuPhase {
    NOT_INSTALLED,
    NOT_RUNNING,
    NEED_PERMISSION,
    READY,
}

object ShizukuStatus {

    const val PACKAGE = "moe.shizuku.privileged.api"
    const val PERMISSION_CODE = 9101

    fun phase(context: Context): ShizukuPhase {
        if (!isInstalled(context)) return ShizukuPhase.NOT_INSTALLED
        val binder = try {
            Shizuku.pingBinder()
        } catch (_: Throwable) {
            false
        }
        if (!binder) return ShizukuPhase.NOT_RUNNING
        val granted = try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }
        return if (granted) ShizukuPhase.READY else ShizukuPhase.NEED_PERMISSION
    }

    fun isReady(context: Context): Boolean = phase(context) == ShizukuPhase.READY

    fun isInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(PACKAGE, 0)
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun openShizukuApp(context: Context): Boolean {
        val launch = context.packageManager.getLaunchIntentForPackage(PACKAGE) ?: return false
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(launch)
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun openShizukuOrStore(context: Context) {
        if (openShizukuApp(context)) return
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PACKAGE")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(market)
            return
        } catch (_: Throwable) {
        }
        val web = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://github.com/RikkaApps/Shizuku/releases"),
        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        runCatching { context.startActivity(web) }
    }

    fun requestPermission() {
        if (!Shizuku.pingBinder()) return
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) return
        Shizuku.requestPermission(PERMISSION_CODE)
    }

    fun notReadyMessage(context: Context): String {
        return when (phase(context)) {
            ShizukuPhase.NOT_INSTALLED ->
                "Shizuku is not installed. Open Setup and follow the Shizuku steps. HyperOS cannot start a hidden window without it."
            ShizukuPhase.NOT_RUNNING ->
                "Shizuku is installed but not running. Open Shizuku and tap Start, then return here."
            ShizukuPhase.NEED_PERMISSION ->
                "GhostDisplay does not have Shizuku permission yet. Open Setup and tap Grant Shizuku."
            ShizukuPhase.READY ->
                "Shizuku is ready."
        }
    }
}
