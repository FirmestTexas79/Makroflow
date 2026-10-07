package cz.uhk.macroflow.training

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import cz.uhk.macroflow.R
import cz.uhk.macroflow.common.MainActivity
import cz.uhk.macroflow.common.MakroflowTimePicker
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.training.body.BodyMapView
import cz.uhk.macroflow.training.body.Muscle
import cz.uhk.macroflow.training.log.LoggedSet
import cz.uhk.macroflow.training.log.WorkoutRepository
import cz.uhk.macroflow.training.log.WorkoutSessionSheet
import cz.uhk.macroflow.training.log.WorkoutTemplates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

/**
 * „Jdu na trénink“ (docs/adr/0065): klepnutím na partie se předvybere trénink a čas „za 15 min“,
 * jedním klepnutím jde přepnout na „teď“ nebo upravit čas. Když už trénink běží nebo je naplánovaný,
 * ukazuje stopky, zápis sérií a „Hotovo“.
 */
class QuickWorkoutSheet : BottomSheetDialogFragment() {

    companion object {
        const val RESULT = "quick_workout_changed"
        private const val TAG = "QuickWorkoutSheet"
        fun show(fm: FragmentManager) {
            if (fm.findFragmentByTag(TAG) == null) QuickWorkoutSheet().show(fm, TAG)
        }
    }

    private enum class When { NOW, IN_15, CUSTOM }

    private val kinds = WorkoutTemplates.Kind.entries.map { it.name } + listOf(QuickWorkout.CARDIO, QuickWorkout.OTHER)
    private val selected = linkedSetOf<Muscle>()
    private var kind: String? = null
    private var variant: Char? = null
    private var whenMode = When.IN_15
    private var custom: LocalTime = LocalTime.now().plusMinutes(15)
    private var history: List<LoggedSet> = emptyList()
    private var cancelArmed = false
    private val ticker = object : Runnable {
        override fun run() { renderRun(); view?.postDelayed(this, 1000) }
    }

