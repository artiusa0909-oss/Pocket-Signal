# Comparative technical audit: Pocket Broker 1.449 vs Pocket Signal

Audit baseline: Pocket Signal source at the chart-restoration commit used for build #124, plus the supplied APK files in the Library.

## 1. APK identity and packaging

| Artifact | Package | Version | Observed structure |
|---|---|---|---|
| Supplied original `Pocket Broker_1.449.apk` | `com.potradeweb` | `1.449` / versionCode 449 | Original Pocket Broker application |
| Earlier `PocketBroker-ALGML-separate.apk` | `com.potradeweb` | `1.449` / versionCode 449 | Repackaged original plus three unused assets |
| Pocket Signal build #89 | `com.pocketsignal.app` | `2.0` / versionCode 2 | Standalone WebView/Compose prototype |
| Current Pocket Signal project | `com.pocketsignal.gptsignal` | `2.1.3` / versionCode 6 | Standalone Compose shell with Pocket Option WebView |

The earlier ALG/ML APK is **not package-separated**: its compiled manifest still declares `com.potradeweb`. Its `classes.dex` and `resources.arsc` are byte-identical to the supplied original. The only additional application assets are:

- `assets/pocket-signal/BUILD-NOTES.txt`
- `assets/pocket-signal/signal-engine.js`
- `assets/pocket-signal/webview-bridge.js`

The bundled build notes explicitly say that the scripts are not invoked by the original WebView activity and that no chart candles are connected. This package can conflict with the installed official app and is signed with a different development certificate. Do not use it as the independent-install baseline.

## 2. Architecture comparison

### Original Pocket Broker 1.449

The manifest identifies the package application class `com.potradeweb.App`, launcher activity `com.potradeweb.activities.MainActivity`, and trading WebView activity `com.potradeweb.activities.WebViewActivity`. The APK also includes the original app's supporting Android components and dependencies (including notification/Firebase and account-related components).

The original APK is a full broker client, not just a browser wrapper. Its internal trading protocol and private application implementation are not treated as a stable public API.

### Pocket Signal

The current project is a separate Android application. It loads the Pocket Option HTTPS website in an accelerated Android WebView and places a compact Compose analysis panel above the broker's own bottom navigation. It has:

- a separate application ID, `com.pocketsignal.gptsignal`;
- JavaScript and DOM storage enabled for the trading website;
- third-party cookies enabled for the broker WebView session;
- HTTPS-only navigation to the Pocket Option host family;
- SSL errors cancelled rather than bypassed;
- renderer/page error reporting and manual reload;
- chart layout recovery and read-only diagnostics restored from the chart-working build #89 code in build #124.

The chart-rendering implementation is intentionally unchanged in this audit branch because the user confirmed that build #124 restored working charts.

## 3. Functional gaps found in Pocket Signal

1. **No verified candle adapter is connected.** `M1CandleAggregator` can build a completed one-minute candle from ticks, but the repository does not currently supply it with an authorized, validated quote feed.
2. **The analysis engine is not wired into the screen.** `MainActivity` creates `signalDecision` as null and never updates it. The `SignalEngine` and `AiAnalysisClient` are not called by the screen.
3. **The GPT client is only a transport component.** There is no configured backend endpoint or live multi-timeframe request flow in the Android screen.
4. **The previous native-package experiment did not integrate the panel.** Its bridge and strategy JavaScript were bundled as assets but were not injected into the original WebView activity.
5. **A WebView page load is not proof of chart or quote-feed health.** Build #124's chart is user-confirmed working, but the quote feed still requires independent validation.

## 4. Changes in this audit branch

- Hardened `SignalEngine`: invalid OHLC, non-finite values, duplicate timestamps, and out-of-order candles now stop analysis instead of being silently filtered, sorted, or deduplicated.
- Raised the minimum history to 35 completed candles to match the AI analysis contract.
- Replaced the simple last-window RSI with Wilder-smoothed RSI(14).
- Replaced the simple ATR average with Wilder-smoothed ATR(14).
- Seeded EMA with an SMA to reduce short-history initialization bias.
- Added a conservative M1/M5/M15 confluence gate. A combined candidate is suppressed if any timeframe lacks a valid candidate or if directions disagree.
- Added regression tests for candle validation, ordering, M1 candle closure, out-of-order ticks, and missing timeframe data.
- Updated CI to run unit tests before building the APK.
- Bumped app version to 2.1.3 (versionCode 6).

## 5. Explicitly preserved

- Build #124 chart/WebView recovery logic.
- Pocket Option's own bottom navigation and trading interface.
- Independent package ID `com.pocketsignal.gptsignal`.
- Manual user sign-in; no copying/exporting of credentials, cookies, or session tokens.
- No automatic trade placement.

## 6. Remaining integration gate

A signal must remain unavailable until a legitimate, authorized market-data adapter supplies closed candles with verified symbol, timeframe, timestamps, ordering, OHLC bounds, and freshness. Do not scrape undocumented broker WebSocket messages, inspect chart pixels as price data, or infer candles from diagnostics. After a feed is established, connect the M1/M5/M15 engine to UI state, configure the server-side GPT gateway, and run walk-forward backtests before presenting a score as a calibrated probability or enabling live signal recommendations.
