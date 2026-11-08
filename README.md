# Pocket Signal GPT

Standalone Android application for read-only Pocket Option market monitoring and AI-assisted manual trading analysis.

## Development branch
`feature/pocket-signal-gpt-v2`

## Product behavior
- Separate Android application ID: `com.pocketsignal.gptsignal`.
- Pocket Option is presented inside an HTTPS-only WebView; users sign in manually.
- GPT analysis is designed for completed OHLC candles across M1, M5, M15 and optional higher timeframes.
- Signal output: `UP`, `DOWN`, or `WAIT`, with evidence, risk flags and suggested expiry.
- Balanced and Strict analysis modes.
- No automatic trade execution, account actions, cookie export, or credential transfer.
- OpenAI credentials must remain on a server-side gateway; they must never be embedded in the APK.

## Current implementation status
- Original APK 1.449 inspected: WebView-based Android app.
- Existing WebView prototype and selective technical-analysis baseline retained as starting point.
- Standalone application ID and Pocket Signal GPT label configured.
- Server-side GPT HTTP client and request/response contract added.
- A verified, supported source of completed chart candles is still outstanding.
- GPT gateway deployment/configuration is still outstanding.
- Live signals must remain disabled until both the candle feed and GPT gateway are verified and strategy results are back-tested.

See [AI analysis architecture](docs/AI_ANALYSIS_ARCHITECTURE.md) and [monitor architecture](docs/POCKET_OPTION_MONITOR_ARCHITECTURE.md).

## Build
GitHub Actions workflow `Pocket Signal Android Build` builds a debug APK on pushes and uploads the APK artifact.
