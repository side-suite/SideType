# Build & install — Sidephone TT9 fork

Verified working on this Mac 2026-07-08 (M0). The stock upstream `assembleDebug` targets an
unreleased SDK and has a flavor bug; use the recipe below instead.

## Toolchain (installed)

- **JDK 21** to *run* Gradle (Gradle build script uses `Locale.of()`, JDK 19+ only):
  `/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`  (app still compiles to Java 17)
- **Android SDK**: `~/Library/Android/sdk`, cmdline-tools 21.0, platforms android-34/35/36,
  build-tools 37.0.0, platform-tools.
- adb + device `SP01GE260600728` authorized.

## Fork changes already applied

- `app/build.gradle`: `compileSdk 37 → 36`, `targetSdk 37 → 36` (Google isn't serving android-37 yet).

## Gotchas

- **Build only the `full` flavor.** `assembleDebug` also builds `lite`, which fails to compile
  (`OneKeyEmojiOptions` exists only in `app/src/full/…` but `main/` references it — upstream bug).
  `full` bundles all dictionaries (self-contained, ideal for device testing).
- First build is ~20 min (compiles 546 MB of dictionaries). Incremental builds are fast.
- **Never kill a running build** — let it finish.

## Build the full debug APK

```bash
export JAVA_HOME=/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME="$HOME/Library/Android/sdk"; export ANDROID_SDK_ROOT="$ANDROID_HOME"
cd /Users/oliverpalonkorpi/Projects/Sidephone/tt9
./gradlew :app:assembleFullDebug --no-daemon
# APK -> app/build/outputs/apk/full/debug/tt9-v0.0-full-debug.apk
```

## Install & enable

```bash
adb install -r app/build/outputs/apk/full/debug/tt9-v0.0-full-debug.apk
adb shell ime list -a -s | grep tt9          # io.github.sspanak.tt9/.ime.TraditionalT9
adb shell ime enable io.github.sspanak.tt9/.ime.TraditionalT9
adb shell ime set    io.github.sspanak.tt9/.ime.TraditionalT9
```

Package: `io.github.sspanak.tt9` (does not conflict with stock `com.sidephone.jaketype`).
