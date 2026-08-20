package com.clipvault.app.xposed

/**
 * Always returns false when read normally. When the LSPosed module is active
 * (it's self-scoped onto this app too, see res/values/arrays.xml), ClipboardHook
 * replaces this method's return value with true inside this app's own process,
 * so Settings can show real status instead of assuming.
 */
object HookStatus {
    @JvmStatic
    fun isActive(): Boolean = false
}
