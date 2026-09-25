package cz.uhk.macroflow.training.log

import cz.uhk.macroflow.training.analysis.Lift
import cz.uhk.macroflow.training.analysis.SetSummary

/**
 * Série naměřená kamerou → zápis do tréninkového deníku (docs/adr/0027), bez Androidu.
 *
 *  - cvik: dřep, bench, tlaky nad hlavu a mrtvý tah mají v knihovně odpovídající cvik s osou,
 *  - opakování = rozpoznaná opakování, váha = zátěž zadaná v trackeru (bez ní se nic nezapíše –
 *    deník bez váhy nedává odhad síly),
 *  - pomalé spouštění: průměrná excentrická fáze ≥ [SLOW_ECCENTRIC_MS] (kamera ji měří),
 *  - RIR: když poslední opakování kleslo skoro na rychlost při 1RM (MVT cviku + [NEAR_FAILURE_MPS]),
 *    série šla prakticky do selhání → RIR 0; jinak výchozí 2 (rychlost sama RIR přesně neurčí).
 */
object CameraToDiary {

    const val SLOW_ECCENTRIC_MS = 2500L
    const val NEAR_FAILURE_MPS = 0.05

    data class Draft(
        val exerciseId: String,
        val weightKg: Double,
        val reps: Int,
        val slowEccentric: Boolean,
        val rir: Int
    )

    fun exerciseId(lift: Lift): String = when (lift) {
        Lift.SQUAT -> "back_squat"
        Lift.BENCH -> "bench_press"
        Lift.OHP -> "overhead_press"
        Lift.DEADLIFT -> "deadlift"
    }

    fun nearFailure(s: SetSummary): Boolean = s.lastMcv > 0.0 && s.lastMcv <= s.lift.mvt + NEAR_FAILURE_MPS

    /** Návrh zápisu, nebo null (bez zátěže nebo bez opakování). */
    fun draft(s: SetSummary, loadKg: Double?): Draft? {
        if (loadKg == null || loadKg <= 0.0 || s.repCount < 1) return null
        return Draft(
            exerciseId = exerciseId(s.lift),
            weightKg = loadKg,
            reps = s.repCount,
            slowEccentric = s.avgEccentricMs >= SLOW_ECCENTRIC_MS,
            rir = if (nearFailure(s)) 0 else StrengthModel.DEFAULT_RIR
        )
    }
}
