package cz.uhk.macroflow.training.log

import org.junit.Assert.assertEquals
import org.junit.Test

class LbsTest {
    @Test fun convertsToKgWithTwoDecimals() {
        assertEquals(20.41, Lbs.toKg(45.0), 0.0)
        assertEquals(102.06, Lbs.toKg(225.0), 0.0)
        assertEquals(0.0, Lbs.toKg(0.0), 0.0)
    }

    @Test fun roundTripKeepsPounds() {
        listOf(5.0, 45.0, 135.0, 225.0, 315.0, 47.5).forEach { assertEquals(it, Lbs.fromKg(Lbs.toKg(it)), 0.0) }
    }

    @Test fun kgLabelShowsUpToTwoDecimals() {
        assertEquals("20,41", LogSetSheet.kg(20.41))
        assertEquals("82,5", LogSetSheet.kg(82.5))
        assertEquals("80", LogSetSheet.kg(80.0))
    }
}
