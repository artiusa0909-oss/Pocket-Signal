/**
 * Pocket Signal GPT gateway — Cloudflare Worker
 *
 * Required Worker secret: OPENAI_API_KEY
 * Optional plain-text variable: OPENAI_MODEL (defaults to gpt-6-luna)
 *
 * Routes:
 *   GET  /              health check
 *   POST /v1/analyze    validated market analysis
 */

const JSON_HEADERS = {
  "content-type": "application/json; charset=utf-8",
  "cache-control": "no-store",
  "access-control-allow-origin": "*",
  "access-control-allow-methods": "GET, POST, OPTIONS",
  "access-control-allow-headers": "Content-Type, Accept",
};

const RESPONSE_SCHEMA = {
  type: "object",
  additionalProperties: false,
  properties: {
    direction: { type: "string", enum: ["UP", "DOWN", "WAIT"] },
    score: { type: "integer", minimum: 0, maximum: 100 },
    expiry_minutes: { type: "integer", minimum: 1, maximum: 60 },
    reasons: { type: "array", items: { type: "string" } },
    risk_flags: { type: "array", items: { type: "string" } },
    as_of: { type: "string" },
    model: { type: "string" }
  },
  required: [
    "direction", "score", "expiry_minutes", "reasons",
    "risk_flags", "as_of", "model"
  ]
};

function json(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: JSON_HEADERS
  });
}

function timeframeMs(value) {
  const match = /^([1-9]\\d*)([mhd])$/i.exec(String(value || ""));
  if (!match) return null;
  const n = Number(match[1]);
  const unit = match[2].toLowerCase();
  return n * (unit === "m" ? 60_000 : unit === "h" ? 3_600_000 : 86_400_000);
}

function validateRequest(body) {
  if (!body || typeof body !== "object" || Array.isArray(body)) {
    throw new Error("Тело запроса должно быть JSON-объектом.");
  }
  if (typeof body.symbol !== "string" || !body.symbol.trim() || body.symbol.length > 40) {
    throw new Error("Некорректный symbol.");
  }
  if (!["balanced", "strict"].includes(body.mode)) {
    throw new Error("mode должен быть balanced или strict.");
  }
  if (!Array.isArray(body.timeframes) || body.timeframes.length < 1 || body.timeframes.length > 6) {
    throw new Error("Нужно передать от 1 до 6 таймфреймов.");
  }

  let totalCandles = 0;
  const normalized = body.timeframes.map((series) => {
    const duration = timeframeMs(series?.timeframe);
    if (!duration) throw new Error("Неподдерживаемый формат timeframe.");
    if (!Array.isArray(series.candles) || series.candles.length < 35 || series.candles.length > 200) {
      throw new Error("Для каждого таймфрейма требуется от 35 до 200 свечей.");
    }
    totalCandles += series.candles.length;
    if (totalCandles > 600) throw new Error("Слишком много свечей в одном запросе.");

    let previous = -Infinity;
    const candles = series.candles.map((c) => {
      const timestamp = Number(c?.timestamp_ms);
      const open = Number(c?.open);
      const high = Number(c?.high);
      const low = Number(c?.low);
      const close = Number(c?.close);
      const volume = c?.volume == null ? 0 : Number(c.volume);
      if (![timestamp, open, high, low, close, volume].every(Number.isFinite)) {
        throw new Error("Свеча содержит нечисловые значения.");
      }
      if (timestamp <= previous) throw new Error("Временные метки свечей должны строго возрастать.");
      if (timestamp > Date.now() + 120_000) throw new Error("Обнаружена свеча из будущего.");
      if (low <= 0 || high < Math.max(open, close, low) ||
          low > Math.min(open, close) || volume < 0) {
        throw new Error("Некорректные OHLC/volume значения.");
      }
      previous = timestamp;
      return { timestamp_ms: timestamp, open, high, low, close, volume };
    });

    return { timeframe: series.timeframe, duration_ms: duration, candles };
  });

  return {
    symbol: body.symbol.trim(),
    market_type: typeof body.market_type === "string" ? body.market_type.slice(0, 40) : "unknown",
    mode: body.mode,
    timeframes: normalized
  };
}

