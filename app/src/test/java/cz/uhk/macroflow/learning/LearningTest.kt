package cz.uhk.macroflow.learning

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class LearningTest {
    private val energy = EnergyIn(true, 0.93, 2650.0, 2465.0, 0.4, 12, 20)

    @Test fun energyLowerThanEquationIsReported() {
        val r = LearningModel.build(Inputs(14, energy = energy))
        val e = r.items.single()
        assertTrue(e.headline.contains("7 %") && e.headline.contains("nižší"))
        assertEquals(2, e.dots); assertTrue(e.differs)
        assertEquals("2650 kcal", e.from); assertEquals("2465 kcal", e.to)
    }

    @Test fun equationThatFitsDoesNotCountAsDifference() {
        val r = LearningModel.build(Inputs(14, energy = energy.copy(factor = 1.01)))
        assertFalse(r.items.single().differs); assertEquals(0, r.differing)
    }

    @Test fun notEnoughDataSaysWhatIsMissing() {
        val e = LearningModel.build(Inputs(5, energy = EnergyIn(false, 1.0, 2600.0, 2600.0, 0.0, 4, 6))).items.single()
        assertTrue(e.detail.contains("3 vážení") && e.detail.contains("4 zapsaných")); assertEquals(0, e.dots)
    }

    @Test fun onlyTwoBiggestLiftChangesWithEnoughSessions() {
        val lifts = listOf(LiftIn("Bench", 80.0, 92.0, 3.0, 8), LiftIn("Dřep", 100.0, 101.0, 4.0, 8),
            LiftIn("Mrtvý", 120.0, 135.0, 5.0, 9), LiftIn("OHP", 40.0, 60.0, 2.0, 2))
        val names = LearningModel.build(Inputs(20, lifts = lifts)).items.map { it.headline.substringBefore(':') }
        assertEquals(listOf("Bench", "Mrtvý"), names)
    }

    @Test fun lockedUntilSevenDays() {
        assertFalse(LearningModel.build(Inputs(6)).unlocked)
        assertTrue(LearningModel.build(Inputs(7)).unlocked)
        assertEquals("3 dny", LearningModel.days(3)); assertEquals("1 den", LearningModel.days(1)); assertEquals("6 dní", LearningModel.days(6))
    }

    private val wed = LocalDate.parse("2026-11-04")
    private val sun = LocalDate.parse("2026-11-08")

    @Test fun noNoticeBeforeSevenDays() = assertNull(LearningModel.notice(6, 0.9, TriggerState(), wed))

    @Test fun unlockNoticeThenEnergyShiftThenSunday() {
        assertEquals("unlock", LearningModel.notice(8, 1.0, TriggerState(), wed)!!.key)
        val seen = TriggerState(unlockedSeen = true)
        assertNull(LearningModel.notice(8, 1.02, seen, wed))
        assertEquals("energy:-7", LearningModel.notice(8, 0.93, seen, wed)!!.key)
        assertEquals("energy:-7", LearningModel.notice(8, 0.93, seen.copy(lastFactor = 1.0), sun)!!.key)
        assertNull(LearningModel.notice(8, 0.93, seen.copy(lastFactor = 0.93), wed))
        assertTrue(LearningModel.notice(8, 0.93, seen.copy(lastFactor = 0.93), sun)!!.key.startsWith("week:"))
        assertNull(LearningModel.notice(8, 0.93, seen.copy(lastFactor = 0.93, weekKey = LearningModel.weekKey(sun)), sun))
    }

    @Test fun dismissedNoticeDoesNotRepeat() {
        assertNull(LearningModel.notice(8, 1.0, TriggerState(dismissed = "unlock"), wed))
    }
}
