// Minimal JSON HTTP API on node:http — no framework dependency.
import http from "node:http";

function readJsonBody(req) {
  return new Promise((resolve, reject) => {
    let body = "";
    req.on("data", (chunk) => {
      body += chunk;
      if (body.length > 1_000_000) {
        reject(new Error("body too large"));
        req.destroy();
      }
    });
    req.on("end", () => {
      if (!body) return resolve({});
      try {
        resolve(JSON.parse(body));
      } catch {
        reject(new Error("invalid json"));
      }
    });
    req.on("error", reject);
  });
}

function sendJson(res, status, obj) {
  const data = JSON.stringify(obj);
  res.writeHead(status, {
    "Content-Type": "application/json; charset=utf-8",
    "Content-Length": Buffer.byteLength(data),
  });
  res.end(data);
}

function isNum(v) {
  return typeof v === "number" && Number.isFinite(v);
}

function validateSubscription(sub) {
  if (!sub || typeof sub !== "object") return "subscription must be an object";
  if (!isNum(sub.lat) || sub.lat < -90 || sub.lat > 90) return "invalid subscription.lat";
  if (!isNum(sub.lon) || sub.lon < -180 || sub.lon > 180) return "invalid subscription.lon";
  if (typeof sub.label !== "string" || !sub.label) return "invalid subscription.label";
  if (!isNum(sub.notifyMag)) return "invalid subscription.notifyMag";
  if (!isNum(sub.alarmMag)) return "invalid subscription.alarmMag";
  if (!isNum(sub.radiusKm) || sub.radiusKm <= 0) return "invalid subscription.radiusKm";
  return null;
}

function validateRegisterBody(body) {
  if (!body || typeof body !== "object") return "body must be a JSON object";
  if (typeof body.token !== "string" || !body.token) return "missing/invalid token";
  if (typeof body.lang !== "string" || !body.lang) return "missing/invalid lang";
  if (typeof body.platform !== "string" || !body.platform) return "missing/invalid platform";
  if (!Array.isArray(body.subscriptions) || body.subscriptions.length === 0) {
    return "missing/invalid subscriptions";
  }
  for (const sub of body.subscriptions) {
    const err = validateSubscription(sub);
    if (err) return err;
  }
  return null;
}

/**
 * Create the HTTP server. `store` is a device store (see store.js).
 * `getLastPollTs` is a () => number|null used to report /healthz status.
 */
export function createServer(store, getLastPollTs = () => null) {
  const server = http.createServer(async (req, res) => {
    try {
      if (req.method === "GET" && req.url === "/healthz") {
        return sendJson(res, 200, {
          ok: true,
          devices: store.count(),
          lastPollTs: getLastPollTs(),
        });
      }

      if (req.method === "POST" && req.url === "/register") {
        const body = await readJsonBody(req);
        const err = validateRegisterBody(body);
        if (err) return sendJson(res, 400, { error: err });
        await store.upsert({
          token: body.token,
          lang: body.lang,
          platform: body.platform,
          subscriptions: body.subscriptions,
        });
        return sendJson(res, 200, { ok: true });
      }

      if (req.method === "POST" && req.url === "/unregister") {
        const body = await readJsonBody(req);
        if (typeof body.token !== "string" || !body.token) {
          return sendJson(res, 400, { error: "missing/invalid token" });
        }
        await store.remove(body.token);
        return sendJson(res, 200, { ok: true });
      }

      sendJson(res, 404, { error: "not found" });
    } catch (err) {
      sendJson(res, 400, { error: err.message || "bad request" });
    }
  });
  return server;
}
