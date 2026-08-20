#!/system/bin/sh
# Runs very early in boot, before the package manager's boot-time scan
# finishes. Best-effort defensive workaround for a known class of AOSP bug
# where PackageManager's on-disk parse cache (/data/system/package_cache)
# doesn't get invalidated when a systemless module swaps in a different APK
# at a /system/priv-app path it has seen before - e.g. after updating this
# module to a newer ClipVault build - which can otherwise cause anything
# from a silently-stale app to a crash at launch.
#
# This clears the whole cache directory rather than targeting only
# ClipVault's own entry: PackageManager treats it purely as a performance
# cache and regenerates it for every app on the next boot regardless, so
# clearing all of it is always safe - just marginally slows that one boot.

PKG_CACHE=/data/system/package_cache
if [ -d "${PKG_CACHE}" ]; then
    rm -rf "${PKG_CACHE:?}"/* 2>/dev/null
fi
