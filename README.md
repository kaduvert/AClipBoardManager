# ClipVault

A minimal, single-screen, Material You clipboard manager for rooted / LSPosed
Android devices. No `READ_CLIPBOARD`-style runtime permission dance, no ads, no
cloud sync - it watches the system clipboard at a privileged level and keeps a
local, searchable history.

- **One screen**: a search field pinned at the top, and a scrolling list of
  past clips below it. Tap any entry to put it back on the clipboard - the
  entry that's actually live on the clipboard right now is visually marked.
  Tapping never creates a duplicate row.
- **One settings screen**: private mode (pause saving), how much history to
  keep, and a clear-history action.
- **No normal clipboard permissions used.** Capture happens either via an
  LSPosed module hook (recommended) or, as a fallback, via a root-assisted
  priv-app promotion.

## How capture actually works

Android has blocked background apps from reading clipboard *content* since
Android 10 (they can see that something changed, not what it says) unless
they're the focused app, the default keyboard, or hold the signature
permission `android.permission.READ_CLIPBOARD_IN_BACKGROUND`. ClipVault
never asks for foreground focus or IME access - instead:

### Path 1 - LSPosed (recommended)

`app/src/main/java/com/clipvault/app/xposed/ClipboardHook.kt` is loaded by
LSPosed straight into **system_server** (scope `android`, declared in
`assets/xposed_init` and `res/values/arrays.xml`). It hooks every
`setPrimaryClip(...)` overload it can find on
`com.android.server.clipboard.ClipboardService` (and any nested Binder-stub
class, to be resilient to AOSP/OEM differences), pulls the plain-text item out
of the `ClipData`, and hands it to the app through a small write-only
`ContentProvider` (`provider/ClipboardProvider.kt`). That provider is guarded
by a custom `signature`-level permission - only this app's own signature, or a
caller running as the SYSTEM uid (i.e. system_server itself), can write to it.

Because the hook lives inside system_server, it sees **every** clipboard write
on the device, from any app, regardless of focus - this is the same technique
long-standing Xposed-based clipboard tools have used.

**Setup:**
1. Build and install the app normally (see below).
2. Open LSPosed Manager, enable the **ClipVault** module.
3. Under the module's scope, make sure **Android System** (`android`) is
   ticked - it should be pre-selected from `xposedscope`.
4. Reboot.
5. Open ClipVault → Settings. The capture status card should read
   **"LSPosed hook active."**

### Path 2 - Root only, no LSPosed (`/magisk-module`)

If LSPosed isn't available, ClipVault can fall back to a normal in-app
`ClipboardManager.OnPrimaryClipChangedListener` running in a foreground
service (`root/ClipboardWatcherService.kt`) - but that only receives real
clipboard *content* in the background if the app itself holds
`READ_CLIPBOARD_IN_BACKGROUND`, which is a `signature|privileged` permission
normal apps can never be granted, root or not (it can't be granted with
`pm grant`, even as root - that only works for dangerous/runtime permissions).

The included Magisk module (`/magisk-module`) gets you there the way that
permission class is actually meant to be granted: by turning ClipVault into a
**privileged system app** and adding it to the priv-app permission allowlist.
It does this systemlessly (no direct writes to /system, Magisk overlays it):

1. Install ClipVault as a normal app first (sideload the APK, or install via
   Android Studio).
2. Flash `/magisk-module` as a Magisk module (zip up its contents, or point
   Magisk's "Install from storage" at the folder) and reboot.
   - On this first boot, `post-fs-data.sh` copies your already-installed
     ClipVault APK into the module's own `system/priv-app/ClipVault/` folder.
3. Reboot **again**. This second boot is when Magisk actually mounts that
   staged APK as `/system/priv-app/ClipVault/ClipVault.apk` and Android
   registers it as a priv-app with `READ_CLIPBOARD_IN_BACKGROUND` allowlisted.
4. Open ClipVault → Settings → toggle **"Use root capture service."**

Two reboots is normal for this kind of "promote a user app to priv-app" module
- the first stages the file, the second is a real boot with it in place.

**Caveats, stated plainly:** this path depends on your ROM's clipboard-service
implementation not deviating from AOSP in a way that breaks the priv-app
permission check, and heavily customized OEM skins (MIUI, One UI, etc.) are
more likely to do that than AOSP-based/GSI ROMs. LSPosed is the more reliable
of the two paths for exactly this reason - it doesn't depend on that
permission existing/working at all, since it reads the clip before any
permission check happens.

### Private mode & history limit

Both capture paths funnel into the same `ClipRepository.recordCapture()`,
which is where private mode (skip saving entirely) and the history limit
(oldest entries beyond the limit are trimmed) are enforced - so they behave
identically regardless of which capture path is active.

## Building

Open the project root in Android Studio (Ladybug/Koala or newer) and let it
sync - it's a standard Gradle Kotlin DSL project, nothing exotic. Or from the
command line, once you've let Android Studio generate the Gradle wrapper jar
once (or run `gradle wrapper` yourself with Gradle 8.9+ installed):

```
./gradlew assembleDebug
```

The debug build installs alongside a release build (`applicationIdSuffix
".debug"`) if you ever need both side by side.

- `compileSdk` / `targetSdk`: 35 (Android 15)
- `minSdk`: 26 (Android 8) - the UI and database work fine much further back
  than the capture mechanisms do; capture itself obviously needs LSPosed or
  root regardless of API level.

## Project layout

```
app/src/main/java/com/clipvault/app/
├── ClipVaultApp.kt              application-level repository singleton
├── MainActivity.kt              two-route NavHost (main, settings)
├── data/                        Room entity/DAO/DB, DataStore settings, repository
├── provider/ClipboardProvider.kt  write-only IPC bridge used by the LSPosed hook
├── xposed/
│   ├── ClipboardHook.kt         the actual system_server hook
│   └── HookStatus.kt            in-process marker so Settings can show real status
├── root/
│   ├── ClipboardWatcherService.kt  foreground service for the root/priv-app path
│   └── CaptureStatus.kt         root detection + "which capture path is active"
└── ui/                          Compose screens, view model, Material You theme

magisk-module/                   root-mode priv-app promoter (see above)
```

## What's intentionally not here

Per the brief, this stays extremely simple: no per-entry delete/pin, no image
or rich-content clips (plain text only), no cloud sync, no widgets. Search,
tap-to-activate, private mode, a history cap, and clear-all are the whole
feature set.
