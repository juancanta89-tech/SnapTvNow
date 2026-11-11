#!/usr/bin/env bash
set -euo pipefail
source_dir="$(cd "$(dirname "$0")" && pwd)"
output_dir="$RUNNER_TEMP/snaptvnow-recent-movies-probe"
platform="$ANDROID_HOME/platforms/android-35/android.jar"
build_tools="$ANDROID_HOME/build-tools/35.0.0"
mkdir -p "$output_dir/classes" "$output_dir/dex"
javac -source 8 -target 8 -classpath "$platform" -d "$output_dir/classes" "$source_dir/NativeProbe.java"
jar cf "$output_dir/probe.jar" -C "$output_dir/classes" .
"$build_tools/d8" --min-api 23 --lib "$platform" --output "$output_dir/dex" "$output_dir/probe.jar"
"$build_tools/aapt2" link -I "$platform" --manifest "$source_dir/AndroidManifest.xml" -o "$output_dir/probe.apk"
(cd "$output_dir/dex" && zip -q "$output_dir/probe.apk" classes.dex)
keytool -genkeypair -keystore "$output_dir/probe.jks" -storepass android -keypass android \
    -alias androiddebugkey -dname "CN=SNAPTVNOW Native Test" -keyalg RSA -keysize 2048 -validity 2 >/dev/null 2>&1
"$build_tools/apksigner" sign --ks "$output_dir/probe.jks" --ks-pass pass:android \
    --key-pass pass:android --out "$output_dir/native-probe.apk" "$output_dir/probe.apk"
"$build_tools/apksigner" verify "$output_dir/native-probe.apk"
