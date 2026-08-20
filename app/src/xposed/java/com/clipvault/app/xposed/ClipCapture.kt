package com.clipvault.app.xposed

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import com.clipvault.app.provider.ClipboardProvider
import java.lang.reflect.Method

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
     * Looks for a [ClipData] among the hooked method's arguments and, if one
     * with usable plain text is found, forwards it to the app. Safe to call
     * from any thread; the actual IPC to the app happens on a new thread so
     * the caller (a live clipboard write, mid system_server call) is never
     * held up by it.
     */
    fun captureAndForward(args: Array<*>, logError: (String) -> Unit) {
        try {
            val clipData = args.firstOrNull { it is ClipData } as? ClipData ?: return
            if (clipData.itemCount <= 0) return
            val item = clipData.getItemAt(0)

            val text = item.text?.toString()?.takeIf { it.isNotBlank() }
                ?: resolveSystemContext(logError)?.let { ctx ->
                    runCatching { item.coerceToText(ctx)?.toString() }.getOrNull()
                }

            if (text.isNullOrBlank()) return
            forwardToApp(text, logError)
        } catch (t: Throwable) {
            logError("error handling clipboard change: $t")
        }
    }

    private fun forwardToApp(text: String, logError: (String) -> Unit) {
        val ctx = resolveSystemContext(logError) ?: return
        Thread {
            try {
                val values = ContentValues().apply {
                    put(ClipboardProvider.COLUMN_CONTENT, text)
                }
                ctx.contentResolver.insert(ClipboardProvider.CONTENT_URI, values)
            } catch (t: Throwable) {
                logError("failed to forward clip to app: $t")
            }
        }.start()
    }
}
