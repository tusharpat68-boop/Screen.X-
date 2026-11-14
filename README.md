# ScreenX AI - Ultimate Edition

## Build option A (recommended): GitHub Actions
1. Push this project to a GitHub repo.
2. Repo > Actions > "Build debug APK" > Run workflow (it also runs on every push).
3. Download the `screenx-debug-apk` artifact, unzip, install the APK.

## Build option B: Termux (experimental)
Official Android SDK build tools are x86_64 and do not run on ARM phones.
You need an ARM64 SDK build plus Termux's own aapt2.

    pkg update && pkg upgrade -y
    pkg install -y git openjdk-17 gradle wget unzip aapt2
    termux-setup-storage

Put an ARM64 Android SDK (platforms;android-35, build-tools 34.x) in ~/android-sdk, then in the
project folder create local.properties:

    sdk.dir=/data/data/com.termux/files/home/android-sdk

and add to ~/.gradle/gradle.properties:

    android.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2

Build:

    gradle :app:assembleDebug

APK: app/build/outputs/apk/debug/
