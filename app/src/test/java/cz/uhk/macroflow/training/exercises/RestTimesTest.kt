package cz.uhk.macroflow.training.exercises

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RestTimesTest {
    @Test fun everyExerciseHasExplicitRest() {
        val missing = ExerciseLibrary.ALL.map { it.id }.filterNot { RestTimes.known(it) }
        assertTrue("Chybí pauza: $missing", missing.isEmpty())
    }

    @Test fun heavyCompoundRestsLongerThanIsolation() {
        assertTrue(RestTimes.seconds("back_squat") > RestTimes.seconds("bench_press"))
        assertTrue(RestTimes.seconds("bench_press") > RestTimes.seconds("lat_pulldown"))
        assertTrue(RestTimes.seconds("lat_pulldown") > RestTimes.seconds("leg_extension"))
        assertTrue(RestTimes.seconds("leg_extension") > RestTimes.seconds("lateral_raise"))
        assertEquals(RestTimes.MEDIUM, RestTimes.seconds("custom_unknown"))
        assertEquals(RestTimes.MEDIUM, RestTimes.seconds(null))
    }

    @Test fun label() {
        assertEquals("4 min", RestTimes.label(240))
        assertEquals("1:30 min", RestTimes.label(90))
    }
}
