package cz.uhk.macroflow.pokemon.skills

import cz.uhk.macroflow.pokemon.balls.Makroball
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SkillsTest {

    @Test fun xpCurveMatchesIdleOnFormula() {
        // ⌊15 + 4L + (1,5L)^2,2 + 5L·1,45^max(0,(L−20)/7)⌋
        assertEquals(26L, SkillMath.xpToNext(1))
        assertEquals(491L, SkillMath.xpToNext(10))
        assertEquals(1971L, SkillMath.xpToNext(20))
        // Nad 20 přibývá exponenciální člen
        assertTrue(SkillMath.xpToNext(40) > SkillMath.xpToNext(39) * 1.05)
    }

    @Test fun levelStartsAtOneAndCarriesXp() {
        assertEquals(1, SkillMath.levelOf(0))
        assertEquals(1, SkillMath.levelOf(25))
        assertEquals(2, SkillMath.levelOf(26))
        val p = SkillMath.progress(30)
        assertEquals(2, p.level); assertEquals(4L, p.xpInLevel); assertEquals(44L, p.xpNeeded)
        assertEquals(70L, SkillMath.totalForLevel(3))
        assertEquals(3, SkillMath.levelOf(SkillMath.totalForLevel(3)))
    }

    @Test fun skillPointsAt3_6_10_15_20() {
        assertEquals(0, SkillMath.skillPointsEarned(2))
        assertEquals(1, SkillMath.skillPointsEarned(3))
        assertEquals(2, SkillMath.skillPointsEarned(6))
        assertEquals(2, SkillMath.skillPointsEarned(9))
        assertEquals(3, SkillMath.skillPointsEarned(10))
        assertEquals(5, SkillMath.skillPointsEarned(20))
        assertEquals(6, SkillMath.nextSkillPointLevel(3))
        assertEquals(15, SkillMath.nextSkillPointLevel(10))
    }

    @Test fun gainFormulaAddsThenMultiplies() {
        // 100 × (1 + 0,15 + 0,10) × 2 × 0,8 = 200
        assertEquals(200, SkillMath.gain(100.0, listOf(0.15, 0.10), listOf(2.0), 0.8))
        assertEquals(100, SkillMath.gain(100.0))
        assertEquals(1.25, SkillMath.xpMultiplier(listOf(0.15, 0.10)), 1e-9)
    }

    @Test fun passiveReducesFromBaseNotAbsolute() {
        assertEquals(0.0, SkillMath.passiveBonus(1), 1e-9)
        assertEquals(0.01, SkillMath.passiveBonus(2), 1e-9)
        assertEquals(0.5, SkillMath.passiveBonus(500), 1e-9)
        // 8 % šance, bonus 10 % → 7,2 % (ne −2 %)
        assertEquals(0.072, SkillMath.reduced(0.08, 0.10), 1e-9)
        assertEquals(0.0792, CatchRules.fleeChance(0.08, SkillMath.passiveBonus(2)), 1e-9)
    }

    @Test fun rollDoubleRespectsChance() {
        val rng = Random(1)
        assertEquals(1, SkillMath.rollDouble(0.0, rng))
        assertEquals(2, SkillMath.rollDouble(1.0, rng))
        val twos = (1..10_000).count { SkillMath.rollDouble(0.1, rng) == 2 }
        assertTrue("$twos", twos in 850..1150)
    }

    @Test fun treeNeedsPointsAndPrerequisites() {
        val team2 = SkillTree.node("team_2")!!
        val team3 = SkillTree.node("team_3")!!
        var s = SkillState()
        assertEquals(SkillState.NodeStatus.NO_POINTS, s.status(team2))
        assertEquals(SkillState.NodeStatus.LOCKED, s.status(team3))
        s = s.withXp(Skill.CATCHING, SkillMath.totalForLevel(3).toInt())
        assertEquals(1, s.availablePoints(Skill.CATCHING))
        assertTrue(s.canUnlock(team2))
        s = s.withNode("team_2")
        assertEquals(0, s.availablePoints(Skill.CATCHING))
        assertEquals(2, s.teamSlots)
        assertEquals(SkillState.NodeStatus.NO_POINTS, s.status(team3))
        // Body jiné dovednosti se nepočítají
        s = s.withXp(Skill.CRAFTING, 10_000)
        assertEquals(0, s.availablePoints(Skill.CATCHING))
    }

    @Test fun effectsFromTree() {
        val all = SkillState(unlocked = SkillTree.NODES.map { it.id }.toSet())
        assertEquals(6, all.teamSlots)
        assertEquals(4, all.plotsOpen)
        assertTrue(all.basicEquipment)
        assertEquals(1.15, all.xpMultiplier(Skill.CATCHING), 1e-9)
        assertEquals(0.15, all.growthSpeedup, 1e-9)
        val none = SkillState()
        assertEquals(1, none.teamSlots)
        assertEquals(2, none.plotsOpen)
        assertFalse(none.basicEquipment)
    }

    @Test fun treeIsConsistent() {
        val ids = SkillTree.NODES.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        SkillTree.NODES.forEach { n ->
            n.requires?.let { r -> assertTrue("${n.id} vyžaduje uzel jiné dovednosti", n.skill == SkillTree.node(r)!!.skill) }
        }
        assertEquals(5, SkillTree.NODES.count { it.effect == SkillTree.Effect.TeamSlot })
    }

    // ── Svět 1 ──

    @Test fun craftingNeedsFragmentAndMatchingBerry() {
        assertEquals(mapOf("energy_fragment" to 1, "berry_blue" to 1), Crafting.recipe(Makroball.PROTEIN))
        assertEquals(2, Crafting.maxCraftable(Makroball.MAKRO, mapOf("energy_fragment" to 3, "berry_green" to 2)))
        assertEquals(0, Crafting.maxCraftable(Makroball.KREATIN, mapOf("energy_fragment" to 3, "berry_green" to 2)))
        assertTrue(Crafting.baseXp(Makroball.KREATIN) > Crafting.baseXp(Makroball.MAKRO))
    }

    @Test fun rarerBerriesTakeLongerAndGiveMore() {
        val b = Berry.entries
        for (i in 1 until b.size) {
            assertTrue(b[i].growSeconds > b[i - 1].growSeconds)
            assertTrue(b[i].harvestXp > b[i - 1].harvestXp)
            assertTrue(b[i].seedPrice > b[i - 1].seedPrice)
        }
        assertEquals(Berry.BLUE, Berry.forBall(Makroball.PROTEIN))
    }

    @Test fun dropsOnlySeedsFromGrass() {
        val rng = Random(7)
        repeat(500) { assertTrue(Drops.roll("IGNAR", 5, caught = false, rng = rng).none { it.itemId.startsWith("seed_") }) }
        val seeds = (1..4000).flatMap { Drops.roll("FLORI", 5, caught = false, rng = rng) }.filter { it.itemId.startsWith("seed_") }
        assertTrue(seeds.size in 850..1150)
        val black = seeds.count { it.itemId == "seed_black" }
        val green = seeds.count { it.itemId == "seed_green" }
        assertTrue("$black < $green", black < green)
        assertEquals(Berry.BLACK, Drops.seedTier(0.01))
        assertEquals(Berry.BLUE, Drops.seedTier(0.2))
        assertEquals(Berry.GREEN, Drops.seedTier(0.9))
        assertEquals(0.6, Drops.fragmentChance(40, false), 1e-9)
    }

    @Test fun catchXpGrowsWithLevel() {
        assertTrue(CatchRules.baseXp(10) > CatchRules.baseXp(2))
        val s = SkillState()
        assertEquals(44, s.gain(Skill.CATCHING, CatchRules.baseXp(4), CatchRules.multipliers(true)))
    }

    @Test fun gardenEncodingRoundTrip() {
        val now = Garden.EPOCH + 1_234_567
        val q = Garden.encode(Berry.BLACK, now)
        assertTrue(q > 0)
        val p = Garden.decode(q)
        assertEquals(Berry.BLACK, p.berry); assertEquals(now, p.plantedAt)
        assertTrue(Garden.decode(0).isEmpty)
        // Do roku 2030 se vejde do Int
        val far = Garden.encode(Berry.BLUE, Garden.EPOCH + 4L * 365 * 24 * 3600)
        assertTrue(far > 0)
    }

    @Test fun gardenTimer() {
        val t0 = Garden.EPOCH + 1000
        val p = Garden.decode(Garden.encode(Berry.GREEN, t0))
        assertEquals(900L, Garden.remaining(p, t0, 0.0))
        assertFalse(Garden.isReady(p, t0 + 899, 0.0))
        assertTrue(Garden.isReady(p, t0 + 900, 0.0))
        assertEquals(765L, Garden.growSeconds(Berry.GREEN, 0.15))
        assertEquals(0.5f, Garden.fraction(p, t0 + 450, 0.0), 0.001f)
        assertEquals("14:59", Garden.clock(899))
        assertEquals("1:00:00", Garden.clock(3600))
        assertTrue(Garden.isOpen(1, 2)); assertFalse(Garden.isOpen(2, 2)); assertTrue(Garden.isOpen(3, 4))
    }

    @Test fun teamRules() {
        assertEquals(listOf(3, 5), Team.parse("3, 5,x,3,-1"))
        assertEquals(listOf(7, 3), Team.normalize(listOf(3, 5, 7), active = 7, slots = 2))
        assertEquals(listOf(3), Team.normalize(listOf(3, 5), active = null, slots = 1))
        assertEquals(listOf(5), Team.normalize(listOf(3, 5), active = null, slots = 2, existing = setOf(5)))
        assertEquals(Team.Result.Full, Team.add(listOf(1), 2, 1))
        assertEquals(Team.Result.Ok(listOf(1, 2)), Team.add(listOf(1), 2, 2))
        assertEquals(listOf(2, 1), Team.makeActive(listOf(1, 2), 2, 2))
        assertEquals(listOf(9, 1), Team.makeActive(listOf(1, 2), 9, 2))
        assertEquals("1,2", Team.format(listOf(1, 2)))
    }
}
