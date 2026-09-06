package app.alert2iq

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class AlertDeduplicatorTest {

    @Before
    fun setUp() {
        AlertDeduplicator.reset()
    }

    @Test
    fun testFirstArrivalIsProcessed() {
        val action = AlertDeduplicator.evaluate("eq-1", 1, PushEval.Level.ALARM)
        assertEquals(AlertDeduplicator.Action.PROCESS, action)
    }

    @Test
    fun testDuplicateSameVersionIsIgnored() {
        AlertDeduplicator.evaluate("eq-1", 1, PushEval.Level.ALARM)
        val duplicate = AlertDeduplicator.evaluate("eq-1", 1, PushEval.Level.ALARM)
        assertEquals(AlertDeduplicator.Action.IGNORE, duplicate)
    }

    @Test
    fun testOlderVersionIsIgnored() {
        AlertDeduplicator.evaluate("eq-1", 2, PushEval.Level.ALARM)
        val older = AlertDeduplicator.evaluate("eq-1", 1, PushEval.Level.ALARM)
        assertEquals(AlertDeduplicator.Action.IGNORE, older)
    }

    @Test
    fun testHigherVersionUpgradeToAlarmIsProcessed() {
        AlertDeduplicator.evaluate("eq-1", 1, PushEval.Level.NOTIFY)
        val upgrade = AlertDeduplicator.evaluate("eq-1", 2, PushEval.Level.ALARM)
        assertEquals(AlertDeduplicator.Action.PROCESS, upgrade)
    }

    @Test
    fun testHigherVersionSameLevelIsUpdateOnly() {
        AlertDeduplicator.evaluate("eq-1", 1, PushEval.Level.ALARM)
        val update = AlertDeduplicator.evaluate("eq-1", 2, PushEval.Level.ALARM)
        assertEquals(AlertDeduplicator.Action.UPDATE_ONLY, update)
    }
}