package cz.uhk.macroflow.dashboard

import android.content.Context
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.data.CheckInEntity
import cz.uhk.macroflow.data.ConsumedSnackEntity
import cz.uhk.macroflow.data.FirebaseRepository
import cz.uhk.macroflow.energy.EnergyModel
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object MacroFlowEngine {

    /**
     * Stav dne = cíle z [MacroCalculator] (už obsahují kroky i trénink) − snědené.
     *
     * Dřív se tu navíc odečítal TEF od snědených kalorií a zvlášť přičítaly kroky
     * s pevným rozdělením 80 % S / 20 % T. TEF je ale už součástí výdeje (10 %),
     * takže se počítal dvakrát a deficit při dietě mizel. Viz docs/adr/0002.
     */
    fun calculateDailyStatus(context: Context, consumedList: List<ConsumedSnackEntity>): DailyStatus =
        calculateDailyStatusForDate(context, Date(), consumedList)

    fun calculateDailyStatusForDate(
        context: Context,
        date: Date,
        consumedList: List<ConsumedSnackEntity>
    ): DailyStatus {
        val target = MacroCalculator.calculateForDate(context, date)

        val eatenP = consumedList.sumOf { it.p.toDouble() }
        val eatenS = consumedList.sumOf { it.s.toDouble() }
        val eatenT = consumedList.sumOf { it.t.toDouble() }
        val eatenFiber = consumedList.sumOf { it.fiber.toDouble() }
        val eatenCal = consumedList.sumOf { it.calories.toDouble() }

        // Pro zobrazení skutečně naměřené kroky (model může u dnešku/prázdného dne předpokládat 6000)
        val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date)
        val recordedSteps = AppDatabase.getDatabase(context).stepsDao().getStepsForDateSync(dateKey)?.count ?: 0
        val person = MacroCalculator.personOf(
            AppDatabase.getDatabase(context).userProfileDao().getProfileSync()
        )

        return DailyStatus(
            caloriesLeft = target.calories - eatenCal,
            proteinLeft = target.protein - eatenP,
            carbsLeft = target.carbs - eatenS,
            fatLeft = target.fat - eatenT,
            fiberLeft = target.fiber - eatenFiber,
            target = target,
            eatenP = eatenP,
            eatenS = eatenS,
            eatenT = eatenT,
            eatenFiber = eatenFiber,
            eatenCal = eatenCal,
            stepsCount = recordedSteps,
            stepsCalories = EnergyModel.walkingNetKcal(recordedSteps, person)
        )
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
                fiber = fiber.toFloat(),
                energyKj = kj.toFloat(),
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