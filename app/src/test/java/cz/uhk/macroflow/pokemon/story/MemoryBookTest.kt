package cz.uhk.macroflow.pokemon.story

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryBookTest {
    @Test fun nobodyGoneBeforeThirdDay() {
        assertTrue(MemoryBook.goneAfter(0).isEmpty())
        assertTrue(MemoryBook.goneAfter(2).isEmpty())
        assertEquals(listOf("corra"), MemoryBook.goneAfter(3))
        assertEquals(listOf("corra", "oswin", "pip"), MemoryBook.goneAfter(9))
    }

    @Test fun atMostFiveVanishAndKeyCharactersNever() {
        assertEquals(5, MemoryBook.goneAfter(300).size)
        val safe = setOf("bohousek", "venka", "lida", "korinek", "pell", "sabeth")
        assertTrue(MemoryBook.VANISH_ORDER.none { it in safe })
        assertTrue(MemoryBook.VANISH_ORDER.all { MemoryBook.resident(it) != null && it in MemoryBook.TRACES })
    }

    @Test fun firstNameIsFreeThenOnePerRitual() {
        assertEquals(1, MemoryBook.freeNames(0, 0))
        assertEquals(0, MemoryBook.freeNames(0, 1))
        assertEquals(3, MemoryBook.freeNames(4, 2))
        assertEquals(0, MemoryBook.freeNames(1, 5))
    }

    @Test fun bookKeepsMemoryNotPerson() {
        assertEquals(MemoryBook.Line.WRITTEN_GONE, MemoryBook.line(written = true, gone = true))
        assertEquals(MemoryBook.Line.BLANK, MemoryBook.line(written = false, gone = true))
        assertEquals(MemoryBook.Line.OPEN, MemoryBook.line(written = false, gone = false))
        assertEquals(MemoryBook.Line.WRITTEN, MemoryBook.line(written = true, gone = false))
    }

    @Test fun keysAreStoryKeys() {
        assertTrue(MemoryBook.KEYS.all { StoryProgress.isStoryKey(it) })
        assertEquals(MemoryBook.RESIDENTS.size * 2, MemoryBook.KEYS.size)
    }
}
