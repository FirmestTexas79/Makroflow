package cz.uhk.macroflow.pokemon.trade

import cz.uhk.macroflow.pokemon.PokemonLevelCalc
import cz.uhk.macroflow.pokemon.species.SpeciesRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class TradingTest {

    private val offer = TradeOffer("uid-1", "001", 5, PokemonLevelCalc.xpForLevel(5) + 10, listOf("SCRATCH"), shiny = true)

    @Test
    fun codesUseUnambiguousAlphabet() {
        repeat(200) {
            val c = Trading.newCode(Random(it))
            assertEquals(Trading.CODE_LEN, c.length)
            assertTrue(c.all { ch -> ch in Trading.ALPHABET })
        }
        assertEquals("ABCDEF", Trading.normalizeCode(" abc-def "))
        assertEquals("ABC DEF", Trading.pretty("ABCDEF"))
    }

    @Test
    fun phasesFollowStateAndConfirmations() {
        val t = Trade("ABCDEF", "a", "Sam", offer)
        assertEquals(Trading.Phase.OPEN, Trading.phase(t))
        val offered = t.copy(b = "b", offerB = offer, state = Trading.OFFERED)
        assertEquals(Trading.Phase.OFFERED, Trading.phase(offered))
        assertEquals(Trading.Phase.OFFERED, Trading.phase(offered.copy(confirmA = true)))
        assertEquals(Trading.Phase.DONE, Trading.phase(offered.copy(confirmA = true, confirmB = true)))
        assertEquals(Trading.Phase.CANCELLED, Trading.phase(offered.copy(state = Trading.CANCELLED)))
    }

    @Test
    fun rolesPickTheRightSides() {
        val other = offer.copy(uid = "uid-2", speciesId = "012")
        val t = Trade("ABCDEF", "a", "Sam", offer, b = "b", bName = "Tom", offerB = other, state = Trading.OFFERED, confirmB = true)
        assertEquals(Trading.Role.A, Trading.role(t, "a"))
        assertEquals(Trading.Role.B, Trading.role(t, "b"))
        assertNull(Trading.role(t, "x"))
        assertEquals(other, Trading.theirOffer(t, Trading.Role.A))
        assertEquals("Tom", Trading.theirName(t, Trading.Role.A))
        assertEquals("Sam", Trading.theirName(t, Trading.Role.B))
        assertTrue(Trading.confirmed(t, Trading.Role.B))
    }

    @Test
    fun sanitizeRejectsGuardiansAndClampsLevelAndXp() {
        val guardian = SpeciesRegistry.ALL.first { it.guardianOf != null }.id
        assertNull(Trading.sanitize(offer.copy(speciesId = guardian)))
        assertNull(Trading.sanitize(offer.copy(speciesId = "999")))
        assertNull(Trading.sanitize(offer.copy(uid = "")))
        val s = Trading.sanitize(offer.copy(level = 99, xp = 0, moves = listOf("OUTRAGE")))!!
        assertEquals(PokemonLevelCalc.MAX_LEVEL, s.level)
        assertEquals(PokemonLevelCalc.xpForLevel(PokemonLevelCalc.MAX_LEVEL), s.xp)
        assertTrue(s.moves.isEmpty())
        // XP nesmí přesáhnout další level
        val t = Trading.sanitize(offer.copy(xp = 1_000_000))!!
        assertEquals(5, PokemonLevelCalc.levelFromXp(t.xp))
    }

    @Test
    fun receivedKeepsIdentityAndRecordsOriginalTrainer() {
        val e = Trading.received(offer, "Tomáš", 123L)
        assertEquals("uid-1", e.uid)
        assertEquals("Tomáš", e.otName)
        assertEquals(5, e.level)
        assertEquals(offer.xp, e.xp)
        assertTrue(e.isShiny)
        assertEquals("SCRATCH", e.moveListStr)
        assertEquals(Trading.speciesAfterTrade("001"), e.makromonId)
        assertEquals(SpeciesRegistry.byId(e.makromonId)!!.name, e.name)
    }

    @Test
    fun tradeEvolutionTargetsExist() {
        SpeciesRegistry.ALL.mapNotNull { it.tradeEvolvesTo }.forEach { assertTrue(it, SpeciesRegistry.byId(it) != null) }
    }

    @Test
    fun firestoreMapRoundTrip() {
        val m = Trading.createMap("a", "Sam", offer, 5L)
        val t = Trading.fromMap("ABCDEF", m)!!
        assertEquals("a", t.a)
        assertEquals(Trading.sanitize(offer), t.offerA)
        assertNull(t.b)
        assertEquals(Trading.Phase.OPEN, Trading.phase(t))
        assertEquals(offer, Trading.offerFromMap(Trading.offerToMap(offer)))
        assertNull(Trading.fromMap("X", mapOf("state" to "open")))
    }
}
