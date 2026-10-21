package cz.uhk.macroflow.pokemon.species

import org.junit.Assert.assertTrue
import org.junit.Test

/** Všechny druhy ve stejné stupnici statistik (docs/adr/0087). */
class StatsBalanceTest {

    private fun total(s: Species) = s.stats.hp + s.stats.atk + s.stats.def + s.stats.spd

    @Test
    fun everySpeciesIsOnTheMainScale() {
        SpeciesRegistry.ALL.filter { it.guardianOf == null }.forEach { sp ->
            val t = total(sp)
            assertTrue("${sp.displayName} má součet $t (mimo 110–360)", t in 110..360)
        }
    }

    @Test
    fun evolutionIsAlwaysStronger() {
        SpeciesRegistry.ALL.forEach { sp ->
            val to = sp.evolves?.to?.let { SpeciesRegistry.byId(it) } ?: return@forEach
            assertTrue("${sp.displayName} → ${to.displayName}", total(to) > total(sp))
        }
    }
}
