package cz.uhk.macroflow.pokemon.stamina

import cz.uhk.macroflow.pokemon.stamina.Stamina.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pravidla energie Makrosvěta (docs/adr/0065). */
class StaminaTest {
    private val h = 60L * 60 * 1000

    @Test fun firstEverEntryFills() {
        assertEquals(State(base = 100, over = 0, refillDay = "2026-10-07"), Stamina.refill(State(base = 7, over = 3), "2026-10-07", 0))
    }

    @Test fun newDayRefillsOnlyAfterFourHoursAway() {
        val s = State(base = 10, over = 20, refillDay = "2026-10-06", lastExitAt = 100 * h)
        assertEquals(s, Stamina.refill(s, "2026-10-07", 100 * h + 2 * h))           // přes půlnoc, jen 2 h pryč
        val r = Stamina.refill(s, "2026-10-07", 100 * h + 4 * h)
        assertEquals(100, r.base); assertEquals(0, r.over)                            // overstim se maže
        assertEquals(s, Stamina.refill(s, "2026-10-07", 99 * h))                      // hodiny dozadu
        val same = s.copy(refillDay = "2026-10-07")
        assertEquals(same, Stamina.refill(same, "2026-10-07", 200 * h))               // dnes už doplněno
    }

    @Test fun spendsOverstimFirstAndRefusesWhenShort() {
        assertEquals(State(base = 100, over = 7), Stamina.spend(State(base = 100, over = 10), 3))
        assertEquals(State(base = 98, over = 0), Stamina.spend(State(base = 100, over = 2), 4))
        assertNull(Stamina.spend(State(base = 2, over = 1), 4))
        assertEquals(State(base = 0, over = 0), Stamina.spend(State(base = 3, over = 1), 4))
    }

    @Test fun grantFillsBaseThenOverstimUpToCap() {
        assertEquals(State(base = 100, over = 5), Stamina.grant(State(base = 90), 15))
        assertEquals(State(base = 100, over = 50), Stamina.grant(State(base = 100, over = 45), 20))
        assertEquals(State(base = 60), Stamina.grant(State(base = 50), 10))
    }

    @Test fun quickReturnIsFreeButNotPingPong() {
        var s = State()
        assertEquals(3, Stamina.transitionCost(s, "MEADOW", 0))
        s = Stamina.moved(s, from = "MEADOW", cost = 3, now = 1000)                    // louka → les
        assertEquals(0, Stamina.transitionCost(s, "MEADOW", 1000 + 60_000))           // hned zpátky zdarma
        assertEquals(3, Stamina.transitionCost(s, "TOWN", 1000 + 60_000))             // jinam se platí
        assertEquals(3, Stamina.transitionCost(s, "MEADOW", 1000 + Stamina.RETURN_FREE_MS))
        s = Stamina.moved(s, from = "FOREST", cost = 0, now = 2000)                   // les → louka zdarma
        assertEquals(3, Stamina.transitionCost(s, "FOREST", 3000))                    // znovu do lesa už ne
    }
}
