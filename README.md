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


## Phase 11 — Advanced settings

The Settings tab now includes persistent DataStore-backed recording quality/audio preferences, recording countdown, floating-control and face-camera preferences, theme mode, language preference, and privacy/about information. Countdown runs after the user grants Android MediaProjection consent and immediately before the recording service starts. Theme selection applies to the app UI. The Settings screen provides English and Bengali labels.

Some preferences are intentionally disclosed as not yet connected: the selected custom folder is stored but recordings still export through MediaStore to `Movies/ScreenX`; watermark rendering is not part of the export pipeline. Language preference currently localizes the Settings screen only.

## Phase 12 — QA and APK builds

See `PHASE12-QA.md` for the automated checks, manual device test checklist, known limitations, and Termux build notes. GitHub Actions now runs domain unit tests, Android Lint, a debug APK build, and an unsigned release APK build. Download the `screenx-phase12-build-results` artifact from the completed workflow. The debug APK is suitable for local installation; the release APK is unsigned and must be signed with your own private keystore before distribution.


## Phase 13 — Production release preparation

See `PHASE13-RELEASE.md` for secure optional release signing, GitHub Actions release workflow, checksum verification, and the real-device release checklist. No signing key is included. Without all four `SCREENX_*` signing environment variables, the release APK is unsigned. Phase 13 does not claim Android build or physical-device validation until those checks are actually run.

## Phase 14 build and device validation

See [PHASE14-BUILD-AND-DEVICE-VALIDATION.md](PHASE14-BUILD-AND-DEVICE-VALIDATION.md) for local build requirements, APK verification, installation steps, and the physical-device test matrix. Helper scripts are in `scripts/`.

## Phase 15 build and device validation

See [PHASE15-BUILD-AND-DEVICE-TOOLKIT.md](PHASE15-BUILD-AND-DEVICE-TOOLKIT.md). New helpers:

- `./scripts/check-build-env.sh` — checks required local build tools.
- `./scripts/install-debug-device.sh` — verifies and installs the debug APK over ADB.
- `./scripts/capture-device-logs.sh` — collects recent app and Android crash logs.


## Phase 16

Phase 16 adds a recording-save publication failure guard and static source validation. See `PHASE16-STABILITY-AUDIT.md` and run `./scripts/validate-source.sh`. This is not a substitute for Gradle compilation or physical-device testing.

## Phase 17 — recording pipeline audit

Phase 17 hardens editor export publication and copy integrity for MediaStore (Android 10+) and legacy file destinations (Android 8–9). See `PHASE17-RECORDING-PIPELINE-AUDIT.md`. Run `./scripts/validate-source.sh` for static checks. Gradle build and physical-device tests remain required.

Phase 18 connects the Settings-selected Storage Access Framework folder to new screen recordings. See `PHASE18-CUSTOM-FOLDER-INTEGRATION.md` for behavior and limitations. Editor exports still use the default MediaStore paths, the in-app library does not yet browse arbitrary SAF trees, and watermark rendering remains unimplemented. Static validation is not a substitute for Gradle compilation or real-device testing.


Phase 19 wires selected-folder destinations into edited video export, adds a Media3 watermark overlay to edited exports when enabled, and lists MP4 documents from the selected SAF folder in Recordings. Extracted audio remains in Music/ScreenX; live recording watermarking and recursive SAF folder browsing are not included. See `PHASE19-FEATURE-INTEGRATION.md`. Static validation is not a substitute for Gradle compilation or device testing.


## Phase 20 — Full integration audit and final APK preparation

Phase 20 advances the project version to `0.20.0-phase20` (versionCode 10), adds a repeatable integration audit, and updates CI to run the audit before unit tests, Android Lint, and debug/unsigned-release APK builds. See `PHASE20-INTEGRATION-AUDIT.md`.

Run `./scripts/phase20-audit.sh` for source-level checks. For a real build, use a machine with JDK 17, Gradle 8.9, Android SDK Platform 35, and Build Tools 35.0.0, then run `gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease`. The CI artifact is named `screenx-phase20-build-results`.

**Validation honesty:** packaging and source-level checks do not prove Kotlin compilation, APK installability, runtime behavior, signing, or physical-device compatibility. Those are release gates and must be confirmed by the CI build and device test checklist. Release signing remains opt-in and requires the four `SCREENX_*` secrets; never commit a keystore or passwords.


## Phase 21 — Stability and regression validation

Phase 21 advances the app to `0.21.0-phase21` (versionCode 11), adds a dedicated static stability audit for manifest/service/provider declarations and release hygiene, expands domain regression tests, and updates CI to run the audit, unit tests, lint, and debug/unsigned-release APK builds. See `PHASE21-STABILITY-AND-DEVICE-TESTING.md`. Run `./scripts/phase21-stability-audit.sh`. The CI artifact is `screenx-phase21-build-results`. Static checks do not prove APK compilation or runtime/device behavior.

## Phase 22 — Final build and runtime readiness

Phase 22 advances the app to `0.22.0-phase22` (versionCode 12), adds
`scripts/phase22-build-audit.sh`, and consolidates the primary CI workflow around unit
tests, Android Lint, debug/release APK assembly, debug signature verification, and
SHA-256 checksums. See `PHASE22-FINAL-BUILD-AND-RUNTIME-TESTING.md` and
`PHASE22-DEVICE-TEST-RESULTS.md`. Actual compilation and device tests remain pending
until a CI run and Android device validation are completed.

    ## Phase 23 — Stability, compatibility and bug prevention

    Version `0.23.0-phase23` (versionCode 13). Validates persisted recording settings
    before recording resources are allocated, adds JVM regression tests and a stability
    audit, and updates CI gates. See `PHASE23-STABILITY-COMPATIBILITY-AND-BUGFIX.md`
    and `PHASE23-DEVICE-COMPATIBILITY-RESULTS.md`. Build and device validation remain
    pending CI and physical-device evidence.
    