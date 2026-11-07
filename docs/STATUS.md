# Implementation status

Legend: ✅ implemented (source written) · 🧪 unit-tested on JVM · ⚠️ implemented but **not compiled/run on a device** · ❌ not done

**Nothing below has been run on a device or built into an APK** — the authoring environment had no Android SDK/Maven access. Everything marked ⚠️ needs a first build + device pass (see `docs/TESTING_REPORT.md`). Expect to fix some compile errors on first build.

## 1. Charging intelligence
- ✅⚠️ Level, status, source, voltage, current, power (V×I), temperature, health, technology, cycles (A14+), SoH (A14+), charge counter, est. full capacity, policy, power class (inferred), ETA to full / to empty (system estimate, else computed)
- ✅⚠️ Graphs: power, voltage, current, temperature (live) · 🧪 power/ETA/unit-sign calibration maths
- ✅🧪 Efficiency: adapter-rating share (lower bound) + coulomb self-check, limitations stated
- ❌ Charger-side power (impossible, documented)

## 2. Network
- ✅⚠️ Live traffic, connection type, signal (when provided), Wi-Fi link rate, OS capacity estimate, IPs, latency, jitter, probe loss, DNS time, stability
- ✅⚠️ Speed test (ping, jitter, download, upload), history chart/list, server choice (Cloudflare + custom https)
- ❌ Public IP (privacy), ICMP ping, bundled third-party server list

## 3. Hardware
- ✅⚠️ CPU (model, ABI, cores, max clocks, load/index, per-core freq, thermal), RAM (+swap), storage, display, sensors list + live explorer
- ❌ Panel technology (not exposed)

## 4. 3D engine
- ✅⚠️ Battery (liquid level, particles, temp colour), Charging (rings/helix/streams), Network globe (packets, latency delay), CPU hologram, RAM grid; rotate/zoom gestures; 3 styles
- ❌ Not yet seen on a GPU: shader/geometry correctness and performance on Mali-G57 are unverified

## 5. Battery health
- ✅⚠️ Temperature/voltage/level trends, sessions, speed comparison, degradation estimate 🧪, warnings 🧪, usage pattern by hour

## 6. Dashboard
- ✅🧪⚠️ 14 cards, animated battery ring, show/hide/add/resize/reorder (‹ › buttons), persisted layout
- ❌ Drag-and-drop reordering

## 7. History
- ✅⚠️ Raw / hourly / daily / weekly stats, session & speed history, range + earlier/later date filter, CSV export (SAF, streamed)
- ❌ Calendar date-picker (range chips + stepper used)

## 8. Alerts
- ✅⚠️ Temperature, level, slow charging, connection drop, low speed, storage; notifications; foreground service; WorkManager

## 9. Personalisation
- ✅⚠️ Accent, 3D style, graph style, widget sizes, refresh intervals, units, rated capacity / adapter rating

## 10–12. Engineering & deliverables
- ✅ Kotlin, Compose, Room, WorkManager, BatteryManager, ConnectivityManager, sensors, OpenGL ES, custom charts
- ✅ Android Studio project, docs, CI workflow that builds the APK
- ❌ APK, on-device test report (to be produced by the first CI/Studio build)
