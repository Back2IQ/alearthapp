package app.alearthapp

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * Small local preference store (SharedPreferences-backed) for the choices the spec
 * asks the start screen to offer: location, language, theme mode and the
 * colorblind-safe palette switch. No backend, no network -- everything here is
 * purely local UI state.
 */
object Prefs {
    private const val FILE = "tda_prefs"
    private const val KEY_LANGUAGE = "language"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_COLORBLIND = "colorblind_safe"
    private const val KEY_CITY = "city_id"
    private const val KEY_BEACON_ENABLED = "beacon_enabled"
    private const val KEY_BEACON_MMI = "beacon_mmi_threshold"
    private const val KEY_GRACE_MIN = "grace_minutes"
    private const val KEY_DEADMAN_SEC = "deadman_countdown_sec"
    private const val KEY_SIG_WHISTLE = "signal_whistle"
    private const val KEY_SIG_SCREEN = "signal_screen_strobe"
    private const val KEY_SIG_TORCH = "signal_torch_strobe"
    private const val KEY_DND_OPTIN = "dnd_bypass_optin"
    private const val KEY_HAS_PET = "has_pet"
    private const val KEY_HAS_KIDS = "has_kids"
    private const val KEY_HAS_CAR = "has_car"
    private const val KEY_HOUSEHOLD = "household_size"
    private const val KEY_OWNED = "owned_keys"
    private const val KEY_ONBOARDED = "onboarded"
    private const val KEY_ALERT_RADIUS_KM = "alert_radius_km"
    private const val KEY_MIN_MAG = "min_mag"
    private const val KEY_ALARM_SOUND_URI = "alarm_sound_uri"
    private const val KEY_FCM_TOKEN = "fcm_token"
    private const val KEY_BACKEND_URL = "backend_url"
    private const val KEY_PUSH_SUBS = "push_subscriptions"
    private const val KEY_LAST_ALARM_ID = "last_alarm_id"
    private const val KEY_LAST_ALARM_TS = "last_alarm_ts"
    private const val KEY_CROWD_ENABLED = "crowdsourcing_enabled"
    private const val KEY_ANON_ID_HASH = "anon_id_hash"
    private const val KEY_ANON_ID_DAY = "anon_id_day"
    private const val KEY_LAST_PING_MS = "last_ping_ms"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        if (!::prefs.isInitialized) {
            prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        }
    }

    enum class ThemeMode { SYSTEM, LIGHT, DARK }

    var languageTag: String
        get() = prefs.getString(KEY_LANGUAGE, "en") ?: "en"
        set(value) { prefs.edit().putString(KEY_LANGUAGE, value).apply() }

    var themeMode: ThemeMode
        get() = ThemeMode.valueOf(prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name)
        set(value) { prefs.edit().putString(KEY_THEME_MODE, value.name).apply() }

    var colorblindSafe: Boolean
        get() = prefs.getBoolean(KEY_COLORBLIND, false)
        set(value) { prefs.edit().putBoolean(KEY_COLORBLIND, value).apply() }

    var cityId: String
        get() = prefs.getString(KEY_CITY, Eew.CITIES.first().id) ?: Eew.CITIES.first().id
        set(value) { prefs.edit().putString(KEY_CITY, value).apply() }

    fun selectedCity(): Eew.City = Eew.CITIES.find { it.id == cityId } ?: Eew.CITIES.first()

    /** Applies the persisted day/night preference; call before setContentView. */
    fun applyNightMode() {
        val mode = when (themeMode) {
            ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
        }
        if (AppCompatDelegate.getDefaultNightMode() != mode) {
            AppCompatDelegate.setDefaultNightMode(mode)
        }
    }

    /** Applies the persisted locale at runtime (AppCompat per-app language). */
    fun applyLocale() {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTag))
    }

    /** Style resource to apply via Activity.setTheme() before setContentView. */
    fun themeStyleRes(alert: Boolean): Int = if (colorblindSafe) {
        if (alert) R.style.Theme_Tda_ColorblindSafe_Alert else R.style.Theme_Tda_ColorblindSafe
    } else {
        if (alert) R.style.Theme_Tda_Alert else R.style.Theme_Tda
    }

    var beaconEnabled: Boolean
        get() = prefs.getBoolean(KEY_BEACON_ENABLED, true)
        set(v) { prefs.edit().putBoolean(KEY_BEACON_ENABLED, v).apply() }

    var beaconMmiThreshold: Int
        get() = prefs.getInt(KEY_BEACON_MMI, 7).coerceIn(5, 9)
        set(v) { prefs.edit().putInt(KEY_BEACON_MMI, v.coerceIn(5, 9)).apply() }

    var graceMinutes: Int
        get() = prefs.getInt(KEY_GRACE_MIN, 5).coerceIn(0, 30)
        set(v) { prefs.edit().putInt(KEY_GRACE_MIN, v.coerceIn(0, 30)).apply() }

    var deadmanCountdownSec: Int
        get() = prefs.getInt(KEY_DEADMAN_SEC, 60).coerceIn(15, 300)
        set(v) { prefs.edit().putInt(KEY_DEADMAN_SEC, v.coerceIn(15, 300)).apply() }

    var signalWhistle: Boolean
        get() = prefs.getBoolean(KEY_SIG_WHISTLE, true)
        set(v) { prefs.edit().putBoolean(KEY_SIG_WHISTLE, v).apply() }

    var signalScreenStrobe: Boolean
        get() = prefs.getBoolean(KEY_SIG_SCREEN, true)
        set(v) { prefs.edit().putBoolean(KEY_SIG_SCREEN, v).apply() }

    var signalTorchStrobe: Boolean
        get() = prefs.getBoolean(KEY_SIG_TORCH, true)
        set(v) { prefs.edit().putBoolean(KEY_SIG_TORCH, v).apply() }

    var dndBypassOptIn: Boolean
        get() = prefs.getBoolean(KEY_DND_OPTIN, false)
        set(v) { prefs.edit().putBoolean(KEY_DND_OPTIN, v).apply() }

    /** Baut die reine [SafetyConfig] aus den persistierten Parametern. */
    fun safetyConfig(): SafetyConfig = SafetyConfig(
        beaconEnabled = beaconEnabled,
        mmiThreshold = beaconMmiThreshold,
        graceMillis = graceMinutes * 60_000L,
        deadmanMillis = deadmanCountdownSec * 1000L
    )

    var hasPet: Boolean
        get() = prefs.getBoolean(KEY_HAS_PET, false)
        set(v) { prefs.edit().putBoolean(KEY_HAS_PET, v).apply() }

    var hasKids: Boolean
        get() = prefs.getBoolean(KEY_HAS_KIDS, false)
        set(v) { prefs.edit().putBoolean(KEY_HAS_KIDS, v).apply() }

    var hasCar: Boolean
        get() = prefs.getBoolean(KEY_HAS_CAR, false)
        set(v) { prefs.edit().putBoolean(KEY_HAS_CAR, v).apply() }

    var householdSize: Int
        get() = prefs.getInt(KEY_HOUSEHOLD, 1).coerceIn(1, 12)
        set(v) { prefs.edit().putInt(KEY_HOUSEHOLD, v.coerceIn(1, 12)).apply() }

    /** Set der Item-Keys, die der Nutzer als „hab ich" markiert hat. */
    var ownedKeys: Set<String>
        get() = prefs.getStringSet(KEY_OWNED, emptySet())?.toSet() ?: emptySet()
        set(v) { prefs.edit().putStringSet(KEY_OWNED, HashSet(v)).apply() }

    /** Setzt/entfernt einen einzelnen Key (SharedPreferences-Set nie in-place mutieren). */
    fun setOwned(key: String, owned: Boolean) {
        val next = ownedKeys.toMutableSet()
        if (owned) next.add(key) else next.remove(key)
        ownedKeys = next
    }

    /** Ob der Nutzer das Onboarding einmal abgeschlossen (oder übersprungen) hat. */
    var onboarded: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDED, false)
        set(v) { prefs.edit().putBoolean(KEY_ONBOARDED, v).apply() }

    /** Alarmradius in km (Startseite/Disasters-Filter „in der Nähe"). */
    var alertRadiusKm: Int
        get() = prefs.getInt(KEY_ALERT_RADIUS_KM, 300).coerceIn(50, 1000)
        set(v) { prefs.edit().putInt(KEY_ALERT_RADIUS_KM, v.coerceIn(50, 1000)).apply() }

    /** Mindest-Magnitude, ab der Beben interessieren. */
    var minMagnitude: Double
        get() = prefs.getFloat(KEY_MIN_MAG, 3.5f).toDouble().coerceIn(1.0, 8.0)
        set(v) { prefs.edit().putFloat(KEY_MIN_MAG, v.toFloat()).apply() }

    /** Vom Nutzer gewählter Alarmton (content:// URI); leer = System-Alarmton. */
    var alarmSoundUri: String
        get() = prefs.getString(KEY_ALARM_SOUND_URI, "") ?: ""
        set(v) { prefs.edit().putString(KEY_ALARM_SOUND_URI, v).apply() }

    /** Aktueller FCM-Push-Token dieses Geräts (von [PushService.onNewToken]). */
    var fcmToken: String
        get() = prefs.getString(KEY_FCM_TOKEN, "") ?: ""
        set(v) { prefs.edit().putString(KEY_FCM_TOKEN, v).apply() }

    /** Basis-URL des Push-Backends (z.B. https://alearthapp.duckdns.org); leer = keine Registrierung. */
    var backendUrl: String
        get() = prefs.getString(KEY_BACKEND_URL, "") ?: ""
        set(v) { prefs.edit().putString(KEY_BACKEND_URL, v).apply() }

    /** Zuletzt von der Web-Oberfläche gemeldete Abo-Liste (JSON: {lang, subscriptions:[...]}). */
    var pushSubscriptions: String
        get() = prefs.getString(KEY_PUSH_SUBS, "") ?: ""
        set(v) { prefs.edit().putString(KEY_PUSH_SUBS, v).apply() }

    /** Zuletzt ausgelöste Alarm-Event-ID — geteiltes Dedup zwischen Web- und Push-Pfad. */
    var lastAlarmId: String
        get() = prefs.getString(KEY_LAST_ALARM_ID, "") ?: ""
        set(v) { prefs.edit().putString(KEY_LAST_ALARM_ID, v).apply() }

    /** Zeitstempel (ms) des letzten Alarms — für das Dedup-Zeitfenster. */
    var lastAlarmTs: Long
        get() = prefs.getLong(KEY_LAST_ALARM_TS, 0L)
        set(v) { prefs.edit().putLong(KEY_LAST_ALARM_TS, v).apply() }

    /** Opt-in: Handy horcht am Strom und meldet Erschütterungen. Standard aus. */
    var crowdsourcingEnabled: Boolean
        get() = prefs.getBoolean(KEY_CROWD_ENABLED, false)
        set(v) { prefs.edit().putBoolean(KEY_CROWD_ENABLED, v).apply() }

    /** Rohspeicher der rotierenden anonymen Kennung (siehe [AnonDeviceId]). */
    var anonIdHash: String
        get() = prefs.getString(KEY_ANON_ID_HASH, "") ?: ""
        set(v) { prefs.edit().putString(KEY_ANON_ID_HASH, v).apply() }

    var anonIdDay: Long
        get() = prefs.getLong(KEY_ANON_ID_DAY, -1L)
        set(v) { prefs.edit().putLong(KEY_ANON_ID_DAY, v).apply() }

    /** Zeitstempel (ms) des zuletzt gesendeten Aktiv-Pings. */
    var lastPingMs: Long
        get() = prefs.getLong(KEY_LAST_PING_MS, 0L)
        set(v) { prefs.edit().putLong(KEY_LAST_PING_MS, v).apply() }
}
