package com.clipvault.app.xposed

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import com.clipvault.app.provider.ClipboardProvider
import java.lang.reflect.Method
import java.util.concurrent.Executors

/**
 * Everything both hook implementations need: finding every overload of
 * ClipboardService#setPrimaryClip, pulling a Context out of system_server,
 * extracting plain text from a captured ClipData, and forwarding it to the
 * app through [ClipboardProvider]. Kept in one place so the classic-API and
 * modern-API entry points can't drift out of sync.
 */
internal object ClipCapture {

    /**
     * Finds every declared `setPrimaryClip` overload on ClipboardService, and
     * on any nested class (some AOSP forks implement the Binder stub as an
     * inner class rather than directly on ClipboardService). Pure reflection,
     * independent of which Xposed API generation ends up registering hooks on
     * the results.
     */
    fun findSetPrimaryClipMethods(classLoader: ClassLoader?, logError: (String) -> Unit): List<Method> {
        val serviceClass = try {
            Class.forName("com.android.server.clipboard.ClipboardService", false, classLoader)
        } catch (t: Throwable) {
            logError("ClipboardService class not found: $t")
            return emptyList()
        }

        val candidates = buildList {
            add(serviceClass)
            addAll(serviceClass.declaredClasses)
        }
        val seenSignatures = mutableSetOf<String>()
        val methods = mutableListOf<Method>()

        candidates.forEach { clazz ->
            clazz.declaredMethods
                .filter { it.name == "setPrimaryClip" }
                .forEach { method ->
                    val signature = "${clazz.name}#${method.name}(${method.parameterTypes.joinToString { p -> p.name }})"
                    if (seenSignatures.add(signature)) {
                        method.isAccessible = true
                        methods.add(method)
                    }
                }
        }
        return methods
    }

    @Volatile
    private var cachedSystemContext: Context? = null

    fun resolveSystemContext(logError: (String) -> Unit): Context? {
        cachedSystemContext?.let { return it }
        return try {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val currentActivityThread = activityThreadClass
                .getMethod("currentActivityThread")
                .invoke(null)
            val ctx = activityThreadClass
                .getMethod("getSystemContext")
                .invoke(currentActivityThread) as? Context
            cachedSystemContext = ctx
            ctx
        } catch (t: Throwable) {
            logError("could not resolve system context: $t")
            null
        }
    }

    /**
     * Single-threaded executor for clip delivery. Using one thread (rather than
     * bare [Thread].start()) guarantees that clips are delivered to the app in the
     * same order they were observed in system_server: two rapid clipboard writes A
     * then B cannot arrive as B then A at the ContentProvider. The daemon flag
     * means this thread never prevents system_server from exiting (which it
     * wouldn't do anyway, but belt-and-suspenders in a process we don't own).
     */
    private val deliveryExecutor by lazy {
        Executors.newSingleThreadExecutor { r ->
            Thread(r, "ClipVault-delivery").also { it.isDaemon = true }
        }
    }

    /**
     * Looks for a [ClipData] among the hooked method's arguments and, if one
     * with plain text is found, enqueues it for ordered delivery to the app.
     * Safe to call from any thread; the calling thread is never blocked by the
     * IPC to the app.
     *
     * This app is plain-text-only: only [android.content.ClipData.Item.text] is
     * considered.  [android.content.ClipData.Item.coerceToText] is deliberately
     * not used here - it can read a ContentProvider stream synchronously on the
     * calling thread, which is a live system_server clipboard call.  Instead, a
     * safe fallback chain is used: item.text first (plain/HTML text), then
     * item.uri.toString() (captures file paths and content URIs as opaque strings
     * without opening any stream).  Intent clips are ignored - not useful in
     * clipboard history.
     */
    fun captureAndForward(args: Array<*>, logError: (String) -> Unit) {
        try {
            val clipData = args.firstOrNull { it is ClipData } as? ClipData ?: return
            if (clipData.itemCount <= 0) return

            val item = clipData.getItemAt(0)
            val text = item.text?.toString()?.takeIf { it.isNotBlank() }
                ?: item.uri?.toString()?.takeIf { it.isNotBlank() }
                ?: return

            // Stamp the capture time before enqueueing so the delivery side can
            // apply the privacy-window check against the actual observation time,
            // not the (potentially delayed) delivery time.
            val capturedAt = System.currentTimeMillis()
            forwardToApp(text, capturedAt, logError)
        } catch (t: Throwable) {
            logError("error handling clipboard change: $t")
        }
    }

    private fun forwardToApp(text: String, capturedAt: Long, logError: (String) -> Unit) {
        val ctx = resolveSystemContext(logError) ?: return
        deliveryExecutor.execute {
            try {
                val values = ContentValues().apply {
                    put(ClipboardProvider.COLUMN_CONTENT, text)
                    put(ClipboardProvider.COLUMN_CAPTURED_AT, capturedAt)
                }
                ctx.contentResolver.insert(ClipboardProvider.CONTENT_URI, values)
            } catch (t: Throwable) {
                logError("failed to forward clip to app: $t")
            }
        }
    }
}
