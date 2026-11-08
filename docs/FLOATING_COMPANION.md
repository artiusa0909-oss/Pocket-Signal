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

The repository includes a Cloudflare Worker implementation in `gateway/worker.js`. It authenticates the mobile client with a separate bearer token, validates message size and roles, calls the OpenAI Responses API, disables response storage (`store: false`), and returns only `{ "reply": "..." }`. The OpenAI API key remains a server-side secret. The Worker is a deployment template; it is not deployed automatically by the Android build.\n\n### Deploy the gateway\n\n1. Install Wrangler and authenticate to your Cloudflare account.\n2. From the `gateway/` directory, deploy with `npx wrangler deploy`.\n3. Generate a random client token (for example, `openssl rand -hex 32`). Keep it private.\n4. Add server-side secrets: `npx wrangler secret put OPENAI_API_KEY`, `npx wrangler secret put PS_CLIENT_TOKEN`, and `npx wrangler secret put OPENAI_MODEL`. For `OPENAI_MODEL`, use a model ID enabled for your OpenAI API account.\n5. In the Android app, save the deployed Worker HTTPS base URL and the exact same `PS_CLIENT_TOKEN` in the GPT connection section. The app sends it as an Authorization bearer token.\n\nUse a unique token and rotate it if the APK or token is shared. The Worker should additionally be protected with provider-side rate limits/usage alerts before wider distribution. Never commit API keys or client tokens to GitHub. The gateway is not live until deployed and configured with valid account credentials.

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
