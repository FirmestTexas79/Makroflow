package cz.uhk.macroflow.energy

import cz.uhk.macroflow.energy.ActualTraining.Evidence
import cz.uhk.macroflow.energy.ActualTraining.Group
import cz.uhk.macroflow.energy.ActualTraining.WatchActivity
import org.junit.Assert.assertEquals
import org.junit.Test

class ActualTrainingTest {
    private val legs = Exercise.Strength(StrengthKind.LEGS, 60.0)
    private val run = Exercise.Run(8.0, 30.0)
    private val plan = listOf(legs, run)

    @Test fun `bez dukazu zustava plan`() {
        assertEquals(plan, ActualTraining.resolve(plan, Evidence(), dayOver = false))
        assertEquals(plan, ActualTraining.resolve(plan, Evidence(), dayOver = true))   // nic nezapisuje → nevíme
    }

    @Test fun `hodinky prebiji plan`() {
        val ev = Evidence(watchSynced = true, watch = listOf(WatchActivity(Group.STRENGTH, 75.0, 500), WatchActivity(Group.CARDIO, 20.0, 260),
            WatchActivity(Group.WALK, 40.0, 150)))
        assertEquals(listOf(Exercise.Strength(StrengthKind.LEGS, 75.0), Exercise.Measured(260.0, 20.0)),
            ActualTraining.resolve(plan, ev, dayOver = false))
    }

    @Test fun `vynechany trening po skonceni dne vypadne`() {
        assertEquals(emptyList<Exercise>(), ActualTraining.resolve(plan, Evidence(watchSynced = true), dayOver = true))
        assertEquals(plan, ActualTraining.resolve(plan, Evidence(watchSynced = true), dayOver = false))       // dnešek ještě běží
        assertEquals(listOf(run), ActualTraining.resolve(plan, Evidence(logsSets = true), dayOver = true))     // deník jen o síle
    }

    @Test fun `denik a rychly zapis`() {
        assertEquals(listOf(Exercise.Strength(StrengthKind.LEGS, 52.0), run),
            ActualTraining.resolve(plan, Evidence(loggedStrengthMinutes = 52.0), dayOver = true))
        val quick = Exercise.Strength(StrengthKind.PULL, 40.0)
        assertEquals(listOf(quick), ActualTraining.resolve(emptyList(), Evidence(quickStrength = quick), dayOver = false))
        val rope = Exercise.Met(11.8, 15.0)
        assertEquals(listOf(legs, rope), ActualTraining.resolve(plan, Evidence(quickCardio = rope), dayOver = false))
    }

    @Test fun `delka ze serii a cisty vydej z hodinek`() {
        assertEquals(null, ActualTraining.minutesFromSets(listOf(0L, 60_000L)))
        assertEquals(43.0, ActualTraining.minutesFromSets(listOf(0L, 20 * 60_000L, 40 * 60_000L))!!, 0.01)
        assertEquals(15.0, ActualTraining.minutesFromSets(listOf(0L, 1L, 2L))!!, 0.01)
        assertEquals(230.0, EnergyModel.exerciseNetKcal(Exercise.Measured(260.0, 30.0), 60.0), 0.01)
        assertEquals(0.0, EnergyModel.exerciseNetKcal(Exercise.Measured(10.0, 60.0), 80.0), 0.01)
    }
}
