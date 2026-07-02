package com.clipvault.app.xposed

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import com.clipvault.app.provider.ClipboardProvider
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

/**
 * Loaded by LSPosed into system_server ("android" package, per assets/xposed_init
 * scope). Hooks every overload of ClipboardService#setPrimaryClip it can find and
 * forwards captured plain text to [ClipboardProvider] in our own app process.
 *
 * This is deliberately defensive: system_server crashing takes the whole device
 * down with it, so every hook body is wrapped and never rethrows.
 */
class ClipboardHook : IXposedHookLoadPackage {

    private val hookedSignatures = mutableSetOf<String>()
    private var systemContext: Context? = null

    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        if (lpparam.packageName.startsWith("com.clipvault.app")) {
            hookSelfStatusMarker(lpparam)
            return
        }
        if (lpparam.packageName != "android") return

        try {
            val serviceClass = XposedHelpersCompat.findClassSafe(
                "com.android.server.clipboard.ClipboardService",
                lpparam.classLoader
            ) ?: run {
                XposedBridge.log("ClipVault: ClipboardService class not found, hook aborted")
                return
            }

            hookSetPrimaryClipMethods(serviceClass)

            // Some AOSP forks implement the Binder stub as a nested class rather
            // than directly on ClipboardService - sweep declared inner classes too.
            serviceClass.declaredClasses.forEach { inner ->
                hookSetPrimaryClipMethods(inner)
            }

            XposedBridge.log("ClipVault: clipboard hook installed")
        } catch (t: Throwable) {
            XposedBridge.log("ClipVault: failed to install clipboard hook: $t")
        }
    }

    private fun hookSelfStatusMarker(lpparam: LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.clipvault.app.xposed.HookStatus",
                lpparam.classLoader,
                "isActive",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.result = true
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log("ClipVault: could not install self-status marker: $t")
        }
    }

    private fun hookSetPrimaryClipMethods(clazz: Class<*>) {
        clazz.declaredMethods
            .filter { it.name == "setPrimaryClip" }
            .forEach { method ->
                val signature = "${clazz.name}#${method.name}(${method.parameterTypes.joinToString { p -> p.name }})"
                if (!hookedSignatures.add(signature)) return@forEach
                try {
                    method.isAccessible = true
                    XposedBridge.hookMethod(method, afterHook)
                    XposedBridge.log("ClipVault: hooked $signature")
                } catch (t: Throwable) {
                    XposedBridge.log("ClipVault: could not hook $signature: $t")
                }
            }
    }

    private val afterHook = object : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) {
            try {
                val clipData = param.args.firstOrNull { it is ClipData } as? ClipData ?: return
                if (clipData.itemCount <= 0) return
                val item = clipData.getItemAt(0)

                val text = item.text?.toString()?.takeIf { it.isNotBlank() }
                    ?: resolveSystemContext()?.let { ctx ->
                        runCatching { item.coerceToText(ctx)?.toString() }.getOrNull()
                    }

                if (text.isNullOrBlank()) return
                forwardToApp(text)
            } catch (t: Throwable) {
                XposedBridge.log("ClipVault: error handling clipboard change: $t")
            }
        }
    }

    private fun resolveSystemContext(): Context? {
        systemContext?.let { return it }
        return try {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val currentActivityThread = activityThreadClass
                .getMethod("currentActivityThread")
                .invoke(null)
            val ctx = activityThreadClass
                .getMethod("getSystemContext")
                .invoke(currentActivityThread) as? Context
            systemContext = ctx
            ctx
        } catch (t: Throwable) {
            XposedBridge.log("ClipVault: could not resolve system context: $t")
            null
        }
    }

    private fun forwardToApp(text: String) {
        val ctx = resolveSystemContext() ?: return
        // Off the calling Binder thread: this is itself a Binder call into our
        // app's process and shouldn't hold up the original clipboard write.
        Thread {
            try {
                val values = ContentValues().apply {
                    put(ClipboardProvider.COLUMN_CONTENT, text)
                }
                ctx.contentResolver.insert(ClipboardProvider.CONTENT_URI, values)
            } catch (t: Throwable) {
                XposedBridge.log("ClipVault: failed to forward clip to app: $t")
            }
        }.start()
    }
}

/** Small local helper so a missing class degrades gracefully instead of throwing. */
private object XposedHelpersCompat {
    fun findClassSafe(name: String, loader: ClassLoader?): Class<*>? =
        try {
            Class.forName(name, false, loader)
        } catch (t: Throwable) {
            null
        }
}
