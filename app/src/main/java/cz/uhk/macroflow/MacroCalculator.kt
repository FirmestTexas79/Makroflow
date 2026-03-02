package cz.uhk.macroflow


import android.content.Context
import java.util.*
import java.text.SimpleDateFormat

object MacroCalculator {
    // Původní funkce pro Dashboard (používá dnešek)
    fun calculate(context: Context): MacroResult {
        return calculateForDate(context, Date())
    }

    // NOVÁ FUNKCE: Přijímá konkrétní datum
    fun calculateForDate(context: Context, date: Date): MacroResult {
        val userPrefs = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        val trainingPrefs = context.getSharedPreferences("TrainingPrefs", Context.MODE_PRIVATE)

        // TADY JE TA ZMĚNA: Formátujeme den z předaného data, ne z "teď"
        val sdf = SimpleDateFormat("EEEE", Locale.ENGLISH)
        val dayName = sdf.format(date)
        val trainingType = trainingPrefs.getString("type_$dayName", "rest")?.lowercase() ?: "rest"

        // ... zbytek tvého kódu zůstává stejný ...
        val weight = userPrefs.getString("weightAkt", "83")?.toDoubleOrNull() ?: 83.0
        val height = userPrefs.getString("height", "175")?.toDoubleOrNull() ?: 175.0
        val age = userPrefs.getString("age", "22")?.toIntOrNull() ?: 22
        val gender = userPrefs.getString("gender", "male")?.lowercase() ?: "male"
        val activityMultiplier = userPrefs.getFloat("multiplier", 1.2f).toDouble()

        var bmr = (10 * weight) + (6.25 * height) - (5 * age)
        if (gender == "female") bmr -= 161.0 else bmr += 5.0
        val tdee = bmr * activityMultiplier * 1.10

        val (protPerKg, fatPerKg, extraCal) = when {
            trainingType.contains("legs") -> if (gender == "female") Triple(2.0, 0.9, 450.0) else Triple(2.4, 0.8, 600.0)
            trainingType.contains("pull") -> if (gender == "female") Triple(1.8, 0.8, 250.0) else Triple(2.2, 0.8, 350.0)
            trainingType.contains("push") -> if (gender == "female") Triple(1.8, 0.8, 200.0) else Triple(2.2, 0.8, 300.0)
            else -> if (gender == "female") Triple(1.7, 1.0, 0.0) else Triple(2.0, 1.0, 0.0)
        }

        val protein = weight * protPerKg
        val fat = weight * fatPerKg
        val totalCalories = tdee + extraCal
        val carbs = (totalCalories - (protein * 4) - (fat * 9)) / 4
        val waterTotal = (weight * 0.04) + (if(trainingType == "legs") 0.4 else 0.0) // zkráceno pro přehlednost

        return MacroResult(totalCalories, protein, carbs, fat, waterTotal, trainingType.uppercase())
    }
}

data class MacroResult(
    val calories: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val water: Double,
    val trainingType: String
)