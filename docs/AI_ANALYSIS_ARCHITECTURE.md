# Pocket Signal GPT — architecture and integration gates

## Product target

A separate Android application that presents the Pocket Option trading surface and a compact, multi-timeframe AI analysis panel. It does not replace or patch the installed Pocket Option package and it never places trades.

## Original APK inspection

The supplied Pocket Broker 1.449 APK is a WebView-based Android application. The earlier integration notes identify its activity as `com.potradeweb.activities.WebViewActivity`. The chart's supported, documented closed-candle export/callback has **not** yet been verified. The app must not assume that chart canvas pixels, undocumented JavaScript globals, private WebSocket messages, cookies, session tokens, or account endpoints are a legitimate/stable market-data API.

## Analysis pipeline

1. Obtain completed OHLC candles from a verified and authorized market-data adapter.
2. Validate timestamps, ordering, OHLC bounds, symbol, timeframe, and candle closure.
3. Calculate deterministic indicators locally: EMA 9/21, RSI 14, MACD, ATR, momentum, candle patterns, and nearby support/resistance.
4. Run a multi-timeframe confluence filter. The default set is M1 + M5 + M15; allow M1/M5/M15/M30 selection.
5. Send only the normalized candle/indicator payload to a server-side GPT service. Never put an OpenAI API key in the APK.
6. Require schema-validated output: UP, DOWN, or WAIT; rationale; invalidation conditions; recommended expiry; data timestamp; model identifier.
7. Display a signal only when data freshness and technical/AI agreement gates pass. Otherwise display WAIT.

## Signal semantics

- Confidence is a model/strategy score until calibrated against out-of-sample results. It is not a guaranteed win probability.
- A win-rate or probability claim may only be shown after walk-forward backtesting and calibration on the same instrument class, expiry, and payout assumptions.
- Closed candles only. No repainting from an unfinished candle.
- Balanced and Strict modes remain available. Strict mode requires stronger cross-timeframe agreement.
- No automatic trade execution.

## GPT service contract (planned)

`POST /v1/analyze`

Request fields:
- `symbol`, `market_type`, `timeframes`
- per-timeframe completed OHLC candles (UTC epoch milliseconds)
- calculated indicator summary
- `mode` (`balanced` or `strict`)

Response fields:
- `direction`: `UP` | `DOWN` | `WAIT`
- `score`: integer 0..100 (not a win probability)
- `expiry_minutes`: positive integer
- `reasons`: concise evidence list
- `risk_flags`: list
- `as_of`: UTC timestamp
- `model`: model identifier

The service must reject stale, malformed, insufficient, or out-of-order data. API credentials stay server-side in GitHub/environment secrets or the deployment secret manager, never in source control or APK assets.

## Current verified state

- GitHub repository and Android Actions workflow are accessible.
- This branch starts from the existing Pocket Option WebView integration prototype.
- The application ID is being changed to `com.pocketsignal.gptsignal` to install independently.
- A real chart-candle feed and GPT backend are not yet connected. Until both are verified, the UI must not display live trade signals.
