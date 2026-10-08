package cz.uhk.macroflow.nutrition

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import cz.uhk.macroflow.R
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.data.ConsumedSnackEntity
import cz.uhk.macroflow.data.SnackEntity
import cz.uhk.macroflow.energy.FoodEnergy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Složit jídlo ze surovin (docs/adr/0068). Každá surovina má krokování po 10 g, souhrn ukazuje
 * kcal a makra celého jídla; „Kolik sníš“ zapíše celé jídlo nebo jeho část. Recept z Fitness
 * receptů sem přijde předvyplněný přes [prefill] a uživatel ho upraví.
 */
class MealBuilderSheet(private val isPreSelected: Boolean = false) : BottomSheetDialogFragment() {

    private val db by lazy { AppDatabase.getDatabase(requireContext()) }

    data class Ingredient(
        val name: String,
        var weight: Float,
        val perGramP: Float,
        val perGramS: Float,
        val perGramT: Float,
        val perGramFiber: Float,
        val perGramKj: Float
    ) {
        val kcal: Int get() = (perGramKj * weight / 4.184f).roundToInt()
    }

    private val ingredients = mutableListOf<Ingredient>()
    private var allPantrySnacks: List<SnackEntity> = emptyList()
    private lateinit var pantryAdapter: PantryAdapter
    private var portion = 1f
    private var prefillName: String? = null
    private var prefillBadge: String? = null
    private var prefillSub: String? = null
    private var staple = false

    /** Předvyplnění (recept): název, suroviny a štítek nad názvem. Volat před show(). */
    fun prefill(name: String, items: List<Ingredient>, badge: String = "RECEPT", sub: String? = null): MealBuilderSheet {
        prefillName = name; prefillBadge = badge; prefillSub = sub
        ingredients.clear(); ingredients += items
        return this
    }

    /** Režim „pevná položka dne“ (docs/adr/0069): neuloží se do deníku, ale do [DailyStaples]. */
    fun asStaple(): MealBuilderSheet { staple = true; return this }

