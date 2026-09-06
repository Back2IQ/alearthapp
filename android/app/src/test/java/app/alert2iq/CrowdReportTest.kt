package app.alert2iq

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class CrowdReportTest {
    @Test fun triggerJsonMatchesWireContract() {
        val o = JSONObject(CrowdReport.triggerJson("abc", "d410_289", 1000L, 1000L))
        assertEquals("abc", o.getString("device_hash"))
        assertEquals("d410_289", o.getString("cell"))
        assertEquals("1000", o.getString("trigger_ms"))   // strings per dict[str,str]
        assertEquals("1000", o.getString("clock_unc_ms"))
    }

    @Test fun pingJsonMatchesWireContract() {
        val o = JSONObject(CrowdReport.pingJson("abc", "d410_289", 2000L))
        assertEquals("abc", o.getString("device_hash"))
        assertEquals("d410_289", o.getString("cell"))
        assertEquals("2000", o.getString("ping_ms"))
    }
}
