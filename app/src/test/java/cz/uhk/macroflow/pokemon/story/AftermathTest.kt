package cz.uhk.macroflow.pokemon.story

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AftermathTest {
    private val all: (String) -> Boolean = { true }

    @Test
    fun everyEndingChangesEveryNpcAndTown() {
        for (end in Finale.End.entries) {
            for (n in Aftermath.NPCS) assertNotNull("$end ${n.node}", Aftermath.npc(end, n.biome, n.node, all))
            assertNotNull("$end TOWN", Aftermath.arrival(end, "TOWN", emptySet()))
        }
    }

    @Test
    fun npcWaitsForItsSideQuest() {
        val k = Aftermath.KESH
        assertNull(Aftermath.npc(Finale.End.SPEAR, k.biome, k.node) { false })
        assertNotNull(Aftermath.npc(Finale.End.SPEAR, Aftermath.BRANN.biome, Aftermath.BRANN.node) { false })
        assertNull(Aftermath.npc(Finale.End.SPEAR, "OZVENA", "kasna", all))
    }

    @Test
    fun arrivalOnlyOnce() {
        assertNull(Aftermath.arrival(Finale.End.BLOOM, "TOWN", setOf("TOWN")))
        assertNull(Aftermath.arrival(Finale.End.BLOOM, "MINES", emptySet()))
    }

    @Test
    fun onlyWishRevealsLumenci() {
        for (end in Finale.End.entries.filter { it != Finale.End.WISH }) {
            val texts = Aftermath.NPCS.mapNotNull { Aftermath.npc(end, it.biome, it.node, all) } +
                listOf("TOWN", "MEADOW", "OZVENA", "TOWER_LOBBY", "ROOT_FOREST", "ROOT_TRUNK", "ROOT_NAMELESS", "ROOT_TEMPLE")
                    .mapNotNull { Aftermath.arrival(end, it, emptySet()) }
            texts.forEach { assertFalse("$end: $it", it.contains("Lumenc") || it.contains("byli lidé")) }
        }
        assertEquals(5, Aftermath.NPCS.size)
    }
}
