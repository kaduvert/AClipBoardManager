package com.clipvault.app.capture

import android.content.Context
import com.clipvault.app.R
import com.clipvault.app.data.ClipRepository
import com.clipvault.app.xposed.HookStatus

/**
 * LSPosed-flavor implementation. See app/src/root's CaptureCoordinator for the other
 * half of this pair - only one of the two is ever compiled into a given build.
 */
object CaptureCoordinator {

    /** This build has no foreground service, so there's nothing to ask permission for. */
    const val needsNotificationPermission = false

    /**
     * Nothing to do here: the hook lives entirely inside system_server, in a
     * process this app doesn't control and that starts long before this one does.
     * Kept as a no-op (rather than omitted) so ClipVaultApp.onCreate() can call
     * this unconditionally without caring which flavor it's running in.
     */
    fun onAppCreated(context: Context) {
        // Intentionally empty.
    }

    /**
     * Three-state status check:
     *
     * 1. **Module not loaded** ([HookStatus.isActive] is still false): LSPosed has not
     *    loaded this module into the app process.  The hook in system_server is
     *    certainly not running either.
     *
     * 2. **Module loaded, delivery not yet confirmed this session**
     *    ([HookStatus.isActive] is true but [ClipRepository.sessionCaptureConfirmed]
     *    is false): LSPosed patched [HookStatus.isActive] → true in this process, but
     *    that only confirms the module was loaded *here*.  The system_server hook in
     *    the "android" package is a completely separate LoadPackageParam invocation
     *    that can silently fail (ClipboardService not found, hook exception, wrong
     *    scope) while this process-local patch still succeeds.
     *
     *    Critically, [ClipRepository.sessionCaptureConfirmed] is in-memory only and
     *    resets to false on every cold start.  This means that if LSPosed breaks
     *    between reboots (update that reset the scope, system_server hook now failing
     *    silently, etc.), status 2 is shown on next open rather than a stale "Active"
     *    left over from whenever things last worked.
     *
     * 3. **Confirmed delivery this session** (both true): at least one clip has made
     *    it all the way from system_server through the ContentProvider to the database
     *    since this process last started.  This is the only state that positively
     *    confirms the full capture pipeline is currently working.
     *
     * [active] is true for states 2 and 3: the module is loaded and we just don't
     * yet have session-scoped proof the system_server end is working.
     */
    suspend fun checkStatus(context: Context): CaptureStatus {
        val moduleLoaded = runCatching { HookStatus.isActive() }.getOrDefault(false)
        if (!moduleLoaded) {
            return CaptureStatus(
                active = false,
                statusText = context.getString(R.string.settings_status_inactive),
                hintText = context.getString(R.string.settings_status_hint),
            )
        }

        return if (ClipRepository.sessionCaptureConfirmed.get()) {
            CaptureStatus(
                active = true,
                statusText = context.getString(R.string.settings_status_active),
            )
        } else {
            CaptureStatus(
                active = true,
                statusText = context.getString(R.string.settings_status_module_loaded),
                hintText = context.getString(R.string.settings_status_module_loaded_hint),
            )
        }
    }
}
