package cz.uhk.macroflow.training.figure

import cz.uhk.macroflow.training.exercises.ExerciseLibrary
import cz.uhk.macroflow.training.figure.ExerciseFigures.P
import org.junit.Assert.assertEquals
import org.junit.Test

class ExerciseFiguresTest {
    @Test fun `ik drzi delky clanku`() {
        val root = P(0f, 0f)
        val elbow = ExerciseFigures.ik(root, P(10f, 10f), 16f, 14f, -1)
        assertEquals(16f, (elbow - root).len(), 0.01f)
        assertEquals(14f, (P(10f, 10f) - elbow).len(), 0.01f)
        // nedosažitelný cíl → natažená paže směrem k cíli
        val far = ExerciseFigures.ik(root, P(100f, 0f), 16f, 14f, 1)
        assertEquals(16f, far.x, 0.1f)
    }

    @Test fun `kazdy cvik ma ilustraci a pozy drzi delky clanku`() {
        assertEquals(ExerciseLibrary.ALL.map { it.id }.toSet(), ExerciseFigures.BY_ID.keys)
        ExerciseFigures.BY_ID.forEach { (id, ill) ->
            listOfNotNull(ill.start, ill.end).forEach { pose ->
                val j = ExerciseFigures.solve(pose)
                val arm = pose.armScale
                assertEquals(id, ExerciseFigures.UPPER_ARM * arm, (j.elbow - j.shoulderR).len(), 0.01f)
                assertEquals(id, ExerciseFigures.FOREARM * arm, (j.wrist - j.elbow).len(), 0.01f)
                assertEquals(id, ExerciseFigures.SHIN, (j.ankle - j.knee).len(), 0.01f)
            }
        }
    }
}
