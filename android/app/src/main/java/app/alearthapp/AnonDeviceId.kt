package app.alearthapp

/** Anonyme Gerätekennung + ihr Ausgabetag (Tage seit Epoch). */
data class AnonId(val hash: String, val dayEpoch: Long)

/**
 * Rotierende anonyme Kennung: erfüllt den `device_hash` des Draht-Vertrags für
 * Kurzzeit-Dedup/Reputation, ohne Verfolgbarkeit über Tage. Kein Konto, keine
 * Ad-ID/IMEI. Die Rotationsentscheidung ist reine Logik (hier), die Erzeugung
 * der Zufalls-ID + Prefs-Persistenz sitzt in [Prefs] (siehe Task 9).
 */
object AnonDeviceId {
    fun rotate(stored: AnonId?, todayEpochDay: Long, randomHash: () -> String): AnonId {
        if (stored != null && stored.dayEpoch == todayEpochDay) return stored
        return AnonId(randomHash(), todayEpochDay)
    }
}
