#!/bin/bash
# SPDX-License-Identifier: GPL-3.0-or-later
set -euo pipefail
cd "$(dirname "$0")"
sdk="${ANDROID_SDK_ROOT:-${ANDROID_HOME:?Set ANDROID_HOME or ANDROID_SDK_ROOT}}"
bt="$sdk/build-tools/35.0.0"
out="$PWD/build"
mkdir -p "$out/classes" "$out/dex"
javac -source 8 -target 8 -bootclasspath "$sdk/platforms/android-36/android.jar" -d "$out/classes" src/local/webviewkeyboardprobe/MainActivity.java
"$bt/d8" --lib "$sdk/platforms/android-36/android.jar" --output "$out/dex" "$out"/classes/local/webviewkeyboardprobe/*.class
cat > "$out/AndroidManifest.xml" <<'EOF'
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="local.webviewkeyboardprobe" android:versionCode="1" android:versionName="1">
  <uses-sdk android:minSdkVersion="28" android:targetSdkVersion="36" />
  <application android:theme="@android:style/Theme.Material.Light.NoActionBar" android:label="WebView keyboard probe" android:allowBackup="false" android:debuggable="true">
    <activity android:name="local.webviewkeyboardprobe.MainActivity" android:exported="true" android:windowSoftInputMode="adjustResize">
      <intent-filter><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LAUNCHER"/></intent-filter>
    </activity>
  </application>
</manifest>
EOF
"$bt/aapt" package -f -M "$out/AndroidManifest.xml" -I "$sdk/platforms/android-36/android.jar" -F "$out/unsigned.apk"
(cd "$out/dex" && "$bt/aapt" add "$out/unsigned.apk" classes.dex)
"$bt/zipalign" -f 4 "$out/unsigned.apk" "$out/aligned.apk"
"$bt/apksigner" sign --ks "$HOME/.android/debug.keystore" --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --out "$out/keyboard-probe.apk" "$out/aligned.apk"
