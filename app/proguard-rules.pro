# Rules that apply no matter which capture flavor is built - see
# proguard-rules-xposed.pro and proguard-rules-root.pro (app/build.gradle.kts
# picks exactly one of the two, alongside this file, based on clipvault.useRoot).

# Keep Room entities / DAOs
-keep class com.clipvault.app.data.** { *; }

# Room 3.0's own recommended rule for its generated database implementation.
-keep class * extends androidx.room3.RoomDatabase {
    <init>();
}
