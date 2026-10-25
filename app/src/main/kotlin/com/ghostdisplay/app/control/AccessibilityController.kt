package com.ghostdisplay.app.control

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.ghostdisplay.app.ocr.OcrFallback

/**
 * Node-first controller for a virtual display.
 *
 *  1. Enumerate windows whose displayId matches the VirtualDisplay.
 *  2. Perform ACTION_CLICK / ACTION_SET_TEXT / ACTION_SCROLL / ACTION_FOCUS.
 *  3. Fall back to dispatchGesture only when node actions are unavailable.
 *  4. If gestures cannot target the virtual display, report view-only.
 *
 * This class does not own the AccessibilityService; it talks to whichever
 * instance [GhostAccessibilityService.instance] currently holds.
 */
class AccessibilityController(
    private val serviceProvider: () -> GhostAccessibilityService? = { GhostAccessibilityService.instance },
    private val ocr: OcrFallback? = null,
) {

    data class Probe(
        val serviceBound: Boolean,
        val windowsOnVirtualDisplay: Int,
        val nodesOnVirtualDisplay: Int,
        val nodeControlAvailable: Boolean,
        val gestureControlAvailable: Boolean,
        val viewOnly: Boolean,
        val notices: List<String>,
    )

    sealed class ActionResult {
        data class Ok(val detail: String) : ActionResult()
        data class ViewOnly(val reason: String) : ActionResult()
        data class Failed(val reason: String) : ActionResult()
    }

    fun isBound(): Boolean = serviceProvider() != null

    fun probe(displayId: Int, packageName: String?): Probe {
        val service = serviceProvider()
        if (service == null) {
            return Probe(
                serviceBound = false,
                windowsOnVirtualDisplay = 0,
                nodesOnVirtualDisplay = 0,
                nodeControlAvailable = false,
                gestureControlAvailable = false,
                viewOnly = true,
                notices = listOf(
                    "Accessibility service is off. Open Setup and enable “GhostDisplay control”. " +
                        "Without it, GhostDisplay cannot click or type into the hidden app (view-only).",
                ),
            )
        }

        val windows = windowsOnDisplay(service, displayId, packageName)
        val nodes = windows.flatMap { w ->
            val root = w.root ?: return@flatMap emptyList()
            flatten(root)
        }
        val clickable = nodes.any { it.isClickable || it.isEditable || it.isScrollable }
        val notices = mutableListOf<String>()

        val gestureOk = service.gesturesUsable
        if (windows.isEmpty()) {
            notices += "No Accessibility windows reported on display $displayId. " +
                "The hidden app may not have landed there, or this Android version does not expose virtual-display windows to Accessibility."
        }
        if (!clickable && windows.isNotEmpty()) {
            notices += "Windows exist on the virtual display but expose no clickable/editable nodes."
        }
        if (!gestureOk) {
            notices += "Gesture dispatch cannot target the virtual display on this Android version. Node actions only."
        }

        val nodeOk = clickable
        val viewOnly = !nodeOk && !gestureOk
        if (viewOnly) {
            notices += "View-only on this device: Remote Control cannot click or type into the hidden app."
        }

        return Probe(
            serviceBound = true,
            windowsOnVirtualDisplay = windows.size,
            nodesOnVirtualDisplay = nodes.size,
            nodeControlAvailable = nodeOk,
            gestureControlAvailable = gestureOk && !nodeOk,
            viewOnly = viewOnly,
            notices = notices,
        )
    }

    fun packageOnDisplay(displayId: Int, packageName: String): Boolean {
        val service = serviceProvider() ?: return false
        return windowsOnDisplay(service, displayId, packageName).isNotEmpty()
    }

    fun packageOnDefaultDisplay(packageName: String): Boolean {
        val service = serviceProvider() ?: return false
        return windowsOnDisplay(service, 0, packageName).isNotEmpty()
    }

    fun click(displayId: Int, x: Float, y: Float): ActionResult {
        val service = serviceProvider() ?: return unbound()
        val node = nodeAt(service, displayId, x.toInt(), y.toInt())
        if (node != null) {
            val target = clickableAncestor(node) ?: node
            target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            val clicked = target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            if (clicked) {
                return ActionResult.Ok("Clicked node ${target.className} “${target.text ?: target.contentDescription}”")
            }
        }
        return gestureClick(service, displayId, x, y)
    }

    fun longPress(displayId: Int, x: Float, y: Float): ActionResult {
        val service = serviceProvider() ?: return unbound()
        val node = nodeAt(service, displayId, x.toInt(), y.toInt())
        if (node != null) {
            val target = clickableAncestor(node) ?: node
            if (target.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)) {
                return ActionResult.Ok("Long-clicked node")
            }
        }
        return gestureClick(service, displayId, x, y, durationMs = 600L)
    }

    fun swipe(displayId: Int, x1: Float, y1: Float, x2: Float, y2: Float): ActionResult {
        val service = serviceProvider() ?: return unbound()
        val node = nodeAt(service, displayId, x1.toInt(), y1.toInt())
        if (node != null) {
            val scrollable = scrollableAncestor(node)
            if (scrollable != null) {
                val forward = y2 < y1 || x2 < x1
                val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                if (scrollable.performAction(action)) {
                    return ActionResult.Ok("Scrolled via node action")
                }
            }
        }
        return gestureSwipe(service, displayId, x1, y1, x2, y2)
    }

    fun setText(displayId: Int, text: String, packageName: String?): ActionResult {
        val service = serviceProvider() ?: return unbound()
        val focused = findFocusedEditable(service, displayId, packageName)
            ?: findFirstEditable(service, displayId, packageName)
            ?: return ActionResult.Failed("No editable field on the virtual display. Tap a text field first.")
        focused.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val ok = focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        return if (ok) ActionResult.Ok("Set text on ${focused.className}")
        else ActionResult.Failed("ACTION_SET_TEXT was rejected. The field may not support it; try clipboard paste from Remote Control.")
    }

    fun pasteClipboard(displayId: Int, packageName: String?): ActionResult {
        val service = serviceProvider() ?: return unbound()
        val focused = findFocusedEditable(service, displayId, packageName)
            ?: findFirstEditable(service, displayId, packageName)
            ?: return ActionResult.Failed("No editable field to paste into.")
        focused.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val ok = focused.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        return if (ok) ActionResult.Ok("Pasted clipboard")
        else ActionResult.Failed("ACTION_PASTE was rejected.")
    }

    fun clickByText(displayId: Int, text: String, packageName: String?): ActionResult {
        val service = serviceProvider() ?: return unbound()
        val node = findByText(service, displayId, packageName, text)
        if (node != null) {
            val target = clickableAncestor(node) ?: node
            return if (target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                ActionResult.Ok("Clicked “${node.text ?: node.contentDescription}”")
            } else {
                val bounds = Rect()
                target.getBoundsInScreen(bounds)
                gestureClick(service, displayId, bounds.centerX().toFloat(), bounds.centerY().toFloat())
            }
        }
        val ocrHit = ocr
        if (ocrHit != null) {
            return ActionResult.Failed("No node matching “$text”. OCR fallback is available from debug preview only.")
        }
        return ActionResult.Failed("No node matching “$text” on the virtual display.")
    }

    fun scroll(displayId: Int, packageName: String?, forward: Boolean): ActionResult {
        val service = serviceProvider() ?: return unbound()
        val windows = windowsOnDisplay(service, displayId, packageName)
        for (w in windows) {
            val root = w.root ?: continue
            val scrollable = flatten(root).firstOrNull { it.isScrollable }
            if (scrollable != null) {
                val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                if (scrollable.performAction(action)) return ActionResult.Ok("Scrolled")
            }
        }
        return ActionResult.Failed("No scrollable node on the virtual display.")
    }

    /**
     * Back / Home / Recents scoped as well as a third-party Accessibility
     * service can manage:
     *
     *  - Prefer clicking an in-app "Navigate up" / Back node on the VD.
     *  - GLOBAL_ACTION_* is not display-scoped on most devices and may fire
     *    on the physical screen — we only use it when [allowGlobal] is true
     *    and we tell the caller.
     */
    fun back(displayId: Int, packageName: String?, allowGlobal: Boolean): ActionResult {
        val clicked = clickByText(displayId, "Navigate up", packageName)
        if (clicked is ActionResult.Ok) return clicked
        val service = serviceProvider() ?: return unbound()
        if (!allowGlobal) {
            return ActionResult.Failed(
                "No in-app Back control on the virtual display. " +
                    "System Back cannot be scoped to a virtual display on this device without firing on the physical screen.",
            )
        }
        val ok = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        return if (ok) ActionResult.Ok(
            "Sent system Back. Warning: this is not display-scoped and may affect the app in the foreground on the physical screen.",
        ) else ActionResult.Failed("System Back was rejected.")
    }

    fun home(allowGlobal: Boolean): ActionResult {
        val service = serviceProvider() ?: return unbound()
        if (!allowGlobal) {
            return ActionResult.Failed(
                "Home cannot be scoped to a virtual display. Enable “allow system actions” only if you accept it affecting the physical screen.",
            )
        }
        val ok = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        return if (ok) ActionResult.Ok("Sent system Home (physical display).")
        else ActionResult.Failed("System Home was rejected.")
    }

    fun recents(allowGlobal: Boolean): ActionResult {
        val service = serviceProvider() ?: return unbound()
        if (!allowGlobal) {
            return ActionResult.Failed(
                "Recents cannot be scoped to a virtual display.",
            )
        }
        val ok = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
        return if (ok) ActionResult.Ok("Sent Recents (physical display).")
        else ActionResult.Failed("Recents was rejected.")
    }

    fun runTask(displayId: Int, packageName: String?, goal: String): List<ActionResult> {
        val steps = TaskModeParser.parse(goal)
        if (steps.isEmpty()) return listOf(ActionResult.Failed("Could not parse that goal."))
        return steps.map { step ->
            when (step.kind) {
                TaskModeParser.Kind.CLICK_TEXT -> clickByText(displayId, step.argument, packageName)
                TaskModeParser.Kind.SET_TEXT, TaskModeParser.Kind.OPEN_URL_HINT ->
                    setText(displayId, step.argument, packageName)
                TaskModeParser.Kind.SCROLL_FORWARD -> scroll(displayId, packageName, true)
                TaskModeParser.Kind.SCROLL_BACKWARD -> scroll(displayId, packageName, false)
            }
        }
    }

    private fun gestureClick(
        service: GhostAccessibilityService,
        displayId: Int,
        x: Float,
        y: Float,
        durationMs: Long = 50L,
    ): ActionResult {
        if (!service.gesturesUsable) {
            return ActionResult.ViewOnly(
                "Gestures cannot target virtual display $displayId on this Android version. View-only.",
            )
        }
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val dispatched = if (Build.VERSION.SDK_INT >= 30) {
            // API 30+ dispatchGesture still has no public displayId parameter.
            // Some OEMs route it to the focused display. We try and report.
            service.dispatchGesture(gesture, null, null)
        } else {
            service.dispatchGesture(gesture, null, null)
        }
        return if (dispatched) {
            ActionResult.Ok(
                "Dispatched a gesture at ($x, $y). On many devices gestures only hit the physical display — if the hidden app does not react, this device is view-only for touch.",
            )
        } else {
            ActionResult.ViewOnly("dispatchGesture returned false. View-only on this device.")
        }
    }

    private fun gestureSwipe(
        service: GhostAccessibilityService,
        displayId: Int,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
    ): ActionResult {
        if (!service.gesturesUsable) {
            return ActionResult.ViewOnly("Gestures cannot target virtual display $displayId. View-only.")
        }
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 250)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val dispatched = service.dispatchGesture(gesture, null, null)
        return if (dispatched) ActionResult.Ok("Dispatched swipe")
        else ActionResult.ViewOnly("Swipe gesture rejected. View-only.")
    }

    private fun windowsOnDisplay(
        service: GhostAccessibilityService,
        displayId: Int,
        packageName: String?,
    ): List<AccessibilityWindowInfo> {
        val windows = service.windows() ?: return emptyList()
        return windows.filter { w ->
            val wDisplay = windowDisplayId(w)
            if (wDisplay != displayId) return@filter false
            if (packageName == null) return@filter true
            val root = w.root
            root == null || root.packageName == null || root.packageName.toString() == packageName
        }
    }

    private fun windowDisplayId(window: AccessibilityWindowInfo): Int {
        return if (Build.VERSION.SDK_INT >= 30) window.displayId else 0
    }

    private fun nodeAt(
        service: GhostAccessibilityService,
        displayId: Int,
        x: Int,
        y: Int,
    ): AccessibilityNodeInfo? {
        val windows = windowsOnDisplay(service, displayId, null)
        val hits = mutableListOf<AccessibilityNodeInfo>()
        for (w in windows) {
            val root = w.root ?: continue
            walkHits(root, x, y, hits)
        }
        return hits.minByOrNull { n ->
            val r = Rect()
            n.getBoundsInScreen(r)
            r.width() * r.height()
        }
    }

    private fun walkHits(node: AccessibilityNodeInfo, x: Int, y: Int, out: MutableList<AccessibilityNodeInfo>) {
        val r = Rect()
        node.getBoundsInScreen(r)
        if (r.contains(x, y)) out += node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            walkHits(child, x, y, out)
        }
    }

    private fun flatten(node: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val out = mutableListOf<AccessibilityNodeInfo>()
        fun rec(n: AccessibilityNodeInfo) {
            out += n
            for (i in 0 until n.childCount) {
                val c = n.getChild(i) ?: continue
                rec(c)
            }
        }
        rec(node)
        return out
    }

    private fun clickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var cur: AccessibilityNodeInfo? = node
        while (cur != null) {
            if (cur.isClickable || cur.isCheckable || cur.isLongClickable) return cur
            cur = cur.parent
        }
        return null
    }

    private fun scrollableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var cur: AccessibilityNodeInfo? = node
        while (cur != null) {
            if (cur.isScrollable) return cur
            cur = cur.parent
        }
        return null
    }

    private fun findFocusedEditable(
        service: GhostAccessibilityService,
        displayId: Int,
        packageName: String?,
    ): AccessibilityNodeInfo? {
        return windowsOnDisplay(service, displayId, packageName)
            .flatMap { w -> w.root?.let { flatten(it) } ?: emptyList() }
            .firstOrNull { it.isFocused && it.isEditable }
    }

    private fun findFirstEditable(
        service: GhostAccessibilityService,
        displayId: Int,
        packageName: String?,
    ): AccessibilityNodeInfo? {
        return windowsOnDisplay(service, displayId, packageName)
            .flatMap { w -> w.root?.let { flatten(it) } ?: emptyList() }
            .firstOrNull { it.isEditable }
    }

    private fun findByText(
        service: GhostAccessibilityService,
        displayId: Int,
        packageName: String?,
        text: String,
    ): AccessibilityNodeInfo? {
        val needle = text.trim().lowercase()
        if (needle.isEmpty()) return null
        val nodes = windowsOnDisplay(service, displayId, packageName)
            .flatMap { w -> w.root?.let { flatten(it) } ?: emptyList() }
        return nodes.firstOrNull { n ->
            val t = n.text?.toString()?.lowercase() ?: ""
            val d = n.contentDescription?.toString()?.lowercase() ?: ""
            t == needle || d == needle || t.contains(needle) || d.contains(needle)
        }
    }

    private fun unbound() = ActionResult.Failed(
        "Accessibility service is off. Enable “GhostDisplay control” in Setup.",
    )
}
