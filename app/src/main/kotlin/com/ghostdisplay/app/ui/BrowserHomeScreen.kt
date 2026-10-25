package com.ghostdisplay.app.ui

import com.ghostdisplay.app.data.LaunchableApp
import com.ghostdisplay.app.profile.AppCategory
import com.ghostdisplay.app.profile.AppCategoryClassifier

/** Installed browsers only — the real Chrome/Firefox/etc., not an in-app WebView. */
fun isInstalledBrowser(app: LaunchableApp): Boolean =
    AppCategoryClassifier.classify(
        app.packageName,
        app.label,
        app.requestedOrientation,
        isSystemApp = false,
    ) == AppCategory.BROWSER
