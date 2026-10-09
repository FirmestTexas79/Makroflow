package cz.uhk.macroflow.pokemon.trainer

import cz.uhk.macroflow.pokemon.BattleEngine
import cz.uhk.macroflow.pokemon.BattleFactory
import cz.uhk.macroflow.pokemon.Makromon
import cz.uhk.macroflow.pokemon.MakromonType
import cz.uhk.macroflow.pokemon.PokemonLevelCalc
import cz.uhk.macroflow.pokemon.species.SpeciesRegistry
import cz.uhk.macroflow.pokemon.wild.MovePool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class TrainerTest {

    private fun ghost(vararg team: TrainerMon, name: String = "Samuel") =
        Trainer("uid1", name, Trainer.Kind.GHOST, team.toList(), 42L)

    @Test
    fun sanitizeDropsUnknownAndGuardianSpeciesAndClampsLevel() {
        val guardian = SpeciesRegistry.ALL.first { it.guardianOf != null }.id
        val t = Trainers.sanitize(ghost(
            TrainerMon("001", 999),
            TrainerMon("999", 5),
            TrainerMon(guardian, 5),
            TrainerMon("012", -3)
        ))!!
        assertEquals(listOf("001", "012"), t.team.map { it.speciesId })
        assertEquals(PokemonLevelCalc.MAX_LEVEL, t.team[0].level)
        assertEquals(1, t.team[1].level)
    }

    @Test
    fun sanitizeKeepsOnlyMovesTheSpeciesKnowsAtItsLevel() {
        val allowed = MovePool.pool("001", 2).map { it.name }
        val t = Trainers.sanitize(ghost(TrainerMon("001", 2, listOf(allowed.first(), "OUTRAGE", "HYDRO PUMP", allowed.first()))))!!
        assertEquals(listOf(allowed.first()), t.team[0].moves)
    }

    @Test
    fun sanitizeLimitsTeamAndNameAndRejectsEmpty() {
        val big = ghost(*Array(9) { TrainerMon("012", 3) }, name = "  Někdo s hodně dlouhým jménem  ")
        val t = Trainers.sanitize(big)!!
        assertEquals(Trainers.MAX_TEAM, t.team.size)
        assertTrue(t.name.length <= Trainers.NAME_MAX)
        assertNull(Trainers.sanitize(ghost(TrainerMon("999", 3))))
    }

    @Test
    fun battleStatsComeFromSpeciesAndLevelOnly() {
        val m = Trainers.toBattle(TrainerMon("001", 7))
        val expected = BattleEngine.initializeStatsForLevel(BattleFactory.createById("001"), 7)
        assertEquals(expected.maxHp, m.maxHp)
        assertEquals(expected.attack, m.attack)
        assertEquals(expected.defense, m.defense)
        assertEquals(expected.speed, m.speed)
        assertTrue(m.moves.isNotEmpty())
    }

    @Test
    fun jsonAndMapRoundTrip() {
        val t = ghost(TrainerMon("001", 4, listOf("SCRATCH", "GROWL"), shiny = true), TrainerMon("012", 3))
        assertEquals(t, Trainers.fromJson(Trainers.toJson(t)))
        val back = Trainers.fromMap("uid1", Trainers.toMap(t))
        assertEquals(t, back)
        assertNull(Trainers.fromJson("nesmysl"))
        assertNull(Trainers.fromMap("x", null))
    }

    @Test
    fun battleNameIsAsciiUppercase() {
        assertEquals("BEDA BENCPRES", ghost(name = "Béďa Benčpres").battleName)
        assertEquals("TRAINER", ghost(name = "💪💪").battleName)
    }

    @Test
    fun ghostsClosestInPowerFirstWithoutMe() {
        fun g(id: String, lvl: Int) = Trainer(id, id, Trainer.Kind.GHOST, listOf(TrainerMon("012", lvl)))
        val picked = Arena.pickGhosts(listOf(g("me", 10), g("a", 30), g("b", 11), g("c", 6)), "me", 10, n = 2)
        assertEquals(listOf("b", "c"), picked.map { it.id })
    }

    @Test
    fun aiTrainersScaleWithPlayerAndAreStablePerDay() {
        val my = listOf(TrainerMon("001", 8), TrainerMon("012", 8), TrainerMon("012", 8))
        val a = Arena.aiTrainers(my, day = 100)
        assertEquals(3, a.size)
        assertEquals(listOf(2, 3, 4), a.map { it.team.size })
        assertEquals(listOf(6, 8, 10), a.map { it.team.first().level })
        assertEquals(a, Arena.aiTrainers(my, day = 100))
        a.forEach { assertNotNull(Trainers.sanitize(it)); assertEquals(it.team.size, Trainers.sanitize(it)!!.team.size) }
    }

    @Test
    fun snapshotKeepsTeamOrderAndMoves() {
        val e = cz.uhk.macroflow.pokemon.CapturedMakromonEntity(makromonId = "001", name = "IGNAR", level = 5, moveListStr = "SCRATCH, GROWL")
        val s = Arena.snapshot("u", "Sam", listOf(e), 1L)
        assertEquals(TrainerMon("001", 5, listOf("SCRATCH", "GROWL")), s.team.single())
        assertEquals(5, s.power)
    }

    // ── AI ──

    private class Fixed(private val f: Float) : Random() {
        override fun nextBits(bitCount: Int) = 0
        override fun nextFloat() = f
    }

    private fun mon(type: MakromonType, vararg moves: cz.uhk.macroflow.pokemon.Move) =
        Makromon("X", 10, 50, attack = 20, defense = 20, speed = 20, moves = moves.toList(), type = type)

    @Test
    fun aiPrefersSuperEffectiveMove() {
        val self = mon(MakromonType.FIRE, BattleFactory.attackTackle(), BattleFactory.attackEmber())
        val grass = mon(MakromonType.GRASS, BattleFactory.attackTackle())
        assertEquals("EMBER", TrainerAi.chooseMove(self, grass, rnd = Fixed(0.9f)).name)
    }

    @Test
    fun aiNeverPicksImmuneDamageMoveAsBest() {
        val self = mon(MakromonType.ELECTRIC, BattleFactory.attackThunderbolt(), BattleFactory.attackTackle())
        val ground = mon(MakromonType.GROUND, BattleFactory.attackTackle())
        assertEquals("TACKLE", TrainerAi.chooseMove(self, ground, rnd = Fixed(0.9f)).name)
    }

    @Test
    fun aiSkipsMovesWithoutPp() {
        val ember = BattleFactory.attackEmber().apply { pp = 0 }
        val self = mon(MakromonType.FIRE, BattleFactory.attackTackle(), ember)
        val grass = mon(MakromonType.GRASS, BattleFactory.attackTackle())
        assertEquals("TACKLE", TrainerAi.chooseMove(self, grass, rnd = Fixed(0.9f)).name)
    }

    @Test
    fun typeChartImmunitiesDealNoDamage() {
        val T = MakromonType
        assertEquals(0f, BattleEngine.getTypeEffectiveness(T.GROUND, T.FLYING))
        assertEquals(0f, BattleEngine.getTypeEffectiveness(T.ELECTRIC, T.GROUND))
        assertEquals(2f, BattleEngine.getTypeEffectiveness(T.GROUND, T.ELECTRIC))
        assertEquals(.5f, BattleEngine.getTypeEffectiveness(T.FIRE, T.DRAGON))
        assertEquals(1f, BattleEngine.getTypeEffectiveness(T.NORMAL, T.FIRE))
        assertEquals(0, BattleEngine.calcDamage(10, 80, 50, 50, T.NORMAL, T.GHOST))
    }
}
