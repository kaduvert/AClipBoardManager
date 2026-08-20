package com.clipvault.app.root

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Root/priv-app build only. Starts [ClipboardWatcherService] on every boot so this
 * build behaves like the module it ships in: flash once, forget about it. Nothing
 * in Settings needs to be opened, and there's no toggle left to open Settings for
 * anyway - see capture/CaptureCoordinator.kt's onAppCreated(), which does the same
 * thing defensively for any other reason this process gets created.
 */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            ClipboardWatcherService.start(context)
        }
    }
}
