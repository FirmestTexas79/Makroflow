package cz.uhk.macroflow.training

import android.content.Context
import cz.uhk.macroflow.common.AppPreferences
import java.text.SimpleDateFormat
import java.util.*

/**
 * TrainingTimeManager — ukládá a čte časy tréninků per den.
 *
 * Klíč: "training_time_Monday" → "07:30"  (nebo null = nenastaveno)
 * Délka tréninku: 75 minut (1h 15min)
 *
 * Poskytuje:
 *  - getTrainingTime(day) / setTrainingTime(day, "HH:mm")
 *  - getTrainingTimeForToday()
 *  - getTrainingContext() → enum pro FoodSwipeDialog fuzzy logiku
 */
object TrainingTimeManager {

    private const val DURATION_MIN = 75  // normální trénink = 1h 15min

    // ── Čtení / zápis ────────────────────────────────────────────────
    fun getTrainingTime(context: Context, dayEnglish: String): String? =
        AppPreferences.getTrainingTimeSync(context, dayEnglish)

    fun setTrainingTime(context: Context, dayEnglish: String, time: String?) =
        AppPreferences.setTrainingTimeSync(context, dayEnglish, time)

    fun getTrainingTimeForToday(context: Context): String? {
        val day = SimpleDateFormat("EEEE", Locale.ENGLISH).format(Date())
        return getTrainingTime(context, day)
    }

    // ── Výpočet kontextu pro jídlo ────────────────────────────────────
    enum class MealContext {
        NO_TRAINING,       // žádný trénink dnes
        LONG_BEFORE,       // víc než 3h před
        PRE_WORKOUT,       // 30min–3h před (PRE jídlo)
        IMMINENT,          // méně než 30min před — lehké jídlo
        DURING,            // probíhá trénink
        POST_WORKOUT_EARLY,// 0–2h po tréninku (POST jídlo — kritické okno)
        POST_WORKOUT_LATE, // 2–5h po tréninku
        LONG_AFTER         // víc než 5h po
    }

    /**
     * Vrátí MealContext pro právě teď — pro FoodSwipeDialog
     */
    fun getMealContext(context: Context): MealContext {
        val timeStr = getTrainingTimeForToday(context) ?: return MealContext.NO_TRAINING

        val now = Calendar.getInstance()
        val training = parseTime(timeStr) ?: return MealContext.NO_TRAINING
        val trainingEnd = training.clone() as Calendar
        trainingEnd.add(Calendar.MINUTE, DURATION_MIN)

        val diffToStart = (training.timeInMillis - now.timeInMillis) / 60_000L  // minuty do startu
        val diffFromEnd = (now.timeInMillis - trainingEnd.timeInMillis) / 60_000L // minuty od konce

        return when {
            diffToStart > 180  -> MealContext.LONG_BEFORE
            diffToStart in 30..180 -> MealContext.PRE_WORKOUT
            diffToStart in 0..29   -> MealContext.IMMINENT
            diffToStart < 0 && diffFromEnd < 0 -> MealContext.DURING
            diffFromEnd in 0..120  -> MealContext.POST_WORKOUT_EARLY
            diffFromEnd in 120..300 -> MealContext.POST_WORKOUT_LATE
            diffFromEnd > 300      -> MealContext.LONG_AFTER
            else                   -> MealContext.NO_TRAINING
        }
    }

    /**
     * Vrátí minuty do startu tréninku (záporné = trénink začal)
     */
    fun minutesToTraining(context: Context): Long? {
        val timeStr = getTrainingTimeForToday(context) ?: return null
        val training = parseTime(timeStr) ?: return null
        return (training.timeInMillis - System.currentTimeMillis()) / 60_000L
    }

    /**
     * Přeloží "HH:mm" na Calendar pro dnešní den
     */
    private fun parseTime(timeStr: String): Calendar? {
        return try {
            val parts = timeStr.split(":")
            val h = parts[0].toInt(); val m = parts[1].toInt()
            Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, h)
                set(Calendar.MINUTE, m)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        } catch (_: Exception) { null }
    }

    /**
     * Formátuje minuty do lidsky čitelné podoby
     */
    fun formatCountdown(minutes: Long): String = when {
        minutes > 60  -> "${minutes / 60}h ${minutes % 60}min"
        minutes > 0   -> "${minutes}min"
        minutes == 0L -> "právě teď"
        else          -> "${-minutes}min zpět"
    }

    /**
     * Popis kontextu pro trenéra na dashboardu
     */
    fun getMealContextLabel(context: Context): String? {
        val timeStr = getTrainingTimeForToday(context) ?: return null
        val minutes = minutesToTraining(context) ?: return null
        val trainingType = AppPreferences.getTrainingTypeSync(
            context,
            SimpleDateFormat("EEEE", Locale.ENGLISH).format(Date())
        ).uppercase()

        return when (getMealContext(context)) {
            MealContext.LONG_BEFORE       -> "$trainingType v $timeStr — máš čas se dobře najíst"
            MealContext.PRE_WORKOUT       -> "$trainingType za ${formatCountdown(minutes)} — čas na PRE jídlo!"
            MealContext.IMMINENT          -> "$trainingType za ${formatCountdown(minutes)} — jen lehké jídlo!"
            MealContext.DURING            -> "$trainingType probíhá — hydratace!"
            MealContext.POST_WORKOUT_EARLY-> "$trainingType hotov — kritické okno! Dej POST jídlo hned"
            MealContext.POST_WORKOUT_LATE -> "$trainingType hotov — ${formatCountdown(-minutes)}min zpět"
            MealContext.LONG_AFTER        -> "$trainingType byl dnes v $timeStr"
            MealContext.NO_TRAINING       -> null
        }
    }
}
