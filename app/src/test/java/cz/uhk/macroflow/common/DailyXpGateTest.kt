package cz.uhk.macroflow.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyXpGateTest {

    @Test
    fun onlyOncePerDay() {
        assertTrue(DailyXpGate.shouldAward(null, "2026-09-24"))
        assertFalse(DailyXpGate.shouldAward("2026-09-24", "2026-09-24"))
        assertTrue(DailyXpGate.shouldAward("2026-09-23", "2026-09-24"))
    }

    @Test
    fun legacyKeyCountsForToday() {
        // Starý systém dnes už vyplatil → nový ho nevyplatí znovu
        assertEquals("2026-09-24", DailyXpGate.effectiveLastDate(null, 267, 267, "2026-09-24"))
        assertNull(DailyXpGate.effectiveLastDate(null, 266, 267, "2026-09-24"))
        assertEquals("2026-09-20", DailyXpGate.effectiveLastDate("2026-09-20", 267, 267, "2026-09-24"))
    }
}
