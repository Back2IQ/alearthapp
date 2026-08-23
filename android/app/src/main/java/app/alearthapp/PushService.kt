package app.alearthapp

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Empfängt FCM-Data-Messages vom Push-Backend — auch bei geschlossener App / Standby, weil
 * es reine Data-Messages sind. Laute Stufe ("alarm") → DND-durchdringender Vollbild-Alarm
 * über [Alarm.postFullScreen]; leise Stufe ("notify") → normale Benachrichtigung.
 */
class PushService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        Prefs.init(applicationContext)
        Prefs.fcmToken = token
        // Bei neuem Token erneut beim Backend registrieren (falls Abos bekannt).
        PushRegistrar.register(applicationContext)
    }

    override fun onMessageReceived(msg: RemoteMessage) {
        Prefs.init(applicationContext)
        val d = msg.data
        val id = d["id"] ?: return
        d["lang"]?.takeIf { it.isNotEmpty() }?.let { Prefs.languageTag = it }
        val mmi = d["mmi"]?.toDoubleOrNull() ?: 0.0
        val isTest = d["test"] == "true"
        val tierStr = d["tier"] ?: "notify"
        val loud = tierStr == "alarm" || tierStr == "P0" || tierStr == "P2"
        if (loud) {
            val p = Alarm.Payload(
                id = id,
                lat = d["lat"]?.toDoubleOrNull() ?: 0.0,
                lon = d["lon"]?.toDoubleOrNull() ?: 0.0,
                depthKm = d["depthKm"]?.toDoubleOrNull() ?: 10.0,
                originTs = d["originTs"]?.toLongOrNull() ?: System.currentTimeMillis(),
                userLat = d["userLat"]?.toDoubleOrNull() ?: 0.0,
                userLon = d["userLon"]?.toDoubleOrNull() ?: 0.0,
                cityName = (d["matchedLabel"] ?: d["cityName"] ?: ""),
                sound = d["sound"]?.let { it != "false" } ?: true
            )
            Alarm.postFullScreen(applicationContext, p)
            val tier = when (tierStr) { "P2" -> Eew.Tier.P2; "P1" -> Eew.Tier.P1; else -> Eew.Tier.P0 }
            Alarm.maybeArm(applicationContext, mmi, tier, isTest)
        } else {
            val mag = d["mag"] ?: "?"
            val place = (d["matchedLabel"]?.takeIf { it.isNotEmpty() } ?: d["cityName"] ?: "")
            Alarm.postQuiet(
                applicationContext,
                title = applicationContext.withAppLocale().getString(R.string.alert_title_p0),
                body = "M$mag · $place"
            )
        }
    }
}
