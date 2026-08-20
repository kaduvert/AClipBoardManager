#!/system/bin/sh
# Runs once, during install, inside Magisk/KernelSU's own module-install
# context - ui_print, MODPATH, API, ARCH etc. are already available here,
# provided by whichever manager sourced this (see Magisk's module-development
# docs for the full list); nothing needs to be redefined the way update-binary
# has to for itself.

ui_print "- ClipVault root/priv-app capture module"

if [ -n "${API}" ] && [ "${API}" -lt 29 ] 2>/dev/null; then
    ui_print "- Note: android.permission.READ_CLIPBOARD_IN_BACKGROUND was added in"
    ui_print "  Android 10 (API 29). This device reports API ${API}, below that - the"
    ui_print "  permission probably doesn't exist here. That said, on Android 8/9"
    ui_print "  background clipboard reads aren't restricted in the first place, so"
    ui_print "  ClipVault's watcher service may simply work without it anyway."
fi

ui_print "- Installing to /system/priv-app/com.clipvault.app"
ui_print "- Reboot once when this finishes, then open ClipVault and check that"
ui_print "  Settings reports the capture service as active."
