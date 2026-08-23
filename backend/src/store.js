// Device registry persisted to a JSON file, atomically written (temp file +
// rename) so a crash mid-write can never corrupt the store.
import { promises as fs } from "node:fs";
import path from "node:path";

export function createStore(filePath) {
  const devices = new Map(); // token -> { token, lang, platform, subscriptions }

  async function ensureDir() {
    await fs.mkdir(path.dirname(filePath), { recursive: true });
  }

  async function load() {
    try {
      const raw = await fs.readFile(filePath, "utf8");
      const parsed = JSON.parse(raw);
      devices.clear();
      if (Array.isArray(parsed)) {
        for (const d of parsed) {
          if (d && d.token) devices.set(d.token, d);
        }
      }
    } catch (err) {
      if (err.code === "ENOENT") return; // no file yet — start empty
      // Corrupt/unreadable store (bad JSON, half-written file): back it up and
      // start empty rather than crash the whole backend on startup (which would
      // take down all alarm delivery).
      console.error("[store] load failed, starting empty:", err.message);
      try {
        await fs.rename(filePath, `${filePath}.corrupt.${Date.now()}`);
      } catch { /* best-effort backup */ }
    }
  }

  async function save() {
    await ensureDir();
    const rand = Math.random().toString(36).slice(2);
    const tmpPath = `${filePath}.${process.pid}.${Date.now()}.${rand}.tmp`;
    const data = JSON.stringify(Array.from(devices.values()), null, 2);
    await fs.writeFile(tmpPath, data, "utf8");
    await fs.rename(tmpPath, filePath);
  }

  async function upsert(device) {
    devices.set(device.token, device);
    await save();
    return device;
  }

  async function remove(token) {
    const existed = devices.delete(token);
    if (existed) await save();
    return existed;
  }

  function get(token) {
    return devices.get(token);
  }

  function all() {
    return Array.from(devices.values());
  }

  function count() {
    return devices.size;
  }

  return { load, save, upsert, remove, get, all, count };
}
