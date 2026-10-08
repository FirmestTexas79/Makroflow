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
        private const val ARG_IDS = "ids"
        private const val ARG_TITLE = "title"
        /** Šablona zapsaná u sérií z vlastního výběru cviků („Jiný trénink“). */
        const val CUSTOM_TEMPLATE = "OTHER"
        private const val TAG = "WorkoutSessionSheet"
        /** Jak dlouho po konci pauzy ještě svítí „Další série“. */
        private const val OVER_SHOWN_MS = 90_000L

        fun show(fm: FragmentManager, kind: WorkoutTemplates.Kind, variant: Char) {
            if (fm.findFragmentByTag(TAG) != null) return
            WorkoutSessionSheet().apply { arguments = bundleOf(ARG_KIND to kind.name, ARG_VARIANT to variant.toString()) }.show(fm, TAG)
        }

        /** Vlastní trénink z vybraných cviků (např. podle partií z „Jdu na trénink“), bez šablony A/B. */
        fun showCustom(fm: FragmentManager, title: String, exerciseIds: List<String>) {
            if (fm.findFragmentByTag(TAG) != null) return
            WorkoutSessionSheet().apply { arguments = bundleOf(ARG_TITLE to title, ARG_IDS to ArrayList(exerciseIds)) }.show(fm, TAG)
        }
    }

    private lateinit var kind: WorkoutTemplates.Kind
    private var customIds: List<String>? = null
    /** Počet dnešních sérií při posledním vykreslení – přírůstek = nová série → bublina do Makrosvěta. */
    private var lastTodayCount: Int? = null
    private var variant = 'A'
    private var observeJob: Job? = null

    // ── Pauza po sérii (docs/adr/0071) ──────────────────────────────────────
    /** Poslední dnes zapsaná série – podle jejího cviku se počítá délka pauzy. */
    private var lastSet: WorkoutSetEntity? = null
    private var restExtraMs = 0L
    private var restSkippedFor = 0L
    private var restAlertedFor = 0L
    private val restTick = object : Runnable {
        override fun run() { renderRest(); view?.postDelayed(this, 500) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.sheet_workout_session, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        customIds = requireArguments().getStringArrayList(ARG_IDS)
        kind = WorkoutTemplates.Kind.valueOf(requireArguments().getString(ARG_KIND) ?: WorkoutTemplates.Kind.PUSH.name)
        variant = (savedInstanceState?.getString(ARG_VARIANT) ?: requireArguments().getString(ARG_VARIANT) ?: "A").first()

        view.findViewById<View>(R.id.btnCloseSession).setOnClickListener { dismiss() }
        view.findViewById<View>(R.id.btnEditTemplate).setOnClickListener {
            TemplateEditorSheet.show(childFragmentManager, key())
        }
        val toggle = view.findViewById<MaterialButtonToggleGroup>(R.id.toggleVariant)
        if (customIds != null) {
            toggle.visibility = View.GONE
            view.findViewById<View>(R.id.btnEditTemplate).visibility = View.GONE
        }
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

    private fun key() = if (customIds != null) CUSTOM_TEMPLATE else WorkoutTemplates.key(kind, variant)

    /** Série i šablona jako Flow – po zápisu, smazání nebo úpravě šablony se vše přepočítá. */
    private fun observe() {
        observeJob?.cancel()
        val dao = AppDatabase.getDatabase(requireContext()).workoutDao()
        observeJob = viewLifecycleOwner.lifecycleScope.launch {
            combine(dao.all(), dao.template(key())) { sets, rows -> sets to rows }.collect { (sets, rows) ->
                val ids = customIds ?: rows.map { it.exerciseId }.ifEmpty { WorkoutTemplates.DEFAULTS[key()].orEmpty() }
                render(sets, ids)
            }
        }
    }

    private fun render(entities: List<WorkoutSetEntity>, ids: List<String>) {
        val v = view ?: return
        val today = LocalDate.now()
        val todayDay = today.toEpochDay().toInt()
        val all = WorkoutRepository.toLogged(entities)

        val todayCount = entities.count { it.date == today.toString() }
        entities.filter { it.date == today.toString() }.maxByOrNull { it.createdAt }.let { newest ->
            if (newest?.createdAt != lastSet?.createdAt) {
                lastSet = newest; restExtraMs = 0
                // pauza, která skončila ještě před otevřením okna, už nevibruje
                val restMs = cz.uhk.macroflow.common.AppSettings.restSecondsFor(requireContext(), newest?.exerciseId) * 1000L
                if (newest != null && System.currentTimeMillis() >= newest.createdAt + restMs && lastTodayCount == null) restAlertedFor = newest.createdAt
            }
        }
        v.removeCallbacks(restTick); restTick.run()
        lastTodayCount?.let { if (todayCount > it && cz.uhk.macroflow.common.AppSettings.gymBubble(requireContext())) MakrosvetBubble.show(v, todayCount) }
        lastTodayCount = todayCount

        v.findViewById<TextView>(R.id.tvSessionTitle).text = requireArguments().getString(ARG_TITLE) ?: WorkoutTemplates.label(key())
        val lastOther = if (customIds != null) null
            else all.filter { WorkoutTemplates.parse(it.template)?.first == kind && it.day < todayDay }.maxByOrNull { it.day }
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

    /** Odpočet pauzy dole v okně; po jejím konci zavibruje a nabídne další sérii. */
    private fun renderRest() {
        val v = view ?: return
        val bar = v.findViewById<View>(R.id.restBar)
        val set = lastSet
        val ctx = requireContext()
        val restMs = cz.uhk.macroflow.common.AppSettings.restSecondsFor(ctx, set?.exerciseId) * 1000L
        val now = System.currentTimeMillis()
        val left = set?.takeIf { it.createdAt != restSkippedFor }?.let {
            cz.uhk.macroflow.training.QuickWorkout.restLeft(it.createdAt, now, restExtraMs, cz.uhk.macroflow.training.QuickWorkout.endedAt(ctx), restMs)
        }?.takeIf { it > -OVER_SHOWN_MS }
        if (set == null || left == null) { bar.visibility = View.GONE; return }
        bar.visibility = View.VISIBLE
        val name = ExerciseLibrary.byId(set.exerciseId)?.name ?: "série"
        val label = v.findViewById<TextView>(R.id.tvRestLabel)
        val time = v.findViewById<TextView>(R.id.tvRestTime)
        val more = v.findViewById<TextView>(R.id.btnRestMore)
        val progress = v.findViewById<View>(R.id.restProgress)
        more.setOnClickListener { restExtraMs += 30_000; it.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK); renderRest() }
        v.findViewById<View>(R.id.btnRestSkip).setOnClickListener { restSkippedFor = set.createdAt; renderRest() }
        if (left > 0) {
            val sec = (left + 999) / 1000
            label.text = "PAUZA · ${name.uppercase()}"
            time.text = "${sec / 60}:${String.format(java.util.Locale.US, "%02d", sec % 60)}"
            bar.backgroundTintList = ContextCompat.getColorStateList(ctx, R.color.brand_dark)
            label.setTextColor(ContextCompat.getColor(ctx, R.color.brand_accent_warm))
            time.setTextColor(ContextCompat.getColor(ctx, R.color.brand_cream))
            progress.scaleX = (1f - left.toFloat() / (restMs + restExtraMs)).coerceIn(0f, 1f)
            bar.contentDescription = "Pauza, zbývá $sec sekund"
            bar.setOnClickListener(null)
            return
        }
        label.text = "PAUZA SKONČILA"
        time.text = "Další série ▸"
        bar.backgroundTintList = ContextCompat.getColorStateList(ctx, R.color.brand_accent_warm)
        label.setTextColor(ContextCompat.getColor(ctx, R.color.brand_dark))
        time.setTextColor(ContextCompat.getColor(ctx, R.color.brand_dark))
        progress.scaleX = 1f
        bar.contentDescription = "Pauza skončila"
        bar.setOnClickListener { restSkippedFor = set.createdAt; renderRest() }
        val key = set.createdAt + restExtraMs
        if (restAlertedFor != key) {
            restAlertedFor = key
            vibrate()
            bar.scaleX = 0.92f; bar.scaleY = 0.92f
            bar.animate().scaleX(1f).scaleY(1f).setInterpolator(android.view.animation.OvershootInterpolator(3f)).setDuration(380).start()
        }
    }

    private fun vibrate() {
        val ctx = requireContext()
        @Suppress("DEPRECATION")
        val vib = if (android.os.Build.VERSION.SDK_INT >= 31)
            (ctx.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager)?.defaultVibrator
        else ctx.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
        vib?.vibrate(android.os.VibrationEffect.createWaveform(longArrayOf(0, 180, 120, 260), -1))
    }

    override fun onDestroyView() {
        view?.removeCallbacks(restTick)
        super.onDestroyView()
    }

    private fun label(w: Double, r: Int, slow: Boolean, rir: Int = StrengthModel.DEFAULT_RIR) =
        (if (w <= 0.0) "$r ×" else "${LogSetSheet.kg(w)} kg × $r") + listOfNotNull(if (slow) "pomalu" else null, if (rir != StrengthModel.DEFAULT_RIR) "RIR $rir" else null)
            .takeIf { it.isNotEmpty() }?.joinToString(", ", " (", ")").orEmpty()

    private fun date(day: Int) = LocalDate.ofEpochDay(day.toLong()).let { "${it.dayOfMonth}. ${it.monthValue}." }
}
