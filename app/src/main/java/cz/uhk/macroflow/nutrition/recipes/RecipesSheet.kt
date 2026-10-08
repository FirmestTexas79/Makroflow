package cz.uhk.macroflow.nutrition.recipes

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
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.shape.ShapeAppearanceModel
import cz.uhk.macroflow.R
import cz.uhk.macroflow.nutrition.MealBuilderSheet
import cz.uhk.macroflow.nutrition.recipes.FitnessRecipes.Category
import cz.uhk.macroflow.nutrition.recipes.FitnessRecipes.Recipe
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Fitness recepty (docs/adr/0068): seznam s fotkami a filtrem podle chodu, detail s alergeny,
 * surovinami pro zvolený počet porcí a postupem. „Přidat do tabulky“ otevře Složit jídlo
 * s předvyplněnými surovinami, které jde před zápisem upravit.
 */
class RecipesSheet : BottomSheetDialogFragment() {

    companion object {
        private const val TAG = "RecipesSheet"
        fun show(fm: FragmentManager, isPre: Boolean = false) {
            if (fm.findFragmentByTag(TAG) == null) RecipesSheet().apply { this.isPre = isPre }.show(fm, TAG)
        }
    }

    private var isPre = false
    private var filter: Category? = null
    private lateinit var root: FrameLayout

