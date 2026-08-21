package app.tda

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
}
