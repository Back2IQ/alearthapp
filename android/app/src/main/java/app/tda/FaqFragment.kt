package app.tda

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment

/** FAQ-Tab: 6 aufklappbare Frage/Antwort-Einträge (Inhalte aus der Web-App, #view-faq)
 * plus Button zum erneuten Start des Onboardings. */
class FaqFragment : Fragment(R.layout.fragment_faq) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupExpandable(view, R.id.faqQ1, R.id.faqA1)
        setupExpandable(view, R.id.faqQ2, R.id.faqA2)
        setupExpandable(view, R.id.faqQ3, R.id.faqA3)
        setupExpandable(view, R.id.faqQ4, R.id.faqA4)
        setupExpandable(view, R.id.faqQ5, R.id.faqA5)
        setupExpandable(view, R.id.faqQ6, R.id.faqA6)

        view.findViewById<View>(R.id.btnRestartOnboarding).setOnClickListener {
            Prefs.onboarded = false
            startActivity(Intent(requireContext(), OnboardingActivity::class.java))
        }
    }

    private fun setupExpandable(view: View, questionId: Int, answerId: Int) {
        val question = view.findViewById<TextView>(questionId)
        val answer = view.findViewById<View>(answerId)
        question.setOnClickListener {
            answer.visibility = if (answer.visibility == View.GONE) View.VISIBLE else View.GONE
        }
    }
}
