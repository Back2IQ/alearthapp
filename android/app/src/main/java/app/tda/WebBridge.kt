package app.tda

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.webkit.JavascriptInterface
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.json.JSONObject

/**
 * JS→native Brücke der Hybrid-Hülle: die Web-Oberfläche ruft bei einem Alarm
 * `AndroidBridge.onAlert(json)` und `AndroidBridge.onAlertCleared()`. Damit greift die
 * lebensrettende Native-Schicht (TP-3), die eine reine Web-Seite NICHT kann:
 * DND-durchdringender Vollbild-Alarm über [AlertActivity] + Wecknotification, und bei
 * starkem Beben die Nachbeben-Wache/Beacon-Kette über [AlarmService].
 *
 * @JavascriptInterface-Methoden laufen auf dem WebView-eigenen Bridge-Thread; alles, was
 * UI/Start berührt, wird auf den Main-Thread der Activity gepostet.
 */
class WebBridge(private val activity: Activity) {

    private var lastAlarmId: String? = null

    /**
     * json: { id, lat, lon, depthKm, originTs, mag, tier ("P0"|"P1"|"P2"), mmi,
     *         userLat, userLon, cityName, test (optional bool) }
     */
    @JavascriptInterface
    fun onAlert(json: String) {
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return
        val id = o.optString("id").ifEmpty { return }
        // Gewählte UI-Sprache aus dem Web übernehmen, damit der native Alarm in dieser
        // Sprache erscheint (nicht in der System-Locale).
        Prefs.init(activity)
        o.optString("lang").takeIf { it.isNotEmpty() }?.let { Prefs.languageTag = it }
        val tier = when (o.optString("tier")) {
            "P2" -> Eew.Tier.P2
            "P1" -> Eew.Tier.P1
            else -> Eew.Tier.P0
        }
        val isTest = o.optBoolean("test", false)
        val mmi = o.optDouble("mmi", 0.0)
        val payload = AlertData(
            id = id,
            lat = o.optDouble("lat"), lon = o.optDouble("lon"),
            depthKm = o.optDouble("depthKm", 10.0), originTs = o.optLong("originTs", System.currentTimeMillis()),
            userLat = o.optDouble("userLat"), userLon = o.optDouble("userLon"),
            cityName = o.optString("cityName")
        )
        activity.runOnUiThread {
            val firstForId = lastAlarmId != id
            lastAlarmId = id
            if (firstForId) {
                postFullScreen(payload)
                launchAlert(payload)
            }
            // Nachbeben-Wache/Beacon nur bei starkem, bestätigtem Beben und wenn aktiviert.
            if (!isTest && Prefs.beaconEnabled && mmi >= Prefs.beaconMmiThreshold) {
                AlarmService.arm(activity, mmi = mmi, tier = tier, isTest = false)
            }
        }
    }

    /** Web-Seite meldet Entwarnung (Alarm geschlossen) → Reste stoppen. */
    @JavascriptInterface
    fun onAlertCleared() {
        activity.runOnUiThread {
            lastAlarmId = null
            runCatching { AlarmService.stop(activity) }
        }
    }

    private data class AlertData(
        val id: String, val lat: Double, val lon: Double, val depthKm: Double,
        val originTs: Long, val userLat: Double, val userLon: Double, val cityName: String
    )

    private fun alertIntent(p: AlertData): Intent =
        Intent(activity, AlertActivity::class.java).apply {
            putExtra(AlertActivity.EXTRA_ID, p.id)
            putExtra(AlertActivity.EXTRA_EPI_LAT, p.lat)
            putExtra(AlertActivity.EXTRA_EPI_LON, p.lon)
            putExtra(AlertActivity.EXTRA_DEPTH_KM, p.depthKm)
            putExtra(AlertActivity.EXTRA_ORIGIN_TS, p.originTs)
            putExtra(AlertActivity.EXTRA_USER_LAT, p.userLat)
            putExtra(AlertActivity.EXTRA_USER_LON, p.userLon)
            putExtra(AlertActivity.EXTRA_USER_CITY_NAME, p.cityName)
        }

    private fun launchAlert(p: AlertData) {
        runCatching { activity.startActivity(alertIntent(p)) }
    }

    private fun postFullScreen(p: AlertData) {
        NotificationChannels.ensure(activity)
        val fsi = PendingIntent.getActivity(
            activity, 2, alertIntent(p).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(activity, NotificationChannels.CRITICAL)
            .setSmallIcon(R.drawable.ic_tier_p0)
            .setContentTitle(activity.withAppLocale().getString(R.string.alert_title_p0))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fsi, true)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(activity).notify(4200, n) }
    }
}
