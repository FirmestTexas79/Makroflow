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

    /**
     * HLAVNÍ VÝPOČETNÍ JÁDRO
     * Zpracovává dynamickou rovnováhu mezi příjmem, výdejem a termickým efektem.
     */
    fun calculateDailyStatus(context: Context, consumedList: List<ConsumedSnackEntity>): DailyStatus {
        val target = MacroCalculator.calculate(context)
        val db = AppDatabase.getDatabase(context)
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        val stepsEntity = runBlocking { db.stepsDao().getStepsForDateSync(todayStr) }
        val stepsCount = stepsEntity?.count ?: 0
        val weight = target.weight

        /**
         * 1. DYNAMICKÝ VÝDEJ Z KROKŮ (MET Metodika)
         * Vychází z průměrné intenzity chůze 4 km/h (cca 2.8 MET).
         * Biologicky: Vyšší váha = vyšší mechanická práce při každém kroku.
         */
        val burnedFromSteps = calculateCaloriesFromSteps(stepsCount, weight)

        /**
         * 2. DYNAMICKÝ TERMICKÝ EFEKT (TEF)
         * Trávení jídla spotřebovává energii.
         * Bílkoviny (25%), Sacharidy (7%), Tuky (2.5%).
         * Toto vrací do aplikace "biologickou čistou energii".
         */
        val eatenP = consumedList.sumOf { it.p.toDouble() }
        val eatenS = consumedList.sumOf { it.s.toDouble() }
        val eatenT = consumedList.sumOf { it.t.toDouble() }
        val eatenCalRaw = consumedList.sumOf { it.calories.toDouble() }

        val totalTEF = (eatenP * 4 * 0.25) + (eatenS * 4 * 0.07) + (eatenT * 9 * 0.025)
        val netEatenCalories = eatenCalRaw - totalTEF

        /**
         * 3. ADAPTIVNÍ NAVÝŠENÍ CÍLŮ
         * Bonusové kalorie z kroků (LISS aktivita) rozdělujeme:
         * 70 % Sacharidy (rychlá obnova svalového glykogenu)
         * 30 % Tuky (podpora buněčných membrán a hormonů)
         */
        val extraCarbsFromSteps = (burnedFromSteps * 0.7) / 4.0
        val extraFatFromSteps = (burnedFromSteps * 0.3) / 9.0

        val finalTargetCalories = target.calories + burnedFromSteps
        val finalTargetCarbs = target.carbs + extraCarbsFromSteps
        val finalTargetFat = target.fat + extraFatFromSteps

        return DailyStatus(
            caloriesLeft = finalTargetCalories - netEatenCalories, // Započten TEF bonus
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

    /**
     * Vědecký výpočet kalorií z chůze
     * Reflektuje nelineární nárůst únavy a mechanickou zátěž hmotnosti.
     */
    fun calculateCaloriesFromSteps(steps: Int, weight: Double): Double {
        if (steps <= BAZALNI_KROKY) return 0.0
        val extraSteps = steps - BAZALNI_KROKY

        // Konstanta 0.00042 odpovídá pohybu v mírném terénu při 2.8 MET
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

            hunger >= 5 ->
                "Pozor na vlčí hlad! Těch $weight kg dneska potřebuje pořádný objem jídla a bílkovin. 🥩"

            sleep <= 2 && energy >= 4 ->
                "Jedeš na dluh! Energie tam je, ale tělo $weight kg je po špatné noci v šoku. Dneska už radši žádný kofein odpoledne! ☕🚫"

            energy >= 4 && sleep >= 4 ->
                "Ideální konstelace! Tvých $weight kg je připraveno na rekordy. Rozbij to! 🔥"

            energy <= 2 && sleep <= 2 ->
                "Krizový režim. Tělo $weight kg dneska potřebuje úplný rest a regeneraci. 🛌"

            sleep >= 4 && energy <= 3 && hunger >= 4 ->
                "Spánek byl top, ale motor je prázdný. Tvých $weight kg potřebuje dnes víc paliva! 🍝"

            steps < 3000 && energy >= 3 ->
                "Dneska je to spíš odpočinek pro tvých $weight kg. Zkus aspoň krátkou procházku, ať se ti lépe tráví! 🚶‍♂️"

            else -> "Váha $weight kg v normě. Sleduj svůj hlad a drž se plánu!"
        }
    }

    // ✅ PLNÁ VERZE: Lokální DB + Firebase Sync
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

            // 1. Uložení do lokální Room databáze
            db.consumedSnackDao().insertConsumed(snack)

            // 2. Synchronizace do Firebase (pokud je uživatel přihlášen)
            if (FirebaseRepository.isLoggedIn) {
                try {
                    FirebaseRepository.uploadConsumedSnack(snack)
                } catch (e: Exception) {
                    android.util.Log.e("FIREBASE_SYNC", "Nepovedlo se nahrát snack: ${e.message}")
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