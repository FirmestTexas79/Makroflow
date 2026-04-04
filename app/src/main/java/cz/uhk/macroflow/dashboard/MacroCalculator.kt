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

        val strengthType = trainingPrefs.getString("type_$dayName", "rest")?.lowercase() ?: "rest"
        val kardioType = trainingPrefs.getString("kardio_type_$dayName", "rest")?.lowercase() ?: "rest"

        val profile: UserProfileEntity? = runBlocking {
            AppDatabase.getDatabase(context).userProfileDao().getProfileSync()
        }

        val weight = profile?.weight ?: 75.0
        val height = profile?.height ?: 175.0
        val age = profile?.age ?: 22
        val gender = profile?.gender ?: "male"
        val isElite = profile?.isEliteMode ?: false
        val wrist = profile?.lastWristMeasurement ?: 15.0
        val bodyFat = profile?.bodyFatPercentage ?: 12.0
        val goal = profile?.goal?.uppercase() ?: "MAINTAIN"
        val dietType = (profile?.dietType ?: "BALANCED").normalizeDiet()

        // 1. BAZÁLNÍ METABOLISMUS (BMR) - Katch-McArdle je pro 13% BF přesný
        val bmr = if (isElite) {
            EliteMetabolicEngine.calculateEliteBMR(weight, bodyFat)
        } else {
            var miffBmr = (10 * weight) + (6.25 * height) - (5 * age)
            if (gender == "female") miffBmr -= 161.0 else miffBmr += 5.0
            miffBmr
        }

        // 2. TDEE (PASIVNÍ VÝDEJ)
        // OPRAVA: U Elite módu musíme být s multiplierem opatrnější, pokud tréninky sčítáme extra
        val activityMultiplier = if (isElite) 1.2 else (profile?.activityMultiplier ?: 1.2f).toDouble()

        // Aplikace TEF (Termický efekt diety) - upraveno, aby neházelo extrémy
        val dietModifier = EliteMetabolicEngine.getDietaryTEFModifier(dietType)
        val maintenanceKcal = (bmr * activityMultiplier) * (1.0 + dietModifier)

        // 3. APLIKACE CÍLE
        var targetCalories = when (goal) {
            "CUT" -> maintenanceKcal * 0.85
            "BULK" -> maintenanceKcal + 250.0 // Čistý bulk = +250 kcal, ne procentuální snowball
            else -> maintenanceKcal
        }

        // 4. VÝPOČET TRÉNINKOVÉHO VÝDEJE
        var exerciseExpenditure = 0.0

        // Silový trénink
        exerciseExpenditure += when {
            strengthType.contains("legs") -> weight * 4.5  // Sníženo z 5.8
            strengthType.contains("pull") -> weight * 3.0  // Sníženo z 3.8
            strengthType.contains("push") -> weight * 2.5  // Sníženo z 3.3
            else -> 0.0
        }

        // Kardio (Švihadlo/Běh)
        if (kardioType != "rest") {
            val duration = trainingPrefs.getString("kardio_duration_$dayName", "0")?.toDoubleOrNull() ?: 0.0

            exerciseExpenditure += when (kardioType) {
                "run" -> {
                    val speed = trainingPrefs.getString("kardio_speed_$dayName", "8.0")?.toDoubleOrNull() ?: 8.0
                    (0.95 * weight) * (duration / 60.0) * speed // Realističtější běh
                }
                "rope" -> {
                    val jumps = trainingPrefs.getString("kardio_jumps_$dayName", "0")?.toDoubleOrNull() ?: 0.0
                    if (jumps > 0) {
                        // OPRAVA: 6000 skoků = cca 400-500 kcal pro 76kg, ne 1000.
                        (jumps * 0.07) * (weight / 75.0)
                    } else {
                        (8.0 * weight) * (duration / 60.0) // MET 8 pro lehké švihadlo
                    }
                }
                else -> 0.0
            }
        }

        // Finální přičtení tréninku (zastropováno)
        targetCalories += exerciseExpenditure

        // 5. DISTRIBUCE MAKER
        val carbSens = EliteMetabolicEngine.getCarbSensitivity(wrist, height)
        var pPerKg = if (goal == "CUT") 2.4 else 2.1 // V bulku stačí 2.1g
        var fPerKg = 0.9 // Stabilní tuky pro hormony

        // Úpravy podle diety (pokud je specifická)
        if (isElite) {
            when {
                dietType.contains("KETO") -> { pPerKg = 2.0; fPerKg = 2.0 }
                dietType.contains("HIGH_PROTEIN") -> { pPerKg = 2.7; fPerKg = 0.7 }
            }
        }

        var protein = weight * pPerKg
        var fat = weight * fPerKg

        // Zbytek do sacharidů
        var carbs = (targetCalories - (protein * 4) - (fat * 9)) / 4

        // 6. POJISTKY A INZULÍNOVÁ SENZITIVITA
        if (isElite) {
            val originalCarbs = carbs
            carbs *= carbSens // Tady ti tvých 15cm zápěstí (poměr 11.6) přidá 10% sacharidů
            val diffKcal = (originalCarbs - carbs) * 4
            fat += (diffKcal / 9)
        }

        // Finální srovnání kalorií
        val finalCalories = (protein * 4) + (carbs * 4) + (fat * 9)

        return MacroResult(
            calories = finalCalories,
            protein = protein,
            carbs = carbs,
            fat = fat,
            fiber = 0.0,
            water = (weight * 0.04) + (if (finalCalories > 3000) 1.0 else 0.0),
            trainingType = strengthType.uppercase(),
            weight = weight,
            isEliteMode = isElite
        )
    }
}