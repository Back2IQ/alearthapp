package app.alert2iq

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.webkit.JavascriptInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
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
            cityName = o.optString("cityName"), sound = o.optBoolean("sound", true),
            mag = o.optDouble("mag", 6.0),
            tier = tier
        )
        activity.runOnUiThread {
            Alarm.dispatch(activity, payload, tier, mmi, isTest, foreground = true)
        }
    }

    /**
     * Web-Onboarding meldet die gewählte Sprache sofort (beim Chip-Klick) an die native Schicht.
     * Speichert den Language-Tag in Prefs und startet die Activity neu, damit attachBaseContext
     * die neue Locale aufgreift — ohne das gesamte Onboarding zu verlieren.
     */
    @JavascriptInterface
    fun setLanguage(languageTag: String) {
        if (languageTag.isBlank()) return
        Prefs.init(activity)
        val current = Prefs.languageTag
        if (current == languageTag) return   // Keine Änderung → kein Neustart nötig
        Prefs.languageTag = languageTag
        Prefs.applyLocale()                  // AppCompatDelegate informieren
        // recreate() startet die Activity neu damit attachBaseContext die neue Locale
        // aufgreift. Die WebApp liest die Sprache aus localStorage (savePrefs() wurde
        // kurz vorher aufgerufen) und zeigt das Onboarding weiterhin an (Settings.onboarded=false).
        activity.runOnUiThread { activity.recreate() }
    }

    /** Web-Seite meldet die aktuell beobachteten Orte + Schwellen, damit der Server (FCM-Push)
     * das Gerät auch bei geschlossener App warnen kann.
     * json: { lang, subscriptions:[{ lat, lon, label, notifyMag, alarmMag, radiusKm }] }
     */
    @JavascriptInterface
    fun syncSubscriptions(json: String) {
        Prefs.init(activity)
        Prefs.pushSubscriptions = json
        runCatching { JSONObject(json).optString("lang") }.getOrNull()
            ?.takeIf { it.isNotEmpty() }?.let { Prefs.languageTag = it }
        PushTopics.sync(activity.applicationContext, json)
    }

    /** Web-Seite bittet um Auswahl eines eigenen Alarmtons → nativer Ringtone-Picker. */
    @JavascriptInterface
    fun pickAlarmSound() {
        activity.runOnUiThread { (activity as? MainActivity)?.pickAlarmSound() }
    }

    /** Ist der DND-durchdringende Alarm aktiv (Opt-in + System-Policy-Zugriff gewährt)? */
    @JavascriptInterface
    fun dndActive(): Boolean {
        Prefs.init(activity)
        return Prefs.dndBypassOptIn && DndAccess.isGranted(activity)
    }

    /**
     * Schaltet den DND-Bypass. Bei Aktivierung ohne System-Zugriff öffnet sich der
     * System-Dialog „Zugriff auf Nicht stören"; die Kanäle werden neu gesetzt, damit der
     * kritische Kanal DND durchbricht.
     */
    @JavascriptInterface
    fun setDndBypass(enable: Boolean) {
        activity.runOnUiThread {
            Prefs.init(activity)
            Prefs.dndBypassOptIn = enable
            if (enable && !DndAccess.isGranted(activity)) {
                runCatching { activity.startActivity(DndAccess.requestIntent()) }
            }
            NotificationChannels.ensure(activity)
        }
    }

    /** Ist der Crowdsourcing-Opt-in aktiv (Web-Anzeige des Einstellungs-Schalters)? */
    @JavascriptInterface
    fun crowdsourcingEnabled(): Boolean {
        Prefs.init(activity)
        return Prefs.crowdsourcingEnabled
    }

    /**
     * Schaltet das Crowdsourcing-Opt-in. Bei Aktivierung wird die grobe Standort-Permission
     * angefragt (der Sensor-Dienst degradiert ohne Zugriff einfach zu keiner Zellen-Meldung)
     * und der Dienst gestartet, falls das Gerät gerade lädt; bei Deaktivierung wird er gestoppt.
     */
    @JavascriptInterface
    fun setCrowdsourcing(enable: Boolean) {
        activity.runOnUiThread {
            Prefs.init(activity)
            Prefs.crowdsourcingEnabled = enable
            if (enable) {
                if (androidx.core.content.ContextCompat.checkSelfPermission(activity, android.Manifest.permission.ACCESS_COARSE_LOCATION)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    runCatching {
                        androidx.core.app.ActivityCompat.requestPermissions(
                            activity, arrayOf(android.Manifest.permission.ACCESS_COARSE_LOCATION), 102
                        )
                    }
                }
                val bm = activity.getSystemService(android.content.Context.BATTERY_SERVICE) as android.os.BatteryManager
                if (bm.isCharging) QuakeSensorService.start(activity)
            } else {
                QuakeSensorService.stop(activity)
            }
        }
    }

    /** Öffnet die System-App-Einstellungen (für Autostart/Akku auf Xiaomi/Huawei/Oppo). */
    @JavascriptInterface
    fun openAppSettings() {
        activity.runOnUiThread {
            val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", activity.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { activity.startActivity(i) }
        }
    }

    /** Öffnet die Akku-Optimierungs-Einstellungen (Schutz vor Doze Mode). */
    @JavascriptInterface
    fun openBatterySettings() {
        activity.runOnUiThread {
            val i = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { activity.startActivity(i) }
        }
    }

    @JavascriptInterface
    fun isMaxVolumeOnAlarm(): Boolean {
        Prefs.init(activity)
        return Prefs.maxVolumeOnAlarm
    }

    @JavascriptInterface
    fun setMaxVolumeOnAlarm(enable: Boolean) {
        Prefs.init(activity)
        Prefs.maxVolumeOnAlarm = enable
    }

    @JavascriptInterface
    fun isHapticCountdown(): Boolean {
        Prefs.init(activity)
        return Prefs.hapticCountdown
    }

    @JavascriptInterface
    fun setHapticCountdown(enable: Boolean) {
        Prefs.init(activity)
        Prefs.hapticCountdown = enable
    }

    @JavascriptInterface
    fun isTorchOnAlarm(): Boolean {
        Prefs.init(activity)
        return Prefs.torchOnAlarm
    }

    @JavascriptInterface
    fun setTorchOnAlarm(enable: Boolean) {
        Prefs.init(activity)
        Prefs.torchOnAlarm = enable
    }

    @JavascriptInterface
    fun getEmergencySmsPhone(): String {
        Prefs.init(activity)
        return Prefs.emergencySmsPhone
    }

    @JavascriptInterface
    fun setEmergencySmsPhone(phone: String) {
        Prefs.init(activity)
        Prefs.emergencySmsPhone = phone
    }

    /** Öffnet die native Katastrophen- & Erdbeben-Übersicht. */
    @JavascriptInterface
    fun openNativeDisasters() {
        activity.runOnUiThread {
            val intent = Intent(activity, DisastersActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { activity.startActivity(intent) }
        }
    }

    /** Öffnet die native OSM-Karte, optional zentriert auf gegebene Koordinaten. */
    @JavascriptInterface
    fun openNativeMap(lat: Double = 0.0, lon: Double = 0.0) {
        activity.runOnUiThread {
            val intent = Intent(activity, MapActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (lat != 0.0 && lon != 0.0) {
                    putExtra(MapActivity.EXTRA_FOCUS_LAT, lat)
                    putExtra(MapActivity.EXTRA_FOCUS_LON, lon)
                }
            }
            runCatching { activity.startActivity(intent) }
        }
    }

    /** Web-Seite meldet Entwarnung (Alarm geschlossen) → Reste stoppen. */
    @JavascriptInterface
    fun onAlertCleared() {
        activity.runOnUiThread {
            Alarm.clearDedup(activity)
            runCatching { AlarmService.stop(activity) }
        }
    }

    /** Speichert Notfallprofil im verschlüsselten AES-GCM-256 Tresor. */
    @JavascriptInterface
    fun saveVaultProfile(json: String): Boolean {
        return runCatching {
            val obj = JSONObject(json)
            val profile = EmergencyProfile.fromJson(obj)
            EmergencyVaultManager.saveProfile(activity, profile)
        }.getOrDefault(false)
    }

    /** Lädt Notfallprofil aus dem verschlüsselten AES-GCM-256 Tresor. */
    @JavascriptInterface
    fun loadVaultProfile(): String {
        return runCatching {
            EmergencyVaultManager.loadProfile(activity).toJson().toString()
        }.getOrDefault("{}")
    }

    /** Löscht Notfallprofil restlos (DSGVO-Recht auf Vergessenwerden). */
    @JavascriptInterface
    fun clearVaultProfile(): Boolean {
        return EmergencyVaultManager.clearProfile(activity)
    }

    /** Ermittelt den aktuellen kryptografisch geschützten AppTier (FREE, PRO, GUARDIAN). */
    @JavascriptInterface
    fun getTier(): String {
        Prefs.init(activity)
        return TierSecurityManager.getActiveTier(activity).name
    }

    /** Lädt überwachte Multi-Location Orte. */
    @JavascriptInterface
    fun getMonitoredLocations(): String {
        return runCatching {
            val list = MultiLocationManager.getLocations(activity)
            val arr = org.json.JSONArray()
            for (loc in list) {
                arr.put(loc.toJson())
            }
            arr.toString()
        }.getOrDefault("[]")
    }

    /** Speichert überwachte Multi-Location Orte. */
    @JavascriptInterface
    fun saveMonitoredLocations(json: String): Boolean {
        return runCatching {
            val arr = org.json.JSONArray(json)
            val list = mutableListOf<MonitoredLocation>()
            for (i in 0 until arr.length()) {
                list.add(MonitoredLocation.fromJson(arr.getJSONObject(i)))
            }
            MultiLocationManager.saveLocations(activity, list)
            true
        }.getOrDefault(false)
    }

    /** Lädt den Zustand des Smart Go-Bag Notfallrucksacks. */
    @JavascriptInterface
    fun getGoBagStatus(): String {
        return runCatching {
            val items = SmartReadinessManager.getItems(activity)
            val arr = org.json.JSONArray()
            for (item in items) {
                arr.put(item.toJson())
            }
            arr.toString()
        }.getOrDefault("[]")
    }

    /** Speichert den Zustand des Smart Go-Bag Notfallrucksacks. */
    @JavascriptInterface
    fun saveGoBagStatus(json: String): Boolean {
        return runCatching {
            val arr = org.json.JSONArray(json)
            val list = mutableListOf<ReadinessSupplyItem>()
            for (i in 0 until arr.length()) {
                list.add(ReadinessSupplyItem.fromJson(arr.getJSONObject(i)))
            }
            SmartReadinessManager.saveItems(activity, list)
            true
        }.getOrDefault(false)
    }

    /** Teilt den Einladungs-Link für das Guardian Circle Familien-Sicherheitsnetzwerk. */
    @JavascriptInterface
    fun shareGuardianInvite(inviteCode: String) {
        activity.runOnUiThread {
            val inviteUrl = "https://back2iq.com/alert2iq/guardian?invite=$inviteCode"
            val text = "🛡️ Tritt meinem Alert2IQ Notfall-Familiennetzwerk bei: $inviteUrl"
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            runCatching { activity.startActivity(Intent.createChooser(intent, "Guardian Circle Einladung teilen")) }
        }
    }

    /** Evaluation des 3D Seismischen Polarisationsvektors. */
    @JavascriptInterface
    fun evaluatePolarizationVector(ax: Double, ay: Double, az: Double, staLta: Double): String {
        val result = SeismicPolarizationFilter.evaluate(ax, ay, az, staLta)
        return org.json.JSONObject().apply {
            put("isPWaveVector", result.isPWaveVector)
            put("magnitudeG", result.magnitudeG)
            put("dipAngleDeg", result.dipAngleDeg)
            put("staLtaRatio", result.staLtaRatio)
            put("confidenceScore", result.confidenceScore)
        }.toString()
    }

    /** Sub-2ms Lokaler P2P Multicast Subnet Ping im lokalen WLAN / LAN. */
    @JavascriptInterface
    fun broadcastLocalMeshPing(geohash: String, mag: Double, deviceIdHash: String): Boolean {
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            LocalP2PMesh.broadcastLocalAlert(geohash, mag, deviceIdHash)
        }
        return true
    }
}

