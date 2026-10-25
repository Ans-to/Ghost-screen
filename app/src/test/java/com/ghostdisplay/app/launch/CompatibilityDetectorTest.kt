package com.ghostdisplay.app.launch

import com.ghostdisplay.app.data.Compatibility
import com.ghostdisplay.app.data.LaunchableApp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CompatibilityDetectorTest {

    private val chrome = LaunchableApp(
        packageName = "com.android.chrome",
        activityName = "com.google.android.apps.chrome.Main",
        label = "Chrome",
        requestedOrientation = -1,
        resizeable = true,
    )

    @Test
    fun failedDisplayIsUnsupported() {
        val report = CompatibilityDetector.classify(
            CompatibilityDetector.Input(
                app = chrome,
                virtualDisplayCreated = false,
                flagsUsed = "none",
                flagsRequested = "public",
                launch = null,
                verifiedOnVirtualDisplay = null,
                appearedOnDefaultDisplay = null,
                nodeControlAvailable = null,
                gestureControlAvailable = null,
                audioCaptureAvailable = false,
                audioRequested = false,
                secureRequested = false,
                knownLimit = null,
            ),
        )
        assertThat(report.compatibility).isEqualTo(Compatibility.UNSUPPORTED)
        assertThat(report.virtualDisplayCreated).isFalse()
    }

    @Test
    fun verifiedWithNodesIsWorks() {
        val report = CompatibilityDetector.classify(
            CompatibilityDetector.Input(
                app = chrome,
                virtualDisplayCreated = true,
                flagsUsed = "public+presentation+touch+own_focus",
                flagsRequested = "public+presentation+touch+own_focus",
                launch = AppLauncher.LaunchResult.Launched(1, 2, 3, listOf("ok")),
                verifiedOnVirtualDisplay = true,
                appearedOnDefaultDisplay = false,
                nodeControlAvailable = true,
                gestureControlAvailable = false,
                audioCaptureAvailable = true,
                audioRequested = true,
                secureRequested = false,
                knownLimit = null,
            ),
        )
        assertThat(report.compatibility).isEqualTo(Compatibility.SUPPORTED)
        assertThat(report.viewOnly).isFalse()
        assertThat(report.launchedOnVirtualDisplay).isTrue()
    }

    @Test
    fun leakedOntoPhysicalScreenIsUnsupported() {
        val report = CompatibilityDetector.classify(
            CompatibilityDetector.Input(
                app = chrome,
                virtualDisplayCreated = true,
                flagsUsed = "public",
                flagsRequested = "public+touch",
                launch = AppLauncher.LaunchResult.Launched(1, 2, null, emptyList()),
                verifiedOnVirtualDisplay = false,
                appearedOnDefaultDisplay = true,
                nodeControlAvailable = true,
                gestureControlAvailable = true,
                audioCaptureAvailable = false,
                audioRequested = false,
                secureRequested = false,
                knownLimit = null,
            ),
        )
        assertThat(report.compatibility).isEqualTo(Compatibility.UNSUPPORTED)
        assertThat(report.notices.any { it.contains("physical screen") }).isTrue()
    }

    @Test
    fun noNodesNoGesturesIsViewOnlyLimited() {
        val report = CompatibilityDetector.classify(
            CompatibilityDetector.Input(
                app = chrome,
                virtualDisplayCreated = true,
                flagsUsed = "public",
                flagsRequested = "public",
                launch = AppLauncher.LaunchResult.Launched(1, 2, null, emptyList()),
                verifiedOnVirtualDisplay = true,
                appearedOnDefaultDisplay = false,
                nodeControlAvailable = false,
                gestureControlAvailable = false,
                audioCaptureAvailable = false,
                audioRequested = false,
                secureRequested = false,
                knownLimit = null,
            ),
        )
        assertThat(report.viewOnly).isTrue()
        assertThat(report.compatibility).isEqualTo(Compatibility.LIMITED)
        assertThat(report.notices.any { it.contains("View-only") }).isTrue()
    }

    @Test
    fun flagDegradationIsReported() {
        val report = CompatibilityDetector.classify(
            CompatibilityDetector.Input(
                app = chrome,
                virtualDisplayCreated = true,
                flagsUsed = "public",
                flagsRequested = "public+touch+own_focus",
                launch = AppLauncher.LaunchResult.Launched(1, 2, null, emptyList()),
                verifiedOnVirtualDisplay = true,
                appearedOnDefaultDisplay = false,
                nodeControlAvailable = true,
                gestureControlAvailable = false,
                audioCaptureAvailable = false,
                audioRequested = false,
                secureRequested = false,
                knownLimit = null,
            ),
        )
        assertThat(report.notices.any { it.contains("flags degraded") }).isTrue()
    }
}
