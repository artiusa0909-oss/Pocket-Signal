# Pocket Signal

Android application for read-only OTC market monitoring and manual trading support.

## Current development direction

The active development branch is `feature/pocket-option-monitor`.

- The user signs in manually to a separate Pocket Option account inside an embedded HTTPS WebView.
- The app does not execute trades or perform balance, deposit, or withdrawal operations.
- The initial WebView does not export cookies, passwords, session IDs, or authorization data to native code.
- OTCharts integration is not part of this architecture.
- Live quote extraction is not yet implemented or verified.
- M1 candle aggregation is implemented as a separate market-data component.
- Signals remain disabled until the quote source is validated.

See [the monitor architecture](docs/POCKET_OPTION_MONITOR_ARCHITECTURE.md) for the staged implementation plan and security boundaries.

## Build

The GitHub Actions workflow builds a debug APK on every push and uploads it as an artifact.
