package com.clipvault.app.xposed

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

/**
 * Classic-API (rovo89-style, API level 82) hook, loaded by LSPosed/Vector into
 * system_server ("android" package, per assets/xposed_init scope). Hooks every
 * overload of ClipboardService#setPrimaryClip it can find (via [ClipCapture])
 * and forwards captured plain text to ClipboardProvider.
 *
 * Kept alongside the modern-API entry point ([ClipboardHookModern]) rather
 * than replaced by it: this classic API has been stable for years and works
 * on every Xposed-family framework in existence, while the modern API is
 * still a moving target (see ClipboardHookModern's doc comment). Shipping
 * both costs nothing - a framework that only understands one of the two
 * loading mechanisms simply ignores the other's manifest/metadata.
 *
 * This is deliberately defensive: system_server crashing takes the whole
 * device down with it, so every hook body is wrapped and never rethrows.
 */
class ClipboardHook : IXposedHookLoadPackage {

    private fun log(msg: String) = XposedBridge.log("ClipVault (classic API): $msg")

    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        if (lpparam.packageName.startsWith("com.clipvault.app")) {
            hookSelfStatusMarker(lpparam)
            return
        }
        if (lpparam.packageName != "android") return

        try {
            val methods = ClipCapture.findSetPrimaryClipMethods(lpparam.classLoader, ::log)
            if (methods.isEmpty()) {
                log("no setPrimaryClip overloads found, hook aborted")
                return
            }
            methods.forEach { method ->
                try {
                    XposedBridge.hookMethod(method, afterHook)
                } catch (t: Throwable) {
                    log("could not hook $method: $t")
                }
            }
            log("clipboard hook installed on ${methods.size} overload(s)")
        } catch (t: Throwable) {
            log("failed to install clipboard hook: $t")
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
            log("could not install self-status marker: $t")
        }
    }

    private val afterHook = object : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) {
            ClipCapture.captureAndForward(param.args, ::log)
        }
    }
}
