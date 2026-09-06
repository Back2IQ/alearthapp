package app.alert2iq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PushEvalTest {

    private val singleIstanbulSubs = """
        {
            "lang": "de",
            "subscriptions": [
                {
                    "lat": 41.01,
                    "lon": 28.98,
                    "label": "Istanbul Home",
                    "notifyMag": 4.0,
                    "alarmMag": 6.0,
                    "radiusKm": 150.0
                }
            ]
        }
    """.trimIndent()

    private val multiSubs = """
        {
            "lang": "de",
            "subscriptions": [
                {
                    "lat": 41.01,
                    "lon": 28.98,
                    "label": "Istanbul",
                    "notifyMag": 4.0,
                    "alarmMag": 6.0,
                    "radiusKm": 150.0
                },
                {
                    "lat": 39.93,
                    "lon": 32.86,
                    "label": "Ankara",
                    "notifyMag": 4.0,
                    "alarmMag": 6.0,
                    "radiusKm": 150.0
                }
            ]
        }
    """.trimIndent()

    @Test
    fun testEventAlarmNearIstanbul() {
        val decision = PushEval.evaluate(41.10, 29.00, 6.5, singleIstanbulSubs)
        assertEquals(PushEval.Level.ALARM, decision.level)
        assertEquals("Istanbul Home", decision.cityName)
        assertEquals(41.01, decision.userLat, 0.0001)
        assertEquals(28.98, decision.userLon, 0.0001)
        assertTrue(decision.distKm < 20.0)
        assertEquals(Eew.mmi(6.5, decision.distKm), decision.mmi, 0.0001)
    }

    @Test
    fun testEventNotifyNearIstanbul() {
        val decision = PushEval.evaluate(41.10, 29.00, 4.5, singleIstanbulSubs)
        assertEquals(PushEval.Level.NOTIFY, decision.level)
        assertEquals("Istanbul Home", decision.cityName)
    }

    @Test
    fun testEventOutOfRadiusDrop() {
        val decision = PushEval.evaluate(45.0, 29.0, 6.5, singleIstanbulSubs)
        assertEquals(PushEval.Level.DROP, decision.level)
    }

    @Test
    fun testEventUnderNotifyMagDrop() {
        val decision = PushEval.evaluate(41.10, 29.00, 3.5, singleIstanbulSubs)
        assertEquals(PushEval.Level.DROP, decision.level)
    }

    @Test
    fun testMultipleSubsChoosesClosestAnkara() {
        val decision = PushEval.evaluate(39.95, 32.90, 6.5, multiSubs)
        assertEquals(PushEval.Level.ALARM, decision.level)
        assertEquals("Ankara", decision.cityName)
        assertEquals(39.93, decision.userLat, 0.0001)
        assertEquals(32.86, decision.userLon, 0.0001)
    }

    @Test
    fun testEmptyOrInvalidJsonDrop() {
        assertEquals(PushEval.Level.DROP, PushEval.evaluate(41.10, 29.00, 6.5, "").level)
        assertEquals(PushEval.Level.DROP, PushEval.evaluate(41.10, 29.00, 6.5, "{}").level)
        assertEquals(PushEval.Level.DROP, PushEval.evaluate(41.10, 29.00, 6.5, "{ invalid json ").level)
    }
}