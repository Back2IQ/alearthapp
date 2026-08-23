package app.tda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AffiliateTest {
    @Test fun amazonUrlHasHostQueryAndTag() {
        val u = Affiliate.amazonSearchUrl("Erste-Hilfe-Set")
        assertTrue(u.startsWith("https://www.amazon.com.tr/s?k="))
        assertTrue(u.contains("Erste-Hilfe-Set"))
        assertTrue(u.contains("&tag=TDA-PLACEHOLDER-21"))
    }

    @Test fun amazonUrlEncodesSpaces() {
        val u = Affiliate.amazonSearchUrl("Trinkwasser Notvorrat")
        assertTrue(u.contains("Trinkwasser+Notvorrat") || u.contains("Trinkwasser%20Notvorrat"))
        assertTrue(!u.contains("Trinkwasser Notvorrat")) // kein rohes Leerzeichen
    }

    @Test fun localUrlIsNeutralSearch() {
        val u = Affiliate.localSearchUrl("Gummistiefel")
        assertTrue(u.startsWith("https://www.google.com/search?q="))
        assertTrue(u.contains("Gummistiefel"))
    }

    @Test fun customTagIsUsed() {
        val u = Affiliate.amazonSearchUrl("Powerbank", "MYTAG-99")
        assertEquals(true, u.endsWith("&tag=MYTAG-99"))
    }
}
