import http from "node:http";

const PORT = Number(process.env.PORT || 8787);
const API_KEY = process.env.OPENAI_API_KEY;
const MODEL = process.env.OPENAI_MODEL || "gpt-5.4-mini";
const WINDOW_MS = 60_000;
const MAX_REQUESTS = 5;
const MAX_BODY = 7 * 1024 * 1024;
const requests = new Map();

function send(res, status, data) {
  res.writeHead(status, {
    "content-type": "application/json; charset=utf-8",
    "cache-control": "no-store"
  });
  res.end(JSON.stringify(data));
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    let size = 0;
    const chunks = [];
    req.on("data", chunk => {
      size += chunk.length;
      if (size > MAX_BODY) {
        reject(new Error("REQUEST_TOO_LARGE"));
        req.destroy();
        return;
      }
      chunks.push(chunk);
    });
    req.on("end", () => {
      try { resolve(JSON.parse(Buffer.concat(chunks).toString("utf8"))); }
      catch { reject(new Error("INVALID_JSON")); }
    });
    req.on("error", reject);
  });
}

function extractOutputText(response) {
  if (typeof response.output_text === "string") return response.output_text;
  for (const item of response.output || []) {
    for (const part of item.content || []) {
      if ((part.type === "output_text" || part.type === "text") && part.text) return part.text;
    }
  }
  throw new Error("EMPTY_MODEL_RESPONSE");
}

function parseModelJson(text) {
  const cleaned = text.trim().replace(/^\`\`\`(?:json)?\s*/i, "").replace(/\s*\`\`\`$/, "");
  const value = JSON.parse(cleaned);
  const direction = ["CALL", "PUT", "WAIT"].includes(value.direction) ? value.direction : "WAIT";
  const upScore = Math.max(0, Math.min(100, Math.round(Number(value.upScore) || 0)));
  const downScore = 100 - upScore;
  const confidence = Math.max(0, Math.min(100, Math.round(Number(value.confidence) || 0)));
  const expiryMinutes = [1, 2, 3].includes(Number(value.expiryMinutes)) ? Number(value.expiryMinutes) : 1;
  return {
    direction,
    upScore,
    downScore,
    confidence,
    expiryMinutes,
    summary: String(value.summary || "Недостаточно данных для уверенного вывода.").slice(0, 700),
    risks: String(value.risks || "").slice(0, 500)
  };
}

const server = http.createServer(async (req, res) => {
  if (req.method === "GET" && req.url === "/health") return send(res, 200, { ok: true, service: "pocket-signal-ai" });
  if (req.method !== "POST" || req.url !== "/analyze") return send(res, 404, { error: "NOT_FOUND" });
  if (!API_KEY) return send(res, 503, { error: "OPENAI_API_KEY_NOT_CONFIGURED" });

  const ip = req.headers["x-forwarded-for"]?.split(",")[0]?.trim() || req.socket.remoteAddress || "unknown";
  const now = Date.now();
  const recent = (requests.get(ip) || []).filter(t => now - t < WINDOW_MS);
  if (recent.length >= MAX_REQUESTS) return send(res, 429, { error: "RATE_LIMITED", retryAfterSeconds: 60 });
  recent.push(now);
  requests.set(ip, recent);

  try {
    const body = await readBody(req);
    const image = String(body.imageBase64 || "");
    if (!image || image.length > 6_500_000 || !/^[A-Za-z0-9+/]+={0,2}$/.test(image)) {
      return send(res, 400, { error: "INVALID_IMAGE" });
    }
    const asset = String(body.asset || "не определён").slice(0, 40);
    const timeframe = String(body.timeframe || "M1").slice(0, 8);
    const candles = Array.isArray(body.candles) ? body.candles.slice(-30) : [];
    const local = body.localAnalysis || {};
    const candleText = candles.map((c, i) =>
      `${i + 1}: O=${c.open} H=${c.high} L=${c.low} C=${c.close}`
    ).join("\n");

    const prompt = `Ты независимый аналитик технического графика бинарных опционов. Анализируй только предоставленный график и OHLC. Не выдумывай котировки, индикаторы или уровни, которых нельзя увидеть или рассчитать. Актив: ${asset}. Таймфрейм: ${timeframe}. Локальный алгоритм: ${JSON.stringify(local)}. Распознанные свечи (последние сверху вниз по времени):\n${candleText || "OHLC недоступны"}.\nОцени тренд, структуру максимумов/минимумов, импульс, свечные формации, возможные уровни поддержки/сопротивления и риск ложного пробоя. Изображение важнее приблизительных OHLC, если они противоречат друг другу. Если график неразборчив, данных мало, рынок во флэте или преимущества нет, direction=WAIT. Не обещай прибыль и не называй оценку вероятностью выигрыша. Верни ТОЛЬКО JSON: {"direction":"CALL|PUT|WAIT","upScore":0-100,"confidence":0-100,"expiryMinutes":1|2|3,"summary":"краткий вывод по-русски","risks":"основные риски по-русски"}. upScore и downScore должны быть взаимно дополняющими оценками поддержки факторов, их сумма 100.`;

    const apiResponse = await fetch("https://api.openai.com/v1/responses", {
      method: "POST",
      headers: {
        "authorization": `Bearer ${API_KEY}`,
        "content-type": "application/json"
      },
      body: JSON.stringify({
        model: MODEL,
        max_output_tokens: 650,
        input: [{
          role: "user",
          content: [
            { type: "input_text", text: prompt },
            { type: "input_image", image_url: `data:image/jpeg;base64,${image}`, detail: "high" }
          ]
        }]
      }),
      signal: AbortSignal.timeout(45_000)
    });

    const payload = await apiResponse.json();
    if (!apiResponse.ok) {
      const message = payload?.error?.message || "OpenAI request failed";
      console.error("OpenAI API error:", apiResponse.status, message);
      return send(res, 502, { error: "OPENAI_API_ERROR", status: apiResponse.status });
    }
    const result = parseModelJson(extractOutputText(payload));
    return send(res, 200, { ok: true, model: MODEL, ...result });
  } catch (error) {
    const message = error?.message || "UNKNOWN_ERROR";
    const status = message === "INVALID_JSON" ? 400 : message === "REQUEST_TOO_LARGE" ? 413 : 502;
    console.error("Analysis request failed:", message);
    return send(res, status, { error: message });
  }
});

server.listen(PORT, "0.0.0.0", () => {
  console.log(`Pocket Signal AI backend listening on ${PORT}`);
});
