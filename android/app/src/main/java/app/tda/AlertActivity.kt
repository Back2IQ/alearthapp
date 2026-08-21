package app.tda

import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Full-screen-capable alert screen (spec §"Alertscreen"). Started once by MainActivity
 * when a fresh P0 alarm (ver==1) comes in from [TestScenarios] via [EventBus]; from then
 * on it listens to [EventBus.alarm] itself to pick up the P0 -> P2 escalation live,
 * without MainActivity spawning a second activity.
 */
class AlertActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ID = "id"
        const val EXTRA_EPI_LAT = "epi_lat"
        const val EXTRA_EPI_LON = "epi_lon"
        const val EXTRA_DEPTH_KM = "depth_km"
        const val EXTRA_ORIGIN_TS = "origin_ts"
        const val EXTRA_USER_LAT = "user_lat"
        const val EXTRA_USER_LON = "user_lon"
        const val EXTRA_USER_CITY_NAME = "user_city_name"
    }

    private lateinit var scope: CoroutineScope
    private var currentTier: Eew.Tier = Eew.Tier.P0
    private var mag: Double = 0.0
    private var originTs: Long = 0L
    private var distKm: Double = 0.0
    private lateinit var eventId: String
    private lateinit var cityName: String

    override fun onCreate(savedInstanceState: Bundle?) {
        Prefs.init(this)
        Prefs.applyNightMode()
        setTheme(Prefs.themeStyleRes(alert = true))
        super.onCreate(savedInstanceState)

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )

        setContentView(R.layout.activity_alert)
        scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

        eventId = intent.getStringExtra(EXTRA_ID) ?: ""
        val epiLat = intent.getDoubleExtra(EXTRA_EPI_LAT, 0.0)
        val epiLon = intent.getDoubleExtra(EXTRA_EPI_LON, 0.0)
        originTs = intent.getLongExtra(EXTRA_ORIGIN_TS, System.currentTimeMillis())
        val userLat = intent.getDoubleExtra(EXTRA_USER_LAT, 0.0)
        val userLon = intent.getDoubleExtra(EXTRA_USER_LON, 0.0)
        cityName = intent.getStringExtra(EXTRA_USER_CITY_NAME) ?: ""
        distKm = Eew.haversineKm(userLat, userLon, epiLat, epiLon)
        mag = 6.8
        currentTier = Eew.Tier.P0

        findViewById<android.widget.Button>(R.id.btnCloseAlert).setOnClickListener { finish() }
        findViewById<android.widget.Button>(R.id.btnOpenReport).setOnClickListener {
            startActivity(android.content.Intent(this, ReportActivity::class.java))
        }

        renderTier(currentTier)
        triggerAlertFeedback(currentTier)
        startCountdownTicker()
        observeEscalation()
    }

    private fun observeEscalation() {
        scope.launch {
            EventBus.alarm.collect { payload ->
                if (payload.id == eventId && payload.tier == Eew.Tier.P2 && currentTier != Eew.Tier.P2) {
                    currentTier = Eew.Tier.P2
                    mag = payload.mag
                    renderTier(currentTier)
                    findViewById<android.view.View>(R.id.escalationText).visibility = android.view.View.VISIBLE
                    triggerAlertFeedback(currentTier)
                }
            }
        }
    }

    private fun renderTier(tier: Eew.Tier) {
        val tierIcon = findViewById<ImageView>(R.id.tierIcon)
        val titleText = findViewById<TextView>(R.id.alertTitleText)
        val tierLabel = findViewById<TextView>(R.id.tierLabelText)
        val magnitudeValue = findViewById<TextView>(R.id.magnitudeValueText)

        when (tier) {
            Eew.Tier.P0 -> {
                tierIcon.setImageResource(R.drawable.ic_tier_p0)
                titleText.setText(R.string.alert_title_p0)
                tierLabel.setText(R.string.tier_p0_label)
                magnitudeValue.text = getString(R.string.magnitude_proxy_note).let { note -> "%.1f %s".format(mag, note) }
            }
            Eew.Tier.P1 -> {
                tierIcon.setImageResource(R.drawable.ic_tier_p1)
                tierLabel.setText(R.string.tier_p1_label)
            }
            Eew.Tier.P2 -> {
                tierIcon.setImageResource(R.drawable.ic_tier_p2)
                titleText.setText(R.string.alert_title_p2)
                tierLabel.setText(R.string.tier_p2_label)
                magnitudeValue.text = getString(R.string.magnitude_pd_note).let { note -> "%.1f %s".format(mag, note) }
            }
        }

        val mmi = Eew.mmi(mag, distKm)
        val mmiLevel = Eew.mmiLevel(mmi)
        val descriptions = resources.getStringArray(R.array.mmi_descriptions)
        findViewById<TextView>(R.id.intensityValueText).text =
            "${Eew.mmiRoman(mmi)} · ${descriptions.getOrElse(mmiLevel - 1) { "" }}"

        findViewById<TextView>(R.id.distanceValueText).text = getString(R.string.distance_value_format, distKm)

        val issueElapsedSec = (System.currentTimeMillis() - originTs) / 1000.0
        val warningTime = Eew.sWaveEtaSeconds(distKm, issueElapsedSec)
        findViewById<TextView>(R.id.warningTimeValueText).text = getString(R.string.warning_time_value_format, warningTime)

        val protectionLevel = Eew.protectionLevel(mmi)
        val protectionIcon = findViewById<ImageView>(R.id.protectionIcon)
        val protectionText = findViewById<TextView>(R.id.protectionText)
        when (protectionLevel) {
            Eew.ProtectionLevel.STRONG -> {
                protectionIcon.setImageResource(R.drawable.ic_protection_strong)
                protectionText.setText(R.string.protection_strong)
            }
            Eew.ProtectionLevel.MODERATE -> {
                protectionIcon.setImageResource(R.drawable.ic_protection_moderate)
                protectionText.setText(R.string.protection_moderate)
            }
            Eew.ProtectionLevel.WEAK -> {
                protectionIcon.setImageResource(R.drawable.ic_protection_weak)
                protectionText.setText(R.string.protection_weak)
            }
        }
    }

    private fun startCountdownTicker() {
        scope.launch {
            while (isActive) {
                val elapsedSec = (System.currentTimeMillis() - originTs) / 1000.0
                val remaining = Eew.sWaveEtaSeconds(distKm, elapsedSec)
                val pWaveRemaining = Eew.pWaveEtaSeconds(distKm, elapsedSec)
                val countdownText = findViewById<TextView>(R.id.countdownText)
                countdownText.text = if (remaining <= 0.0) {
                    getString(R.string.countdown_arrived)
                } else {
                    "${remaining.toInt()} " + getString(R.string.countdown_label, cityName)
                }
                findViewById<TextView>(R.id.pWaveNoteText).text =
                    if (pWaveRemaining > 0.0) getString(R.string.countdown_p_wave_note, pWaveRemaining.toInt()) else ""
                delay(1000)
            }
        }
    }

    /**
     * Best-effort alarm sound + vibration. P2 (confirmed, life-saving) uses the alarm
     * audio stream, which on most devices sounds even in some silent/DND profiles; P0
     * is intentionally more muted (vibration only) since it is not yet confirmed.
     * TODO(Stufe 2): true DND-bypass needs the full permission gauntlet (spec §5),
     * not wired here.
     */
    private fun triggerAlertFeedback(tier: Eew.Tier) {
        val vibrator = getSystemService(VIBRATOR_SERVICE) as? Vibrator
        if (tier == Eew.Tier.P2) {
            try {
                val uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                val ringtone = RingtoneManager.getRingtone(this, uri)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ringtone?.audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                }
                ringtone?.play()
            } catch (_: Exception) {
                // Best-effort only; never let a missing ringtone crash the alert screen.
            }
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400, 200, 400), -1))
        } else {
            vibrator?.vibrate(VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
