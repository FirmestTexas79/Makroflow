package cz.uhk.macroflow.training.log

import android.graphics.Color
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
        view.findViewById<TextView>(R.id.tvEditorTitle).text = "Šablona ${WorkoutTemplates.label(key)}"
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

    private fun renderRows() {
        rows.removeAllViews()
        ids.forEachIndexed { i, id ->
            val e = ExerciseLibrary.byId(id) ?: return@forEachIndexed
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding((12 * dp).toInt(), (8 * dp).toInt(), (4 * dp).toInt(), (8 * dp).toInt())
                background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_search_soft)
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = (6 * dp).toInt() }
            }
            row.addView(TextView(requireContext()).apply {
                text = "${i + 1}."
                setTextColor(ContextCompat.getColor(requireContext(), R.color.brand_primary))
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams((26 * dp).toInt(), -2)
            })
            row.addView(LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                addView(TextView(requireContext()).apply {
                    text = e.name; textSize = 14.5f
                    setTextColor(ContextCompat.getColor(requireContext(), R.color.brand_dark))
                })
                addView(TextView(requireContext()).apply {
                    text = "${e.equipment.label} · ${e.primary.joinToString(", ") { it.label }}"; textSize = 11.5f; alpha = 0.6f
                    setTextColor(ContextCompat.getColor(requireContext(), R.color.brand_dark))
                })
            })
            fun btn(icon: Int, desc: String, enabled: Boolean, rotate: Float = 0f, onClick: () -> Unit) = ImageButton(requireContext()).apply {
                setImageResource(icon); rotation = rotate
                contentDescription = desc
                imageTintList = ContextCompat.getColorStateList(requireContext(), R.color.brand_dark)
                background = null
                alpha = if (enabled) 1f else 0.25f
                isEnabled = enabled
                layoutParams = LinearLayout.LayoutParams((40 * dp).toInt(), (40 * dp).toInt())
                setOnClickListener { onClick() }
            }
            row.addView(btn(R.drawable.ic_chevron_right, "Posunout nahoru", i > 0, -90f) { ids.add(i - 1, ids.removeAt(i)); renderRows() })
            row.addView(btn(R.drawable.ic_chevron_right, "Posunout dolů", i < ids.lastIndex, 90f) { ids.add(i + 1, ids.removeAt(i)); renderRows() })
            row.addView(btn(R.drawable.ic_close, "Odebrat", true) { ids.removeAt(i); renderRows() })
            rows.addView(row)
        }
    }

    /** Výběr cviku z knihovny s hledáním bez diakritiky. */
    private fun pickExercise() {
        val ctx = requireContext()
        val box = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding((20 * dp).toInt(), (8 * dp).toInt(), (20 * dp).toInt(), 0) }
        val search = EditText(ctx).apply { hint = "Hledat cvik…"; setSingleLine() }
        val list = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        box.addView(search)
        box.addView(ScrollView(ctx).apply { addView(list); layoutParams = LinearLayout.LayoutParams(-1, (420 * dp).toInt()) })
        val dialog = MaterialAlertDialogBuilder(ctx).setTitle("Přidat cvik").setView(box).setNegativeButton("Zavřít", null).create()
        fun fill(q: String) {
            list.removeAllViews()
            val query = SnackCatalog.normalize(q)
            ExerciseLibrary.ALL.filter { it.id !in ids && (query.isBlank() ||
                SnackCatalog.normalize(it.name).contains(query) || SnackCatalog.normalize(it.alias).contains(query)) }
                .forEach { e ->
                    list.addView(TextView(ctx).apply {
                        text = "${e.name}\n${e.alias} · ${e.equipment.label}"
                        textSize = 14f
                        setTextColor(ContextCompat.getColor(ctx, R.color.brand_dark))
                        setPadding(0, (10 * dp).toInt(), 0, (10 * dp).toInt())
                        setOnClickListener { ids += e.id; renderRows(); dialog.dismiss() }
                    })
                }
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) = fill(s?.toString().orEmpty())
        })
        fill("")
        dialog.show()
    }
}
