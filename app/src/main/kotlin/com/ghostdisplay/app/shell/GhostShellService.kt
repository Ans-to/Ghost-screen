package com.ghostdisplay.app.shell

/**
 * Runs as the Shizuku (shell) identity. AOSP allows INTERNAL_SYSTEM_WINDOW
 * callers to start activities on any display, including an app-created
 * virtual display that GhostDisplay’s own uid cannot target with
 * launchDisplayId.
 */
class GhostShellService : IGhostShell.Stub {

    constructor() : super()

    @Suppress("unused")
    constructor(context: android.content.Context) : super()

    @Suppress("unused")
    fun destroy() {
        System.exit(0)
    }

    override fun startOnDisplay(packageName: String, activityName: String, displayId: Int): String {
        val component = if (activityName.startsWith(".")) {
            "$packageName$activityName"
        } else {
            activityName
        }
        val command = "am start --user 0 --display $displayId " +
            "--activity-multiple-task -n $packageName/$component"
        val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
        val stdout = process.inputStream.bufferedReader().use { it.readText() }
        val stderr = process.errorStream.bufferedReader().use { it.readText() }
        val code = process.waitFor()
        val out = (stdout + "\n" + stderr).trim()
        val failed = code != 0 ||
            out.contains("Error type", ignoreCase = true) ||
            out.contains("Exception", ignoreCase = true) ||
            out.contains("Permission Denial", ignoreCase = true) ||
            out.contains("unable to resolve", ignoreCase = true)
        if (failed) {
            return "ERROR: ${out.ifBlank { "am start exited $code" }}"
        }
        return out.ifBlank { "am start --display $displayId ok" }
    }
}
