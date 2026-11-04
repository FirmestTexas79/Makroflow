package cz.uhk.macroflow.loop

import org.junit.Assert.*
import org.junit.Test

class LoopModelTest {
    private val today = 1000

    /** 20 běžných dní: spánek 7,5 h, energie 4, v pondělky objem 8000. */
    private fun history(sleep: Double = 7.5, energy: Int = 4) = (1..20).map { i ->
        DayIn(day = today - i, sleepH = sleep, energy = energy, sleepQ = 4, restHr = 55, volume = if (i % 3 == 0) 8000.0 else 0.0,
            intakeKcal = 2500.0, targetKcal = 2500.0)
    }

    private fun sit(todayRow: DayIn, train: Boolean = false, cutting: Boolean? = null, extra: List<DayIn> = history()) =
        Situation(today, extra + todayRow, train, cutting, usualBedtimeMin = 23 * 60)

    @Test fun goodNightGivesHighReadinessAndFullVolume() {
        val r = LoopModel.readiness(sit(DayIn(today, sleepH = 8.0, energy = 5, sleepQ = 5, restHr = 54)))!!
        assertTrue(r.score >= 80)
        assertEquals(1.05, LoopModel.volumeFactor(r.score), 0.001)
    }

    @Test fun badNightLowersReadinessAndVolumeAndAddsReserve() {
        val r = LoopModel.readiness(sit(DayIn(today, sleepH = 4.5, energy = 2, sleepQ = 2, restHr = 62), train = true))!!
        assertTrue(r.score < 50)
        val a = LoopModel.advice(sit(DayIn(today, sleepH = 4.5, energy = 2, sleepQ = 2, restHr = 62), train = true), r)
        assertTrue(a.volumeFactor < 0.95); assertTrue(a.rirDelta >= 1)
        assertTrue(a.headline.contains("objem")); assertTrue(a.headline.contains("noc 4,5 h"))
    }

    @Test fun volumeFactorIsMonotonicAndBounded() {
        val f = (0..100 step 5).map { LoopModel.volumeFactor(it) }
        assertTrue(f.zipWithNext().all { it.first <= it.second })
        assertEquals(0.8, f.first(), 0.001); assertEquals(1.05, f.last(), 0.001)
    }

    @Test fun missingPartsAreRenormalisedAndTooLittleGivesNull() {
        // jen spánek (35 %) = pod 35 % pokrytí? přesně na hraně, bez rituálu a historie
        assertNull(LoopModel.readiness(Situation(today, listOf(DayIn(today)), false)))
        val onlySleep = LoopModel.readiness(Situation(today, listOf(DayIn(today, sleepH = 8.0, energy = 5)), false))!!
        assertTrue(onlySleep.coverage < 0.7); assertTrue(onlySleep.score > 80)
    }

    @Test fun heavyYesterdayLowersLoadPart() {
        val days = history().map { if (it.day == today - 1) it.copy(volume = 16000.0) else it } + DayIn(today, sleepH = 7.5, energy = 4, sleepQ = 4)
        val r = LoopModel.readiness(Situation(today, days, true))!!
        assertTrue(r.parts.first { it.name == "zátěž" }.goodness < 60)
    }

    @Test fun calorieAdjustmentIsCappedAndOnlyForDeficitAfterShortNight() {
        val s = sit(DayIn(today, sleepH = 5.0, energy = 3, sleepQ = 3), train = true, cutting = true)
        val a = LoopModel.advice(s.copy(weeklyLossPct = 1.4), LoopModel.readiness(s)!!)
        assertTrue(a.kcalPct <= LoopModel.MAX_KCAL_PCT + 1e-9); assertTrue(a.kcalPct > 0)
        assertTrue(a.warnings.isNotEmpty())
        val n = sit(DayIn(today, sleepH = 5.0, energy = 3, sleepQ = 3), cutting = false)
        assertEquals(0.0, LoopModel.advice(n, LoopModel.readiness(n)!!).kcalPct, 0.0)
    }

    @Test fun sleepDebtMovesBedtimeEarlier() {
        val days = history(sleep = 6.0) + DayIn(today, sleepH = 6.0, energy = 3, sleepQ = 3)
        val s = Situation(today, days, false, usualBedtimeMin = 23 * 60)
        val a = LoopModel.advice(s, LoopModel.readiness(s)!!)
        assertNotNull(a.bedtimeMin); assertTrue(a.bedtimeMin!! < 23 * 60)
    }

    @Test fun learnsThatShortSleepMeansLowEnergy() {
        val days = (0 until 14).map { DayIn(it, sleepH = if (it % 2 == 0) 6.0 else 8.0, energy = if (it % 2 == 0) 2 else 4) }
        val l = LoopModel.links(days).single { it.id == "sleep_energy" }
        assertTrue(l.sentence.contains("energii") && l.sentence.contains("vyšší"))
        assertTrue(l.effect > 1.0 && l.effect < 2.0) // stažené k nule oproti skutečným 2 bodům
        assertEquals(2, l.dots)
    }

    @Test fun noRelationMeansNoSentence() {
        val days = (0 until 14).map { DayIn(it, sleepH = if (it % 2 == 0) 6.0 else 8.0, energy = 3) }
        assertTrue(LoopModel.links(days).none { it.id == "sleep_energy" })
        assertTrue(LoopModel.links(days.take(4)).isEmpty())
    }

    @Test fun trainingToSleepUsesOnlyTrainingDays() {
        val days = (0 until 24).map { DayIn(it, sleepH = when { it % 2 == 1 && (it - 1) % 4 == 0 -> 6.0; it % 2 == 1 -> 7.5; else -> 8.0 }, volume = if (it % 2 == 0) (if (it % 4 == 0) 12000.0 else 6000.0) else 0.0) }
        val l = LoopModel.links(days).firstOrNull { it.id == "training_sleep" }
        assertNotNull(l); assertTrue(l!!.sentence.contains("min"))
    }
}
