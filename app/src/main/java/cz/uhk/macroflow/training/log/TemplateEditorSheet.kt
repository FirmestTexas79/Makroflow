package cz.uhk.macroflow.training.log

import android.graphics.Color
import android.graphics.Typeface
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.widget.HorizontalScrollView
import android.widget.ImageView
import cz.uhk.macroflow.training.body.Muscle
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import cz.uhk.macroflow.R
import cz.uhk.macroflow.nutrition.SnackCatalog
import cz.uhk.macroflow.training.exercises.ExerciseLibrary
import kotlinx.coroutines.launch

/** Úprava šablony tréninkového dne (docs/adr/0024): pořadí, přidání, odebrání, výchozí stav. */
class TemplateEditorSheet : BottomSheetDialogFragment() {

    companion object {
        private const val ARG_KEY = "key"
        private const val TAG = "TemplateEditorSheet"

        fun show(fm: FragmentManager, key: String) {
            if (fm.findFragmentByTag(TAG) != null) return
            TemplateEditorSheet().apply { arguments = bundleOf(ARG_KEY to key) }.show(fm, TAG)
        }
    }

    private lateinit var key: String
    private val ids = mutableListOf<String>()
    private lateinit var rows: LinearLayout
    private val dp get() = resources.displayMetrics.density

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.sheet_template_editor, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        key = requireArguments().getString(ARG_KEY)!!
        rows = view.findViewById(R.id.llEditorRows)
        view.findViewById<TextView>(R.id.tvEditorTitle).text = WorkoutTemplates.label(key)
        viewLifecycleOwner.lifecycleScope.launch {
            ids.clear(); ids += WorkoutRepository.templateIds(requireContext().applicationContext, key)
            renderRows()
        }
        view.findViewById<View>(R.id.btnEditorAdd).setOnClickListener { pickExercise() }
        view.findViewById<View>(R.id.btnEditorReset).setOnClickListener {
            ids.clear(); ids += WorkoutTemplates.DEFAULTS[key].orEmpty(); renderRows()
        }
        view.findViewById<View>(R.id.btnEditorSave).setOnClickListener {
            val app = requireContext().applicationContext
            val snapshot = ids.toList()
            viewLifecycleOwner.lifecycleScope.launch { WorkoutRepository.saveTemplate(app, key, snapshot); dismiss() }
        }
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

    private fun c(res: Int) = ContextCompat.getColor(requireContext(), res)

    private fun bg(fill: Int, radiusDp: Float, stroke: Int = 0, oval: Boolean = false) = GradientDrawable().apply {
        if (oval) setShape(GradientDrawable.OVAL) else cornerRadius = radiusDp * dp
        setColor(fill)
        if (stroke != 0) setStroke((1 * dp).toInt(), stroke)
    }

    private fun countLabel(n: Int) = "$n " + when (n) { 1 -> "cvik"; in 2..4 -> "cviky"; else -> "cviků" }

