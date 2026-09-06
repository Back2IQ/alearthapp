package app.alert2iq

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Vollbild „Bist du sicher?" (MODE_ASK) bzw. aktives Notsignal (MODE_BEACON) — spec TP-3
 * §"Teil 2/3/4". Integriert BLE-Offline-Beaconing und Rescue-Radar zur Suche Verschütteter.
 */
class BeaconActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MODE = "mode"
        const val MODE_ASK = "ask"
        const val MODE_BEACON = "beacon"
    }

    private lateinit var scope: CoroutineScope
    private var strobe: Strobe? = null
    private var isRadarActive = false
    private lateinit var proximityAudio: RescueProximityAudio

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.withAppLocale())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Prefs.init(this)
        Prefs.applyNightMode()
        setTheme(Prefs.themeStyleRes(alert = true))
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )
        setContentView(R.layout.activity_beacon)
        scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
        proximityAudio = RescueProximityAudio(this)

        findViewById<Button>(R.id.btnSafe).setOnClickListener { onSafe() }
        findViewById<Button>(R.id.btnHelp).setOnClickListener { onHelp() }
        findViewById<Button>(R.id.btnStop).setOnClickListener { onSafe() }
        findViewById<Button>(R.id.btnShareSafe).setOnClickListener { shareSafe() }
        findViewById<Button>(R.id.btnShareSms).setOnClickListener { shareSms() }
        findViewById<Button>(R.id.btnPrepare).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        }

        findViewById<Button>(R.id.btnRescueRadar).setOnClickListener {
            toggleRescueRadar()
        }

        findViewById<Button>(R.id.btnAudioMute).setOnClickListener {
            proximityAudio.isMuted = !proximityAudio.isMuted
            val btn = findViewById<Button>(R.id.btnAudioMute)
            btn.setText(if (proximityAudio.isMuted) R.string.btn_audio_unmute else R.string.btn_audio_mute)
        }

        when (intent.getStringExtra(EXTRA_MODE)) {
            MODE_BEACON -> renderBeacon()
            else -> renderAsk()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        when (intent.getStringExtra(EXTRA_MODE)) {
            MODE_BEACON -> renderBeacon()
            else -> renderAsk()
        }
    }

    private fun renderAsk() {
        findViewById<TextView>(R.id.beaconTitle).setText(R.string.beacon_ask_title)
        findViewById<LinearLayout>(R.id.bleStatusCard).visibility = View.GONE
        findViewById<Button>(R.id.btnRescueRadar).visibility = View.GONE
        findViewById<View>(R.id.rescueRadarView).visibility = View.GONE
        findViewById<View>(R.id.radarSummaryContainer).visibility = View.GONE
        findViewById<TextView>(R.id.tvRadarResults).visibility = View.GONE
        findViewById<Button>(R.id.btnSafe).visibility = View.VISIBLE
        findViewById<Button>(R.id.btnHelp).visibility = View.VISIBLE
        findViewById<Button>(R.id.btnStop).visibility = View.GONE
        findViewById<Button>(R.id.btnShareSafe).visibility = View.GONE
        findViewById<Button>(R.id.btnShareSms).visibility = View.GONE
        findViewById<Button>(R.id.btnPrepare).visibility = View.GONE
        val total = Prefs.deadmanCountdownSec
        val cd = findViewById<TextView>(R.id.beaconCountdown)
        scope.launch {
            for (s in total downTo 1) {
                cd.text = s.toString()
                delay(1000)
                if (!isActive) return@launch
            }
            cd.text = "…"
        }
    }

    private fun renderBeacon() {
        findViewById<TextView>(R.id.beaconTitle).setText(R.string.beacon_active_title)
        findViewById<TextView>(R.id.beaconCountdown).text = ""
        findViewById<LinearLayout>(R.id.bleStatusCard).visibility = View.VISIBLE
        findViewById<Button>(R.id.btnRescueRadar).visibility = View.VISIBLE
        findViewById<Button>(R.id.btnSafe).visibility = View.GONE
        findViewById<Button>(R.id.btnHelp).visibility = View.GONE
        findViewById<Button>(R.id.btnStop).visibility = View.VISIBLE
        findViewById<Button>(R.id.btnShareSafe).visibility = View.GONE
        findViewById<Button>(R.id.btnShareSms).visibility = View.GONE
        findViewById<Button>(R.id.btnPrepare).visibility = View.GONE
        strobe = Strobe(this).also {
            if (Prefs.signalScreenStrobe) it.startScreen(StrobePattern.screen)
            if (Prefs.signalTorchStrobe) it.startTorch(StrobePattern.torch)
        }
    }

    private fun toggleRescueRadar() {
        val tvResults = findViewById<TextView>(R.id.tvRadarResults)
        val btnRadar = findViewById<Button>(R.id.btnRescueRadar)
        val radarView = findViewById<RescueRadarView>(R.id.rescueRadarView)
        val summaryContainer = findViewById<View>(R.id.radarSummaryContainer)
        val tvTriage = findViewById<TextView>(R.id.tvRadarTriageSummary)
        val tvNearest = findViewById<TextView>(R.id.tvRadarNearestTarget)

        if (isRadarActive) {
            BleRescueScanner.stopScan()
            proximityAudio.stop()
            isRadarActive = false
            btnRadar.setText(R.string.btn_rescue_radar)
            radarView.visibility = View.GONE
            summaryContainer.visibility = View.GONE
            tvResults.visibility = View.GONE
        } else {
            isRadarActive = true
            btnRadar.setText(R.string.btn_stop_radar)
            radarView.visibility = View.VISIBLE
            summaryContainer.visibility = View.VISIBLE
            tvResults.visibility = View.VISIBLE
            tvResults.setText(R.string.radar_searching)
            tvTriage.text = ""
            tvNearest.text = ""
            proximityAudio.start()

            BleRescueScanner.startScan(this) { list ->
                runOnUiThread {
                    radarView.setBeacons(list)
                    proximityAudio.updateBeacons(list)

                    if (list.isEmpty()) {
                        tvResults.setText(R.string.radar_no_devices)
                        tvTriage.text = ""
                        tvNearest.text = ""
                    } else {
                        val trappedCount = list.count { it.message.status == BleSosStatus.TRAPPED }
                        val injuredCount = list.count { it.message.status == BleSosStatus.INJURED }
                        val okCount = list.count { it.message.status == BleSosStatus.OK }

                        tvTriage.text = getString(R.string.radar_triage_count, trappedCount, injuredCount, okCount)

                        val nearestPriority = RescueProximityAudio.selectPriorityBeacon(list)
                        if (nearestPriority != null) {
                            val statusStr = when (nearestPriority.message.status) {
                                BleSosStatus.TRAPPED -> getString(R.string.status_trapped)
                                BleSosStatus.INJURED -> getString(R.string.status_injured)
                                BleSosStatus.OK -> getString(R.string.status_ok)
                            }
                            tvNearest.text = getString(R.string.radar_nearest_target, statusStr, nearestPriority.estimatedDistanceMeters)
                        } else {
                            tvNearest.text = ""
                        }

                        val sb = StringBuilder()
                        sb.append(getString(R.string.radar_found_header, list.size)).append("\n\n")
                        for (item in list) {
                            val statusText = when (item.message.status) {
                                BleSosStatus.TRAPPED -> getString(R.string.status_trapped)
                                BleSosStatus.INJURED -> getString(R.string.status_injured)
                                BleSosStatus.OK -> getString(R.string.status_ok)
                            }
                            val distStr = if (item.estimatedDistanceMeters > 0) {
                                String.format(Locale.US, " · ~%.1f m", item.estimatedDistanceMeters)
                            } else ""

                            val t = item.message.triage
                            val identityDetails = StringBuilder()
                            if (t.nameInitials.isNotBlank()) identityDetails.append(" [${t.nameInitials}]")
                            if (t.gender != BleGender.UNKNOWN) identityDetails.append(" ${t.gender.label}")
                            if (t.age > 0) identityDetails.append(" ${t.age}y")
                            if (t.bloodType != BleBloodType.UNKNOWN) identityDetails.append(" (${t.bloodType.label})")

                            val medFlags = mutableListOf<String>()
                            if (t.isUserResponsive) medFlags.add("👤 ACTIVE") else medFlags.add("⚠️ UNRESPONSIVE")
                            if (t.hasInsulinDiabetes) medFlags.add("💉 INSULIN")
                            if (t.hasHeartCondition) medFlags.add("🫀 HEART")
                            if (t.hasRespiratoryRisk) medFlags.add("🫁 ASTHMA")

                            sb.append(getString(
                                R.string.radar_beacon_entry,
                                statusText,
                                "$distStr$identityDetails",
                                item.message.batteryPercent
                            )).append("\n")

                            if (medFlags.isNotEmpty()) {
                                sb.append("   └─ ").append(medFlags.joinToString(" · ")).append("\n")
                            }
                        }
                        tvResults.text = sb.toString().trimEnd()
                    }
                }
            }
        }
    }

    private fun onSafe() {
        if (isRadarActive) {
            BleRescueScanner.stopScan()
            proximityAudio.stop()
            isRadarActive = false
        }
        strobe?.stopAll()
        AlarmService.userSafe(this)
        AlarmService.stop(this)
        BleEmergencyBeacon.stop(this)
        findViewById<TextView>(R.id.beaconTitle).setText(R.string.beacon_safe_title)
        findViewById<TextView>(R.id.beaconCountdown).text = ""
        findViewById<LinearLayout>(R.id.bleStatusCard).visibility = View.GONE
        findViewById<Button>(R.id.btnRescueRadar).visibility = View.GONE
        findViewById<View>(R.id.rescueRadarView).visibility = View.GONE
        findViewById<View>(R.id.radarSummaryContainer).visibility = View.GONE
        findViewById<TextView>(R.id.tvRadarResults).visibility = View.GONE
        findViewById<Button>(R.id.btnSafe).visibility = View.GONE
        findViewById<Button>(R.id.btnHelp).visibility = View.GONE
        findViewById<Button>(R.id.btnStop).visibility = View.GONE
        findViewById<Button>(R.id.btnShareSafe).visibility = View.VISIBLE
        findViewById<Button>(R.id.btnShareSms).visibility = View.VISIBLE
        findViewById<Button>(R.id.btnPrepare).visibility = View.VISIBLE
    }

    private fun onHelp() {
        AlarmService.userHelp(this)
        renderBeacon()
    }

    private fun shareSafe() {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, getString(R.string.beacon_share_text))
        }
        startActivity(Intent.createChooser(send, getString(R.string.beacon_share_safe)))
    }

    private fun shareSms() {
        val phone = Prefs.emergencySmsPhone
        val uri = if (phone.isNotEmpty()) Uri.parse("smsto:$phone") else Uri.parse("smsto:")
        val send = Intent(Intent.ACTION_SENDTO, uri).apply {
            putExtra("sms_body", getString(R.string.beacon_share_text))
        }
        runCatching { startActivity(send) }
    }

    override fun onDestroy() {
        if (isRadarActive) {
            BleRescueScanner.stopScan()
            proximityAudio.stop()
        }
        proximityAudio.release()
        strobe?.stopAll()
        scope.cancel()
        super.onDestroy()
    }
}