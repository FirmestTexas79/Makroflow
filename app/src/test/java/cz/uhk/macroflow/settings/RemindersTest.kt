package cz.uhk.macroflow.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class RemindersTest {

    @Test
    fun nextTriggerTodayOrTomorrow() {
        val morning = LocalDateTime.of(2026, 9, 25, 7, 30)
        assertEquals(LocalDateTime.of(2026, 9, 25, 8, 0), ReminderTime.nextTrigger(morning, 8 * 60))
        val later = LocalDateTime.of(2026, 9, 25, 8, 0)          // přesně v čase → až zítra
        assertEquals(LocalDateTime.of(2026, 9, 26, 8, 0), ReminderTime.nextTrigger(later, 8 * 60))
        val lastDay = LocalDateTime.of(2026, 12, 31, 22, 0)      // přelom roku
        assertEquals(LocalDateTime.of(2027, 1, 1, 20, 30), ReminderTime.nextTrigger(lastDay, 20 * 60 + 30))
    }

    @Test
    fun formatting() {
        assertEquals("8:00", ReminderTime.format(8 * 60))
        assertEquals("20:05", ReminderTime.format(20 * 60 + 5))
    }

    @Test
    fun validationKeepsMorningAndEveningApart() {
        assertNull(ReminderTime.validate(Reminder.MORNING, 7 * 60))
        assertNotNull(ReminderTime.validate(Reminder.MORNING, 19 * 60))
        assertNull(ReminderTime.validate(Reminder.EVENING, 21 * 60))
        assertNotNull(ReminderTime.validate(Reminder.STREAK, 9 * 60))
        assertNull(ReminderTime.validate(Reminder.WATER, 3 * 60))
    }

    @Test
    fun onlyTimedRemindersHaveDefaults() {
        assertTrue(Reminder.MORNING.hasTime && Reminder.EVENING.hasTime && Reminder.STREAK.hasTime)
        assertTrue(!Reminder.WORKOUT.hasTime && !Reminder.WATER.hasTime)
        Reminder.entries.filter { it.hasTime }.forEach { assertNull(ReminderTime.validate(it, it.defaultMinutes!!)) }
    }

    @Test
    fun waterGoalFromModel() {
        assertEquals(3100, WaterGoal.ml(3.1))
        assertEquals(WaterGoal.FALLBACK_ML, WaterGoal.ml(null))
        assertEquals(WaterGoal.FALLBACK_ML, WaterGoal.ml(0.0))
    }
}
