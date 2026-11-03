# SimpleRemote

A simple infrared remote app for Android phones with a built-in IR blaster (Xiaomi / Redmi / POCO,
Huawei / Honor, Vivo, Realme, TCL, older Samsung / LG / HTC, …). It uses the standard
`android.hardware.ConsumerIrManager` API, which is what these phones expose.

Each remote's screen is built from that remote's own capabilities: only the modes, fan speeds,
swing positions, temperature range and extra buttons (LED, clean, sleep…) it actually supports are shown.

## Current scope: air conditioners

- **343 AC code sets from 126 brands** (Daikin, Gree, Midea, LG, Samsung, Panasonic, Mitsubishi, Toshiba, Haier, Hisense, …)
- Guided setup: pick a brand → "Turn ON / Turn OFF" test → "Yes, save" / "No, try next"
- Saved remotes remember their last state (IR is one-way, so the phone is the source of truth)

### Why AC codes are different
TV remotes send one code per key. AC remotes send the **whole state** (power + mode + fan + swing +
temperature) on every press. The database therefore stores a code for each state combination,
and the app looks up `mode → fan → [swing] → temperature` each time you change something.

## Code database

Codes come from [SmartIR](https://github.com/smartHomeHub/SmartIR) (MIT license, attribution in the
app's About screen). They are converted at build time, not at runtime:

```bash
git clone --depth 1 https://github.com/smartHomeHub/SmartIR.git /tmp/SmartIR
python3 tools/convert_smartir.py /tmp/SmartIR
```

This writes `app/src/main/assets/ac/index.json` (small list used for browsing) plus one
`<id>.json` per remote, which is loaded only when that remote is opened. Broadlink (base64) and ESPHome
(raw µs) encodings are supported; 13 Xiaomi-gateway-format files and 1 LOOKin file are skipped for now.

Broadlink packets are decoded in `ir/IrCodec.kt` (≈30.5 µs ticks, 38 kHz carrier assumed) and
trimmed to fit Android's 2-second transmit limit.

## Build

```bash
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # decodes every bundled code and checks state lookup
```

AGP 8.11 · Kotlin 2.2 · Jetpack Compose · minSdk 23 · compileSdk 36.

## Roadmap ideas
- More code sources: IRremoteESP8266 AC protocol encoders (generate any state instead of
  looking it up), IRDB / Flipper-IRDB for TVs, fans, projectors, set-top boxes
- SmartIR `fan`, `light` and `media_player` sets (saved remotes already carry a device `type`)
- Xiaomi-gateway code format
