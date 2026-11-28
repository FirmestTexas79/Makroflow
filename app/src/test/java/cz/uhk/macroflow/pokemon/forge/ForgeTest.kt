package cz.uhk.macroflow.pokemon.forge

import cz.uhk.macroflow.pokemon.MakromonType
import cz.uhk.macroflow.pokemon.forge.Forge.Up
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForgeTest {
    private fun st(vararg l: Pair<Up, Int>, coals: Double = 0.0, reborns: Int = 0) =
        Forge.State(coals, Up.entries.map { u -> l.firstOrNull { it.first == u }?.second ?: 0 }, reborns)

    @Test fun `sleva podle vzorce s klesajicim ucinkem`() {
        assertEquals(0.9091, Forge.discountMult(1), 1e-4)
        assertEquals(0.5, Forge.discountMult(10), 1e-9)
        assertEquals(33.33, Forge.discountPct(5), 0.01)
        val base = Forge.cost(st(), Up.AUTO)
        assertEquals(base * 0.5, Forge.cost(st(Up.DISCOUNT to 10), Up.AUTO), 1e-9)
    }

    @Test fun `kupovani a nekonecne urovne`() {
        val s = Forge.buy(st(coals = 100.0), Up.BELLOWS)!!
        assertEquals(1, s.lvl(Up.BELLOWS)); assertEquals(90.0, s.coals, 1e-9)
        assertNull(Forge.buy(st(coals = 5.0), Up.BELLOWS))
        val (m, n) = Forge.buyMax(st(coals = 1e6), Up.BELLOWS)
        assertTrue(n > 40); assertTrue(m.coals < Forge.cost(m, Up.BELLOWS))
    }

    @Test fun `vydelek, jiskra, kroky a spanek`() {
        val life = Forge.Life(steps = 10_000, sleepHours = 8.0)
        assertEquals(1.0, Forge.perTap(st(), Forge.Life()), 1e-9)
        assertEquals(2.0, Forge.stepsMult(Int.MAX_VALUE / 2, 10_000), 1e-6)
        assertEquals(1.0, Forge.stepsMult(0, 10_000), 1e-9)
        assertEquals(Forge.stepsMult(10, 20_000), Forge.stepsMult(10, 50_000), 1e-9)          // strop 20 000 kroků
        assertEquals(1.5, Forge.sleepMult(10, 8.0), 1e-9)
        assertEquals(9.0, Forge.perSecond(st(Up.AUTO to 1, Up.HEARTH to 1), Forge.Life()), 1e-9)
        assertTrue(Forge.perTap(st(Up.STEPS to 5, Up.SLEEP to 5), life) > Forge.perTap(st(Up.STEPS to 5, Up.SLEEP to 5), Forge.Life()))
        assertTrue(Forge.critChance(1000) < 1.0)
        assertEquals(0.1, Forge.offlineFraction(0), 1e-9)
        val day = 24 * 3_600_000L
        assertEquals(Forge.offlineGain(st(Up.AUTO to 1), Forge.Life(), day), Forge.offlineGain(st(Up.AUTO to 1), Forge.Life(), 3 * day), 1e-9)
    }

    @Test fun `pretaveni a artefakty`() {
        val s = st(Up.BELLOWS to 50, coals = 1e7)
        val r = Forge.reborn(s.copy(passives = mapOf("pas" to 1)))!!
        assertEquals(1, r.reborns); assertEquals(0.0, r.coals, 0.0); assertEquals(0, r.lvl(Up.BELLOWS))
        assertEquals(mapOf("pas" to 1), r.passives)                                   // pasivní bonusy zůstávají
        assertNull(Forge.reborn(st(coals = 9e6)))
        assertEquals(10.0, Forge.perTap(r, Forge.Life()), 1e-9)                       // Srdce výhně ×10
        assertEquals(1e9, Forge.rebornCost(r)!!, 0.0)
        assertNull(Forge.rebornCost(st(reborns = 3)))
        val hammer = st(Up.BELLOWS to 10, reborns = 3)
        assertEquals(Forge.cost(st(Up.BELLOWS to 10), Up.AUTO) * Math.pow(0.99, 10.0), Forge.cost(hammer, Up.AUTO), 1e-9)
    }

    @Test fun `pasivni bonusy, kovadlina a stropy`() {
        val p = Forge.State(passives = mapOf("pas" to 1, "kaleni" to 5, "magma" to 1, "sit" to 1))
        val b = Forge.bonuses(p)
        assertEquals(0.15, b.defense, 1e-9); assertEquals(0.05, b.escape, 1e-9)
        assertEquals(0.85, b.damageMult(MakromonType.FIRE), 1e-9); assertEquals(1.0, b.damageMult(MakromonType.WATER), 1e-9)
        val b2 = Forge.bonuses(p.copy(reborns = 2))
        assertEquals(0.30, b2.defense, 1e-9); assertEquals(0.70, b2.damageMult(MakromonType.FIRE), 1e-9)
        assertEquals(50_000.0 * 4 * 4, Forge.passiveCoals(Forge.State(passives = mapOf("kaleni" to 2)), Forge.Passive.TEMPER), 1e-6)
    }

    @Test fun `odemykani vylepseni podle vydelanych uhliku`() {
        fun earned(v: Double) = Forge.State(coals = 0.0, earned = v)
        assertTrue(Forge.unlocked(earned(0.0), Up.BELLOWS))
        Up.entries.drop(1).forEach { assertTrue(!Forge.unlocked(earned(0.0), it)) }           // na začátku jen Silnější měch
        assertTrue(!Forge.unlocked(earned(99.0), Up.SPARK)); assertTrue(Forge.unlocked(earned(100.0), Up.SPARK))
        assertTrue(!Forge.unlocked(earned(999.0), Up.DISCOUNT)); assertTrue(Forge.unlocked(earned(1_000.0), Up.DISCOUNT))
        assertTrue(Forge.unlocked(earned(500_000.0), Up.ASH)); assertTrue(!Forge.unlocked(earned(500_000.0), Up.STEPS))
        assertTrue(Up.entries.all { Forge.unlocked(earned(2_000_000.0), it) })
        assertEquals(Up.SPARK to 100.0, Forge.nextUnlock(earned(0.0)))
        assertEquals(Up.DISCOUNT to 1_000.0, Forge.nextUnlock(earned(100.0)))
        assertNull(Forge.nextUnlock(earned(2e6)))
        assertEquals(Up.entries.size - 1, Forge.UNLOCK.size)
        assertEquals(Forge.UNLOCK.sorted(), Forge.UNLOCK)
        // zamčené vylepšení nejde koupit ani s dostatkem uhlíků; Silnější měch ano
        assertNull(Forge.buy(Forge.State(coals = 1e6), Up.SPARK))
        assertTrue(Forge.buy(Forge.State(coals = 1e6), Up.BELLOWS) != null)
        // útrata prahy nesnižuje, vydělek ano
        val s = Forge.earn(Forge.State(), 150.0)
        assertEquals(150.0, s.earned, 0.0); assertEquals(150.0, Forge.buy(s, Up.BELLOWS)!!.earned, 0.0)
        // už koupená úroveň (starší uložení) zůstává odemčená; přetavení zamkne a vynuluje vydělané
        assertTrue(Forge.unlocked(Forge.State(levels = Up.entries.map { if (it == Up.HEARTH) 1 else 0 }), Up.HEARTH))
        assertEquals(0.0, Forge.reborn(Forge.State(coals = 1e7, earned = 5e7))!!.earned, 0.0)
    }

    @Test fun `ceny jsou strme a rostou s poradim vylepseni`() {
        val first = Up.entries.map { it.base }
        assertEquals(first.sorted(), first)
        assertEquals(Up.entries.map { it.growth }.sorted(), Up.entries.map { it.growth })
        assertTrue(Up.SLEEP.base >= 10 * Up.AUTO.base)
        // každé vylepšení jde poprvé koupit nejdřív po odemčení (cena první úrovně nepřeskakuje další práh)
        Up.entries.drop(1).forEach { assertTrue("${it.label} první úroveň", it.base <= Forge.UNLOCK.last() * 2) }
    }

    @Test fun `meny – stribrne a zlate uhliky`() {
        assertEquals("12 345 uhlíků", Forge.format(12_345.9))
        assertEquals("99 999 999 uhlíků", Forge.format(99_999_999.0))
        assertEquals(Forge.Coin.SILVER to "100", Forge.amount(1e8))
        assertEquals("1 500 stříbrných", Forge.format(1.5005e9)); assertEquals("150,5 stříbrných", Forge.format(1.5055e8))
        assertEquals(Forge.Coin.GOLD to "100", Forge.amount(1e14))
    }
}
