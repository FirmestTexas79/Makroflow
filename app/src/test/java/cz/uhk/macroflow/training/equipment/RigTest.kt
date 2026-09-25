package cz.uhk.macroflow.training.equipment

import cz.uhk.macroflow.training.exercises.ExerciseLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RigTest {

    private fun ex(id: String) = ExerciseLibrary.byId(id)!!

    @Test
    fun platesGreedyLikeInGym() {
        // Samuelovy příklady: 5 kg na stranu = jeden malý kotouč, 25 kg = 20 + 5
        assertEquals(listOf(5.0), RigMath.plates(5.0).perSide)
        assertEquals(listOf(20.0, 5.0), RigMath.plates(25.0).perSide)
        assertEquals(listOf(20.0, 20.0, 10.0, 2.5, 1.25), RigMath.plates(53.75).perSide)
        assertEquals(0.0, RigMath.plates(53.75).remainderKg, 1e-9)
        assertEquals(listOf<Double>(), RigMath.plates(0.0).perSide)
    }

    @Test
    fun olympicBarSubtractsBar() {
        assertEquals(40.0, RigMath.perSideKg(Rig.OLYMPIC_BAR, 100.0), 1e-9)
        assertEquals(listOf(20.0, 20.0), RigMath.plates(Rig.OLYMPIC_BAR, 100.0).perSide)
        assertEquals(listOf(20.0, 10.0, 1.25), RigMath.plates(Rig.OLYMPIC_BAR, 82.5).perSide)
        assertEquals(0.0, RigMath.perSideKg(Rig.OLYMPIC_BAR, 15.0), 1e-9)
        // 21 kg nejde poskládat – na stranu chybí 0,5
        assertEquals(0.5, RigMath.plates(Rig.OLYMPIC_BAR, 21.0).remainderKg, 1e-9)
    }

    @Test
    fun machineWeightIsPerSide() {
        assertEquals(listOf(20.0, 5.0), RigMath.plates(Rig.MACHINE_BOTH, 25.0).perSide)
        assertEquals(listOf(20.0, 5.0), RigMath.plates(Rig.MACHINE_ONE, 25.0).perSide)
        assertTrue(RigMath.plates(Rig.STACK, 25.0).perSide.isEmpty())
    }

    @Test
    fun sizeGrowsWithWeight() {
        val a = RigMath.size(Rig.DUMBBELL_PAIR, 4.0)
        val b = RigMath.size(Rig.DUMBBELL_PAIR, 20.0)
        val c = RigMath.size(Rig.DUMBBELL_PAIR, 40.0)
        assertTrue(a < b && b < c)
        assertEquals(0.0, RigMath.size(Rig.DUMBBELL_SINGLE, 0.5), 1e-9)
        assertEquals(1.0, RigMath.size(Rig.EZ_BAR, 100.0), 1e-9)
        assertEquals(0.0, RigMath.size(Rig.OLYMPIC_BAR, 100.0), 1e-9)
    }

    @Test
    fun stackBlocks() {
        assertEquals(RigMath.Stack(9, 0.0), RigMath.stack(45.0))
        assertEquals(RigMath.Stack(4, 2.5), RigMath.stack(22.5))
        assertEquals(RigMath.Stack(0, 0.0), RigMath.stack(0.0))
    }

    @Test
    fun defaultsAndOptionsPerExercise() {
        assertEquals(Rig.OLYMPIC_BAR, Rig.default(ex("bench_press")))
        assertEquals(Rig.EZ_BAR, Rig.default(ex("ez_bar_curl")))
        assertEquals(Rig.EZ_BAR, Rig.default(ex("skull_crusher")))
        assertEquals(Rig.STRAIGHT_BAR, Rig.default(ex("barbell_curl")))
        assertEquals(Rig.DUMBBELL_PAIR, Rig.default(ex("lateral_raise")))
        assertEquals(Rig.DUMBBELL_SINGLE, Rig.default(ex("single_arm_supported_curl")))
        assertEquals(Rig.MACHINE_BOTH, Rig.default(ex("machine_chest_press")))
        assertEquals(Rig.STACK, Rig.default(ex("pec_deck")))
        assertEquals(Rig.STACK, Rig.default(ex("lat_pulldown")))
        assertNull(Rig.default(ex("pull_up")))
        assertTrue(Rig.options(ex("pull_up")).isEmpty())
        // uložená volba platí jen, když k cviku patří
        assertEquals(Rig.EZ_BAR, Rig.resolve(ex("barbell_curl"), "EZ_BAR"))
        assertEquals(Rig.STRAIGHT_BAR, Rig.resolve(ex("barbell_curl"), "DUMBBELL_PAIR"))
        // každý cvik v knihovně má buď obrázek, nebo je bez náčiní
        ExerciseLibrary.ALL.forEach { e ->
            val d = Rig.default(e)
            assertTrue(e.id, d == null || d in Rig.options(e))
        }
    }

    @Test
    fun summaries() {
        assertEquals("2 × 22,5 kg = 45 kg", RigMath.summary(Rig.DUMBBELL_PAIR, 22.5))
        assertEquals("Na stranu 20 + 20 kg · osa 20 kg", RigMath.summary(Rig.OLYMPIC_BAR, 100.0))
        assertEquals("Jen osa 20 kg (zapsáno 15 kg)", RigMath.summary(Rig.OLYMPIC_BAR, 15.0))
        assertEquals("Na stranu 20 + 5 kg · celkem 50 kg", RigMath.summary(Rig.MACHINE_BOTH, 25.0))
        assertEquals("Na stranu 20 + 5 kg · jen jedna strana", RigMath.summary(Rig.MACHINE_ONE, 25.0))
        assertEquals("4 × 5 kg + 2,5 kg = 22,5 kg", RigMath.summary(Rig.STACK, 22.5))
        assertEquals("EZ činka 30 kg", RigMath.summary(Rig.EZ_BAR, 30.0))
        assertEquals("Na stranu 1,25 kg (chybí 0,5 kg na stranu) · osa 20 kg", RigMath.summary(Rig.OLYMPIC_BAR, 23.5))
    }
}
