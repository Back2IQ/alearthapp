package app.alearthapp

import org.json.JSONObject

/**
 * Reine, testbare Auswertung eines Broadcast-Bebens gegen die lokal beobachteten Orte
 * ([Prefs.pushSubscriptions]). Spiegelt die Web-Entscheidung (webapp/index.html::
 * notifyNativeAlert) + [Eew.mmi]: laut bei dist<=radiusKm && mag>=alarmMag, sonst leise
 * bei mag>=notifyMag, sonst verwerfen. Der Server-Topic-Broadcast liefert nur Event-Daten;
 * userLat/userLon/cityName/mmi werden hier lokal aus dem nächstgelegenen passenden Ort bestimmt.
 */
object PushEval {
    enum class Level { DROP, NOTIFY, ALARM }

    data class Decision(
        val level: Level,
        val userLat: Double,
        val userLon: Double,
        val cityName: String,
        val distKm: Double,
        val mmi: Double,
    )

    fun evaluate(lat: Double, lon: Double, mag: Double, subsJson: String): Decision {
        val drop = Decision(Level.DROP, 0.0, 0.0, "", 0.0, 0.0)
        val arr = runCatching { JSONObject(subsJson).optJSONArray("subscriptions") }
            .getOrNull() ?: return drop
        var best: Decision? = null
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (!o.has("lat") || !o.has("lon")) continue
            val sLat = o.optDouble("lat"); val sLon = o.optDouble("lon")
            val notifyMag = o.optDouble("notifyMag", 4.0)
            val alarmMag = o.optDouble("alarmMag", 6.0)
            val radiusKm = o.optDouble("radiusKm", 150.0)
            val dist = Eew.haversineKm(sLat, sLon, lat, lon)
            if (dist > radiusKm) continue
            val level = when {
                mag >= alarmMag -> Level.ALARM
                mag >= notifyMag -> Level.NOTIFY
                else -> continue
            }
            val mmi = Eew.mmi(mag, dist)
            val cand = Decision(level, sLat, sLon, o.optString("label"), dist, mmi)
            if (best == null || level.ordinal > best!!.level.ordinal ||
                (level == best!!.level && mmi > best!!.mmi)) {
                best = cand
            }
        }
        return best ?: drop
    }
}