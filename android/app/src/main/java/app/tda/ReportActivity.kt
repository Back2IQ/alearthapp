package app.tda

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Ereignisbericht (spec §"Ereignisbericht"): timeline of the last local test event --
 * rupture onset -> detection (P0) -> confirmation (P2) -> S-wave at the user's
 * location -- exportable via the system share sheet.
 */
class ReportActivity : AppCompatActivity() {

    private lateinit var scope: CoroutineScope
    private var latestReport: ReportData? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        Prefs.init(this)
        Prefs.applyNightMode()
        setTheme(Prefs.themeStyleRes(alert = false))
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report)
        scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

        findViewById<android.widget.Button>(R.id.btnShareReport).setOnClickListener { shareReport() }

        scope.launch {
            EventBus.report.collect { report ->
                latestReport = report
                render(report)
            }
        }
    }

    private fun render(report: ReportData?) {
        val emptyText = findViewById<TextView>(R.id.reportEmptyText)
        val headerText = findViewById<TextView>(R.id.reportHeaderText)
        val timelineContainer = findViewById<android.widget.LinearLayout>(R.id.timelineContainer)
        val shareButton = findViewById<android.widget.Button>(R.id.btnShareReport)
        timelineContainer.removeAllViews()

        if (report == null) {
            emptyText.visibility = android.view.View.VISIBLE
            headerText.visibility = android.view.View.GONE
            shareButton.visibility = android.view.View.GONE
            return
        }
        emptyText.visibility = android.view.View.GONE
        headerText.visibility = android.view.View.VISIBLE
        shareButton.visibility = android.view.View.VISIBLE

        headerText.text = getString(
            R.string.report_header_format, report.eventId, report.src, report.mag, report.depthKm
        )

        val inflater = layoutInflater
        fun addRow(labelRes: Int, seconds: Double?) {
            val row = inflater.inflate(R.layout.view_timeline_item, timelineContainer, false)
            row.findViewById<TextView>(R.id.timelineLabel).text = getString(labelRes)
            row.findViewById<TextView>(R.id.timelineValue).text =
                if (seconds == null) "--" else "+%.1f s".format(seconds)
            timelineContainer.addView(row)
        }

        addRow(R.string.report_timeline_origin, 0.0)
        addRow(R.string.report_timeline_detection, (report.p0IssuedTs - report.originTs) / 1000.0)
        addRow(
            R.string.report_timeline_confirmed,
            report.p2IssuedTs?.let { (it - report.originTs) / 1000.0 }
        )
        addRow(R.string.report_timeline_swave, Eew.sWaveEtaSeconds(report.userDistKm, 0.0))
    }

    private fun shareReport() {
        val report = latestReport ?: return
        val sb = StringBuilder()
        sb.append(getString(R.string.report_header_format, report.eventId, report.src, report.mag, report.depthKm))
        sb.append('\n')
        sb.append(getString(R.string.report_timeline_origin)).append(": +0.0 s\n")
        sb.append(getString(R.string.report_timeline_detection)).append(": +%.1f s\n".format((report.p0IssuedTs - report.originTs) / 1000.0))
        report.p2IssuedTs?.let {
            sb.append(getString(R.string.report_timeline_confirmed)).append(": +%.1f s\n".format((it - report.originTs) / 1000.0))
        }
        sb.append(getString(R.string.report_timeline_swave)).append(": +%.1f s\n".format(Eew.sWaveEtaSeconds(report.userDistKm, 0.0)))

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.report_title))
            putExtra(Intent.EXTRA_TEXT, sb.toString())
        }
        startActivity(Intent.createChooser(intent, getString(R.string.report_share)))
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
