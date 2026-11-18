package cz.uhk.macroflow.demo

import android.content.Context
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.data.CheckInEntity
import cz.uhk.macroflow.data.ConsumedSnackEntity
import cz.uhk.macroflow.data.StepsEntity
import cz.uhk.macroflow.data.WaterEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Ukázková data pro recenzenty a testery: 14 dní jídla, vody, kroků a check-inů.
 * Zapnout jde jen do prázdné aplikace; vypnutí smaže právě to, co demo vložilo
 * (jídla a check-iny podle značky, voda a kroky podle uložených klíčů).
 */
object DemoData {
    private const val PREFS = "demo_data"
    private const val MEAL_TAG = "DEMO"
    private const val MOOD_TAG = "__demo__"

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isActive(c: Context) = prefs(c).getBoolean("on", false)

    /** Blokující – volat z Dispatchers.IO. */
    fun isEmptyApp(c: Context): Boolean {
        val db = AppDatabase.getDatabase(c)
        return db.consumedSnackDao().getAllConsumedSync().isEmpty() && db.checkInDao().getAllCheckInsSync().isEmpty()
    }

    private val meals = listOf(
        Triple("Ovesná kaše s banánem", 18f to 62f, 9f to 520),
        Triple("Kuřecí s rýží a brokolicí", 48f to 70f, 10f to 640),
        Triple("Řecký jogurt s ořechy", 24f to 14f, 16f to 290),
        Triple("Těstoviny s tuňákem", 38f to 85f, 12f to 610)
    )
    private val times = listOf("07:45", "12:30", "16:00", "19:15")

    /** Blokující – volat z Dispatchers.IO. */
    fun enable(c: Context) {
        val db = AppDatabase.getDatabase(c)
        val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val waterTs = mutableSetOf<String>()
        val stepDates = mutableSetOf<String>()
        for (back in 13 downTo 1) {
            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -back); set(Calendar.HOUR_OF_DAY, 8) }
            val date = dayFmt.format(cal.time)
            val base = cal.timeInMillis
            meals.forEachIndexed { i, (name, ps, tf) ->
                // jemná obměna po dnech, ať grafy nejsou rovné čáry
                val k = 1f + ((back + i) % 5 - 2) * 0.04f
                val kcal = (tf.second * k).toInt()
                db.consumedSnackDao().insertConsumed(ConsumedSnackEntity(
                    timestamp = base + i * 3_600_000L + back, date = date, time = times[i], name = name,
                    p = ps.first * k, s = ps.second * k, t = tf.first * k, calories = kcal,
                    mealContext = MEAL_TAG, energyKj = kcal * 4.184f, fiber = 5f
                ))
            }
            for (j in 0 until 6) {
                val ts = base + j * 5_400_000L + back
                db.waterDao().insertWater(WaterEntity(timestamp = ts, date = date, amountMl = 350))
                waterTs += ts.toString()
            }
            db.stepsDao().insertSteps(StepsEntity(date, 6500 + ((back * 937) % 5000)))
            stepDates += date
            db.checkInDao().insertCheckIn(CheckInEntity(
                date = date, weight = 82.0 - (13 - back) * 0.08, energyLevel = 3 + back % 3,
                sleepQuality = 3 + (back + 1) % 3, hungerLevel = 2 + back % 3, mood = MOOD_TAG
            ))
        }
        prefs(c).edit().putBoolean("on", true).putStringSet("water", waterTs).putStringSet("steps", stepDates).apply()
    }

    /** Blokující – volat z Dispatchers.IO. */
    fun disable(c: Context) {
        val db = AppDatabase.getDatabase(c)
        val p = prefs(c)
        db.consumedSnackDao().deleteDemoMeals(MEAL_TAG)
        db.checkInDao().deleteDemoCheckIns(MOOD_TAG)
        p.getStringSet("water", emptySet())!!.mapNotNull { it.toLongOrNull() }.chunked(500)
            .forEach { db.waterDao().deleteByTimestamps(it) }
        db.stepsDao().deleteByDates(p.getStringSet("steps", emptySet())!!.toList())
        p.edit().clear().apply()
    }
}