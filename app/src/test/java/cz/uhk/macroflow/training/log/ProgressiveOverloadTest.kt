package cz.uhk.macroflow.training.log

import cz.uhk.macroflow.training.exercises.ExerciseLibrary
import cz.uhk.macroflow.training.log.ProgressiveOverload.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgressiveOverloadTest {

    private val bench = ExerciseLibrary.byId("bench_press")!!      // osa, +2,5 kg
    private val pullUp = ExerciseLibrary.byId("pull_up")!!

    private fun set(day: Int, w: Double, r: Int, kind: SetKind = SetKind.WORK, rir: Int = 2, ex: String = "bench_press") =
        LoggedSet(day = day, exerciseId = ex, weightKg = w, reps = r, kind = kind, rir = rir)

    private fun decide(vararg s: LoggedSet, p: ProgressiveOverload.Params = ProgressiveOverload.DEFAULT) =
        ProgressiveOverload.decide(bench, s.toList(), 100, p)

    @Test fun `bez historie nic`() = assertNull(decide())

    @Test fun `na 10 opakovani pridat vahu podle Epleyho`() {
        val d = decide(set(90, 100.0, 10), set(90, 100.0, 10))!!
        assertEquals(Kind.ADD_WEIGHT, d.kind)
        assertEquals(107.5, d.weightKg, 1e-9)                       // 100·(1+10/30)/(1+7/30) = 108,1 → kotouče
        assertEquals(6, d.reps)
    }

    @Test fun `skok vahy nejvys o 10 procent`() {
        assertEquals(110.0, decide(set(90, 100.0, 14), set(90, 100.0, 13))!!.weightKg, 1e-9)
    }

    @Test fun `lehka vaha pridava aspon jeden kotouc`() {
        assertEquals(12.5, decide(set(90, 10.0, 10), set(90, 10.0, 10))!!.weightKg, 1e-9)
    }

    @Test fun `v rozsahu stejna vaha a o opakovani vic`() {
        val d = decide(set(90, 100.0, 8), set(90, 100.0, 7))!!
        assertEquals(Kind.PUSH_REPS, d.kind)
        assertEquals(100.0, d.weightKg, 1e-9)
        assertEquals(9, d.reps)                                       // skóre 7,5 → 8 + 1
    }

    @Test fun `rozehrivaci a dojizdeci serie se nepocitaji`() {
        val d = decide(set(90, 60.0, 12, SetKind.WARMUP), set(90, 100.0, 7), set(90, 100.0, 7), set(90, 80.0, 15, SetKind.BACKOFF))!!
        assertEquals(Kind.PUSH_REPS, d.kind)
        assertEquals(100.0, d.weightKg, 1e-9)
    }

    @Test fun `rezerva RIR posune skore`() {
        // 9 opakování, ale 4 v rezervě → skóre 10 → přidat
        assertEquals(Kind.ADD_WEIGHT, decide(set(90, 100.0, 9, rir = 4), set(90, 100.0, 9, rir = 4))!!.kind)
    }

    @Test fun `pod 6 drzet, opakovane odlehcit`() {
        assertEquals(Kind.HOLD, decide(set(90, 100.0, 5), set(90, 100.0, 4))!!.kind)
        val d = decide(set(85, 100.0, 5), set(90, 100.0, 5), set(90, 100.0, 4))!!
        assertEquals(Kind.DELOAD, d.kind)
        assertEquals(90.0, d.weightKg, 1e-9)
    }

    @Test fun `parametry jdou ladit`() {
        val strict = ProgressiveOverload.DEFAULT.copy(minWeight = 1.0)    // musí všechny série
        assertEquals(Kind.PUSH_REPS, decide(set(90, 100.0, 11), set(90, 100.0, 8), p = strict)!!.kind)
        assertEquals(Kind.ADD_WEIGHT, decide(set(90, 100.0, 12), set(90, 100.0, 8))!!.kind)   // výchozí: průměr 10
    }

    @Test fun `vlastni vaha pres opakovani`() {
        val d = ProgressiveOverload.decide(pullUp, listOf(set(90, 0.0, 8, ex = "pull_up")), 100)!!
        assertEquals(Kind.PUSH_REPS, d.kind); assertEquals(9, d.reps)
        assertEquals(Kind.HARDER_VARIANT, ProgressiveOverload.decide(pullUp, listOf(set(90, 0.0, 10, ex = "pull_up")), 100)!!.kind)
    }

    @Test fun `rozehrivaci a dojizdeci vaha`() {
        assertEquals(60.0, ProgressiveOverload.warmupLoad(100.0, 2.5), 1e-9)
        assertEquals(80.0, ProgressiveOverload.backoffLoad(100.0, 2.5), 1e-9)
        assertEquals(47.5, ProgressiveOverload.warmupLoad(80.0, 2.5), 1e-9)
    }
}
