package app.alert2iq

data class GuideArticle(
    val id: String,
    val titleRes: Int,
    val icon: String,
    val phase: String, // "BEFORE", "DURING", "AFTER"
    val bulletPointsRes: List<Int>
)

data class QuizScenario(
    val id: String,
    val scenarioRes: Int,
    val optionARes: Int,
    val optionBRes: Int,
    val optionCRes: Int,
    val correctOption: String, // "A", "B", "C"
    val explanationRes: Int
)

object SurvivalGuideData {

    fun calculateBadge(score: Int, totalScenarios: Int): String {
        val pct = if (totalScenarios > 0) (score.toDouble() / totalScenarios) * 100 else 0.0
        return when {
            pct >= 90.0 -> "🛡️ Disaster Guardian"
            pct >= 70.0 -> "🥇 Survival Specialist"
            pct >= 50.0 -> "🥈 Prepared Citizen"
            else -> "🥉 Novice"
        }
    }

    val SCENARIOS = listOf(
        QuizScenario(
            id = "q1_bed",
            scenarioRes = R.string.quiz_q1_scenario,
            optionARes = R.string.quiz_q1_opt_a,
            optionBRes = R.string.quiz_q1_opt_b,
            optionCRes = R.string.quiz_q1_opt_c,
            correctOption = "C",
            explanationRes = R.string.quiz_q1_expl
        ),
        QuizScenario(
            id = "q2_gas",
            scenarioRes = R.string.quiz_q2_scenario,
            optionARes = R.string.quiz_q2_opt_a,
            optionBRes = R.string.quiz_q2_opt_b,
            optionCRes = R.string.quiz_q2_opt_c,
            correctOption = "C",
            explanationRes = R.string.quiz_q2_expl
        ),
        QuizScenario(
            id = "q3_trapped",
            scenarioRes = R.string.quiz_q3_scenario,
            optionARes = R.string.quiz_q3_opt_a,
            optionBRes = R.string.quiz_q3_opt_b,
            optionCRes = R.string.quiz_q3_opt_c,
            correctOption = "C",
            explanationRes = R.string.quiz_q3_expl
        ),
        QuizScenario(
            id = "q4_highrise",
            scenarioRes = R.string.quiz_q4_scenario,
            optionARes = R.string.quiz_q4_opt_a,
            optionBRes = R.string.quiz_q4_opt_b,
            optionCRes = R.string.quiz_q4_opt_c,
            correctOption = "B",
            explanationRes = R.string.quiz_q4_expl
        ),
        QuizScenario(
            id = "q5_tsunami",
            scenarioRes = R.string.quiz_q5_scenario,
            optionARes = R.string.quiz_q5_opt_a,
            optionBRes = R.string.quiz_q5_opt_b,
            optionCRes = R.string.quiz_q5_opt_c,
            correctOption = "A",
            explanationRes = R.string.quiz_q5_expl
        )
    )
}
