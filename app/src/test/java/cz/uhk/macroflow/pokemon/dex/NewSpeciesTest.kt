package cz.uhk.macroflow.pokemon.dex

import cz.uhk.macroflow.pokemon.BattleFactory
import cz.uhk.macroflow.pokemon.BiomeType
import cz.uhk.macroflow.pokemon.MakromonGrowthManager
import cz.uhk.macroflow.pokemon.MakromonType
import cz.uhk.macroflow.pokemon.Rarity
import cz.uhk.macroflow.pokemon.SpawnManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Mysnic (#032), Mysnor (#033), Ignileo (#034) a Aquavulp (#035) v Makrodexu (docs/adr/0061, 0063). */
class NewSpeciesTest {

    private fun file(rel: String): File = listOf(File(rel), File("app/$rel")).first { it.exists() }

    @Test fun mysnicIsACommonMountainMakromon() {
        val e = SpawnManager.allEntries.single { it.id == "032" }
        assertEquals("MYSNIC", e.name)
        assertEquals(Rarity.COMMON, e.rarity)
        assertTrue(BiomeType.MOUNTAINS in e.biomes)
        val m = BattleFactory.createById("032")
        assertEquals("MYSNIC", m.name)
        assertEquals("032", BattleFactory.makrodexId(m))
        assertEquals(MakromonType.GROUND, m.moves.first().type)                     // typ podle prvního útoku
        assertEquals("makromon_32_mysnic", BattleFactory.drawableName(m))
        assertEquals("033", MakromonGrowthManager.getProfile("032")!!.evolutionToId)          // vývoj na levelu 10
        assertEquals(10, MakromonGrowthManager.getProfile("032")!!.evolutionLevel)
        val evo = BattleFactory.createById("033")
        assertEquals("MYSNOR", evo.name)
        assertEquals("033", BattleFactory.makrodexId(evo))
        assertTrue(evo.defense > m.defense)
    }

    @Test fun guardiansAreInTheDexAfterTheirDefeat() {
        val ids = DexText.dexIds(SpawnManager.allEntries.map { it.id })
        assertTrue(listOf("032", "033", "034", "035").all { it in ids })
        assertTrue(SpawnManager.allEntries.none { it.id in DexText.EXTRA_IDS })     // strážce v divočině nepotkáš
        assertEquals(setOf("035"), DexText.defeatedGuardians(setOf("crystal_BLUE")))
        assertEquals(setOf("034"), DexText.defeatedGuardians(setOf("boss_defeated_RED")))
        assertEquals(emptySet<String>(), DexText.defeatedGuardians(emptySet()))
        // čísla Makrodexu se neopakují
        val spawnIds = SpawnManager.allEntries.map { it.id }.distinct()
        assertTrue(spawnIds.none { it in DexText.EXTRA_IDS })
    }

    @Test fun spritesExist() {
        listOf("makromon_32_mysnic", "makromon_34_ignileo", "makromon_35_aquavulp", "makromon_05_aqulind", "makromon_09_florindra", "makromon_27_phantil", "makromon_06_aqulinox", "makromon_18_glacirra").forEach {
            assertTrue(it, file("src/main/res/drawable/$it.png").exists())
        }
    }
}
