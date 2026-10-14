package cz.uhk.macroflow.pokemon.species

import cz.uhk.macroflow.pokemon.MakromonGrowthManager
import cz.uhk.macroflow.pokemon.wild.MoveDex
import cz.uhk.macroflow.pokemon.wild.MovePool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Noví Makromoni (docs/adr/0084): vývoj, učení útoků a typ podpisového útoku. */
class NewSpeciesTest {

    private val NEW = listOf("033", "041", "042", "043", "044", "045", "047")

    @Test
    fun everyNewSpeciesLearnsMovesThatResolve() {
        NEW.forEach { id ->
            val learned = MakromonGrowthManager.getProfile(id)!!.movesLearnedAt
            assertTrue("$id se nic neučí", learned.isNotEmpty())
            learned.forEach { assertNotNull("$id: ${it.move.name} není v MoveDex", MoveDex.get(it.move.name)) }
            SpeciesRegistry.byId(id)!!.moves().forEach { assertNotNull("$id: ${it.name}", MoveDex.get(it.name)) }
        }
    }

    @Test
    fun evolvedFormsLearnOnlyAfterEvolving() {
        assertEquals("045", SpeciesRegistry.byId("044")!!.evolves!!.to)
        val lvl = SpeciesRegistry.byId("044")!!.evolves!!.level
        assertTrue(MakromonGrowthManager.getProfile("045")!!.movesLearnedAt.all { it.level >= lvl })
        assertTrue(MakromonGrowthManager.getProfile("041")!!.movesLearnedAt.all { it.level > 12 })
        // Krystalos umí i to, co se naučil jako Gemak
        assertTrue(MovePool.pool("045", 12).any { it.name == "DIG" })
    }

    @Test
    fun signatureMoveMatchesSpeciesType() {
        NEW.mapNotNull { SpeciesRegistry.byId(it) }.filter { it.type != null }.forEach { sp ->
            assertEquals(sp.id, sp.type, sp.moves().first().type)
        }
    }
}
