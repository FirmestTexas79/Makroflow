package cz.uhk.macroflow.learning

import android.content.Context
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.training.exercises.ExerciseLibrary
import cz.uhk.macroflow.training.log.StrengthModel
import cz.uhk.macroflow.training.log.WorkoutRepository
import cz.uhk.macroflow.wearable.RitualPrefill
import cz.uhk.macroflow.wearable.SleepInsights
import cz.uhk.macroflow.wearable.latestNight
import java.time.LocalDate

/** Android strana: posbírá data z databáze a z preferencí a drží stav upozornění. Volat mimo hlavní vlákno. */
object LearningData {
    private const val PREFS = "LearningPrefs"
    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    data class Quick(val daysUsed: Int, val factor: Double?)

    private fun daysSince(first: String?, today: LocalDate) =
        first?.let { (today.toEpochDay() - LocalDate.parse(it).toEpochDay() + 1).toInt().coerceAtLeast(1) } ?: 0

    /** Levně: kolik dní aplikaci používáš a poslední faktor výdeje (jen když už je spočítaný). */
    fun quick(ctx: Context, today: LocalDate = LocalDate.now()): Quick {
        val db = AppDatabase.getDatabase(ctx.applicationContext)
        val first = listOfNotNull(
            db.checkInDao().getAllCheckInsSync().minOfOrNull { it.date },
            db.workoutDao().getAllSync().minOfOrNull { it.date },
            db.adaptiveTdeeDao().getAllSync().minOfOrNull { it.date }
        ).minOrNull()
        val snap = db.adaptiveTdeeDao().getLatestSync()?.takeIf { it.status == "OK" }
        return Quick(daysSince(first, today), snap?.factor)
    }

    fun load(ctx: Context, today: LocalDate = LocalDate.now()): Report {
        val app = ctx.applicationContext
        val db = AppDatabase.getDatabase(app)
        val sets = db.workoutDao().getAllSync()
        val snaps = db.adaptiveTdeeDao().getAllSync()
        val first = listOfNotNull(db.checkInDao().getAllCheckInsSync().minOfOrNull { it.date },
            sets.minOfOrNull { it.date }, snaps.minOfOrNull { it.date }).minOrNull()

        val energy = snaps.lastOrNull()?.let {
            EnergyIn(it.status == "OK", it.factor, it.modelTdee, it.adaptiveTdee, it.confidence, it.weighIns, it.loggedDays)
        }

        val todayDay = today.toEpochDay().toInt()
        val lifts = WorkoutRepository.toLogged(sets).groupBy { it.exerciseId }.mapNotNull { (id, l) ->
            val firstDay = l.minOf { it.day }
            val start = StrengthModel.estimate(l.filter { it.day <= firstDay + 10 }, firstDay + 10) ?: return@mapNotNull null
            val now = StrengthModel.estimate(l, todayDay) ?: return@mapNotNull null
            LiftIn(ExerciseLibrary.byId(id)?.name ?: id, start.e1rm, now.e1rm, (now.upper - now.lower) / 2, now.sessions)
        }

        val ritual = RitualPrefill.accuracy(app)?.let { RitualIn(it.first, it.second) }

        val days = db.wearableDao().getDaysSync(today.minusDays(90).toString(), today.toString())
        val sleep = latestNight(days)?.let { night ->
            val s = SleepInsights.summarize(days, night.date)
            SleepIn(s.nights, SleepInsights.recommendations(s, SleepInsights.stressEffect(days, night.date)).firstOrNull())
        }

        val wp = app.getSharedPreferences("WearableCompare", Context.MODE_PRIVATE)
        val watch = if (wp.contains("days")) WatchIn(wp.getInt("days", 0), wp.getFloat("modelErr", 0f).toDouble(),
            wp.getFloat("garminErr", 0f).toDouble(), wp.getFloat("garminWeight", 0f).toDouble()) else null

        return LearningModel.build(Inputs(daysSince(first, today), energy, lifts, ritual, sleep, watch))
    }

    // ── Stav upozornění ─────────────────────────────────────────────────────

    fun state(ctx: Context): TriggerState = prefs(ctx).run {
        TriggerState(getBoolean("unlocked_seen", false),
            if (contains("last_factor")) getFloat("last_factor", 1f).toDouble() else null,
            getString("week_key", null), getString("dismissed", null))
    }

    /** Uživatel upozornění otevřel nebo zavřel: už se neukáže znovu. */
    fun acknowledge(ctx: Context, n: Notice, factor: Double?) {
        val e = prefs(ctx).edit().putString("dismissed", n.key)
        when {
            n.key == "unlock" -> e.putBoolean("unlocked_seen", true)
            n.key.startsWith("energy") -> factor?.let { e.putFloat("last_factor", it.toFloat()) }
            n.key.startsWith("week") -> e.putString("week_key", n.key.removePrefix("week:"))
        }
        e.apply()
    }

    /** Otevření okna bere všechna upozornění jako viděná. */
    fun markOpened(ctx: Context, factor: Double?, today: LocalDate = LocalDate.now()) {
        val e = prefs(ctx).edit().putBoolean("unlocked_seen", true).putString("week_key", LearningModel.weekKey(today)).remove("dismissed")
        factor?.let { e.putFloat("last_factor", it.toFloat()) }
        e.apply()
    }
}
