package cz.uhk.macroflow.training.atlas

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.os.bundleOf
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButtonToggleGroup
import cz.uhk.macroflow.R
import cz.uhk.macroflow.training.body.BodyMapView
import cz.uhk.macroflow.training.body.BodySide
import cz.uhk.macroflow.training.body.Muscle
import cz.uhk.macroflow.training.exercises.ExerciseLibrary
import cz.uhk.macroflow.training.exercises.MuscleAnatomy

/**
 * Atlas svalů (docs/adr/0019). Jedna velká postava s týdenním zapojením partií; klepnutím
 * na sval (nebo na čip) se partie rozsvítí a vysune se její popis, latinské názvy a cviky.
 * Klepnutím na cvik se otevře [ExerciseDetailSheet].
 */
class MuscleAtlasSheet : BottomSheetDialogFragment() {

    companion object {
        private const val ARG_FREQ = "freq"
        private const val ARG_COLOR = "color"
        private const val ARG_MUSCLE = "muscle"
        private const val STATE_SIDE = "side"
        private const val STATE_MUSCLE = "selected"
        const val TAG = "MuscleAtlasSheet"

        fun show(fm: FragmentManager, weeklyFrequency: Map<Muscle, Double>, color: Int, initial: Muscle? = null) {
            if (fm.findFragmentByTag(TAG) != null) return
            MuscleAtlasSheet().apply {
                arguments = bundleOf(
                    ARG_FREQ to DoubleArray(Muscle.entries.size) { weeklyFrequency[Muscle.entries[it]] ?: 0.0 },
                    ARG_COLOR to color,
                    ARG_MUSCLE to (initial?.ordinal ?: -1)
                )
            }.show(fm, TAG)
        }

        /** Na které straně je partie vidět nejlépe (když je na obou, zůstane aktuální). */
        private val BACK_ONLY = setOf(Muscle.REAR_DELTS, Muscle.TRICEPS, Muscle.LATS, Muscle.LOWER_BACK, Muscle.GLUTES, Muscle.HAMSTRINGS)
        private val FRONT_ONLY = setOf(Muscle.FRONT_DELTS, Muscle.CHEST, Muscle.BICEPS, Muscle.ABS, Muscle.OBLIQUES, Muscle.QUADS)
    }

    private lateinit var freq: Map<Muscle, Double>
    private var color = Color.parseColor("#606C38")
    private var side = BodySide.FRONT
    private var selected: Muscle? = null

    private lateinit var body: BodyMapView
    private lateinit var toggle: MaterialButtonToggleGroup
    private lateinit var chips: LinearLayout
    private lateinit var scroll: NestedScrollView
    private lateinit var detail: LinearLayout

    private val dp get() = resources.displayMetrics.density

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.sheet_muscle_atlas, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val args = requireArguments()
        val raw = args.getDoubleArray(ARG_FREQ) ?: DoubleArray(Muscle.entries.size)
        freq = Muscle.entries.associateWith { raw.getOrElse(it.ordinal) { 0.0 } }
        color = args.getInt(ARG_COLOR, color)
        val initial = savedInstanceState?.getInt(STATE_MUSCLE, -1) ?: args.getInt(ARG_MUSCLE, -1)
        side = savedInstanceState?.getString(STATE_SIDE)?.let { BodySide.valueOf(it) }
            ?: Muscle.entries.getOrNull(initial)?.let { preferredSide(it, BodySide.FRONT) } ?: BodySide.FRONT

        body = view.findViewById(R.id.atlasBody)
        toggle = view.findViewById(R.id.toggleSide)
        chips = view.findViewById(R.id.llMuscleChips)
        scroll = view.findViewById(R.id.atlasScroll)
        detail = view.findViewById(R.id.llMuscleDetail)

        view.findViewById<View>(R.id.btnCloseAtlas).setOnClickListener { dismiss() }

        body.apply {
            sides = listOf(side)
            showCaptions = false
            showSilhouetteOutline = true
            setIntensities(AtlasFormat.intensities(freq), color, animate = false)
            onMuscleTap = { select(it, scrollToDetail = true) }
        }

        toggle.check(if (side == BodySide.FRONT) R.id.btnSideFront else R.id.btnSideBack)
        toggle.addOnButtonCheckedListener { _, id, checked ->
            if (!checked) return@addOnButtonCheckedListener
            setSide(if (id == R.id.btnSideFront) BodySide.FRONT else BodySide.BACK)
        }

