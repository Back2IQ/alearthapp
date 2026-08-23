package app.tda

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Netzwerk-Tab: Live-Seismogramm (Telefon-Beschleunigungssensor) + Nachbeben-Sequenz.
 * Früher der „Start"-Tab — jetzt korrekt vom Landing getrennt (siehe [StartFragment]).
 * Reine Anzeige; der Alarm-Launch lebt tab-unabhängig in [MainActivity].
 */
class NetworkFragment : Fragment(R.layout.fragment_network) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<SeismogramView>(R.id.seismogramView).applyColorblindSafe(Prefs.colorblindSafe)
        viewLifecycleOwner.lifecycleScope.launch {
            EventBus.sequence.collect { renderSequence(view, it) }
        }
    }

    private fun renderSequence(view: View, entries: List<Eew.SequenceEntry>) {
        val container = view.findViewById<android.widget.LinearLayout>(R.id.sequenceContainer)
        val emptyText = view.findViewById<TextView>(R.id.sequenceEmptyText)
        container.removeAllViews()
        if (entries.isEmpty()) { emptyText.visibility = View.VISIBLE; return }
        emptyText.visibility = View.GONE
        val inflater = LayoutInflater.from(requireContext())
        for (entry in entries) {
            val row = inflater.inflate(R.layout.view_sequence_item, container, false)
            row.findViewById<ImageView>(R.id.itemIcon).setImageResource(if (entry.isMainshock) R.drawable.ic_tier_p0 else R.drawable.ic_tier_p1)
            val label = getString(if (entry.isMainshock) R.string.sequence_mainshock else R.string.sequence_aftershock)
            row.findViewById<TextView>(R.id.itemText).text =
                getString(R.string.sequence_item_format, label, entry.magnitude, Eew.mmiRoman(entry.mmiAtUser), formatTime(entry.timestamp))
            container.addView(row)
        }
    }

    private fun formatTime(ts: Long): String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(java.util.Date(ts))
}
