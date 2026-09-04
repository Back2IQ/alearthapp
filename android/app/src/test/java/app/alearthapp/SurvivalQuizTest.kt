package app.alearthapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SurvivalQuizTest {

    @Test
    fun testBadgeCalculation() {
        assertEquals("🛡️ Disaster Guardian", SurvivalGuideData.calculateBadge(5, 5))
        assertEquals("🥇 Survival Specialist", SurvivalGuideData.calculateBadge(4, 5))
        assertEquals("🥈 Prepared Citizen", SurvivalGuideData.calculateBadge(3, 5))
        assertEquals("🥉 Novice", SurvivalGuideData.calculateBadge(1, 5))
    }

    @Test
    fun testScenariosStructure() {
        val scenarios = SurvivalGuideData.SCENARIOS
        assertTrue(scenarios.isNotEmpty())
        assertEquals(5, scenarios.size)

        for (s in scenarios) {
            assertTrue(s.id.isNotBlank())
            assertTrue(s.correctOption in listOf("A", "B", "C"))
        }
    }
}
