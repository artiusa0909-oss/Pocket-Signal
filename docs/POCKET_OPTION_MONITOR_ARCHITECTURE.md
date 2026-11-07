# Pocket Signal Android v2 — rebuild plan

## Product requirements
- Android application for read-only market monitoring.
- User manually signs in to Pocket Option in the app.
- No order placement, account balance operations, deposits or withdrawals.
- M1 analysis; all instruments visible to the user's broker session.
- Selective signal frequency; strategy target: combined_ml.
- Expiration is selected by the strategy, minimum one minute.
- Signals remain disabled until a quote feed is verified and strategy tests pass.

## v2 baseline
- Separate branch: rewrite/android-v2.
- Rebuilt responsive Compose layout with system insets and compact broker viewport.
- WebView hardware acceleration, JavaScript, DOM storage, database storage and third-party cookies are enabled.
- Secure HTTPS-only navigation; Pocket Option host allowlist remains enforced.
- JavaScript popup behavior is configured for a single-window WebView.
- Main-frame load, HTTP, SSL and renderer failures are surfaced in the UI.
- A manual reload action is available.
- Credentials, cookies, local storage and session identifiers are not exported to native code.

## Remaining verification
1. Build and install the APK on the user's Android device.
2. Test registration/login and the Trade Now transition.
3. If the transition still fails, identify the blocked hostname or main-frame error without exposing account data. Verify the exact official host before changing the allowlist.
4. Confirm the chart renders and updates over time.
5. Only then assess a compliant, stable quote-data source and connect M1 aggregation.
6. Validate signal logic with replay/backtesting before enabling live signals.

## Important limitations
A successful WebView page load does not prove that the broker chart or a live quote feed is working. The broker's internal web protocol is not treated as a public API. No TLS bypass, credential extraction, cookie export, hidden API scraping or automated trade execution is permitted.
