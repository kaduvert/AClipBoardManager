package com.clipvault.app.root

import android.content.Context
import android.content.pm.PackageManager
import com.clipvault.app.xposed.HookStatus
import com.topjohnwu.superuser.Shell

enum class CaptureMethod {
    LSPOSED,
    ROOT_PRIV_APP,
    NONE
}

data class CaptureStatus(
    val method: CaptureMethod,
    val deviceRooted: Boolean,
    val isPrivApp: Boolean
)

object CaptureStatusChecker {

    /**
     * Cheap, synchronous, in-process check - safe to call from a Composable's
     * LaunchedEffect.
     *
     * Note: [HookStatus] can only ever reflect the *classic*-API hook
     * ([com.clipvault.app.xposed.ClipboardHook]). The modern-API hook
     * ([com.clipvault.app.xposed.ClipboardHookModern]) has no equivalent - the
     * modern libxposed API doesn't let a module hook its own app process the
     * way the classic one does, so there's no in-process marker to flip. In
     * practice this only matters if a framework somehow runs the modern hook
     * but not the classic one; since both are shipped together and the
     * classic API works everywhere, that's an unlikely combination. If it
     * ever needs to be precise, the fix is a real status channel (the
     * `libxposed/service` library's XposedService), not a bigger version of
     * this workaround.
     */
    fun check(context: Context): CaptureStatus {
        val lsposedActive = runCatching { HookStatus.isActive() }.getOrDefault(false)
        val isPrivApp = context.packageManager.checkPermission(
            "android.permission.READ_CLIPBOARD_IN_BACKGROUND",
            context.packageName
        ) == PackageManager.PERMISSION_GRANTED

        val method = when {
            lsposedActive -> CaptureMethod.LSPOSED
            isPrivApp -> CaptureMethod.ROOT_PRIV_APP
            else -> CaptureMethod.NONE
        }
        return CaptureStatus(
            method = method,
            deviceRooted = isDeviceRooted(),
            isPrivApp = isPrivApp
        )
    }

    /** Root check is a bit heavier (spawns a shell) - call off the main thread. */
    fun isDeviceRooted(): Boolean = runCatching { Shell.getShell().isRoot }.getOrDefault(false)
}
