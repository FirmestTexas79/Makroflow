package cz.uhk.macroflow.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class CalendarWeekTest {

    private val today = LocalDate.of(2026, 9, 25)   // pátek

    @Test
    fun weekStartsOnMonday() {
        val days = CalendarWeek.days(today)
        assertEquals(7, days.size)
        assertEquals(LocalDate.of(2026, 9, 21), days.first())
        assertEquals(DayOfWeek.SUNDAY, days.last().dayOfWeek)
        assertEquals(LocalDate.of(2026, 9, 21), CalendarWeek.monday(LocalDate.of(2026, 9, 27)))
    }

    @Test
    fun labels() {
        assertEquals("21.–27. září", CalendarWeek.label(today, today))
        assertEquals("28. září – 4. října", CalendarWeek.label(LocalDate.of(2026, 9, 30), today))
        assertEquals("28. prosince – 3. ledna 2027", CalendarWeek.label(LocalDate.of(2026, 12, 30), today))
    }

    @Test
    fun cannotGoPastCurrentWeek() {
        assertFalse(CalendarWeek.canGoForward(today, today))
        assertTrue(CalendarWeek.canGoForward(today.minusDays(7), today))
    }
}
