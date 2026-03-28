package cz.uhk.macroflow.dashboard

import android.content.Context
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.data.UserProfileEntity
import kotlinx.coroutines.runBlocking
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.*

object MacroCalculator {

    private fun String.normalizeDiet(): String {
        val temp = Normalizer.normalize(this, Normalizer.Form.NFD)
        return temp.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .uppercase()
            .replace(" ", "_")
            .trim()
    }

    fun calculate(context: Context): MacroResult {
        return calculateForDate(context, Date())
    }

    fun calculateForDate(context: Context, date: Date): MacroResult {
        val trainingPrefs = context.getSharedPreferences("TrainingPrefs", Context.MODE_PRIVATE)
        val sdf = SimpleDateFormat("EEEE", Locale.ENGLISH)
        val dayName = sdf.format(date)
        val trainingType = trainingPrefs.getString("type_$dayName", "rest")?.lowercase() ?: "rest"

        val profile: UserProfileEntity? = runBlocking {
            AppDatabase.getDatabase(context).userProfileDao().getProfileSync()
        }

        val weight = profile?.weight ?: 75.0
        val height = profile?.height ?: 175.0
        val age = profile?.age ?: 22
        val gender = profile?.gender ?: "male"
        val activityMultiplier = (profile?.activityMultiplier ?: 1.5f).toDouble()
        val isElite = profile?.isEliteMode ?: false

        val diet = (profile?.dietType ?: "BALANCED").normalizeDiet()

        // 1. BMR
        val bmr = if (isElite) {
            EliteMetabolicEngine.calculateEliteBMR(weight, profile?.bodyFatPercentage ?: 10.0)
        } else {
            var miffBmr = (10 * weight) + (6.25 * height) - (5 * age)
            if (gender == "female") miffBmr -= 161.0 else miffBmr += 5.0
            miffBmr
        }

        // 2. TDEE + TEF + TRAINING
        var totalCalories = bmr * activityMultiplier
        totalCalories *= (1.0 + (if (isElite) EliteMetabolicEngine.getDietaryTEFModifier(diet) else 0.05))

        totalCalories += when {
            trainingType.contains("legs") -> 600.0
            trainingType.contains("pull") -> 300.0
            trainingType.contains("push") -> 250.0
            else -> 0.0
        }

        // 3. NASTAVENÍ ELITE KOEFICIENTŮ (Tvůj manual)
        var pPerKg = 2.0
        var fPerKg = 1.0
        var forceCarbLimit: Double? = null

        if (isElite) {
            when {
                diet.contains("KETO") -> {
                    pPerKg = 1.8   // 135g (Ty jsi psal, že to teď sedí)
                    fPerKg = 2.4
                    forceCarbLimit = 38.0
                }
                diet.contains("LOW") -> {
                    pPerKg = 2.4   // 180g bílkovin
                    fPerKg = 1.5   // 112g tuku (zdravý základ)
                    forceCarbLimit = 150.0
                }
                diet.contains("HIGH") -> {
                    pPerKg = 2.8   // Zvedneme na 2.8g/kg -> 210g bílkovin (Elite recovery)
                    fPerKg = 1.1   // Zvedneme na 1.1g/kg -> 82g tuku
                    // Sacharidy se automaticky dopočítají a vyjdou kolem 450-480g, což je mnohem zdravější objem
                }
                diet.contains("VEGAN") -> {
                    pPerKg = 2.4   // Kompenzace rostlinných bílkovin
                    fPerKg = 1.2   // Rostlinné tuky jsou klíčové
                    // Sacharidy vyjdou kolem 400-450g
                }
                else -> { // VYVAZENA
                    pPerKg = 2.2
                    fPerKg = 1.1
                }
            }
        }

        var protein = weight * pPerKg
        var fat = weight * fPerKg
        var carbs = (totalCalories - (protein * 4) - (fat * 9)) / 4

        // 4. KOREKCE A PŘELITÍ (Důležité pro Low Carb!)
        if (isElite) {
            forceCarbLimit?.let { limit ->
                if (carbs > limit) {
                    val extraKcal = (carbs - limit) * 4
                    carbs = limit
                    fat += (extraKcal / 9) // Zbytek energie z cukrů jde do tuků
                }
            }
            // Zápěstí ovlivní jen ty, co nejsou Keto/LowCarb
            if (forceCarbLimit == null) {
                val carbSens = EliteMetabolicEngine.getCarbSensitivity(profile?.lastWristMeasurement ?: 15.0, height)
                carbs *= carbSens
            }
        }

        val finalCalories = (protein * 4) + (carbs * 4) + (fat * 9)

        return MacroResult(
            calories = finalCalories,
            protein = protein,
            carbs = carbs,
            fat = fat,
            water = (weight * 0.04) + (if (totalCalories > 3000) 1.0 else 0.0),
            trainingType = trainingType.uppercase(),
            weight = weight,
            isEliteMode = isElite
        )
    }
}