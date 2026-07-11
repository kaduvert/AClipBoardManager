package com.clipvault.app.xposed

import android.util.Log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam

private const val TAG = "ClipVault (modern API)"

/**
 * Modern libxposed hook entry point, targeting API level 102.
 *
 * Why 102 and not 100: the user-facing "API level" the LSPosed/Vector docs
 * talk about is a separate, newer API family (`io.github.libxposed`) from the
 * classic rovo89-style one ([ClipboardHook], API 82) - it isn't a simple
 * successor number, it's a different module contract entirely (this class
 * extends [XposedModule] and registers hooks through it, rather than
 * implementing IXposedHookLoadPackage). Level 100 was the first cut of that
 * new contract, but per the libxposed project's own release notes it "was
 * never officially published" as a dependency - there's no Maven artifact for
 * it, only a specific pre-release commit some frameworks built against. 101
 * was the first version actually published to Maven Central, and it redesigned
 * hooking around an OkHttp-style interceptor chain; 102 (used here) is the
 * refinement of that same design and is what's current as of this writing.
 *
 * Framework support for 102 specifically is still settling out across the
 * post-LSPosed-archival ecosystem (Vector's fork, other continuations) - if a
 * given framework build only understands classic API 82 or modern API 100,
 * this class simply won't load there, but [ClipboardHook] (API 82) still
 * will, since we ship both side by side (see that class's doc comment).
 *
 * One correctness-critical detail specific to this design: every hook here
 * MUST call `chain.proceed()` and return its result. Skipping that would
 * silently break every clipboard write on the device instead of just
 * observing it - this hook is read-only by design, never replacing behavior.
 *
 * Note on API surface confidence: the interceptor-chain hook shape
 * (`hook(method).setPriority(...).setExceptionMode(...).intercept { chain ->
 * ... }`) and the lifecycle callback name `onSystemServerStarting` are
 * corroborated by the libxposed project's own PR discussion and generated
 * javadoc. The one spot most likely to need a small fix against your exact
 * framework/library version is the import path for [SystemServerStartingParam]
 * below - if it doesn't resolve, check
 * https://libxposed.github.io/api/io/github/libxposed/api/XposedModuleInterface.html
 * for its current nesting/package.
 */
class ClipboardHookModern : XposedModule() {

    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        super.onSystemServerStarting(param)
        try {
            // Deliberately not relying on `param` for the classloader: this
            // module instance was itself loaded by system_server's own
            // classloader when the framework instantiated it there, so
            // reaching for our own class's loader is at least as reliable as
            // any classloader accessor the param object might expose, and
            // sidesteps needing to guess that accessor's exact name.
            val classLoader = this.javaClass.classLoader

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
                            ClipCapture.captureAndForward(chain.args.toTypedArray()) { msg -> Log.w(TAG, msg) }
                            // Read-only: always let the real call through unchanged.
                            chain.proceed()
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
}
