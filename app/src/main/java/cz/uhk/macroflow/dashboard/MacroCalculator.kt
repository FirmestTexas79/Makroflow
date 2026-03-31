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
        val isElite = profile?.isEliteMode ?: false
        val wrist = profile?.lastWristMeasurement ?: 15.0
        val bodyFat = profile?.bodyFatPercentage ?: 12.0
        val goal = profile?.goal?.uppercase() ?: "MAINTAIN"

        // 1. VÝPOČET BAZÁLNÍHO METABOLISMU (BMR)
        val bmr = if (isElite) {
            // Katch-McArdle (Elite) - přesnější pro sportovce s nízkým body fat
            EliteMetabolicEngine.calculateEliteBMR(weight, bodyFat)
        } else {
            // Mifflin-St Jeor (Normal) - standardní lékařský vzorec
            var miffBmr = (10 * weight) + (6.25 * height) - (5 * age)
            if (gender == "female") miffBmr -= 161.0 else miffBmr += 5.0
            miffBmr
        }

        // 2. VÝPOČET TDEE (CELKOVÝ VÝDEJ)
        val rawMult = (profile?.activityMultiplier ?: 1.2f).toDouble()
        val activityMultiplier = if (isElite) (rawMult * 0.85).coerceAtLeast(1.2) else rawMult
        val maintenanceKcal = bmr * activityMultiplier

        // APLIKACE CÍLE (GOAL BRANCHING)
        var targetCalories = when (goal) {
            "CUT" -> maintenanceKcal * 0.85 // Zdravotně bezpečný deficit 15%
            "BULK" -> maintenanceKcal * 1.10 // Čisté nabírání (lean bulk) + 10%
            else -> maintenanceKcal // Udržování (maintenance)
        }

        // Přidání tréninkového výdeje (dynamicky podle váhy)
        targetCalories += when {
            trainingType.contains("legs") -> weight * 5.8
            trainingType.contains("pull") -> weight * 3.8
            trainingType.contains("push") -> weight * 3.3
            else -> 0.0
        }

        // 3. DISTRIBUCE MAKER (DOKTORSKÁ PŘESNOST)
        val carbSens = EliteMetabolicEngine.getCarbSensitivity(wrist, height)
        var pPerKg: Double
        var fPerKg: Double

        if (isElite) {
            // ELITE VĚTEV - Vyšší nároky na bílkoviny pro ochranu svalové hmoty
            val diet = (profile?.dietType ?: "BALANCED").normalizeDiet()
            when {
                diet.contains("KETO") -> { pPerKg = 2.1; fPerKg = 2.3 }
                diet.contains("LOW_CARB") -> { pPerKg = 2.5; fPerKg = 1.3 }
                diet.contains("HIGH_PROTEIN") -> { pPerKg = 2.9; fPerKg = 0.8 }
                else -> { pPerKg = 2.4; fPerKg = 1.0 }
            }
        } else {
            // NORMAL VĚTEV - Standardní sportovní výživa
            pPerKg = when(goal) {
                "CUT" -> 2.2 // Vyšší v dietě kvůli sytosti a svalům
                "BULK" -> 1.9 // V objemu stačí méně díky dostatku energie
                else -> 2.0
            }
            fPerKg = if (carbSens > 1.0) 0.85 else 1.05 // Úprava podle somatotypu (zápěstí)
        }

        var protein = weight * pPerKg
        var fat = weight * fPerKg
        var carbs = (targetCalories - (protein * 4) - (fat * 9)) / 4

        // 4. PREVENCE SNOWBALL EFEKTU & BIOLOGICKÉ STROPY
        val maxCarbCap = if (goal == "BULK") weight * 7.5 else weight * 5.5
        val minFatCap = weight * 0.75 // Nikdy neklesnout pod 0.75g tuku/kg (hormony!)

        // Pokud sacharidy přetečou strop, zbytek jde do tuků
        if (carbs > maxCarbCap) {
            val overflow = (carbs - maxCarbCap) * 4
            carbs = maxCarbCap
            fat += (overflow / 9)
        }

        // Pokud tuky klesnou pod minimum, sebereme sacharidy
        if (fat < minFatCap) {
            val deficitFat = (minFatCap - fat) * 9
            fat = minFatCap
            carbs -= (deficitFat / 4)
        }

        // Finální doladění Elite Inzulínové senzitivity
        if (isElite) {
            val originalCarbs = carbs
            carbs *= carbSens
            fat += ((originalCarbs - carbs) * 4) / 9
        }

        val finalCalories = (protein * 4) + (carbs * 4) + (fat * 9)

        return MacroResult(
            calories = finalCalories,
            protein = protein,
            carbs = carbs,
            fat = fat,
            water = (weight * 0.04) + (if (finalCalories > 3000) 1.0 else 0.0),
            trainingType = trainingType.uppercase(),
            weight = weight,
            isEliteMode = isElite
        )
    }
}