#!/bin/bash
# Manual APK build (Gradle's daemon does not survive this VM, so build directly
# with kotlinc + the SDK command-line tools).
set -e
export JAVA_HOME=~/workspace/tools/jdk21
export PATH=$JAVA_HOME/bin:$PATH
SDK=~/workspace/tools/android-sdk
AJAR=$SDK/platforms/android-34/android.jar
BT=$SDK/build-tools/34.0.0
PROJ=~/workspace/fuwa-hands-android
B=$PROJ/build/manual
rm -rf "$B"
mkdir -p "$B"

echo "== 1. kotlinc =="
~/workspace/tools/kotlinc/bin/kotlinc \
  -classpath "$AJAR" -jvm-target 17 -nowarn \
  -d "$B/classes.jar" \
  $(find "$PROJ/app/src/main/java" -name '*.kt')

echo "== 2. aapt2 compile + link =="
$BT/aapt2 compile --dir "$PROJ/app/src/main/res" -o "$B/res.zip"
$BT/aapt2 link -o "$B/app-unsigned.apk" \
  -I "$AJAR" \
  --manifest "$PROJ/app/src/main/AndroidManifest.xml" \
  --min-sdk-version 29 --target-sdk-version 34 \
  --version-code 2 --version-name 0.2.0 \
  "$B/res.zip"

echo "== 3. d8 =="
mkdir -p "$B/dex"
$BT/d8 --lib "$AJAR" --min-api 29 --output "$B/dex" \
  "$B/classes.jar" ~/workspace/tools/kotlinc/lib/kotlin-stdlib.jar

echo "== 4. package dex, align, sign =="
cd "$B"
zip -q -j app-unsigned.apk dex/classes.dex
$BT/zipalign -f -p 4 app-unsigned.apk app-aligned.apk
# Signing key lives OUTSIDE the wiped build dir so every build shares one
# signature and updates install in place. (v0.1.0 was signed with a throwaway
# key inside the build dir, so v0.2.0 needs a one-time reinstall.)
KS="$PROJ/debug.keystore"
if [ ! -f "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -alias androiddebugkey \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass android -keypass android \
    -dname "CN=Android Debug,O=Android,C=US" >/dev/null 2>&1
fi
$BT/apksigner sign --ks "$KS" --ks-pass pass:android --key-pass pass:android \
  --out "$PROJ/fuwa-hands-0.2.0.apk" app-aligned.apk
$BT/apksigner verify "$PROJ/fuwa-hands-0.2.0.apk"
ls -la "$PROJ/fuwa-hands-0.2.0.apk"
echo MANUAL_BUILD_DONE
