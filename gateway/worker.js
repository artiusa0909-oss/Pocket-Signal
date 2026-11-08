export default {
  async fetch(request, env) {
    const corsHeaders = {
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "POST, OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type, Authorization",
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store"
    };

    const json = (body, status = 200) =>
      new Response(JSON.stringify(body), { status, headers: corsHeaders });

    if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: corsHeaders });
    if (request.method !== "POST" || new URL(request.url).pathname !== "/v1/chat") {
      return json({ error: "Not found" }, 404);
    }
    if (!env.OPENAI_API_KEY || !env.PS_CLIENT_TOKEN || !env.OPENAI_MODEL) {
      return json({ error: "Gateway is not configured" }, 503);
    }

    const supplied = (request.headers.get("Authorization") || "").replace(/^Bearer\s+/i, "");
    if (!supplied || !(await constantTimeEqual(supplied, env.PS_CLIENT_TOKEN))) {
      return json({ error: "Unauthorized" }, 401);
    }

    const declaredLength = Number(request.headers.get("Content-Length") || 0);
    if (declaredLength > 24000) return json({ error: "Request too large" }, 413);

    let body;
    try { body = await request.json(); }
    catch { return json({ error: "Invalid JSON" }, 400); }

    if (!Array.isArray(body.messages) || body.messages.length < 1 || body.messages.length > 20) {
      return json({ error: "messages must contain 1 to 20 items" }, 400);
    }
    const messages = [];
    let totalChars = 0;
    for (const item of body.messages) {
      if (!item || !["user", "assistant"].includes(item.role) ||
          typeof item.content !== "string" || !item.content.trim() || item.content.length > 4000) {
        return json({ error: "Invalid message item" }, 400);
      }
      totalChars += item.content.length;
      messages.push({ role: item.role, content: item.content });
    }
    if (totalChars > 16000) return json({ error: "Conversation too large" }, 413);

    try {
      const upstream = await fetch("https://api.openai.com/v1/responses", {
        method: "POST",
        headers: {
          "Authorization": `Bearer ${env.OPENAI_API_KEY}`,
          "Content-Type": "application/json"
        },
        body: JSON.stringify({
          model: env.OPENAI_MODEL,
          input: messages,
          max_output_tokens: 900,
          store: false
        })
      });
      const result = await upstream.json().catch(() => ({}));
      if (!upstream.ok) {
        // Do not return upstream details or secrets to the mobile client.
        return json({ error: "AI provider request failed", status: upstream.status }, 502);
      }
      const reply = (typeof result.output_text === "string" ? result.output_text :
        Array.isArray(result.output) ? result.output.flatMap(item => Array.isArray(item.content) ? item.content : [])\n          .filter(item => item.type === "output_text" && typeof item.text === "string")\n          .map(item => item.text).join("\n") : "").trim();
      if (!reply) return json({ error: "AI provider returned no text" }, 502);
      return json({ reply });
    } catch {
      return json({ error: "AI provider unavailable" }, 502);
    }
  }
};

async function constantTimeEqual(a, b) {
  const enc = new TextEncoder();
  const [left, right] = await Promise.all([
    crypto.subtle.digest("SHA-256", enc.encode(a)),
    crypto.subtle.digest("SHA-256", enc.encode(b))
  ]);
  const x = new Uint8Array(left), y = new Uint8Array(right);
  let diff = 0;
  for (let i = 0; i < x.length; i++) diff |= x[i] ^ y[i];
  return diff === 0;
}
