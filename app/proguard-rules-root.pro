# Root/priv-app build. Nothing extra needed here at the moment:
# ClipboardWatcherService and BootCompletedReceiver are both declared in
# app/src/root/AndroidManifest.xml, so AGP's default consumer rules already
# keep them, and CaptureCoordinator is referenced directly from ClipVaultApp
# and SettingsScreen rather than being loaded reflectively by name the way
# the Xposed hook classes are (see proguard-rules-xposed.pro for why that
# case needs an explicit -keep).
#
# Kept as its own file (rather than just omitted) so both capture flavors
# have a matching entry point in app/build.gradle.kts's proguardFiles(...),
# and so anything root-specific that does need a rule later has an obvious
# home.
