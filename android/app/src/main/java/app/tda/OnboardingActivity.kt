package app.tda

import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

/**
 * First-run onboarding (6 steps), content mirrored 1:1 from the web app's onboarding
 * overlay (webapp/index.html #onboarding, webapp/lib/prep.js PROFILES) -- trimmed to
 * what is meaningful natively: no GPS step (no location permission requested here),
 * no sound toggle, no "would have warned you" retro preview (feeds not wired yet).
 *
 * Single Activity, no ViewPager2 (not a declared Gradle dependency) -- steps are
 * plain views toggled via visibility, same approach as the web overlay's
 * `.ob-card[hidden]` swap (see obGoTo in index.html).
 *
 * Expects no Intent extras. Always calls Prefs.init(this) first (may be the first
 * Prefs access of the process if launched before MainActivity).
 */
class OnboardingActivity : AppCompatActivity() {

    private data class Profile(val radiusKm: Int, val minMag: Double)

    private val profileCautious = Profile(radiusKm = 500, minMag = 2.5)
    private val profileBalanced = Profile(radiusKm = 300, minMag = 3.5)
    private val profileStrong = Profile(radiusKm = 150, minMag = 5.0)

    private var step = 1
    private val totalSteps = 6
    private var selectedCityId: String = ""

    private lateinit var dotsContainer: LinearLayout
    private lateinit var stepViews: List<View>
    private lateinit var btnBack: android.widget.Button
    private lateinit var btnNext: android.widget.Button

    override fun onCreate(savedInstanceState: Bundle?) {
        Prefs.init(this)
        Prefs.applyNightMode()
        setTheme(Prefs.themeStyleRes(alert = false))
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        dotsContainer = findViewById(R.id.onboardingDots)
        stepViews = listOf(
            findViewById(R.id.onboardingStep1), findViewById(R.id.onboardingStep2),
            findViewById(R.id.onboardingStep3), findViewById(R.id.onboardingStep4),
            findViewById(R.id.onboardingStep5), findViewById(R.id.onboardingStep6)
        )
        btnBack = findViewById(R.id.onboardingBtnBack)
        btnNext = findViewById(R.id.onboardingBtnNext)

        selectedCityId = Prefs.cityId
        setupCityChips()
        setupProfileRadios()
        setupNotifyButton()
        setupDrillButton()
        setupNav()

        goTo(1)
    }

    private fun setupCityChips() {
        val group = findViewById<ChipGroup>(R.id.onboardingCityChips)
        Eew.CITIES.forEach { city ->
            val chip = Chip(this).apply {
                text = city.displayName
                isCheckable = true
                isChecked = city.id == selectedCityId
                tag = city.id
            }
            group.addView(chip)
        }
        group.setOnCheckedStateChangeListener { _, checkedIds ->
            val checkedChip = checkedIds.firstOrNull()?.let { group.findViewById<Chip>(it) }
            val cityId = checkedChip?.tag as? String ?: return@setOnCheckedStateChangeListener
            selectedCityId = cityId
            Prefs.cityId = cityId
        }
    }

    private fun setupProfileRadios() {
        val group = findViewById<RadioGroup>(R.id.onboardingProfiles)
        val cautious = findViewById<RadioButton>(R.id.profileCautious)
        val balanced = findViewById<RadioButton>(R.id.profileBalanced)
        val strong = findViewById<RadioButton>(R.id.profileStrong)

        cautious.text = getString(R.string.ob_profile_row_format, getString(R.string.ob_profile_cautious), getString(R.string.ob_profile_cautious_desc))
        balanced.text = getString(R.string.ob_profile_row_format, getString(R.string.ob_profile_balanced), getString(R.string.ob_profile_balanced_desc))
        strong.text = getString(R.string.ob_profile_row_format, getString(R.string.ob_profile_strong), getString(R.string.ob_profile_strong_desc))

        fun selectFromPrefs() {
            val id = when {
                Prefs.alertRadiusKm == profileCautious.radiusKm && Prefs.minMagnitude == profileCautious.minMag -> R.id.profileCautious
                Prefs.alertRadiusKm == profileStrong.radiusKm && Prefs.minMagnitude == profileStrong.minMag -> R.id.profileStrong
                else -> R.id.profileBalanced
            }
            group.check(id)
        }
        selectFromPrefs()

        group.setOnCheckedChangeListener { _, checkedId ->
            val profile = when (checkedId) {
                R.id.profileCautious -> profileCautious
                R.id.profileStrong -> profileStrong
                else -> profileBalanced
            }
            Prefs.alertRadiusKm = profile.radiusKm
            Prefs.minMagnitude = profile.minMag
        }
    }

    private fun setupNotifyButton() {
        findViewById<View>(R.id.onboardingBtnNotify).setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
            }
        }
    }

    private fun setupDrillButton() {
        findViewById<View>(R.id.onboardingBtnDrill).setOnClickListener {
            TestScenarios.runEarthquakeDrill(Prefs.selectedCity())
            finishOnboarding()
        }
    }

    private fun setupNav() {
        btnBack.setOnClickListener { goTo(step - 1) }
        findViewById<View>(R.id.onboardingBtnSkip).setOnClickListener { finishOnboarding() }
        btnNext.setOnClickListener {
            if (step == totalSteps) finishOnboarding() else goTo(step + 1)
        }
    }

    private fun goTo(newStep: Int) {
        step = newStep.coerceIn(1, totalSteps)
        stepViews.forEachIndexed { index, view -> view.visibility = if (index == step - 1) View.VISIBLE else View.GONE }
        renderDots()
        btnBack.visibility = if (step == 1) View.INVISIBLE else View.VISIBLE
        btnNext.text = if (step == totalSteps) getString(R.string.ob_finish) else getString(R.string.ob_next)
    }

    private fun renderDots() {
        dotsContainer.removeAllViews()
        val activeColor = themeColor(R.attr.colorReady)
        val inactiveColor = ContextCompat.getColor(this, android.R.color.darker_gray)
        val sizePx = (8 * resources.displayMetrics.density).toInt()
        val marginPx = (4 * resources.displayMetrics.density).toInt()
        for (i in 1..totalSteps) {
            val dot = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).apply {
                    marginStart = marginPx; marginEnd = marginPx
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(if (i == step) activeColor else inactiveColor)
                }
                contentDescription = getString(R.string.ob_step_cd, i, totalSteps)
            }
            dotsContainer.addView(dot)
        }
    }

    private fun finishOnboarding() {
        Prefs.onboarded = true
        finish()
    }

    companion object {
        private const val REQUEST_NOTIFICATIONS = 201
    }
}
