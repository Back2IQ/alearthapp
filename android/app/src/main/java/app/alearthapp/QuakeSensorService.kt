package app.alearthapp

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlin.math.sqrt

/**
 * Läuft NUR während das Handy lädt (an-/abgeschaltet vom [PowerConnectionReceiver]).
 * Horcht am Beschleunigungssensor, erkennt über [StillnessDetector]/[ShakeDetector]
 * ein Rütteln bei ruhigem Gerät und meldet es anonym via [CrowdReport]. Die
 * eigentliche Erkennungslogik ist rein und andernorts getestet — hier nur der
 * Android-Rand. No-op, solange [Prefs.crowdsourcingEnabled] aus ist.
 */
class QuakeSensorService : Service(), SensorEventListener {

    companion object {
        private const val NOTIF_ID = 4300
        private const val PING_INTERVAL_MS = 30 * 60_000L
        private const val CLOCK_UNC_MS = 1000L

        fun start(ctx: Context) {
            val i = Intent(ctx, QuakeSensorService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i)
                else ctx.startService(i)
            } catch (e: Exception) { /* FGS-from-background restriction: retry on app open */ }
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, QuakeSensorService::class.java))
        }
    }

    private var sensorManager: SensorManager? = null
    private lateinit var cfg: SensorConfig
    private lateinit var stillness: StillnessDetector
    private lateinit var shake: ShakeDetector
    private var settled = false

    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        NotificationChannels.ensure(this)
        cfg = SensorConfig()
        stillness = StillnessDetector(cfg)
        shake = ShakeDetector(cfg)
        startForeground(NOTIF_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Prefs.crowdsourcingEnabled) { stopSelf(); return START_NOT_STICKY }
        val sm = getSystemService(SENSOR_SERVICE) as SensorManager
        sensorManager = sm
        sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        } ?: run { stopSelf() }   // no accelerometer -> feature inactive
        return START_STICKY
    }

    override fun onSensorChanged(event: SensorEvent) {
        val tMs = System.currentTimeMillis()
        val mag = sqrt(
            (event.values[0] * event.values[0] +
             event.values[1] * event.values[1] +
             event.values[2] * event.values[2]).toDouble()
        )
        settled = stillness.onSample(tMs, mag) == Stillness.SETTLED
        if (!settled) return
        maybePing(tMs)
        val triggerMs = shake.onSample(tMs, mag) ?: return
        val cell = currentCell() ?: return
        val hash = AnonDeviceId.current(this, tMs / 86_400_000L)
        CrowdReport.postTrigger(this, hash, cell, triggerMs, CLOCK_UNC_MS)
    }

    private fun maybePing(tMs: Long) {
        if (tMs - Prefs.lastPingMs < PING_INTERVAL_MS) return
        val cell = currentCell() ?: return
        val hash = AnonDeviceId.current(this, tMs / 86_400_000L)
        CrowdReport.postPing(this, hash, cell, tMs)
        Prefs.lastPingMs = tMs
    }

    /** Grobe Position (Last-Known) → 0,1°-Zelle; null ohne Permission/Fix. */
    private fun currentCell(): String? {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) return null
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        val loc = runCatching {
            lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
        }.getOrNull() ?: return null
        return GeoCell.coarsenCell(loc.latitude, loc.longitude)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, NotificationChannels.SERVICE)
            .setSmallIcon(R.drawable.ic_status_ready)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.crowd_svc_running))
            .setOngoing(true)
            .setContentIntent(open)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        sensorManager?.unregisterListener(this)
        super.onDestroy()
    }
}
