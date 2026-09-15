package com.clipvault.app.root

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.clipvault.app.ClipVaultApp
import com.clipvault.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The only capture path in the root/priv-app build (see app/build.gradle.kts'
 * clipvault.useRoot flag - the LSPosed build has no equivalent of this class).
 *
 * Relies on the app having been promoted to a privileged system app holding
 * android.permission.READ_CLIPBOARD_IN_BACKGROUND by /module before this app was
 * ever installed - only that permission lets [ClipboardManager.getPrimaryClip]
 * return real data while this service, and therefore the app, isn't in the
 * foreground. Without it (module not flashed yet, or flashed but not yet
 * rebooted into) this service simply won't observe anything useful, which is
 * expected and harmless; see capture/CaptureCoordinator for the status check
 * that reports this in Settings. Started from BootCompletedReceiver and,
 * defensively, from ClipVaultApp.onCreate() - never from user interaction, since
 * there's no toggle left to start it from.
 */
class ClipboardWatcherService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var clipboardManager: ClipboardManager
    private var lastSeenContent: String? = null

    private val listener = ClipboardManager.OnPrimaryClipChangedListener {
        // Stamp the observation time immediately on the listener thread, before any
        // coroutine dispatch, so the privacy-window check in recordCapture() uses the
        // real clip-observed time rather than the (slightly later) time the coroutine
        // actually runs.  This mirrors what the LSPosed hook does with capturedAt.
        val capturedAt = System.currentTimeMillis()
        val clip = runCatching { clipboardManager.primaryClip }.getOrNull() ?: return@OnPrimaryClipChangedListener
        if (clip.itemCount <= 0) return@OnPrimaryClipChangedListener
        // coerceToText is intentionally not used - this is a plain-text-only app, and
        // coerceToText can read a ContentProvider stream synchronously on this thread.
        // Safe fallback chain: .text first, then .uri as an opaque string (captures
        // file paths and content URIs without opening any stream).
        val text = clip.getItemAt(0).text?.toString()?.takeIf { it.isNotBlank() }
            ?: clip.getItemAt(0).uri?.toString()?.takeIf { it.isNotBlank() }
            ?: return@OnPrimaryClipChangedListener
        if (text == lastSeenContent) return@OnPrimaryClipChangedListener
        lastSeenContent = text
        val repo = (application as ClipVaultApp).repository
        scope.launch { repo.recordCapture(text, capturedAt) }
    }

    override fun onCreate() {
        super.onCreate()
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        startForeground(NOTIFICATION_ID, buildNotification())
        clipboardManager.addPrimaryClipChangedListener(listener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        clipboardManager.removePrimaryClipChangedListener(listener)
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Clipboard capture",
                NotificationManager.IMPORTANCE_MIN
            ).apply { description = "Keeps ClipVault's root capture service alive" }
            manager.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.settings_status_active))
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "clipvault_capture"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            val intent = Intent(context, ClipboardWatcherService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ClipboardWatcherService::class.java))
        }
    }
}
