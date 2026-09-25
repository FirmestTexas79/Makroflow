package cz.uhk.macroflow.pokemon

import cz.uhk.macroflow.pokemon.wild.BattleRewards
import cz.uhk.macroflow.pokemon.wild.MoveDex
import cz.uhk.macroflow.pokemon.wild.MovePool
import cz.uhk.macroflow.pokemon.wild.SpeciesIds
import cz.uhk.macroflow.pokemon.wild.WildLevels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class WildRulesTest {

    @Test
    fun levelRangesPerBiome() {
        assertEquals(2..4, WildLevels.range(BiomeType.TOWN))
        assertEquals(2..4, WildLevels.range(BiomeType.MEADOW))
        assertEquals(4..8, WildLevels.range(BiomeType.MOUNTAINS))
        assertEquals(6..12, WildLevels.range(BiomeType.CAVE_OPEN))
        assertEquals(6..12, WildLevels.range(BiomeType.CAVE_MAZE))
        BiomeType.entries.forEach { b ->
            val rnd = Random(1)
            repeat(500) { assertTrue(b.name, WildLevels.roll(b, rnd) in WildLevels.range(b)) }
        }
    }

    @Test
    fun meadowLevelFourIsOccasional() {
        val rnd = Random(42)
        val rolls = List(10_000) { WildLevels.roll(BiomeType.MEADOW, rnd) }
        val four = rolls.count { it == 4 } / 10_000.0
        assertTrue("4 = $four", four in 0.07..0.13)
        assertTrue(rolls.count { it == 2 } > 4000 && rolls.count { it == 3 } > 4000)
    }

    @Test
    fun caveCoversWholeRange() {
        val rnd = Random(7)
        assertEquals((6..12).toSet(), List(2000) { WildLevels.roll(BiomeType.CAVE_OPEN, rnd) }.toSet())
    }

    @Test
    fun moveDexKnowsAllSpeciesMoves() {
        SpeciesIds.ALL.forEach { id ->
            BattleFactory.createById(id).moves.forEach { m -> assertTrue("$id ${m.name}", MoveDex.get(m.name) != null) }
        }
        assertTrue(MoveDex.get("SPITE") != null)
        assertEquals(null, MoveDex.get("NEEXISTUJE"))
    }

    @Test
    fun strongMovesOnlyAtHigherLevel() {
        // Ignaroth: FLAMETHROWER (90) a výš – na levelu 2 ne, na 12 ano
        val low = MovePool.pool("003", 2).map { it.name }
        val high = MovePool.pool("003", 12).map { it.name }
        assertTrue(low.contains("EMBER"))                // podpisový útok vždy
        assertFalse(low.contains("FLAMETHROWER"))
        assertTrue(high.contains("FLAMETHROWER"))
        assertFalse(MovePool.defaultUnlocked(BattleFactory.attackHydroPump(), 8))
        assertTrue(MovePool.defaultUnlocked(BattleFactory.attackHydroPump(), 9))
    }

    @Test
    fun evolvedFormKnowsAncestorMoves() {
        assertEquals(listOf("001"), MovePool.ancestors("002"))
        assertEquals(listOf("002", "001"), MovePool.ancestors("003"))
        assertTrue(MovePool.pool("002", 5).any { it.name == "SCRATCH" })
    }

    @Test
    fun wildMovesetsAreValidAndVaried() {
        SpeciesIds.ALL.forEach { id ->
            for (level in listOf(2, 4, 8, 12)) {
                val rnd = Random(id.toInt() * 100 + level)
                val set = MovePool.wildMoveset(id, level, rnd)
                val signature = BattleFactory.createById(id).moves.first().name
                assertTrue("$id $level", set.size in 1..4)
                assertTrue("$id $level", signature == set.first().name)
                assertTrue("$id $level útočný", set.any { it.power > 0 })
                assertEquals(set.size, set.map { it.name }.distinct().size)
            }
        }
        // stejný druh a level → různé sady (aspoň někdy)
        val sets = (1..30).map { MovePool.wildMoveset("031", 25, Random(it)).map { m -> m.name }.toSet() }.toSet()
        assertTrue("sad: ${sets.size}", sets.size > 1)
    }

    @Test
    fun storedMovesResolve() {
        val fallback = BattleFactory.createIgnar().moves
        assertEquals(fallback, MovePool.resolve("", fallback))
        assertEquals(listOf("EMBER", "SCRATCH"), MovePool.resolve("EMBER, SCRATCH,NEEXISTUJE", fallback).map { it.name })
        assertEquals(fallback, MovePool.resolve("NEEXISTUJE", fallback))
        assertEquals("EMBER,SCRATCH", MovePool.namesOf(MovePool.resolve("EMBER,SCRATCH", fallback)))
        // PP po načtení plné
        assertTrue(MovePool.resolve("EMBER", fallback).all { it.pp == it.maxPp })
    }

    @Test
    fun coinsOneToFive() {
        val rnd = Random(3)
        val s = List(1000) { BattleRewards.coinsForWin(rnd) }
        assertEquals((1..5).toSet(), s.toSet())
    }

    @Test
    fun levelCalcKeepsWildLevel() {
        // chycený level 7 začíná na XP levelu 7 a odměna ho nesníží
        val xp7 = PokemonLevelCalc.xpForLevel(7)
        assertEquals(7, PokemonLevelCalc.levelFromXp(xp7))
        assertEquals(7, PokemonLevelCalc.gain(7, 0, 10).second)          // starý zápis: level 7, XP 0
        assertEquals(xp7 + 10, PokemonLevelCalc.gain(7, 0, 10).first)
        assertEquals(12, PokemonLevelCalc.levelFromXp(PokemonLevelCalc.xpForLevel(12)))
        assertTrue(PokemonLevelCalc.xpForLevel(12) > PokemonLevelCalc.xpForLevel(11))
        assertEquals(PokemonLevelCalc.MAX_LEVEL, PokemonLevelCalc.levelFromXp(Int.MAX_VALUE / 2))
        // původní křivka do levelu 10 beze změny
        assertEquals(listOf(0, 50, 150, 300, 500, 800, 1200, 1800, 2500, 3500), (1..10).map { PokemonLevelCalc.xpForLevel(it) })
    }

    @Test
    fun speciesTypeStaysWithRandomMoves() {
        val base = BattleFactory.createAqulin()
        val m = base.copy(moves = listOf(BattleFactory.attackTackle()), type = base.speciesType)
        assertEquals(base.moves.first().type, m.speciesType)
    }
}
