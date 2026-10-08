package cz.uhk.macroflow.nutrition.plan

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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.shape.ShapeAppearanceModel
import cz.uhk.macroflow.R
import cz.uhk.macroflow.dashboard.MacroCalculator
import cz.uhk.macroflow.dashboard.MacroResult
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.data.ConsumedSnackEntity
import cz.uhk.macroflow.nutrition.FoodLog
import cz.uhk.macroflow.nutrition.MealBuilderSheet
import cz.uhk.macroflow.nutrition.MealRepeat
import cz.uhk.macroflow.nutrition.SnackCatalog
import cz.uhk.macroflow.data.SnackEntity
import kotlinx.coroutines.flow.first
import cz.uhk.macroflow.nutrition.recipes.FitnessRecipes
import cz.uhk.macroflow.nutrition.plan.DayPlanner.Nutr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Ideální den (docs/adr/0069): predikce cíle dne, pevné položky (shake …) a návrh jídel
 * na zbytek dne z fitness receptů. Klepnutí na návrh → Složit jídlo s předvyplněnými surovinami.
 */
class DayPlanSheet : BottomSheetDialogFragment() {

    companion object {
        const val REFRESH = "day_plan_refresh"
        fun show(fm: FragmentManager, isPre: Boolean = false) =
            DayPlanSheet().apply { arguments = Bundle().apply { putBoolean("pre", isPre) } }.show(fm, "DayPlan")
    }

    private data class State(
        val target: MacroResult, val eaten: Nutr, val staples: List<Pair<MealRepeat.Item, Boolean>>,
        val slots: List<DayPlanner.Slot>, val plans: List<DayPlanner.Plan>, val pantry: List<SnackEntity>
    )

    /** Z čeho skládat návrh – volba se pamatuje. */
    private enum class Source(val label: String) { ALL("Vše"), RECIPES("Recepty"), PANTRY("Špajzka") }
    private var source: Source
        get() = runCatching { Source.valueOf(prefs().getString("plan_source", null) ?: "") }.getOrDefault(Source.ALL)
        set(v) = prefs().edit().putString("plan_source", v.name).apply()
    private fun prefs() = requireContext().getSharedPreferences("NutritionPrefs", android.content.Context.MODE_PRIVATE)

    private lateinit var root: LinearLayout
    private var state: State? = null
    private var planIndex = 0
    private val isPre get() = arguments?.getBoolean("pre") ?: false