        buildLegend(view.findViewById(R.id.llAtlasLegend))
        buildChips()
        Muscle.entries.getOrNull(initial)?.let { select(it, scrollToDetail = false, animate = false) }
    }

    override fun onStart() {
        super.onStart()
        // Atlas je vysoký – rovnou celý, bez mezistavu „napůl“.
        (dialog as? BottomSheetDialog)?.let { d ->
            d.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)?.let { sheet ->
                sheet.setBackgroundColor(Color.TRANSPARENT)
                sheet.layoutParams = sheet.layoutParams.apply { height = ViewGroup.LayoutParams.MATCH_PARENT }
            }
            d.behavior.skipCollapsed = true
            d.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_SIDE, side.name)
        outState.putInt(STATE_MUSCLE, selected?.ordinal ?: -1)
    }

    // ── Strana a výběr ──────────────────────────────────────────────────────

    private fun preferredSide(m: Muscle, current: BodySide) = when (m) {
        in BACK_ONLY -> BodySide.BACK
        in FRONT_ONLY -> BodySide.FRONT
        else -> current
    }

    private fun setSide(s: BodySide) {
        if (s == side) return
        side = s
        body.sides = listOf(s)
        val id = if (s == BodySide.FRONT) R.id.btnSideFront else R.id.btnSideBack
        if (toggle.checkedButtonId != id) toggle.check(id)
    }

    private fun select(m: Muscle, scrollToDetail: Boolean, animate: Boolean = true) {
        setSide(preferredSide(m, side))
        selected = m
        body.select(m, animate)
        updateChips()
        showDetail(m, animate)
        if (scrollToDetail) scroll.postDelayed({
            if (isAdded) scroll.smoothScrollTo(0, (detail.top - 8 * dp).toInt().coerceAtLeast(0))
        }, 260)
    }

    private fun showDetail(m: Muscle, animate: Boolean) {
        val v = requireView()
        val perWeek = freq[m] ?: 0.0
        v.findViewById<TextView>(R.id.tvMuscleName).text = AtlasFormat.capitalized(m)
        v.findViewById<TextView>(R.id.tvMuscleFreq).text = AtlasFormat.frequency(perWeek).uppercase()
        v.findViewById<TextView>(R.id.tvMuscleFunction).text =
            "${MuscleAnatomy.FUNCTION[m].orEmpty()}\n${AtlasFormat.frequencyNote(m, perWeek)}"
        v.findViewById<TextView>(R.id.tvMuscleLatin).text =
            MuscleAnatomy.LATIN[m].orEmpty().joinToString("\n") { "• $it" }

        val exercises = ExerciseLibrary.forMuscle(m)
        v.findViewById<TextView>(R.id.tvExercisesSection).text = "CVIKY · ${exercises.size}"
        val list = v.findViewById<LinearLayout>(R.id.llExercises)
        list.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())
        exercises.forEachIndexed { i, e ->
            val item = inflater.inflate(R.layout.item_exercise, list, false)
            val main = m in e.primary
            item.findViewById<TextView>(R.id.tvExName).text = e.name
            item.findViewById<TextView>(R.id.tvExAlias).text = e.alias
            item.findViewById<TextView>(R.id.tvExMeta).text = "${e.equipment.label} · ${e.levelLabel}".uppercase()
            item.findViewById<TextView>(R.id.tvExRole).apply {
                text = if (main) "HLAVNÍ" else "POMÁHÁ"
                if (!main) {
                    backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.brand_primary_alpha10)
                    setTextColor(ContextCompat.getColor(requireContext(), R.color.brand_primary))
                }
            }
            if (!main) item.findViewById<View>(R.id.vRole).backgroundTintList =
                ContextCompat.getColorStateList(requireContext(), R.color.brand_primary_alpha30)
            item.setOnClickListener { ExerciseDetailSheet.show(childFragmentManager, e.id) }
            list.addView(item)
            if (animate) {
                item.alpha = 0f; item.translationY = 16 * dp
                item.animate().alpha(1f).translationY(0f).setStartDelay(60L + i * 30L).setDuration(220).start()
            }
        }

        if (detail.visibility != View.VISIBLE) {
            detail.visibility = View.VISIBLE
            if (animate) {
                detail.alpha = 0f; detail.translationY = 24 * dp
                detail.animate().alpha(1f).translationY(0f).setDuration(240).start()
            }
        }
    }

    // ── Legenda a čipy ──────────────────────────────────────────────────────

    private fun buildLegend(ll: LinearLayout) {
        val idle = body.idleMuscleColor
        listOf(
            "0×" to idle,
            "1×" to ColorUtils.blendARGB(idle, color, 0.35f + 0.65f * 0.5f),
            "2×+ týdně" to color,
            "vybraný" to body.selectedColor
        ).forEach { (label, c) ->
            ll.addView(View(requireContext()).apply {
                background = GradientDrawable().apply { cornerRadius = 3 * dp; setColor(c) }
                layoutParams = LinearLayout.LayoutParams((12 * dp).toInt(), (12 * dp).toInt()).apply { marginStart = (10 * dp).toInt() }
            })
            ll.addView(TextView(requireContext()).apply {
                text = label; textSize = 11f; setTextColor(Color.parseColor("#99283618"))
                setPadding((4 * dp).toInt(), 0, 0, 0)
            })
        }
    }

    private val chipViews = mutableMapOf<Muscle, TextView>()

    private fun buildChips() {
        chips.removeAllViews()
        chipViews.clear()
        Muscle.entries.forEachIndexed { i, m ->
            val chip = TextView(requireContext()).apply {
                text = AtlasFormat.capitalized(m)
                textSize = 12.5f
                setPadding((14 * dp).toInt(), (8 * dp).toInt(), (14 * dp).toInt(), (8 * dp).toInt())
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { if (i > 0) marginStart = (6 * dp).toInt() }
                setOnClickListener { select(m, scrollToDetail = true) }
            }
            chipViews[m] = chip
            chips.addView(chip)
        }
        updateChips()
    }

    private fun updateChips() {
        val ctx = requireContext()
        val dark = ContextCompat.getColor(ctx, R.color.brand_dark)
        val cream = ContextCompat.getColor(ctx, R.color.brand_cream)
        chipViews.forEach { (m, chip) ->
            val on = m == selected
            chip.background = GradientDrawable().apply {
                cornerRadius = 100 * dp
                setColor(if (on) dark else ContextCompat.getColor(ctx, R.color.profile_card))
                setStroke((1 * dp).toInt(), if (on) dark else ContextCompat.getColor(ctx, R.color.brand_dark_alpha12))
            }
            chip.setTextColor(if (on) cream else dark)
            chip.setTypeface(null, if (on) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        }
        // Vybraný čip posunout do záběru.
        selected?.let { m -> chipViews[m]?.let { c -> (chips.parent as? View)?.post {
            (chips.parent as? android.widget.HorizontalScrollView)?.smoothScrollTo((c.left - 24 * dp).toInt().coerceAtLeast(0), 0)
        } } }
    }
}
