package cz.uhk.macroflow.report

import cz.uhk.macroflow.training.log.LoggedSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WeeklyReportModelTest {
    private val mon = LocalDate.of(2026, 11, 2).toEpochDay().toInt()

    private fun dayFood(i: Int, kcal: Double = 2400.0, protein: Double = 160.0) =
        DayFood(mon + i, kcal, protein, 270.0, 70.0, 2400.0, 160.0, 270.0, 70.0)
    private fun set(day: Int, id: String, kg: Double, reps: Int) = LoggedSet(day = day, exerciseId = id, weightKg = kg, reps = reps)
    private fun nights(sec: Int, count: Int = 7, bed: Int = 1410) = (0 until count).map { NightIn(mon + it, sec, 80, bed) }

    /** Týden, kde je všechno dobré (hubnutí zdravým tempem). */
    private fun good() = WeekIn(
        monday = mon, today = mon + 8, goal = "CUT",
        food = (0..5).map { dayFood(it) },
        plannedSoFar = 4, doneDays = 4,
        weekSets = listOf(set(mon, "bench", 100.0, 5)),
        nights = nights(7 * 3600 + 3000),
        weighIns = 4, trendKg = 84.0, trendChangeKg = -0.4,
        steps = List(6) { 8000 }, stepGoal = 7000,
        waterDays = 5, waterLoggedDays = 6, closedDays = 7, checkInDays = 6, avgEnergy = 3.8
    )

    private fun Report.card(d: Domain) = cards.first { it.domain == d }

    @Test fun goodWeekIsAllGoodAndKeepsRhythm() {
        val r = WeeklyReportModel.build(good())
        assertTrue(r.cards.all { it.status == Status.GOOD })
        assertTrue(r.headline.startsWith("Povedlo se"))
        assertFalse(r.headline.contains("Pozor"))
        assertEquals(listOf("Drž stávající rytmus, nic zásadního nemusíš měnit."), r.actions)
        assertFalse(r.gaps)
    }

    @Test fun emptyWeekIsGapsNotFailure() {
        val r = WeeklyReportModel.build(WeekIn(monday = mon, today = mon + 8))
        assertTrue(r.gaps)
        assertTrue(r.cards.all { it.status == Status.NONE })
        assertTrue(r.headline.startsWith("Týden s mezerami"))
        assertTrue(r.actions.isNotEmpty() && r.actions.size <= 3)
    }

    @Test fun foodNeedsThreeDaysAndUnderEatingWarns() {
        assertEquals(Status.NONE, WeeklyReportModel.build(good().copy(food = (0..1).map { dayFood(it) })).card(Domain.FOOD).status)
        val low = WeeklyReportModel.build(good().copy(food = (0..5).map { dayFood(it, kcal = 1700.0) }))
        assertEquals(Status.WARN, low.card(Domain.FOOD).status)
        assertTrue(low.actions.any { it.startsWith("Přidej jídlo") })
        assertTrue(low.headline.contains("Pozor na příjem jídla"))
    }

    @Test fun sleepWarnComesBeforeFoodAndNamesBedtime() {
        val w = good().copy(nights = nights(5 * 3600 + 1800), food = (0..5).map { dayFood(it, kcal = 1700.0) })
        val r = WeeklyReportModel.build(w)
        assertEquals(Status.WARN, r.card(Domain.SLEEP).status)
        assertTrue(r.actions[0].contains("v posteli do 23:00"))
        assertTrue(r.actions[1].startsWith("Přidej jídlo"))
    }

    @Test fun cutTooFastIsFirstActionAndAddsCalories() {
        val r = WeeklyReportModel.build(good().copy(trendKg = 80.0, trendChangeKg = -1.2))
        assertEquals(Status.WARN, r.card(Domain.BODY).status)
        assertTrue(r.actions[0].contains("150 kcal"))
        assertTrue(r.headline.contains("Pozor na tempo změny váhy"))
    }

    @Test fun bodyRulesByGoal() {
        assertEquals(Status.GOOD, WeeklyReportModel.build(good().copy(goal = "BULK", trendKg = 80.0, trendChangeKg = 0.2)).card(Domain.BODY).status)
        assertEquals(Status.WARN, WeeklyReportModel.build(good().copy(goal = "BULK", trendKg = 80.0, trendChangeKg = 0.6)).card(Domain.BODY).status)
        assertEquals(Status.GOOD, WeeklyReportModel.build(good().copy(goal = "MAINTAIN", trendChangeKg = 0.1)).card(Domain.BODY).status)
        assertEquals(Status.NONE, WeeklyReportModel.build(good().copy(weighIns = 2)).card(Domain.BODY).status)
    }

    @Test fun trainingCountsAgainstPlan() {
        assertEquals(Status.WARN, WeeklyReportModel.build(good().copy(plannedSoFar = 4, doneDays = 2)).card(Domain.TRAINING).status)
        assertEquals(Status.OK, WeeklyReportModel.build(good().copy(plannedSoFar = 5, doneDays = 3)).card(Domain.TRAINING).status)
        assertEquals(Status.GOOD, WeeklyReportModel.build(good().copy(plannedSoFar = 3, doneDays = 4)).card(Domain.TRAINING).status)
        val rest = WeeklyReportModel.build(good().copy(plannedSoFar = 0, doneDays = 0)).card(Domain.TRAINING)
        assertEquals(Status.NONE, rest.status)
        assertEquals(Status.OK, WeeklyReportModel.build(good().copy(plannedSoFar = 0, doneDays = 2)).card(Domain.TRAINING).status)
    }

    @Test fun volumeChangeAndTopSetAreShown() {
        val w = good().copy(
            weekSets = List(3) { set(mon + 1, "bench", 100.0, 5) },
            earlierSets = List(3) { set(mon - 5, "bench", 100.0, 5) },
            names = mapOf("bench" to "Bench")
        )
        val r = WeeklyReportModel.build(w)
        // minulý týden 3 × 500 = 1 500, letos 3 × 500 = 1 500 → ±0 %
        assertTrue(r.card(Domain.TRAINING).details.any { it.contains("±0 %") })
        assertEquals("Bench 100 kg × 5", r.topSet)
    }

    @Test fun recordNeedsHistoryAndAGain() {
        val before = listOf(set(mon - 14, "bench", 100.0, 5), set(mon - 7, "bench", 100.0, 5))
        val w = good().copy(
            weekSets = listOf(set(mon, "bench", 105.0, 5), set(mon, "squat", 140.0, 5)),
            earlierSets = before, names = mapOf("bench" to "Bench", "squat" to "Dřep")
        )
        val rec = WeeklyReportModel.build(w).records
        assertEquals(1, rec.size)                    // dřep nemá historii, takže to není rekord
        assertEquals("Bench", rec[0].name)
        assertTrue(rec[0].gainKg > 5.0)
        assertTrue(WeeklyReportModel.build(w.copy(earlierSets = before.take(1))).records.isEmpty())   // jen jeden starší trénink
        assertTrue(WeeklyReportModel.build(w.copy(weekSets = listOf(set(mon, "bench", 100.0, 5)))).records.isEmpty())
    }

    @Test fun actionsAreCappedAtThreeAndSafetyIsFirst() {
        val w = good().copy(
            trendKg = 80.0, trendChangeKg = -1.4, nights = nights(5 * 3600), food = (0..5).map { dayFood(it, kcal = 1500.0) },
            plannedSoFar = 5, doneDays = 1, steps = List(6) { 2000 }, waterDays = 1, checkInDays = 1
        )
        val r = WeeklyReportModel.build(w)
        assertEquals(3, r.actions.size)
        assertTrue(r.actions[0].contains("kcal"))
    }

    @Test fun sundayIsPreliminary() {
        assertFalse(WeeklyReportModel.build(good().copy(today = mon + 6)).isFinal)
        assertTrue(WeeklyReportModel.build(good().copy(today = mon + 7)).isFinal)
    }

    @Test fun exportHasNoPersonalDataOrFoodNames() {
        val text = WeeklyReportModel.exportText(WeeklyReportModel.build(good()), "2.–8. listopadu")
        assertTrue(text.contains("týdenní report"))
        assertFalse(text.contains("@"))
        assertTrue(text.contains("Příští týden"))
    }
}
