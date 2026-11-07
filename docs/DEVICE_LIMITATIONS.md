# Device & Android limitations and what AURA does instead

Target: Samsung Galaxy Tab A11 (published specs, [source](https://mobilemasr.com/en/guide/samsung-galaxy-tab-a11)): MediaTek Helio G99, 5100 mAh battery, 15 W wired charging, 8.7" 800×1340 LCD, ships with Android 15 (One UI 7), upgradable to 16. Listed sensors: accelerometer, proximity, compass. **AURA never relies on these numbers**; it reads actual values at runtime. They are only used if you type them into Settings.

Limitations below are from Android's documented behaviour. Where marked **(verify on device)** the actual behaviour on this tablet is unknown until tested.

| Feature | Restriction | AURA's approach |
|---|---|---|
| Wall-adapter output power | Not exposed to apps | Only battery-side V×I is shown and labelled so; optional comparison with the adapter rating *you* enter, labelled "lower bound" |
| True charging efficiency | Needs input power | Not computed. A *measurement self-check* compares ∫current vs charge-counter change instead |
| Charging protocol / "fast charge" | Not exposed | "Power class" inferred from battery-side watts and labelled *inferred* |
| Battery current / charge counter | `CURRENT_NOW`, `CHARGE_COUNTER` are optional OEM features; units/sign vary | Unit (µA vs mA) and sign are learned from observed behaviour; if unsupported or stuck at 0 → "Not available" **(verify on device)** |
| Cycle count, state of health | Android 14+ and OEM must publish | Shown only if published **(verify on device)** |
| Exact battery health / capacity | Not provided | Only a coarse Android category + a clearly-labelled rough estimate from the coulomb counter once enough history exists |
| CPU utilisation | `/proc/stat` blocked for apps since Android 8 | Try `/proc/stat`; else **frequency index** (cur/max clock, labelled); else "Not available". AURA's own CPU share always shown separately |
| Per-core frequency | `cpufreq` sysfs often SELinux-restricted | Read if allowed; else "Not available" **(verify on device)** |
| CPU temperature | Not available to apps | `PowerManager` thermal status + thermal headroom drive the "heat" colour |
| Display panel technology | Not exposed | Shown as "Not available"; HDR/wide-gamut/refresh rates shown |
| ICMP ping / true packet loss | No raw sockets | Latency = timed TCP connect; "probe loss" = failed connects (labelled) |
| Public IP | Requires contacting a third party | Not queried (privacy); local addresses shown |
| 4G/5G type, cellular signal | Needs `READ_PHONE_STATE` (and a cellular model) | Optional permission prompt on Network screen; Wi-Fi-only tablets show Wi-Fi details |
| Live traffic vs speed | TrafficStats = bytes used, not capacity | Shown separately and labelled; speed test is a distinct active measurement |
| Speed-test servers | No public API list | Cloudflare built in; **custom https server** (download and optional upload URL) supported. No third-party server list is bundled because endpoints could not be verified |
| Background work | Doze / App Standby / Android 15 FGS rules | Optional foreground service (user toggled) + 15-min WorkManager; exact timing is not guaranteed |
| Drag-to-reorder dashboard | — | Not implemented; cards are reordered with ‹ › buttons (see STATUS) |
| Daily/weekly buckets | — | Aligned to UTC, not local midnight |
