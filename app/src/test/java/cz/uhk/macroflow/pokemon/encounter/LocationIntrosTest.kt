package cz.uhk.macroflow.pokemon.encounter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

class LocationIntrosTest {
    private val names = listOf("SKY_PASS", "HIDDEN_GROVE", "MINES", "ASH_PLAIN", "QUIET_ORCHARD", "ECHO_CANYON",
        "TOWER_PLAIN", "TOWER_FOREST", "TOWER_ROCK", "TOWER_CAVE", "TOWER_WATER", "TOWER_DORM", "TOWER_ICE",
        "TOWER_FIRE", "TOWER_FAIRY", "TOWER_POISON")

    private fun load(name: String): Img {
        val b = ImageIO.read(File("src/main/res/drawable-nodpi/$name.png"))
        val px = IntArray(b.width * b.height); b.getRGB(0, 0, b.width, b.height, px, 0, b.width)
        return Img(b.width, b.height, px)
    }

    @Test fun timelineIsOrdered() {
        val t = listOf(Kit.FADE, Kit.TELL1, Kit.TELL2, Kit.SKIP, Kit.CLIMAX, Kit.PEAK, Kit.REVEAL, Kit.END)
        assertEquals(t.sorted(), t)
    }

    @Test fun everyLocationRendersOpaqueFramesAtAnyScreenSize() {
        for (n in names) {
            val intro = LocationIntros.forBiome(n); assertNotNull(n, intro)
            val map = load(intro!!.map)
            for ((w, h) in listOf(72 to 160, 66 to 143, 77 to 171)) {
                val s = intro.make(map, w, h)
                val px = IntArray(w * h)
                for (t in 0L..Kit.END step 131) { s.render(t, px); assertTrue("$n ${w}x$h t=$t", px.all { (it ushr 24) == 0xFF }) }
            }
        }
    }
}
