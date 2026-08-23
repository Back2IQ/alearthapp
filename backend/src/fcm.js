// FCM sender. Uses firebase-admin only if GOOGLE_APPLICATION_CREDENTIALS is
// set; otherwise runs in dry-run mode and just records pushes in `sink`.
// firebase-admin is imported lazily (dynamic import inside the send
// function, wrapped in try/catch) so tests can run without it installed.

let messagingApp = null;

/** Dry-run sink: pushes that would have been sent, most recent last. */
export const sink = [];

export function clearSink() {
  sink.length = 0;
}

function toStringData(data) {
  const out = {};
  for (const [k, v] of Object.entries(data || {})) {
    out[k] = String(v);
  }
  return out;
}

async function getMessagingApp() {
  if (messagingApp) return messagingApp;
  const admin = await import("firebase-admin");
  const appNs = admin.default ?? admin;
  if (appNs.apps.length === 0) {
    appNs.initializeApp({
      credential: appNs.credential.applicationDefault(),
    });
  }
  messagingApp = appNs.messaging();
  return messagingApp;
}

/**
 * Send a batch of pushes. If GOOGLE_APPLICATION_CREDENTIALS is set, sends
 * data-only, high-priority messages via firebase-admin's sendEach. If not,
 * or if firebase-admin can't be loaded, falls back to dry-run: pushes are
 * appended to `sink` and logged compactly.
 */
export async function sendPushes(pushes) {
  if (!Array.isArray(pushes) || pushes.length === 0) {
    return { mode: "dry-run", sent: 0, results: [] };
  }

  if (!process.env.GOOGLE_APPLICATION_CREDENTIALS) {
    return dryRun(pushes);
  }

  try {
    const messaging = await getMessagingApp();
    const messages = pushes.map((p) => ({
      token: p.token,
      data: toStringData(p.data),
      android: { priority: "high" },
    }));
    const response = await messaging.sendEach(messages);
    console.log(
      `[fcm] sent ${response.successCount}/${messages.length} pushes (fcm mode)`
    );
    return { mode: "fcm", sent: response.successCount, results: response.responses };
  } catch (err) {
    console.error("[fcm] send failed, falling back to dry-run:", err.message);
    return dryRun(pushes);
  }
}

function dryRun(pushes) {
  for (const p of pushes) {
    sink.push(p);
  }
  console.log(
    `[fcm] dry-run: ${pushes.length} push(es) ->`,
    pushes.map((p) => `${p.token.slice(0, 8)}…:${p.tier}`).join(", ")
  );
  return { mode: "dry-run", sent: pushes.length, results: pushes };
}
