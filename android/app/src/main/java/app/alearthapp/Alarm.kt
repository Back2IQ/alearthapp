package app.alearthapp

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Gemeinsame Alarm-Auslösung für Vordergrund (Web-Brücke [WebBridge]) UND Hintergrund
 * (FCM-Push [PushService]). Baut den [AlertActivity]-Intent, postet die DND-durchdringende
 * Full-Screen-Wecknotification und armiert bei starkem Beben die Nachbeben-Wache.
 *
 * Aus dem Hintergrund (App zu / Standby) ist die Full-Screen-Intent-Notification der einzige
 * zuverlässige Weg, den Vollbild-Alarm zu zeigen — ein direktes startActivity blockiert Android
 * dort. Im Vordergrund starten wir zusätzlich direkt für sofortige Anzeige.
 */
object Alarm {
    data class Payload(
        val id: String, val lat: Double, val lon: Double, val depthKm: Double,
        val originTs: Long, val userLat: Double, val userLon: Double,
        val cityName: String, val sound: Boolean
    )

    fun alertIntent(ctx: Context, p: Payload): Intent =
        Intent(ctx, AlertActivity::class.java).apply {
            putExtra(AlertActivity.EXTRA_ID, p.id)
            putExtra(AlertActivity.EXTRA_EPI_LAT, p.lat)
            putExtra(AlertActivity.EXTRA_EPI_LON, p.lon)
            putExtra(AlertActivity.EXTRA_DEPTH_KM, p.depthKm)
            putExtra(AlertActivity.EXTRA_ORIGIN_TS, p.originTs)
            putExtra(AlertActivity.EXTRA_USER_LAT, p.userLat)
            putExtra(AlertActivity.EXTRA_USER_LON, p.userLon)
            putExtra(AlertActivity.EXTRA_USER_CITY_NAME, p.cityName)
            putExtra(AlertActivity.EXTRA_SOUND, p.sound)
        }

    /** DND-durchdringende Full-Screen-Wecknotification — funktioniert auch aus dem Hintergrund. */
    fun postFullScreen(ctx: Context, p: Payload) {
        NotificationChannels.ensure(ctx)
        val fsi = PendingIntent.getActivity(
            ctx, 2, alertIntent(ctx, p).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(ctx, NotificationChannels.CRITICAL)
            .setSmallIcon(R.drawable.ic_tier_p0)
            .setContentTitle(ctx.withAppLocale().getString(R.string.alert_title_p0))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fsi, true)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(4200, n) }
    }

    /** Direktes Starten der Vollbild-Activity (nur zulässig aus dem Vordergrund). */
    fun launchDirect(ctx: Context, p: Payload) {
        runCatching { ctx.startActivity(alertIntent(ctx, p).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /** Leise Benachrichtigung (Stufe „notify") — kein Vollbild, normale Wichtigkeit. */
    fun postQuiet(ctx: Context, title: String, body: String) {
        NotificationChannels.ensure(ctx)
        val open = PendingIntent.getActivity(
            ctx, 3, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(ctx, NotificationChannels.TEST)
            .setSmallIcon(R.drawable.ic_tier_p0)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(4201, n) }
    }

    /** Dedup-Fenster: derselbe Event bleibt so lange „schon behandelt". */
    private const val DEDUP_WINDOW_MS = 90_000L

    /**
     * Zentraler, DEDUPLIZIERTER Auslöser des lauten Alarms — von BEIDEN Pfaden genutzt
     * (Vordergrund [WebBridge.onAlert] und Hintergrund [PushService]). Verhindert doppelte
     * Vollbild-Alarme/Armierungen, wenn Web-Erkennung UND FCM-Push für dieselbe Event-ID
     * feuern, während die App vorne ist. Dedup liegt in [Prefs] (geräteweit geteilt).
     */
    fun dispatch(ctx: Context, p: Payload, tier: Eew.Tier, mmi: Double, isTest: Boolean, foreground: Boolean) {
        Prefs.init(ctx)
        val now = System.currentTimeMillis()
        val alreadyHandled = Prefs.lastAlarmId == p.id && (now - Prefs.lastAlarmTs) < DEDUP_WINDOW_MS
        if (alreadyHandled) return
        Prefs.lastAlarmId = p.id
        Prefs.lastAlarmTs = now
        postFullScreen(ctx, p)
        if (foreground) launchDirect(ctx, p)
        maybeArm(ctx, mmi, tier, isTest)
    }

    /** Setzt das Dedup zurück (z. B. nach Entwarnung), damit dieselbe ID erneut alarmieren darf. */
    fun clearDedup(ctx: Context) {
        Prefs.init(ctx)
        Prefs.lastAlarmId = ""
        Prefs.lastAlarmTs = 0L
    }

    /** Nachbeben-Wache/Beacon nur bei starkem, bestätigtem Beben und wenn aktiviert. */
    fun maybeArm(ctx: Context, mmi: Double, tier: Eew.Tier, isTest: Boolean) {
        if (!isTest && Prefs.beaconEnabled && mmi >= Prefs.beaconMmiThreshold) {
            runCatching { AlarmService.arm(ctx, mmi = mmi, tier = tier, isTest = isTest) }
        }
    }
}
