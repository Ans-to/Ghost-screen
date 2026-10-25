package com.ghostdisplay.app.util

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ghostdisplay.app.control.GhostAccessibilityService
import com.ghostdisplay.app.safety.GhostNotificationListenerService
import com.ghostdisplay.app.service.MediaProjectionHolder

data class ChecklistItem(
    val id: String,
    val title: String,
    val body: String,
    val required: Boolean,
    val granted: Boolean,
    val actionLabel: String,
)

object SetupStatus {

    fun items(context: Context): List<ChecklistItem> {
        return listOf(
            ChecklistItem(
                id = "accessibility",
                title = "Accessibility (control)",
                body = "Enable “GhostDisplay control”. This is how hold-an-app on the home screen shows Run hidden / Stop, and the only control path inside a hidden window. Without it, hold apps in the GhostDisplay list instead; hidden apps are view-only.",
                required = false,
                granted = isAccessibilityEnabled(context),
                actionLabel = "Open Accessibility settings",
            ),
            ChecklistItem(
                id = "battery",
                title = "Battery exemption",
                body = "Stops the system from killing the hidden session after a few minutes. Recommended, not required.",
                required = false,
                granted = isIgnoringBattery(context),
                actionLabel = "Request exemption",
            ),
            ChecklistItem(
                id = "notifications",
                title = "Notifications",
                body = "Needed so the persistent “running hidden” notification (with Stop) can appear.",
                required = true,
                granted = hasNotifications(context),
                actionLabel = "Grant",
            ),
            ChecklistItem(
                id = "audio",
                title = "Microphone (audio routing)",
                body = "Android reuses RECORD_AUDIO for MediaProjection playback capture. Required to hear a hidden video/game with the screen off.",
                required = false,
                granted = hasAudio(context),
                actionLabel = "Grant",
            ),
            ChecklistItem(
                id = "mediaprojection",
                title = "MediaProjection consent",
                body = "The standard screen-capture dialog. GhostDisplay does not record or save frames. Needed for video, DRM-ish apps, and audio routing. Asked when you launch, not up front.",
                required = false,
                granted = MediaProjectionHolder.hasConsent(),
                actionLabel = "Asked at launch",
            ),
            ChecklistItem(
                id = "listener",
                title = "Notification listener (optional)",
                body = "Lets GhostDisplay drop notifications from a hidden app when that app’s “block notifications” toggle is on.",
                required = false,
                granted = GhostNotificationListenerService.isConnected() || isNotificationListenerEnabled(context),
                actionLabel = "Open listener settings",
            ),
            ChecklistItem(
                id = "usage",
                title = "Usage access (overlay return indicator)",
                body = "Optional. Lets the 8dp overlay dot know which real app is in the foreground so it never covers home, GhostDisplay, or a different app. Accessibility is used as a fallback.",
                required = false,
                granted = hasUsageAccess(context),
                actionLabel = "Open usage access",
            ),
            ChecklistItem(
                id = "overlay",
                title = "Draw over other apps (overlay dot)",
                body = "Optional and off by default. Grants SYSTEM_ALERT_WINDOW so an 8dp return-indicator dot can appear — not a floating app window. Enable the overlay toggle below after this grant.",
                required = false,
                granted = Settings.canDrawOverlays(context),
                actionLabel = "Open overlay permission",
            ),
        )
    }

    fun isAccessibilityEnabled(context: Context): Boolean {
        if (GhostAccessibilityService.isEnabled()) return true
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabled = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
        val me = ComponentName(context, GhostAccessibilityService::class.java).flattenToString()
        return enabled.any { it.id.contains(context.packageName) || it.id == me }
    }

    fun isIgnoringBattery(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun hasNotifications(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    fun hasAudio(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    fun isNotificationListenerEnabled(context: Context): Boolean {
        val cn = ComponentName(context, GhostNotificationListenerService::class.java)
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        ) ?: return false
        return flat.split(":").any { it.contains(cn.flattenToString()) || it.contains(cn.packageName) }
    }

    fun accessibilitySettingsIntent(): Intent =
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    fun batteryIntent(context: Context): Intent {
        return Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }

    fun notificationListenerIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

    fun appNotificationSettings(context: Context): Intent {
        return Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
    }

    fun hasUsageAccess(context: Context): Boolean {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName,
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun usageSettingsIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    fun overlayIntent(context: Context): Intent {
        return Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        )
    }

    fun canDraw(context: Context): Boolean = Settings.canDrawOverlays(context)
}
