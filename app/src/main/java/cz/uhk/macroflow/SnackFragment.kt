package cz.uhk.macroflow

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButtonToggleGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
                SnackEntity(name = "Rýžové chlebíčky", weight = "40g", p = 3f, s = 30f, t = 1f, isPre = true),
                SnackEntity(name = "Datlová tyčinka", weight = "50g", p = 2f, s = 38f, t = 4f, isPre = true),
                SnackEntity(name = "Proteinový pudink", weight = "200g", p = 20f, s = 15f, t = 3f, isPre = true),
                SnackEntity(name = "Sušené maso", weight = "25g", p = 12f, s = 1f, t = 1f, isPre = true),
                SnackEntity(name = "Vajíčko natvrdo", weight = "60g", p = 7f, s = 1f, t = 5f, isPre = true),
                SnackEntity(name = "Gainer / Sacharidy", weight = "60g", p = 10f, s = 45f, t = 1f, isPre = false),
                SnackEntity(name = "Ovesná kaše", weight = "60g", p = 8f, s = 40f, t = 5f, isPre = false),
                SnackEntity(name = "Piškoty", weight = "50g", p = 3f, s = 38f, t = 1f, isPre = false),
                SnackEntity(name = "Syrovátkový Izolát", weight = "30g", p = 25f, s = 2f, t = 1f, isPre = false),
                SnackEntity(name = "Řecký jogurt 0%", weight = "200g", p = 20f, s = 8f, t = 0f, isPre = false),
                SnackEntity(name = "Nízkotučný tvaroh", weight = "250g", p = 30f, s = 10f, t = 1f, isPre = false)
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
        val globalCeiling = absoluteMax * 1.15f

        filtered.forEach { snack ->
            val card = layoutInflater.inflate(R.layout.item_snack_block, null)

            // --- TADY BYLA CHYBA: VRÁCENÍ TEXTŮ ---
            card.findViewById<TextView>(R.id.tvSnackName).text = snack.name
            card.findViewById<TextView>(R.id.valP).text = "B: ${snack.p.toInt()}g"
            card.findViewById<TextView>(R.id.valS).text = "S: ${snack.s.toInt()}g"
            card.findViewById<TextView>(R.id.valT).text = "T: ${snack.t.toInt()}g"

            val barP = card.findViewById<View>(R.id.barP)
            val barS = card.findViewById<View>(R.id.barS)
            val barT = card.findViewById<View>(R.id.barT)
            barP.layoutParams = (barP.layoutParams as LinearLayout.LayoutParams).apply { weight = snack.p / globalCeiling }
            barS.layoutParams = (barS.layoutParams as LinearLayout.LayoutParams).apply { weight = snack.s / globalCeiling }
            barT.layoutParams = (barT.layoutParams as LinearLayout.LayoutParams).apply { weight = snack.t / globalCeiling }

            // GESTO MAZÁNÍ - ČAS UPRAVEN NA 1000ms (1 SEKUNDA)
            card.setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = event.rawX
                        longPressHandler.postDelayed({
                            isSelectionMode = true
                            deleteOverlay.visibility = View.VISIBLE
                            v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                        }, 1000) // Zrychleno na polovinu
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (isSelectionMode) {
                            val currentX = event.rawX
                            btnDeleteAction.alpha = if (currentX < startX - 100) 1.0f else 0.4f
                            btnCancelAction.alpha = if (currentX > startX + 100) 1.0f else 0.4f
                        }
                        true
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
                        true
                    }
                    else -> false
                }
            }

            if (snack.p > snack.s) listProteins.addView(card) else listCarbs.addView(card)
        }
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

    private fun showAddDialog() {
        val dialog = BottomSheetDialog(requireContext())
        val v = layoutInflater.inflate(R.layout.dialog_add_snack, null)
        dialog.setContentView(v)

        val switchPre = v.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.cbIsPreWorkout)

        // Načtení barev přímo z tvého manuálu [cite: 2026-02-19]
        val colorOn = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.brand_accent_warm)
        val colorOff = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.brand_dark)

        // Vytvoření stavů pro barvu dráhy (track)
        val trackStates = android.content.res.ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked), // Stav ZAPNUTO
                intArrayOf(-android.R.attr.state_checked) // Stav VYPNUTO
            ),
            intArrayOf(colorOn, colorOff)
        )

        // Aplikace barev na přepínač
        switchPre.trackTintList = trackStates

        v.findViewById<Button>(R.id.btnSaveSnack).setOnClickListener {
            // ... zbytek tvého kódu pro uložení ...
            dialog.dismiss()
        }
        dialog.show()
    }
}