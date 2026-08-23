package app.tda

import java.net.URLEncoder

/**
 * Reine Affiliate-/Such-URL-Logik (Port aus webapp/lib/prep.js). Nur statische Such-Links —
 * kein Tracker, keine Bezahl-API, kein Standortabfluss (spec TP-5 §D, Leitplanken).
 */
object Affiliate {
    const val TAG = "TDA-PLACEHOLDER-21"

    fun amazonSearchUrl(query: String, tag: String = TAG): String =
        "https://www.amazon.com.tr/s?k=" + enc(query) + "&tag=" + enc(tag)

    fun localSearchUrl(query: String): String =
        "https://www.google.com/search?q=" + enc(query)

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
}
