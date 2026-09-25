package cz.uhk.macroflow.training.log

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import cz.uhk.macroflow.R
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.data.WorkoutSetEntity
import cz.uhk.macroflow.training.atlas.ExerciseDetailSheet
import cz.uhk.macroflow.training.exercises.ExerciseLibrary
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Trénink podle šablony (docs/adr/0024): PUSH/PULL/LEGS A/B. U každého cviku model síly
 * (1RM teď, trend), na jakou váhu je uživatel dnes připraven, předpověď na příště, minulý
 * trénink a dnešní série. Varianta A/B se střídá automaticky, jde přepnout ručně.
 */
class WorkoutSessionSheet : BottomSheetDialogFragment() {

    companion object {
        private const val ARG_KIND = "kind"
        private const val ARG_VARIANT = "variant"
        private const val TAG = "WorkoutSessionSheet"

        fun show(fm: FragmentManager, kind: WorkoutTemplates.Kind, variant: Char) {
            if (fm.findFragmentByTag(TAG) != null) return
            WorkoutSessionSheet().apply { arguments = bundleOf(ARG_KIND to kind.name, ARG_VARIANT to variant.toString()) }.show(fm, TAG)
        }
    }

    private lateinit var kind: WorkoutTemplates.Kind
    private var variant = 'A'
    private var observeJob: Job? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.sheet_workout_session, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        kind = WorkoutTemplates.Kind.valueOf(requireArguments().getString(ARG_KIND)!!)
        variant = (savedInstanceState?.getString(ARG_VARIANT) ?: requireArguments().getString(ARG_VARIANT) ?: "A").first()

