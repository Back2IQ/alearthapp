// Matches new earthquake events against device subscriptions and produces
// at most one push per device (the strongest match wins).
import { haversineKm, mmi } from "./eew.js";

/**
 * For a single device, find the strongest match for a single event across
 * all its subscriptions. Returns null if no subscription matches.
 * "Strongest": tier "alarm" beats "notify"; ties broken by smallest distance.
 */
function bestSubscriptionMatch(device, event) {
  let best = null;
  for (const sub of device.subscriptions || []) {
    if (typeof sub.notifyMag !== "number" || typeof sub.radiusKm !== "number") {
      continue;
    }
    const distanceKm = haversineKm(sub.lat, sub.lon, event.lat, event.lon);
    if (distanceKm > sub.radiusKm) continue;
    if (event.mag < sub.notifyMag) continue;
    const tier = event.mag >= sub.alarmMag ? "alarm" : "notify";
    const candidate = { sub, distanceKm, tier };
    if (!best) {
      best = candidate;
      continue;
    }
    if (candidate.tier === "alarm" && best.tier !== "alarm") {
      best = candidate;
    } else if (candidate.tier === best.tier && candidate.distanceKm < best.distanceKm) {
      best = candidate;
    }
  }
  return best;
}

/**
 * Build the push payload for a single device against a single event's best
 * match.
 */
function buildPush(device, event, match) {
  const { sub, distanceKm, tier } = match;
  return {
    token: device.token,
    lang: device.lang,
    tier,
    matchedLabel: sub.label,
    distanceKm,
    data: {
      id: event.id,
      lat: event.lat,
      lon: event.lon,
      depthKm: event.depthKm,
      originTs: event.originTs,
      mag: event.mag,
      tier,
      mmi: mmi(event.mag, distanceKm),
      matchedLabel: sub.label,
      distanceKm,
      userLat: sub.lat,
      userLon: sub.lon,
      lang: device.lang,
    },
  };
}

/**
 * Match a batch of new events against a batch of devices. For each device,
 * across all matching events, picks the single strongest match (alarm beats
 * notify, then higher mag, then smaller distance) and emits exactly one
 * push for it. Devices with no match produce no push.
 */
export function matchEvents(events, devices) {
  const pushes = [];
  for (const device of devices) {
    let bestForDevice = null; // { event, match }
    for (const event of events) {
      const match = bestSubscriptionMatch(device, event);
      if (!match) continue;
      if (!bestForDevice) {
        bestForDevice = { event, match };
        continue;
      }
      const cur = bestForDevice.match;
      if (match.tier === "alarm" && cur.tier !== "alarm") {
        bestForDevice = { event, match };
      } else if (match.tier === cur.tier) {
        if (event.mag > bestForDevice.event.mag) {
          bestForDevice = { event, match };
        } else if (
          event.mag === bestForDevice.event.mag &&
          match.distanceKm < cur.distanceKm
        ) {
          bestForDevice = { event, match };
        }
      }
    }
    if (bestForDevice) {
      pushes.push(buildPush(device, bestForDevice.event, bestForDevice.match));
    }
  }
  return pushes;
}
