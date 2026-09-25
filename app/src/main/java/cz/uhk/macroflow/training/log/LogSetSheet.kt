package cz.uhk.macroflow.training.log

import android.content.Context
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.LifecycleCoroutineScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.materialswitch.MaterialSwitch
import cz.uhk.macroflow.R
import cz.uhk.macroflow.data.WorkoutSetEntity
import cz.uhk.macroflow.training.exercises.Equipment
import cz.uhk.macroflow.training.exercises.Exercise
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale

/**
 * Panel „Zapsat sérii“ (docs/adr/0023, 0024) – z atlasu i z tréninku podle šablony.
 * Předvyplnění: poslední dnešní série → [prefillWeight]/[prefillReps] (připravenost) → spodní hranice rozsahu.
 */
object LogSetSheet {

    fun show(
        ctx: Context,
        scope: LifecycleCoroutineScope,
        exercise: Exercise,
        template: String?,
        history: List<LoggedSet>,
        todaySets: List<WorkoutSetEntity>,
        prefillWeight: Double? = null,
        prefillReps: Int? = null
    ) {
        val dialog = BottomSheetDialog(ctx)
        val v = View.inflate(ctx, R.layout.sheet_log_set, null)
        dialog.setContentView(v)
        dialog.setOnShowListener {
            dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.setBackgroundColor(Color.TRANSPARENT)
            dialog.behavior.skipCollapsed = true; dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }

        val range = Progression.repRange(exercise)
        val inc = Progression.increment(exercise)
        val bodyweight = inc == 0.0
        val lastToday = todaySets.maxByOrNull { it.createdAt }
        val startW = lastToday?.weightKg ?: prefillWeight ?: 0.0
        val startR = lastToday?.reps ?: prefillReps ?: range.min

        v.findViewById<TextView>(R.id.tvLogSetTitle).text = exercise.name
        v.findViewById<TextView>(R.id.tvLogSetSub).text = when {
            bodyweight -> "Váha = přidaná zátěž (vesta, kotouč); 0 = jen vlastní váha"
            exercise.equipment == Equipment.DUMBBELL -> "Váha jedné jednoručky"
            exercise.equipment == Equipment.BARBELL -> "Celková váha včetně osy"
            else -> "Váha na stroji / kladce"
        } + " · ${todaySets.size + 1}. série" + (template?.let { " · ${WorkoutTemplates.label(it)}" } ?: "")

        val etW = v.findViewById<EditText>(R.id.etWeight)
        val etR = v.findViewById<EditText>(R.id.etReps)
        val swSlow = v.findViewById<MaterialSwitch>(R.id.swLogSlow)
        swSlow.isChecked = lastToday?.slowEccentric ?: false
        v.findViewById<View>(R.id.rowLogSlow).setOnClickListener { swSlow.toggle() }
        val tvE = v.findViewById<TextView>(R.id.tvLogSetE1rm)
        fun w() = etW.text.toString().replace(',', '.').toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
        fun r() = etR.text.toString().toIntOrNull()?.coerceAtLeast(0) ?: 0
        val prevBest = history.filter { it.exerciseId == exercise.id }.mapNotNull { StrengthModel.setEstimate(it) }.maxOrNull()

        fun refresh() {
            val probe = LoggedSet(day = 0, exerciseId = exercise.id, weightKg = w(), reps = r(), slowEccentric = swSlow.isChecked)
            val e1 = StrengthModel.setEstimate(probe)
            tvE.text = when {
                r() <= 0 -> "Zadej počet opakování"
                w() <= 0.0 -> "${r()} opakování s vlastní vahou"
                e1 == null -> "Přes ${StrengthModel.MAX_REPS} opakování – do odhadu 1RM se nepočítá"
                prevBest != null && e1 > prevBest -> "Odhad 1RM ${kg(e1)} kg – nový rekord 🏆"
                else -> "Odhad 1RM ${kg(e1)} kg"
            }
            v.findViewById<View>(R.id.btnLogSetSave).isEnabled = r() > 0
        }
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) = refresh()
        }
        etW.setText(plain(startW)); etR.setText(startR.toString())
        etW.addTextChangedListener(watcher); etR.addTextChangedListener(watcher)
        swSlow.setOnCheckedChangeListener { _, _ -> refresh() }
        val wStep = if (inc > 0.0) inc else 2.5
        v.findViewById<View>(R.id.btnWeightMinus).setOnClickListener { etW.setText(plain((w() - wStep).coerceAtLeast(0.0))) }
        v.findViewById<View>(R.id.btnWeightPlus).setOnClickListener { etW.setText(plain(w() + wStep)) }
        v.findViewById<View>(R.id.btnRepsMinus).setOnClickListener { etR.setText((r() - 1).coerceAtLeast(1).toString()) }
        v.findViewById<View>(R.id.btnRepsPlus).setOnClickListener { etR.setText((r() + 1).toString()) }
        refresh()

        v.findViewById<View>(R.id.btnLogSetSave).setOnClickListener {
            val weight = w(); val reps = r()
            if (reps <= 0) return@setOnClickListener
            val entity = WorkoutSetEntity(
                date = LocalDate.now().toString(), createdAt = System.currentTimeMillis(),
                exerciseId = exercise.id, weightKg = weight, reps = reps,
                slowEccentric = swSlow.isChecked, template = template
            )
            val e1 = StrengthModel.setEstimate(entity.toLogged())
            val isPr = e1 != null && prevBest != null && e1 > prevBest
            dialog.dismiss()
            v.rootView.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            scope.launch {
                WorkoutRepository.log(ctx.applicationContext, entity)
                if (isPr) Toast.makeText(ctx, "Nový rekord: odhad 1RM ${kg(e1!!)} kg 🏆", Toast.LENGTH_LONG).show()
            }
        }
        dialog.show()
    }

    fun kg(v: Double) = String.format(Locale.US, "%.1f", v).replace('.', ',').removeSuffix(",0")
    private fun plain(v: Double) = String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')
}
