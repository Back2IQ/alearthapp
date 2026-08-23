package app.tda

import android.content.Context
import android.content.res.Configuration
import android.util.TypedValue
import java.util.Locale

/** Resolves a theme color attribute (e.g. R.attr.colorTierP0) to an ARGB int. */
fun Context.themeColor(attrRes: Int): Int {
    val tv = TypedValue()
    theme.resolveAttribute(attrRes, tv, true)
    return tv.data
}

/**
 * Wraps this context so its resources resolve in the user-selected UI language
 * ([Prefs.languageTag], pushed from the web layer via the alarm payload). Used by the
 * native alarm/beacon activities in attachBaseContext so a warning always appears in the
 * language the user picked in the app — not the system locale.
 */
fun Context.withAppLocale(): Context {
    Prefs.init(this)
    val locale = Locale.forLanguageTag(Prefs.languageTag)
    Locale.setDefault(locale)
    val config = Configuration(resources.configuration)
    config.setLocale(locale)
    return createConfigurationContext(config)
}
