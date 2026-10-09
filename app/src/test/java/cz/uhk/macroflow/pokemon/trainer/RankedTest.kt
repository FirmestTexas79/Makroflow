package cz.uhk.macroflow.pokemon.trainer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RankedTest {

    @Test
    fun tiersByPoints() {
        assertEquals(Ranked.Tier.BRONZE, Ranked.tierOf(0))
        assertEquals(Ranked.Tier.BRONZE, Ranked.tierOf(99))
        assertEquals(Ranked.Tier.SILVER, Ranked.tierOf(100))
        assertEquals(Ranked.Tier.MASTER, Ranked.tierOf(5000))
        assertEquals(0.5f, Ranked.progress(50), 0.001f)
        assertEquals(1f, Ranked.progress(1200), 0.001f)
    }

    @Test
    fun winsAndLossesWithTierFloor() {
        assertEquals(20, Ranked.apply(0, won = true).delta)
        assertEquals(0, Ranked.apply(5, won = false).after)            // Bronz až na nulu
        assertEquals(100, Ranked.apply(105, won = false).after)        // ze Stříbra nespadneš
        val up = Ranked.apply(90, won = true)
        assertTrue(up.promoted); assertFalse(up.demoted)
        assertTrue(Ranked.WIN <= Ranked.MAX_GAIN)
    }

    @Test
    fun opponentsGrowWithRank() {
        val my = listOf(TrainerMon("001", 10), TrainerMon("012", 10))
        val sizes = Ranked.Tier.entries.map { Ranked.opponent(it.min, my, seed = 3).team.size }
        assertEquals(listOf(1, 2, 3, 4, 5, 6), sizes)
        val bronze = Ranked.opponent(0, my, 3).team.first().level
        val master = Ranked.opponent(1000, my, 3).team.first().level
        assertTrue(master > bronze)
        Ranked.Tier.entries.forEach { t ->
            val o = Ranked.opponent(t.min, my, 7)
            assertTrue(o.ranked)
            assertEquals(o.team.size, Trainers.sanitize(o)!!.team.size)
        }
    }

    @Test
    fun opponentStableUntilNextMatch() {
        val my = listOf(TrainerMon("001", 5))
        assertEquals(Ranked.opponent(120, my, 4), Ranked.opponent(120, my, 4))
        val json = Trainers.fromJson(Trainers.toJson(Ranked.opponent(120, my, 4)))!!
        assertTrue(json.ranked)
    }
}
