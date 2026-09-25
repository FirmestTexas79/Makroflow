package cz.uhk.macroflow.common

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class RemindersTest {

    private val day = LocalDate.of(2026, 9, 25)

    @Test
    fun dailyTriggerIsTodayOrTomorrow() {
        assertEquals(day.atTime(8, 0), ReminderSchedule.nextDaily(day.atTime(7, 59), 8 * 60))
        assertEquals(day.plusDays(1).atTime(8, 0), ReminderSchedule.nextDaily(day.atTime(8, 0), 8 * 60))
        assertEquals(day.atTime(20, 30), ReminderSchedule.nextDaily(day.atTime(10, 0), 20 * 60 + 30))
    }

    @Test
    fun waterSlotsNeverInThePast() {
        assertEquals(day.atTime(9, 0), ReminderSchedule.nextWaterSlot(day.atTime(6, 0)))
        assertEquals(day.atTime(11, 0), ReminderSchedule.nextWaterSlot(day.atTime(10, 57)))
        assertEquals(day.atTime(13, 0), ReminderSchedule.nextWaterSlot(day.atTime(11, 0)))
        assertEquals(day.plusDays(1).atTime(9, 0), ReminderSchedule.nextWaterSlot(day.atTime(21, 5)))
    }

    @Test
    fun streakCountsConsecutiveDaysEndingYesterday() {
        val days = setOf(day.minusDays(1), day.minusDays(2), day.minusDays(3), day.minusDays(10))
        assertEquals(3, ReminderSchedule.streakAtRisk(days, day))
        assertEquals(0, ReminderSchedule.streakAtRisk(setOf(day.minusDays(2)), day))
        // Dnešní check-in sérii „v ohrožení“ nemění
        assertEquals(3, ReminderSchedule.streakAtRisk(days + day, day))
    }

    @Test
    fun formatting() {
        assertEquals("8:05", ReminderSchedule.format(8 * 60 + 5))
        assertEquals("20:00", ReminderSchedule.format(20 * 60))
    }

    @Test
    fun defaultsMatchPreviousBehaviour() {
        assertEquals(8 * 60, Reminder.MORNING.defaultMinutes)
        assertEquals(20 * 60, Reminder.EVENING.defaultMinutes)
        assertEquals(21 * 60, Reminder.STREAK.defaultMinutes)
        assertEquals(null, Reminder.WORKOUT.defaultMinutes)
    }
}
