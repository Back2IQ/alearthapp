package app.alearthapp

import android.content.Context
import com.google.firebase.messaging.FirebaseMessaging
import org.json.JSONObject

/**
 * Verdrahtet die beobachteten Orte der Web-Oberfläche mit FCM-Topics: pro Ort wird die
 * Publish-Zelle (0,5°) plus ihre 8 Nachbarn als Topic `cell_c<lat>_<lon>` abonniert. Der
 * Server ([tda_server.alert.publisher.Publisher]) sendet Beben-Payloads genau an diese
 * `cell_…`-Topics und weitet dabei den Radius um das Epizentrum — so weckt ein Beben in der
 * Nähe das Gerät auch bei geschlossener App. Kein Token, keine Server-Registrierung.
 *
 * [sync] ist idempotent: nur die Differenz zum zuletzt abonnierten Stand
 * ([Prefs.subscribedTopics]) wird an FCM geschickt.
 */
object PushTopics {

    /** Alle Topics, die die aktuelle Abo-Liste abdecken soll. */
    fun topicsFor(subsJson: String): Set<String> {
        val out = HashSet<String>()
        val arr = runCatching { JSONObject(subsJson).optJSONArray("subscriptions") }
            .getOrNull() ?: return out
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (!o.has("lat") || !o.has("lon")) continue
            out.addAll(GeoCell.topicsAround(o.optDouble("lat"), o.optDouble("lon")))
        }
        return out
    }

    /** Bringt die FCM-Topic-Abos in Deckung mit [subsJson]. Best-effort. */
    fun sync(ctx: Context, subsJson: String) {
        Prefs.init(ctx)
        val desired = topicsFor(subsJson)
        val current = Prefs.subscribedTopics
        val fm = FirebaseMessaging.getInstance()
        (desired - current).forEach { fm.subscribeToTopic(it) }
        (current - desired).forEach { fm.unsubscribeFromTopic(it) }
        Prefs.subscribedTopics = desired
    }
}
