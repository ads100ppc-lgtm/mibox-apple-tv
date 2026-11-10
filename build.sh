#!/bin/sh
# Збирає обидві програми з вихідного коду (без Gradle). Потрібні: JDK 11+ і Android SDK (build-tools + platforms).
#   ANDROID_HOME=~/Library/Android/sdk sh build.sh
# Власний ключ підпису створюється сам у keys/ (у git не потрапляє).
set -e
cd "$(dirname "$0")"
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
BT="$(ls -d "$SDK"/build-tools/* | sort -V | tail -1)"
AJ="$(ls -d "$SDK"/platforms/android-* | sort -V | tail -1)/android.jar"
[ -x "$BT/aapt2" ] && [ -f "$AJ" ] || { echo "Не знайдено Android SDK у $SDK (задайте ANDROID_HOME)."; exit 1; }
mkdir -p keys apks
KS=keys/release.jks
[ -f "$KS" ] || keytool -genkeypair -keystore "$KS" -storepass keylauncher -keypass keylauncher -alias key \
  -keyalg RSA -keysize 2048 -validity 36500 -dname "CN=PcHome" >/dev/null 2>&1

build() { # $1 папка, $2 targetSdk, $3 apk
  rm -rf "$1/out" && mkdir -p "$1/out/cls"
  "$BT/aapt2" compile --dir "$1/res" -o "$1/out/res.zip"
  "$BT/aapt2" link -o "$1/out/base.apk" -I "$AJ" --manifest "$1/AndroidManifest.xml" --min-sdk-version 26 --target-sdk-version "$2" "$1/out/res.zip"
  javac --release 8 -Xlint:-options -cp "$AJ" -d "$1/out/cls" $(find "$1/src" -name '*.java')
  "$BT/d8" --lib "$AJ" --min-api 26 --output "$1/out" $(find "$1/out/cls" -name '*.class')
  cp "$1/out/base.apk" "$1/out/unsigned.apk" && (cd "$1/out" && zip -q -j unsigned.apk classes.dex)
  "$BT/zipalign" -f 4 "$1/out/unsigned.apk" "$1/out/aligned.apk"
  "$BT/apksigner" sign --ks "$KS" --ks-pass pass:keylauncher --key-pass pass:keylauncher --out "apks/$3" "$1/out/aligned.apk"
  echo "готово: apks/$3"
}
build launcher 28 home.apk
build keymap 30 keylauncher.apk
