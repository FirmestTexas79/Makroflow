package cz.uhk.macroflow.dashboard

import android.content.Context
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.data.CheckInEntity
import cz.uhk.macroflow.data.ConsumedSnackEntity
import cz.uhk.macroflow.data.FirebaseRepository
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

private const val BAZALNI_KROKY = 5000

object MacroFlowEngine {

    fun calculateDailyStatus(context: Context, consumedList: List<ConsumedSnackEntity>): DailyStatus {
        val target = MacroCalculator.calculate(context)
        val db = AppDatabase.getDatabase(context)
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        val stepsEntity = runBlocking { db.stepsDao().getStepsForDateSync(todayStr) }
        val stepsCount = stepsEntity?.count ?: 0
        val weight = target.weight

        /**
         * 1. DYNAMICKÝ VÝDEJ Z KROKŮ
         */
        val burnedFromSteps = calculateCaloriesFromSteps(stepsCount, weight)

        /**
         * 2. DYNAMICKÝ TERMICKÝ EFEKT (TEF)
         */
        val eatenP = consumedList.sumOf { it.p.toDouble() }
        val eatenS = consumedList.sumOf { it.s.toDouble() }
        val eatenT = consumedList.sumOf { it.t.toDouble() }
        val eatenCalRaw = consumedList.sumOf { it.calories.toDouble() }

        val totalTEF = (eatenP * 4 * 0.25) + (eatenS * 4 * 0.07) + (eatenT * 9 * 0.025)
        val netEatenCalories = eatenCalRaw - totalTEF

        /**
         * 3. ADAPTIVNÍ NAVÝŠENÍ CÍLŮ
         */
        val extraCarbsFromSteps = (burnedFromSteps * 0.7) / 4.0
        val extraFatFromSteps = (burnedFromSteps * 0.3) / 9.0

        val finalTargetCalories = target.calories + burnedFromSteps
        val finalTargetCarbs = target.carbs + extraCarbsFromSteps
        val finalTargetFat = target.fat + extraFatFromSteps

        return DailyStatus(
            caloriesLeft = finalTargetCalories - netEatenCalories,
            proteinLeft = target.protein - eatenP,
            carbsLeft = finalTargetCarbs - eatenS,
            fatLeft = finalTargetFat - eatenT,
            target = target.copy(
                calories = finalTargetCalories,
                carbs = finalTargetCarbs,
                fat = finalTargetFat
            ),
            eatenP = eatenP, eatenS = eatenS, eatenT = eatenT, eatenCal = eatenCalRaw,
            stepsCount = stepsCount,
            stepsCalories = burnedFromSteps
        )
    }

    fun calculateCaloriesFromSteps(steps: Int, weight: Double): Double {
        if (steps <= BAZALNI_KROKY) return 0.0
        val extraSteps = steps - BAZALNI_KROKY
        return extraSteps * weight * 0.00042
    }

    fun getCoachAdvice(status: DailyStatus, checkIn: CheckInEntity?): String {
        if (checkIn == null) return "Ještě jsi neudělal ranní rituál. Klikni zde! ✨"

        val weight = checkIn.weight
        val sleep = checkIn.sleepQuality
        val energy = checkIn.energyLevel
        val hunger = checkIn.hungerLevel
        val steps = status.stepsCount

        return when {
            steps >= 12000 && status.caloriesLeft > 800 ->
                "Dneska jsi pořádná mašina ($steps kroků)! Tvých $weight kg potřebuje dotankovat sacharidy. Navýšili jsme ti limit, tak se neboj kvalitní přílohy! 🏃‍♂️🍝"
            hunger >= 5 -> "Pozor na vlčí hlad! Tělo $weight kg dneska potřebuje pořádný objem jídla a bílkovin. 🥩"
            sleep <= 2 && energy >= 4 -> "Jedeš na dluh! Energie tam je, ale tělo $weight kg je po špatné noci v šoku. ☕🚫"
            energy >= 4 && sleep >= 4 -> "Ideální konstelace! Tvých $weight kg je připraveno na rekordy. Rozbij to! 🔥"
            else -> "Váha $weight kg v normě. Sleduj svůj hlad a drž se plánu!"
        }
    }

    suspend fun logSwipedFood(
        context: Context,
        name: String,
        p: Double,
        s: Double,
        t: Double,
        cal: Double,
        mealContext: String = "NO_TRAINING"
    ) {
        withContext(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            val now = Date()
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(now)

            val snack = ConsumedSnackEntity(
                date = todayStr,
                time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now),
                name = name,
                p = p.toFloat(),
                s = s.toFloat(),
                t = t.toFloat(),
                calories = cal.toInt(),
                mealContext = mealContext
            )
            db.consumedSnackDao().insertConsumed(snack)
            if (FirebaseRepository.isLoggedIn) {
                try {
                    FirebaseRepository.uploadConsumedSnack(snack)
                } catch (e: Exception) {
                    android.util.Log.e("FIREBASE_SYNC", "Chyba syncu: ${e.message}")
                }
            }
        }
    }
}

data class DailyStatus(
    val caloriesLeft: Double,
    val proteinLeft: Double,
    val carbsLeft: Double,
    val fatLeft: Double,
    val target: MacroResult,
    val eatenP: Double,
    val eatenS: Double,
    val eatenT: Double,
    val eatenCal: Double,
    val stepsCount: Int = 0,
    val stepsCalories: Double = 0.0
)