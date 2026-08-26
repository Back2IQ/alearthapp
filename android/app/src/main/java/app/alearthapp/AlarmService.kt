package app.alearthapp

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Vordergrund-Dienst (spec TP-3 §"Teil 2"). Trägt den DND-fähigen Alarmton, fährt die
 * Schonfrist-/Totmann-Zeitgeber über [SafetyState] und startet die [BeaconActivity] per
 * Full-Screen-Intent. Die reine Politik steckt in [SafetyState]/[CriticalAlarmPolicy]
 * (dort getestet); hier nur der Android-Rand.
 */
class AlarmService : Service() {

    companion object {
        const val ACTION_ARM = "app.alearthapp.ARM"
        const val ACTION_USER_SAFE = "app.alearthapp.USER_SAFE"
        const val ACTION_USER_HELP = "app.alearthapp.USER_HELP"
        const val ACTION_STOP = "app.alearthapp.STOP"
        const val EXTRA_MMI = "mmi"
        const val EXTRA_TIER = "tier"
        const val EXTRA_TEST = "test"
        private const val NOTIF_ID = 4201
        private const val BEACON_ASK_NOTIF_ID = NOTIF_ID + 1
        private const val ALARM_FSI_NOTIF_ID = 4200

        fun arm(ctx: Context, mmi: Double, tier: Eew.Tier, isTest: Boolean) =
            start(ctx, Intent(ctx, AlarmService::class.java).setAction(ACTION_ARM)
                .putExtra(EXTRA_MMI, mmi).putExtra(EXTRA_TIER, tier.name).putExtra(EXTRA_TEST, isTest))

        fun userSafe(ctx: Context) = start(ctx, Intent(ctx, AlarmService::class.java).setAction(ACTION_USER_SAFE))
        fun userHelp(ctx: Context) = start(ctx, Intent(ctx, AlarmService::class.java).setAction(ACTION_USER_HELP))
        fun stop(ctx: Context) = start(ctx, Intent(ctx, AlarmService::class.java).setAction(ACTION_STOP))

        private fun start(ctx: Context, intent: Intent) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(intent)
            else ctx.startService(intent)
        }
    }

    private lateinit var scope: CoroutineScope
    private lateinit var safety: SafetyState
    private var config: SafetyConfig = SafetyConfig(true, 7, 300_000, 60_000)
    private var timerJob: Job? = null
    private var whistleTrack: AudioTrack? = null
    private var isTestChain = false

    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        NotificationChannels.ensure(this)
        config = Prefs.safetyConfig()
        safety = SafetyState(config)
        scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
        startForeground(NOTIF_ID, buildServiceNotification(getString(R.string.svc_watching)))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ARM -> onArm(
                intent.getDoubleExtra(EXTRA_MMI, 0.0),
                runCatching { Eew.Tier.valueOf(intent.getStringExtra(EXTRA_TIER) ?: "P2") }.getOrDefault(Eew.Tier.P2),
                intent.getBooleanExtra(EXTRA_TEST, false)
            )
            ACTION_USER_SAFE -> { safety.onUserSafe(); enterWatch() }
            ACTION_USER_HELP -> { safety.onUserHelp(); enterBeacon() }
            ACTION_STOP -> stopEverything()
        }
        return START_STICKY
    }

    private fun onArm(mmi: Double, tier: Eew.Tier, isTest: Boolean) {
        isTestChain = isTest
        config = Prefs.safetyConfig()
        safety = SafetyState(config)
        if (safety.onConfirmedQuake(mmi) != SafetyPhase.ARMED) { // unterhalb Schwelle / deaktiviert
            if (!isTest) return else { /* TEST: Kette trotzdem zeigen */ }
        }
        // Schonfrist läuft leise; danach Rückfrage; unbeantwortet → Beacon (Totmann).
        timerJob?.cancel()
        timerJob = scope.launch {
            postGraceNotification()
            delay(if (isTest) 3_000 else config.graceMillis)
            if (!isActive) return@launch
            safety.onGraceElapsed()
            launchBeaconAsk()
            delay(config.deadmanMillis)
            if (!isActive) return@launch
            if (safety.phase == SafetyPhase.ASKING) { safety.onCountdownElapsed(); enterBeacon() }
        }
    }

    /** Bereitschafts-Notification während der Schonfrist (spec-Schritt 2): zwei Aktionen,
     * damit der Nutzer selbst entscheiden kann, statt nur den Totmann-Zeitgeber ablaufen zu lassen. */
    private fun postGraceNotification() {
        val open = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val safeAction = PendingIntent.getService(this, 2,
            Intent(this, AlarmService::class.java).setAction(ACTION_USER_SAFE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val helpAction = PendingIntent.getService(this, 3,
            Intent(this, AlarmService::class.java).setAction(ACTION_USER_HELP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(this, NotificationChannels.BEACON_READY)
            .setSmallIcon(R.drawable.ic_status_attention)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.svc_grace))
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, getString(R.string.beacon_im_safe), safeAction)
            .addAction(0, getString(R.string.beacon_need_help), helpAction)
            .build()
        androidx.core.app.NotificationManagerCompat.from(this).notify(NOTIF_ID, n)
    }

    private fun launchBeaconAsk() {
        val fsi = PendingIntent.getActivity(
            this, 0,
            Intent(this, BeaconActivity::class.java)
                .putExtra(BeaconActivity.EXTRA_MODE, BeaconActivity.MODE_ASK)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val channel = if (isTestChain) NotificationChannels.TEST else NotificationChannels.CRITICAL
        val n = NotificationCompat.Builder(this, channel)
            .setSmallIcon(R.drawable.ic_status_attention)
            .setContentTitle(getString(R.string.beacon_ask_title))
            .setContentText(getString(R.string.beacon_ask_body))
            .apply {
                if (!isTestChain) {
                    setPriority(NotificationCompat.PRIORITY_MAX)
                    setCategory(NotificationCompat.CATEGORY_ALARM)
                }
            }
            .setFullScreenIntent(fsi, true)
            .setAutoCancel(true)
            .build()
        androidx.core.app.NotificationManagerCompat.from(this).notify(BEACON_ASK_NOTIF_ID, n)
    }

    private fun enterWatch() {
        timerJob?.cancel()
        stopWhistle()
        BleEmergencyBeacon.stop(this)
        updateNotification(getString(R.string.svc_watching))
        androidx.core.app.NotificationManagerCompat.from(this).cancel(BEACON_ASK_NOTIF_ID)
        androidx.core.app.NotificationManagerCompat.from(this).cancel(ALARM_FSI_NOTIF_ID)
    }

    private fun enterBeacon() {
        updateNotification(getString(R.string.svc_beacon))
        if (Prefs.signalWhistle && !isTestChain) startWhistle()
        BleEmergencyBeacon.start(this, BleSosStatus.TRAPPED)
        val fsi = PendingIntent.getActivity(
            this, 0,
            Intent(this, BeaconActivity::class.java)
                .putExtra(BeaconActivity.EXTRA_MODE, BeaconActivity.MODE_BEACON)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val channel = if (isTestChain) NotificationChannels.TEST else NotificationChannels.CRITICAL
        val n = NotificationCompat.Builder(this, channel)
            .setSmallIcon(R.drawable.ic_status_attention)
            .setContentTitle(getString(R.string.beacon_active_title))
            .apply {
                if (!isTestChain) {
                    setPriority(NotificationCompat.PRIORITY_MAX)
                    setCategory(NotificationCompat.CATEGORY_ALARM)
                }
            }
            .setFullScreenIntent(fsi, true)
            .setAutoCancel(true)
            .build()
        androidx.core.app.NotificationManagerCompat.from(this).notify(BEACON_ASK_NOTIF_ID, n)
    }

    /** Looped Sinus-Sweep über AudioTrack, USAGE_ALARM. Puffer aus [SignalGenerator] (getestet). */
    private fun startWhistle() {
        stopWhistle()
        val pcm = SignalGenerator.sweepPcm(900.0, 2200.0, 1200)
        val bytes = ByteArray(pcm.size * 2)
        for (i in pcm.indices) { bytes[i * 2] = (pcm[i].toInt() and 0xff).toByte(); bytes[i * 2 + 1] = (pcm[i].toInt() shr 8).toByte() }
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(SignalGenerator.SAMPLE_RATE)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(bytes.size)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(bytes, 0, bytes.size)
        track.setLoopPoints(0, pcm.size, -1)
        track.play()
        whistleTrack = track
    }

    private fun stopWhistle() {
        whistleTrack?.let { runCatching { it.stop() }; runCatching { it.release() } }
        whistleTrack = null
    }

    private fun stopEverything() {
        timerJob?.cancel(); stopWhistle(); BleEmergencyBeacon.stop(this); safety.reset()
        androidx.core.app.NotificationManagerCompat.from(this).cancel(BEACON_ASK_NOTIF_ID)
        androidx.core.app.NotificationManagerCompat.from(this).cancel(ALARM_FSI_NOTIF_ID)
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
    }

    private fun buildServiceNotification(text: String): Notification {
        val open = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, NotificationChannels.SERVICE)
            .setSmallIcon(R.drawable.ic_status_ready)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(open)
            .build()
    }

    private fun updateNotification(text: String) {
        androidx.core.app.NotificationManagerCompat.from(this).notify(NOTIF_ID, buildServiceNotification(text))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopWhistle()
        BleEmergencyBeacon.stop(this) // Beacon-Advertising stoppen wenn Service vom OS beendet wird
        scope.cancel()
        super.onDestroy()
    }
}
