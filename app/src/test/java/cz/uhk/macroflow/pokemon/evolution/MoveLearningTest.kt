package cz.uhk.macroflow.pokemon.evolution

import cz.uhk.macroflow.pokemon.MakromonType
import cz.uhk.macroflow.pokemon.Move
import cz.uhk.macroflow.pokemon.status.EffectKind
import cz.uhk.macroflow.pokemon.status.MoveEffect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Učení útoku po evoluci – hráč vždy volí sám (docs/adr/0064). */
class MoveLearningTest {

    private val four = listOf("TACKLE", "BITE", "GROWL", "WATER GUN")

    @Test fun situations() {
        assertEquals(MoveLearning.Situation.ALREADY_KNOWN, MoveLearning.situation(four, "bite"))
        assertEquals(MoveLearning.Situation.FREE_SLOT, MoveLearning.situation(listOf("TACKLE"), "BITE"))
        assertEquals(MoveLearning.Situation.MUST_REPLACE, MoveLearning.situation(four, "HYDRO PUMP"))
    }

    @Test fun learnOnlyIntoAFreeSlot() {
        assertEquals(listOf("TACKLE", "BITE"), MoveLearning.learn(listOf("TACKLE"), "BITE"))
        assertEquals(four, MoveLearning.learn(four, "HYDRO PUMP"))                 // plno: nic se nestane samo
        assertEquals(listOf("TACKLE"), MoveLearning.learn(listOf("TACKLE"), "TACKLE"))
    }

    @Test fun replaceKeepsOrder() {
        assertEquals(listOf("TACKLE", "HYDRO PUMP", "GROWL", "WATER GUN"), MoveLearning.replace(four, 1, "HYDRO PUMP"))
        assertEquals(four, MoveLearning.replace(four, 0, "GROWL"))                 // už ho umí
        assertTrue(runCatching { MoveLearning.replace(four, 4, "X") }.isFailure)
    }

    @Test fun moveTexts() {
        assertEquals("ZEMĚ", MoveLearning.typeLabel(MakromonType.GROUND))
        val sleep = Move("SLEEP POWDER", MakromonType.GRASS, 0, 75, 15, effect = MoveEffect(EffectKind.SLEEP))
        assertEquals("Uspí soupeře.", MoveLearning.effectText(sleep))
        val burn = Move("EMBER", MakromonType.FIRE, 40, 100, 25, effect = MoveEffect(EffectKind.BURN, 10))
        assertEquals("Popálí soupeře (10 %).", MoveLearning.effectText(burn))
        assertEquals("", MoveLearning.effectText(Move("TACKLE", MakromonType.NORMAL, 40, 100, 35)))
        MakromonType.entries.forEach { assertTrue(MoveLearning.typeLabel(it).isNotBlank()) }
    }
}
