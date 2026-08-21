package app.tda

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var scope: CoroutineScope

    private data class LanguageOption(val tag: String, val label: String)
    private val languages = listOf(
        LanguageOption("tr", "Türkçe"),
        LanguageOption("en", "English"),
        LanguageOption("ku", "Kurdî"),
        LanguageOption("ar", "العربية")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        Prefs.init(this)
        Prefs.applyNightMode()
        setTheme(Prefs.themeStyleRes(alert = false))
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

        findViewById<app.tda.SeismogramView>(R.id.seismogramView).applyColorblindSafe(Prefs.colorblindSafe)

        setupCitySpinner()
        setupLanguageSpinner()
        setupThemeControls()
        setupTestButtons()
        setupServerControls()
        observeEventBus()
    }

    private fun setupCitySpinner() {
        val spinner = findViewById<android.widget.Spinner>(R.id.citySpinner)
        val names = Eew.CITIES.map { it.displayName }
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
        val currentIndex = Eew.CITIES.indexOfFirst { it.id == Prefs.cityId }.coerceAtLeast(0)
        spinner.setSelection(currentIndex)
        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                Prefs.cityId = Eew.CITIES[position].id
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    private fun setupLanguageSpinner() {
        val spinner = findViewById<android.widget.Spinner>(R.id.languageSpinner)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, languages.map { it.label })
        val currentIndex = languages.indexOfFirst { it.tag == Prefs.languageTag }.coerceAtLeast(0)
        spinner.setSelection(currentIndex)
        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val tag = languages[position].tag
                if (tag != Prefs.languageTag) {
                    Prefs.languageTag = tag
                    Prefs.applyLocale()
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    private fun setupThemeControls() {
        val group = findViewById<android.widget.RadioGroup>(R.id.themeRadioGroup)
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
            if (newMode != Prefs.themeMode) {
                Prefs.themeMode = newMode
                Prefs.applyNightMode()
                recreate()
            }
        }

        val cbSwitch = findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.colorblindSwitch)
        cbSwitch.isChecked = Prefs.colorblindSafe
        cbSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked != Prefs.colorblindSafe) {
                Prefs.colorblindSafe = isChecked
                recreate()
            }
        }
    }

    private fun setupTestButtons() {
        findViewById<android.widget.Button>(R.id.btnTestQuake).setOnClickListener {
            TestScenarios.runEarthquakeDrill(scope, Prefs.selectedCity())
        }
        findViewById<android.widget.Button>(R.id.btnAftershockSequence).setOnClickListener {
            TestScenarios.runAftershockSequence(scope, Prefs.selectedCity())
        }
        findViewById<android.widget.Button>(R.id.btnDisturbanceFirework).setOnClickListener {
            TestScenarios.runFireworksDisturbance(scope)
        }
        findViewById<android.widget.Button>(R.id.btnReset).setOnClickListener {
            TestScenarios.reset()
        }
        findViewById<android.widget.Button>(R.id.btnViewReport).setOnClickListener {
            startActivity(Intent(this, ReportActivity::class.java))
        }
    }

    /** Wires the "Mit Server verbinden" section (spec "Server-Verbindungsmodus") to
     * [ServerLink]. The local [TestScenarios] injector above is completely untouched --
     * this is an additional, independent alarm source that also verifies Ed25519
     * signatures before anything reaches [EventBus]. */
    private fun setupServerControls() {
        val urlField = findViewById<android.widget.EditText>(R.id.editServerUrl)

        findViewById<android.widget.Button>(R.id.btnServerConnect).setOnClickListener {
            val url = urlField.text.toString().trim()
            if (url.isNotEmpty()) {
                ServerLink.connect(url)
            }
        }
        findViewById<android.widget.Button>(R.id.btnServerDisconnect).setOnClickListener {
            ServerLink.disconnect()
        }
        findViewById<android.widget.Button>(R.id.btnServerTestQuake).setOnClickListener {
            ServerLink.sendSimulate("quake")
        }
        findViewById<android.widget.Button>(R.id.btnServerTestFirework).setOnClickListener {
            ServerLink.sendSimulate("firework")
        }

        scope.launch {
            ServerLink.connState.collect { state -> renderServerStatusChip(state) }
        }
        scope.launch {
            ServerLink.invalidSignature.collect {
                Toast.makeText(this@MainActivity, R.string.ws_invalid_signature, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun renderServerStatusChip(state: ServerConnState) {
        val icon = findViewById<ImageView>(R.id.serverStatusIcon)
        val text = findViewById<TextView>(R.id.serverStatusText)
        val chip = findViewById<android.view.View>(R.id.serverStatusChip)
        val (drawableRes, stringRes, attrRes) = when (state) {
            ServerConnState.DISCONNECTED -> Triple(R.drawable.ic_status_block, R.string.server_status_disconnected, R.attr.colorDisturbance)
            ServerConnState.CONNECTING -> Triple(R.drawable.ic_status_attention, R.string.server_status_connecting, R.attr.colorAttention)
            ServerConnState.CONNECTED -> Triple(R.drawable.ic_status_ready, R.string.server_status_connected, R.attr.colorReady)
            ServerConnState.FAILED -> Triple(R.drawable.ic_status_block, R.string.server_status_failed, R.attr.colorDisturbance)
        }
        icon.setImageResource(drawableRes)
        text.setText(stringRes)
        chip.backgroundTintList = android.content.res.ColorStateList.valueOf(themeColor(attrRes))
    }

    private fun observeEventBus() {
        scope.launch {
            EventBus.status.collect { status -> renderStatusChip(status) }
        }
        scope.launch {
            EventBus.sequence.collect { list -> renderSequence(list) }
        }
        scope.launch {
            EventBus.pushNotice.collect { entry ->
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.sequence_item_format, getString(R.string.sequence_aftershock),
                        entry.magnitude, Eew.mmiRoman(entry.mmiAtUser), formatTime(entry.timestamp)),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        scope.launch {
            EventBus.disturbance.collect { reasonKey ->
                showDisturbanceDialog(reasonKey)
            }
        }
        scope.launch {
            EventBus.alarm.collect { payload ->
                if (payload.ver == 1 && EventBus.lastLaunchedAlarmId != payload.id) {
                    EventBus.lastLaunchedAlarmId = payload.id
                    launchAlert(payload)
                }
            }
        }
    }

    private fun launchAlert(payload: Eew.AlarmPayload) {
        val city = Prefs.selectedCity()
        val intent = Intent(this, AlertActivity::class.java).apply {
            putExtra(AlertActivity.EXTRA_ID, payload.id)
            putExtra(AlertActivity.EXTRA_EPI_LAT, payload.lat)
            putExtra(AlertActivity.EXTRA_EPI_LON, payload.lon)
            putExtra(AlertActivity.EXTRA_DEPTH_KM, payload.depthKm)
            putExtra(AlertActivity.EXTRA_ORIGIN_TS, payload.originTs)
            putExtra(AlertActivity.EXTRA_USER_LAT, city.lat)
            putExtra(AlertActivity.EXTRA_USER_LON, city.lon)
            putExtra(AlertActivity.EXTRA_USER_CITY_NAME, city.displayName)
        }
        startActivity(intent)
    }

    private fun renderStatusChip(status: StatusState) {
        val icon = findViewById<ImageView>(R.id.statusIcon)
        val text = findViewById<TextView>(R.id.statusText)
        val chip = findViewById<android.view.View>(R.id.statusChip)
        val (drawableRes, stringRes, attrRes) = when (status) {
            StatusState.READY -> Triple(R.drawable.ic_status_ready, R.string.status_ready, R.attr.colorReady)
            StatusState.ATTENTION -> Triple(R.drawable.ic_status_attention, R.string.status_attention, R.attr.colorAttention)
            StatusState.ALARM_P0 -> Triple(R.drawable.ic_tier_p0, R.string.status_alarm_p0, R.attr.colorTierP0)
            StatusState.CONFIRMED_P2 -> Triple(R.drawable.ic_tier_p2, R.string.status_confirmed_p2, R.attr.colorTierP2)
            StatusState.DISTURBANCE_DISCARDED -> Triple(R.drawable.ic_status_block, R.string.status_disturbance_discarded, R.attr.colorDisturbance)
        }
        icon.setImageResource(drawableRes)
        text.setText(stringRes)
        val color = themeColor(attrRes)
        chip.backgroundTintList = android.content.res.ColorStateList.valueOf(color)
    }

    private fun renderSequence(entries: List<Eew.SequenceEntry>) {
        val container = findViewById<android.widget.LinearLayout>(R.id.sequenceContainer)
        val emptyText = findViewById<TextView>(R.id.sequenceEmptyText)
        container.removeAllViews()
        if (entries.isEmpty()) {
            emptyText.visibility = android.view.View.VISIBLE
            return
        }
        emptyText.visibility = android.view.View.GONE
        val inflater = LayoutInflater.from(this)
        for (entry in entries) {
            val row = inflater.inflate(R.layout.view_sequence_item, container, false)
            val icon = row.findViewById<ImageView>(R.id.itemIcon)
            val text = row.findViewById<TextView>(R.id.itemText)
            icon.setImageResource(if (entry.isMainshock) R.drawable.ic_tier_p0 else R.drawable.ic_tier_p1)
            val label = getString(if (entry.isMainshock) R.string.sequence_mainshock else R.string.sequence_aftershock)
            text.text = getString(R.string.sequence_item_format, label, entry.magnitude, Eew.mmiRoman(entry.mmiAtUser), formatTime(entry.timestamp))
            container.addView(row)
        }
    }

    private fun showDisturbanceDialog(reasonKey: String) {
        val reason = when (reasonKey) {
            "firework" -> getString(R.string.disturbance_reason_firework)
            else -> getString(R.string.disturbance_reason_firework)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.disturbance_title)
            .setMessage(reason)
            .setPositiveButton(R.string.btn_dismiss, null)
            .show()
    }

    private fun formatTime(ts: Long): String =
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(java.util.Date(ts))

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
