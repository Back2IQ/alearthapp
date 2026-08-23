package app.tda

import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment

/**
 * Sichtbarer Schirm für die bisher UI-losen TP-3-Notsignal-/Beacon-Parameter aus [Prefs]:
 * Notsignal-Kanäle (Pfeife/Bildschirm-/Taschenlampen-Blitz), Beacon-Auslösung
 * (Schwelle/Schonfrist/Totmann-Countdown), DND-Durchbruch-Opt-in sowie ein Test-Button,
 * der die echte Signalkette über [AlarmService.arm] im TEST-Modus anstößt.
 */
class SafetySettingsFragment : Fragment(R.layout.fragment_safety) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupSignalSwitches(view)
        setupBeaconControls(view)
        setupDndControls(view)
        setupTestButton(view)
    }

    override fun onResume() {
        super.onResume()
        view?.let { updateDndStatus(it) }
    }

    private fun setupSignalSwitches(view: View) {
        val whistle = view.findViewById<SwitchCompat>(R.id.switchSignalWhistle)
        whistle.isChecked = Prefs.signalWhistle
        whistle.setOnCheckedChangeListener { _, checked -> Prefs.signalWhistle = checked }

        val screenStrobe = view.findViewById<SwitchCompat>(R.id.switchSignalScreenStrobe)
        screenStrobe.isChecked = Prefs.signalScreenStrobe
        screenStrobe.setOnCheckedChangeListener { _, checked -> Prefs.signalScreenStrobe = checked }

        val torchStrobe = view.findViewById<SwitchCompat>(R.id.switchSignalTorchStrobe)
        torchStrobe.isChecked = Prefs.signalTorchStrobe
        torchStrobe.setOnCheckedChangeListener { _, checked -> Prefs.signalTorchStrobe = checked }
    }

    private fun setupBeaconControls(view: View) {
        val beaconEnabled = view.findViewById<SwitchCompat>(R.id.switchBeaconEnabled)
        beaconEnabled.isChecked = Prefs.beaconEnabled
        beaconEnabled.setOnCheckedChangeListener { _, checked -> Prefs.beaconEnabled = checked }

        val mmiValue = view.findViewById<android.widget.TextView>(R.id.textBeaconMmiValue)
        val mmiSeek = view.findViewById<SeekBar>(R.id.seekBeaconMmi)
        mmiValue.text = getString(R.string.safety_beacon_mmi_value, Prefs.beaconMmiThreshold)
        mmiSeek.progress = Prefs.beaconMmiThreshold
        mmiSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val coerced = progress.coerceIn(5, 9)
                mmiValue.text = getString(R.string.safety_beacon_mmi_value, coerced)
                if (fromUser) Prefs.beaconMmiThreshold = coerced
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        val graceValue = view.findViewById<android.widget.TextView>(R.id.textGraceMinutesValue)
        val graceSeek = view.findViewById<SeekBar>(R.id.seekGraceMinutes)
        graceValue.text = getString(R.string.safety_grace_value, Prefs.graceMinutes)
        graceSeek.progress = Prefs.graceMinutes
        graceSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val coerced = progress.coerceIn(0, 30)
                graceValue.text = getString(R.string.safety_grace_value, coerced)
                if (fromUser) Prefs.graceMinutes = coerced
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        val deadmanValue = view.findViewById<android.widget.TextView>(R.id.textDeadmanCountdownValue)
        val deadmanSeek = view.findViewById<SeekBar>(R.id.seekDeadmanCountdown)
        deadmanValue.text = getString(R.string.safety_deadman_value, Prefs.deadmanCountdownSec)
        deadmanSeek.progress = Prefs.deadmanCountdownSec
        deadmanSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val coerced = progress.coerceIn(15, 300)
                deadmanValue.text = getString(R.string.safety_deadman_value, coerced)
                if (fromUser) Prefs.deadmanCountdownSec = coerced
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
    }

    private fun setupDndControls(view: View) {
        val dndSwitch = view.findViewById<SwitchCompat>(R.id.switchDndBypass)
        dndSwitch.isChecked = Prefs.dndBypassOptIn
        dndSwitch.setOnCheckedChangeListener { _, checked -> Prefs.dndBypassOptIn = checked }

        view.findViewById<View>(R.id.btnOpenDndSettings).setOnClickListener {
            runCatching { startActivity(DndAccess.requestIntent()) }
        }
        updateDndStatus(view)
    }

    private fun updateDndStatus(view: View) {
        val status = view.findViewById<android.widget.TextView>(R.id.textDndStatus)
        status.setText(
            if (DndAccess.isGranted(requireContext())) R.string.safety_dnd_status_granted
            else R.string.safety_dnd_status_not_granted
        )
    }

    private fun setupTestButton(view: View) {
        view.findViewById<View>(R.id.btnTestSignalChain).setOnClickListener {
            AlarmService.arm(requireContext(), mmi = 9.0, tier = Eew.Tier.P2, isTest = true)
            Toast.makeText(requireContext(), R.string.safety_test_toast, Toast.LENGTH_LONG).show()
        }
    }
}
