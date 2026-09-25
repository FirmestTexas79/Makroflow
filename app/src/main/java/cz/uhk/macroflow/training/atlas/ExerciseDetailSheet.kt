package cz.uhk.macroflow.training.atlas

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import cz.uhk.macroflow.R
import cz.uhk.macroflow.training.body.BodyMapView
import cz.uhk.macroflow.training.exercises.Exercise
import cz.uhk.macroflow.training.exercises.ExerciseLibrary

/**
 * Detail cviku (docs/adr/0019): zapojené partie na postavě + latinsky, provedení krok za krokem,
 * tipy a časté chyby. Data z [ExerciseLibrary]. Nahoře tréninkový deník (docs/adr/0023).
 */
class ExerciseDetailSheet : BottomSheetDialogFragment() {

    companion object {
        private const val ARG_ID = "exercise"
        private const val TAG = "ExerciseDetailSheet"

        fun show(fm: FragmentManager, exerciseId: String) {
            if (fm.findFragmentByTag(TAG) != null) return
            ExerciseDetailSheet().apply { arguments = bundleOf(ARG_ID to exerciseId) }.show(fm, TAG)
        }
    }

    private val dp get() = resources.displayMetrics.density

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.sheet_exercise_detail, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val e = ExerciseLibrary.byId(requireArguments().getString(ARG_ID).orEmpty())
        if (e == null) { dismiss(); return }
        view.findViewById<View>(R.id.btnCloseExercise).setOnClickListener { dismiss() }

        view.findViewById<TextView>(R.id.tvExBadge).text = e.equipment.label.uppercase()
        view.findViewById<TextView>(R.id.tvExTitle).text = e.name
        view.findViewById<TextView>(R.id.tvExSubtitle).text = "${e.alias} · ${e.levelLabel}"

        bindMuscles(view, e)
        cz.uhk.macroflow.training.log.WorkoutLogSection(this, view, e).start()
        fillNumbered(view.findViewById(R.id.llExSteps), e.steps)
        fillBullets(view.findViewById(R.id.llExTips), e.tips, R.drawable.ic_check_circle, R.color.brand_primary)
        fillBullets(view.findViewById(R.id.llExMistakes), e.mistakes, R.drawable.ic_close, R.color.exercise_mistake)
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

    private fun bindMuscles(view: View, e: Exercise) {
        val ctx = requireContext()
        val cream = ContextCompat.getColor(ctx, R.color.brand_cream)
        val warm = ContextCompat.getColor(ctx, R.color.brand_accent_warm)
        val body = view.findViewById<BodyMapView>(R.id.exBody).apply {
            // Tmavá karta: tlumená postava, zapojené partie teplou barvou.
            baseColor = Color.parseColor("#3A4A26")
            idleMuscleColor = Color.parseColor("#4E5E37")
            captionColor = ColorUtils.setAlphaComponent(cream, 0x99)
            setIntensities(AtlasFormat.exerciseIntensities(e), warm, animate = false)
        }
        val legend = view.findViewById<LinearLayout>(R.id.llExLegend)
        listOf(
            "hlavní: " + e.primary.joinToString(", ") { it.label } to warm,
            "pomáhá" to ColorUtils.blendARGB(body.idleMuscleColor, warm, 0.35f + 0.65f * 0.5f)
        ).filter { it.first != "pomáhá" || e.secondary.isNotEmpty() }.forEach { (label, c) ->
            legend.addView(View(ctx).apply {
                background = GradientDrawable().apply { cornerRadius = 3 * dp; setColor(c) }
                layoutParams = LinearLayout.LayoutParams((12 * dp).toInt(), (12 * dp).toInt()).apply { marginStart = (10 * dp).toInt() }
            })
            legend.addView(TextView(ctx).apply {
                text = label; textSize = 11f; setTextColor(ColorUtils.setAlphaComponent(cream, 0xCC))
                setPadding((4 * dp).toInt(), 0, 0, 0)
            })
        }

        view.findViewById<TextView>(R.id.tvExPrimary).text = e.latinPrimary.joinToString("\n") { "• $it" }
        val secondary = view.findViewById<TextView>(R.id.tvExSecondary)
        if (e.latinSecondary.isEmpty()) {
            secondary.visibility = View.GONE
            view.findViewById<View>(R.id.tvExSecondaryLabel).visibility = View.GONE
        } else secondary.text = e.latinSecondary.joinToString("\n") { "• $it" }
    }

    private fun fillNumbered(ll: LinearLayout, items: List<String>) {
        val ctx = requireContext()
        items.forEachIndexed { i, text ->
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, (10 * dp).toInt(), 0, 0)
            }
            row.addView(TextView(ctx).apply {
                this.text = (i + 1).toString()
                gravity = Gravity.CENTER
                textSize = 12f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(ContextCompat.getColor(ctx, R.color.brand_cream))
                setBackgroundResource(R.drawable.bg_step_number)
                layoutParams = LinearLayout.LayoutParams((24 * dp).toInt(), (24 * dp).toInt())
            })
            row.addView(bodyText(text))
            ll.addView(row)
        }
    }

    private fun fillBullets(ll: LinearLayout, items: List<String>, icon: Int, tint: Int) {
        val ctx = requireContext()
        items.forEach { text ->
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, (10 * dp).toInt(), 0, 0)
            }
            row.addView(ImageView(ctx).apply {
                setImageResource(icon)
                imageTintList = ContextCompat.getColorStateList(ctx, tint)
                layoutParams = LinearLayout.LayoutParams((20 * dp).toInt(), (20 * dp).toInt()).apply { topMargin = (1 * dp).toInt() }
            })
            row.addView(bodyText(text))
            ll.addView(row)
        }
    }

    private fun bodyText(s: String) = TextView(requireContext()).apply {
        text = s
        textSize = 14f
        setLineSpacing(2 * dp, 1f)
        setTextColor(ContextCompat.getColor(requireContext(), R.color.brand_dark))
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = (12 * dp).toInt() }
    }
}
