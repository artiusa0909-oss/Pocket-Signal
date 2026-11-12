# Pocket Signal AI backend

The Android app sends a compressed chart screenshot and the locally extracted OHLC candles to this service. The service calls the OpenAI Responses API with image input and returns a compact Russian analysis.

## Deploy on Render

1. Create a new Web Service from this GitHub repository.
2. Set the root directory to `backend` (or let Render read `backend/render.yaml`).
3. Add the secret environment variable `OPENAI_API_KEY` in the Render dashboard. Never put this key in the Android app, GitHub source, or chat.
4. Deploy. Verify `https://YOUR-SERVICE.onrender.com/health` returns `{"ok":true,...}`.
5. Enter `https://YOUR-SERVICE.onrender.com` in Pocket Signal's AI server setting.

Optional environment variable: `OPENAI_MODEL` (defaults to `gpt-5.4-mini`).

## API

- `GET /health`
- `POST /analyze`: JSON body with `asset`, `timeframe`, `candles`, `localAnalysis`, and JPEG `imageBase64` (base64 only, without data URL prefix).

The service does not log or persist screenshots. It limits each source IP to five analysis requests per minute and rejects oversized payloads. Add authentication and a persistent abuse-control strategy before public commercial release.
