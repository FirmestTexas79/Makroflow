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

/** Ignileo a Mysnic v Makrodexu (docs/adr/0061). */
class NewSpeciesTest {

    private fun file(rel: String): File = listOf(File(rel), File("app/$rel")).first { it.exists() }

    @Test fun mysnicIsACommonMountainMakromon() {
        val e = SpawnManager.allEntries.single { it.id == "033" }
        assertEquals("MYSNIC", e.name)
        assertEquals(Rarity.COMMON, e.rarity)
        assertTrue(BiomeType.MOUNTAINS in e.biomes)
        val m = BattleFactory.createById("033")
        assertEquals("MYSNIC", m.name)
        assertEquals("033", BattleFactory.makrodexId(m))
        assertEquals(MakromonType.GROUND, m.moves.first().type)                     // typ podle prvního útoku
        assertEquals("makromon_33_mysnic", BattleFactory.drawableName(m))
        assertTrue(MakromonGrowthManager.getProfile("033") != null)
    }

    @Test fun ignileoIsInTheDexAfterHisDefeat() {
        val ids = DexText.dexIds(SpawnManager.allEntries.map { it.id })
        assertTrue("032" in ids && "033" in ids)
        assertTrue(SpawnManager.allEntries.none { it.id == DexText.IGNILEO })       // v divočině ho nepotkáš
        assertEquals(emptySet<String>(), DexText.defeatedGuardians(setOf("crystal_BLUE")))
        assertEquals(setOf("032"), DexText.defeatedGuardians(setOf("boss_defeated_RED")))
    }

    @Test fun spritesExist() {
        listOf("makromon_32_ignileo", "makromon_33_mysnic", "makromon_03_ignileo").forEach {
            assertTrue(it, file("src/main/res/drawable/$it.png").exists())
        }
    }
}
