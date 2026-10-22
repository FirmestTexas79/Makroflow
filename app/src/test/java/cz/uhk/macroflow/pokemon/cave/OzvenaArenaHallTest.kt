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
}
