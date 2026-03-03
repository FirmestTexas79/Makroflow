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

        view.findViewById<View>(R.id.fabAddSnack).setOnClickListener { showAddDialog() }
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
                SnackEntity(name = "Banán s medem", weight = "150g", p = 1f, s = 35f, t = 0f, isPre = true),
                SnackEntity(name = "Syrovátkový Izolát", weight = "30g", p = 25f, s = 2f, t = 1f, isPre = false)
            )
            defaultItems.forEach { db.snackDao().insertSnack(it) }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun displaySnacks(allSnacks: List<SnackEntity>) {
        listCarbs.removeAllViews()
        listProteins.removeAllViews()

        val filtered = allSnacks.filter { it.isPre == isPreSelected }

        // Logika pro výpočet maximální hodnoty pro škálování pruhů [cite: 2026-03-01]
        val absoluteMax = allSnacks.flatMap { listOf(it.p, it.s, it.t) }.maxOrNull() ?: 1f
        val globalCeiling = absoluteMax * 1.1f // 10% rezerva pro vizuální komfort

        filtered.forEach { snack ->
            val card = layoutInflater.inflate(R.layout.item_snack_block, null)
            card.findViewById<TextView>(R.id.tvSnackName).text = snack.name
            card.findViewById<TextView>(R.id.valP).text = "B: ${snack.p.toInt()}g"
            card.findViewById<TextView>(R.id.valS).text = "S: ${snack.s.toInt()}g"
            card.findViewById<TextView>(R.id.valT).text = "T: ${snack.t.toInt()}g"

            // --- NÁVRAT LOGIKY VODOROVNÝCH SLOUPCŮ ---
            val barP = card.findViewById<View>(R.id.barP)
            val barS = card.findViewById<View>(R.id.barS)
            val barT = card.findViewById<View>(R.id.barT)

            // Nastavení šířky pruhů pomocí LayoutParams (weight) [cite: 2026-02-20]
            setupMacroBar(barP, snack.p, globalCeiling)
            setupMacroBar(barS, snack.s, globalCeiling)
            setupMacroBar(barT, snack.t, globalCeiling)

            card.setOnTouchListener { v, event ->
                handleDeleteGesture(v, event, snack)
            }

            // Rozřazení do sloupců podle převládající složky [cite: 2026-03-01]
            if (snack.p > snack.s) listProteins.addView(card) else listCarbs.addView(card)
        }
    }

    private fun setupMacroBar(bar: View, value: Float, max: Float) {
        val params = bar.layoutParams as LinearLayout.LayoutParams
        // Výpočet poměrné šířky (0.0 až 1.0)
        params.weight = if (value > 0) (value / max) else 0.001f
        bar.layoutParams = params
    }

    private fun showAddDialog() {
        val dialog = BottomSheetDialog(requireContext())
        val v = layoutInflater.inflate(R.layout.dialog_add_snack, null)
        dialog.setContentView(v)

        val switchPre = v.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.cbIsPreWorkout)
        val tilName = v.findViewById<TextInputLayout>(R.id.tilSnackName)
        val etWeight = v.findViewById<EditText>(R.id.etSnackWeight)
        val etP = v.findViewById<EditText>(R.id.etSnackP)
        val etS = v.findViewById<EditText>(R.id.etSnackS)
        val etT = v.findViewById<EditText>(R.id.etSnackT)

        setupSwitchColors(switchPre)

        tilName.setEndIconOnClickListener { startBarcodeScanner(v) }

        // Smart Math přepočet při změně gramáže [cite: 2026-02-20]
        etWeight.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val weight = s.toString().toFloatOrNull() ?: 100f
                if (baseP > 0 || baseS > 0 || baseT > 0) {
                    etP.setText("%.1f".format(baseP * weight))
                    etS.setText("%.1f".format(baseS * weight))
                    etT.setText("%.1f".format(baseT * weight))
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        v.findViewById<Button>(R.id.btnSaveSnack).setOnClickListener {
            saveSnackFromDialog(v, dialog)
        }
        dialog.show()
    }

    private fun startBarcodeScanner(dialogView: View) {
        val options = GmsBarcodeScannerOptions.Builder().build()
        val scanner = GmsBarcodeScanning.getClient(requireContext(), options)
        scanner.startScan().addOnSuccessListener { barcode ->
            barcode.rawValue?.let { code -> fetchFoodData(code, dialogView) }
        }
    }

    private fun fetchFoodData(barcode: String, view: View) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = URL("https://world.openfoodfacts.org/api/v2/product/$barcode.json").readText()
                val json = JSONObject(response)
                if (json.getInt("status") == 1) {
                    val product = json.getJSONObject("product")
                    val nutriments = product.getJSONObject("nutriments")
                    withContext(Dispatchers.Main) {
                        baseP = (nutriments.optDouble("proteins_100g", 0.0) / 100.0).toFloat()
                        baseS = (nutriments.optDouble("carbohydrates_100g", 0.0) / 100.0).toFloat()
                        baseT = (nutriments.optDouble("fat_100g", 0.0) / 100.0).toFloat()
                        view.findViewById<EditText>(R.id.etSnackName).setText(product.optString("product_name", "Neznámý produkt"))
                        view.findViewById<EditText>(R.id.etSnackWeight).setText("100")
                        view.findViewById<EditText>(R.id.etSnackP).setText((baseP * 100).toString())
                        view.findViewById<EditText>(R.id.etSnackS).setText((baseS * 100).toString())
                        view.findViewById<EditText>(R.id.etSnackT).setText((baseT * 100).toString())
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

    private fun saveSnackFromDialog(v: View, dialog: BottomSheetDialog) {
        val name = v.findViewById<EditText>(R.id.etSnackName).text.toString()
        val weight = v.findViewById<EditText>(R.id.etSnackWeight).text.toString()
        val p = v.findViewById<EditText>(R.id.etSnackP).text.toString().replace(",", ".").toFloatOrNull() ?: 0f
        val s = v.findViewById<EditText>(R.id.etSnackS).text.toString().replace(",", ".").toFloatOrNull() ?: 0f
        val t = v.findViewById<EditText>(R.id.etSnackT).text.toString().replace(",", ".").toFloatOrNull() ?: 0f
        val isPre = v.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.cbIsPreWorkout).isChecked

        if (name.isNotEmpty()) {
            lifecycleScope.launch(Dispatchers.IO) {
                db.snackDao().insertSnack(SnackEntity(name = name, weight = "${weight}g", p = p, s = s, t = t, isPre = isPre))
                withContext(Dispatchers.Main) { dialog.dismiss() }
            }
        }
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