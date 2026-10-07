package cz.uhk.macroflow.pokemon.stamina

import android.content.Context
import cz.uhk.macroflow.dashboard.MacroCalculator
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.data.GameEventType
import cz.uhk.macroflow.training.QuickWorkout
import cz.uhk.macroflow.pokemon.daily.DailyQuestStore
import java.time.LocalDate

/** Uložení energie v GamePrefs (docs/adr/0065). Pravidla jsou v [Stamina]. */
object StaminaStore {
    private const val PREFS = "GamePrefs"
    private const val BASE = "stamina_base"
    private const val OVER = "stamina_over"
    private const val DAY = "stamina_refill_day"
    private const val EXIT = "stamina_last_exit"
    private const val FROM = "stamina_last_from"
    private const val MOVED = "stamina_last_move"
    private const val GRANTED = "stamina_granted"
    private const val GRANTED_DAY = "stamina_granted_day"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(ctx: Context): Stamina.State = prefs(ctx).run {
        Stamina.State(
            base = getInt(BASE, Stamina.BASE_MAX),
            over = getInt(OVER, 0),
            refillDay = getString(DAY, null),
            lastExitAt = getLong(EXIT, 0),
            lastFrom = getString(FROM, null),
            lastMoveAt = getLong(MOVED, 0),
            granted = getInt(GRANTED, 0),
            grantedDay = getString(GRANTED_DAY, null)
        )
    }

    fun save(ctx: Context, s: Stamina.State) {
        prefs(ctx).edit()
            .putInt(BASE, s.base).putInt(OVER, s.over)
            .putString(DAY, s.refillDay).putLong(EXIT, s.lastExitAt)
            .putString(FROM, s.lastFrom).putLong(MOVED, s.lastMoveAt)
            .putInt(GRANTED, s.granted).putString(GRANTED_DAY, s.grantedDay)
            .apply()
    }

    /** Příchod do Makrosvěta: případné doplnění přes noc. */
    fun onEnter(ctx: Context, now: Long = System.currentTimeMillis()): Stamina.State {
        val s = Stamina.refill(load(ctx), LocalDate.now().toString(), now)
        save(ctx, s)
        return s
    }

    /** Dnešní zápisy z databáze pro tabulku A. Musí běžet mimo hlavní vlákno. */
    fun dayFacts(ctx: Context): Stamina.Day {
        val f = DailyQuestStore.facts(ctx)
        val goals = runCatching { MacroCalculator.calculate(ctx) }.getOrNull()
        val db = AppDatabase.getDatabase(ctx)
        val today = LocalDate.now().toString()
        val kcal = db.consumedSnackDao().getConsumedByDateSync(today).sumOf { it.calories }
        val events = db.gameEventDao()
        return Stamina.Day(
            checkIn = f.checkIn,
            meals = f.meals,
            waterMl = f.waterMl,
            waterGoalMl = goals?.let { (it.water * 1000).toInt() } ?: 0,
            macrosHit = goals != null && Stamina.macrosHit(f.proteinG.toDouble(), goals.protein, kcal.toDouble(), goals.calories),
            steps = f.steps,
            sets = f.workoutSets,
            workoutDone = events.countOnDate(GameEventType.WORKOUT_DONE.name, today) > 0,
            cardioDone = events.countOnDate(GameEventType.CARDIO_DONE.name, today) > 0,
            restDay = f.workoutSets == 0 && QuickWorkout.today(ctx) == null && plannedRestDay(ctx)
        )
    }

    /** Dnes je v plánu volno a zbytek týdne plán má (bez plánu není co odpočívat). */
    private fun plannedRestDay(ctx: Context): Boolean {
        val p = ctx.getSharedPreferences("TrainingPrefs", Context.MODE_PRIVATE)
        fun rest(day: java.time.DayOfWeek) = listOf("type_", "kardio_type_").all {
            p.getString(it + day.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH), "rest") == "rest"
        }
        return rest(LocalDate.now().dayOfWeek) && !java.time.DayOfWeek.entries.all { rest(it) }
    }

    /** Připíše energii za dnešní zápisy, které ještě připsané nebyly. */
    fun collect(ctx: Context, day: Stamina.Day): Stamina.State {
        val s = Stamina.reward(load(ctx), LocalDate.now().toString(), Stamina.dayReward(day))
        save(ctx, s)
        return s
    }

    /** Odchod z Makrosvěta: odtud se počítá pauza pro doplnění. */
    fun onExit(ctx: Context, now: Long = System.currentTimeMillis()) = save(ctx, load(ctx).copy(lastExitAt = now))

    /** Zaplatí [cost]. null = nestačí (stav se nemění). */
    fun pay(ctx: Context, cost: Int): Stamina.State? = Stamina.spend(load(ctx), cost)?.also { save(ctx, it) }

    /** Debug: nastaví energii přímo (adb … --es debug_stamina 30+10). */
    fun debugSet(ctx: Context, base: Int, over: Int) =
        save(ctx, load(ctx).copy(base = base.coerceIn(0, Stamina.BASE_MAX), over = over.coerceIn(0, Stamina.OVER_MAX)))
}
