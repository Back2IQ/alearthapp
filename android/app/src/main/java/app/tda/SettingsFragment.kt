package app.tda

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/** Einstellungen-Tab: Stadt/Sprache/Theme/Farbenblind + ausklappbarer Entwickler-Bereich
 * (Test-Szenarien + Server-Verbindung, vorher lose in MainActivity). */
class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private data class LanguageOption(val tag: String, val label: String)
    private val languages = listOf(
        LanguageOption("tr", "Türkçe"), LanguageOption("en", "English"),
        LanguageOption("ku", "Kurdî"), LanguageOption("ar", "العربية"),
        LanguageOption("de", "Deutsch")
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupCitySpinner(view)
        setupLanguageSpinner(view)
        setupThemeControls(view)
        setupDevToggle(view)
        setupTestButtons(view)
        setupServerControls(view)
    }

    private fun setupCitySpinner(view: View) {
        val spinner = view.findViewById<android.widget.Spinner>(R.id.citySpinner)
        spinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, Eew.CITIES.map { it.displayName })
        spinner.setSelection(Eew.CITIES.indexOfFirst { it.id == Prefs.cityId }.coerceAtLeast(0))
        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) { Prefs.cityId = Eew.CITIES[pos].id }
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
        }
    }

    private fun setupLanguageSpinner(view: View) {
        val spinner = view.findViewById<android.widget.Spinner>(R.id.languageSpinner)
        spinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, languages.map { it.label })
        spinner.setSelection(languages.indexOfFirst { it.tag == Prefs.languageTag }.coerceAtLeast(0))
        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) {
                val tag = languages[pos].tag
                if (tag != Prefs.languageTag) { Prefs.languageTag = tag; Prefs.applyLocale() }
            }
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
        }
    }

    private fun setupThemeControls(view: View) {
        val group = view.findViewById<android.widget.RadioGroup>(R.id.themeRadioGroup)
        when (Prefs.themeMode) {
            Prefs.ThemeMode.SYSTEM -> group.check(R.id.radioThemeSystem)
            Prefs.ThemeMode.LIGHT -> group.check(R.id.radioThemeLight)
            Prefs.ThemeMode.DARK -> group.check(R.id.radioThemeDark)
        }
        group.setOnCheckedChangeListener { _, checkedId ->
            val newMode = when (checkedId) {
                R.id.radioThemeLight -> Prefs.ThemeMode.LIGHT
                R.id.radioThemeDark -> Prefs.ThemeMode.DARK
                else -> Prefs.ThemeMode.SYSTEM
            }
            if (newMode != Prefs.themeMode) { Prefs.themeMode = newMode; Prefs.applyNightMode(); requireActivity().recreate() }
        }
        val cbSwitch = view.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.colorblindSwitch)
        cbSwitch.isChecked = Prefs.colorblindSafe
        cbSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked != Prefs.colorblindSafe) { Prefs.colorblindSafe = isChecked; requireActivity().recreate() }
        }
    }

    private fun setupDevToggle(view: View) {
        val section = view.findViewById<View>(R.id.devSection)
        view.findViewById<View>(R.id.btnToggleDev).setOnClickListener {
            section.visibility = if (section.visibility == View.GONE) View.VISIBLE else View.GONE
        }
    }

    private fun setupTestButtons(view: View) {
        view.findViewById<View>(R.id.btnTestQuake).setOnClickListener { TestScenarios.runEarthquakeDrill(Prefs.selectedCity()) }
        view.findViewById<View>(R.id.btnAftershockSequence).setOnClickListener { TestScenarios.runAftershockSequence(Prefs.selectedCity()) }
        view.findViewById<View>(R.id.btnDisturbanceFirework).setOnClickListener { TestScenarios.runFireworksDisturbance() }
        view.findViewById<View>(R.id.btnReset).setOnClickListener { TestScenarios.reset() }
        view.findViewById<View>(R.id.btnTestBeaconChain).setOnClickListener {
            AlarmService.arm(requireContext(), mmi = 9.0, tier = Eew.Tier.P2, isTest = true)
            Toast.makeText(requireContext(), R.string.test_beacon_started, Toast.LENGTH_LONG).show()
        }
        view.findViewById<View>(R.id.btnViewReport).setOnClickListener { startActivity(android.content.Intent(requireContext(), ReportActivity::class.java)) }
    }

    private fun setupServerControls(view: View) {
        val urlField = view.findViewById<android.widget.EditText>(R.id.editServerUrl)
        view.findViewById<View>(R.id.btnServerConnect).setOnClickListener {
            val url = urlField.text.toString().trim(); if (url.isNotEmpty()) ServerLink.connect(url)
        }
        view.findViewById<View>(R.id.btnServerDisconnect).setOnClickListener { ServerLink.disconnect() }
        view.findViewById<View>(R.id.btnServerTestQuake).setOnClickListener { ServerLink.sendSimulate("quake") }
        view.findViewById<View>(R.id.btnServerTestFirework).setOnClickListener { ServerLink.sendSimulate("firework") }
        viewLifecycleOwner.lifecycleScope.launch {
            ServerLink.connState.collect { renderServerStatusChip(view, it) }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            ServerLink.invalidSignature.collect { Toast.makeText(requireContext(), R.string.ws_invalid_signature, Toast.LENGTH_LONG).show() }
        }
    }

    private fun renderServerStatusChip(view: View, state: ServerConnState) {
        val icon = view.findViewById<ImageView>(R.id.serverStatusIcon)
        val text = view.findViewById<TextView>(R.id.serverStatusText)
        val chip = view.findViewById<View>(R.id.serverStatusChip)
        val (drawableRes, stringRes, attrRes) = when (state) {
            ServerConnState.DISCONNECTED -> Triple(R.drawable.ic_status_block, R.string.server_status_disconnected, R.attr.colorDisturbance)
            ServerConnState.CONNECTING -> Triple(R.drawable.ic_status_attention, R.string.server_status_connecting, R.attr.colorAttention)
            ServerConnState.CONNECTED -> Triple(R.drawable.ic_status_ready, R.string.server_status_connected, R.attr.colorReady)
            ServerConnState.FAILED -> Triple(R.drawable.ic_status_block, R.string.server_status_failed, R.attr.colorDisturbance)
        }
        icon.setImageResource(drawableRes); text.setText(stringRes)
        chip.backgroundTintList = android.content.res.ColorStateList.valueOf(requireContext().themeColor(attrRes))
    }
}
