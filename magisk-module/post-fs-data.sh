#!/system/bin/sh
# ClipVault priv-app promoter
# Copies the already-installed ClipVault APK into this module's system/priv-app
# tree so Magisk's systemless overlay picks it up as a privileged system app.
# Must run in post-fs-data (before Magisk performs its mount pass).

MODDIR=${0%/*}
PKG="com.clipvault.app"
DEST_DIR="$MODDIR/system/priv-app/ClipVault"
DEST_APK="$DEST_DIR/ClipVault.apk"

log() { echo "[clipvault_privapp] $1" >> "$MODDIR/install.log"; }

APK_PATH=$(pm path "$PKG" 2>/dev/null | head -n1 | sed 's/^package://')

if [ -z "$APK_PATH" ] || [ ! -f "$APK_PATH" ]; then
    log "ClipVault (package $PKG) not found on this boot - user app not installed yet, skipping."
    exit 0
fi

mkdir -p "$DEST_DIR"
cp -f "$APK_PATH" "$DEST_APK"
chmod 644 "$DEST_APK"
chown root:root "$DEST_APK"
chcon u:object_r:system_file:s0 "$DEST_APK" 2>/dev/null

log "Staged $APK_PATH -> $DEST_APK"
log "Reboot once more so Android registers ClipVault as a priv-app with the granted permission."
