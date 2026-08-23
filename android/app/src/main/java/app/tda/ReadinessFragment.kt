package app.tda

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment

/**
 * Bereitschaft-Tab (spec TP-5 §D): Score + Profil-Schalter + Lücken-/Checkliste mit
 * gekennzeichneten Affiliate-Such-Links. Liest/schreibt Prefs direkt; rechnet via
 * ReadinessScore/ReadinessCatalog (getestet). Kein Affiliate außerhalb dieses Screens.
 */
class ReadinessFragment : Fragment(R.layout.fragment_readiness) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<SwitchCompat>(R.id.switchPet).apply {
            isChecked = Prefs.hasPet
            setOnCheckedChangeListener { _, c -> Prefs.hasPet = c; render(view) }
        }
        view.findViewById<SwitchCompat>(R.id.switchKids).apply {
            isChecked = Prefs.hasKids
            setOnCheckedChangeListener { _, c -> Prefs.hasKids = c; render(view) }
        }
        view.findViewById<SwitchCompat>(R.id.switchCar).apply {
            isChecked = Prefs.hasCar
            setOnCheckedChangeListener { _, c -> Prefs.hasCar = c; render(view) }
        }
        render(view)
    }

    private fun render(view: View) {
        val applicable = ReadinessCatalog.applicable(Prefs.hasPet, Prefs.hasKids, Prefs.hasCar)
        val owned = Prefs.ownedKeys
        val r = ReadinessScore.compute(applicable, owned)

        view.findViewById<TextView>(R.id.scoreText).text = getString(R.string.readiness_score_format, r.score)
        view.findViewById<TextView>(R.id.missingText).text =
            if (r.missingCount == 0) getString(R.string.readiness_ready)
            else getString(R.string.readiness_missing_format, r.missingCount)

        val container = view.findViewById<LinearLayout>(R.id.itemsContainer)
        container.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())
        for (item in applicable) {
            val row = inflater.inflate(R.layout.view_prep_item, container, false)
            val nameRes = resId("prep_item_${item.key}")
            val whyRes = resId("prep_why_${item.key}")
            row.findViewById<TextView>(R.id.itemName).text = if (nameRes != 0) getString(nameRes) else item.key
            row.findViewById<TextView>(R.id.itemWhy).text = if (whyRes != 0) getString(whyRes) else ""
            row.findViewById<CheckBox>(R.id.itemOwned).apply {
                setOnCheckedChangeListener(null)
                isChecked = owned.contains(item.key)
                setOnCheckedChangeListener { _, c -> Prefs.setOwned(item.key, c); render(view) }
            }
            row.findViewById<Button>(R.id.itemAmazon).setOnClickListener { open(Affiliate.amazonSearchUrl(item.query)) }
            row.findViewById<Button>(R.id.itemLocal).setOnClickListener { open(Affiliate.localSearchUrl(item.query)) }
            container.addView(row)
        }
    }

    private fun resId(name: String): Int =
        resources.getIdentifier(name, "string", requireContext().packageName)

    private fun open(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }
}
