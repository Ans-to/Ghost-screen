package com.ghostdisplay.app.launch

import android.app.ActivityManager
import android.content.Context
import android.os.IBinder
import java.lang.reflect.InvocationTargetException

/**
 * Reparents a task GhostDisplay already owns onto a virtual display.
 *
 * Used only for *our* [DisplayTrampolineActivity] task when HyperOS ignores
 * launchDisplayId and leaves the starter on the phone. The target app is
 * never started on display 0.
 */
object HiddenTaskMover {

    fun relaunchOnDisplay(context: Context, taskId: Int, options: android.os.Bundle): Result<String> {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val method = am.javaClass.methods.firstOrNull {
                it.name == "startActivityFromRecents" && it.parameterTypes.size == 2
            } ?: ActivityManager::class.java.methods.firstOrNull {
                it.name == "startActivityFromRecents" && it.parameterTypes.size == 2
            } ?: return Result.failure(
                IllegalStateException("startActivityFromRecents is not on this device"),
            )
            method.isAccessible = true
            method.invoke(am, taskId, options)
            Result.success("ActivityManager.startActivityFromRecents")
        } catch (t: Throwable) {
            Result.failure(IllegalStateException(unwrap("startActivityFromRecents", t)))
        }
    }

    fun moveTaskToDisplay(context: Context, taskId: Int, displayId: Int): Result<String> {
        val attempts = listOf(
            tryAmMove(context, taskId, displayId),
            tryAtmService(taskId, displayId),
            tryBinder(taskId, displayId),
        )
        attempts.firstOrNull { it.isSuccess }?.let { return it }
        val detail = attempts.mapNotNull { it.exceptionOrNull()?.message }.joinToString(" | ")
        return Result.failure(
            IllegalStateException(
                detail.ifBlank { "This device exposes no task-reparent API to GhostDisplay." },
            ),
        )
    }

    private fun tryAmMove(context: Context, taskId: Int, displayId: Int): Result<String> {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val method = am.javaClass.methods.firstOrNull { it.name == "moveTaskToDisplay" }
                ?: ActivityManager::class.java.methods.firstOrNull { it.name == "moveTaskToDisplay" }
                ?: return Result.failure(
                    IllegalStateException("ActivityManager.moveTaskToDisplay is not on this device"),
                )
            method.isAccessible = true
            method.invoke(am, taskId, displayId)
            Result.success("ActivityManager.moveTaskToDisplay")
        } catch (t: Throwable) {
            Result.failure(IllegalStateException(unwrap("ActivityManager.moveTaskToDisplay", t)))
        }
    }

    private fun tryAtmService(taskId: Int, displayId: Int): Result<String> {
        return try {
            val atmClass = Class.forName("android.app.ActivityTaskManager")
            val getService = atmClass.getDeclaredMethod("getService")
            getService.isAccessible = true
            val service = getService.invoke(null)
                ?: return Result.failure(IllegalStateException("ActivityTaskManager.getService returned null"))
            val name = invokeMove(service, taskId, displayId)
                ?: return Result.failure(
                    IllegalStateException("ActivityTaskManager has no moveRootTaskToDisplay / moveTaskToDisplay"),
                )
            Result.success("ActivityTaskManager.$name")
        } catch (t: Throwable) {
            Result.failure(IllegalStateException(unwrap("ActivityTaskManager", t)))
        }
    }

    private fun tryBinder(taskId: Int, displayId: Int): Result<String> {
        return try {
            val sm = Class.forName("android.os.ServiceManager")
            val getService = sm.getDeclaredMethod("getService", String::class.java)
            getService.isAccessible = true
            val binder = getService.invoke(null, "activity_task") as? IBinder
                ?: return Result.failure(IllegalStateException("activity_task binder missing"))
            val stub = Class.forName("android.app.IActivityTaskManager\$Stub")
            val asInterface = stub.getMethod("asInterface", IBinder::class.java)
            val service = asInterface.invoke(null, binder)
                ?: return Result.failure(IllegalStateException("IActivityTaskManager.asInterface returned null"))
            val name = invokeMove(service, taskId, displayId)
                ?: return Result.failure(
                    IllegalStateException("IActivityTaskManager has no moveRootTaskToDisplay / moveTaskToDisplay"),
                )
            Result.success("IActivityTaskManager.$name")
        } catch (t: Throwable) {
            Result.failure(IllegalStateException(unwrap("IActivityTaskManager", t)))
        }
    }

    private fun invokeMove(service: Any, taskId: Int, displayId: Int): String? {
        val names = listOf("moveRootTaskToDisplay", "moveTaskToDisplay")
        for (name in names) {
            val method = service.javaClass.methods.firstOrNull {
                it.name == name && it.parameterTypes.size == 2
            } ?: continue
            method.isAccessible = true
            method.invoke(service, taskId, displayId)
            return name
        }
        return null
    }

    private fun unwrap(prefix: String, t: Throwable): String {
        val cause = if (t is InvocationTargetException) t.targetException ?: t.cause ?: t else t
        return "$prefix: ${cause.javaClass.simpleName}: ${cause.message}"
    }
}
