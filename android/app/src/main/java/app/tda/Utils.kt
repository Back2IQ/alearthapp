package app.tda

import android.content.Context
import android.util.TypedValue

/** Resolves a theme color attribute (e.g. R.attr.colorTierP0) to an ARGB int. */
fun Context.themeColor(attrRes: Int): Int {
    val tv = TypedValue()
    theme.resolveAttribute(attrRes, tv, true)
    return tv.data
}
