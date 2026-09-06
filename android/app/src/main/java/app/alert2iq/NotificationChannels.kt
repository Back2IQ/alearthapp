package app.alert2iq

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.os.Build

/**
 * Legt die Notification-Kanäle an (spec TP-3 §"Teil 1"). Der kritische Kanal durchbricht
 * „Nicht stören" nur, wenn der Nutzer opt-in ist UND der App der Policy-Zugriff gewährt wurde
 * — sonst best-effort (hoher Kanal + Alarm-Audio). TEST ist ein eigener, dezenter Kanal.
 */
object NotificationChannels {
    const val CRITICAL = "alarm_critical"
    const val TEST = "alarm_test"
    const val BEACON_READY = "beacon_ready"
    const val SERVICE = "svc_watch"

    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        val alarmAudio = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val desiredBypass = Prefs.dndBypassOptIn &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || nm.isNotificationPolicyAccessGranted)
        val existing = nm.getNotificationChannel(CRITICAL)
        if (existing != null && existing.canBypassDnd() != desiredBypass) nm.deleteNotificationChannel(CRITICAL)

        val critical = NotificationChannel(CRITICAL, context.getString(R.string.chan_critical), NotificationManager.IMPORTANCE_HIGH).apply {
            description = context.getString(R.string.chan_critical_desc)
            setSound(android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI, alarmAudio)
            enableVibration(true)
            setBypassDnd(desiredBypass)
        }
        val test = NotificationChannel(TEST, context.getString(R.string.chan_test), NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = context.getString(R.string.chan_test_desc)
        }
        val ready = NotificationChannel(BEACON_READY, context.getString(R.string.chan_ready), NotificationManager.IMPORTANCE_LOW).apply {
            description = context.getString(R.string.chan_ready_desc)
        }
        val service = NotificationChannel(SERVICE, context.getString(R.string.chan_service), NotificationManager.IMPORTANCE_LOW).apply {
            description = context.getString(R.string.chan_service_desc)
        }
        nm.createNotificationChannels(listOf(critical, test, ready, service))
    }
}
