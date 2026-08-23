// Einmaliger Ende-zu-Ende-Push-Test: schickt eine echte FCM-Alarm-Warnung an EIN Gerät.
// Nutzung: GOOGLE_APPLICATION_CREDENTIALS=<service-account.json> node scripts/send-test.mjs <fcm-token> [tier]
// tier: "alarm" (Vollbild, Standard) oder "notify" (leise Benachrichtigung).
import { sendPushes } from "../src/fcm.js";

const token = process.argv[2];
const tier = process.argv[3] || "alarm";
if (!token) {
  console.error("usage: node scripts/send-test.mjs <fcm-token> [alarm|notify]");
  process.exit(1);
}

const now = Date.now();
const push = {
  token,
  tier,
  data: {
    id: "test-" + now,
    lat: 37.0, lon: 35.32, depthKm: 10, originTs: now,
    mag: 6.8, tier, mmi: 8.5,
    matchedLabel: "Zuhause (Test)", distanceKm: 3,
    userLat: 37.0, userLon: 35.32, lang: "de",
    test: "false",
  },
};

const res = await sendPushes([push]);
console.log("SEND RESULT:", JSON.stringify({ mode: res.mode, sent: res.sent }));
if (res.mode !== "fcm") {
  console.error("!! Nicht via FCM gesendet (GOOGLE_APPLICATION_CREDENTIALS gesetzt?).");
  process.exit(2);
}
process.exit(0);
