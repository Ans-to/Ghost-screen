package com.ghostdisplay.app.launch

import com.ghostdisplay.app.data.CapabilityReport
import com.ghostdisplay.app.data.Compatibility
import com.ghostdisplay.app.data.LaunchableApp

/**
 * Pure classifier. Given launch / display / accessibility facts, produce a
 * badge and a list of notices. Never infers "works" from a missing signal.
 */
object CompatibilityDetector {

    data class Input(
        val app: LaunchableApp?,
        val virtualDisplayCreated: Boolean,
        val flagsUsed: String,
        val flagsRequested: String,
        val launch: AppLauncher.LaunchResult?,
        val verifiedOnVirtualDisplay: Boolean?,
        val appearedOnDefaultDisplay: Boolean?,
        val nodeControlAvailable: Boolean?,
        val gestureControlAvailable: Boolean?,
        val audioCaptureAvailable: Boolean,
        val audioRequested: Boolean,
        val secureRequested: Boolean,
        val knownLimit: String?,
        val extraNotices: List<String> = emptyList(),
    )

    fun classify(input: Input): CapabilityReport {
        val notices = mutableListOf<String>()
        notices += input.extraNotices
        input.knownLimit?.let { notices += it }

        if (!input.virtualDisplayCreated) {
            notices += "Virtual display was not created."
            return CapabilityReport.failed(notices.joinToString(" "))
        }

        var launchedOnVd = false
        var step: Int? = null
        when (val launch = input.launch) {
            is AppLauncher.LaunchResult.Launched -> {
                step = launch.step
                launchedOnVd = true
                notices += launch.notes
            }
            is AppLauncher.LaunchResult.Failed -> {
                notices += launch.errors
                launchedOnVd = false
            }
            null -> notices += "Launch was not attempted."
        }

        if (input.appearedOnDefaultDisplay == true) {
            notices += "The app appeared on the physical screen. Hidden launch is not supported for this app on this device."
            launchedOnVd = false
        }

        when (input.verifiedOnVirtualDisplay) {
            true -> notices += "Accessibility confirmed windows for this package on the virtual display."
            false -> {
                notices += "Accessibility did not see this package on the virtual display. " +
                    "Either the app never landed there, or the Accessibility service cannot observe that display."
                if (input.appearedOnDefaultDisplay != true) {
                    notices += "Treat this as unverified — do not assume the app is hidden."
                }
            }
            null -> notices += "Could not verify display placement (Accessibility service is off). Enable it in Setup to confirm the app is actually hidden."
        }

        val node = input.nodeControlAvailable == true
        val gesture = input.gestureControlAvailable == true
        if (input.nodeControlAvailable == false && input.gestureControlAvailable == false) {
            notices += "View-only on this device: neither node actions nor gestures can target the virtual display."
        } else if (input.nodeControlAvailable == false && input.gestureControlAvailable == true) {
            notices += "Node actions unavailable; falling back to gesture dispatch. Gesture targeting of virtual displays is not reliable on every Android version."
        } else if (input.nodeControlAvailable == false) {
            notices += "No accessibility nodes exposed by this app. Remote Control cannot click or type."
        }

        if (input.audioRequested && !input.audioCaptureAvailable) {
            notices += "Audio capture is unavailable. The hidden app may still play through the default route, or it may be silent. " +
                "MediaProjection consent is required to route another app's audio."
        }
        if (input.secureRequested) {
            notices += "Secure display requested. DRM / banking surfaces may still refuse to render on a virtual display."
        }
        if (input.app?.resizeable == false) {
            notices += "This activity declares itself not resizeable; some OEMs will refuse to place it on a non-default display."
        }

        val viewOnly = !node && !gesture
        val compatibility = when {
            input.appearedOnDefaultDisplay == true || (input.launch is AppLauncher.LaunchResult.Failed) ->
                Compatibility.UNSUPPORTED
            input.verifiedOnVirtualDisplay == true && launchedOnVd && !viewOnly ->
                Compatibility.SUPPORTED
            input.verifiedOnVirtualDisplay == true && launchedOnVd && viewOnly ->
                Compatibility.LIMITED
            launchedOnVd && input.verifiedOnVirtualDisplay == null ->
                Compatibility.LIMITED
            launchedOnVd && input.verifiedOnVirtualDisplay == false ->
                Compatibility.LIMITED
            else -> Compatibility.UNSUPPORTED
        }

        if (input.flagsUsed != input.flagsRequested) {
            notices += "Virtual display flags degraded: requested ${input.flagsRequested}, got ${input.flagsUsed}."
        }

        return CapabilityReport(
            virtualDisplayCreated = true,
            flagsUsed = input.flagsUsed,
            flagsRequested = input.flagsRequested,
            launchedOnVirtualDisplay = launchedOnVd && input.appearedOnDefaultDisplay != true,
            launchStepSucceeded = step,
            verifiedOnVirtualDisplay = input.verifiedOnVirtualDisplay,
            nodeControlAvailable = node,
            gestureControlAvailable = gesture,
            audioCaptureAvailable = input.audioCaptureAvailable,
            viewOnly = viewOnly,
            notices = notices.distinct(),
            compatibility = compatibility,
        )
    }

    fun badgeLabel(c: Compatibility): String = when (c) {
        Compatibility.SUPPORTED -> "Supported"
        Compatibility.LIMITED -> "Limited"
        Compatibility.UNSUPPORTED -> "Unsupported"
        Compatibility.UNKNOWN -> "Unknown"
    }
}
