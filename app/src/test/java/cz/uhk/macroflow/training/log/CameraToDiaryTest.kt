package cz.uhk.macroflow.training.log

import cz.uhk.macroflow.training.analysis.Lift
import cz.uhk.macroflow.training.analysis.RepMetrics
import cz.uhk.macroflow.training.analysis.SetSummary
import cz.uhk.macroflow.training.exercises.ExerciseLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraToDiaryTest {

    private fun rep(i: Int) = RepMetrics(i, 0.0, 40.0, 0.0, 40.0, 1500, 900, 0, 2400, 0.5, 0.8, 1.0, 1.0)

    private fun summary(lift: Lift = Lift.SQUAT, reps: Int = 5, ecc: Long = 1500, lastMcv: Double = 0.45) = SetSummary(
        lift = lift, reps = (1..reps).map { rep(it) }, cmPerPx = 0.1,
        avgRomCm = 40.0, weightedRomCm = 40.0, romCvPct = 3.0,
        avgEccentricMs = ecc, avgConcentricMs = 900, avgTotalMs = 2400,
        bestMcv = 0.6, lastMcv = lastMcv, velocityLossPct = 25.0, avgDeviationCm = 2.0, quality = 1.0
    )

    @Test
    fun everyLiftMapsToLibraryExercise() {
        Lift.entries.forEach { l ->
            val id = CameraToDiary.exerciseId(l)
            assertTrue(id, ExerciseLibrary.byId(id) != null)
        }
    }

    @Test
    fun draftCopiesLoadAndReps() {
        val d = CameraToDiary.draft(summary(reps = 6), 100.0)!!
        assertEquals("back_squat", d.exerciseId)
        assertEquals(100.0, d.weightKg, 0.0)
        assertEquals(6, d.reps)
        assertFalse(d.slowEccentric)
        assertEquals(StrengthModel.DEFAULT_RIR, d.rir)
    }

    @Test
    fun noLoadOrNoRepsNoDraft() {
        assertNull(CameraToDiary.draft(summary(), null))
        assertNull(CameraToDiary.draft(summary(), 0.0))
        assertNull(CameraToDiary.draft(summary(reps = 0), 100.0))
    }

    @Test
    fun slowEccentricFromMeasuredTempo() {
        assertTrue(CameraToDiary.draft(summary(ecc = 3100), 80.0)!!.slowEccentric)
        assertTrue(CameraToDiary.draft(summary(ecc = 2500), 80.0)!!.slowEccentric)
        assertFalse(CameraToDiary.draft(summary(ecc = 2400), 80.0)!!.slowEccentric)
    }

    @Test
    fun lastRepNearMvtMeansFailure() {
        // dřep MVT 0,30 m/s
        assertEquals(0, CameraToDiary.draft(summary(lastMcv = 0.33), 140.0)!!.rir)
        assertEquals(0, CameraToDiary.draft(summary(lastMcv = 0.35), 140.0)!!.rir)
        assertEquals(2, CameraToDiary.draft(summary(lastMcv = 0.40), 140.0)!!.rir)
        // bench MVT 0,17
        assertEquals(0, CameraToDiary.draft(summary(lift = Lift.BENCH, lastMcv = 0.20), 100.0)!!.rir)
        assertEquals("bench_press", CameraToDiary.draft(summary(lift = Lift.BENCH), 100.0)!!.exerciseId)
    }
}
