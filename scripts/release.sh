#!/bin/sh
# 一条命令出正式版 APK：构建 → 校验签名 → 投到共享目录。
# 用法：cd /workspace/Eta && scripts/release.sh
# 签名信息读仓库根 keystore.properties（.gitignore 已忽略），CI 仍走环境变量。
set -eu

cd "$(dirname "$0")/.."
ROOT=$(pwd)

export ANDROID_SDK_ROOT=${ANDROID_SDK_ROOT:-/workspace/android-sdk}
export ANDROID_HOME=$ANDROID_SDK_ROOT
export JAVA_HOME=${JAVA_HOME:-/usr/lib/jvm/java-25-openjdk-arm64}
export PATH=$JAVA_HOME/bin:$PATH

SHARED=${SHARED_DIR:-/storage/emulated/0/共享目录}
APP_ID_SUFFIX=${ETA_APP_ID_SUFFIX:-.stable}

if [ ! -f keystore.properties ]; then
    echo "缺少 keystore.properties（storeFile/storePassword/keyAlias/keyPassword）" >&2
    exit 1
fi

echo "== 构建正式版 (applicationId 后缀 ${APP_ID_SUFFIX}) =="
# 本机（aarch64）上 optimizeReleaseResources 会静默产出空结果，导致 APK 缺
# AndroidManifest.xml 与 resources.arsc，必须关掉资源优化。CI 不走本脚本。
./gradlew --no-daemon :app:assembleRelease \
    -Peta.appIdSuffix="$APP_ID_SUFFIX" \
    -Pandroid.enableResourceOptimizations=false

APK=$(ls -t app/build/outputs/apk/release/*.apk 2>/dev/null | head -1)
[ -n "$APK" ] || { echo "没有产出 APK" >&2; exit 1; }

# 完整性门禁：缺 manifest 或 arsc 的包装不上，直接判失败
if ! unzip -l "$APK" | grep -q "AndroidManifest.xml" || ! unzip -l "$APK" | grep -q "resources.arsc"; then
    echo "产物残缺：缺少 AndroidManifest.xml 或 resources.arsc" >&2
    exit 1
fi

APKSIGNER=$(ls "$ANDROID_SDK_ROOT"/build-tools/*/apksigner 2>/dev/null | tail -1)
if [ -n "$APKSIGNER" ]; then
    echo "== 签名校验 =="
    "$APKSIGNER" verify --print-certs "$APK" 2>/dev/null | grep -E "certificate DN|SHA-256" || true
fi

NAME=$(basename "$APK" .apk)
DEST="$SHARED/${NAME}-正式版.apk"
if [ -d "$SHARED" ]; then
    cp -f "$APK" "$DEST"
    chmod 666 "$DEST" 2>/dev/null || true
    echo "== 已投放 =="
    echo "$DEST"
fi
echo "== SHA256 =="
sha256sum "$APK"
