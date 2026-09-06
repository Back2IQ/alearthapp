package app.alert2iq

import android.content.Context
import android.util.Base64
import java.security.SecureRandom

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

    /** Lädt die Kennung aus Prefs, rotiert bei Tageswechsel, persistiert, gibt hash. */
    fun current(ctx: Context, todayEpochDay: Long): String {
        Prefs.init(ctx)
        val stored = if (Prefs.anonIdHash.isNotEmpty() && Prefs.anonIdDay >= 0)
            AnonId(Prefs.anonIdHash, Prefs.anonIdDay) else null
        val next = rotate(stored, todayEpochDay) { randomHash() }
        if (next.hash != Prefs.anonIdHash || next.dayEpoch != Prefs.anonIdDay) {
            Prefs.anonIdHash = next.hash
            Prefs.anonIdDay = next.dayEpoch
        }
        return next.hash
    }

    private fun randomHash(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.URL_SAFE)
    }
}
