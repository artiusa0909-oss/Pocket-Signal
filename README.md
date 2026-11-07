# Pocket Signal

Android application for manual trading support and OTC market analysis.

## OTCharts market-data integration

The app uses the independent, read-only OTCharts API for Pocket Option OTC market data.
It does not log in to Pocket Option, request a Pocket Option SSID, or place trades.

### Configure the API key

1. Create an account at https://otcharts.com and confirm the email address.
2. Create an OTCharts API key and enable the Pocket Option (`otc`) book.
3. In Pocket Signal, paste the key into the OTCharts connection panel and press
   **Save key and load M1**.
4. The key is encrypted at rest with Android Keystore. Do not commit API keys to GitHub,
   source files, Gradle properties, or screenshots.

### Current implementation scope

- Reads account usage from `GET /v1/usage`.
- Reads the Pocket Option OTC instrument catalogue from `GET /v1/symbols?venue=otc`.
- Downloads up to 120 one-minute candles from `GET /v1/candles`.
- Displays the returned M1 candles in the app chart.
- Reports gaps in the candle series; a gapped series must not be treated as consecutive M1 bars.
- Read-only: no order placement, no account balance access, and no trade execution.

### Important API limits

The OTCharts free tier currently provides a one-time allowance of 200 requests in the first month,
five instruments on one selected book, and **no live streaming**. Paid data plans enable
Server-Sent Events streaming. The current Android integration performs an on-demand candle
snapshot; it does not claim to be real-time. Check https://otcharts.com/api and
https://otcharts.com/pricing for current limits before choosing a plan.

Pocket Option OTC prices are broker-generated synthetic quotes, not interbank FX prices.
Validate the feed against the Pocket Option terminal before using it for any decisions.

## Build

The repository includes a GitHub Actions workflow that builds a debug APK on push or manual run.
