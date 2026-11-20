package cz.uhk.macroflow.pokemon.encounter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationIntrosTest {
    private val names = listOf("SKY_PASS", "HIDDEN_GROVE", "MINES", "ASH_PLAIN", "QUIET_ORCHARD", "ECHO_CANYON",
        "TOWER_PLAIN", "TOWER_FOREST", "TOWER_ROCK", "TOWER_CAVE", "TOWER_WATER", "TOWER_DORM", "TOWER_ICE",
        "TOWER_FIRE", "TOWER_FAIRY", "TOWER_POISON")

    @Test fun timelineIsOrdered() {
        val t = listOf(Kit.FADE, Kit.TELL1, Kit.TELL2, Kit.SKIP, Kit.CLIMAX, Kit.PEAK, Kit.REVEAL, Kit.END)
        assertEquals(t.sorted(), t)
    }

    @Test fun everyLocationRendersOpaqueFrames() {
        for (n in names) {
            val s = assertNotNullIntro(n).make(128, 280)
            val px = IntArray(128 * 280)
            for (t in 0L..Kit.END step 113) { s.render(t, px); assertTrue("$n t=$t", px.all { (it ushr 24) == 0xFF }) }
        }
    }

    private fun assertNotNullIntro(n: String): LocationIntro = LocationIntros.forBiome(n).also { assertNotNull(n, it) }!!
}
