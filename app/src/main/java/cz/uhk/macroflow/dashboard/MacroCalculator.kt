package cz.uhk.macroflow.dashboard

import android.content.Context
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.data.UserProfileEntity
import cz.uhk.macroflow.energy.AdaptiveExpenditure
import cz.uhk.macroflow.energy.Diet
import cz.uhk.macroflow.energy.EnergyModel
import cz.uhk.macroflow.energy.Exercise
import cz.uhk.macroflow.energy.Goal
import cz.uhk.macroflow.energy.Lifestyle
import cz.uhk.macroflow.energy.MacroPlanner
import cz.uhk.macroflow.energy.Person
import cz.uhk.macroflow.energy.Sex
import cz.uhk.macroflow.energy.StrengthKind
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.*

/**
 * Android adaptér nad čistým modelem [cz.uhk.macroflow.energy.MacroPlanner]:
 * načte profil, tréninkový plán a kroky pro daný den a vrátí cíle.
 *
 * Všechny obrazovky (dashboard, historie, achievementy, spawny, report) berou cíle
 * odsud, takže ukazují stejná čísla. Kroky jsou už započtené – nic dalšího nepřičítat.
 */
object MacroCalculator {

    fun calculate(context: Context): MacroResult = calculateForDate(context, Date())

    /**
     * @param applyAdaptive false = čistý model bez korekce (vstup pro samotný adaptivní odhad)
     */
    fun calculateForDate(context: Context, date: Date, applyAdaptive: Boolean = true): MacroResult {
        val db = AppDatabase.getDatabase(context)
        val profile: UserProfileEntity? = runBlocking { db.userProfileDao().getProfileSync() }
        val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date)
        val isToday = dateKey == SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val recordedSteps = runBlocking { db.stepsDao().getStepsForDateSync(dateKey)?.count }

        // Adaptivní odhad platný k tomuto dni (max. 14 dní starý)
        val adaptive = db.adaptiveTdeeDao().getLatestOnOrBeforeSync(dateKey)
            ?.takeIf { daysBetween(it.date, dateKey) <= 14 }
        val factor = if (!applyAdaptive) 1.0
            else adaptive?.takeIf { it.status == AdaptiveExpenditure.Status.OK.name }?.factor ?: 1.0

        return calculateFor(
            profile = profile,
            exercises = plannedExercises(context, date),
            strengthLabel = strengthLabel(context, date),
            steps = EnergyModel.stepsForDay(recordedSteps, isToday),
            adaptiveFactor = factor,
            trendWeightKg = adaptive?.trendWeightKg
        )
    }

    private fun daysBetween(from: String, to: String): Long =
        java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.parse(from), java.time.LocalDate.parse(to))

    /** Náhled pro jiný typ diety (výběr diety v Elite módu) – bez ukládání. */
    fun previewForDiet(context: Context, dietLabel: String): MacroResult {
        val profile = runBlocking { AppDatabase.getDatabase(context).userProfileDao().getProfileSync() }
        return calculateFor(
            profile = profile?.copy(dietType = dietLabel) ?: UserProfileEntity(dietType = dietLabel),
            exercises = plannedExercises(context, Date()),
            strengthLabel = strengthLabel(context, Date()),
            steps = EnergyModel.ASSUMED_DAILY_STEPS
        )
    }

    /** Čistý výdej z kroků pro zobrazení (např. v Plánu). */
    fun walkingKcal(profile: UserProfileEntity?, steps: Int): Double =
        EnergyModel.walkingNetKcal(steps, personOf(profile))

    /**
     * @param trendWeightKg vyhlazená hmotnost z trendu (bez denních výkyvů vody) – má přednost
     *                      před posledním vážením uloženým v profilu
     */
    fun personOf(profile: UserProfileEntity?, trendWeightKg: Double? = null): Person = Person(
        weightKg = trendWeightKg ?: profile?.weight ?: 75.0,
        heightCm = profile?.height ?: 175.0,
        ageYears = profile?.age ?: 22,
        sex = Sex.from(profile?.gender),
        // Změřené % tuku používáme jen v Elite módu (tam ho uživatel skutečně zadává)
        measuredBodyFatPct = profile?.takeIf { it.isEliteMode }?.bodyFatPercentage?.takeIf { it > 0.0 }
    )

    private fun calculateFor(
        profile: UserProfileEntity?,
        exercises: List<Exercise>,
        strengthLabel: String,
        steps: Int,
        adaptiveFactor: Double = 1.0,
        trendWeightKg: Double? = null
    ): MacroResult {
        val person = personOf(profile, trendWeightKg)
        val t = MacroPlanner.plan(
            p = person,
            goal = Goal.from(profile?.goal),
            diet = Diet.from(profile?.dietType),
            lifestyle = Lifestyle.fromStored(profile?.activityMultiplier ?: 1.2f),
            steps = steps,
            exercises = exercises,
            adaptiveFactor = adaptiveFactor
        )
        return MacroResult(
            calories = t.kcal,
            protein = t.proteinG,
            carbs = t.carbsG,
            fat = t.fatG,
            fiber = t.fiberG,
            water = t.waterL,
            trainingType = strengthLabel,
            weight = person.weightKg,
            isEliteMode = profile?.isEliteMode ?: false,
            expenditure = t.expenditure
        )
    }

    private fun dayName(date: Date) = SimpleDateFormat("EEEE", Locale.ENGLISH).format(date)

    private fun strengthLabel(context: Context, date: Date): String =
        (context.getSharedPreferences("TrainingPrefs", Context.MODE_PRIVATE)
            .getString("type_${dayName(date)}", "rest") ?: "rest").uppercase()

    /** Převod týdenního plánu (TrainingPrefs) na seznam aktivit pro model. */
    private fun plannedExercises(context: Context, date: Date): List<Exercise> {
        val prefs = context.getSharedPreferences("TrainingPrefs", Context.MODE_PRIVATE)
        val day = dayName(date)
        fun num(key: String, def: Double) = prefs.getString(key, null)?.replace(",", ".")?.toDoubleOrNull() ?: def

        val list = mutableListOf<Exercise>()

        StrengthKind.from(prefs.getString("type_$day", "rest"))?.let { kind ->
            // Délka silového tréninku zatím v UI není → výchozí 60 min
            list += Exercise.Strength(kind, num("strength_duration_$day", EnergyModel.DEFAULT_STRENGTH_MINUTES))
        }

        val minutes = num("kardio_duration_$day", 0.0)
        when (prefs.getString("kardio_type_$day", "rest")?.lowercase()) {
            "run" -> if (minutes > 0) list += Exercise.Run(num("kardio_speed_$day", 8.0), minutes)
            "rope" -> {
                val jumps = num("kardio_jumps_$day", 0.0).toInt()
                if (jumps > 0 || minutes > 0) list += Exercise.JumpRope(jumps, minutes)
            }
            "stairs" -> if (minutes > 0) list += Exercise.Stairs(minutes)
        }
        return list
    }
}
