#!/system/bin/sh
# Runs later in boot (the "late_start" service stage), by which point a
# per-user data directory can actually exist to fix. Best-effort defensive
# fix for a known class of AOSP bug on some OEM builds where a data
# directory created before an app was recognized as a priv-app ends up with
# the wrong SELinux label, which can then block that app from using it.
#
# Only realistically relevant if ClipVault was previously installed as a
# normal app (e.g. you're switching from the Xposed build) before this
# module was ever flashed; a fresh, module-first install shouldn't hit this,
# but restorecon-ing an already-correct directory is a harmless no-op.

PKG=com.clipvault.app
for path in /data/user_de/0/"${PKG}" /data/user/0/"${PKG}" /data/data/"${PKG}"; do
    [ -d "${path}" ] && restorecon -RDv "${path}" 2>/dev/null
done
