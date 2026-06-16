#!/usr/bin/env bash
#
# Build Fcitx5 Android APK — arm64-v8a only
#
# Usage:
#   ./build-arm64.sh            # debug build
#   ./build-arm64.sh release    # release build (requires signing config)
#
# Output:
#   app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
#   app/build/outputs/apk/release/app-arm64-v8a-release.apk (unsigned unless SIGN_KEY_* vars are set)
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

BUILD_TYPE="${1:-debug}"

# --- Java 17 (required) ---
if [ -d "/usr/lib/jvm/java-17-openjdk" ]; then
    export JAVA_HOME="/usr/lib/jvm/java-17-openjdk"
elif command -v java &>/dev/null; then
    java_version=$(java -version 2>&1 | head -1 | grep -oP '\d+' | head -1)
    if [ "$java_version" -lt 17 ]; then
        echo "ERROR: Java 17+ required, found Java $java_version"
        exit 1
    fi
else
    echo "ERROR: Java not found"
    exit 1
fi

echo "==> Building $BUILD_TYPE APK (arm64-v8a only)"
echo "    JAVA_HOME=$JAVA_HOME"

# Only build arm64-v8a native libs
export BUILD_ABI="arm64-v8a"

case "$BUILD_TYPE" in
    release)
        ./gradlew :app:assembleRelease
        APK_PATH="app/build/outputs/apk/release/app-arm64-v8a-release.apk"
        ;;
    debug|*)
        ./gradlew :app:assembleDebug
        APK_PATH="app/build/outputs/apk/debug/app-arm64-v8a-debug.apk"
        ;;
esac

echo ""
echo "==> Build complete!"
echo "    APK: $SCRIPT_DIR/$APK_PATH"
echo "    Size: $(du -h "$APK_PATH" | cut -f1)"
echo ""
echo "==> Install with:"
echo "    adb install $APK_PATH"
