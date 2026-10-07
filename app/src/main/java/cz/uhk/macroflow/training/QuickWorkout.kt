package cz.uhk.macroflow.training

import android.content.Context
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.data.GameEventType
import cz.uhk.macroflow.data.GameEvents
import cz.uhk.macroflow.training.log.WorkoutTemplates
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale

/**
 * Rychlý zápis „Jdu na trénink“ a běžící trénink (docs/adr/0065, body 5 a 6).
 *
 * Jednorázová aktivita na dnešek, mimo týdenní plán. Čas se propisuje do
 * [TrainingTimeManager.getTrainingTimeForToday], takže kontext jídel a notifikace ji berou jako trénink.
 * Potvrzení „Hotovo“ zapíše [GameEventType.WORKOUT_DONE] / [GameEventType.CARDIO_DONE] do `game_events`,
 * odkud si to Makrosvět přečte jako odměnu.
 */
object QuickWorkout {
    const val CARDIO = "CARDIO"
    const val OTHER = "OTHER"
    /** Kardio kratší než tohle se do odměny nepočítá. */
    const val CARDIO_MIN = 15
    /** Zapomenutý trénink po této době přestane běžet. */
    private const val MAX_ACTIVE_MS = 4L * 60 * 60 * 1000

    private const val PREFS = "TrainingPrefs"
    private const val K_DAY = "quick_day"
    private const val K_KIND = "quick_kind"
    private const val K_VARIANT = "quick_variant"
    private const val K_TIME = "quick_time"
    private const val K_STARTED = "quick_started"
    private const val K_FINISHED = "quick_finished"

    /** [kind] = název [WorkoutTemplates.Kind], [CARDIO] nebo [OTHER]. [startedAt] 0 = zatím jen naplánováno. */
    data class Entry(val kind: String, val variant: Char?, val time: String, val startedAt: Long, val finishedAt: Long) {
        val strength: WorkoutTemplates.Kind? get() = WorkoutTemplates.Kind.entries.firstOrNull { it.name == kind }
        val label: String get() = when {
            kind == CARDIO -> "Kardio"
            strength != null && variant != null -> WorkoutTemplates.label(WorkoutTemplates.key(strength!!, variant))
            else -> "Jiný trénink"
        }
        fun running(now: Long = System.currentTimeMillis()) = startedAt > 0 && finishedAt == 0L && now - startedAt < MAX_ACTIVE_MS
        fun minutes(now: Long = System.currentTimeMillis()) = ((now - startedAt) / 60_000).toInt().coerceAtLeast(0)
    }

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun today() = LocalDate.now().toString()

    fun today(ctx: Context): Entry? = prefs(ctx).run {
        if (getString(K_DAY, null) != today()) return null
        Entry(
            kind = getString(K_KIND, OTHER) ?: OTHER,
            variant = getString(K_VARIANT, null)?.singleOrNull(),
            time = getString(K_TIME, null) ?: return null,
            startedAt = getLong(K_STARTED, 0),
            finishedAt = getLong(K_FINISHED, 0)
        )
    }

    fun active(ctx: Context): Entry? = today(ctx)?.takeIf { it.running() }

    private fun save(ctx: Context, kind: String, variant: Char?, time: String, startedAt: Long) =
        prefs(ctx).edit()
            .putString(K_DAY, today()).putString(K_KIND, kind).putString(K_VARIANT, variant?.toString())
            .putString(K_TIME, time).putLong(K_STARTED, startedAt).putLong(K_FINISHED, 0)
            .apply()

    /** „Za 15 min“ / „Vlastní čas“: jen naplánuje na dnešek. */
    fun plan(ctx: Context, kind: String, variant: Char?, time: String) = save(ctx, kind, variant, time, 0)

    /** „Teď“: trénink rovnou běží. */
    fun start(ctx: Context, kind: String, variant: Char?) {
        val now = System.currentTimeMillis()
        save(ctx, kind, variant, SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(now)), now)
    }

    /**
     * Ukončí běžící trénink. [done] = potvrzené „Hotovo“, zapíše odměnu (kardio až od [CARDIO_MIN] minut).
     * Vrací délku v minutách. Zrušení ([done] = false) zápis na dnešek smaže.
     */
    suspend fun finish(ctx: Context, done: Boolean): Int {
        val e = today(ctx) ?: return 0
        val minutes = e.minutes()
        if (!done) { prefs(ctx).edit().remove(K_DAY).apply(); return minutes }
        prefs(ctx).edit().putLong(K_FINISHED, System.currentTimeMillis()).apply()
        val db = AppDatabase.getDatabase(ctx)
        when {
            e.kind != CARDIO -> GameEvents.record(db, GameEventType.WORKOUT_DONE, payload = e.kind)
            minutes >= CARDIO_MIN -> GameEvents.record(db, GameEventType.CARDIO_DONE, payload = minutes.toString())
        }
        return minutes
    }
}
