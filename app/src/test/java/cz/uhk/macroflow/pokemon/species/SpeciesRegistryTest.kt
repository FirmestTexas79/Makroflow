package cz.uhk.macroflow.pokemon.species

import cz.uhk.macroflow.pokemon.BattleFactory
import cz.uhk.macroflow.pokemon.MakromonGrowthManager
import cz.uhk.macroflow.pokemon.SpawnManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Registr druhů je jediný zdroj pravdy (docs/adr/0067) – hlídá, že do sebe všechno zapadá. */
class SpeciesRegistryTest {
    private val all = SpeciesRegistry.ALL

    @Test fun `cisla a jmena jsou jedinecna a ve spravnem tvaru`() {
        assertEquals(all.size, all.map { it.id }.toSet().size)
        assertEquals(all.size, all.map { it.name }.toSet().size)
        all.forEach {
            assertTrue(it.id, Regex("\\d{3}").matches(it.id))
            assertEquals(it.id, it.name.uppercase(), it.name)
            assertTrue(it.id, it.displayName.isNotBlank() && it.dexType.isNotBlank())
        }
    }

    @Test fun `kazdy druh jde vytvorit a sedi s BattleFactory`() {
        SpeciesRegistry.PLAYABLE.forEach { s ->
            val m = BattleFactory.createById(s.id)
            assertEquals(s.name, m.name)
            assertEquals(s.id, BattleFactory.makrodexId(m))
            assertEquals(s.sprite, BattleFactory.drawableName(m))
            assertTrue(s.id, m.moves.isNotEmpty())
        }
    }

    @Test fun `strazci maji vlastni cislo ale statistiky jineho druhu`() {
        all.filter { it.guardianOf != null }.forEach { g ->
            val m = BattleFactory.createById(g.id)
            assertEquals(g.name, m.name)
            assertEquals(g.guardianOf, BattleFactory.makrodexId(m))
            assertEquals(g.id, BattleFactory.GUARDIAN_DEX[g.name])
            assertEquals("makromon_${g.id.takeLast(2)}_${g.name.lowercase()}", BattleFactory.drawableName(m))
            assertTrue(g.spawns.isEmpty())
        }
    }

    @Test fun `vyvoj vede na existujici druh a sedi s rustovou krivkou`() {
        all.mapNotNull { s -> s.evolves?.let { s to it } }.forEach { (s, e) ->
            assertNotNull("${s.id} → ${e.to}", SpeciesRegistry.byId(e.to))
            assertTrue(s.id, e.level > 0)
            val p = MakromonGrowthManager.getProfile(s.id)!!
            assertEquals(e.to, p.evolutionToId); assertEquals(e.level, p.evolutionLevel)
        }
    }

    @Test fun `spawny vznikaji z registru`() {
        val expected = all.sumOf { it.spawns.size }
        assertEquals(expected, SpawnManager.allEntries.size)
        SpawnManager.allEntries.forEach { assertEquals(it.name, it.createMakromon().name) }
    }
}
