# ClipVault root/priv-app module

This is what `clipvault.useRoot=true` is for. Flash it, reboot once, and
ClipVault runs as a privileged system app holding
`android.permission.READ_CLIPBOARD_IN_BACKGROUND` - no LSPosed involved, and
after that first reboot, nothing left to do or open.

## Building and flashing

1. Build the root-flavor APK from the repo root:
   ```
   ./gradlew assembleRelease -Pclipvault.useRoot=true
   ```
2. Package the module zip:
   ```
   module/build-module.sh
   ```
   This finds the APK Gradle just produced, bakes it into
   `system/priv-app/com.clipvault.app/` alongside the permission allowlist
   and install scripts below, and writes `ClipVault-root-vX.Y.zip` at the
   repo root. It doesn't invoke Gradle itself - see the comment at the top
   of `build-module.sh` for why.
3. In Magisk Manager or KernelSU Manager, install that zip as a module.
4. Reboot once. ClipVault should now be a system app; open it and confirm
   Settings reports the capture service as active.

Do **not** also install ClipVault as a normal app on the same device - this
module ships the whole APK itself. If you'd previously installed the Xposed
build normally and are switching to this instead, uninstall that first (your
history is stored under this same package name either way, so as long as you
don't wipe app data in between, it carries over).

## Why this isn't "promote an already-installed app"

The version of this module before this rewrite worked differently: it
expected you to install ClipVault normally first, then flashed a module
whose `post-fs-data.sh` located that already-installed APK with `pm path`
and copied it into `system/priv-app/` on first boot - which only took effect
on the *second* reboot after flashing, since Magisk's own mount pass for the
first boot had already run by the time the copy happened.

This version bakes the APK into the zip at packaging time instead (see
`build-module.sh`), so Magisk/KernelSU's normal systemless-overlay mount
picks it up on the very first reboot after flashing. Nothing here reads
`pm path` or depends on install order any more.

## A note on Android version

`READ_CLIPBOARD_IN_BACKGROUND` was added in Android 10 (API 29).
`customize.sh` will warn (not refuse to install) if it detects an older
device. On Android 8/9, background clipboard reads aren't restricted in the
first place, so `ClipboardWatcherService` may simply work without this
module at all on those versions - it's Android 10+ where the restriction
this module works around actually exists.

## KernelSU

Supported, but KernelSU's own execution of `post-fs-data.sh`/`service.sh`
inside modules has, at various points, needed an extra compatibility
component installed alongside plain KernelSU for those scripts to run at all
- check KernelSU Manager/your KernelSU variant's own documentation if the
capture service doesn't come up after a reboot despite the module showing as
installed and enabled. The permission grant itself
(`system/etc/permissions/...xml` plus the baked-in APK) doesn't depend on
either of those scripts actually running - only the two defensive
workarounds above do.
