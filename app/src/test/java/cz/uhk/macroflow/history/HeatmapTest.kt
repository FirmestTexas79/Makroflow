package cz.uhk.macroflow.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class HeatmapTest {

    private val today = LocalDate.of(2026, 9, 24) // čtvrtek

    @Test
    fun stepLevelsUseFixedHealthBounds() {
        assertEquals(Heatmap.NO_DATA, Heatmap.level(HeatMetric.STEPS, null))
        assertEquals(0, Heatmap.level(HeatMetric.STEPS, 0.0))
        assertEquals(1, Heatmap.level(HeatMetric.STEPS, 3999.0))
        assertEquals(2, Heatmap.level(HeatMetric.STEPS, 4000.0))
        assertEquals(3, Heatmap.level(HeatMetric.STEPS, 7000.0))
        assertEquals(4, Heatmap.level(HeatMetric.STEPS, 15000.0))
    }

    @Test
    fun goalLevelIsNumberOfHitBands() {
        assertEquals(0, Heatmap.level(HeatMetric.GOALS, 0.0))
        assertEquals(3, Heatmap.level(HeatMetric.GOALS, 3.0))
        assertEquals(4, Heatmap.level(HeatMetric.GOALS, 4.0))
    }

    @Test
    fun activeKcalUsesOwnQuartiles() {
        val values = (1..8).associate { today.minusDays(it.toLong()) to it * 100.0 } + (today to 0.0)
        val l = Heatmap.levels(HeatMetric.ACTIVE_KCAL, values)
        assertEquals(0, l[today])
        assertEquals(1, l[today.minusDays(1)])   // 100 kcal = nejnižší čtvrtina
        assertEquals(4, l[today.minusDays(8)])   // 800 kcal = nejvyšší čtvrtina
        // Každá čtvrtina má dva dny
        assertEquals(listOf(2, 2, 2, 2), (1..4).map { lv -> l.values.count { it == lv } })
    }

    @Test
    fun quartilesOfEmptyAreNull() {
        assertNull(Heatmap.quartiles(emptyList()))
    }

    @Test
    fun weekColumnsStartMondayAndHideFuture() {
        val cols = Heatmap.weekColumns(today, 53)
        assertEquals(53, cols.size)
        assertEquals(DayOfWeek.MONDAY, cols.first().first()!!.dayOfWeek)
        val last = cols.last()
        assertEquals(today, last[3])          // čtvrtek
        assertNull(last[4]); assertNull(last[6])
        assertTrue(cols.flatten().filterNotNull().zipWithNext().all { (a, b) -> Heatmap.daysBetween(a, b) == 1L })
    }

    @Test
    fun streakIgnoresUnfinishedToday() {
        val v = mapOf(
            today to 2000.0,                  // dnešek ještě běží → nepřeruší
            today.minusDays(1) to 8000.0,
            today.minusDays(2) to 9000.0,
            today.minusDays(3) to 3000.0,
            today.minusDays(4) to 12000.0
        )
        assertEquals(2, Heatmap.streak(v, today) { it >= 7000 })
        assertEquals(3, Heatmap.streak(v + (today to 7500.0), today) { it >= 7000 })
    }

    @Test
    fun czechPlurals() {
        assertEquals(listOf("0 dní", "1 den", "3 dny", "5 dní"), listOf(0, 1, 3, 5).map { Heatmap.days(it) })
    }

    @Test
    fun summaryForSteps() {
        val v = mapOf(today.minusDays(1) to 8000.0, today.minusDays(2) to 6000.0, today.minusDays(3) to null)
        val s = Heatmap.summary(HeatMetric.STEPS, v, today)
        assertTrue(s, s.startsWith("Ø 7 000 kroků/den · 1 den ≥ 7 000 · série 1 den"))
    }
}