    private fun c(id: Int) = ContextCompat.getColor(requireContext(), id)
    private fun dp(v: Number) = (v.toFloat() * resources.displayMetrics.density).toInt()
    private val black get() = Typeface.create("sans-serif-black", Typeface.NORMAL)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        root = FrameLayout(requireContext()).apply { setBackgroundResource(R.drawable.bg_sheet_cream) }
        return root
    }

    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)?.let { d ->
            d.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)?.let { sheet ->
                sheet.setBackgroundColor(Color.TRANSPARENT)
                sheet.layoutParams = sheet.layoutParams.apply { height = (resources.displayMetrics.heightPixels * 0.94f).toInt() }
            }
            d.behavior.skipCollapsed = true
            d.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = showList()

    // ── Seznam ─────────────────────────────────────────────────────────────

    private fun showList() {
        val ctx = requireContext()
        root.removeAllViews()
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), 0, dp(20), dp(28)) }
        col.addView(handle())
        col.addView(pill("FITNESS RECEPTY", c(R.color.brand_accent_deep)), wrap().apply { topMargin = dp(14) })
        col.addView(TextView(ctx).apply {
            text = "Co dneska uvaříš?"; typeface = black; textSize = 28f; setTextColor(c(R.color.brand_dark))
        }, wrap().apply { topMargin = dp(6) })
        col.addView(TextView(ctx).apply {
            text = "${FitnessRecipes.ALL.size} jídel, která teď frčí na fitness TikToku a Instagramu. Jedním klepnutím je přepíšeš do deníku."
            textSize = 13f; alpha = 0.7f; setTextColor(c(R.color.brand_dark))
        })

        // filtr podle chodu
        val chips = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        (listOf<Category?>(null) + Category.entries).forEachIndexed { i, cat ->
            val on = cat == filter
            chips.addView(TextView(ctx).apply {
                text = cat?.label ?: "Vše"; textSize = 13f; typeface = black; gravity = Gravity.CENTER
                setPadding(dp(16), 0, dp(16), 0)
                setTextColor(if (on) c(R.color.brand_cream) else c(R.color.brand_dark))
                background = roundRect(100, if (on) c(R.color.brand_dark) else c(R.color.brand_dark_alpha05), if (on) null else c(R.color.brand_dark_alpha12))
                setOnClickListener { filter = cat; it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); showList() }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)).apply { if (i > 0) marginStart = dp(6) })
        }
        col.addView(HorizontalScrollView(ctx).apply { isHorizontalScrollBarEnabled = false; addView(chips) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(14) })

        FitnessRecipes.ALL.filter { filter == null || it.category == filter }.forEach { r ->
            col.addView(card(r), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(196)).apply { topMargin = dp(12) })
        }
        root.addView(NestedScrollView(ctx).apply { addView(col) })
    }

    /** Karta receptu: fotka přes celou plochu, tmavý přechod a údaje dole. */
    private fun card(r: Recipe): View {
        val ctx = requireContext()
        val m = r.macros(1f)
        return FrameLayout(ctx).apply {
            addView(photo(r, 22f), FrameLayout.LayoutParams(-1, -1))
            addView(View(ctx).apply {
                background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.TRANSPARENT, Color.parseColor("#E61B240F"))).apply { cornerRadius = dp(22).toFloat() }
            }, FrameLayout.LayoutParams(-1, -1))
            val info = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(14)) }
            info.addView(TextView(ctx).apply {
                text = r.category.label.uppercase(Locale.ROOT); textSize = 10f; letterSpacing = 0.14f
                setTypeface(typeface, Typeface.BOLD); setTextColor(c(R.color.brand_accent_warm))
            })
            info.addView(TextView(ctx).apply { text = r.name; typeface = black; textSize = 19f; maxLines = 2; setTextColor(c(R.color.brand_cream)) })
            info.addView(TextView(ctx).apply {
                text = "${m.kcal} kcal · ${m.p.roundToInt()} g bílkovin · ${r.minutes} min" + if (r.servings > 1) " · na porci" else ""
                textSize = 12f; setTextColor(Color.parseColor("#D9FEFAE0"))
            })
            addView(info, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
            if (r.alias.isNotEmpty()) addView(pill(r.alias, Color.parseColor("#B3283618")), FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply { setMargins(0, dp(12), dp(12), 0) })
            isClickable = true
            setOnClickListener { it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); showDetail(r, 1) }
            contentDescription = "${r.name}, ${m.kcal} kilokalorií"
        }
    }

    // ── Detail ─────────────────────────────────────────────────────────────

    private fun showDetail(r: Recipe, portions: Int) {
        val ctx = requireContext()
        root.removeAllViews()
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

        // fotka s tlačítkem zpět
        val head = FrameLayout(ctx)
        head.addView(photo(r, 0f), FrameLayout.LayoutParams(-1, -1))
        head.addView(ImageButton(ctx).apply {
            setImageResource(R.drawable.ic_close); setColorFilter(c(R.color.brand_dark)); contentDescription = "Zpět na recepty"
            background = roundRect(100, c(R.color.brand_cream), null); setPadding(dp(10), dp(10), dp(10), dp(10))
            setOnClickListener { showList() }
        }, FrameLayout.LayoutParams(dp(44), dp(44)).apply { gravity = Gravity.TOP or Gravity.START; setMargins(dp(16), dp(16), 0, 0) })
        head.addView(TextView(ctx).apply {
            text = "Foto: ${r.photoCredit}"; textSize = 9.5f; setTextColor(Color.WHITE); alpha = 0.85f
            setShadowLayer(4f, 0f, 1f, Color.BLACK); setPadding(dp(10), dp(4), dp(10), dp(6))
        }, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END))
        col.addView(head, LinearLayout.LayoutParams(-1, dp(250)))

        val body = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(16), dp(20), dp(24)) }
        body.addView(pill(r.category.label.uppercase(Locale.ROOT), c(R.color.brand_primary)), wrap())
        body.addView(TextView(ctx).apply { text = r.name; typeface = black; textSize = 26f; setTextColor(c(R.color.brand_dark)) }, wrap().apply { topMargin = dp(6) })
        if (r.alias.isNotEmpty()) body.addView(TextView(ctx).apply { text = r.alias; textSize = 13f; setTextColor(c(R.color.brand_primary)) })
        body.addView(TextView(ctx).apply {
            text = "🔥 ${r.trend}"; textSize = 13f; setTextColor(c(R.color.brand_dark)); alpha = 0.8f
            background = roundRect(16, c(R.color.brand_accent_warm_alpha15), null); setPadding(dp(14), dp(10), dp(14), dp(10))
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })

        // porce a makra na zvolený počet porcí
        val m = r.macros(portions.toFloat())
        val stats = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(14), dp(14), dp(14), dp(14)); background = roundRect(22, c(R.color.brand_dark), null) }
        val stepper = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        stepper.addView(TextView(ctx).apply { text = "PORCE"; textSize = 9.5f; letterSpacing = 0.12f; setTypeface(typeface, Typeface.BOLD); setTextColor(c(R.color.brand_accent_warm)) })
        val sRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        fun stepBtn(icon: Int, desc: String, to: Int) = ImageButton(ctx).apply {
            setImageResource(icon); contentDescription = desc; setColorFilter(c(R.color.brand_cream))
            background = roundRect(100, Color.parseColor("#26FEFAE0"), null); setPadding(dp(7), dp(7), dp(7), dp(7))
            isEnabled = to in 1..(r.servings * 4); alpha = if (isEnabled) 1f else 0.35f
            setOnClickListener { it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); showDetail(r, to) }
        }
        sRow.addView(stepBtn(R.drawable.ic_line_minus, "Méně porcí", portions - 1), LinearLayout.LayoutParams(dp(30), dp(30)))
        sRow.addView(TextView(ctx).apply { text = "$portions"; typeface = black; textSize = 22f; setTextColor(c(R.color.brand_cream)); gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(dp(34), -2))
        sRow.addView(stepBtn(R.drawable.ic_line_plus, "Víc porcí", portions + 1), LinearLayout.LayoutParams(dp(30), dp(30)))
        stepper.addView(sRow)
        stats.addView(stepper, LinearLayout.LayoutParams(0, -2, 1.3f))
        listOf("KCAL" to "${m.kcal}", "BÍLK." to "${m.p.roundToInt()} g", "S / T" to "${m.s.roundToInt()} / ${m.t.roundToInt()}", "ČAS" to "${r.minutes}′").forEach { (k, v) ->
            stats.addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
                addView(TextView(ctx).apply { text = k; textSize = 9.5f; letterSpacing = 0.12f; setTypeface(typeface, Typeface.BOLD); setTextColor(c(R.color.brand_accent_warm)) })
                addView(TextView(ctx).apply { text = v; typeface = black; textSize = 16f; setTextColor(c(R.color.brand_cream)); setPadding(0, dp(6), 0, 0) })
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        body.addView(stats, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        body.addView(TextView(ctx).apply {
            text = "Recept je na ${r.servings} ${porci(r.servings)}. Suroviny níže jsou pro $portions ${porci(portions)}."
            textSize = 11.5f; alpha = 0.6f; setTextColor(c(R.color.brand_dark)); gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })

        // alergeny
        section(body, "ALERGENY")
        if (r.allergens.isEmpty()) body.addView(TextView(ctx).apply { text = "Bez hlavních alergenů (zkontroluj koření a hotové směsi)."; textSize = 13f; setTextColor(c(R.color.brand_dark)) })
        else {
            val flow = com.google.android.material.chip.ChipGroup(ctx).apply { chipSpacingHorizontal = dp(6); chipSpacingVertical = dp(6) }
            r.allergens.forEach { a ->
                flow.addView(TextView(ctx).apply {
                    text = "⚠ $a"; textSize = 12.5f; setTextColor(c(R.color.brand_accent_deep)); typeface = black
                    background = roundRect(100, c(R.color.brand_accent_deep_alpha20), null); setPadding(dp(12), dp(6), dp(12), dp(6))
                })
            }
            body.addView(flow)
        }

        // suroviny
        section(body, "SUROVINY")
        val ingBox = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; background = roundRect(20, c(R.color.brand_dark_alpha05), c(R.color.brand_dark_alpha10)); setPadding(dp(14), dp(6), dp(14), dp(6)) }
        r.scaled(portions.toFloat()).forEachIndexed { i, ing ->
            ingBox.addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(9), 0, dp(9))
                addView(TextView(ctx).apply { text = ing.name; textSize = 14f; setTextColor(c(R.color.brand_dark)) }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(TextView(ctx).apply { text = "${grams(ing.grams)} g"; typeface = black; textSize = 14f; setTextColor(c(R.color.brand_primary)) })
            })
            if (i < r.ingredients.size - 1) ingBox.addView(View(ctx).apply { setBackgroundColor(c(R.color.brand_dark_alpha10)) }, LinearLayout.LayoutParams(-1, 1))
        }
        body.addView(ingBox)

        // postup
        section(body, "POSTUP")
        r.steps.forEachIndexed { i, s ->
            body.addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL; setPadding(0, if (i == 0) 0 else dp(10), 0, 0)
                addView(TextView(ctx).apply {
                    text = "${i + 1}"; typeface = black; textSize = 13f; gravity = Gravity.CENTER; setTextColor(c(R.color.brand_cream))
                    background = roundRect(100, c(R.color.brand_dark), null)
                }, LinearLayout.LayoutParams(dp(28), dp(28)))
                addView(TextView(ctx).apply { text = s; textSize = 14f; setTextColor(c(R.color.brand_dark)); setLineSpacing(0f, 1.15f); setPadding(dp(12), dp(3), 0, 0) },
                    LinearLayout.LayoutParams(0, -2, 1f))
            })
        }
        col.addView(body)

        // tlačítko dole
        val frame = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        frame.addView(NestedScrollView(ctx).apply { addView(col) }, LinearLayout.LayoutParams(-1, 0, 1f))
        frame.addView(MaterialButton(ctx).apply {
            text = "Přidat do tabulky · $portions ${porci(portions)}"
            typeface = black; textSize = 15f; setTextColor(c(R.color.brand_cream))
            backgroundTintList = android.content.res.ColorStateList.valueOf(c(R.color.brand_dark))
            cornerRadius = dp(18); insetTop = 0; insetBottom = 0
            setIconResource(R.drawable.ic_line_plus); iconTint = android.content.res.ColorStateList.valueOf(c(R.color.brand_cream)); iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            setOnClickListener { addToTable(r, portions) }
        }, LinearLayout.LayoutParams(-1, dp(58)).apply { setMargins(dp(20), dp(8), dp(20), dp(20)) })
        root.addView(frame)
    }

    /** Suroviny receptu do Složit jídlo – uživatel je ještě upraví a teprve pak zapíše. */
    private fun addToTable(r: Recipe, portions: Int) {
        val items = r.scaled(portions.toFloat()).map { i ->
            MealBuilderSheet.Ingredient(i.name, i.grams, i.p100 / 100f, i.s100 / 100f, i.t100 / 100f, i.fiber100 / 100f, i.kcal100 * 4.184f / 100f)
        }
        MealBuilderSheet(isPre).prefill(r.name, items, "RECEPT · $portions ${porci(portions).uppercase(Locale.ROOT)}",
            "Suroviny z receptu – uprav gramy podle toho, co máš doma, a zapiš.").show(parentFragmentManager, "MealBuilder")
        dismiss()
    }

    // ── Pomocné ────────────────────────────────────────────────────────────

    private fun porci(n: Int) = when (n) { 1 -> "porci"; in 2..4 -> "porce"; else -> "porcí" }
    private fun grams(g: Float) = if (g >= 20f) ((g / 5f).roundToInt() * 5).toString() else g.roundToInt().coerceAtLeast(1).toString()

    private fun photo(r: Recipe, radiusDp: Float): ImageView {
        val ctx = requireContext()
        return ShapeableImageView(ctx).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            shapeAppearanceModel = ShapeAppearanceModel.builder().setAllCornerSizes(dp(radiusDp).toFloat()).build()
            val id = resources.getIdentifier(r.photo, "drawable", ctx.packageName)
            if (id != 0) setImageResource(id) else setBackgroundColor(c(R.color.brand_primary_alpha20))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
    }

    private fun handle() = View(requireContext()).apply {
        background = roundRect(100, c(R.color.brand_dark_alpha20), null)
        layoutParams = LinearLayout.LayoutParams(dp(40), dp(4)).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(12) }
    }

    private fun pill(text: String, color: Int) = TextView(requireContext()).apply {
        this.text = text; textSize = 10f; letterSpacing = 0.12f; setTypeface(typeface, Typeface.BOLD)
        setTextColor(c(R.color.brand_cream)); background = roundRect(100, color, null); setPadding(dp(14), dp(5), dp(14), dp(5))
    }

    private fun section(box: LinearLayout, title: String) = box.addView(TextView(requireContext()).apply {
        text = title; textSize = 10f; letterSpacing = 0.14f; setTypeface(typeface, Typeface.BOLD); setTextColor(c(R.color.brand_accent_deep))
    }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(22); bottomMargin = dp(8) })

    private fun roundRect(radiusDp: Int, fill: Int, stroke: Int?) = GradientDrawable().apply {
        cornerRadius = dp(radiusDp).toFloat(); setColor(fill); stroke?.let { setStroke(dp(1), it) }
    }

    private fun wrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
}
