package cz.uhk.macroflow.wearable

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class SleepInsightsTest {
    private val end = LocalDate.parse("2026-11-04")

    /** noc N dní před koncem; bedtime v minutách od půlnoci (např. 1410 = 23:30) */
    private fun night(back: Int, sleepH: Double, bed: Int = 1410, score: Int? = null, stress: Int? = null, rem: Double = 0.22) =
        WearableDayEntity(
            date = end.minusDays(back.toLong()).toString(), sleepSec = (sleepH * 3600).toInt(),
            remSec = (sleepH * 3600 * rem).toInt(), deepSec = (sleepH * 3600 * 0.18).toInt(),
            sleepStartMs = (bed % 1440) * 60_000L, sleepScore = score, stressAvg = stress
        )

    @Test fun steadyGoodSleeperGetsPraiseOnly() {
        val days = (0..13).map { night(it, 8.0) }
        val s = SleepInsights.summarize(days, end.toString())
        assertEquals(0, s.debt7Sec); assertEquals(8 * 3600, s.avg7Sec)
        assertEquals(0, s.bedtimeSdMin)
        assertEquals(1, SleepInsights.recommendations(s).size)
        assertTrue(SleepInsights.recommendations(s)[0].contains("v pořádku"))
    }

    @Test fun debtAndIrregularBedtimeAreFlagged() {
        val days = (0..13).map { night(it, 6.0, bed = if (it % 2 == 0) 1320 else 1500) }   // 22:00 / 01:00
        val s = SleepInsights.summarize(days, end.toString())
        assertEquals(14 * 3600, s.debt7Sec)
        assertTrue(s.bedtimeSdMin!! >= 60)
        val r = SleepInsights.recommendations(s)
        assertTrue(r[0].contains("chybí")); assertTrue(r.any { it.contains("nepravidelně") })
    }

    @Test fun bedtimeAfterMidnightIsAdjacentToBeforeMidnight() {
        val a = SleepInsights.bedtimeMin(night(0, 8.0, bed = 1410))!!   // 23:30
        val b = SleepInsights.bedtimeMin(night(0, 8.0, bed = 1470))!!   // 00:30
        assertEquals(60, b - a)
    }

    @Test fun trendNeedsTwoWeeksAndDetectsDrop() {
        val days = (0..6).map { night(it, 6.5) } + (7..13).map { night(it, 8.0) }
        assertEquals(-90 * 60, SleepInsights.summarize(days, end.toString()).trendSec)
        assertNull(SleepInsights.summarize((0..5).map { night(it, 7.0) }, end.toString()).trendSec)
    }

    @Test fun tooFewNightsAsksForMoreData() {
        val s = SleepInsights.summarize(listOf(night(0, 7.0), night(1, 7.0)), end.toString())
        assertTrue(SleepInsights.recommendations(s)[0].contains("aspoň"))
    }

    @Test fun summaryIsAsOfSelectedDate() {
        val days = (0..13).map { night(it, if (it < 7) 5.0 else 8.0) }
        val past = SleepInsights.summarize(days, end.minusDays(7).toString())
        assertEquals(8 * 3600, past.avg7Sec)
    }

    @Test fun lowRemIsReported() {
        val days = (0..9).map { night(it, 8.0, rem = 0.10) }
        assertTrue(SleepInsights.recommendations(SleepInsights.summarize(days, end.toString())).any { it.contains("REM") })
    }

    @Test fun stressEffectOnlyWhenClear() {
        // spánek je horší po dnech s vysokým stresem předchozího dne
        fun stress(b: Int) = if (b % 2 == 0) 60 else 20
        val nights = (0..16).map { b -> night(b, 7.0, score = if (stress(b + 1) == 60) 50 else 80, stress = stress(b)) }
        val e = SleepInsights.stressEffect(nights, end.toString())
        assertNotNull(e); assertTrue(e!!.first < e.second)
        assertNull(SleepInsights.stressEffect(nights.take(4), end.toString()))
    }
}
