# Pocket Signal

Android application for OTC chart analysis and trading signals.

## Build

The Android debug APK is built automatically by GitHub Actions on every push and can also be started manually with the **Pocket Signal Android Build** workflow.

To build locally:

```bash
gradle assembleDebug
```

The debug APK is written to:

`app/build/outputs/apk/debug/app-debug.apk`

## Current implementation status

- Android client with Jetpack Compose.
- M1 candle model and a deterministic technical-analysis baseline.
- Live broker quote ingestion is not yet integrated.
- The chart currently uses clearly labelled demo candles; it must not be treated as live market data.
- No automatic trade execution.
