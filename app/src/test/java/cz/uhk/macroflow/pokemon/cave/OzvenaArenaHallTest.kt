package cz.uhk.macroflow.pokemon.cave

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Město Ozvěna a hala Arény (docs/adr/0092). */
class OzvenaArenaHallTest {
    @Test fun mestoJeDosazitelneAVedeZpetNaPlan() {
        val m = OzvenaCity.MAP
        assertEquals(m.nodes.map { it.id }.toSet(), m.reachableFrom(m.exitNode))
        assertEquals("ASH_PLAIN", m.parentBiome)
        assertEquals(AshPlain.CITY_GATE_NODE, m.mountainNode)
        assertTrue(m.artPixelsAcross < m.artW)                     // přiblížená kamera, město je větší než obrazovka
        OzvenaCity.ACTION_NODES.forEach { assertTrue(it, m.node(it) != null && m.tapAreas.containsKey(it)) }
    }

    @Test fun halaVedeZMestaASouboj() {
        val h = ArenaHall.MAP
        assertEquals(h.nodes.map { it.id }.toSet(), h.reachableFrom(h.exitNode))
        assertEquals("OZVENA", h.parentBiome)
        assertEquals(OzvenaCity.ARENA_NODE, h.mountainNode)
        ArenaHall.ACTION_NODES.forEach { assertTrue(it, h.node(it) != null && h.tapAreas.containsKey(it)) }
        assertTrue(h.node(ArenaHall.GATE_NODE)!!.y < h.node(ArenaHall.ENTRY_NODE)!!.y)   // brána do arény na konci haly
    }

    @Test fun sinSlavyMaTriPortretyAVelitelMluvi() {
        assertEquals(3, ArenaHall.PORTRAITS.size)
        ArenaHall.PORTRAITS.forEach { r -> assertTrue(r[0] >= 0 && r[0] + r[2] <= ArenaHall.MAP.artW && r[1] + r[3] <= ArenaHall.MAP.artH) }
        assertEquals(3, ArenaHall.LEGENDS.size)
        assertTrue(ArenaHall.COMMANDER_PAGES.size >= 3)
        assertTrue(ArenaHall.WANTED_TEXT.contains("DRAKIRRA"))
    }

    @Test fun obyvateleStojiVeMesteAMluvi() {
        val m = OzvenaCity.MAP
        assertEquals(5, OzvenaCity.TOWNSFOLK.size)
        val sprites = listOf("src/main/res/drawable", "app/src/main/res/drawable").map { java.io.File(it) }.first { it.exists() }.list()!!.toList()
        OzvenaCity.TOWNSFOLK.forEach { t -> assertTrue(t.id, sprites.any { it.startsWith("makromon_%02d_".format(t.species)) }) }
        OzvenaCity.TOWNSFOLK.forEach { t ->
            assertTrue(t.id, t.x in 0 until m.artW && t.y in 0 until m.artH && m.node(t.node) != null && t.node in OzvenaCity.ACTION_NODES)
        }
        for (n in listOf(OzvenaCity.LIDA_NODE, OzvenaCity.JARDA_NODE, OzvenaCity.KORINEK_NODE)) {
            assertTrue(n, OzvenaCity.residentLine(n, 1) != OzvenaCity.residentLine(n, 2))   // repliky se střídají po dnech
            assertTrue(n, OzvenaCity.residentLine(n, -5) != null)
        }
        assertEquals(null, OzvenaCity.residentLine(OzvenaCity.BAKERY_NODE, 1))              // pekař jede ve smyčce
    }
}
