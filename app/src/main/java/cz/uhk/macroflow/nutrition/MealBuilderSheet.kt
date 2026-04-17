package cz.uhk.macroflow.nutrition

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import cz.uhk.macroflow.R
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.data.ConsumedSnackEntity
import cz.uhk.macroflow.data.SnackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class MealBuilderSheet(private val isPreSelected: Boolean) : BottomSheetDialogFragment() {

    private val db by lazy { AppDatabase.getDatabase(requireContext()) }

    data class Ingredient(
        val name: String,
        var weight: Float,
        val perGramP: Float,
        val perGramS: Float,
        val perGramT: Float,
        val perGramFiber: Float,
        val perGramKj: Float
    )

    private val ingredients = mutableListOf<Ingredient>()
    private lateinit var selectedAdapter: SelectedAdapter
    private var isUpdatingInternally = false

    // Všechny snacky ze spíže — načteme jednou
    private var allPantrySnacks: List<SnackEntity> = emptyList()
    private lateinit var pantryAdapter: PantryAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.dialog_meal_builder, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val sheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        sheet?.let { BottomSheetBehavior.from(it).state = BottomSheetBehavior.STATE_EXPANDED }

        // ── Reference na obě vrstvy layoutu ─────────────────────────────────
        val viewMealBuilder  = view.findViewById<LinearLayout>(R.id.viewMealBuilder)
        val viewPantrySearch = view.findViewById<LinearLayout>(R.id.viewPantrySearch)

        // ── MealBuilder refs ─────────────────────────────────────────────────
        val etMealWeight     = view.findViewById<EditText>(R.id.etMealWeight)
        val rvIngredients    = view.findViewById<RecyclerView>(R.id.rvIngredients)
        val btnAddFromPantry = view.findViewById<View>(R.id.btnAddFromPantry)
        val btnScanIngredient = view.findViewById<View>(R.id.btnScanIngredient)
        val btnSaveMeal      = view.findViewById<View>(R.id.btnSaveMeal)

        // ── PantrySearch refs ────────────────────────────────────────────────
        val etPantrySearch   = view.findViewById<EditText>(R.id.etPantrySearch)
        val rvPantryResults  = view.findViewById<RecyclerView>(R.id.rvPantryResults)
        val btnClosePantry   = view.findViewById<View>(R.id.btnClosePantry)

        // ── Adapter pro vybrané ingredience ─────────────────────────────────
        selectedAdapter = SelectedAdapter(ingredients) { calculateTotalWeight(etMealWeight) }
        rvIngredients.layoutManager = LinearLayoutManager(requireContext())
        rvIngredients.adapter = selectedAdapter

        // ── Adapter pro výsledky spíže ───────────────────────────────────────
        pantryAdapter = PantryAdapter { selectedSnack ->
            // Přidání vybrané položky do ingrediencí
            addSnackAsIngredient(selectedSnack)
            // Zavřeme spíž, vrátíme se na builder
            viewPantrySearch.visibility = View.GONE
            viewMealBuilder.visibility  = View.VISIBLE
            etPantrySearch.setText("")
        }
        rvPantryResults.layoutManager = LinearLayoutManager(requireContext())
        rvPantryResults.adapter = pantryAdapter

        // ── Načtení spíže z DB ───────────────────────────────────────────────
        lifecycleScope.launch {
            allPantrySnacks = withContext(Dispatchers.IO) {
                db.snackDao().getAllSnacks().first()
            }
        }

        // ── Celková váha — synchronizace s ingrediencemi ─────────────────────
        etMealWeight.addTextChangedListener { s ->
            if (isUpdatingInternally || ingredients.isEmpty()) return@addTextChangedListener
            val totalInput = s.toString().toFloatOrNull() ?: 0f
            if (totalInput <= 0) return@addTextChangedListener
            val currentTotal = ingredients.sumOf { it.weight.toDouble() }.toFloat()
            if (currentTotal > 0) {
                val ratio = totalInput / currentTotal
                if (Math.abs(ratio - 1.0) > 0.001) {
                    isUpdatingInternally = true
                    ingredients.forEach { it.weight *= ratio }
                    selectedAdapter.notifyDataSetChanged()
                    isUpdatingInternally = false
                }
            }
        }

        // ── Tlačítko "Ze spíže" — přepne pohled ─────────────────────────────
        btnAddFromPantry.setOnClickListener {
            viewMealBuilder.visibility  = View.GONE
            viewPantrySearch.visibility = View.VISIBLE
            // Zobraz všechny položky bez filtru
            pantryAdapter.updateList(allPantrySnacks)
            etPantrySearch.requestFocus()
        }

        // ── Vyhledávání ve spíži ─────────────────────────────────────────────
        etPantrySearch.addTextChangedListener { text ->
            val query = text.toString().trim()
            val filtered = if (query.isEmpty()) {
                allPantrySnacks
            } else {
                allPantrySnacks.filter {
                    it.name.contains(query, ignoreCase = true)
                }
            }
            pantryAdapter.updateList(filtered)
        }

        // ── Zavřít spíž ──────────────────────────────────────────────────────
        btnClosePantry.setOnClickListener {
            viewPantrySearch.visibility = View.GONE
            viewMealBuilder.visibility  = View.VISIBLE
            etPantrySearch.setText("")
        }

        // ── Barcode scanner ──────────────────────────────────────────────────
        btnScanIngredient.setOnClickListener {
            val scanner = GmsBarcodeScanning.getClient(requireContext())
            scanner.startScan().addOnSuccessListener { barcode ->
                barcode.rawValue?.let { code ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            val response = URL("https://world.openfoodfacts.org/api/v2/product/$code.json").readText()
                            val json = JSONObject(response)
                            if (json.optInt("status") == 1) {
                                val product    = json.getJSONObject("product")
                                val nutriments = product.optJSONObject("nutriments")
                                val name = product.optString("product_name_cs")
                                    .ifEmpty { product.optString("product_name", "Neznámý") }
                                nutriments?.let { n ->
                                    withContext(Dispatchers.Main) {
                                        val kj = (n.optDouble("energy-kj_100g", 0.0) / 100.0).toFloat().let {
                                            if (it == 0f) (n.optDouble("energy-kcal_100g", 0.0) / 100.0).toFloat() * 4.184f else it
                                        }
                                        ingredients.add(Ingredient(
                                            name         = name,
                                            weight       = 100f,
                                            perGramP     = (n.optDouble("proteins_100g",      0.0) / 100.0).toFloat(),
                                            perGramS     = (n.optDouble("carbohydrates_100g", 0.0) / 100.0).toFloat(),
                                            perGramT     = (n.optDouble("fat_100g",           0.0) / 100.0).toFloat(),
                                            perGramFiber = (n.optDouble("fiber_100g",         0.0) / 100.0).toFloat(),
                                            perGramKj    = kj
                                        ))
                                        selectedAdapter.notifyDataSetChanged()
                                        calculateTotalWeight(etMealWeight)
                                    }
                                }
                            }
                        } catch (e: Exception) { e.printStackTrace() }
                    }
                }
            }
        }

        // ── Uložit jídlo ─────────────────────────────────────────────────────
        btnSaveMeal.setOnClickListener { saveFinalMeal(view) }
    }

    // ── Přidání SnackEntity jako ingredience ─────────────────────────────────
    private fun addSnackAsIngredient(snack: SnackEntity) {
        val grams = snack.weight.filter { it.isDigit() }.toFloatOrNull()?.takeIf { it > 0 } ?: 100f
        val kjPerGram = if (snack.energyKj > 0.1f) snack.energyKj / grams
        else (snack.p * 17f + snack.s * 17f + snack.t * 38f) / grams
        ingredients.add(Ingredient(
            name         = snack.name,
            weight       = grams,
            perGramP     = snack.p     / grams,
            perGramS     = snack.s     / grams,
            perGramT     = snack.t     / grams,
            perGramFiber = snack.fiber / grams,
            perGramKj    = kjPerGram
        ))
        selectedAdapter.notifyDataSetChanged()
        view?.let { calculateTotalWeight(it.findViewById(R.id.etMealWeight)) }
    }

    private fun calculateTotalWeight(etMealWeight: EditText?) {
        if (isUpdatingInternally) return
        isUpdatingInternally = true
        etMealWeight?.setText(ingredients.sumOf { it.weight.toDouble() }.toInt().toString())
        isUpdatingInternally = false
    }

    private fun saveFinalMeal(root: View) {
        val mealName = root.findViewById<EditText>(R.id.etMealName).text.toString().trim()
        if (mealName.isEmpty()) {
            Toast.makeText(requireContext(), "Musíš zadat název jídla", Toast.LENGTH_SHORT).show()
            return
        }
        if (ingredients.isEmpty()) {
            Toast.makeText(requireContext(), "Přidej aspoň jednu ingredienci", Toast.LENGTH_SHORT).show()
            return
        }

        var p = 0f; var s = 0f; var t = 0f; var fiber = 0f; var kj = 0f
        ingredients.forEach {
            p     += it.perGramP     * it.weight
            s     += it.perGramS     * it.weight
            t     += it.perGramT     * it.weight
            fiber += it.perGramFiber * it.weight
            kj    += it.perGramKj    * it.weight
        }

        val entity = ConsumedSnackEntity(
            date        = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
            time        = SimpleDateFormat("HH:mm",      Locale.getDefault()).format(Date()),
            name        = mealName,
            p = p, s = s, t = t, fiber = fiber, energyKj = kj,
            calories    = (kj / 4.184).toInt(),
            mealContext = if (isPreSelected) "PRE_WORKOUT" else "POST_WORKOUT"
        )

        lifecycleScope.launch(Dispatchers.IO) {
            db.consumedSnackDao().insertConsumed(entity)
            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), "Jídlo uloženo do deníku", Toast.LENGTH_SHORT).show()
                dismiss()
            }
        }
    }

    // ── Adapter pro vybrané ingredience ──────────────────────────────────────
    private inner class SelectedAdapter(
        val list: MutableList<Ingredient>,
        val onChange: () -> Unit
    ) : RecyclerView.Adapter<SelectedAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context)
                .inflate(R.layout.item_ingredient_editable, parent, false))

        override fun onBindViewHolder(h: VH, pos: Int) {
            val item     = list[pos]
            val etWeight = h.itemView.findViewById<EditText>(R.id.etIngWeight)
            val tvKcal   = h.itemView.findViewById<TextView>(R.id.tvIngKcal)

            h.itemView.findViewById<TextView>(R.id.tvIngName).text = item.name

            isUpdatingInternally = true
            etWeight.setText(item.weight.toInt().toString())
            tvKcal.text = "${((item.perGramKj / 4.184f) * item.weight).toInt()} kcal"
            isUpdatingInternally = false

            etWeight.addTextChangedListener { s ->
                if (isUpdatingInternally) return@addTextChangedListener
                item.weight = s.toString().toFloatOrNull() ?: 0f
                tvKcal.text = "${((item.perGramKj / 4.184f) * item.weight).toInt()} kcal"
                onChange()
            }

            h.itemView.findViewById<View>(R.id.btnDeleteIng).setOnClickListener {
                list.removeAt(h.adapterPosition)
                notifyDataSetChanged()
                onChange()
            }
        }

        override fun getItemCount() = list.size
    }

    // ── Adapter pro spíž (výběr ingredience) ─────────────────────────────────
    private inner class PantryAdapter(
        private val onSelect: (SnackEntity) -> Unit
    ) : RecyclerView.Adapter<PantryAdapter.VH>() {

        private val list = mutableListOf<SnackEntity>()

        fun updateList(newList: List<SnackEntity>) {
            list.clear()
            list.addAll(newList)
            notifyDataSetChanged()
        }

        inner class VH(v: View) : RecyclerView.ViewHolder(v)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            // Použijeme jednoduchý row layout — dva TextViews a click
            val row = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_snack_block, parent, false)
            return VH(row)
        }

        override fun onBindViewHolder(h: VH, pos: Int) {
            val snack = list[pos]
            h.itemView.findViewById<TextView>(R.id.tvSnackName).text  = snack.name
            h.itemView.findViewById<TextView>(R.id.tvSnackWeight).text = snack.weight

            val kcal = if (snack.energyKj > 0.1f) (snack.energyKj / 4.184).toInt()
            else ((snack.p * 4) + (snack.s * 4) + (snack.t * 9)).toInt()
            h.itemView.findViewById<TextView>(R.id.tvSnackKcal).text = "$kcal kcal"

            h.itemView.findViewById<TextView>(R.id.valP).text = "B: ${snack.p.toInt()}g"
            h.itemView.findViewById<TextView>(R.id.valS).text = "S: ${snack.s.toInt()}g"
            h.itemView.findViewById<TextView>(R.id.valT).text = "T: ${snack.t.toInt()}g"

            // Vláknina
            val tvFiber     = h.itemView.findViewById<TextView>(R.id.valFiber)
            val divFiber    = h.itemView.findViewById<View>(R.id.dividerFiber)
            if (snack.fiber > 0.05f) {
                tvFiber?.visibility  = View.VISIBLE
                tvFiber?.text        = "V: ${"%.1f".format(snack.fiber)}g"
                divFiber?.visibility = View.VISIBLE
            } else {
                tvFiber?.visibility  = View.GONE
                divFiber?.visibility = View.GONE
            }

            // Progress bary — jen vizuálně, ceiling z lokálního maxima listu
            val ceiling = (list.flatMap { listOf(it.p, it.s, it.t) }.maxOrNull() ?: 1f) * 1.1f
            fun setBar(bar: View?, value: Float) {
                bar ?: return
                val p = bar.layoutParams as LinearLayout.LayoutParams
                p.weight = if (value > 0) ((value / ceiling) * 30f) else 0f
                bar.layoutParams = p
            }
            setBar(h.itemView.findViewById(R.id.barP), snack.p)
            setBar(h.itemView.findViewById(R.id.barS), snack.s)
            setBar(h.itemView.findViewById(R.id.barT), snack.t)

            // Klik = přidej do jídla
            h.itemView.setOnClickListener { onSelect(snack) }
        }

        override fun getItemCount() = list.size
    }
}