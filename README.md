# AURA 2.0 — Advanced Android Intelligence & Visualisation Studio

Real-measurement device monitor for the Samsung Galaxy Tab A11 (works on any Android 12+ phone/tablet; targets Android 15, runs on 16).
Kotlin · Jetpack Compose · Room · WorkManager · OpenGL ES 2.0 · custom Compose-canvas charts.

**Rule of the app:** every number is measured on the device, or the UI says "Not available on this device" (tap a value to see its data source / reason). No simulated data anywhere.

> **Build status:** the source was written without access to the Android SDK / Maven, so the **APK has not been built yet** and the app has not been run on a device.
> The pure-logic layer is compiled and unit-tested (20 tests). See `docs/TESTING_REPORT.md` and `docs/STATUS.md`.

## Get the APK (pick one)

### A. GitHub Actions (no local setup)
1. Create a GitHub repo and push this folder.
2. Actions → **Build AURA APK** → Run workflow (it also runs on every push).
3. Download the `aura-apks` artifact → `app-debug.apk` (and `app-release.apk`, debug-signed so it installs).

### B. Android Studio
1. Install Android Studio (Ladybug or newer, JDK 17+ bundled).
2. File → Open → this folder; let Gradle sync (needs internet for dependencies).
3. Build → Build APK(s) → `app/build/outputs/apk/debug/app-debug.apk`.

### C. Command line
```
export ANDROID_HOME=~/Android/Sdk      # platform 36 + build-tools installed
./gradlew testDebugUnitTest assembleDebug
```

## Install on the Tab A11
1. Copy `app-debug.apk` to the tablet (USB, Drive, etc.).
2. Settings → Apps → special access → *Install unknown apps* → allow your file manager.
3. Open the APK and install. Or: `adb install -r app-debug.apk`.
4. First launch: allow notifications (for alerts). Optional: Phone permission on the Network screen to see 4G/5G label.

## First things to check on the device
1. **Charging screen** while plugged in: is *Current* and *Power* shown? Tap them to read the data source. If "Not available", Samsung's fuel gauge does not expose it — that is reported, not faked.
2. **Hardware → CPU**: shows whether utilisation, a frequency index, or nothing is readable (see `docs/DEVICE_LIMITATIONS.md`).
3. **3D Lab**: confirm each scene renders and responds to touch.
4. Leave it a couple of days (Settings → keep periodic check on) to populate Health/History and degradation estimates.

## Docs
- `docs/ARCHITECTURE.md` — modules, data flow, sampling intervals
- `docs/DEVICE_LIMITATIONS.md` — Android restrictions and the alternative used for each
- `docs/STATUS.md` — requirement-by-requirement completed / unfinished record
- `docs/TESTING_REPORT.md` — what was and was not verified
