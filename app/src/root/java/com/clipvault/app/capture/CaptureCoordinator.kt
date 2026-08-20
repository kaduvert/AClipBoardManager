package com.clipvault.app.capture

import android.content.Context
import android.content.pm.PackageManager
import com.clipvault.app.R
import com.clipvault.app.root.ClipboardWatcherService

/**
 * Root/priv-app-flavor implementation. See app/src/xposed's CaptureCoordinator for
 * the other half of this pair - only one of the two is ever compiled into a given
 * build.
 *
 * Notably absent: any su/root-shell invocation. The old in-app "Use root capture
 * service" switch used libsu to check `Shell.getShell().isRoot` before letting you
 * flip it on, because flipping it on was the thing that (attempted to) promote the
 * app. That promotion now happens once, before this app is ever installed, by
 * flashing /module - so by the time this code runs, the app either already is a
 * priv-app holding READ_CLIPBOARD_IN_BACKGROUND, or it isn't, and there is nothing
 * for the running app itself to do about that either way. Checking the permission
 * directly is both simpler and a more accurate signal than a root-shell probe ever
 * was: it answers "is capture actually working", not "could this device grant it".
 */
object CaptureCoordinator {

    private const val PRIV_APP_PERMISSION = "android.permission.READ_CLIPBOARD_IN_BACKGROUND"

    /** The watcher runs as a foreground service, so Android 13+ needs this asked once. */
    const val needsNotificationPermission = true

    /**
     * Belt-and-suspenders alongside BootCompletedReceiver: also (re-)start the
     * watcher whenever this process is created for any other reason (e.g. the
     * system starting it to deliver some other component), rather than only on
     * boot. Starting an already-running foreground service is a harmless no-op
     * redelivery of onStartCommand.
     */
    fun onAppCreated(context: Context) {
        ClipboardWatcherService.start(context)
    }

    suspend fun checkStatus(context: Context): CaptureStatus {
        val granted = context.packageManager.checkPermission(
            PRIV_APP_PERMISSION,
            context.packageName,
        ) == PackageManager.PERMISSION_GRANTED

        return if (granted) {
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
