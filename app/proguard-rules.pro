# Keep the Xposed hook entry point intact - it's loaded by name via assets/xposed_init
# and instantiated reflectively by the LSPosed framework, not referenced from our own code.
-keep class com.clipvault.app.xposed.** { *; }

# Keep Room entities / DAOs
-keep class com.clipvault.app.data.** { *; }

# Keep the ContentProvider (referenced from the manifest, but be explicit since
# it's the cross-process bridge used by the Xposed hook running in system_server)
-keep class com.clipvault.app.provider.** { *; }
