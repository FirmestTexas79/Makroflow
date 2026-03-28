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

        // Načtení profilu včetně nových Elite parametrů
        val profile: UserProfileEntity? = runBlocking {
            AppDatabase.getDatabase(context).userProfileDao().getProfileSync()
        }

        val weight = profile?.weight ?: 83.0
        val height = profile?.height ?: 175.0
        val age = profile?.age ?: 22
        val gender = profile?.gender ?: "male"
        val activityMultiplier = profile?.activityMultiplier ?: 1.2f

        // Elite flag z DB
        val isElite = profile?.isEliteMode ?: false

        /**
         * 1. VÝPOČET BAZÁLNÍHO METABOLISMU (BMR)
         * Rozcestník: Klinická LBM metoda vs. Standardní populační metoda.
         */
        val bmr = if (isElite) {
            EliteMetabolicEngine.calculateEliteBMR(weight, profile?.bodyFatPercentage ?: 15.0)
        } else {
            var miffBmr = (10 * weight) + (6.25 * height) - (5 * age)
            if (gender == "female") miffBmr -= 161.0 else miffBmr += 5.0
            miffBmr
        }

        /**
         * 2. TERMICKÝ EFEKT A CELKOVÝ VÝDEJ (TDEE)
         */
        var totalCalories = bmr * activityMultiplier

        if (isElite) {
            // V Elite módu připočítáváme specifickou režii trávení podle typu diety
            val tefModifier = EliteMetabolicEngine.getDietaryTEFModifier(profile?.dietType ?: "BALANCED")
            totalCalories *= (1.0 + tefModifier)
        } else {
            // Standardní fixní TEF 5%
            totalCalories *= 1.05
        }

        /**
         * 3. TRÉNINKOVÉ KOMPENZACE (EPOC)
         */
        val (baseProt, baseFat, trainingExtraKcal) = when {
            trainingType.contains("legs") -> if (gender == "female") Triple(2.0, 0.9, 500.0) else Triple(2.4, 0.8, 650.0)
            trainingType.contains("pull") -> if (gender == "female") Triple(1.8, 0.8, 250.0) else Triple(2.2, 0.8, 350.0)
            trainingType.contains("push") -> if (gender == "female") Triple(1.8, 0.8, 200.0) else Triple(2.2, 0.8, 300.0)
            else -> if (gender == "female") Triple(1.7, 1.0, 0.0) else Triple(2.0, 1.0, 0.0)
        }

        totalCalories += trainingExtraKcal

        /**
         * 4. FINÁLNÍ DISTRIBUCE MAKROŽIVIN
         */
        val protein = weight * baseProt
        val fat = weight * baseFat

        // Výpočet sacharidů s ohledem na bio-typologii v Elite módu
        var carbs = (totalCalories - (protein * 4) - (fat * 9)) / 4

        if (isElite) {
            val carbSens = EliteMetabolicEngine.getCarbSensitivity(profile?.lastWristMeasurement ?: 17.5, height)
            // U elitních atletů upravujeme poměr v rámci kalorií podle inzulínové sensitivity
            carbs *= carbSens
            // Pozn: Pokud carbSens pohne se sacharidy, zbytek energie se v reálném čase přelije do tuků,
            // aby byly zachovány kalorie. (Zde pro jednoduchost držíme kalorický strop).
        }

        /**
         * 5. MEDICÍNSKÁ HYDRATACE (35-45ml / kg)
         */
        val waterTotal = (weight * 0.035) + (if (trainingExtraKcal > 0) 0.8 else 0.0)

        return MacroResult(
            calories = totalCalories,
            protein = protein,
            carbs = carbs,
            fat = fat,
            water = waterTotal,
            trainingType = trainingType.uppercase(),
            weight = weight,
            isEliteMode = isElite // Přidáno do resultu pro UI kontrolu
        )
    }
}

data class MacroResult(
    val calories: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val water: Double,
    val trainingType: String,
    val weight: Double,
    val isEliteMode: Boolean = false
)