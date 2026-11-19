package cz.uhk.macroflow.pokemon.story

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CalibrationTest {
    private val start = LocalDate.of(2026, 11, 1)
    private fun days(vararg offsets: Int) = offsets.map { start.plusDays(it.toLong()) }.toSet()

    @Test fun sevenDaysInARowCalibrate() {
        assertEquals(Calibration.Streak(7, 0), Calibration.streak(days(0, 1, 2, 3, 4, 5, 6), start, start.plusDays(6)))
    }

    @Test fun oneMissedDayIsTolerated() {
        val s = Calibration.streak(days(0, 1, 2, 4, 5, 6, 7), start, start.plusDays(7))
        assertEquals(7, s.days); assertEquals(1, s.missed)
    }

    @Test fun secondMissedDayResets() {
        val s = Calibration.streak(days(0, 1, 3, 4, 6, 7), start, start.plusDays(7))
        assertEquals(Calibration.Streak(2, 0), s)               // druhé vynechání (den 5) vynuluje, pak dny 6 a 7
    }

    @Test fun todayWithoutMealIsNotAMiss() {
        val s = Calibration.streak(days(0, 1, 2), start, start.plusDays(3))
        assertEquals(Calibration.Streak(3, 0), s)
    }

    @Test fun fileNeverShowsWeightAndRedactsByInsight() {
        val st = Calibration.Stats(20, 10, 5, 6500, 2, Calibration.Streak(3, 1), false)
        val low = Calibration.fileText(st, 0)
        assertTrue(low.contains("KALIBRACE PROBÍHÁ (3/7)")); assertTrue(low.contains("███"))
        assertFalse(low.contains("kg")); assertTrue(low.contains("neevidováno"))
        assertTrue(Calibration.fileText(st, 4).contains("kalibrace nedokončena"))
        assertTrue(Calibration.elevatorText(0).contains("hladký")); assertTrue(Calibration.elevatorText(3).contains("tlačítko chybí"))
    }
}
