package cz.uhk.macroflow.training.log

import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import cz.uhk.macroflow.R
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.data.WorkoutSetEntity
import cz.uhk.macroflow.training.exercises.Exercise
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale

/**
 * Sekce „Můj deník“ v detailu cviku (docs/adr/0023): rekord (odhad 1RM), návrh na příště,
 * minulý trénink, dnešní série (podržením smazat) a zápis nové série. Logika v [Progression].
 */
class WorkoutLogSection(private val fragment: Fragment, private val root: View, private val exercise: Exercise) {

    private val ctx get() = fragment.requireContext()
    private val dao get() = AppDatabase.getDatabase(ctx).workoutDao()
    private var sets: List<WorkoutSetEntity> = emptyList()

    fun start() {
        root.findViewById<View>(R.id.btnLogSet).setOnClickListener { showLogSheet() }
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            dao.forExercise(exercise.id).collect { sets = it; render() }
        }
    }

    private fun today() = LocalDate.now()

    private fun logged(): List<LoggedSet> = WorkoutRepository.toLogged(sets)

    private fun render() {
        val all = logged()
        val todayDay = today().toEpochDay().toInt()
        val range = Progression.repRange(exercise)
        val bodyweight = Progression.increment(exercise) == 0.0

        // Rekord
        val best = Progression.best(all.filter { it.weightKg > 0 || bodyweight })
        root.findViewById<TextView>(R.id.tvLogBest).text = when {
            best == null -> "–"
            bodyweight && best.weightKg == 0.0 -> "${all.maxOf { it.reps }} ×"
            else -> "${kg(Progression.e1rm(best.weightKg, best.reps))} kg"
        }
        root.findViewById<TextView>(R.id.tvLogBestSub).text = best?.let {
            "${setLabel(it.weightKg, it.reps)} · ${date(it.day)}"
        } ?: "zatím bez zápisu"

        // Návrh na příště
        val last = Progression.lastSession(all, exercise.id, beforeDay = todayDay)
        val suggestion = Progression.suggest(exercise, last)
        root.findViewById<TextView>(R.id.tvLogNext).text = suggestion?.let { setLabel(it.weightKg, it.reps) } ?: "${range.min}–${range.max} ×"
        root.findViewById<TextView>(R.id.tvLogRange).text = "rozsah ${range.min}–${range.max} opakování"
        root.findViewById<TextView>(R.id.tvLogReason).text = suggestion?.reason
            ?: "Začni vahou, se kterou zvládneš ${range.max} opakování s 1–2 v rezervě. Příště ti poradím, kdy přidat."
        root.findViewById<TextView>(R.id.tvLogLast).apply {
            visibility = if (last != null) View.VISIBLE else View.GONE
            text = last?.let { s -> "Minule (${date(s.day)}): " + s.sets.joinToString(" · ") { setLabel(it.weightKg, it.reps, it.slowEccentric, it.rir) } } ?: ""
        }

        // Dnešní série
        val todaySets = sets.filter { it.date == today().toString() }.sortedBy { it.createdAt }
        root.findViewById<View>(R.id.tvLogTodayLabel).visibility = if (todaySets.isEmpty()) View.GONE else View.VISIBLE
        val chips = root.findViewById<ChipGroup>(R.id.chipsLogToday)
        chips.removeAllViews()
        todaySets.forEachIndexed { i, s ->
            chips.addView(Chip(ctx).apply {
                text = "${i + 1}.  ${setLabel(s.weightKg, s.reps, s.slowEccentric, s.rir)}"
                isCloseIconVisible = true
                closeIconContentDescription = "Smazat sérii"
                chipBackgroundColor = ContextCompat.getColorStateList(ctx, R.color.brand_primary_alpha10)
                setTextColor(ContextCompat.getColor(ctx, R.color.brand_dark))
                chipStrokeWidth = 0f
                setOnCloseIconClickListener { delete(s) }
            })
        }
        root.findViewById<TextView>(R.id.btnLogSet).text = if (todaySets.isEmpty()) "Zapsat sérii" else "Zapsat ${todaySets.size + 1}. sérii"
    }

    private fun delete(s: WorkoutSetEntity) {
        fragment.viewLifecycleOwner.lifecycleScope.launch { WorkoutRepository.delete(ctx.applicationContext, s) }
    }

    // ── Zápis série ─────────────────────────────────────────────────────────

    private fun showLogSheet() {
        val all = logged()
        val suggestion = Progression.suggest(exercise, Progression.lastSession(all, exercise.id, today().toEpochDay().toInt()))
        LogSetSheet.show(
            ctx, fragment.viewLifecycleOwner.lifecycleScope, exercise, template = null, history = all,
            todaySets = sets.filter { it.date == today().toString() },
            prefillWeight = suggestion?.weightKg, prefillReps = suggestion?.reps
        )
    }

    // ── Formát ──────────────────────────────────────────────────────────────

    private fun kg(v: Double) = String.format(Locale.US, "%.1f", v).replace('.', ',').removeSuffix(",0")

    private fun setLabel(w: Double, r: Int, slow: Boolean = false, rir: Int = StrengthModel.DEFAULT_RIR) =
        (if (w <= 0.0) "$r ×" else "${kg(w)} kg × $r") + listOfNotNull(if (slow) "pomalu" else null, if (rir != StrengthModel.DEFAULT_RIR) "RIR $rir" else null)
            .takeIf { it.isNotEmpty() }?.joinToString(", ", " (", ")").orEmpty()

    private fun date(day: Int) = LocalDate.ofEpochDay(day.toLong()).let { "${it.dayOfMonth}. ${it.monthValue}." }
}
