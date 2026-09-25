package cz.uhk.macroflow.pokemon.evolution

import cz.uhk.macroflow.pokemon.evolution.SpirraEvolution.Branch
import cz.uhk.macroflow.pokemon.evolution.SpirraEvolution.Day
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SpirraEvolutionTest {

    private val d0 = LocalDate.of(2026, 9, 1)
    private fun day(i: Int, fiber: Double = 0.0, target: Double = 30.0) = Day(d0.plusDays(i.toLong()), fiberG = fiber, fiberTargetG = target)

    @Test
    fun branchesMatchMakrodex() {
        assertEquals(listOf("013", "014", "015", "016", "017", "018"), Branch.entries.map { it.id })
        assertFalse(Branch.entries.any { it.id == SpirraEvolution.DRAKIRRA_ID })
    }

    @Test
    fun sumsAcrossActiveDays() {
        val days = (0 until 10).map { Day(d0.plusDays(it.toLong()), burnedKcal = 400, waterMl = 2600, nightMeals = 1, steps = 9000, workoutSets = 15) }
        val p = SpirraEvolution.progress(days)
        assertEquals(4000, p[Branch.FLAMIRRA]); assertEquals(26_000, p[Branch.AQUIRRA])
        assertEquals(10, p[Branch.SHADIRRA]); assertEquals(90_000, p[Branch.CHARMIRRA]); assertEquals(150, p[Branch.GLACIRRA])
        // stejný den dvakrát se nepočítá dvakrát
        assertEquals(26_000, SpirraEvolution.progress(days + days.first())[Branch.AQUIRRA])
    }

    @Test
    fun fiberStreakMustBeConsecutiveAndInRange() {
        assertTrue(SpirraEvolution.fiberInRange(day(0, 30.0)))
        assertTrue(SpirraEvolution.fiberInRange(day(0, 27.0)))
        assertFalse(SpirraEvolution.fiberInRange(day(0, 26.0)))       // pod 90 %
        assertFalse(SpirraEvolution.fiberInRange(day(0, 43.0)))       // nad 140 %
        assertFalse(SpirraEvolution.fiberInRange(day(0, 30.0, target = 0.0)))
        assertEquals(2, SpirraEvolution.bestFiberStreak(listOf(day(0, 30.0), day(1, 30.0), day(2, 10.0), day(3, 30.0))))
        // mezera v kalendáři (den bez Spirry) řadu přeruší
        assertEquals(2, SpirraEvolution.bestFiberStreak(listOf(day(0, 30.0), day(1, 30.0), day(3, 30.0))))
        assertEquals(3, SpirraEvolution.bestFiberStreak(listOf(day(5, 30.0), day(3, 30.0), day(4, 30.0))))
    }

    @Test
    fun readyPicksReachedBranch() {
        assertEquals(null, SpirraEvolution.ready(SpirraEvolution.progress(listOf(day(0)))))
        val p = mapOf(Branch.FLAMIRRA to 5200, Branch.AQUIRRA to 30_000, Branch.VERDIRRA to 1,
            Branch.SHADIRRA to 0, Branch.CHARMIRRA to 0, Branch.GLACIRRA to 0)
        assertEquals(Branch.AQUIRRA, SpirraEvolution.ready(p))            // 120 % > 104 %
        assertEquals(Branch.GLACIRRA, SpirraEvolution.ready(mapOf(Branch.GLACIRRA to 200)))
    }

    @Test
    fun nightMeals() {
        assertTrue(SpirraEvolution.isNight("21:00")); assertTrue(SpirraEvolution.isNight("23:59"))
        assertTrue(SpirraEvolution.isNight("00:30")); assertTrue(SpirraEvolution.isNight("4:59"))
        assertFalse(SpirraEvolution.isNight("05:00")); assertFalse(SpirraEvolution.isNight("20:59"))
        assertFalse(SpirraEvolution.isNight(""))
    }

    @Test
    fun textsAndStorage() {
        assertEquals("1 250 / 5 000 kcal", SpirraEvolution.progressText(Branch.FLAMIRRA, 1250))
        assertEquals("12,5 / 25 l", SpirraEvolution.progressText(Branch.AQUIRRA, 12_500))
        assertEquals("100 000 / 100 000 kroků", SpirraEvolution.progressText(Branch.CHARMIRRA, 140_000))
        val d = Day(d0, 321, 2750, 31.5, 30.0, 2, 8123, 14)
        assertEquals(d, SpirraEvolution.decode(SpirraEvolution.encode(d)))
        assertEquals(null, SpirraEvolution.decode("rozbité"))
    }
}
