package app.alearthapp

import android.content.Context
import android.telephony.SmsManager
import android.util.Log

/**
 * Automatischer Notfall-Broadcast mit Schutz gegen Akku-Entleerung & Empfänger-Überlastung:
 * - Strenger 5-Minuten-Drosselschutz (Throttle): Keine SMS-Stürme bei Nachbeben.
 * - Ruhiger, strukturierter Infotext mit Standort & Akkustand (keine Panikmache).
 */
object AutoEmergencyBroadcast {

    private const val TAG = "AutoEmergencyBroadcast"
    const val MIN_BROADCAST_INTERVAL_MS = 5 * 60 * 1000L // 5 Minuten Drosselung

    private var lastBroadcastTsMs: Long = 0L

    fun canSendAutoBroadcast(context: Context): Boolean {
        val tier = TierSecurityManager.getActiveTier(context)
        return tier.hasAutoSms
    }

    fun triggerAutoSms(
        context: Context,
        latitude: Double,
        longitude: Double,
        intensityText: String,
        batteryPercent: Int = 50,
        userName: String = ""
    ): Boolean {
        if (!canSendAutoBroadcast(context)) {
            Log.w(TAG, "Auto SMS not allowed in current tier")
            return false
        }

        val now = System.currentTimeMillis()
        if (now - lastBroadcastTsMs < MIN_BROADCAST_INTERVAL_MS) {
            Log.i(TAG, "Auto SMS throttled (5 min protection against battery drain & recipient stress)")
            return false
        }

        val phone = Prefs.emergencySmsPhone
        if (phone.isBlank()) {
            Log.w(TAG, "No emergency phone configured")
            return false
        }

        val namePrefix = if (userName.isNotBlank()) "$userName: " else ""
        // Ruhiger, strukturierter Notfalltext: Sachlich, koordinatenbasiert, kein Panik-Dauerfeuer
        val msg = "${namePrefix}Alert2IQ Notfallmeldung: Starkes Beben ($intensityText). Letzter Standort: https://maps.google.com/?q=$latitude,$longitude (Akku: $batteryPercent%). Offline-BLE-Signal aktiv. Bitte Ruhe bewahren."

        return try {
            val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            smsManager.sendTextMessage(phone, null, msg, null, null)
            lastBroadcastTsMs = now
            Log.i(TAG, "Auto emergency SMS sent successfully (throttled 5 min) to $phone")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send auto emergency SMS", e)
            false
        }
    }

    fun resetThrottleForTesting() {
        lastBroadcastTsMs = 0L
    }
}
