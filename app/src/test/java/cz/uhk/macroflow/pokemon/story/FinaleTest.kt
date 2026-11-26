package cz.uhk.macroflow.pokemon.story

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Konce: každá volba u Trpitele má svůj konec ve Středu, tajný konec chce celou paměť, příznaky jsou příběhové. */
class FinaleTest {
    @Test fun everyChoiceHasAnEnding() {
        for (e in Trpitel.Ending.entries) assertNotNull(Finale.ENDINGS[Finale.pending(e)])
        assertEquals(4, Finale.ENDINGS.size)
        Finale.ENDINGS.values.forEach { assertTrue(it.pages.size >= 5); assertTrue(it.epilogue.isNotBlank()) }
        Finale.KEYS.forEach { assertTrue(it, StoryProgress.isStoryKey(it)) }
        assertTrue(StoryProgress.isStoryKey(cz.uhk.macroflow.pokemon.cave.RootHeart.VISITED_KEY))
    }

    @Test fun secretEndingNeedsMemoryAndNamesInTheBook() {
        val all = Finale.MEMORIES.map { it.key }.toSet()
        assertTrue(Finale.secretReady({ it in all }, Finale.BOOK_NAMES))
        assertFalse(Finale.secretReady({ it in all }, Finale.BOOK_NAMES - 1))
        assertFalse(Finale.secretReady({ it in all - ForestNames.FINAL_KEY }, 5))
        assertEquals(Finale.MEMORIES.size + 1, Finale.memoriesMissing({ false }, 0).size)
    }

    @Test fun wishRevealIsOnlyInTheWishEnding() {
        val reveal = "Makromoni byli lidé"
        assertTrue(Finale.ENDINGS.getValue(Finale.End.WISH).pages.any { reveal in it })
        Finale.End.entries.filter { it != Finale.End.WISH }.forEach { e -> assertTrue(Finale.ENDINGS.getValue(e).pages.none { reveal in it }) }
    }
}
