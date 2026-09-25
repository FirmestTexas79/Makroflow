package cz.uhk.macroflow.nutrition

import cz.uhk.macroflow.nutrition.MealRepeat.Item
import cz.uhk.macroflow.nutrition.MealRepeat.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MealRepeatTest {

    private fun item(name: String, time: String, kcal: Int = 100, p: Float = 10f) =
        Item(name, p, 5f, 2f, kcal, kcal * 4.184f, 1f, time)

    private val day = listOf(
        item("Vločky", "07:40", 350, 12f), item("Whey", "07:55", 120, 24f), item("Banán", "08:20", 105, 1f),
        item("Kuře s rýží", "12:30", 650, 45f),
        item("Tvaroh", "15:10", 180, 25f),
        item("Losos", "19:00", 520, 40f), item("Brambory", "19:05", 200, 4f),
        item("Kasein", "22:45", 120, 24f)
    )

    @Test
    fun groupsByTimeGapAndNamesBySlot() {
        val meals = MealRepeat.meals(day)
        assertEquals(listOf("Snídaně", "Oběd", "Odpolední svačina", "Večeře", "Pozdní jídlo"), meals.map { it.label })
        assertEquals(3, meals[0].items.size)
        assertEquals("07:40", meals[0].time)
        assertEquals(575, meals[0].kcal)
        assertEquals(2, meals[3].items.size)
    }

    @Test
    fun gapOverAnHourSplitsMeal() {
        val meals = MealRepeat.meals(listOf(item("A", "12:00"), item("B", "13:00"), item("C", "14:01")))
        assertEquals(2, meals.size)
        assertEquals(listOf("Oběd", "Oběd 2"), meals.map { it.label })
    }

    @Test
    fun unsortedInputAndMissingTimes() {
        val meals = MealRepeat.meals(listOf(item("Večeře", "19:00"), item("Snídaně", "7:30"), item("?", "")))
        assertEquals(listOf("Snídaně", "Večeře", "Bez času"), meals.map { it.label })
    }

    @Test
    fun slotBoundaries() {
        assertEquals(MealRepeat.Slot.LATE, MealRepeat.slotOf(0))
        assertEquals(MealRepeat.Slot.BREAKFAST, MealRepeat.slotOf(4 * 60))
        assertEquals(MealRepeat.Slot.SNACK_AM, MealRepeat.slotOf(10 * 60 + 30))
        assertEquals(MealRepeat.Slot.LUNCH, MealRepeat.slotOf(11 * 60 + 30))
        assertEquals(MealRepeat.Slot.DINNER, MealRepeat.slotOf(20 * 60))
        assertEquals("Pozdní jídlo", MealRepeat.slotOf(23 * 60).label)
    }

    @Test
    fun relogMealUsesNowDayKeepsTimes() {
        val meal = MealRepeat.meals(day)[0].items
        assertTrue(MealRepeat.relog(meal, "09:15", keepTimes = false).all { it.time == "09:15" })
        assertEquals(day.map { it.time }, MealRepeat.relog(day, "09:15", keepTimes = true).map { it.time })
        // makra se nemění
        assertEquals(day.map { it.calories }, MealRepeat.relog(day, "09:15", true).map { it.calories })
    }

    @Test
    fun encodeDecodeRoundTrip() {
        val tricky = day + item("Pizza \u001F \u001E \"šunková\", 1/2", "20:00")
        val back = MealRepeat.decode(MealRepeat.encode(tricky))
        assertEquals(tricky.size, back.size)
        assertEquals(day, back.take(day.size))
        assertEquals("Pizza   \"šunková\", 1/2", back.last().name)
        assertEquals(emptyList<Item>(), MealRepeat.decode(""))
        assertEquals(1, MealRepeat.decode("broken\u001Erow" + "\u001E" + MealRepeat.encode(listOf(day[0]))).size)
    }

    @Test
    fun namesAndDescription() {
        assertEquals("Oběd", MealRepeat.defaultName(Kind.MEAL, "Oběd 2", 24, 9))
        assertEquals("Den 24. 9.", MealRepeat.defaultName(Kind.DAY, null, 24, 9))
        assertEquals("3 položky · 575 kcal · 37 g B", MealRepeat.describe(MealRepeat.meals(day)[0].items))
        assertEquals("1 položka · 650 kcal · 45 g B", MealRepeat.describe(listOf(day[3])))
        assertEquals("8 položek · 2245 kcal · 175 g B", MealRepeat.describe(day))
    }
}
