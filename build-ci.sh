#!/bin/bash
# CI APK build: the same kotlinc + SDK-tools steps as build-manual.sh, but
# using the tools provided by the GitHub Actions runner.
set -e
SDK="${ANDROID_HOME:?ANDROID_HOME is not set}"
AJAR=$SDK/platforms/android-34/android.jar
BT=$SDK/build-tools/34.0.0
PROJ="$(cd "$(dirname "$0")" && pwd)"
B=$PROJ/build/manual
VERSION="${VERSION:-0.2.0}"
rm -rf "$B"
mkdir -p "$B"

echo "== 1. kotlinc =="
kotlinc -classpath "$AJAR" -jvm-target 17 -nowarn \
  -d "$B/classes.jar" \
  $(find "$PROJ/app/src/main/java" -name '*.kt')

echo "== 2. aapt2 compile + link =="
$BT/aapt2 compile --dir "$PROJ/app/src/main/res" -o "$B/res.zip"
$BT/aapt2 link -o "$B/app-unsigned.apk" \
  -I "$AJAR" \
  --manifest "$PROJ/app/src/main/AndroidManifest.xml" \
  --min-sdk-version 29 --target-sdk-version 34 \
  --version-code 2 --version-name "$VERSION" \
  "$B/res.zip"

echo "== 3. d8 =="
mkdir -p "$B/dex"
KOTLINC_BIN="$(readlink -f "$(which kotlinc)")"
KOTLIN_HOME="$(dirname "$(dirname "$KOTLINC_BIN")")"
$BT/d8 --lib "$AJAR" --min-api 29 --output "$B/dex" \
  "$B/classes.jar" "$KOTLIN_HOME/lib/kotlin-stdlib.jar"

echo "== 4. package dex, align, sign =="
cd "$B"
zip -q -j app-unsigned.apk dex/classes.dex
$BT/zipalign -f -p 4 app-unsigned.apk app-aligned.apk
KS="${KEYSTORE_PATH:-$PROJ/debug.keystore}"
$BT/apksigner sign --ks "$KS" --ks-pass pass:android --key-pass pass:android \
  --out "$PROJ/fuwa-hands-$VERSION.apk" app-aligned.apk
$BT/apksigner verify "$PROJ/fuwa-hands-$VERSION.apk"
ls -la "$PROJ/fuwa-hands-$VERSION.apk"
echo CI_BUILD_DONE
