package cz.uhk.macroflow.pokemon.stamina

import cz.uhk.macroflow.pokemon.stamina.Stamina.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pravidla energie Makrosvěta (docs/adr/0065). */
class StaminaTest {
    private val h = 60L * 60 * 1000

    @Test fun firstEverEntryFills() {
        assertEquals(State(base = 100, over = 0, refillDay = "2026-10-07", grantedDay = "2026-10-07"), Stamina.refill(State(base = 7, over = 3), "2026-10-07", 0))
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

    @Test fun `denni odmena respektuje stropy`() {
        assertEquals(0, Stamina.dayReward(Stamina.Day()))
        val max = Stamina.Day(checkIn = true, meals = 9, waterMl = 5000, waterGoalMl = 2500, macrosHit = true, steps = 30000, sets = 40)
        assertEquals(15 + 20 + 5 + 10 + 10 + 20 + 30 + 15, Stamina.dayReward(max))
        assertEquals(5, Stamina.dayReward(Stamina.Day(waterMl = 1250, waterGoalMl = 2500)))
        assertEquals(10, Stamina.dayReward(Stamina.Day(sets = 5)))                      // bez dokončeného tréninku
        assertEquals(10 + 2, Stamina.dayReward(Stamina.Day(steps = 4100, sets = 1)))
        assertEquals(15, Stamina.dayReward(Stamina.Day(workoutDone = true, sets = 0)))
        assertEquals(12 + 15, Stamina.dayReward(Stamina.Day(workoutDone = true, sets = 6)))   // trénink se nepočítá dvakrát
        assertEquals(20, Stamina.dayReward(Stamina.Day(cardioDone = true, restDay = true)))         // 2 × 2000 kroků + série
    }

    @Test fun `makra jen v pasmu`() {
        assert(Stamina.macrosHit(150.0, 160.0, 2600.0, 2500.0))
        assert(!Stamina.macrosHit(200.0, 160.0, 2500.0, 2500.0))                       // přebytek bílkovin
        assert(!Stamina.macrosHit(160.0, 160.0, 3000.0, 2500.0))
        assert(!Stamina.macrosHit(0.0, 0.0, 0.0, 0.0))
    }

    @Test fun `odmena se dorovnava a neklesa`() {
        var s = State(base = 90, over = 0, refillDay = "d1")
        s = Stamina.reward(s, "d1", 15)
        assertEquals(100, s.base); assertEquals(5, s.over)
        assertEquals(s, Stamina.reward(s, "d1", 15))                                    // podruhé nic
        assertEquals(s, Stamina.reward(s, "d1", 10))                                    // smazané jídlo nebere
        s = Stamina.reward(s, "d1", 20)
        assertEquals(10, s.over)
        assertEquals(15, Stamina.reward(s, "d2", 5).over)                                // nový den počítá od nuly
    }

    @Test fun `doplneni pres noc maze pripsane odmeny`() {
        val s = Stamina.reward(State(refillDay = "d1"), "d2", 30)                          // zápisy po půlnoci
        val r = Stamina.refill(s.copy(lastExitAt = 0), "d2", 10)
        assertEquals(100, r.base); assertEquals(0, r.over)
        assertEquals(30, Stamina.reward(r, "d2", 30).over)                                // dnešní odměny znovu nad čistých 100
    }
}
