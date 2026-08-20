#!/usr/bin/env bash
# Builds the flashable ClipVault root/priv-app module zip.
#
# Usage:
#   ./gradlew assembleRelease -Pclipvault.useRoot=true      # from the repo root, first
#   module/build-module.sh [path/to/some-release.apk]       # then this
#
# If no APK path is given, this looks for exactly one *.apk under
# app/build/outputs/apk/release/ and uses that.
#
# This does NOT invoke Gradle itself, on purpose: building the actual APK
# needs the Android SDK and Google's Maven repo, which may not be reachable
# from every environment this script runs in (sandboxes, minimal CI images,
# etc.), whereas everything below only needs a POSIX-ish shell, zip, and
# grep/sed - so it's kept as its own dependency-free step you run once you
# already have a built APK, rather than a fancier Gradle task wired through
# AGP's variant APIs.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
PKG="com.clipvault.app"

STAGE_DIR="$(mktemp -d)"
trap 'rm -rf "${STAGE_DIR}"' EXIT

# --- find the APK ------------------------------------------------------
APK_PATH="${1:-}"
if [ -z "${APK_PATH}" ]; then
    candidates=()
    while IFS= read -r -d '' f; do
        candidates+=("${f}")
    done < <(find "${REPO_ROOT}/app/build/outputs/apk/release" -maxdepth 1 -name "*.apk" -print0 2>/dev/null)

    if [ "${#candidates[@]}" -eq 0 ]; then
        echo "error: no APK found under app/build/outputs/apk/release/" >&2
        echo "  Build one first, e.g.:" >&2
        echo "    ./gradlew assembleRelease -Pclipvault.useRoot=true" >&2
        exit 1
    fi
    if [ "${#candidates[@]}" -gt 1 ]; then
        echo "error: more than one APK found there - pass the path explicitly:" >&2
        printf '  %s\n' "${candidates[@]}" >&2
        exit 1
    fi
    APK_PATH="${candidates[0]}"
fi

if [ ! -f "${APK_PATH}" ]; then
    echo "error: '${APK_PATH}' not found" >&2
    exit 1
fi
echo "Using APK: ${APK_PATH}"

# --- version sync check (warns, doesn't fail the build) -----------------
GRADLE_FILE="${REPO_ROOT}/app/build.gradle.kts"
PROP_FILE="${SCRIPT_DIR}/module.prop"

gradle_version_name=$(grep -oE 'versionName[[:space:]]*=[[:space:]]*"[^"]+"' "${GRADLE_FILE}" \
    | head -n1 | sed -E 's/.*"(.*)"/\1/')
gradle_version_code=$(grep -oE 'versionCode[[:space:]]*=[[:space:]]*[0-9]+' "${GRADLE_FILE}" \
    | head -n1 | sed -E 's/.*=[[:space:]]*//')
prop_version=$(grep '^version=' "${PROP_FILE}" | cut -d= -f2 | sed 's/^v//')
prop_version_code=$(grep '^versionCode=' "${PROP_FILE}" | cut -d= -f2)

if [ "${gradle_version_name}" != "${prop_version}" ] || [ "${gradle_version_code}" != "${prop_version_code}" ]; then
    echo "warning: module.prop (v${prop_version}, code ${prop_version_code}) doesn't" >&2
    echo "         match app/build.gradle.kts (v${gradle_version_name}, code ${gradle_version_code})." >&2
    echo "         Update module/module.prop's version/versionCode before releasing." >&2
fi

# --- stage the zip contents ---------------------------------------------
mkdir -p "${STAGE_DIR}/system/priv-app/${PKG}"
mkdir -p "${STAGE_DIR}/system/etc/permissions"
mkdir -p "${STAGE_DIR}/META-INF/com/google/android"

cp "${APK_PATH}" "${STAGE_DIR}/system/priv-app/${PKG}/$(basename "${APK_PATH}")"
cp "${SCRIPT_DIR}/system/etc/permissions/privapp-permissions-${PKG}.xml" \
    "${STAGE_DIR}/system/etc/permissions/"
cp "${SCRIPT_DIR}/module.prop" "${STAGE_DIR}/module.prop"
cp "${SCRIPT_DIR}/customize.sh" "${STAGE_DIR}/customize.sh"
cp "${SCRIPT_DIR}/post-fs-data.sh" "${STAGE_DIR}/post-fs-data.sh"
cp "${SCRIPT_DIR}/service.sh" "${STAGE_DIR}/service.sh"
cp "${SCRIPT_DIR}/META-INF/com/google/android/update-binary" \
    "${STAGE_DIR}/META-INF/com/google/android/update-binary"
cp "${SCRIPT_DIR}/META-INF/com/google/android/updater-script" \
    "${STAGE_DIR}/META-INF/com/google/android/updater-script"

chmod 644 "${STAGE_DIR}/system/priv-app/${PKG}/$(basename "${APK_PATH}")"
chmod 644 "${STAGE_DIR}/system/etc/permissions/privapp-permissions-${PKG}.xml"
chmod 644 "${STAGE_DIR}/module.prop"
chmod 755 "${STAGE_DIR}"/*.sh
chmod 755 "${STAGE_DIR}/META-INF/com/google/android/update-binary"
chmod 644 "${STAGE_DIR}/META-INF/com/google/android/updater-script"

# --- zip it up -----------------------------------------------------------
OUT_ZIP="${REPO_ROOT}/ClipVault-root-v${gradle_version_name:-unknown}.zip"
rm -f "${OUT_ZIP}"
( cd "${STAGE_DIR}" && zip -r -X "${OUT_ZIP}" . -x '.*' >/dev/null )

echo "Wrote ${OUT_ZIP}"
echo "Flash it from Magisk Manager or KernelSU Manager's module installer, then reboot."
