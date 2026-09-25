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
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.materialswitch.MaterialSwitch
import android.content.res.ColorStateList
import androidx.core.widget.NestedScrollView
import cz.uhk.macroflow.training.equipment.EquipmentView
import cz.uhk.macroflow.training.equipment.Rig
import cz.uhk.macroflow.training.equipment.RigMath
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
 * RIR (opakování v rezervě) se převezme z poslední dnešní série, jinak výchozí [StrengthModel.DEFAULT_RIR].
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
        // s obrázkem náčiní je panel vyšší – na menších displejích se musí dát posouvat
        dialog.setContentView(NestedScrollView(ctx).apply { addView(v) })
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
        val tvSub = v.findViewById<TextView>(R.id.tvLogSetSub)

        // ── Náčiní (docs/adr/0028) ──
        val prefs = ctx.getSharedPreferences(RIG_PREFS, Context.MODE_PRIVATE)
        var rig: Rig? = Rig.resolve(exercise, prefs.getString("rig_${exercise.id}", null))
        val eqView = v.findViewById<EquipmentView>(R.id.equipmentView)
        val tvEq = v.findViewById<TextView>(R.id.tvEquipmentSummary)
        val chipsEq = v.findViewById<ChipGroup>(R.id.chipsEquipment)
        fun subText() = when {
            bodyweight -> "Váha = přidaná zátěž (vesta, kotouč); 0 = jen vlastní váha"
            rig != null -> rig!!.weightHint
            exercise.equipment == Equipment.DUMBBELL -> "Váha jedné jednoručky"
            exercise.equipment == Equipment.BARBELL -> "Celková váha včetně osy"
            else -> "Váha na stroji / kladce"
        } + " · ${todaySets.size + 1}. série" + (template?.let { " · ${WorkoutTemplates.label(it)}" } ?: "")
        tvSub.text = subText()
        val options = Rig.options(exercise)
        v.findViewById<View>(R.id.cardEquipment).visibility = if (rig == null) View.GONE else View.VISIBLE
        val dark = ctx.getColor(R.color.brand_dark); val cream = ctx.getColor(R.color.brand_cream)
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        if (options.size > 1) options.forEach { o ->
            chipsEq.addView(Chip(ctx).apply {
                id = View.generateViewId(); tag = o
                text = o.label; isCheckable = true; isCheckedIconVisible = false; isChecked = o == rig
                chipBackgroundColor = ColorStateList(states, intArrayOf(dark, ctx.getColor(R.color.brand_primary_alpha10)))
                setTextColor(ColorStateList(states, intArrayOf(cream, dark)))
                chipStrokeWidth = 0f
            })
        } else chipsEq.visibility = View.GONE

        val etW = v.findViewById<EditText>(R.id.etWeight)
        val etR = v.findViewById<EditText>(R.id.etReps)
        val swSlow = v.findViewById<MaterialSwitch>(R.id.swLogSlow)
        swSlow.isChecked = lastToday?.slowEccentric ?: false
        v.findViewById<View>(R.id.rowLogSlow).setOnClickListener { swSlow.toggle() }
        val tvRir = v.findViewById<TextView>(R.id.tvRir)
        var rir = (lastToday?.rir ?: StrengthModel.DEFAULT_RIR).coerceIn(0, StrengthModel.MAX_RIR)
        val tvE = v.findViewById<TextView>(R.id.tvLogSetE1rm)
        fun w() = etW.text.toString().replace(',', '.').toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
        fun r() = etR.text.toString().toIntOrNull()?.coerceAtLeast(0) ?: 0
        val prevBest = history.filter { it.exerciseId == exercise.id }.mapNotNull { StrengthModel.setEstimate(it) }.maxOrNull()

        fun refreshEquipment() {
            val r = rig ?: return
            eqView.set(r, w())
            tvEq.text = RigMath.summary(r, w())
        }
        chipsEq.setOnCheckedStateChangeListener { group, ids ->
            val chosen = ids.firstOrNull()?.let { group.findViewById<View>(it)?.tag as? Rig } ?: return@setOnCheckedStateChangeListener
            rig = chosen
            prefs.edit().putString("rig_${exercise.id}", chosen.name).apply()
            tvSub.text = subText()
            refreshEquipment()
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }

        fun refresh() {
            refreshEquipment()
            val probe = LoggedSet(day = 0, exerciseId = exercise.id, weightKg = w(), reps = r(), slowEccentric = swSlow.isChecked, rir = rir)
            val e1 = StrengthModel.setEstimate(probe)
            tvE.text = when {
                r() <= 0 -> "Zadej počet opakování"
                w() <= 0.0 -> "${r()} opakování s vlastní vahou"
                e1 == null -> "Přes ${StrengthModel.MAX_REPS} opakování – do odhadu 1RM se nepočítá"
                prevBest != null && e1 > prevBest -> "Odhad 1RM ${kg(e1)} kg – nový rekord 🏆"
                else -> "Odhad 1RM ${kg(e1)} kg"
            }
            v.findViewById<View>(R.id.btnLogSetSave).isEnabled = r() > 0
            tvRir.text = if (rir >= StrengthModel.MAX_RIR) "${StrengthModel.MAX_RIR}+" else rir.toString()
            v.findViewById<View>(R.id.btnRirMinus).alpha = if (rir > 0) 1f else 0.35f
            v.findViewById<View>(R.id.btnRirPlus).alpha = if (rir < StrengthModel.MAX_RIR) 1f else 0.35f
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
        v.findViewById<View>(R.id.btnRirMinus).setOnClickListener { rir = (rir - 1).coerceAtLeast(0); refresh() }
        v.findViewById<View>(R.id.btnRirPlus).setOnClickListener { rir = (rir + 1).coerceAtMost(StrengthModel.MAX_RIR); refresh() }
        refresh()

        v.findViewById<View>(R.id.btnLogSetSave).setOnClickListener {
            val weight = w(); val reps = r()
            if (reps <= 0) return@setOnClickListener
            val entity = WorkoutSetEntity(
                date = LocalDate.now().toString(), createdAt = System.currentTimeMillis(),
                exerciseId = exercise.id, weightKg = weight, reps = reps,
                slowEccentric = swSlow.isChecked, template = template, rir = rir
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

    private const val RIG_PREFS = "EquipmentPrefs"

    fun kg(v: Double) = String.format(Locale.US, "%.1f", v).replace('.', ',').removeSuffix(",0")
    private fun plain(v: Double) = String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')
}