    private fun renderRows() {
        val ctx = requireContext()
        view?.findViewById<TextView>(R.id.tvEditorCount)?.text = countLabel(ids.size)
        rows.removeAllViews()
        ids.forEachIndexed { i, id ->
            val e = ExerciseLibrary.byId(id) ?: return@forEachIndexed
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding((12 * dp).toInt(), (10 * dp).toInt(), (8 * dp).toInt(), (10 * dp).toInt())
                background = bg(c(R.color.profile_card), 18f, c(R.color.brand_dark_alpha12))
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = (8 * dp).toInt() }
            }
            row.addView(TextView(ctx).apply {
                text = "${i + 1}"; gravity = Gravity.CENTER; textSize = 13f
                typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
                setTextColor(c(R.color.brand_cream))
                background = bg(c(R.color.brand_dark), 0f, oval = true)
                layoutParams = LinearLayout.LayoutParams((30 * dp).toInt(), (30 * dp).toInt()).apply { marginEnd = (12 * dp).toInt() }
            })
            row.addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                addView(TextView(ctx).apply {
                    text = e.name; textSize = 15f
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    setTextColor(c(R.color.brand_dark))
                })
                addView(TextView(ctx).apply {
                    text = "${e.equipment.label} · ${e.primary.joinToString(", ") { it.label }}"; textSize = 12f
                    setTextColor(c(R.color.brand_primary))
                })
            })
            fun btn(icon: Int, desc: String, enabled: Boolean, rotate: Float, fill: Int, tint: Int, onClick: () -> Unit) = ImageButton(ctx).apply {
                setImageResource(icon); rotation = rotate
                contentDescription = desc
                imageTintList = ColorStateList.valueOf(tint)
                background = bg(fill, 0f, oval = true)
                setPadding((8 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt())
                scaleType = ImageView.ScaleType.FIT_CENTER
                alpha = if (enabled) 1f else 0.25f
                isEnabled = enabled
                layoutParams = LinearLayout.LayoutParams((36 * dp).toInt(), (36 * dp).toInt()).apply { marginStart = (6 * dp).toInt() }
                setOnClickListener { onClick() }
            }
            val soft = c(R.color.brand_primary_alpha10); val dark = c(R.color.brand_dark)
            row.addView(btn(R.drawable.ic_chevron_right, "Posunout nahoru", i > 0, -90f, soft, dark) { ids.add(i - 1, ids.removeAt(i)); renderRows() })
            row.addView(btn(R.drawable.ic_chevron_right, "Posunout dolů", i < ids.lastIndex, 90f, soft, dark) { ids.add(i + 1, ids.removeAt(i)); renderRows() })
            row.addView(btn(R.drawable.ic_close, "Odebrat", true, 0f, c(R.color.brand_accent_deep_alpha20), c(R.color.brand_accent_deep)) { ids.removeAt(i); renderRows() })
            rows.addView(row)
        }
    }

    /** Výběr cviku z knihovny: hledání bez diakritiky + filtr podle svalové partie. */
    private fun pickExercise() {
        val ctx = requireContext()
        var muscle: Muscle? = null
        val box = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding((20 * dp).toInt(), (22 * dp).toInt(), (20 * dp).toInt(), (4 * dp).toInt()) }
        box.addView(TextView(ctx).apply {
            text = "Přidat cvik"; textSize = 24f
            typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
            setTextColor(c(R.color.brand_dark))
        })
        val count = TextView(ctx).apply { textSize = 12f; setTextColor(c(R.color.brand_primary)) }
        box.addView(count)
        val search = EditText(ctx).apply {
            hint = "Hledat cvik…"; setSingleLine(); textSize = 15f; background = null
            setTextColor(c(R.color.brand_dark)); setHintTextColor(c(R.color.brand_primary))
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        box.addView(LinearLayout(ctx).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding((14 * dp).toInt(), (2 * dp).toInt(), (10 * dp).toInt(), (2 * dp).toInt())
            background = bg(c(R.color.profile_card), 20f, c(R.color.brand_primary_alpha30))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = (12 * dp).toInt() }
            addView(ImageView(ctx).apply {
                setImageResource(R.drawable.ic_search); imageTintList = ColorStateList.valueOf(c(R.color.brand_primary))
                layoutParams = LinearLayout.LayoutParams((20 * dp).toInt(), (20 * dp).toInt()).apply { marginEnd = (10 * dp).toInt() }
            })
            addView(search)
        })
        val chipRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        box.addView(HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = (10 * dp).toInt() }
            addView(chipRow)
        })
        val list = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        box.addView(ScrollView(ctx).apply {
            addView(list)
            layoutParams = LinearLayout.LayoutParams(-1, (380 * dp).toInt()).apply { topMargin = (6 * dp).toInt() }
        })
        val dialog = MaterialAlertDialogBuilder(ctx).setView(box).setNegativeButton("Zavřít", null).create()
        dialog.window?.setBackgroundDrawable(InsetDrawable(bg(c(R.color.brand_cream), 28f), (8 * dp).toInt()))

        fun fill() {
            list.removeAllViews()
            val query = SnackCatalog.normalize(search.text?.toString().orEmpty())
            val found = ExerciseLibrary.ALL.filter { it.id !in ids && (muscle == null || muscle in it.primary) && (query.isBlank() ||
                SnackCatalog.normalize(it.name).contains(query) || SnackCatalog.normalize(it.alias).contains(query)) }
            count.text = countLabel(found.size) + " v nabídce"
            found.forEach { e ->
                list.addView(LinearLayout(ctx).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding((14 * dp).toInt(), (10 * dp).toInt(), (10 * dp).toInt(), (10 * dp).toInt())
                    background = bg(c(R.color.profile_card), 16f, c(R.color.brand_dark_alpha12))
                    layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = (8 * dp).toInt() }
                    addView(LinearLayout(ctx).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                        addView(TextView(ctx).apply {
                            text = e.name; textSize = 15f
                            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                            setTextColor(c(R.color.brand_dark))
                        })
                        addView(TextView(ctx).apply {
                            text = "${e.alias} · ${e.equipment.label}"; textSize = 12f
                            setTextColor(c(R.color.brand_primary))
                        })
                    })
                    addView(ImageView(ctx).apply {
                        setImageResource(R.drawable.ic_line_plus)
                        imageTintList = ColorStateList.valueOf(c(R.color.brand_dark))
                        background = bg(c(R.color.brand_primary_alpha20), 0f, oval = true)
                        setPadding((7 * dp).toInt(), (7 * dp).toInt(), (7 * dp).toInt(), (7 * dp).toInt())
                        layoutParams = LinearLayout.LayoutParams((34 * dp).toInt(), (34 * dp).toInt()).apply { marginStart = (8 * dp).toInt() }
                    })
                    setOnClickListener { ids += e.id; renderRows(); dialog.dismiss() }
                })
            }
        }
        fun chips() {
            chipRow.removeAllViews()
            val used = ExerciseLibrary.ALL.flatMap { it.primary }.toSet()
            (listOf<Muscle?>(null) + Muscle.entries.filter { it in used }).forEach { m ->
                val on = m == muscle
                chipRow.addView(TextView(ctx).apply {
                    text = m?.label ?: "Vše"; textSize = 13f
                    setTextColor(if (on) c(R.color.brand_cream) else c(R.color.brand_dark))
                    background = bg(if (on) c(R.color.brand_dark) else c(R.color.brand_primary_alpha10), 16f)
                    setPadding((14 * dp).toInt(), (7 * dp).toInt(), (14 * dp).toInt(), (7 * dp).toInt())
                    layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginEnd = (6 * dp).toInt() }
                    setOnClickListener { muscle = m; chips(); fill() }
                })
            }
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) = fill()
        })
        chips(); fill()
        dialog.show()
    }
}
