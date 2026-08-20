package com.clipvault.app.capture

import android.content.Context
import com.clipvault.app.R
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
     * HookStatus.isActive() is patched to return true, in-process, by whichever of
     * ClipboardHook/ClipboardHookModern actually loaded (see their doc comments) -
     * it's a reliable signal for "the module is active in LSPosed/EdXposed and
     * scoped onto this app's own process", regardless of which of the two APIs
     * ended up being the one the framework picked.
     */
    suspend fun checkStatus(context: Context): CaptureStatus {
        val active = runCatching { HookStatus.isActive() }.getOrDefault(false)
        return if (active) {
            CaptureStatus(
                active = true,
                statusText = context.getString(R.string.settings_status_active),
            )
        } else {
            CaptureStatus(
                active = false,
                statusText = context.getString(R.string.settings_status_inactive),
                hintText = context.getString(R.string.settings_status_hint),
            )
        }
    }
}
