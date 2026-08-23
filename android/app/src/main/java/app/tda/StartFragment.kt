package app.tda

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * Echte Startseite (entspricht dem „Start"-Tab der Web-App, NICHT dem Seismographen):
 * großer Status-Hero, Standort, „Alarm zurücksetzen" nach einem Ereignis und ein
 * Panel „nächstes Beben in der Nähe" (an die Live-Feeds angebunden in Etappe 2/3).
 * Der Seismograph lebt jetzt im Netzwerk-Tab ([NetworkFragment]).
 */
class StartFragment : Fragment(R.layout.fragment_start) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<Button>(R.id.btnClearStatus).setOnClickListener { TestScenarios.reset() }
        view.findViewById<TextView>(R.id.locationBadge).text =
            getString(R.string.start_location_format, Prefs.selectedCity().displayName)
        viewLifecycleOwner.lifecycleScope.launch {
            EventBus.status.collect { renderHero(view, it) }
        }
    }

    override fun onResume() {
        super.onResume()
        view?.findViewById<TextView>(R.id.locationBadge)?.text =
            getString(R.string.start_location_format, Prefs.selectedCity().displayName)
    }

    private fun renderHero(view: View, status: StatusState) {
        val icon = view.findViewById<ImageView>(R.id.heroIcon)
        val title = view.findViewById<TextView>(R.id.heroTitle)
        val hero = view.findViewById<View>(R.id.hero)
        val (drawableRes, stringRes, attrRes) = when (status) {
            StatusState.READY -> Triple(R.drawable.ic_status_ready, R.string.status_ready, R.attr.colorReady)
            StatusState.ATTENTION -> Triple(R.drawable.ic_status_attention, R.string.status_attention, R.attr.colorAttention)
            StatusState.ALARM_P0 -> Triple(R.drawable.ic_tier_p0, R.string.status_alarm_p0, R.attr.colorTierP0)
            StatusState.CONFIRMED_P2 -> Triple(R.drawable.ic_tier_p2, R.string.status_confirmed_p2, R.attr.colorTierP2)
            StatusState.DISTURBANCE_DISCARDED -> Triple(R.drawable.ic_status_block, R.string.status_disturbance_discarded, R.attr.colorDisturbance)
        }
        icon.setImageResource(drawableRes)
        title.setText(stringRes)
        hero.backgroundTintList = ColorStateList.valueOf(requireContext().themeColor(attrRes))
        view.findViewById<Button>(R.id.btnClearStatus).visibility =
            if (status == StatusState.READY) View.GONE else View.VISIBLE
    }
}