    private fun c(id: Int) = ContextCompat.getColor(requireContext(), id)
    private fun dp(v: Number) = (v.toFloat() * resources.displayMetrics.density).toInt()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.sheet_meal_builder, container, false)

    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)?.let { d ->
            d.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)?.let { sheet ->
                sheet.setBackgroundColor(Color.TRANSPARENT)
                sheet.layoutParams = sheet.layoutParams.apply { height = (resources.displayMetrics.heightPixels * 0.92f).toInt() }
            }
            d.behavior.skipCollapsed = true
            d.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val builder = view.findViewById<View>(R.id.viewMealBuilder)
        val pantry = view.findViewById<View>(R.id.viewPantrySearch)
        val etSearch = view.findViewById<EditText>(R.id.etPantrySearch)

        prefillName?.let { view.findViewById<EditText>(R.id.etMealName).setText(it) }
        prefillBadge?.let { view.findViewById<TextView>(R.id.tvMealBadge).text = it }
        prefillSub?.let { view.findViewById<TextView>(R.id.tvMealSub).text = it }
        if (staple) {
            view.findViewById<TextView>(R.id.tvMealBadge).text = "KAŽDÝ DEN"
            view.findViewById<EditText>(R.id.etMealName).hint = "Např. Proteinový shake"
            view.findViewById<TextView>(R.id.tvMealSub).text = "Pevná položka: plán dne s ní počítá každý den a zapíšeš ji jedním klepnutím."
            view.findViewById<TextView>(R.id.btnSaveMeal).text = "Uložit jako pevnou položku"
        }

        view.findViewById<MacroDonutView>(R.id.donutMeal).apply {
            trackColor = Color.parseColor("#26FEFAE0"); centerTextColor = c(R.color.brand_cream); ringWidthDp = 8f
        }

        pantryAdapter = PantryAdapter { snack ->
            addSnackAsIngredient(snack)
            pantry.visibility = View.GONE; builder.visibility = View.VISIBLE
            etSearch.setText("")
        }
        view.findViewById<RecyclerView>(R.id.rvPantryResults).apply {
            layoutManager = LinearLayoutManager(requireContext()); adapter = pantryAdapter
        }
        lifecycleScope.launch {
            allPantrySnacks = withContext(Dispatchers.IO) { db.snackDao().getAllSnacks().first() }
        }

        view.findViewById<View>(R.id.btnAddFromPantry).setOnClickListener {
            builder.visibility = View.GONE; pantry.visibility = View.VISIBLE
            pantryAdapter.updateList(allPantrySnacks)
            etSearch.requestFocus()
        }
        etSearch.addTextChangedListener { t ->
            val q = t.toString().trim()
            pantryAdapter.updateList(if (q.isEmpty()) allPantrySnacks else allPantrySnacks.filter { it.name.contains(q, ignoreCase = true) })
        }
        view.findViewById<View>(R.id.btnClosePantry).setOnClickListener {
            pantry.visibility = View.GONE; builder.visibility = View.VISIBLE; etSearch.setText("")
        }
        view.findViewById<View>(R.id.btnScanIngredient).setOnClickListener { scan() }
        view.findViewById<View>(R.id.btnSaveMeal).setOnClickListener { saveFinalMeal(view) }

        renderPortions()
        renderIngredients()
    }

    // ── Vykreslení ──────────────────────────────────────────────────────────

    private fun renderIngredients() {
        val v = view ?: return
        val box = v.findViewById<LinearLayout>(R.id.llIngredients)
        box.removeAllViews()
        v.findViewById<View>(R.id.tvIngEmpty).visibility = if (ingredients.isEmpty()) View.VISIBLE else View.GONE
        ingredients.forEachIndexed { i, ing -> box.addView(ingredientRow(ing, i)) }
        renderTotals()
    }

    private fun ingredientRow(ing: Ingredient, index: Int): View {
        val ctx = requireContext()
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(6), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat(); setColor(c(R.color.brand_dark_alpha05)); setStroke(dp(1), c(R.color.brand_dark_alpha10))
            }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { if (index > 0) topMargin = dp(8) }
        }
        val info = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        info.addView(TextView(ctx).apply {
            text = ing.name; textSize = 14.5f; maxLines = 2; setTextColor(c(R.color.brand_dark))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        })
        val macros = TextView(ctx).apply { textSize = 11.5f; setTextColor(c(R.color.brand_primary)) }
        fun macroText() = "${ing.kcal} kcal · B ${fmt(ing.perGramP * ing.weight)} · S ${fmt(ing.perGramS * ing.weight)} · T ${fmt(ing.perGramT * ing.weight)}"
        macros.text = macroText()
        info.addView(macros)
        row.addView(info, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        // krokování po 10 g
        val stepper = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply { cornerRadius = dp(100).toFloat(); setColor(c(R.color.brand_cream)) }
        }
        val et = EditText(ctx).apply {
            background = null; gravity = Gravity.CENTER; textSize = 15f; setTextColor(c(R.color.brand_dark))
            inputType = InputType.TYPE_CLASS_NUMBER; setSelectAllOnFocus(true); minEms = 2
            typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
            setText(ing.weight.roundToInt().toString())
            setPadding(0, 0, 0, 0)
        }
        fun setWeight(w: Float) {
            ing.weight = w.coerceAtLeast(0f)
            macros.text = macroText(); renderTotals()
        }
        et.addTextChangedListener { t -> t.toString().toFloatOrNull()?.let { if (it != ing.weight) setWeight(it) } }
        fun stepBtn(icon: Int, desc: String, delta: Float) = ImageButton(ctx).apply {
            setImageResource(icon); contentDescription = desc
            background = null; setColorFilter(c(R.color.brand_dark)); setPadding(dp(8), dp(8), dp(8), dp(8))
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                val w = ((ing.weight + delta) / 10f).roundToInt() * 10f
                et.setText(w.coerceAtLeast(0f).roundToInt().toString())
            }
        }
        stepper.addView(stepBtn(R.drawable.ic_line_minus, "Ubrat 10 g", -10f), LinearLayout.LayoutParams(dp(34), dp(34)))
        stepper.addView(et, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34)))
        stepper.addView(TextView(ctx).apply { text = "g"; textSize = 12f; setTextColor(c(R.color.brand_primary)) })
        stepper.addView(stepBtn(R.drawable.ic_line_plus, "Přidat 10 g", 10f), LinearLayout.LayoutParams(dp(34), dp(34)))
        row.addView(stepper, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { marginStart = dp(8) })

        row.addView(ImageButton(ctx).apply {
            setImageResource(R.drawable.ic_line_trash); contentDescription = "Odebrat ${ing.name}"
            background = null; setColorFilter(c(R.color.brand_accent_deep)); setPadding(dp(10), dp(10), dp(10), dp(10))
            setOnClickListener { ingredients.remove(ing); renderIngredients() }
        }, LinearLayout.LayoutParams(dp(40), dp(40)))
        return row
    }

    /** Volby „Kolik sníš“: celé jídlo nebo jeho část. */
    private fun renderPortions() {
        val box = view?.findViewById<LinearLayout>(R.id.llPortion) ?: return
        box.removeAllViews()
        listOf(1f to "Celé", 0.5f to "½", 1f / 3 to "⅓", 0.25f to "¼").forEachIndexed { i, (f, label) ->
            val on = kotlin.math.abs(f - portion) < 0.01f
            box.addView(TextView(requireContext()).apply {
                text = label; gravity = Gravity.CENTER; textSize = 14f
                typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
                setTextColor(if (on) c(R.color.brand_cream) else c(R.color.brand_dark))
                background = GradientDrawable().apply {
                    cornerRadius = dp(100).toFloat()
                    setColor(if (on) c(R.color.brand_dark) else c(R.color.brand_dark_alpha05))
                    if (!on) setStroke(dp(1), c(R.color.brand_dark_alpha12))
                }
                setOnClickListener { portion = f; it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); renderPortions(); renderTotals() }
            }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { if (i > 0) marginStart = dp(6) })
        }
    }

    private data class Totals(val g: Float, val p: Float, val s: Float, val t: Float, val fiber: Float, val kj: Float) {
        val kcal: Int get() = FoodEnergy.kjToKcal(kj.toDouble()).roundToInt()
    }

    private fun totals(k: Float = portion) = Totals(
        ingredients.sumOf { it.weight.toDouble() }.toFloat() * k,
        ingredients.sumOf { (it.perGramP * it.weight).toDouble() }.toFloat() * k,
        ingredients.sumOf { (it.perGramS * it.weight).toDouble() }.toFloat() * k,
        ingredients.sumOf { (it.perGramT * it.weight).toDouble() }.toFloat() * k,
        ingredients.sumOf { (it.perGramFiber * it.weight).toDouble() }.toFloat() * k,
        ingredients.sumOf { (it.perGramKj * it.weight).toDouble() }.toFloat() * k
    )

    private fun renderTotals() {
        val v = view ?: return
        val t = totals()
        v.findViewById<MacroDonutView>(R.id.donutMeal).apply {
            setMacros(t.p, t.s, t.t); centerText = t.kcal.toString(); centerSub = "kcal"; invalidate()
        }
        val box = v.findViewById<LinearLayout>(R.id.llMealTotals)
        box.removeAllViews()
        val onDark = listOf(Color.parseColor("#B5C47A"), Color.parseColor("#E9B072"), Color.parseColor("#E8955A"), Color.parseColor("#D9D3B0"))
        box.addView(TextView(requireContext()).apply {
            text = "${t.g.roundToInt()} g" + if (portion < 1f) " · část z ${totals(1f).g.roundToInt()} g" else ""
            textSize = 12f; setTextColor(Color.parseColor("#B3FEFAE0"))
        })
        listOf("Bílkoviny" to t.p, "Sacharidy" to t.s, "Tuky" to t.t, "Vláknina" to t.fiber).forEachIndexed { i, (n, g) ->
            box.addView(LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(5), 0, 0)
                addView(TextView(context).apply { text = n; textSize = 13f; setTextColor(onDark[i]) }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(TextView(context).apply {
                    text = "${fmt(g)} g"; textSize = 13f; setTextColor(c(R.color.brand_cream))
                    typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
                })
            })
        }
    }

    private fun fmt(v: Float) = if (v >= 10f) v.roundToInt().toString() else String.format(Locale.US, "%.1f", v).replace('.', ',').removeSuffix(",0")

    // ── Suroviny ────────────────────────────────────────────────────────────

    private fun addSnackAsIngredient(snack: SnackEntity) {
        val grams = snack.weight.filter { it.isDigit() }.toFloatOrNull()?.takeIf { it > 0 } ?: 100f
        val kjPerGram = if (snack.energyKj > 0.1f) snack.energyKj / grams
            else FoodEnergy.kj(snack.p, snack.s, snack.t, snack.fiber) / grams
        ingredients += Ingredient(snack.name, grams, snack.p / grams, snack.s / grams, snack.t / grams, snack.fiber / grams, kjPerGram)
        renderIngredients()
    }

    private fun scan() {
        GmsBarcodeScanning.getClient(requireContext()).startScan().addOnSuccessListener { barcode ->
            val code = barcode.rawValue ?: return@addOnSuccessListener
            lifecycleScope.launch {
                val product = BarcodeProductLookup.lookup(db, code)
                if (product == null) {
                    context?.let { Toast.makeText(it, "Produkt $code se nepodařilo dohledat", Toast.LENGTH_SHORT).show() }
                    return@launch
                }
                ingredients += Ingredient(product.name, 100f, product.proteins100g / 100f, product.carbs100g / 100f,
                    product.fat100g / 100f, product.fiber100g / 100f, product.energyKj100g / 100f)
                renderIngredients()
            }
        }
    }

    private fun saveFinalMeal(root: View) {
        val mealName = root.findViewById<EditText>(R.id.etMealName).text.toString().trim()
        if (mealName.isEmpty()) { Toast.makeText(requireContext(), "Pojmenuj jídlo", Toast.LENGTH_SHORT).show(); return }
        if (ingredients.isEmpty()) { Toast.makeText(requireContext(), "Přidej aspoň jednu surovinu", Toast.LENGTH_SHORT).show(); return }
        val t = totals()
        if (staple) {
            cz.uhk.macroflow.nutrition.plan.DailyStaples.add(requireContext(),
                MealRepeat.Item(mealName, t.p, t.s, t.t, t.kcal, t.kj, t.fiber, ""))
            root.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            parentFragmentManager.setFragmentResult(cz.uhk.macroflow.nutrition.plan.DayPlanSheet.REFRESH, Bundle())
            dismiss(); return
        }
        val entity = ConsumedSnackEntity(
            date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
            time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()),
            name = mealName,
            p = t.p, s = t.s, t = t.t, fiber = t.fiber, energyKj = t.kj,
            calories = t.kcal,
            mealContext = if (isPreSelected) "PRE_WORKOUT" else "POST_WORKOUT"
        )
        root.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        val app = requireContext().applicationContext
        lifecycleScope.launch(Dispatchers.IO) {
            FoodLog.insert(app, entity)   // lokálně i do cloudu
            withContext(Dispatchers.Main) {
                Toast.makeText(app, "$mealName · ${t.kcal} kcal zapsáno", Toast.LENGTH_SHORT).show()
                if (isAdded) dismiss()
            }
        }
    }

    // ── Spíž ────────────────────────────────────────────────────────────────

    private inner class PantryAdapter(private val onSelect: (SnackEntity) -> Unit) : RecyclerView.Adapter<PantryAdapter.VH>() {
        private val list = mutableListOf<SnackEntity>()
        fun updateList(newList: List<SnackEntity>) { list.clear(); list.addAll(newList); notifyDataSetChanged() }
        inner class VH(v: View) : RecyclerView.ViewHolder(v)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_snack_row, parent, false))
        override fun onBindViewHolder(h: VH, pos: Int) {
            // Stejný řádek jako ve Snacích; klepnutí i „+“ přidá surovinu do jídla
            SnackRowBinder.bind(h.itemView, list[pos], onClick = onSelect, onQuickAdd = onSelect)
        }
        override fun getItemCount() = list.size
    }
}
