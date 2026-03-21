package cz.uhk.macroflow

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.TextInputLayout
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class SnackFragment : Fragment() {

    private lateinit var listCarbs: LinearLayout
    private lateinit var listProteins: LinearLayout
    private lateinit var snackListsContainer: View
    private lateinit var deleteOverlay: View
    private lateinit var btnDeleteAction: View
    private lateinit var btnCancelAction: View

    private var isPreSelected = true
    private val db by lazy { AppDatabase.getDatabase(requireContext()) }
    private val longPressHandler = Handler(Looper.getMainLooper())
    private var isSelectionMode = false
    private var startX = 0f

    private var baseP = 0f
    private var baseS = 0f
    private var baseT = 0f

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_snack, container, false)

        listCarbs = view.findViewById(R.id.listCarbs)
        listProteins = view.findViewById(R.id.listProteins)
        snackListsContainer = view.findViewById(R.id.snackListsContainer)
        deleteOverlay = view.findViewById(R.id.deleteOverlay)
        btnDeleteAction = view.findViewById(R.id.btnDeleteAction)
        btnCancelAction = view.findViewById(R.id.btnCancelAction)

        view.findViewById<MaterialButtonToggleGroup>(R.id.toggleSnackTiming).addOnButtonCheckedListener { _, id, isChecked ->
            if (isChecked) {
                isPreSelected = (id == R.id.btnPreWorkout)
                animateAndRefresh()
            }
        }

        view.findViewById<View>(R.id.btnOpenTinder)?.setOnClickListener {
            val dialog = FoodSwipeDialog()
            dialog.show(parentFragmentManager, "FoodSwipe")
        }

        view.findViewById<View>(R.id.fabAddSnack)?.setOnClickListener {
            showAddDialog()
        }

        observeSnacks()
        return view
    }

    private fun observeSnacks() {
        lifecycleScope.launch {
            db.snackDao().getAllSnacks().collect { snacks ->
                if (snacks.isEmpty()) seedDatabase() else displaySnacks(snacks)
            }
        }
    }

    private fun seedDatabase() {
        lifecycleScope.launch(Dispatchers.IO) {
            val defaultItems = listOf(
                // --- PROTEINOVÁ JÍDLA (P > S) -> Půjdou do listProteins [cite: 2026-03-04] ---
                SnackEntity(name = "Hovězí steak (Sirloin)", weight = "150g", p = 35f, s = 0f, t = 12f, isPre = false),
                SnackEntity(name = "Syrovátkový Izolát", weight = "30g", p = 26f, s = 2f, t = 1f, isPre = true),
                SnackEntity(name = "Grilované kuřecí prso", weight = "150g", p = 32f, s = 0f, t = 3f, isPre = false),
                SnackEntity(name = "Řecký jogurt (Skyr)", weight = "140g", p = 16f, s = 5f, t = 0f, isPre = true),
                SnackEntity(name = "Tvaroh s ořechy", weight = "200g", p = 24f, s = 8f, t = 10f, isPre = false),
                SnackEntity(name = "Tuňák ve vl. šťávě", weight = "130g", p = 28f, s = 0f, t = 1f, isPre = false),
                SnackEntity(name = "Tofu na pánvi", weight = "150g", p = 18f, s = 4f, t = 9f, isPre = true),
                SnackEntity(name = "Vajíčka natvrdo", weight = "120g", p = 15f, s = 1f, t = 11f, isPre = false),
                SnackEntity(name = "Krůtí šunka", weight = "100g", p = 20f, s = 1f, t = 2f, isPre = false),
                SnackEntity(name = "Cottage cheese", weight = "180g", p = 22f, s = 6f, t = 8f, isPre = true),

                // --- SACHARIDOVÁ JÍDLA (S > P) -> Půjdou do listCarbs [cite: 2026-03-04] ---
                SnackEntity(name = "Banán s medem", weight = "150g", p = 1.5f, s = 38f, t = 0.5f, isPre = true),
                SnackEntity(name = "Ovesná kaše s jablkem", weight = "250g", p = 8f, s = 45f, t = 6f, isPre = true),
                SnackEntity(name = "Rýžové chlebíčky", weight = "40g", p = 3f, s = 32f, t = 1f, isPre = true),
                SnackEntity(name = "Těstoviny s pestem", weight = "200g", p = 12f, s = 55f, t = 14f, isPre = false),
                SnackEntity(name = "Toast s džemem", weight = "80g", p = 5f, s = 42f, t = 3f, isPre = true),
                SnackEntity(name = "Kuskus se zeleninou", weight = "200g", p = 9f, s = 48f, t = 4f, isPre = false),
                SnackEntity(name = "Sušené datle", weight = "50g", p = 1f, s = 35f, t = 0f, isPre = true),
                SnackEntity(name = "Gnocchi s rajčaty", weight = "220g", p = 7f, s = 52f, t = 5f, isPre = false),
                SnackEntity(name = "Palačinky s ovocem", weight = "180g", p = 10f, s = 40f, t = 8f, isPre = true),
                SnackEntity(name = "Pečená brambora", weight = "200g", p = 4f, s = 40f, t = 0f, isPre = false)
            )

            defaultItems.forEach { db.snackDao().insertSnack(it) }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun displaySnacks(allSnacks: List<SnackEntity>) {
        listCarbs.removeAllViews()
        listProteins.removeAllViews()

        val filtered = allSnacks.filter { it.isPre == isPreSelected }
        val absoluteMax = allSnacks.flatMap { listOf(it.p, it.s, it.t) }.maxOrNull() ?: 1f
        val globalCeiling = absoluteMax * 1.1f

        filtered.forEach { snack ->
            val card = layoutInflater.inflate(R.layout.item_snack_block, null)
            card.findViewById<TextView>(R.id.tvSnackName).text = snack.name
            card.findViewById<TextView>(R.id.valP).text = "B: ${snack.p.toInt()}g"
            card.findViewById<TextView>(R.id.valS).text = "S: ${snack.s.toInt()}g"
            card.findViewById<TextView>(R.id.valT).text = "T: ${snack.t.toInt()}g"

            setupMacroBar(card.findViewById(R.id.barP), snack.p, globalCeiling)
            setupMacroBar(card.findViewById(R.id.barS), snack.s, globalCeiling)
            setupMacroBar(card.findViewById(R.id.barT), snack.t, globalCeiling)

            // Akce při kliknutí - konzumace [cite: 2026-03-04]
            card.setOnClickListener {
                consumeSnack(snack)
            }

            // Gesta pro mazání zůstávají
            card.setOnTouchListener { v, event ->
                handleDeleteGesture(v, event, snack)
            }

            if (snack.p > snack.s) listProteins.addView(card) else listCarbs.addView(card)
        }
    }

    private fun setupMacroBar(bar: View, value: Float, max: Float) {
        val params = bar.layoutParams as LinearLayout.LayoutParams
        params.weight = if (value > 0) (value / max) else 0.001f
        bar.layoutParams = params
    }

    // Nová funkce pro zápis snědeného jídla do DB
    private fun consumeSnack(snack: SnackEntity) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()) // PŘIDEJ TENTO ŘÁDEK
        val calories = ((snack.p * 4) + (snack.s * 4) + (snack.t * 9)).toInt()

        val consumed = ConsumedSnackEntity(
            date = today,
            time = currentTime, // PŘIDEJ TENTO PARAMETR SEM
            name = snack.name,
            p = snack.p,
            s = snack.s,
            t = snack.t,
            calories = calories
        )

        lifecycleScope.launch(Dispatchers.IO) {
            db.consumedSnackDao().insertConsumed(consumed)
            withContext(Dispatchers.Main) {
                // Haptická odezva pro lepší pocit z "zaškrtnutí" [cite: 2026-03-04]
                view?.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
                Toast.makeText(requireContext(), "${snack.name} přidáno do dnešního dne! 🔥", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showAddDialog() {
        val dialog = BottomSheetDialog(requireContext())
        val v = layoutInflater.inflate(R.layout.dialog_add_snack, null)
        dialog.setContentView(v)

        val btnSave = v.findViewById<Button>(R.id.btnSaveSnack)
        val etName = v.findViewById<EditText>(R.id.etSnackName)
        val etWeight = v.findViewById<EditText>(R.id.etSnackWeight)
        val etP = v.findViewById<EditText>(R.id.etSnackP)
        val etS = v.findViewById<EditText>(R.id.etSnackS)
        val etT = v.findViewById<EditText>(R.id.etSnackT)
        val switchPre = v.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.cbIsPreWorkout)
        val tilName = v.findViewById<TextInputLayout>(R.id.tilSnackName)

        setupSwitchColors(switchPre)

        btnSave.setOnClickListener {
            val name = etName.text.toString()
            val weight = etWeight.text.toString()
            val p = etP.text.toString().replace(",", ".").toFloatOrNull() ?: 0f
            val s = etS.text.toString().replace(",", ".").toFloatOrNull() ?: 0f
            val t = etT.text.toString().replace(",", ".").toFloatOrNull() ?: 0f
            val isPre = switchPre.isChecked

            if (name.isNotEmpty()) {
                lifecycleScope.launch(Dispatchers.IO) {
                    db.snackDao().insertSnack(SnackEntity(name = name, weight = "${weight}g", p = p, s = s, t = t, isPre = isPre))
                    withContext(Dispatchers.Main) { dialog.dismiss() }
                }
            } else {
                etName.error = "Zadej název"
            }
        }

        tilName.setEndIconOnClickListener {
            startBarcodeScanner(etName, etWeight, etP, etS, etT)
        }

        etWeight.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val weight = s.toString().toFloatOrNull() ?: 100f
                if (baseP > 0 || baseS > 0 || baseT > 0) {
                    etP.setText("%.1f".format(baseP * weight).replace(",", "."))
                    etS.setText("%.1f".format(baseS * weight).replace(",", "."))
                    etT.setText("%.1f".format(baseT * weight).replace(",", "."))
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        dialog.show()
    }

    private fun startBarcodeScanner(etName: EditText, etWeight: EditText, etP: EditText, etS: EditText, etT: EditText) {
        val options = GmsBarcodeScannerOptions.Builder().build()
        val scanner = GmsBarcodeScanning.getClient(requireContext(), options)
        scanner.startScan().addOnSuccessListener { barcode ->
            barcode.rawValue?.let { code -> fetchFoodData(code, etName, etWeight, etP, etS, etT) }
        }
    }

    private fun fetchFoodData(barcode: String, etName: EditText, etWeight: EditText, etP: EditText, etS: EditText, etT: EditText) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = URL("https://world.openfoodfacts.org/api/v2/product/$barcode.json").readText()
                val json = JSONObject(response)
                if (json.getInt("status") == 1) {
                    val product = json.getJSONObject("product")
                    val nutriments = product.getJSONObject("nutriments")
                    val p100 = nutriments.optDouble("proteins_100g", 0.0)
                    val s100 = nutriments.optDouble("carbohydrates_100g", 0.0)
                    val t100 = nutriments.optDouble("fat_100g", 0.0)
                    val productName = product.optString("product_name", "Neznámý produkt")

                    withContext(Dispatchers.Main) {
                        baseP = (p100 / 100.0).toFloat()
                        baseS = (s100 / 100.0).toFloat()
                        baseT = (t100 / 100.0).toFloat()
                        etName.setText(productName)
                        etWeight.setText("100")
                        etP.setText("%.1f".format(p100).replace(",", "."))
                        etS.setText("%.1f".format(s100).replace(",", "."))
                        etT.setText("%.1f".format(t100).replace(",", "."))
                    }
                }
            } catch (e: Exception) { /* Chyba sítě */ }
        }
    }

    private fun setupSwitchColors(switch: com.google.android.material.switchmaterial.SwitchMaterial) {
        val colorOn = ContextCompat.getColor(requireContext(), R.color.brand_accent_warm)
        val colorOff = ContextCompat.getColor(requireContext(), R.color.brand_dark)
        switch.trackTintList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf(-android.R.attr.state_checked)),
            intArrayOf(colorOn, colorOff)
        )
    }

    private fun handleDeleteGesture(v: View, event: MotionEvent, snack: SnackEntity): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.rawX
                longPressHandler.postDelayed({
                    isSelectionMode = true
                    deleteOverlay.visibility = View.VISIBLE
                    v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                }, 1000)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isSelectionMode) {
                    val currentX = event.rawX
                    btnDeleteAction.alpha = if (currentX < startX - 100) 1.0f else 0.4f
                    btnCancelAction.alpha = if (currentX > startX + 100) 1.0f else 0.4f
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                longPressHandler.removeCallbacksAndMessages(null)
                if (isSelectionMode) {
                    if (event.rawX < startX - 100) deleteSnackFromDb(snack)
                    deleteOverlay.visibility = View.GONE
                    isSelectionMode = false
                } else if (event.action == MotionEvent.ACTION_UP) {
                    v.performClick()
                }
                return true
            }
        }
        return false
    }

    private fun deleteSnackFromDb(snack: SnackEntity) {
        lifecycleScope.launch(Dispatchers.IO) {
            db.snackDao().deleteSnack(snack)
            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), "${snack.name} odstraněn", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun animateAndRefresh() {
        snackListsContainer.animate().alpha(0f).translationY(15f).setDuration(150).withEndAction {
            lifecycleScope.launch {
                val currentSnacks = db.snackDao().getAllSnacks().first()
                displaySnacks(currentSnacks)
                snackListsContainer.animate().alpha(1f).translationY(0f).setDuration(300).start()
            }
        }.start()
    }
}