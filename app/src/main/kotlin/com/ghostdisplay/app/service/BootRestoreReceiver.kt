package com.ghostdisplay.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class BootRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val request = OneTimeWorkRequestBuilder<RestoreWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "ghost-restore",
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}
