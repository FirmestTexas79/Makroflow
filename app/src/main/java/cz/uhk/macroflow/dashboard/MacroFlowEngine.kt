package cz.uhk.macroflow.dashboard

import android.content.Context
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.data.CheckInEntity
import cz.uhk.macroflow.data.ConsumedSnackEntity
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object MacroFlowEngine {

    // Výpočet denního stavu
    fun calculateDailyStatus(context: Context, consumedList: List<ConsumedSnackEntity>): DailyStatus {
        val target = MacroCalculator.calculate(context)
        val eatenP = consumedList.sumOf { it.p.toDouble() }
        val eatenS = consumedList.sumOf { it.s.toDouble() }
        val eatenT = consumedList.sumOf { it.t.toDouble() }
        val eatenCal = consumedList.sumOf { it.calories.toDouble() }

        return DailyStatus(
            caloriesLeft = target.calories - eatenCal,
            proteinLeft = target.protein - eatenP,
            carbsLeft = target.carbs - eatenS,
            fatLeft = target.fat - eatenT,
            target = target,
            eatenP = eatenP,
            eatenS = eatenS,
            eatenT = eatenT,
            eatenCal = eatenCal
        )
    }

    // Logika trenéra
    fun getCoachAdvice(status: DailyStatus, checkIn: CheckInEntity?): String {
        if (checkIn == null) return "Ještě jsi neudělal ranní rituál. Klikni zde! ✨"

        val weight = checkIn.weight
        val sleep = checkIn.sleepQuality
        val energy = checkIn.energyLevel
        val hunger = checkIn.hungerLevel

        return when {
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

            // 🆕 PŘIDANÁ VĚTEV PRO STŘEDNÍ HODNOTY (Aby to hned reagovalo i na výchozí hodnoty)
            sleep == 3 || energy == 3 ->
                "Dneska je to takový průměr pro tvých $weight kg. Žádné extrémy, nalož si stabilní jídlo a jdeme na to! 📈"

            else -> "Váha $weight kg v normě. Sleduj svůj hlad a drž se plánu!"
        }
    }

    suspend fun logSwipedFood(context: Context, name: String, p: Double, s: Double, t: Double, cal: Double) {
        withContext(Dispatchers.IO) {
            val db = AppDatabase.Companion.getDatabase(context)
            val now = Date()
            val snack = ConsumedSnackEntity(
                date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(now),
                time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now),
                name = name,
                p = p.toFloat(),
                s = s.toFloat(),
                t = t.toFloat(),
                calories = cal.toInt()
            )
            db.consumedSnackDao().insertConsumed(snack)
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
    val eatenCal: Double
)