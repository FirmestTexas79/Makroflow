package cz.uhk.macroflow.pokemon.story

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Trpitel se probudí jen vyváženému dni (hladovění se nepočítá) a volba konce je jedna. */
class TrpitelTest {
    @Test fun onlyBalancedDayWakesHim() {
        assertFalse(Trpitel.balanced(0))
        assertFalse(Trpitel.balanced(89))
        assertTrue(Trpitel.balanced(90))
        assertTrue(Trpitel.balanced(110))
        assertFalse(Trpitel.balanced(111))
    }

    @Test fun endingFromFlags() {
        assertNull(Trpitel.ending { false })
        assertEquals(Trpitel.Ending.BLOOM, Trpitel.ending { it == "konec_kveteni" })
        assertTrue(Trpitel.Ending.entries.all { it.key in Trpitel.KEYS })
    }
}
