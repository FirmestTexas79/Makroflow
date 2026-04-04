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

private const val BAZALNI_KROKY = 6000 // Prvních 6000 kroků je v základu (activityMultiplier)

object MacroFlowEngine {

    fun calculateDailyStatus(context: Context, consumedList: List<ConsumedSnackEntity>): DailyStatus {
        val target = MacroCalculator.calculate(context)
        val db = AppDatabase.getDatabase(context)
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        val stepsEntity = runBlocking { db.stepsDao().getStepsForDateSync(todayStr) }
        val stepsCount = stepsEntity?.count ?: 0
        val weight = target.weight

        /**
         * 1. DYNAMICKÝ VÝDEJ Z KROKŮ (Pouze kroky nad bazál)
         * Tohle je extra pohyb nad rámec plánovaného kardia/tréninku.
         */
        val burnedFromSteps = calculateCaloriesFromSteps(stepsCount, weight)

        /**
         * 2. DYNAMICKÝ TERMICKÝ EFEKT (TEF) - Přesný výpočet ze snědeného
         * Odčítáme energii, kterou tělo spotřebovalo na trávení, abychom dostali "čistý" příjem.
         */
        val eatenP = consumedList.sumOf { it.p.toDouble() }
        val eatenS = consumedList.sumOf { it.s.toDouble() }
        val eatenT = consumedList.sumOf { it.t.toDouble() }
        val eatenFiber = consumedList.sumOf { it.fiber.toDouble() }
        val eatenCalRaw = consumedList.sumOf { it.calories.toDouble() }

        // Bílkoviny pálí nejvíc (25%), sacharidy (7%), tuky téměř nic (2.5%)
        val totalTEF = (eatenP * 4 * 0.25) + (eatenS * 4 * 0.07) + (eatenT * 9 * 0.025)
        val netEatenCalories = eatenCalRaw - totalTEF

        /**
         * 3. ADAPTIVNÍ NAVÝŠENÍ CÍLŮ PODLE KROKŮ
         * Přidáváme extra palivo, pokud uživatel nachodil víc, než je jeho běžný standard.
         */
        val extraCarbsFromSteps = (burnedFromSteps * 0.8) / 4.0
        val extraFatFromSteps = (burnedFromSteps * 0.2) / 9.0

        // Výsledný cíl pro dnešek (Základ + Tréninky + Extra kroky)
        val finalTargetCalories = target.calories + burnedFromSteps
        val finalTargetCarbs = target.carbs + extraCarbsFromSteps
        val finalTargetFat = target.fat + extraFatFromSteps

        /**
         * 4. INTERAKTIVNÍ VÝPOČET VLÁKNINY (Makroflow 2.0 Logic)
         * Výpočet: 14g na každých 1000 kcal cílového příjmu (včetně extra kalorií z kroků).
         * Zároveň garantujeme minimum 25g (nebo 0.4g na kg váhy), aby engine neházel nesmysly při nízkém příjmu.
         */
        val fiberFromCalories = (finalTargetCalories / 1000.0) * 14.0
        val fiberFromWeight = weight * 0.4
        val finalTargetFiber = fiberFromCalories.coerceAtLeast(fiberFromWeight).coerceAtLeast(25.0)

        return DailyStatus(
            caloriesLeft = finalTargetCalories - netEatenCalories,
            proteinLeft = target.protein - eatenP,
            carbsLeft = finalTargetCarbs - eatenS,
            fatLeft = finalTargetFat - eatenT,
            fiberLeft = finalTargetFiber - eatenFiber,
            target = target.copy(
                calories = finalTargetCalories,
                carbs = finalTargetCarbs,
                fat = finalTargetFat,
                fiber = finalTargetFiber
            ),
            eatenP = eatenP,
            eatenS = eatenS,
            eatenT = eatenT,
            eatenFiber = eatenFiber, // Tady to předáš
            eatenCal = eatenCalRaw,
            stepsCount = stepsCount,
            stepsCalories = burnedFromSteps
        )
    }

    fun calculateCaloriesFromSteps(steps: Int, weight: Double): Double {
        if (steps <= BAZALNI_KROKY) return 0.0
        val extraSteps = steps - BAZALNI_KROKY
        // 0.00045 kcal/kg/step pro extra aktivitu nad běžný rámec dne
        return extraSteps * weight * 0.00045
    }

    fun getCoachAdvice(status: DailyStatus, checkIn: CheckInEntity?): String {
        if (checkIn == null) return "Ještě jsi neudělal ranní rituál. Klikni zde! ✨"

        val weight = checkIn.weight
        val sleep = checkIn.sleepQuality
        val energy = checkIn.energyLevel
        val hunger = checkIn.hungerLevel
        val steps = status.stepsCount

        // Zjistíme, jestli má dnes v plánu kardio
        val trainingType = status.target.trainingType

        return when {
            steps >= 15000 && status.caloriesLeft > 600 ->
                "Dneska jsi neuvěřitelný chodec ($steps kroků)! Tvých $weight kg pálí jako zběsilé. Přidali jsme ti sacharidy, tak je pořádně využij! 🏃‍♂️🍚"

            hunger >= 5 && status.caloriesLeft < 300 ->
                "Vidím velký hlad a málo zbývajících kalorií. Zkus vsadit na velký objem zeleniny a bílkoviny, ať tělo $weight kg netrpí. 🥦"

            energy <= 2 && (trainingType != "REST") ->
                "Energie je na dně, ale máš v plánu makat. Pokud se na to cítíš, dej si před tréninkem rychlé cukry, jinak to dneska nehroť. ⚡"

            sleep <= 2 && energy >= 4 ->
                "Jedeš na kofeinový dluh! Pozor na zranění při tréninku. Tělo $weight kg po špatné noci hůře regeneruje. ☕🚫"

            energy >= 4 && sleep >= 4 ->
                "Perfektní setup! Tělo $weight kg je připravené na výkon. Ať už je to kardio nebo železo, dneska to bude tvoje! 🔥"

            else -> "Váha $weight kg je v optimálním trendu. Sleduj pocit hladu a užij si dnešní den!"
        }
    }

    suspend fun logSwipedFood(
        context: Context,
        name: String,
        p: Double,
        s: Double,
        t: Double,
        cal: Double,
        fiber: Double = 0.0,
        kj: Double = 0.0,
        chol: Double = 0.0,
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
                fiber = fiber.toFloat(),          // Přidáno
                energyKj = kj.toFloat(),    // Přidáno
                cholesterol = chol.toFloat(), // Přidáno
                mealContext = mealContext,
                timestamp = System.currentTimeMillis()
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
    val fiberLeft: Double = 0.0,
    val target: MacroResult,
    val eatenP: Double,
    val eatenS: Double,
    val eatenT: Double,
    val eatenFiber: Double = 0.0,
    val eatenCal: Double,
    val stepsCount: Int = 0,
    val stepsCalories: Double = 0.0
)