function extractOutputText(response) {
  for (const item of response?.output || []) {
    if (item.type !== "message" || !Array.isArray(item.content)) continue;
    for (const part of item.content) {
      if (part.type === "output_text" && typeof part.text === "string") return part.text;
    }
  }
  return "";
}

export default {
  async fetch(request, env) {
    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: JSON_HEADERS });
    }

    const url = new URL(request.url);
    if (request.method === "GET" && url.pathname === "/") {
      return json({ service: "Pocket Signal GPT Gateway", status: "online", route: "POST /v1/analyze" });
    }
    if (request.method !== "POST" || url.pathname !== "/v1/analyze") {
      return json({ error: "Not found" }, 404);
    }
    if (!env.OPENAI_API_KEY) {
      return json({ error: "Gateway is not configured: OPENAI_API_KEY secret is missing." }, 503);
    }

    try {
      const raw = await request.text();
      if (raw.length > 256_000) return json({ error: "Request body is too large." }, 413);

      let body;
      try { body = JSON.parse(raw); }
      catch { return json({ error: "Invalid JSON." }, 400); }

      const market = validateRequest(body);
      const model = env.OPENAI_MODEL || "gpt-6-luna";
      const now = new Date().toISOString();

      const instructions = [
        "You are the analytical engine for Pocket Signal, a read-only trading assistant.",
        "Analyze only the supplied completed OHLC candles. Do not invent missing indicators, prices, news, or market context.",
        "Compare all supplied timeframes. Consider trend, momentum, candle structure, volatility and agreement between timeframes.",
        "If evidence is mixed, data is insufficient, or timeframes conflict, return WAIT and explain the uncertainty.",
        "UP means a possible upward move; DOWN means a possible downward move. These are analytical opinions, not guarantees.",
        "score is an evidence-strength score from 0 to 100, NOT a probability of winning.",
        "Use concise Russian-language reasons and risk_flags. Do not claim a guaranteed outcome.",
        "In strict mode, require clear agreement across the supplied timeframes; otherwise return WAIT.",
        "Return only the required JSON object."
      ].join(" ");

      const upstream = await fetch("https://api.openai.com/v1/responses", {
        method: "POST",
        headers: {
          "Authorization": `Bearer ${env.OPENAI_API_KEY}`,
          "Content-Type": "application/json"
        },
        body: JSON.stringify({
          model,
          store: false,
          instructions,
          input: JSON.stringify(market),
          text: {
            format: {
              type: "json_schema",
              name: "pocket_signal_analysis",
              strict: true,
              schema: RESPONSE_SCHEMA
            }
          },
          max_output_tokens: 700
        })
      });

      if (!upstream.ok) {
        const detail = (await upstream.text()).slice(0, 1200);
        return json({ error: "OpenAI API request failed.", upstream_status: upstream.status, detail }, 502);
      }

      const response = await upstream.json();
      const outputText = extractOutputText(response);
      if (!outputText) return json({ error: "The model returned no structured output." }, 502);

      let result;
      try { result = JSON.parse(outputText); }
      catch { return json({ error: "The model returned invalid JSON." }, 502); }

      if (!["UP", "DOWN", "WAIT"].includes(result.direction) ||
          !Number.isInteger(result.score) || result.score < 0 || result.score > 100 ||
          !Number.isInteger(result.expiry_minutes) || result.expiry_minutes < 1 ||
          result.expiry_minutes > 60 || !Array.isArray(result.reasons) ||
          !Array.isArray(result.risk_flags)) {
        return json({ error: "The model response failed validation." }, 502);
      }

      return json({
        direction: result.direction,
        score: result.score,
        expiry_minutes: result.expiry_minutes,
        reasons: result.reasons.slice(0, 6).map(String),
        risk_flags: result.risk_flags.slice(0, 6).map(String),
        as_of: now,
        model: response.model || model
      });
    } catch (error) {
      return json({ error: error instanceof Error ? error.message : "Unexpected gateway error." }, 400);
    }
  }
};
