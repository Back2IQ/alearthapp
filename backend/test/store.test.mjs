import { test } from "node:test";
import assert from "node:assert/strict";
import { promises as fs } from "node:fs";
import os from "node:os";
import path from "node:path";
import { createStore } from "../src/store.js";

async function tmpFile() {
  const dir = await fs.mkdtemp(path.join(os.tmpdir(), "tda-store-"));
  return path.join(dir, "devices.json");
}

test("register (upsert) -> same token overwrites -> unregister roundtrip", async () => {
  const filePath = await tmpFile();
  const store = createStore(filePath);
  await store.load(); // no file yet -> empty

  assert.equal(store.count(), 0);

  await store.upsert({
    token: "tok1",
    lang: "tr",
    platform: "android",
    subscriptions: [{ lat: 1, lon: 1, label: "a", notifyMag: 4, alarmMag: 6, radiusKm: 50 }],
  });
  assert.equal(store.count(), 1);

  // same token again with different data -> overwrite, not duplicate
  await store.upsert({
    token: "tok1",
    lang: "en",
    platform: "ios",
    subscriptions: [{ lat: 2, lon: 2, label: "b", notifyMag: 5, alarmMag: 7, radiusKm: 100 }],
  });
  assert.equal(store.count(), 1);
  assert.equal(store.get("tok1").lang, "en");
  assert.equal(store.get("tok1").subscriptions[0].label, "b");

  // persisted to disk correctly
  const raw = JSON.parse(await fs.readFile(filePath, "utf8"));
  assert.equal(raw.length, 1);
  assert.equal(raw[0].token, "tok1");

  // a fresh store loading from the same file sees the persisted device
  const store2 = createStore(filePath);
  await store2.load();
  assert.equal(store2.count(), 1);
  assert.equal(store2.get("tok1").lang, "en");

  // unregister
  await store.remove("tok1");
  assert.equal(store.count(), 0);
  const raw2 = JSON.parse(await fs.readFile(filePath, "utf8"));
  assert.equal(raw2.length, 0);
});
