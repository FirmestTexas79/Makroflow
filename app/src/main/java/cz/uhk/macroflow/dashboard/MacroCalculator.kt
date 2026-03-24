package cz.uhk.macroflow.dashboard

import android.content.Context
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.data.UserProfileEntity
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.*

object MacroCalculator {

    fun calculate(context: Context): MacroResult {
        return calculateForDate(context, Date())
    }

    fun calculateForDate(context: Context, date: Date): MacroResult {
        val trainingPrefs = context.getSharedPreferences("TrainingPrefs", Context.MODE_PRIVATE)

        val sdf = SimpleDateFormat("EEEE", Locale.ENGLISH)
        val dayName = sdf.format(date)
        val trainingType = trainingPrefs.getString("type_$dayName", "rest")?.lowercase() ?: "rest"

        // Načtení profilu z DB – runBlocking je zde záměrné, MacroCalculator je volán
        // ze synchronních kontextů (HistoryFragment, atd.). Volání je rychlé (lokální DB).
        val profile: UserProfileEntity? = runBlocking {
            AppDatabase.Companion.getDatabase(context).userProfileDao().getProfileSync()
        }

        // Fallback na SharedPrefs pro případ, že DB profil ještě nebyl vytvořen
        // (první spuštění po migraci)
        val legacyPrefs = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)

        val weight = profile?.weight
            ?: legacyPrefs.getString("weightAkt", "83")?.toDoubleOrNull()
            ?: 83.0
        val height = profile?.height
            ?: legacyPrefs.getString("height", "175")?.toDoubleOrNull()
            ?: 175.0
        val age = profile?.age
            ?: legacyPrefs.getString("age", "22")?.toIntOrNull()
            ?: 22
        val gender = profile?.gender
            ?: legacyPrefs.getString("gender", "male")?.lowercase()
            ?: "male"
        val activityMultiplier = profile?.activityMultiplier
            ?: legacyPrefs.getFloat("multiplier", 1.2f)

        // 1. Výpočet BMR (Mifflin-St Jeor)
        var bmr = (10 * weight) + (6.25 * height) - (5 * age)
        if (gender == "female") bmr -= 161.0 else bmr += 5.0

        // 2. TDEE
        val tdee = bmr * activityMultiplier * 1.10

        // 3. Dynamické koeficienty podle tréninku
        val activityBonus = (activityMultiplier - 1.2) * 0.4
        val ageFactor = if (age > 40) 0.1 else 0.0

        val (baseProt, baseFat, extraCal) = when {
            trainingType.contains("legs") ->
                if (gender == "female") Triple(2.0, 0.9, 450.0) else Triple(2.4, 0.8, 600.0)
            trainingType.contains("pull") ->
                if (gender == "female") Triple(1.8, 0.8, 250.0) else Triple(2.2, 0.8, 350.0)
            trainingType.contains("push") ->
                if (gender == "female") Triple(1.8, 0.8, 200.0) else Triple(2.2, 0.8, 300.0)
            else ->
                if (gender == "female") Triple(1.7, 1.0, 0.0) else Triple(2.0, 1.0, 0.0)
        }

        val finalProtPerKg = baseProt + activityBonus + ageFactor
        val finalFatPerKg = baseFat + (activityBonus * 0.2)

        val protein = weight * finalProtPerKg
        val fat = weight * finalFatPerKg
        val totalCalories = tdee + extraCal
        val carbs = (totalCalories - (protein * 4) - (fat * 9)) / 4

        val waterTotal = (weight * 0.04) + (if (trainingType.contains("legs")) 0.5 else 0.2)

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