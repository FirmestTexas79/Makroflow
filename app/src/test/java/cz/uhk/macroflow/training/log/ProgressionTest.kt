package cz.uhk.macroflow.training.log

import cz.uhk.macroflow.training.body.Muscle
import cz.uhk.macroflow.training.exercises.ExerciseLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressionTest {

    private val bench = ExerciseLibrary.byId("bench_press")!!      // osa
    private val pullUp = ExerciseLibrary.byId("pull_up")!!         // hrazda, 6–15
    private val fly = ExerciseLibrary.byId("cable_fly")!!          // kladka

    private fun set(day: Int, w: Double, r: Int, ex: String = "bench_press", order: Int = 0, id: Long = 0) =
        LoggedSet(id, day, ex, w, r, order)

    @Test
    fun epley() {
        assertEquals(100.0, Progression.e1rm(100.0, 1), 1e-9)
        assertEquals(100.0 * (1 + 10 / 30.0), Progression.e1rm(100.0, 10), 1e-9)
        assertEquals(0.0, Progression.e1rm(80.0, 0), 1e-9)
    }

    @Test
    fun lastSessionSkipsToday() {
        val sets = listOf(set(10, 60.0, 8), set(12, 62.5, 8, order = 0), set(12, 62.5, 7, order = 1), set(14, 65.0, 6))
        val last = Progression.lastSession(sets, "bench_press", beforeDay = 14)!!
        assertEquals(12, last.day)
        assertEquals(2, last.sets.size)
        assertNull(Progression.lastSession(sets, "bench_press", beforeDay = 10))
    }

    @Test
    fun workingSetsIgnoreWarmups() {
        val s = Progression.Session(1, listOf(set(1, 40.0, 10), set(1, 60.0, 8), set(1, 60.0, 7)))
        assertEquals(2, s.workingSets.size)
    }

    @Test
    fun repRangeFollowsOverloadParams() {
        assertEquals(Progression.RepRange(6, 8), Progression.repRange(bench))
        assertEquals(Progression.RepRange(6, 8), Progression.repRange(fly))
        assertEquals(Progression.RepRange(6, 15), Progression.repRange(pullUp))
    }

    @Test
    fun warmupsDoNotCountAsHardSets() {
        val sets = listOf(set(1, 40.0, 10).copy(kind = SetKind.WARMUP), set(1, 60.0, 8), set(1, 50.0, 10).copy(kind = SetKind.BACKOFF))
        val m = Progression.weeklySets(sets) { ExerciseLibrary.byId(it) }
        assertEquals(2.0, m.getValue(bench.primary.first()), 1e-9)
    }

    @Test
    fun personalRecords() {
        val prev = listOf(set(1, 60.0, 8, id = 1), set(2, 62.5, 6, id = 2))
        assertTrue(Progression.isPersonalRecord(set(3, 62.5, 9, id = 3), prev))
        assertFalse(Progression.isPersonalRecord(set(3, 60.0, 6, id = 3), prev))
        assertFalse(Progression.isPersonalRecord(set(1, 60.0, 8, id = 1), emptyList()))   // první zápis
    }

    @Test
    fun weeklySetsCountPrimaryAndSecondary() {
        val sets = List(3) { set(1, 60.0, 8) } + List(2) { set(2, 0.0, 8, "pull_up") }
        val w = Progression.weeklySets(sets, ExerciseLibrary::byId)
        assertEquals(3.0, w.getValue(Muscle.CHEST), 1e-9)
        assertEquals(1.5, w.getValue(Muscle.TRICEPS), 1e-9)       // bench: triceps pomocný
        assertEquals(2.0, w.getValue(Muscle.LATS), 1e-9)
        assertEquals(Progression.Volume.LOW, Progression.volume(w.getValue(Muscle.CHEST)))
        assertEquals(Progression.Volume.OK, Progression.volume(12.0))
        assertEquals(Progression.Volume.HIGH, Progression.volume(24.0))
        assertEquals(Progression.Volume.NONE, Progression.volume(0.0))
    }

    @Test
    fun rounding() {
        assertEquals(62.5, Progression.roundTo(61.3, 2.5), 1e-9)
        assertEquals(22.0, Progression.roundTo(21.1, 2.0), 1e-9)
    }
}
