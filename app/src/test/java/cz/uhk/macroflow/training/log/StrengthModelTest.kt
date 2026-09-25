package cz.uhk.macroflow.training.log

import cz.uhk.macroflow.training.exercises.ExerciseLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class StrengthModelTest {

    private fun set(day: Int, w: Double, r: Int, slow: Boolean = false, ex: String = "machine_chest_press", template: String? = null) =
        LoggedSet(day = day, exerciseId = ex, weightKg = w, reps = r, slowEccentric = slow, template = template)

    @Test
    fun slowTempoCountsAsMoreReps() {
        val normal = StrengthModel.setEstimate(set(1, 60.0, 8))!!
        val slow = StrengthModel.setEstimate(set(1, 60.0, 8, slow = true))!!
        assertTrue(slow > normal)
        assertEquals(60.0 * (1 + (8 * StrengthModel.TEMPO_FACTOR + StrengthModel.DEFAULT_RIR) / 30.0), slow, 1e-9)
        assertEquals(60.0 * (1 + (8 + StrengthModel.DEFAULT_RIR) / 30.0), normal, 1e-9)
    }

    @Test
    fun loggedRirChangesEstimate() {
        val toFailure = StrengthModel.setEstimate(LoggedSet(day = 1, exerciseId = "x", weightKg = 60.0, reps = 8, rir = 0))!!
        val default = StrengthModel.setEstimate(LoggedSet(day = 1, exerciseId = "x", weightKg = 60.0, reps = 8))!!
        assertEquals(60.0 * (1 + 8 / 30.0), toFailure, 1e-9)
        assertEquals(60.0 * (1 + 10 / 30.0), default, 1e-9)            // výchozí RIR 2
        assertEquals(StrengthModel.effectiveReps(8, false, 5), StrengthModel.effectiveReps(8, false, 9), 1e-9)   // omezeno na 5
    }

    @Test
    fun unusableSetsAreIgnored() {
        assertNull(StrengthModel.setEstimate(set(1, 0.0, 10)))      // vlastní váha
        assertNull(StrengthModel.setEstimate(set(1, 20.0, 25)))     // přes 20 opakování
        assertNull(StrengthModel.setEstimate(set(1, 60.0, 0)))
        assertEquals(100.0 * (1 + (1 + StrengthModel.DEFAULT_RIR) / 30.0), StrengthModel.setEstimate(set(1, 100.0, 1))!!, 1e-9)
    }

    @Test
    fun onlyBestSetPerDayIsObservation() {
        val obs = StrengthModel.observations(listOf(set(1, 60.0, 10), set(1, 60.0, 8), set(1, 40.0, 12), set(3, 62.5, 8)))
        assertEquals(2, obs.size)
        assertEquals(60.0 * (1 + 12 / 30.0), obs[0].e1rm, 1e-9)       // 60 × 10 (+2 rezerva)
        assertTrue(obs[0].sd > StrengthModel.obsSd(10.0))
    }

    @Test
    fun steadyProgressIsTrackedAndExtrapolated() {
        // 1RM roste z 80 o 0,5 kg každý trénink (co 3 dny) – série odpovídají přesně Epleymu
        val sets = (0 until 12).map { i ->
            val oneRm = 80.0 + 0.5 * i
            set(i * 3, oneRm / (1 + (8 + StrengthModel.DEFAULT_RIR) / 30.0), 8)
        }
        val e = StrengthModel.estimate(sets, 33)!!
        assertEquals(12, e.sessions)
        assertEquals(80.0 + 0.5 * 11, e.e1rm, 0.8)
        assertTrue("růst ${e.weeklyChangePct}", e.weeklyChangePct > 0.5)
        val next = StrengthModel.estimate(sets, 36)!!
        assertTrue(next.e1rm > e.e1rm)
        assertTrue(next.upper - next.lower > e.upper - e.lower)
    }

    @Test
    fun singleSessionHasWideInterval() {
        val e = StrengthModel.estimate(listOf(set(0, 60.0, 8)), 0)!!
        assertEquals(60.0 * (1 + 10 / 30.0), e.e1rm, 0.01)
        assertTrue(e.lower < 77 && e.upper > 83)
        assertNull(StrengthModel.estimate(emptyList(), 0))
        assertNull(StrengthModel.estimate(listOf(set(5, 60.0, 8)), 4))   // jen budoucí zápis
    }

    @Test
    fun readyLoadRoundsDownWithReserve() {
        // 1RM 100, 8 opakování + 2 rezerva → 100 / (1 + 10/30) = 75 → přesně 75
        assertEquals(75.0, StrengthModel.readyLoad(100.0, 8, 2.5), 1e-9)
        assertEquals(72.5, StrengthModel.readyLoad(99.0, 8, 2.5), 1e-9)
        assertEquals(74.25, StrengthModel.readyLoad(99.0, 8, 0.0), 1e-9)
    }

    @Test
    fun typicalInterval() {
        assertEquals(7, StrengthModel.typicalInterval(listOf(set(1, 50.0, 8))))
        assertEquals(3, StrengthModel.typicalInterval(listOf(set(0, 50.0, 8), set(3, 50.0, 8), set(6, 50.0, 8), set(10, 50.0, 8))))
    }

    // ── šablony ─────────────────────────────────────────────────────────────

    @Test
    fun defaultsAreValidExercises() {
        assertEquals(6, WorkoutTemplates.DEFAULTS.size)
        WorkoutTemplates.DEFAULTS.values.flatten().forEach { assertTrue(it, ExerciseLibrary.byId(it) != null) }
        assertEquals(listOf("machine_chest_press", "incline_machine_press", "skull_crusher", "lateral_raise", "triceps_kickback", "pec_deck"),
            WorkoutTemplates.DEFAULTS["PUSH_A"])
        assertEquals(listOf("lat_pulldown", "close_grip_pulldown", "wide_cable_row", "seated_cable_row",
            "single_arm_supported_curl", "hammer_curl", "ez_bar_curl", "reverse_pec_deck"), WorkoutTemplates.DEFAULTS["PULL_A"])
        assertEquals(listOf("seated_leg_curl", "rdl", "single_leg_press", "standing_calf_raise", "leg_extension"),
            WorkoutTemplates.DEFAULTS["LEGS_A"])
        for (k in listOf("PUSH", "PULL", "LEGS")) assertEquals(WorkoutTemplates.DEFAULTS["${k}_A"], WorkoutTemplates.DEFAULTS["${k}_B"])
    }

    @Test
    fun variantsAlternate() {
        val k = WorkoutTemplates.Kind.PUSH
        assertEquals('A', WorkoutTemplates.variantFor(k, emptyList(), 10))
        val afterA = listOf(set(5, 60.0, 8, template = "PUSH_A"), set(7, 50.0, 8, template = "PULL_A"))
        assertEquals('B', WorkoutTemplates.variantFor(k, afterA, 10))
        val afterB = afterA + set(8, 60.0, 8, template = "PUSH_B")
        assertEquals('A', WorkoutTemplates.variantFor(k, afterB, 10))
        // Dnes už se cvičilo B → zůstává B
        val today = afterA + set(10, 60.0, 8, template = "PUSH_B")
        assertEquals('B', WorkoutTemplates.variantFor(k, today, 10))
    }

    @Test
    fun keysRoundTrip() {
        assertEquals("PULL_B", WorkoutTemplates.key(WorkoutTemplates.Kind.PULL, 'B'))
        assertEquals(WorkoutTemplates.Kind.LEGS to 'A', WorkoutTemplates.parse("LEGS_A"))
        assertNull(WorkoutTemplates.parse("LEGS_C"))
        assertEquals("PUSH A", WorkoutTemplates.label("PUSH_A"))
        assertEquals(WorkoutTemplates.Kind.PULL, WorkoutTemplates.Kind.fromPlanType("Pull"))
        assertTrue(abs(1.0) > 0)
    }
}