        view.findViewById<View>(R.id.btnCloseSession).setOnClickListener { dismiss() }
        view.findViewById<View>(R.id.btnEditTemplate).setOnClickListener {
            TemplateEditorSheet.show(childFragmentManager, key())
        }
        val toggle = view.findViewById<MaterialButtonToggleGroup>(R.id.toggleVariant)
        toggle.check(if (variant == 'A') R.id.btnVariantA else R.id.btnVariantB)
        toggle.addOnButtonCheckedListener { _, id, checked ->
            if (!checked) return@addOnButtonCheckedListener
            variant = if (id == R.id.btnVariantA) 'A' else 'B'
            observe()
        }
        observe()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(ARG_VARIANT, variant.toString())
    }

    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)?.let { d ->
            d.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)?.let { sheet ->
                sheet.setBackgroundColor(Color.TRANSPARENT)
                sheet.layoutParams = sheet.layoutParams.apply { height = ViewGroup.LayoutParams.MATCH_PARENT }
            }
            d.behavior.skipCollapsed = true
            d.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    private fun key() = WorkoutTemplates.key(kind, variant)

    /** Série i šablona jako Flow – po zápisu, smazání nebo úpravě šablony se vše přepočítá. */
    private fun observe() {
        observeJob?.cancel()
        val dao = AppDatabase.getDatabase(requireContext()).workoutDao()
        observeJob = viewLifecycleOwner.lifecycleScope.launch {
            combine(dao.all(), dao.template(key())) { sets, rows -> sets to rows }.collect { (sets, rows) ->
                val ids = rows.map { it.exerciseId }.ifEmpty { WorkoutTemplates.DEFAULTS[key()].orEmpty() }
                render(sets, ids)
            }
        }
    }

    private fun render(entities: List<WorkoutSetEntity>, ids: List<String>) {
        val v = view ?: return
        val today = LocalDate.now()
        val todayDay = today.toEpochDay().toInt()
        val all = WorkoutRepository.toLogged(entities)

        v.findViewById<TextView>(R.id.tvSessionTitle).text = WorkoutTemplates.label(key())
        val lastOther = all.filter { WorkoutTemplates.parse(it.template)?.first == kind && it.day < todayDay }.maxByOrNull { it.day }
        v.findViewById<TextView>(R.id.tvSessionSub).text = buildString {
            append("${today.dayOfMonth}. ${today.monthValue}. · ${ids.size} cviků")
            lastOther?.let { append(" · minule ${WorkoutTemplates.label(it.template!!)} (${date(it.day)})") }
        }

        val box = v.findViewById<LinearLayout>(R.id.llSessionExercises)
        box.removeAllViews()
        ids.mapNotNull { ExerciseLibrary.byId(it) }.forEachIndexed { i, e ->
            val insight = ExerciseInsight.of(e, all, todayDay)
            val row = layoutInflater.inflate(R.layout.item_session_exercise, box, false)
            row.findViewById<TextView>(R.id.tvSxIndex).text = (i + 1).toString()
            row.findViewById<TextView>(R.id.tvSxName).text = e.name
            val range = Progression.repRange(e)
            row.findViewById<TextView>(R.id.tvSxModel).text = insight.estimate?.let { est ->
                val pm = ((est.upper - est.lower) / 2).roundToInt()
                val trend = est.weeklyChangePct.let { t -> (if (t >= 0) "+" else "−") + String.format(java.util.Locale.US, "%.1f", kotlin.math.abs(t)).replace('.', ',') }
                "1RM ≈ ${LogSetSheet.kg(est.e1rm)} kg (±$pm) · $trend % týdně · ${est.sessions}× zapsáno"
            } ?: "Rozsah ${range.min}–${range.max} opakování · zatím bez zápisu"
            row.findViewById<View>(R.id.rowExHeader).setOnClickListener { ExerciseDetailSheet.show(childFragmentManager, e.id) }

            row.findViewById<TextView>(R.id.tvSxReady).text = insight.readyWeightKg?.let { "${LogSetSheet.kg(it)} kg × ${insight.readyReps}" }
                ?: if (Progression.increment(e) == 0.0) "${insight.readyReps} opakování" else "${range.min}–${range.max} ×"
            row.findViewById<TextView>(R.id.tvSxNext).text = insight.nextEstimate?.takeIf { insight.estimate != null }?.let {
                "Příště (~${insight.nextInDays} d)\n1RM ≈ ${LogSetSheet.kg(it.e1rm)} kg"
            } ?: insight.suggestion?.reason ?: "Začni s rezervou 2 opakování – příště poradím."

            row.findViewById<TextView>(R.id.tvSxLast).apply {
                val last = insight.last
                visibility = if (last != null) View.VISIBLE else View.GONE
                text = last?.let { s -> "Minule (${date(s.day)}): " + s.sets.joinToString(" · ") { label(it.weightKg, it.reps, it.slowEccentric, it.rir) } } ?: ""
            }

            val todaySets = entities.filter { it.exerciseId == e.id && it.date == today.toString() }.sortedBy { it.createdAt }
            val chips = row.findViewById<ChipGroup>(R.id.chipsSxToday)
            todaySets.forEachIndexed { n, s ->
                chips.addView(Chip(requireContext()).apply {
                    text = "${n + 1}.  ${label(s.weightKg, s.reps, s.slowEccentric, s.rir)}"
                    isCloseIconVisible = true
                    closeIconContentDescription = "Smazat sérii"
                    chipBackgroundColor = ContextCompat.getColorStateList(requireContext(), R.color.brand_primary_alpha10)
                    setTextColor(ContextCompat.getColor(requireContext(), R.color.brand_dark))
                    chipStrokeWidth = 0f
                    setOnCloseIconClickListener { viewLifecycleOwner.lifecycleScope.launch { WorkoutRepository.delete(requireContext().applicationContext, s) } }
                })
            }
            row.findViewById<TextView>(R.id.btnSxLog).apply {
                text = if (todaySets.isEmpty()) "Zapsat sérii" else "Zapsat ${todaySets.size + 1}. sérii"
                setOnClickListener {
                    LogSetSheet.show(requireContext(), viewLifecycleOwner.lifecycleScope, e, key(), all, todaySets,
                        prefillWeight = insight.readyWeightKg ?: insight.suggestion?.weightKg, prefillReps = insight.readyReps)
                }
            }
            box.addView(row)
        }
    }

    private fun label(w: Double, r: Int, slow: Boolean, rir: Int = StrengthModel.DEFAULT_RIR) =
        (if (w <= 0.0) "$r ×" else "${LogSetSheet.kg(w)} kg × $r") + listOfNotNull(if (slow) "pomalu" else null, if (rir != StrengthModel.DEFAULT_RIR) "RIR $rir" else null)
            .takeIf { it.isNotEmpty() }?.joinToString(", ", " (", ")").orEmpty()

    private fun date(day: Int) = LocalDate.ofEpochDay(day.toLong()).let { "${it.dayOfMonth}. ${it.monthValue}." }
}
