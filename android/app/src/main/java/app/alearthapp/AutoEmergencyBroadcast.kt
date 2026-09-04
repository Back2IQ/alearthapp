package app.alearthapp

import android.content.Context
import android.telephony.SmsManager
import android.util.Log

/**
 * Automatischer Notfall-Broadcast mit präzisem Akku- & Entlastungs-Timing:
 * - Erste SMS: Nach 30 Minuten (wenn nicht manuell als SICHER markiert).
 * - Folge-SMS: Alle 5 Stunden (solange das Notsignal aktiv bleibt).
 * - Ruhiger, strukturierter Infotext mit Standort & Akkustand.
 */
object AutoEmergencyBroadcast {

    private const val TAG = "AutoEmergencyBroadcast"
    const val INITIAL_DELAY_MS = 30 * 60 * 1000L // Erste SMS nach 30 Minuten
    const val REPEAT_INTERVAL_MS = 5 * 60 * 60 * 1000L // Danach alle 5 Stunden

    private var firstBroadcastTsMs: Long = 0L
    private var lastBroadcastTsMs: Long = 0L

    fun canSendAutoBroadcast(context: Context): Boolean {
        val tier = TierSecurityManager.getActiveTier(context)
        return tier.hasAutoSms
    }

    /**
     * Prüft das Zeitfenster (erste SMS nach 30 Min, danach alle 5 Std) und sendet bei Fälligkeit.
     */
    fun checkAndTriggerAutoSms(
        context: Context,
        eventStartTsMs: Long,
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
        val elapsedSinceEvent = now - eventStartTsMs

        // 1. Frühestens nach 30 Minuten erste SMS senden
        if (elapsedSinceEvent < INITIAL_DELAY_MS) {
            Log.i(TAG, "Auto SMS waiting for 30-min grace window (elapsed: ${elapsedSinceEvent / 1000}s)")
            return false
        }

        // 2. Wenn bereits gesendet: Mindestabstand 5 Stunden einhalten
        if (lastBroadcastTsMs > 0L && (now - lastBroadcastTsMs) < REPEAT_INTERVAL_MS) {
            Log.i(TAG, "Auto SMS throttled (5h repeat interval active)")
            return false
        }

        val phone = Prefs.emergencySmsPhone
        if (phone.isBlank()) {
            Log.w(TAG, "No emergency phone configured")
            return false
        }

        val namePrefix = if (userName.isNotBlank()) "$userName: " else ""
        val msg = "${namePrefix}Alert2IQ Notfallstatus: Starkes Beben ($intensityText). Standort: https://maps.google.com/?q=$latitude,$longitude (Akku: $batteryPercent%). BLE-Notsignal aktiv. Bitte Ruhe bewahren."

        return try {
            val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            smsManager.sendTextMessage(phone, null, msg, null, null)
            lastBroadcastTsMs = now
            if (firstBroadcastTsMs == 0L) firstBroadcastTsMs = now
            Log.i(TAG, "Auto emergency SMS sent successfully (30m initial / 5h interval) to $phone")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send auto emergency SMS", e)
            false
        }
    }

    fun reset() {
        firstBroadcastTsMs = 0L
        lastBroadcastTsMs = 0L
    }
}
