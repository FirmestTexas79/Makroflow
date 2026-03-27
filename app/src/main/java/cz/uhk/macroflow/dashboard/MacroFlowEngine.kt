package cz.uhk.macroflow.dashboard

import android.content.Context
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.data.CheckInEntity
import cz.uhk.macroflow.data.ConsumedSnackEntity
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

object MacroFlowEngine {

    // Výpočet denního stavu obohacený o kroky
    fun calculateDailyStatus(context: Context, consumedList: List<ConsumedSnackEntity>): DailyStatus {
        val target = MacroCalculator.calculate(context)
        val db = AppDatabase.getDatabase(context)

        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        // 👟 ✅ Načteme dnešní kroky synchronně pro zachování plynulosti výpočtu
        val stepsEntity = runBlocking { db.stepsDao().getStepsForDateSync(todayStr) }
        val stepsCount = stepsEntity?.count ?: 0

        // 👟 ✅ Spočítáme spálené kalorie z kroků (přičteme k cíli)
        val weight = target.weight // weight jsme si vytáhli do MacroResultu níže
        val burnedFromSteps = calculateCaloriesFromSteps(stepsCount, weight)

        val finalTargetCalories = target.calories + burnedFromSteps
        // Navýšíme sacharidy z nachozených kroků (1g sacharidů = 4 kcal)
        val finalTargetCarbs = target.carbs + (burnedFromSteps / 4.0)

        val eatenP = consumedList.sumOf { it.p.toDouble() }
        val eatenS = consumedList.sumOf { it.s.toDouble() }
        val eatenT = consumedList.sumOf { it.t.toDouble() }
        val eatenCal = consumedList.sumOf { it.calories.toDouble() }

        return DailyStatus(
            caloriesLeft = finalTargetCalories - eatenCal,
            proteinLeft = target.protein - eatenP,
            carbsLeft = finalTargetCarbs - eatenS, // sacharidy se dynamicky posouvají s kroky!
            fatLeft = target.fat - eatenT,
            target = target.copy(calories = finalTargetCalories, carbs = finalTargetCarbs),
            eatenP = eatenP,
            eatenS = eatenS,
            eatenT = eatenT,
            eatenCal = eatenCal,
            stepsCount = stepsCount,
            stepsCalories = burnedFromSteps
        )
    }

    // 🧠 ✅ Výpočet kalorií z kroků podle váhy (MET metoda pro chůzi)
    private fun calculateCaloriesFromSteps(steps: Int, weight: Double): Double {
        if (steps <= 0) return 0.0
        // Konstanta pro běžnou chůzi: cca 0.00057 kcal na krok na 1 kg tělesné hmotnosti
        return steps * weight * 0.00057
    }

    // Logika trenéra
    fun getCoachAdvice(status: DailyStatus, checkIn: CheckInEntity?): String {
        if (checkIn == null) return "Ještě jsi neudělal ranní rituál. Klikni zde! ✨"

        val weight = checkIn.weight
        val sleep = checkIn.sleepQuality
        val energy = checkIn.energyLevel
        val hunger = checkIn.hungerLevel
        val steps = status.stepsCount

        return when {
            // Extrémní výdej kroků bez jídla
            steps >= 12000 && status.caloriesLeft > 1000 ->
                "Dneska jsi pořádná mašina ($steps kroků)! Tvých $weight kg potřebuje dotankovat sacharidy, nebo padneš únavou. Dej si pořádnou porci! 🏃‍♂️🍝"

            // Extrémní hlad
            hunger >= 5 ->
                "Pozor na vlčí hlad! Těch $weight kg dneska potřebuje pořádný objem jídla a bílkovin. 🥩"

            // Špatný spánek, ale vysoká energie
            sleep <= 2 && energy >= 4 ->
                "Jedeš na dluh! Energie tam je, ale tělo $weight kg po špatné noci v šoku. Dneska bez kofeinu odpoledne! ☕🚫"

            // Skvělý spánek, skvělá energie
            energy >= 4 && sleep >= 4 ->
                "Ideální konstelace! Tvých $weight kg je připraveno na rekordy. Rozbij to! 🔥"

            // Úplné vyčerpání (hodnoty 1 nebo 2)
            energy <= 2 && sleep <= 2 ->
                "Krizový režim. Tělo $weight kg dneska potřebuje úplný rest a regeneraci. 🛌"

            // Únava, ale spánek dobrý (motor bez paliva)
            sleep >= 4 && energy <= 3 && hunger >= 4 ->
                "Spánek byl top, ale motor je prázdný. Tvých $weight kg potřebuje dnes víc paliva! 🍝"

            // Střední hodnoty
            sleep == 3 || energy == 3 ->
                "Dneska je to takový průměr pro tvých $weight kg. Žádné extrémy, nalož si stabilní jídlo a jdeme na to! 📈"

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

            if (cz.uhk.macroflow.data.FirebaseRepository.isLoggedIn) {
                try {
                    cz.uhk.macroflow.data.FirebaseRepository.uploadConsumedSnack(snack)
                } catch (e: Exception) {
                    android.util.Log.e("FIREBASE_SYNC", "Nepovedlo se uložit snack: ${e.message}")
                }
            }

            withContext(Dispatchers.Main) {
                (context as? cz.uhk.macroflow.common.MainActivity)?.addXpToActivePokemonRealTime(
                    cz.uhk.macroflow.pokemon.XpRewards.LOGGED_FOOD
                )
            }
        }
    }
}

// 📦 ✅ Přidány vlastnosti do DailyStatus třídy
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