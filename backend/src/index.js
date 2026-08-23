// Entry point: starts the HTTP API and the 30s earthquake poll loop.
import path from "node:path";
import { fileURLToPath } from "node:url";
import { createStore } from "./store.js";
import { createServer } from "./server.js";
import { pollAllSources, createDedup, dedupEvents } from "./feeds.js";
import { matchEvents } from "./matcher.js";
import { sendPushes } from "./fcm.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const DATA_FILE = path.join(__dirname, "..", "data", "devices.json");
const POLL_INTERVAL_MS = 30_000;
const PORT = process.env.PORT ? Number(process.env.PORT) : 8080;

async function main() {
  const store = createStore(DATA_FILE);
  await store.load();

  const dedup = createDedup(5000);
  let lastPollTs = null;

  async function pollOnce() {
    let events = [];
    try {
      events = await pollAllSources();
    } catch (err) {
      console.error("[poll] source poll failed:", err.message);
      events = [];
    }
    lastPollTs = Date.now();

    const newEvents = dedupEvents(events, dedup);
    if (newEvents.length === 0) return;

    const pushes = matchEvents(newEvents, store.all());
    if (pushes.length === 0) return;

    try {
      const result = await sendPushes(pushes);
      console.log(
        `[poll] ${newEvents.length} new event(s) -> ${pushes.length} push(es) (${result.mode})`
      );
    } catch (err) {
      console.error("[poll] sendPushes failed:", err.message);
    }
  }

  const server = createServer(store, () => lastPollTs);
  server.listen(PORT, () => {
    console.log(`[server] listening on :${PORT}`);
  });

  // Prime the dedup with the events already in the feed so a restart/redeploy
  // does NOT re-alarm every device about the last hour of quakes. Best-effort:
  // on failure the set stays empty and the next poll behaves normally.
  try {
    const initial = await pollAllSources();
    dedupEvents(initial, dedup); // records ids as seen; no matching, no push
    lastPollTs = Date.now();
    console.log(`[poll] primed dedup with ${initial.length} existing event(s)`);
  } catch (err) {
    console.error("[poll] prime failed:", err.message);
  }

  // Then poll on the interval. Never let a failed poll crash the process.
  const timer = setInterval(() => {
    pollOnce().catch((err) => console.error("[poll] unexpected error:", err));
  }, POLL_INTERVAL_MS);
  timer.unref();

  return { server, store, pollOnce };
}

main().catch((err) => {
  console.error("[fatal]", err);
  process.exit(1);
});
