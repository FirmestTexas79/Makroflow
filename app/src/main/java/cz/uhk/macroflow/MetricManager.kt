package cz.uhk.macroflow

import android.content.Context
import java.text.SimpleDateFormat
import java.util.*

object MetricsManager {

    fun saveDailyMetrics(
        context: Context,
        neck: Double,
        chest: Double,
        bicep: Double,
        forearm: Double,
        waist: Double,
        abdomen: Double,
        thigh: Double,
        calf: Double
    ) {
        val prefs = context.getSharedPreferences("BodyMetricsPrefs", Context.MODE_PRIVATE)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dateKey = sdf.format(Date())

        prefs.edit().apply {
            putFloat("${dateKey}_neck", neck.toFloat())
            putFloat("${dateKey}_chest", chest.toFloat())
            putFloat("${dateKey}_bicep", bicep.toFloat())
            putFloat("${dateKey}_forearm", forearm.toFloat())
            putFloat("${dateKey}_waist", waist.toFloat())
            putFloat("${dateKey}_abdomen", abdomen.toFloat())
            putFloat("${dateKey}_thigh", thigh.toFloat())
            putFloat("${dateKey}_calf", calf.toFloat())

            // Uložíme datum do seznamu měření pro Historii
            val dates = prefs.getStringSet("measured_dates", mutableSetOf()) ?: mutableSetOf()
            val newDates = HashSet(dates)
            newDates.add(dateKey)
            putStringSet("measured_dates", newDates)

            apply()
        }
    }

    // Pomocná funkce pro načtení dat pro konkrétní den (vhodné pro AI nebo grafy)
    fun getMetricsForDate(context: Context, dateKey: String): BodyMetrics? {
        val prefs = context.getSharedPreferences("BodyMetricsPrefs", Context.MODE_PRIVATE)
        if (!prefs.contains("${dateKey}_chest")) return null

        return BodyMetrics(
            date = System.currentTimeMillis(), // Zde by šlo parsovat dateKey na Long
            neck = prefs.getFloat("${dateKey}_neck", 0f).toDouble(),
            chest = prefs.getFloat("${dateKey}_chest", 0f).toDouble(),
            bicep = prefs.getFloat("${dateKey}_bicep", 0f).toDouble(),
            forearm = prefs.getFloat("${dateKey}_forearm", 0f).toDouble(),
            waist = prefs.getFloat("${dateKey}_waist", 0f).toDouble(),
            abdomen = prefs.getFloat("${dateKey}_abdomen", 0f).toDouble(),
            thigh = prefs.getFloat("${dateKey}_thigh", 0f).toDouble(),
            calf = prefs.getFloat("${dateKey}_calf", 0f).toDouble()
        )
    }
}