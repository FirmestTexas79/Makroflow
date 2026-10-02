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

    /** Bod za každý level do 50 (docs/adr/0059). */
    @Test fun onePointPerLevelUpTo50() {
        assertEquals(0, SkillMath.skillPointsEarned(1))
        assertEquals(1, SkillMath.skillPointsEarned(2))
        assertEquals(9, SkillMath.skillPointsEarned(10))
        assertEquals(49, SkillMath.skillPointsEarned(50))
        assertEquals(49, SkillMath.skillPointsEarned(120))
        assertEquals(4, SkillMath.nextSkillPointLevel(3))
        assertEquals(50, SkillMath.nextSkillPointLevel(49))
        assertEquals(null, SkillMath.nextSkillPointLevel(50))
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

    @Test fun treeNeedsPointsLevelAndPrerequisites() {
        val team2 = SkillTree.node("team_2")!!
        val team3 = SkillTree.node("team_3")!!
        var s = SkillState()
        assertEquals(SkillState.NodeStatus.LOW_LEVEL, s.status(team2))           // Lv 1, uzel chce Lv 2
        assertEquals(SkillState.NodeStatus.LOCKED, s.status(team3))
        s = s.withXp(Skill.CATCHING, SkillMath.totalForLevel(3).toInt())
        assertEquals(2, s.availablePoints(Skill.CATCHING))
        assertTrue(s.canUnlock(team2))
        s = s.withNode("team_2")
        assertEquals(1, s.availablePoints(Skill.CATCHING))
        assertEquals(2, s.teamSlots)
        assertEquals(SkillState.NodeStatus.MAXED, s.status(team2))
        assertEquals(SkillState.NodeStatus.LOW_LEVEL, s.status(team3))           // Trojice až na Lv 5
        s = s.withXp(Skill.CATCHING, SkillMath.totalForLevel(5).toInt())
        assertTrue(s.canUnlock(team3))
        // Body jiné dovednosti se nepočítají
        assertEquals(0, SkillState().withXp(Skill.CRAFTING, 10_000).availablePoints(Skill.CATCHING))
    }

    /** Úrovně: XP I 5×5 %, II 5×10 %, III 5×25 % – další stupeň až po maximu předchozího. */
    @Test fun xpLineRanksStack() {
        val lv = SkillMath.totalForLevel(50).toInt()
        var s = SkillState().withXp(Skill.LOGGING, lv)
        val xp1 = SkillTree.node("log_xp")!!; val xp2 = SkillTree.node("log_xp2")!!; val xp3 = SkillTree.node("log_xp3")!!
        repeat(4) { s = s.withNode("log_xp") }
        assertEquals(SkillState.NodeStatus.LOCKED, s.status(xp2))
        assertEquals(1.20, s.xpMultiplier(Skill.LOGGING), 1e-9)
        s = s.withNode("log_xp")
        assertEquals(SkillState.NodeStatus.MAXED, s.status(xp1))
        assertTrue(s.canUnlock(xp2))
        repeat(5) { s = s.withNode("log_xp2") }
        repeat(5) { s = s.withNode("log_xp3") }
        assertEquals(SkillState.NodeStatus.MAXED, s.status(xp3))
        assertEquals(3.0, s.xpMultiplier(Skill.LOGGING), 1e-9)                    // +25 % +50 % +125 %
        assertEquals(5 + 5 + 10, s.spentPoints(Skill.LOGGING))
    }

    @Test fun effectsFromTree() {
        val all = SkillState(ranks = SkillTree.NODES.associate { it.id to it.maxRank })
        assertEquals(6, all.teamSlots)
        assertEquals(4, all.plotsOpen)
        assertTrue(all.basicEquipment)
        // chytání: XP I–III 200 % + Učitel 10 % + vrchol Výroby 10 % + Návnada z hmyzu 15 %
        assertEquals(1 + 2.0 + 0.10 + 0.10 + 0.15, all.xpMultiplier(Skill.CATCHING), 1e-9)
        assertEquals(0.25 + 0.35 + 0.10, all.growthSpeedup, 1e-9)
        assertEquals(1 + 0.25 + 0.50 + 0.25, all.dropRate, 1e-9)
        assertEquals(12 + 12 + 12, all.afkCapHours(Skill.MINING))
        // efektivita krumpáče: I 50 % + II 100 % + vrchol 50 % + Nástrojář z Výroby 20 %
        assertEquals(0.5 + 1.0 + 0.5 + 0.2, all.efficiencyBonus(Skill.MINING), 1e-9)
        assertEquals(0.0, all.efficiencyBonus(Skill.CRAFTING), 1e-9)
        val none = SkillState()
        assertEquals(1, none.teamSlots)
        assertEquals(2, none.plotsOpen)
        assertFalse(none.basicEquipment)
        assertEquals(1.0, none.dropRate, 1e-9)
    }

    /** Stará uložená hra: uzel s množstvím 1 = úroveň 1, body nikdy záporné. */
    @Test fun oldSavesKeepTheirNodes() {
        val s = SkillState(xp = mapOf(Skill.CATCHING to SkillMath.totalForLevel(3)),
            ranks = mapOf("team_2" to 1, "team_3" to 1, "team_4" to 1))
        assertEquals(4, s.teamSlots)
        assertEquals(0, s.availablePoints(Skill.CATCHING))                        // utraceno 6, máš 2 → 0, ne −4
    }

    @Test fun treeIsConsistent() {
        val ids = SkillTree.NODES.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        SkillTree.NODES.forEach { n ->
            n.requires?.let { r -> assertTrue("${n.id} vyžaduje uzel jiné dovednosti", n.skill == SkillTree.node(r)!!.skill) }
            assertTrue(n.id, n.maxRank >= 1 && n.cost >= 1 && n.minLevel in 1..SkillMath.POINT_CAP)
            val need = SkillState().needed(n)
            n.requires?.let { r -> assertTrue(n.id, need in 1..SkillTree.node(r)!!.maxRank) }
            assertTrue(n.id, n.description.isNotBlank() && SkillTree.effectText(n.effect, 1, n.skill).isNotBlank())
        }
        assertEquals(5, SkillTree.NODES.count { it.effect == SkillTree.Effect.TeamSlot })
    }

    /**
     * Do levelu 50 je bodů méně, než strom unese (49 bodů) – hráč volí, co vylepší.
     * A všechno se dá koupit v pořadí, ve kterém to strom dovolí, ještě v levelu 50.
     */
    @Test fun everyTreeOffersMoreThanFiftyLevelsOfPoints() {
        Skill.entries.forEach { sk ->
            val total = SkillTree.of(sk).sumOf { it.totalCost }
            assertTrue("${sk.name}: $total", total in 50..80)
            // s nekonečnem bodů jde odemknout všechno (žádný uzel nevisí na nesplnitelné podmínce)
            var s = SkillState(xp = mapOf(sk to SkillMath.totalForLevel(50)))
            val ranksBefore = { s.ranks.values.sum() }
            var progress = true
            while (progress) {
                progress = false
                SkillTree.of(sk).forEach { n ->
                    val st = s.status(n)
                    if (st == SkillState.NodeStatus.AVAILABLE || st == SkillState.NodeStatus.NO_POINTS) { s = s.withNode(n.id); progress = true }
                }
            }
            assertTrue(sk.name, SkillTree.of(sk).all { s.rank(it.id) == it.maxRank })
            assertTrue(ranksBefore() > 0)
        }
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