    private fun c(id: Int) = ContextCompat.getColor(requireContext(), id)
    private val dp get() = resources.displayMetrics.density

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.sheet_quick_workout, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<View>(R.id.btnQwClose).setOnClickListener { dismiss() }
        val body = view.findViewById<BodyMapView>(R.id.bodyQw)
        body.selectedColor = c(R.color.brand_accent_deep)
        body.onMuscleTap = { m ->
            body.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            if (!selected.remove(m)) selected += m
            body.select(m.takeIf { it in selected })
            val suggestion = QuickWorkout.suggest(selected)
            if (suggestion != null) setKind(suggestion, lightMuscles = false) else { kind = null; render() }
        }
        view.findViewById<View>(R.id.btnQwGo).setOnClickListener { go() }
        view.findViewById<View>(R.id.btnQwDone).setOnClickListener { done() }
        view.findViewById<View>(R.id.btnQwCancel).setOnClickListener { cancel() }
        view.findViewById<View>(R.id.btnQwSets).setOnClickListener {
            val e = QuickWorkout.today(requireContext()) ?: return@setOnClickListener
            val k = e.strength ?: return@setOnClickListener
            WorkoutSessionSheet.show(parentFragmentManager, k, e.variant ?: 'A')
            dismiss()
        }
        val ctx = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            history = withContext(Dispatchers.IO) { WorkoutRepository.toLogged(AppDatabase.getDatabase(ctx).workoutDao().getAllSync()) }
            kind?.let { k -> if (variant == null) variant = nextVariant(k) }
            render()
        }
        render()
    }

    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)?.let { d ->
            d.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)?.setBackgroundColor(Color.TRANSPARENT)
            d.behavior.skipCollapsed = true
            d.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    override fun onResume() { super.onResume(); view?.post(ticker) }
    override fun onPause() { view?.removeCallbacks(ticker); super.onPause() }

    // ── Stav ────────────────────────────────────────────────────────────

    /** Dnešní trénink, který ještě není hotový (běží nebo čeká na start). */
    private fun pending(): QuickWorkout.Entry? =
        QuickWorkout.today(requireContext())?.takeIf { it.running() || (it.startedAt == 0L && it.finishedAt == 0L) }

    private fun strengthOf(k: String?) = WorkoutTemplates.Kind.entries.firstOrNull { it.name == k }

    private fun nextVariant(k: String): Char? = strengthOf(k)?.let {
        WorkoutTemplates.variantFor(it, history, LocalDate.now().toEpochDay().toInt())
    }

    private fun setKind(k: String, lightMuscles: Boolean) {
        val changed = k != kind
        kind = k
        if (changed) variant = nextVariant(k)
        if (lightMuscles) {
            selected.clear(); selected += QuickWorkout.musclesOf(k)
            view?.findViewById<BodyMapView>(R.id.bodyQw)?.select(null)
        }
        render()
    }

    private fun time(): LocalTime = when (whenMode) {
        When.NOW -> LocalTime.now()
        When.IN_15 -> LocalTime.now().plusMinutes(15)
        When.CUSTOM -> custom
    }

    private fun hhmm(t: LocalTime) = String.format(Locale.US, "%02d:%02d", t.hour, t.minute)

    private fun label(k: String, v: Char?) = when {
        k == QuickWorkout.CARDIO -> "Kardio"
        k == QuickWorkout.OTHER -> "Jiný trénink"
        else -> strengthOf(k)!!.let { s -> v?.let { WorkoutTemplates.label(WorkoutTemplates.key(s, it)) } ?: s.label }
    }

    // ── Vykreslení ──────────────────────────────────────────────────────

    private fun render() {
        val v = view ?: return
        val p = pending()
        v.findViewById<View>(R.id.groupQwPick).visibility = if (p == null) View.VISIBLE else View.GONE
        v.findViewById<View>(R.id.groupQwRun).visibility = if (p == null) View.GONE else View.VISIBLE
        if (p != null) { renderRun(); return }

        v.findViewById<TextView>(R.id.tvQwPill).text = "JDU NA TRÉNINK"
        v.findViewById<TextView>(R.id.tvQwTitle).text = kind?.let { label(it, variant) } ?: "Co dnes procvičíš?"
        v.findViewById<TextView>(R.id.tvQwSub).text =
            if (kind == null) "Klepni na partie, podle nich vyberu trénink." else "Klepnutím na postavu výběr upravíš."

        v.findViewById<BodyMapView>(R.id.bodyQw).setIntensities(selected.associateWith { 1.0 }, c(R.color.brand_accent_deep))
        v.findViewById<TextView>(R.id.tvQwMuscles).text =
            if (selected.isEmpty()) "Zatím nic nevybráno" else selected.joinToString(" · ") { it.label }

        // druh tréninku
        val kindsBox = v.findViewById<LinearLayout>(R.id.llQwKinds)
        kindsBox.removeAllViews()
        kinds.forEachIndexed { i, k ->
            val text = when (k) { QuickWorkout.CARDIO -> "KARDIO"; QuickWorkout.OTHER -> "JINÝ"; else -> k }
            kindsBox.addView(pill(text, k == kind, dark = false) { setKind(k, lightMuscles = true) },
                LinearLayout.LayoutParams(0, (44 * dp).toInt(), 1f).apply { if (i > 0) marginStart = (6 * dp).toInt() })
        }

        // varianta A/B jen u silových
        val strength = strengthOf(kind)
        v.findViewById<View>(R.id.llQwVariant).visibility = if (strength != null) View.VISIBLE else View.GONE
        if (strength != null) {
            val next = nextVariant(kind!!)
            v.findViewById<TextView>(R.id.tvQwVariant).text =
                if (variant == next) "Varianta $variant je na řadě" else "Na řadě je varianta $next"
            val box = v.findViewById<LinearLayout>(R.id.llQwVariantPills)
            box.removeAllViews()
            WorkoutTemplates.VARIANTS.forEachIndexed { i, vv ->
                box.addView(pill(vv.toString(), vv == variant, dark = false) { variant = vv; render() },
                    LinearLayout.LayoutParams((48 * dp).toInt(), (36 * dp).toInt()).apply { if (i > 0) marginStart = (6 * dp).toInt() })
            }
        }

        // kdy
        val card = v.findViewById<View>(R.id.cardQwWhen)
        card.animate().alpha(if (kind == null) 0.45f else 1f).setDuration(220).start()
        v.findViewById<TextView>(R.id.tvQwWhenLabel).text = when (whenMode) {
            When.NOW -> "TEĎ"; When.IN_15 -> "ZA 15 MIN"; When.CUSTOM -> "VLASTNÍ ČAS"
        }
        v.findViewById<TextView>(R.id.tvQwTime).text = hhmm(time())
        val whenBox = v.findViewById<LinearLayout>(R.id.llQwWhen)
        whenBox.removeAllViews()
        listOf(When.NOW to "Teď", When.IN_15 to "Za 15 min", When.CUSTOM to "Upravit čas").forEachIndexed { i, (w, t) ->
            whenBox.addView(pill(t, w == whenMode, dark = true) { pickWhen(w) },
                LinearLayout.LayoutParams(0, (40 * dp).toInt(), 1f).apply { if (i > 0) marginStart = (6 * dp).toInt() })
        }

        val go = v.findViewById<MaterialButton>(R.id.btnQwGo)
        go.isEnabled = kind != null
        go.alpha = if (kind == null) 0.5f else 1f
        go.text = when {
            kind == null -> "Vyber partie nebo trénink"
            whenMode == When.NOW -> "Jdu na to!"
            else -> "Naplánovat na ${hhmm(time())}"
        }
        v.findViewById<TextView>(R.id.tvQwReward).text =
            if (kind == QuickWorkout.CARDIO) "Po dokončení +10 ⚡ v Makrosvětu (kardio aspoň ${QuickWorkout.CARDIO_MIN} min)"
            else "Po dokončení +15 ⚡ v Makrosvětu"
    }

    private fun renderRun() {
        val v = view ?: return
        val p = pending() ?: return
        val running = p.running()
        v.findViewById<TextView>(R.id.tvQwPill).text = if (running) "TRÉNINK BĚŽÍ" else "NAPLÁNOVÁNO"
        v.findViewById<TextView>(R.id.tvQwTitle).text = p.label
        v.findViewById<TextView>(R.id.tvQwSub).text = if (running) "Začátek v ${p.time}" else "Jednorázově, týdenní plán zůstává."
        v.findViewById<TextView>(R.id.tvQwRunState).text = if (running) "STOPKY" else "ZAČÁTEK"
        val timer = v.findViewById<TextView>(R.id.tvQwTimer)
        val hint = v.findViewById<TextView>(R.id.tvQwRunHint)
        if (running) {
            val s = (System.currentTimeMillis() - p.startedAt) / 1000
            timer.text = if (s >= 3600) String.format(Locale.US, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
                else String.format(Locale.US, "%02d:%02d", s / 60, s % 60)
            hint.text = "Po sérii si klidně odskoč do Makrosvěta."
        } else {
            timer.text = p.time
            val mins = java.time.Duration.between(LocalTime.now(), LocalTime.parse(p.time)).toMinutes()
            hint.text = when { mins > 0 -> "za $mins min"; mins == 0L -> "právě teď"; else -> "před ${-mins} min" }
        }
        v.findViewById<View>(R.id.btnQwSets).visibility = if (running && p.strength != null) View.VISIBLE else View.GONE
        v.findViewById<MaterialButton>(R.id.btnQwDone).text = if (running) "Hotovo ✓" else "Začít teď"
        val shortCardio = running && p.kind == QuickWorkout.CARDIO && p.minutes() < QuickWorkout.CARDIO_MIN
        v.findViewById<TextView>(R.id.tvQwRunNote).text = when {
            !running -> "Připomenu se v čase tréninku. Pak stačí klepnout na činku."
            shortCardio -> "Kardio se do energie počítá od ${QuickWorkout.CARDIO_MIN} min (zbývá ${QuickWorkout.CARDIO_MIN - p.minutes()} min)."
            p.kind == QuickWorkout.CARDIO -> "Za dokončené kardio +10 ⚡ v Makrosvětu."
            else -> "Za dokončený trénink +15 ⚡ v Makrosvětu."
        }
        v.findViewById<MaterialButton>(R.id.btnQwCancel).text = if (cancelArmed) "Opravdu zrušit?" else "Zrušit trénink"
    }

    /** Zaoblená volba ve stylu aplikace; [dark] = na tmavé kartě. */
    private fun pill(text: String, on: Boolean, dark: Boolean, onClick: () -> Unit) = TextView(requireContext()).apply {
        this.text = text
        gravity = Gravity.CENTER
        textSize = 12.5f
        letterSpacing = 0.06f
        typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
        val fill = when {
            on && dark -> c(R.color.brand_accent_warm)
            on -> c(R.color.brand_dark)
            dark -> c(R.color.brand_cream_alpha10)
            else -> c(R.color.brand_dark_alpha05)
        }
        setTextColor(when { on && dark -> c(R.color.brand_dark); on || dark -> c(R.color.brand_cream); else -> c(R.color.brand_dark) })
        background = GradientDrawable().apply {
            cornerRadius = 100 * dp
            setColor(fill)
            if (!on && !dark) setStroke((1 * dp).toInt(), c(R.color.brand_dark_alpha12))
        }
        isClickable = true
        setOnClickListener {
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            animate().scaleX(0.92f).scaleY(0.92f).setDuration(70).withEndAction {
                animate().scaleX(1f).scaleY(1f).setDuration(140).start()
            }.start()
            onClick()
        }
    }

    // ── Akce ────────────────────────────────────────────────────────────

    private fun pickWhen(w: When) {
        if (w != When.CUSTOM) { whenMode = w; render(); return }
        val t = time()
        MakroflowTimePicker.show(parentFragmentManager, t.hour, t.minute, "Čas tréninku") { h, m ->
            custom = LocalTime.of(h, m); whenMode = When.CUSTOM; render()
        }
    }

    private fun changed() {
        (activity as? MainActivity)?.refreshStickyNotification()
        parentFragmentManager.setFragmentResult(RESULT, Bundle())
    }

    private fun go() {
        val k = kind ?: return
        val ctx = requireContext()
        if (whenMode == When.NOW) {
            QuickWorkout.start(ctx, k, variant)
            changed()
            strengthOf(k)?.let { WorkoutSessionSheet.show(parentFragmentManager, it, variant ?: 'A') }
            dismiss()
        } else {
            QuickWorkout.plan(ctx, k, variant, hhmm(time()))
            changed()
            render()
        }
    }

    private fun done() {
        val p = pending() ?: return
        val ctx = requireContext().applicationContext
        if (!p.running()) {            // naplánovaný → začít teď
            QuickWorkout.start(ctx, p.kind, p.variant)
            changed()
            p.strength?.let { WorkoutSessionSheet.show(parentFragmentManager, it, p.variant ?: 'A'); dismiss() } ?: render()
            return
        }
        val rewarded = p.kind != QuickWorkout.CARDIO || p.minutes() >= QuickWorkout.CARDIO_MIN
        viewLifecycleOwner.lifecycleScope.launch {
            QuickWorkout.finish(ctx, done = true)
            changed()
            val v = view ?: return@launch
            v.findViewById<View>(R.id.btnQwDone).isEnabled = false
            v.findViewById<TextView>(R.id.tvQwRunState).text = "HOTOVO"
            v.findViewById<TextView>(R.id.tvQwTimer).apply {
                text = if (!rewarded) "✓" else if (p.kind == QuickWorkout.CARDIO) "+10 ⚡" else "+15 ⚡"
                setTextColor(c(R.color.brand_accent_warm))
                scaleX = 0.6f; scaleY = 0.6f
                animate().scaleX(1f).scaleY(1f).setDuration(260).start()
            }
            v.findViewById<TextView>(R.id.tvQwRunHint).text = if (rewarded) "Energie čeká v Makrosvětu." else "Dobrá práce."
            v.removeCallbacks(ticker)
            v.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            v.postDelayed({ if (isAdded) dismiss() }, 1400)
        }
    }

    private fun cancel() {
        if (!cancelArmed) {
            cancelArmed = true; renderRun()
            view?.postDelayed({ cancelArmed = false; renderRun() }, 3000)
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            QuickWorkout.finish(requireContext().applicationContext, done = false)
            cancelArmed = false
            changed()
            render()
        }
    }
}
