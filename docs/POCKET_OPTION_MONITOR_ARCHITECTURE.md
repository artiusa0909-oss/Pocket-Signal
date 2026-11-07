# Pocket Signal — Pocket Option Monitor Architecture

## Product scope

- Android application.
- A separate, empty Pocket Option account is used only to display the broker's OTC market.
- The user signs in manually on the Pocket Option website inside the app.
- No automated order placement, trade execution, balance operations, deposits, or withdrawals.
- Market analysis uses M1 candles; the configured strategy is `combined_ml`.
- Instruments: all OTC symbols exposed by the broker session.
- Signal output: CALL / PUT / WAIT. Insufficient, stale, or discontinuous data must produce WAIT.
- Expiration is selected by the strategy, with a minimum of one minute.

## Architecture stages

1. **Embedded broker page** — load the HTTPS Pocket Option website in an Android WebView. Login remains on the broker's own page.
2. **Feed feasibility test** — determine whether market quotes can be read reliably from the visible page or an explicitly supported interface. No password, cookies, session IDs, or authorization headers may be copied to native code, logged, or transmitted elsewhere.
3. **Market data adapter** — normalize verified quote updates into `MarketTick` values.
4. **M1 aggregation** — build OHLC candles and track timestamps, gaps, duplicates, and stale feeds.
5. **Instrument monitor** — enumerate every OTC instrument actually exposed by the broker session and display connection/data freshness per instrument.
6. **Analysis engine** — add `combined_ml` only after the quote feed has been validated and historical replay/backtesting is available.

## Security boundaries

- Do not request or accept Pocket Option passwords, cookies, SSID/session tokens, or one-time codes in chat, GitHub, logs, or remote services.
- Do not export WebView cookies or local storage to native code.
- Do not bypass TLS validation or accept mixed-content downgrades.
- No trading endpoints or order-submission code.
- WebView navigation is restricted to HTTPS pages on `pocketoption.com` and its subdomains in the initial prototype.

## Current implementation status

- WebView shell: implemented in the monitor branch.
- Manual login: user-controlled on the broker page.
- Quote extraction: not implemented; feasibility and protocol must be assessed first.
- Live OTC feed: not yet verified.
- M1 aggregation model: implemented as a reusable component, not yet connected to a feed.
- Signals: disabled until feed validation and strategy testing are complete.

## Important limitation

The broker's internal web protocol is not a documented public developer API. Page structure, network behavior, and access may change. A visible WebView login does not itself prove that quote extraction is possible, complete, or stable.
