package cz.uhk.macroflow.training

import android.content.Context
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.data.GameEventType
import cz.uhk.macroflow.data.GameEvents
import cz.uhk.macroflow.training.body.Muscle
import cz.uhk.macroflow.training.exercises.ExerciseLibrary
import cz.uhk.macroflow.training.body.TrainingMuscles
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
    private const val K_MUSCLES = "quick_muscles"

    /** [kind] = název [WorkoutTemplates.Kind], [CARDIO] nebo [OTHER]. [startedAt] 0 = zatím jen naplánováno. */
    data class Entry(
        val kind: String, val variant: Char?, val time: String, val startedAt: Long, val finishedAt: Long,
        /** Vybrané partie u „Jiného“ tréninku – podle nich se skládají cviky. */
        val muscles: Set<Muscle> = emptySet()
    ) {
        val strength: WorkoutTemplates.Kind? get() = WorkoutTemplates.Kind.entries.firstOrNull { it.name == kind }
        val label: String get() = when {
            kind == CARDIO -> "Kardio"
            strength != null && variant != null -> WorkoutTemplates.label(WorkoutTemplates.key(strength!!, variant))
            else -> "Jiný trénink"
        }
        fun running(now: Long = System.currentTimeMillis()) = startedAt > 0 && finishedAt == 0L && now - startedAt < MAX_ACTIVE_MS
        fun minutes(now: Long = System.currentTimeMillis()) = ((now - startedAt) / 60_000).toInt().coerceAtLeast(0)
    }

    /** Partie, které trénink [kind] hlavně zatěžuje (pro rozsvícení postavy). */
    fun musclesOf(kind: String): Set<Muscle> = when (kind) {
        CARDIO -> TrainingMuscles.of("run").filterValues { it >= TrainingMuscles.PRIMARY }.keys
        OTHER -> emptySet()
        else -> TrainingMuscles.of(kind.lowercase()).filterValues { it >= TrainingMuscles.PRIMARY }.keys
    }

    /**
     * Šablona, do které se vybrané partie celé vejdou (hlavní 1, vedlejší 0,5 – vyhraje nejlepší pokrytí).
     * Když se nevejdou do žádné (třeba prsa + záda + stehna), je to [OTHER] s cviky podle [exercisesFor].
     * null = nic vybráno.
     */
    fun suggest(selected: Set<Muscle>): String? {
        if (selected.isEmpty()) return null
        return WorkoutTemplates.Kind.entries
            .map { k -> k to TrainingMuscles.of(k.planType) }
            .filter { (_, m) -> selected.all { it in m } }
            .maxByOrNull { (_, m) -> selected.sumOf { m.getValue(it) } }
            ?.first?.name ?: OTHER
    }

    /**
     * Cviky na vybrané partie: ke každé 2 cviky, kde je hlavní. Přednost mají cviky ze šablon
     * (to, co uživatel v posilovně opravdu dělá), pak jednodušší. Nejvýš [max] cviků.
     */
    fun exercisesFor(selected: Collection<Muscle>, max: Int = 8): List<String> {
        val known = WorkoutTemplates.DEFAULTS.values.flatten().toSet()
        val out = linkedSetOf<String>()
        selected.forEach { m ->
            ExerciseLibrary.ALL.filter { m in it.primary && it.id !in out }
                .sortedWith(compareBy({ it.id !in known }, { it.level }, { it.primary.size }))
                .take(2).forEach { out += it.id }
        }
        return out.take(max)
    }

    /** Výchozí pauza mezi sériemi (později v nastavení a per cvik v šabloně). */
    const val REST_MS = 3L * 60 * 1000
    /** Aktivní trénink = série za posledních 45 min (docs/adr/0065, bod 5). */
    const val ACTIVE_WINDOW_MS = 45L * 60 * 1000

    /**
     * Kolik zbývá z pauzy po sérii zapsané v [lastSetAt] (ms, ≤ 0 = pauza skončila).
     * null = žádný aktivní trénink (žádná série za posledních 45 min).
     */
    fun restLeft(lastSetAt: Long, now: Long, extraMs: Long = 0): Long? {
        if (lastSetAt <= 0 || now < lastSetAt || now - lastSetAt > ACTIVE_WINDOW_MS) return null
        return lastSetAt + REST_MS + extraMs - now
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
            finishedAt = getLong(K_FINISHED, 0),
            muscles = getString(K_MUSCLES, "").orEmpty().split(',').mapNotNull { n -> Muscle.entries.firstOrNull { it.name == n } }.toSet()
        )
    }

    fun active(ctx: Context): Entry? = today(ctx)?.takeIf { it.running() }

    private fun save(ctx: Context, kind: String, variant: Char?, time: String, startedAt: Long, muscles: Set<Muscle>) =
        prefs(ctx).edit()
            .putString(K_MUSCLES, muscles.joinToString(",") { it.name })
            .putString(K_DAY, today()).putString(K_KIND, kind).putString(K_VARIANT, variant?.toString())
            .putString(K_TIME, time).putLong(K_STARTED, startedAt).putLong(K_FINISHED, 0)
            .apply()

    /** „Za 15 min“ / „Vlastní čas“: jen naplánuje na dnešek. */
    fun plan(ctx: Context, kind: String, variant: Char?, time: String, muscles: Set<Muscle> = emptySet()) =
        save(ctx, kind, variant, time, 0, muscles)

    /** „Teď“: trénink rovnou běží. */
    fun start(ctx: Context, kind: String, variant: Char?, muscles: Set<Muscle> = emptySet()) {
        val now = System.currentTimeMillis()
        save(ctx, kind, variant, SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(now)), now, muscles)
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
