# Testing report

## What was actually verified
| Check | Result |
|---|---|
| Pure-logic layer (`core/*`) compiled with Kotlin 2.1.20 | ✅ clean |
| JVM unit tests (JUnit 4.13.2): ChargeMath, current-unit/sign calibrator, ETA maths, jitter/median, degradation estimate, coulomb self-check, temperature warnings, CSV escaping, dashboard layout (move/resize/hide/add/decode) | ✅ **20/20 pass** |
| Whole source tree parsed by the Kotlin compiler | ✅ no syntax errors. (Type-checking is **not** possible offline: ~2,700 "unresolved reference" results are the missing Android/Compose/Room libraries, not verified bugs) |

## What was NOT verified (be aware)
- ❌ Gradle build / APK: Maven Central, Google Maven, Android SDK and Gradle hosts were blocked in the authoring environment.
- ❌ Any run on the Galaxy Tab A11 or an emulator. So per feature: data-source correctness, permission-denial paths, memory/battery cost and the 3D shaders are **untested**.
- ❌ Room schema/KSP generation, WorkManager scheduling, foreground-service start on Android 15/16.

## How to complete QA (per feature)
1. Push to GitHub → Actions builds APK and runs `testDebugUnitTest` + lint. Fix compile errors if any.
2. Install on the Tab A11. For each screen, tap values to read sources; record which show "Not available".
3. Permission denial: deny notifications / phone state and confirm UI explains (Alerts and Network screens have prompts).
4. Charging: plug in a known charger; compare W with a USB power meter if you have one (battery-side will be lower than meter reading).
5. Network: compare the speed test with another test app; run with and without VPN; toggle airplane mode to check the drop alert.
6. 3D Lab: check each scene, rotate/zoom, watch for dropped frames; try "Wireframe" if slow.
7. Battery/memory: leave continuous monitoring on for 8 h; check Settings → Battery usage for AURA.
8. Fill in the table below and update `docs/STATUS.md`.

| Feature | Data source verified | Unavailable handled | Permission denial | Perf | Notes |
|---|---|---|---|---|---|
| Charging | | | | | |
| Network | | | | | |
| Hardware | | | | | |
| 3D | | | | | |
| Health/History | | | | | |
| Alerts | | | | | |
