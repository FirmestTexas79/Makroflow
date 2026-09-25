package cz.uhk.macroflow.pokemon.daily

import cz.uhk.macroflow.pokemon.daily.DailyQuests.Facts
import cz.uhk.macroflow.pokemon.daily.DailyQuests.Group
import cz.uhk.macroflow.pokemon.daily.DailyQuests.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyQuestsTest {

    @Test
    fun threeQuestsOnePerGroupSameAllDay() {
        for (day in 20_000L..20_400L) {
            val q = DailyQuests.forDay(day)
            assertEquals(3, q.size)
            assertEquals(listOf(Group.GAME, Group.FOOD, Group.ACTIVITY), q.map { it.kind.group })
            assertEquals(listOf(0, 1, 2), q.map { it.index })
            assertEquals(q, DailyQuests.forDay(day))
            q.forEach { assertTrue("${it.kind} ${it.reward}", it.reward in 10..50) }
        }
    }

    @Test
    fun questsRotate() {
        val combos = (20_000L..20_060L).map { DailyQuests.forDay(it).map { q -> q.kind } }.toSet()
        assertTrue("kombinací: ${combos.size}", combos.size >= 15)
        val kinds = (20_000L..20_200L).flatMap { DailyQuests.forDay(it, unlockedBiomes = setOf("MEADOW", "MOUNTAINS", "CAVE")) }.map { it.kind }.toSet()
        assertEquals(Kind.entries.toSet(), kinds)
    }

    @Test
    fun biomeQuestOnlyForVisitedBiomes() {
        val biomes = (20_000L..20_400L).flatMap { DailyQuests.forDay(it) }.filter { it.kind == Kind.WIN_IN_BIOME }.map { it.param }.toSet()
        assertEquals(setOf("MEADOW"), biomes)
    }

    @Test
    fun targetsFollowPersonalGoals() {
        val t = DailyQuests.Targets(waterMl = 3100, proteinG = 180)
        val all = (20_000L..20_300L).flatMap { DailyQuests.forDay(it, t) }
        assertEquals(3000, all.first { it.kind == Kind.WATER }.target)       // zaokrouhleno na 250 ml
        assertEquals(160, all.first { it.kind == Kind.PROTEIN }.target)      // 90 % cíle, po 5 g
        assertEquals("Vypij 3 l vody", all.first { it.kind == Kind.WATER }.description)
    }

    @Test
    fun progressFromFacts() {
        val f = Facts(
            winsByType = mapOf("WATER" to 2, "FIRE" to 1), winsByBiome = mapOf("MEADOW" to 2, "CAVE_OPEN" to 1, "CAVE_MAZE" to 1),
            catches = 1, waterMl = 1250, proteinG = 90, fiberG = 30, meals = 3, steps = 9000, workoutSets = 12, checkIn = true
        )
        fun q(kind: Kind, target: Int, param: String? = null) = DailyQuests.Quest(0, kind, target, param, 20, "", "")
        assertTrue(DailyQuests.isDone(q(Kind.WIN_TYPE, 2, "WATER"), f))
        assertFalse(DailyQuests.isDone(q(Kind.WIN_TYPE, 2, "FIRE"), f))
        assertEquals(3, DailyQuests.progress(q(Kind.WIN_ANY, 5), f))
        assertEquals(2, DailyQuests.progress(q(Kind.WIN_IN_BIOME, 2, "CAVE"), f))
        assertEquals(0.5f, DailyQuests.fraction(q(Kind.WATER, 2500), f), 1e-6f)
        assertEquals("1,25 / 2,5 l", DailyQuests.progressText(q(Kind.WATER, 2500), f))
        assertEquals("8 000 / 8 000", DailyQuests.progressText(q(Kind.STEPS, 8000), f))
        assertEquals("hotovo", DailyQuests.progressText(q(Kind.CHECK_IN, 1), f))
        assertTrue(DailyQuests.isDone(q(Kind.WORKOUT_SETS, 12), f))
        assertFalse(DailyQuests.isDone(q(Kind.MEALS, 4), f))
    }
}
