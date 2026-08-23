package app.alearthapp

import android.app.Activity
import android.webkit.JavascriptInterface
import org.json.JSONObject

/**
 * JS→native Brücke der Hybrid-Hülle: die Web-Oberfläche ruft bei einem Alarm
 * `AndroidBridge.onAlert(json)` und `AndroidBridge.onAlertCleared()`. Damit greift die
 * lebensrettende Native-Schicht (TP-3), die eine reine Web-Seite NICHT kann:
 * DND-durchdringender Vollbild-Alarm über [AlertActivity] + Wecknotification, und bei
 * starkem Beben die Nachbeben-Wache/Beacon-Kette über [AlarmService].
 *
 * Die eigentliche Alarm-Auslösung liegt in [Alarm], damit Vordergrund (hier) und
 * Hintergrund-Push ([PushService]) exakt denselben Code nutzen.
 *
 * @JavascriptInterface-Methoden laufen auf dem WebView-eigenen Bridge-Thread; alles, was
 * UI/Start berührt, wird auf den Main-Thread der Activity gepostet.
 */
class WebBridge(private val activity: Activity) {

    private var lastAlarmId: String? = null

    /**
     * json: { id, lat, lon, depthKm, originTs, mag, tier ("P0"|"P1"|"P2"), mmi,
     *         userLat, userLon, cityName, sound (optional bool), test (optional bool) }
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
        val payload = Alarm.Payload(
            id = id,
            lat = o.optDouble("lat"), lon = o.optDouble("lon"),
            depthKm = o.optDouble("depthKm", 10.0), originTs = o.optLong("originTs", System.currentTimeMillis()),
            userLat = o.optDouble("userLat"), userLon = o.optDouble("userLon"),
            cityName = o.optString("cityName"), sound = o.optBoolean("sound", true)
        )
        activity.runOnUiThread {
            val firstForId = lastAlarmId != id
            lastAlarmId = id
            if (firstForId) {
                Alarm.postFullScreen(activity, payload)
                Alarm.launchDirect(activity, payload)
            }
            Alarm.maybeArm(activity, mmi, tier, isTest)
        }
    }

    /**
     * Web-Seite meldet die aktuell beobachteten Orte + Schwellen, damit der Server (FCM-Push)
     * das Gerät auch bei geschlossener App warnen kann.
     * json: { lang, subscriptions:[{ lat, lon, label, notifyMag, alarmMag, radiusKm }] }
     */
    @JavascriptInterface
    fun syncSubscriptions(json: String) {
        Prefs.init(activity)
        Prefs.pushSubscriptions = json
        runCatching { JSONObject(json).optString("lang") }.getOrNull()
            ?.takeIf { it.isNotEmpty() }?.let { Prefs.languageTag = it }
        PushRegistrar.register(activity.applicationContext)
    }

    /** Web-Seite bittet um Auswahl eines eigenen Alarmtons → nativer Ringtone-Picker. */
    @JavascriptInterface
    fun pickAlarmSound() {
        activity.runOnUiThread { (activity as? MainActivity)?.pickAlarmSound() }
    }

    /** Web-Seite meldet Entwarnung (Alarm geschlossen) → Reste stoppen. */
    @JavascriptInterface
    fun onAlertCleared() {
        activity.runOnUiThread {
            lastAlarmId = null
            runCatching { AlarmService.stop(activity) }
        }
    }
}
