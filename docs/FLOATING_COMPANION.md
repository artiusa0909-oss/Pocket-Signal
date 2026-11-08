# Pocket Signal Companion: floating overlay MVP

## Product boundary

Pocket Signal Companion is a separate Android application with application ID `com.pocketsignal.gptsignal`. It does not patch, repackage, inject JavaScript into, read private storage from, or alter the installed Pocket Option application (`com.potradeweb`).

The companion:
- requests Android's explicit "display over other apps" permission;
- starts a user-visible foreground service;
- displays a draggable compact PS bubble using `TYPE_APPLICATION_OVERLAY`;
- expands into a compact GPT chat panel;
- can launch the separately installed Pocket Option app through its launcher intent;
- can be stopped from its persistent notification.

The original broker app remains responsible for login, chart rendering, trading controls, order submission, and account state. The overlay does not click trade buttons or submit trades.

## GPT gateway contract

The companion accepts only a configured HTTPS base URL. The OpenAI API key must remain on the server and must never be embedded in this APK.

Request:
```http
POST /v1/chat
Content-Type: application/json
```

```json
{
  "client": "pocket-signal-android",
  "messages": [
    {"role": "user", "content": "Explain the current market context"}
  ]
}
```

Response:
```json
{
  "reply": "A plain-language assistant response"
}
```

The server should authenticate the user, apply rate limits, avoid logging secrets, and return a bounded response. This endpoint is a gateway contract; it is not provisioned by the Android build itself.

## Market-analysis limitation

The overlay UI and chat transport do not automatically obtain candles from Pocket Option. A separate, legitimate and verified quote provider is still required. Until configured, the UI explicitly states that market data is not connected. Do not scrape undocumented broker WebSocket traffic, inspect pixels as price data, or infer quotes from the chart display.

## Android runtime

- Minimum Android API: 26.
- Requires user-granted `SYSTEM_ALERT_WINDOW` special access.
- Uses a foreground service with the Android `specialUse` foreground-service type so the user-visible overlay can remain available while switching to Pocket Option.
- The service is user-started and can be stopped from its notification.
- Some device vendors may apply additional background execution controls; test on the target handset.

## Build verification

CI runs unit tests and assembles a debug APK. Automated build success does not replace on-device validation of overlay permission flow, drag behavior, keyboard resize, foreground notification, and compatibility with the installed Pocket Option version.
