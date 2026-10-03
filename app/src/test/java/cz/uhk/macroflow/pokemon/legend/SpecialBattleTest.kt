package cz.uhk.macroflow.pokemon.legend

import cz.uhk.macroflow.pokemon.cave.CrystalColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpecialBattleTest {

    @Test
    fun guardiansAreLevel12AndLegendLevel80() {
        CrystalColor.entries.forEach { c ->
            val g = SpecialBattle.guardianOf(c)
            assertEquals(12, g.level)
            assertEquals(SpecialBattle.Kind.BOSS, g.kind)
        }
        assertEquals(80, SpecialBattle.LEGEND_PEAK.level)
        assertNull(SpecialBattle.LEGEND_PEAK.crystal)
    }

    @Test
    fun noCatchingNoRunning() {
        SpecialBattle.entries.forEach { assertFalse(it.canCatch); assertFalse(it.canRun) }
    }

    @Test
    fun legendCannotBeBeatenButGuardianCan() {
        assertEquals(1, SpecialBattle.LEGEND_PEAK.clampEnemyHp(0))
        assertEquals(1, SpecialBattle.LEGEND_PEAK.clampEnemyHp(-50))
        assertEquals(0, SpecialBattle.BOSS_RED.clampEnemyHp(0))
        assertEquals(37, SpecialBattle.LEGEND_PEAK.clampEnemyHp(37))
    }

    @Test
    fun linesFitTheBattleTextBox() {
        // 6 px na znak, řádek od x=6 do ~148 → max. 23 znaků; nejdelší jméno má 9
        val longest = "IGNAROTHX"
        SpecialBattle.entries.forEach { sp ->
            val lines = listOf(sp.appearLines(longest), sp.noCatchLines, sp.noRunLines) + sp.fleeLines(longest)
            lines.forEach { (a, b) -> assertTrue(a, a.length <= 23); assertTrue(b, b.length <= 23) }
        }
    }

    @Test
    fun spriteNamesMatchMakrodexNumbers() {
        SpecialBattle.entries.forEach { assertTrue(it.spriteName, it.spriteName.startsWith("makromon_${it.makromonId.takeLast(2)}_")) }
        assertEquals(SpecialBattle.BOSS_BLUE, SpecialBattle.from("boss_blue"))
        assertNull(SpecialBattle.from(null))
    }

    /** Strážci mají vlastní číslo Makrodexu a sprite, statistiky berou z jiného druhu (docs/adr/0063). */
    @Test
    fun guardiansHaveOwnDexNumbersAndBorrowedStats() {
        val red = SpecialBattle.guardianOf(CrystalColor.RED)
        val blue = SpecialBattle.guardianOf(CrystalColor.BLUE)
        assertEquals("IGNILEO", red.displayName)
        assertEquals("AQUAVULP", blue.displayName)
        assertEquals("034" to "003", red.makromonId to red.statsId)
        assertEquals("035" to "021", blue.makromonId to blue.statsId)
        listOf(red, blue).forEach { g ->
            // battle (BattleFactory.drawableName) i mapa (spriteName) najdou stejný sprite
            val m = cz.uhk.macroflow.pokemon.BattleFactory.createById(g.statsId).copy(name = g.displayName!!)
            assertEquals(g.spriteName, cz.uhk.macroflow.pokemon.BattleFactory.drawableName(m))
        }
        assertEquals(SpecialBattle.LEGEND_PEAK.makromonId, SpecialBattle.LEGEND_PEAK.statsId)
    }
}
