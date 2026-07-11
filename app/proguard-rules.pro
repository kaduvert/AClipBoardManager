# Keep the Xposed hook entry point intact - it's loaded by name via assets/xposed_init
# and instantiated reflectively by the LSPosed framework, not referenced from our own code.
-keep class com.clipvault.app.xposed.** { *; }

# Modern libxposed API (see ClipboardHookModern) - official recommended rules.
-dontwarn io.github.libxposed.annotation.**
-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}

# Keep Room entities / DAOs
-keep class com.clipvault.app.data.** { *; }

# Room 3.0's own recommended rule for its generated database implementation.
-keep class * extends androidx.room3.RoomDatabase {
    <init>();
}

# Keep the ContentProvider (referenced from the manifest, but be explicit since
# it's the cross-process bridge used by the Xposed hook running in system_server)
-keep class com.clipvault.app.provider.** { *; }
