# Architecture

Single Gradle module `:app` (package `app.aura`), organised as independent layers. Manual dependency graph (`AuraGraph` in `AuraApp.kt`) instead of a DI framework to keep the build dependency-light.

```
core/      pure Kotlin (JVM-testable): Reading<T>, Fmt, ChargeMath, Analytics, Csv, DashLayout
settings/  DataStore-backed Settings + SettingsRepo (StateFlow)
data/      Room: samples, speed results, charge sessions; Recorder (+session tracker); CsvExport
monitor/   independent samplers: BatteryMonitor, SystemMonitor, NetworkMonitor + LatencyProbe, SpeedTester, SensorExplorer
service/   MonitorHub (scheduler), MonitoringService (foreground), MonitorWorker (WorkManager)
alerts/    AlertEngine + Notifier
gl/        OpenGL ES 2.0 renderer (GlEngine) and five scenes
ui/        Compose: AuraRoot (adaptive nav), components, 9 screens
```

## Principle: Reading<T>
Each measurement is `Reading.Value(value, source)` or `Reading.Unavailable(reason)`. UI shows "Not available" and, on tap, the source or reason. There is no default/simulated variant.

## Data flow
`monitor/*` sample → `MonitorHub` publishes StateFlows + in-memory histories (≤600 battery, 300 system/net) → UI collects with lifecycle awareness.
`MonitorHub` housekeeping (every 5 s) → `Recorder` (throttled by "History logging", default 30 s) → Room; and `AlertEngine.evaluate`.

## Sampling intervals (all user-configurable)
| Monitor | Default | Notes |
|---|---|---|
| Battery | 1 s | `ACTION_BATTERY_CHANGED` + `BatteryManager` properties |
| CPU/RAM/storage/display | 1 s | |
| Network traffic | 1 s | TrafficStats deltas |
| Latency probe | 10 s | 5 TCP connects + 1 uncached DNS query; paused during speed tests |
| History logging | 30 s | |
| Alerts | 5 s | + ~15 min WorkManager when UI/service inactive |
| Background (service on, screen off) | ≥10 s | stretched to save battery |
| 3D scene data | 100 ms | only while the 3D screen is resumed |

## Background execution
- Hub samples only while the app is visible (ProcessLifecycleOwner) or the user enabled the foreground service.
- Foreground service type `specialUse`, started only from the visible activity (Android 15 rule), user-controlled, permanent notification.
- `MonitorWorker` (15 min periodic) records a sample and evaluates alerts if the process was killed.

## 3D engine
One `GLSurfaceView` (ES 2.0, additive blending, single shader). Scenes implement `GlScene.build()` which emits triangles/lines/points each frame from `SceneData` (real readings; `null` = unavailable → scene shows dim idle state). Style (hologram / wireframe / solid) scales face/edge/point alpha. Rendering is capped (~45 fps) and runs only while the screen is RESUMED.

## Charts
Custom Compose-canvas `Chart` (line/area/bars, touch scrubbing, tooltips) rather than a third-party library: no extra dependency risk and full control over honest "no data" states.

## Adaptive layout
≥600 dp wide: scrollable navigation rail (all 9 screens); otherwise bottom bar + "More". Grid columns 2/3/4 by width. Dashboard spans (S/M/L) map to the grid via `DashCards.effectiveSpan`.
