package app.alearthapp

import android.content.Context
import android.os.PowerManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class PushService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        Prefs.init(applicationContext)
        Prefs.fcmToken = token
    }

    override fun onMessageReceived(msg: RemoteMessage) {
        // WakeLock halten, damit das Gerät nicht vor dem Starten des Alarms wieder einschläft
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "app.alearthapp:PushWakeLock")
        wakeLock?.acquire(10_000L)

        Prefs.init(applicationContext)
        val d = msg.data
        val id = d["id"] ?: return
        val ver = d["ver"]?.toIntOrNull() ?: 1
        val lat = d["lat"]?.toDoubleOrNull() ?: return
        val lon = d["lon"]?.toDoubleOrNull() ?: return
        val mag = d["mag"]?.toDoubleOrNull() ?: return
        val depthKm = d["depth_km"]?.toDoubleOrNull() ?: 10.0
        val originTs = d["origin_ts"]?.toLongOrNull() ?: System.currentTimeMillis()
        val isTest = d["test"] == "1"
        val tier = when (d["tier"]) {
            "P2" -> Eew.Tier.P2
            "P0" -> Eew.Tier.P0
            else -> Eew.Tier.P1
        }

        val dec = PushEval.evaluate(lat, lon, mag, Prefs.pushSubscriptions)

        val action = AlertDeduplicator.evaluate(id, ver, dec.level)
        if (action == AlertDeduplicator.Action.IGNORE) {
            return
        }

        when (dec.level) {
            PushEval.Level.DROP -> return
            PushEval.Level.NOTIFY -> {
                val title = applicationContext.withAppLocale().getString(R.string.alert_title_p0)
                val body = "M" + (d["mag"] ?: "?") + " · " + dec.cityName
                Alarm.postQuiet(applicationContext, title = title, body = body)
            }
            PushEval.Level.ALARM -> {
                val payload = Alarm.Payload(
                    id = id,
                    lat = lat,
                    lon = lon,
                    depthKm = depthKm,
                    originTs = originTs,
                    userLat = dec.userLat,
                    userLon = dec.userLon,
                    cityName = dec.cityName,
                    sound = d["sound"]?.let { it != "false" } ?: true
                )
                Alarm.dispatch(
                    applicationContext,
                    payload,
                    tier,
                    dec.mmi,
                    isTest,
                    foreground = false
                )
            }
        }
    }
}