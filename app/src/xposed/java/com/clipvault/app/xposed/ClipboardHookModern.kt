package com.clipvault.app.xposed

import android.util.Log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

private const val TAG = "ClipVault (modern API)"

class ClipboardHookModern : XposedModule() {

    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        super.onSystemServerStarting(param)
        try {
            val classLoader = param.classLoader
            val methods = ClipCapture.findSetPrimaryClipMethods(classLoader) { msg -> Log.w(TAG, msg) }
            if (methods.isEmpty()) {
                Log.w(TAG, "no setPrimaryClip overloads found, hook aborted")
                return
            }

            methods.forEach { method ->
                try {
                    hook(method)
                        .setPriority(XposedInterface.PRIORITY_DEFAULT)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept { chain ->
                            // Let the real call run first.  captureAndForward is only
                            // reached if chain.proceed() returns normally, meaning the
                            // clipboard write actually succeeded.  If it throws, the
                            // exception propagates and we never capture - a failed write
                            // should not enter history.  captureAndForward has its own
                            // try-catch and will not rethrow, so the return value from
                            // proceed() is always what the caller receives.
                            val returnValue = chain.proceed()
                            ClipCapture.captureAndForward(chain.args.toTypedArray()) { msg -> Log.w(TAG, msg) }
                            returnValue
                        }
                } catch (t: Throwable) {
                    Log.w(TAG, "could not hook $method: $t")
                }
            }
            Log.i(TAG, "clipboard hook installed on ${methods.size} overload(s)")
        } catch (t: Throwable) {
            Log.w(TAG, "failed to install clipboard hook: $t")
        }
    }

    override fun onPackageLoaded(param: PackageLoadedParam) {
        super.onPackageLoaded(param)
        if (param.packageName.startsWith("com.clipvault.app")) {
            try {
                val classLoader = param.defaultClassLoader
                val hookStatusClass = classLoader.loadClass("com.clipvault.app.xposed.HookStatus")
                val isActiveMethod = hookStatusClass.getDeclaredMethod("isActive")
                
                hook(isActiveMethod)
                    .setPriority(XposedInterface.PRIORITY_HIGHEST)
                    .intercept { chain -> true } // Force HookStatus.isActive() to return true
            } catch (t: Throwable) {
                Log.w(TAG, "could not install self-status marker: $t")
            }
        }
    }
}
