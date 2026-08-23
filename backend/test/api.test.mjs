import { test } from "node:test";
import assert from "node:assert/strict";
import { promises as fs } from "node:fs";
import os from "node:os";
import path from "node:path";
import { createStore } from "../src/store.js";
import { createServer } from "../src/server.js";

async function tmpFile() {
  const dir = await fs.mkdtemp(path.join(os.tmpdir(), "tda-api-"));
  return path.join(dir, "devices.json");
}

function listen(server) {
  return new Promise((resolve) => {
    server.listen(0, "127.0.0.1", () => resolve(server.address().port));
  });
}

function close(server) {
  return new Promise((resolve) => server.close(resolve));
}

async function postJson(port, urlPath, body) {
  const res = await fetch(`http://127.0.0.1:${port}${urlPath}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  return { status: res.status, json: await res.json() };
}

async function getJson(port, urlPath) {
  const res = await fetch(`http://127.0.0.1:${port}${urlPath}`);
  return { status: res.status, json: await res.json() };
}

test("register -> healthz shows device -> unregister -> healthz shows 0", async () => {
  const filePath = await tmpFile();
  const store = createStore(filePath);
  await store.load();
  const server = createServer(store, () => null);
  const port = await listen(server);

  try {
    const health0 = await getJson(port, "/healthz");
    assert.equal(health0.status, 200);
    assert.equal(health0.json.ok, true);
    assert.equal(health0.json.devices, 0);
    assert.equal(health0.json.lastPollTs, null);

    const reg = await postJson(port, "/register", {
      token: "tok-abc",
      lang: "tr",
      platform: "android",
      subscriptions: [
        { lat: 38.42, lon: 27.14, label: "home", notifyMag: 4.0, alarmMag: 6.0, radiusKm: 100 },
      ],
    });
    assert.equal(reg.status, 200);
    assert.equal(reg.json.ok, true);

    const health1 = await getJson(port, "/healthz");
    assert.equal(health1.json.devices, 1);

    const unreg = await postJson(port, "/unregister", { token: "tok-abc" });
    assert.equal(unreg.status, 200);

    const health2 = await getJson(port, "/healthz");
    assert.equal(health2.json.devices, 0);
  } finally {
    await close(server);
  }
});

test("register with missing fields -> 400", async () => {
  const filePath = await tmpFile();
  const store = createStore(filePath);
  await store.load();
  const server = createServer(store, () => null);
  const port = await listen(server);

  try {
    const missingToken = await postJson(port, "/register", {
      lang: "tr",
      platform: "android",
      subscriptions: [{ lat: 1, lon: 1, label: "a", notifyMag: 4, alarmMag: 6, radiusKm: 50 }],
    });
    assert.equal(missingToken.status, 400);
    assert.ok(missingToken.json.error);

    const missingSubs = await postJson(port, "/register", {
      token: "tok-x",
      lang: "tr",
      platform: "android",
      subscriptions: [],
    });
    assert.equal(missingSubs.status, 400);
    assert.ok(missingSubs.json.error);

    const badSub = await postJson(port, "/register", {
      token: "tok-x",
      lang: "tr",
      platform: "android",
      subscriptions: [{ lat: "not-a-number", lon: 1, label: "a", notifyMag: 4, alarmMag: 6, radiusKm: 50 }],
    });
    assert.equal(badSub.status, 400);
    assert.ok(badSub.json.error);
  } finally {
    await close(server);
  }
});

test("unregister with missing token -> 400", async () => {
  const filePath = await tmpFile();
  const store = createStore(filePath);
  await store.load();
  const server = createServer(store, () => null);
  const port = await listen(server);

  try {
    const res = await postJson(port, "/unregister", {});
    assert.equal(res.status, 400);
    assert.ok(res.json.error);
  } finally {
    await close(server);
  }
});
