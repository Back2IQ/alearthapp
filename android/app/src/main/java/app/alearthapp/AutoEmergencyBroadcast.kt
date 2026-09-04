package app.alearthapp

import android.content.Context
import android.telephony.SmsManager
import android.util.Log

object AutoEmergencyBroadcast {

    private const val TAG = "AutoEmergencyBroadcast"

    fun canSendAutoBroadcast(context: Context): Boolean {
        val tier = TierSecurityManager.getActiveTier(context)
        return tier.hasAutoSms
    }

    fun triggerAutoSms(context: Context, latitude: Double, longitude: Double, intensityText: String): Boolean {
        if (!canSendAutoBroadcast(context)) {
            Log.w(TAG, "Auto SMS not allowed in current tier")
            return false
        }

        val phone = Prefs.emergencySmsPhone
        if (phone.isBlank()) {
            Log.w(TAG, "No emergency phone configured")
            return false
        }

        val msg = "EMERGENCY ALERT: Strong shaking ($intensityText) detected at https://maps.google.com/?q=$latitude,$longitude -- Sent via Alert2IQ Guardian"

        return try {
            val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            smsManager.sendTextMessage(phone, null, msg, null, null)
            Log.i(TAG, "Auto emergency SMS sent successfully to $phone")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send auto emergency SMS", e)
            false
        }
    }
}
