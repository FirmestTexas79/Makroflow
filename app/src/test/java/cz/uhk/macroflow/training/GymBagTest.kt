package cz.uhk.macroflow.training

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GymBagTest {

    private val day1 = "2026-09-24"
    private val day2 = "2026-09-25"

    @Test
    fun defaultsContainUsersItems() {
        val s = GymBag.defaults(day1)
        assertTrue(s.items.map { it.label }.containsAll(listOf("Kompresní triko", "Trhačky", "Žuváky", "Pití")))
        assertEquals(0, s.packedCount)
    }

    @Test
    fun toggleAndAllPacked() {
        var s = GymBagState(listOf(BagItem("a", "Boty"), BagItem("b", "Pití")), emptySet(), day1)
        s = GymBag.toggle(s, "a")
        assertEquals(1, s.packedCount); assertFalse(s.allPacked)
        s = GymBag.toggle(s, "b")
        assertTrue(s.allPacked)
        s = GymBag.toggle(s, "a")
        assertEquals(setOf("b"), s.checked)
    }

    @Test
    fun newDayUnpacksButKeepsItems() {
        val s = GymBag.toggle(GymBag.defaults(day1), "d0")
        assertSame(s, GymBag.forDay(s, day1))
        val next = GymBag.forDay(s, day2)
        assertTrue(next.checked.isEmpty())
        assertEquals(s.items, next.items)
        assertEquals(day2, next.date)
    }

    @Test
    fun addIgnoresBlankAndDuplicates() {
        val s = GymBag.defaults(day1)
        assertSame(s, GymBag.add(s, "   ", "x"))
        assertSame(s, GymBag.add(s, "boty", "x"))
        val added = GymBag.add(s, "  Opasek\t ", "x")
        assertEquals("Opasek", added.items.last().label)
    }

    @Test
    fun removeDropsCheckedToo() {
        val s = GymBag.toggle(GymBag.defaults(day1), "d4")
        val r = GymBag.remove(s, "d4")
        assertFalse(r.items.any { it.id == "d4" })
        assertFalse("d4" in r.checked)
    }

    @Test
    fun encodeRoundTrip() {
        val items = listOf(BagItem("d0", "Kompresní triko"), BagItem("u17", "Opasek, magnézium"))
        assertEquals(items, GymBag.decodeItems(GymBag.encodeItems(items)))
        assertEquals(setOf("d0", "u17"), GymBag.decodeChecked(GymBag.encodeChecked(setOf("d0", "u17"))))
        assertTrue(GymBag.decodeChecked("").isEmpty())
    }
}
