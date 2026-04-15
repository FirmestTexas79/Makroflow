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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.dialog_meal_builder, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val sheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        sheet?.let { BottomSheetBehavior.from(it).state = BottomSheetBehavior.STATE_EXPANDED }

        val etMealWeight = view.findViewById<EditText>(R.id.etMealWeight)
        val rvSelected = view.findViewById<RecyclerView>(R.id.rvIngredients)

        selectedAdapter = SelectedAdapter(ingredients) { calculateTotalWeight() }
        rvSelected.layoutManager = LinearLayoutManager(requireContext())
        rvSelected.adapter = selectedAdapter

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

        view.findViewById<View>(R.id.btnScanIngredient).setOnClickListener {
            val scanner = GmsBarcodeScanning.getClient(requireContext())
            scanner.startScan().addOnSuccessListener { barcode ->
                barcode.rawValue?.let { code ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            val response = URL("https://world.openfoodfacts.org/api/v2/product/$code.json").readText()
                            val json = JSONObject(response)
                            if (json.optInt("status") == 1) {
                                val product = json.getJSONObject("product")
                                val nutriments = product.optJSONObject("nutriments")
                                val name = product.optString("product_name_cs").ifEmpty { product.optString("product_name", "Neznámý") }

                                nutriments?.let { n ->
                                    withContext(Dispatchers.Main) {
                                        val newIng = Ingredient(
                                            name = name,
                                            weight = 100f,
                                            perGramP = (n.optDouble("proteins_100g", 0.0) / 100.0).toFloat(),
                                            perGramS = (n.optDouble("carbohydrates_100g", 0.0) / 100.0).toFloat(),
                                            perGramT = (n.optDouble("fat_100g", 0.0) / 100.0).toFloat(),
                                            perGramFiber = (n.optDouble("fiber_100g", 0.0) / 100.0).toFloat(),
                                            perGramKj = (n.optDouble("energy-kj_100g", 0.0) / 100.0).toFloat().let {
                                                if (it == 0f) (n.optDouble("energy-kcal_100g", 0.0) / 100.0).toFloat() * 4.184f else it
                                            }
                                        )
                                        ingredients.add(newIng)
                                        selectedAdapter.notifyDataSetChanged()
                                        calculateTotalWeight()
                                    }
                                }
                            }
                        } catch (e: Exception) { e.printStackTrace() }
                    }
                }
            }
        }

        view.findViewById<View>(R.id.btnSaveMeal).setOnClickListener { saveFinalMeal(view) }
    }

    private fun calculateTotalWeight() {
        if (isUpdatingInternally) return
        val et = view?.findViewById<EditText>(R.id.etMealWeight) ?: return
        isUpdatingInternally = true
        et.setText(ingredients.sumOf { it.weight.toDouble() }.toInt().toString())
        isUpdatingInternally = false
    }

    private fun saveFinalMeal(root: View) {
        val mealName = root.findViewById<EditText>(R.id.etMealName).text.toString().trim()

        // 1. Úprava: Kontrola názvu
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
            p += it.perGramP * it.weight
            s += it.perGramS * it.weight
            t += it.perGramT * it.weight
            fiber += it.perGramFiber * it.weight
            kj += it.perGramKj * it.weight
        }

        val entity = ConsumedSnackEntity(
            date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
            time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()),
            name = mealName,
            p = p, s = s, t = t, fiber = fiber, energyKj = kj,
            calories = (kj / 4.184).toInt(),
            mealContext = if (isPreSelected) "PRE_WORKOUT" else "POST_WORKOUT"
        )

        lifecycleScope.launch(Dispatchers.IO) {
            db.consumedSnackDao().insertConsumed(entity)
            withContext(Dispatchers.Main) {
                // 2. Úprava: Oznámení o úspěšném uložení
                Toast.makeText(requireContext(), "Jídlo uloženo do deníku", Toast.LENGTH_SHORT).show()
                dismiss()
            }
        }
    }

    private inner class SelectedAdapter(val list: MutableList<Ingredient>, val onChange: () -> Unit) : RecyclerView.Adapter<SelectedAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v)
        override fun onCreateViewHolder(p: ViewGroup, t: Int) = VH(LayoutInflater.from(p.context).inflate(R.layout.item_ingredient_editable, p, false))
        override fun onBindViewHolder(h: VH, pos: Int) {
            val item = list[pos]
            val etWeight = h.itemView.findViewById<EditText>(R.id.etIngWeight)
            val tvKcal = h.itemView.findViewById<TextView>(R.id.tvIngKcal)

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
}