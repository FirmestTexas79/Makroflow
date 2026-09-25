package cz.uhk.macroflow.training.log

import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import cz.uhk.macroflow.R
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.data.FirebaseRepository
import cz.uhk.macroflow.data.WorkoutSetEntity
import cz.uhk.macroflow.training.exercises.Exercise
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

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

    private fun logged(): List<LoggedSet> =
        sets.groupBy { it.date }.flatMap { (_, day) -> day.sortedBy { it.createdAt }.mapIndexed { i, s -> s.toLogged(i) } }

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
            text = last?.let { s -> "Minule (${date(s.day)}): " + s.sets.joinToString(" · ") { setLabel(it.weightKg, it.reps) } } ?: ""
        }

        // Dnešní série
        val todaySets = sets.filter { it.date == today().toString() }.sortedBy { it.createdAt }
        root.findViewById<View>(R.id.tvLogTodayLabel).visibility = if (todaySets.isEmpty()) View.GONE else View.VISIBLE
        val chips = root.findViewById<ChipGroup>(R.id.chipsLogToday)
        chips.removeAllViews()
        todaySets.forEachIndexed { i, s ->
            chips.addView(Chip(ctx).apply {
                text = "${i + 1}.  ${setLabel(s.weightKg, s.reps)}"
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
        fragment.viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            dao.delete(s)
            runCatching { FirebaseRepository.deleteWorkoutSet(s.createdAt) }
        }
    }

    // ── Zápis série ─────────────────────────────────────────────────────────

    private fun showLogSheet() {
        val dialog = BottomSheetDialog(ctx)
        val v = View.inflate(ctx, R.layout.sheet_log_set, null)
        dialog.setContentView(v)
        dialog.setOnShowListener {
            dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.setBackgroundColor(Color.TRANSPARENT)
            dialog.behavior.skipCollapsed = true; dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }

        val all = logged()
        val todaySets = sets.filter { it.date == today().toString() }.sortedBy { it.createdAt }
        val suggestion = Progression.suggest(exercise, Progression.lastSession(all, exercise.id, today().toEpochDay().toInt()))
        val range = Progression.repRange(exercise)
        val inc = Progression.increment(exercise)
        val bodyweight = inc == 0.0

        // Výchozí hodnoty: poslední dnešní série → návrh → prázdná váha a spodní hranice rozsahu
        val startW = todaySets.lastOrNull()?.weightKg ?: suggestion?.weightKg ?: 0.0
        val startR = todaySets.lastOrNull()?.reps ?: suggestion?.reps ?: range.min

        v.findViewById<TextView>(R.id.tvLogSetTitle).text = exercise.name
        v.findViewById<TextView>(R.id.tvLogSetSub).text = when {
            bodyweight -> "Váha = přidaná zátěž (vesta, kotouč); 0 = jen vlastní váha"
            exercise.equipment.name == "DUMBBELL" -> "Váha jedné jednoručky"
            else -> "Celková váha včetně osy"
        } + " · ${todaySets.size + 1}. série"

        val etW = v.findViewById<EditText>(R.id.etWeight)
        val etR = v.findViewById<EditText>(R.id.etReps)
        val tvE = v.findViewById<TextView>(R.id.tvLogSetE1rm)
        fun w() = etW.text.toString().replace(',', '.').toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
        fun r() = etR.text.toString().toIntOrNull()?.coerceAtLeast(0) ?: 0
        fun refresh() {
            val prevBest = Progression.best(all)
            val e1 = Progression.e1rm(w(), r())
            tvE.text = when {
                r() <= 0 -> "Zadej počet opakování"
                w() <= 0.0 -> "${r()} opakování s vlastní vahou"
                prevBest != null && e1 > Progression.e1rm(prevBest.weightKg, prevBest.reps) -> "Odhad 1RM ${kg(e1)} kg – nový rekord 🏆"
                else -> "Odhad 1RM ${kg(e1)} kg"
            }
            v.findViewById<View>(R.id.btnLogSetSave).isEnabled = r() > 0
        }
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) = refresh()
        }
        etW.setText(kgPlain(startW)); etR.setText(startR.toString())
        etW.addTextChangedListener(watcher); etR.addTextChangedListener(watcher)
        val wStep = if (inc > 0.0) inc else 2.5
        v.findViewById<View>(R.id.btnWeightMinus).setOnClickListener { etW.setText(kgPlain((w() - wStep).coerceAtLeast(0.0))) }
        v.findViewById<View>(R.id.btnWeightPlus).setOnClickListener { etW.setText(kgPlain(w() + wStep)) }
        v.findViewById<View>(R.id.btnRepsMinus).setOnClickListener { etR.setText((r() - 1).coerceAtLeast(1).toString()) }
        v.findViewById<View>(R.id.btnRepsPlus).setOnClickListener { etR.setText((r() + 1).toString()) }
        refresh()

        v.findViewById<View>(R.id.btnLogSetSave).setOnClickListener {
            val weight = w(); val reps = r()
            if (reps <= 0) return@setOnClickListener
            val entity = WorkoutSetEntity(
                date = today().toString(), createdAt = System.currentTimeMillis(),
                exerciseId = exercise.id, weightKg = weight, reps = reps
            )
            val isPr = Progression.isPersonalRecord(entity.toLogged(), all)
            dialog.dismiss()
            root.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            fragment.viewLifecycleOwner.lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    dao.insert(entity)
                    runCatching { FirebaseRepository.uploadWorkoutSet(entity) }
                }
                if (isPr) Toast.makeText(ctx, "Nový rekord: ${kg(Progression.e1rm(weight, reps))} kg (odhad 1RM) 🏆", Toast.LENGTH_LONG).show()
            }
        }
        dialog.show()
    }

    // ── Formát ──────────────────────────────────────────────────────────────

    private fun kg(v: Double) = String.format(Locale.US, "%.1f", v).replace('.', ',').removeSuffix(",0")
    private fun kgPlain(v: Double) = String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')

    private fun setLabel(w: Double, r: Int) = if (w <= 0.0) "$r ×" else "${kg(w)} kg × $r"

    private fun date(day: Int) = LocalDate.ofEpochDay(day.toLong()).let { "${it.dayOfMonth}. ${it.monthValue}." }
}