    private fun c(id: Int) = ContextCompat.getColor(requireContext(), id)
    private fun dp(v: Number) = (v.toFloat() * resources.displayMetrics.density).toInt()
    private val black by lazy { Typeface.create("sans-serif-black", Typeface.NORMAL) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_sheet_cream)
            setPadding(dp(20), dp(12), dp(20), dp(28))
        }
        return NestedScrollView(requireContext()).apply { addView(root) }
    }

    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)?.let { d ->
            d.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)?.setBackgroundColor(Color.TRANSPARENT)
            d.behavior.skipCollapsed = true; d.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        parentFragmentManager.setFragmentResultListener(REFRESH, viewLifecycleOwner) { _, _ -> load() }
        load()
    }

    private fun load() {
        val app = requireContext().applicationContext
        val src = source
        lifecycleScope.launch {
            state = withContext(Dispatchers.IO) {
                val target = MacroCalculator.calculate(app)
                val today = AppDatabase.getDatabase(app).consumedSnackDao().getConsumedByDateSync(LocalDate.now().toString())
                val eaten = today.fold(Nutr.ZERO) { a, e -> a + Nutr(e.calories.toDouble(), e.p.toDouble(), e.s.toDouble(), e.t.toDouble()) }
                val staples = DailyStaples.all(app).map { it to DailyStaples.eaten(it, today.map { e -> e.name }) }
                val pending = staples.filterNot { it.second }.fold(Nutr.ZERO) { a, (i, _) -> a + nutr(i) }
                val now = LocalTime.now().let { it.hour * 60 + it.minute }
                val slots = DayPlanner.openSlots(now, today.mapNotNull { MealRepeat.minutes(it.time) })
                val goal = Nutr(target.calories, target.protein, target.carbs, target.fat)
                val pantry = AppDatabase.getDatabase(app).snackDao().getAllSnacksSmart(System.currentTimeMillis()).first()
                val options = (if (src != Source.PANTRY) recipeOptions() else emptyList()) +
                    (if (src != Source.RECIPES) PantryPlates.options(pantry) else emptyList())
                State(target, eaten, staples, slots, DayPlanner.plans(goal, eaten + pending, slots, options), pantry)
            }
            planIndex = 0
            if (isAdded) render()
        }
    }

    private fun nutr(i: MealRepeat.Item) = Nutr(i.calories.toDouble(), i.p.toDouble(), i.s.toDouble(), i.t.toDouble())

    private fun recipeOptions() = FitnessRecipes.ALL.map { r ->
        val m = r.macros(1f)
        DayPlanner.Option(r.id, r.name, when (r.category) {
            FitnessRecipes.Category.BREAKFAST -> DayPlanner.Kind.BREAKFAST
            FitnessRecipes.Category.MAIN -> DayPlanner.Kind.MAIN
            else -> DayPlanner.Kind.SNACK
        }, Nutr(m.kcal.toDouble(), m.p.toDouble(), m.s.toDouble(), m.t.toDouble()))
    }

    // ── Vykreslení ──────────────────────────────────────────────────────────

    private fun render() {
        val s = state ?: return
        root.removeAllViews()
        root.addView(View(requireContext()).apply {
            background = round(100, c(R.color.brand_dark_alpha20))
        }, LinearLayout.LayoutParams(dp(40), dp(4)).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(14) })
        root.addView(pill("IDEÁLNÍ DEN", c(R.color.brand_primary), c(R.color.brand_cream)), wrap())
        root.addView(text("Zbytek dne", 30f, c(R.color.brand_dark), black), wrap().apply { topMargin = dp(10) })
        root.addView(text("Podle predikce výdeje a toho, co už máš snědeno.", 13f, c(R.color.brand_dark)).apply { alpha = 0.7f })

        predictionCard(s)

        section("KAŽDÝ DEN")
        s.staples.forEach { (item, done) -> stapleRow(item, done) }
        root.addView(dashed("+  Přidat pevnou položku (např. shake)") {
            MealBuilderSheet(isPre).asStaple().show(parentFragmentManager, "MealBuilder")
        }, full().apply { topMargin = dp(8) })

        section("NÁVRH NA ZBYTEK DNE")
        sourceChips()
        val plan = s.plans.getOrNull(planIndex)
        val left = s.target.calories - (s.eaten + pendingStaples(s)).kcal
        if (plan == null) {
            root.addView(note(when {
                left < DayPlanner.DONE_KCAL -> "Na dnešek máš splněno. Další jídlo už cíl nepotřebuje."
                s.slots.isEmpty() -> "Dnešní jídla už proběhla. Zbývá ${kcal(left)} – stačí menší svačina."
                source == Source.PANTRY && s.pantry.isEmpty() -> "Špajzka je prázdná. Přidej si potraviny, nebo přepni na recepty."
                else -> "Pro tenhle zbytek dne se nic nehodí. Zkus jiný zdroj."
            }), full())
            return
        }
        plan.meals.forEach { mealCard(it) }
        resultCard(plan, s)
        if (s.plans.size > 1) root.addView(dashed("↻  Jiný návrh  ${planIndex + 1}/${s.plans.size}") {
            planIndex = (planIndex + 1) % s.plans.size; render()
        }, full().apply { topMargin = dp(10) })
    }

    private fun pendingStaples(s: State) = s.staples.filterNot { it.second }.fold(Nutr.ZERO) { a, (i, _) -> a + nutr(i) }

    private fun predictionCard(s: State) {
        val t = s.target
        val card = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(16), dp(18), dp(16))
            background = round(24, c(R.color.brand_dark))
        }
        card.addView(text("PREDIKCE DNE", 9.5f, c(R.color.brand_accent_warm), bold = true).apply { letterSpacing = 0.14f })
        card.addView(text("Cíl ${kcal(t.calories)}", 26f, c(R.color.brand_cream), black))
        t.expenditure?.let { e ->
            card.addView(text("Výdej ≈ ${kcal(e.total)} · kroky do večera ≈ ${num(e.steps.toDouble())}", 12f, c(R.color.brand_cream)).apply { alpha = 0.75f })
        }
        val pend = pendingStaples(s)
        val left = Nutr(t.calories, t.protein, t.carbs, t.fat) - s.eaten - pend
        val row = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL }
        fun col(label: String, value: String, accent: Boolean = false) = row.addView(LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            addView(text(value, 17f, if (accent) c(R.color.brand_accent_warm) else c(R.color.brand_cream), black))
            addView(text(label, 9.5f, c(R.color.brand_cream), bold = true).apply { alpha = 0.6f; letterSpacing = 0.1f })
        }, LinearLayout.LayoutParams(0, -2, 1f))
        col("SNĚDENO", num(s.eaten.kcal))
        col("PEVNÉ", num(pend.kcal))
        col("ZBÝVÁ", num(left.kcal.coerceAtLeast(0.0)), accent = true)
        card.addView(row, full().apply { topMargin = dp(14) })
        card.addView(text("Zbývá B ${g(left.p)} · S ${g(left.s)} · T ${g(left.t)}", 12f, c(R.color.brand_cream)).apply { alpha = 0.75f },
            full().apply { topMargin = dp(8) })
        root.addView(card, full().apply { topMargin = dp(14) })
    }

    private fun stapleRow(item: MealRepeat.Item, done: Boolean) {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(10), dp(12))
            background = round(18, c(R.color.brand_dark_alpha05)).apply { setStroke(dp(1), c(R.color.brand_dark_alpha12)) }
        }
        val info = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        info.addView(text(item.name, 15f, c(R.color.brand_dark), black))
        info.addView(text("${item.calories} kcal · B ${g(item.p.toDouble())} · S ${g(item.s.toDouble())} · T ${g(item.t.toDouble())}", 12f, c(R.color.brand_dark)).apply { alpha = 0.65f })
        row.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
        if (done) row.addView(pill("✓ DNES", c(R.color.brand_primary), c(R.color.brand_cream)))
        else row.addView(pill("ZAPSAT", c(R.color.brand_accent_warm), c(R.color.brand_dark)).apply {
            isClickable = true; contentDescription = "Zapsat ${item.name}"
            setOnClickListener { v -> v.performHapticFeedback(HapticFeedbackConstants.CONFIRM); logStaple(item) }
        })
        row.addView(ImageView(requireContext()).apply {
            setImageResource(R.drawable.ic_close); setColorFilter(c(R.color.brand_dark)); alpha = 0.45f
            setPadding(dp(10), dp(10), dp(6), dp(10)); contentDescription = "Odebrat ${item.name}"
            setOnClickListener { DailyStaples.remove(requireContext(), item.name); load() }
        }, LinearLayout.LayoutParams(dp(36), dp(40)))
        root.addView(row, full().apply { topMargin = dp(8) })
    }

    private fun logStaple(item: MealRepeat.Item) {
        val app = requireContext().applicationContext
        val now = java.util.Date()
        lifecycleScope.launch {
            FoodLog.insert(app, ConsumedSnackEntity(
                date = LocalDate.now().toString(),
                time = java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(now),
                name = item.name, p = item.p, s = item.s, t = item.t, calories = item.calories,
                mealContext = if (isPre) "PRE_WORKOUT" else "POST_WORKOUT", energyKj = item.energyKj, fiber = item.fiber
            ))
            load()
        }
    }

    private fun sourceChips() {
        val row = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL }
        Source.entries.forEach { src ->
            val on = src == source
            row.addView(text(src.label, 13f, if (on) c(R.color.brand_cream) else c(R.color.brand_dark), black).apply {
                gravity = Gravity.CENTER; setPadding(dp(16), dp(8), dp(16), dp(8))
                background = round(100, if (on) c(R.color.brand_dark) else c(R.color.brand_dark_alpha05))
                isClickable = true; contentDescription = "Zdroj návrhu: ${src.label}" + if (on) ", vybráno" else ""
                setOnClickListener { v -> if (!on) { v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); source = src; load() } }
            }, wrap().apply { marginEnd = dp(8) })
        }
        root.addView(row, full().apply { topMargin = dp(8); bottomMargin = dp(2) })
    }

    private fun mealCard(m: DayPlanner.Meal) {
        val s = state ?: return
        val recipe = FitnessRecipes.ALL.firstOrNull { it.id == m.option.id }
        val pantryParts = if (recipe == null) PantryPlates.parts(m.option.id, s.pantry) else emptyList()
        val n = m.nutr
        val name = recipe?.name ?: m.option.name
        val amount = if (recipe != null) portions(m.portions[0])
            else pantryParts.zip(m.portions).joinToString(" · ") { (item, k) -> "${grams((item.grams * k).toFloat())} ${unit(item)}" }
        val card = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(14), dp(8))
            background = round(20, c(R.color.brand_dark_alpha05)).apply { setStroke(dp(1), c(R.color.brand_dark_alpha12)) }
            isClickable = true
            contentDescription = "${m.slot.label}: $name, $amount"
            setOnClickListener { v ->
                v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                if (recipe != null) openMeal(recipe, m.portions[0]) else openPantry(name, pantryParts, m.portions)
            }
        }
        if (recipe != null) {
            val photo = resources.getIdentifier(recipe.photo, "drawable", requireContext().packageName)
            card.addView(ShapeableImageView(requireContext()).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                shapeAppearanceModel = ShapeAppearanceModel.builder().setAllCornerSizes(dp(14).toFloat()).build()
                if (photo != 0) setImageResource(photo)
            }, LinearLayout.LayoutParams(dp(68), dp(68)))
        } else {
            // talíř ze špajzky: miska v barvě značky
            card.addView(ImageView(requireContext()).apply {
                setImageResource(R.drawable.ic_line_bowl); setColorFilter(c(R.color.brand_cream))
                setPadding(dp(18), dp(18), dp(18), dp(18)); background = round(14, c(R.color.brand_primary))
            }, LinearLayout.LayoutParams(dp(68), dp(68)))
        }
        val info = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0) }
        val tag = if (recipe != null) amount else "ZE ŠPAJZKY"
        info.addView(text("${m.slot.label.uppercase(Locale.ROOT)} · ${tag.uppercase(Locale.ROOT)}", 9.5f, c(R.color.brand_accent_deep), bold = true).apply { letterSpacing = 0.1f })
        info.addView(text(name, 14.5f, c(R.color.brand_dark), black).apply { maxLines = 2 })
        if (recipe == null) info.addView(text(amount, 12f, c(R.color.brand_dark)).apply { maxLines = 2 })
        info.addView(text("${n.kcal.roundToInt()} kcal · B ${g(n.p)} · S ${g(n.s)} · T ${g(n.t)}", 12f, c(R.color.brand_dark)).apply { alpha = 0.65f })
        card.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(text("›", 22f, c(R.color.brand_dark), black).apply { alpha = 0.4f })
        root.addView(card, full().apply { topMargin = dp(8) })
    }

    private fun unit(i: PantryPlates.Item) = SnackCatalog.unit(i.snack.weight)
    private fun grams(v: Float) = if (v >= 20f) ((v / 5f).roundToInt() * 5).toString() else v.roundToInt().toString()

    /** Talíř ze špajzky do Složit jídlo – každá potravina s dopočítanými gramy. */
    private fun openPantry(name: String, parts: List<PantryPlates.Item>, portions: List<Double>) {
        val items = parts.zip(portions).map { (item, k) ->
            val sn = item.snack
            val per = item.grams.coerceAtLeast(1f)
            val v = SnackCatalog.scale(sn, per)
            MealBuilderSheet.Ingredient(sn.name, grams(item.grams * k.toFloat()).toFloat(), v.p / per, v.s / per, v.t / per, v.fiber / per, v.kj / per)
        }
        MealBuilderSheet(isPre).prefill(name, items, "IDEÁLNÍ DEN · ZE ŠPAJZKY",
            "Gramy jsou spočítané tak, aby den vyšel na cíl. Uprav je podle sebe a zapiš.").show(parentFragmentManager, "MealBuilder")
        dismiss()
    }

    private fun resultCard(plan: DayPlanner.Plan, s: State) {
        val t = s.target
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL; setPadding(dp(8), dp(12), dp(8), dp(12))
            background = round(18, c(R.color.brand_accent_warm))
        }
        listOf("KCAL" to (plan.total.kcal to t.calories), "BÍLK." to (plan.total.p to t.protein),
            "SACH." to (plan.total.s to t.carbs), "TUKY" to (plan.total.t to t.fat)).forEach { (label, v) ->
            row.addView(LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
                addView(text("${(v.first / v.second.coerceAtLeast(1.0) * 100).roundToInt()} %", 16f, c(R.color.brand_dark), black))
                addView(text(label, 9f, c(R.color.brand_dark), bold = true).apply { alpha = 0.65f; letterSpacing = 0.08f })
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        root.addView(text("S tímhle návrhem splníš cíl dne na", 12f, c(R.color.brand_dark)).apply { alpha = 0.7f }, full().apply { topMargin = dp(14) })
        root.addView(row, full().apply { topMargin = dp(6) })
    }

    private fun openMeal(r: FitnessRecipes.Recipe, portions: Double) {
        val items = r.scaled(portions.toFloat()).map { i ->
            MealBuilderSheet.Ingredient(i.name, i.grams, i.p100 / 100f, i.s100 / 100f, i.t100 / 100f, i.fiber100 / 100f, i.kcal100 * 4.184f / 100f)
        }
        MealBuilderSheet(isPre).prefill(r.name, items, "IDEÁLNÍ DEN · ${portions(portions).uppercase(Locale.ROOT)}",
            "Gramy jsou spočítané tak, aby den vyšel na cíl. Uprav je podle sebe a zapiš.").show(parentFragmentManager, "MealBuilder")
        dismiss()
    }

    // ── Pomocné ─────────────────────────────────────────────────────────────

    private fun section(title: String) = root.addView(text(title, 11f, c(R.color.brand_dark), bold = true).apply {
        alpha = 0.55f; letterSpacing = 0.14f
    }, wrap().apply { topMargin = dp(22) })

    private fun note(msg: String) = text(msg, 13f, c(R.color.brand_dark)).apply {
        alpha = 0.8f; setPadding(dp(16), dp(16), dp(16), dp(16)); background = round(18, c(R.color.brand_dark_alpha05))
    }

    private fun dashed(label: String, onClick: () -> Unit) = text(label, 14f, c(R.color.brand_dark), black).apply {
        gravity = Gravity.CENTER; setPadding(dp(12), dp(14), dp(12), dp(14))
        background = GradientDrawable().apply {
            cornerRadius = dp(18).toFloat(); setStroke(dp(1.5f), c(R.color.brand_dark_alpha30), dp(6).toFloat(), dp(4).toFloat())
        }
        isClickable = true
        setOnClickListener { v -> v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); onClick() }
    }

    private fun pill(label: String, bg: Int, fg: Int) = text(label, 10f, fg, bold = true).apply {
        letterSpacing = 0.12f; setPadding(dp(12), dp(6), dp(12), dp(6)); background = round(100, bg)
    }

    private fun text(s: String, size: Float, color: Int, face: Typeface? = null, bold: Boolean = false) = TextView(requireContext()).apply {
        text = s; textSize = size; setTextColor(color)
        if (face != null) typeface = face else if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun round(radiusDp: Int, color: Int) = GradientDrawable().apply { cornerRadius = dp(radiusDp).toFloat(); setColor(color) }
    private fun wrap() = LinearLayout.LayoutParams(-2, -2)
    private fun full() = LinearLayout.LayoutParams(-1, -2)

    private fun num(v: Double) = String.format(Locale.ROOT, "%,d", v.roundToInt()).replace(',', ' ')
    private fun kcal(v: Double) = "${num(v)} kcal"
    private fun g(v: Double) = "${v.coerceAtLeast(0.0).roundToInt()} g"
    /** „1 porce“, „1,25 porce“ – planner nikdy nedá víc než 2,5 porce. */
    private fun portions(p: Double) =
        (if (p % 1.0 == 0.0) p.toInt().toString() else String.format(Locale.ROOT, "%.2f", p).trimEnd('0').replace('.', ',')) + " porce"
}
