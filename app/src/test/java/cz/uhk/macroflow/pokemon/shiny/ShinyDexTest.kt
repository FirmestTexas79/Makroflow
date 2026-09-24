package cz.uhk.macroflow.pokemon.shiny

import org.junit.Assert.assertEquals
import org.junit.Test

class ShinyDexTest {

    private val all = listOf("001", "004", "012", "019", "030")

    @Test
    fun caughtCountsAsSeen() {
        assertEquals(ShinyDex.Status.CAUGHT, ShinyDex.status("012", seen = emptySet(), caught = setOf("012")))
        assertEquals(ShinyDex.Status.SEEN, ShinyDex.status("004", seen = setOf("004"), caught = emptySet()))
        assertEquals(ShinyDex.Status.UNKNOWN, ShinyDex.status("001", seen = emptySet(), caught = emptySet()))
    }

    @Test
    fun tabsListTheRightEntriesInDexOrder() {
        val seen = setOf("019", "004"); val caught = setOf("012")
        assertEquals(listOf("004", "012", "019"), ShinyDex.visible(all, ShinyDex.Filter.SEEN, seen, caught))
        assertEquals(listOf("012"), ShinyDex.visible(all, ShinyDex.Filter.CAUGHT, seen, caught))
        assertEquals(3 to 1, ShinyDex.counts(all, seen, caught))
    }

    @Test
    fun unknownIdsOutsideTheDexAreIgnored() {
        assertEquals(0 to 0, ShinyDex.counts(all, setOf("999"), setOf("998")))
    }
}
