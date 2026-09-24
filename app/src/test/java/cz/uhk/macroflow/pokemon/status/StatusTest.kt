package cz.uhk.macroflow.pokemon.status

import cz.uhk.macroflow.pokemon.MakromonType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class StatusTest {

    private val rng = Random(7)

    @Test
    fun majorStatusIsExclusive() {
        val c = Condition()
        assertEquals(InflictResult.APPLIED, StatusRules.tryInflict(c, MakromonType.NORMAL, MoveEffect(EffectKind.POISON), rng))
        assertEquals(InflictResult.ALREADY, StatusRules.tryInflict(c, MakromonType.NORMAL, MoveEffect(EffectKind.SLEEP), rng))
        assertEquals(StatusKind.POISON, c.major)
        // Zmatení jde navíc k hlavnímu stavu
        assertEquals(InflictResult.APPLIED, StatusRules.tryInflict(c, MakromonType.NORMAL, MoveEffect(EffectKind.CONFUSE), rng))
        assertTrue(c.confused)
    }

    @Test
    fun typeImmunities() {
        assertEquals(InflictResult.IMMUNE, StatusRules.tryInflict(Condition(), MakromonType.FIRE, MoveEffect(EffectKind.BURN), rng))
        assertEquals(InflictResult.IMMUNE, StatusRules.tryInflict(Condition(), MakromonType.POISON, MoveEffect(EffectKind.POISON), rng))
        assertEquals(InflictResult.IMMUNE, StatusRules.tryInflict(Condition(), MakromonType.ELECTRIC, MoveEffect(EffectKind.PARALYZE), rng))
    }

    @Test
    fun chanceIsRespected() {
        val r = Random(1)
        val hits = (1..10_000).count { StatusRules.tryInflict(Condition(), MakromonType.NORMAL, MoveEffect(EffectKind.BURN, 10), r) == InflictResult.APPLIED }
        assertTrue("hits=$hits", hits in 900..1100)
    }

    @Test
    fun sleepLastsOneToThreeTurnsThenWakes() {
        repeat(200) { seed ->
            val r = Random(seed)
            val c = Condition()
            StatusRules.tryInflict(c, MakromonType.NORMAL, MoveEffect(EffectKind.SLEEP), r)
            var asleep = 0
            while (true) {
                val b = StatusRules.beforeMove(c, r) { 0 }
                if (b == BeforeMove.WokeUp) break
                assertEquals(BeforeMove.StillAsleep, b); asleep++
            }
            assertTrue(asleep in 0..2)
            assertNull(c.major)
        }
    }

    @Test
    fun paralysisSkipsAboutQuarter() {
        val r = Random(3)
        val c = Condition().apply { major = StatusKind.PARALYSIS }
        val skips = (1..10_000).count { StatusRules.beforeMove(c, r) { 0 } == BeforeMove.FullyParalyzed }
        assertTrue("skips=$skips", skips in 2300..2700)
    }

    @Test
    fun confusionEndsAndSometimesHurts() {
        val r = Random(11)
        var hurt = 0
        repeat(2000) {
            val c = Condition()
            StatusRules.tryInflict(c, MakromonType.NORMAL, MoveEffect(EffectKind.CONFUSE), r)
            var turns = 0
            while (true) {
                val b = StatusRules.beforeMove(c, r) { 5 }
                turns++
                if (b == BeforeMove.SnappedOut) break
                if (b is BeforeMove.HurtItself) { hurt++; assertEquals(5, b.damage) }
            }
            assertTrue(turns in 2..5)
            assertFalse(c.confused)
        }
        assertTrue(hurt > 0)
    }

    @Test
    fun flinchOnlyOnce() {
        val c = Condition().apply { flinched = true }
        assertEquals(BeforeMove.Flinched, StatusRules.beforeMove(c, rng) { 0 })
        assertEquals(BeforeMove.CanAct, StatusRules.beforeMove(c, rng) { 0 })
    }

    @Test
    fun residualDamageAndModifiers() {
        val p = Condition().apply { major = StatusKind.POISON }
        val b = Condition().apply { major = StatusKind.BURN }
        assertEquals(12, StatusRules.endOfTurnDamage(p, 100))
        assertEquals(6, StatusRules.endOfTurnDamage(b, 100))
        assertEquals(1, StatusRules.endOfTurnDamage(b, 10))    // aspoň 1
        assertEquals(0.5f, StatusRules.attackMultiplier(b), 0f)
        assertEquals(0.5f, StatusRules.speedMultiplier(Condition().apply { major = StatusKind.PARALYSIS }), 0f)
    }

    @Test
    fun catchBonusBySleep() {
        assertEquals(2.5f, StatusRules.catchBonus(Condition().apply { major = StatusKind.SLEEP }), 0f)
        assertEquals(1f, StatusRules.catchBonus(Condition()), 0f)
    }

    @Test
    fun medsCureOnlyWhatTheyShould() {
        val sleepy = Condition().apply { major = StatusKind.SLEEP; sleepTurns = 2 }
        assertFalse(MedItem.ELECTROLYTE.helps(sleepy))
        assertFalse(MedItem.ELECTROLYTE.apply(sleepy))
        assertTrue(MedItem.CAFFEINE.apply(sleepy))
        assertNull(sleepy.major)

        val messy = Condition().apply { major = StatusKind.BURN; confusedTurns = 3 }
        assertTrue(MedItem.MULTIVITAMIN.apply(messy))
        assertNull(messy.major); assertFalse(messy.confused)
        assertFalse(MedItem.COLD_SHOWER.helps(Condition()))
    }

    @Test
    fun medSpritesAreTwelveByTwelve() {
        MedItem.entries.forEach { m ->
            assertEquals(MedItem.SIZE * MedItem.SIZE, m.pixels.size)
            assertTrue(m.short.length <= 7)
            assertTrue(m.pixels.count { it != 0 } > 20)
        }
        assertEquals(MedItem.entries.size, MedItem.entries.map { it.id }.distinct().size)
    }
}
