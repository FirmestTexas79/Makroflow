package cz.uhk.macroflow

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButtonToggleGroup

class SnackFragment : Fragment() {

    data class SnackItem(
        val name: String, val weight: String,
        val p: Float, val s: Float, val t: Float,
        val isPre: Boolean
    )

    private lateinit var listCarbs: LinearLayout
    private lateinit var listProteins: LinearLayout
    private lateinit var contentWrapper: View // Přejmenováno pro jasnost
    private var isPreSelected = true

    private lateinit var snackListsContainer: View // Referujeme jen na seznamy

    private val defaultSnacks = mutableListOf(
        SnackItem("Banán s medem", "150g", 1f, 35f, 0f, true),
        SnackItem("Rýžové chlebíčky", "40g", 3f, 30f, 1f, true),
        SnackItem("Datlová tyčinka", "50g", 2f, 38f, 4f, true),
        SnackItem("Proteinový pudink", "200g", 20f, 15f, 3f, true),
        SnackItem("Sušené maso", "25g", 12f, 1f, 1f, true),
        SnackItem("Vajíčko natvrdo", "60g", 7f, 1f, 5f, true),
        SnackItem("Gainer / Sacharidy", "60g", 10f, 45f, 1f, false),
        SnackItem("Ovesná kaše", "60g", 8f, 40f, 5f, false),
        SnackItem("Piškoty", "50g", 3f, 38f, 1f, false),
        SnackItem("Syrovátkový Izolát", "30g", 25f, 2f, 1f, false),
        SnackItem("Řecký jogurt 0%", "200g", 20f, 8f, 0f, false),
        SnackItem("Nízkotučný tvaroh", "250g", 30f, 10f, 1f, false)
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_snack, container, false)

        listCarbs = view.findViewById(R.id.listCarbs)
        listProteins = view.findViewById(R.id.listProteins)
        snackListsContainer = view.findViewById(R.id.snackListsContainer) // Tady je změna

        view.findViewById<MaterialButtonToggleGroup>(R.id.toggleSnackTiming).addOnButtonCheckedListener { _, id, isChecked ->
            if (isChecked) {
                isPreSelected = (id == R.id.btnPreWorkout)
                animateAndRefresh()
            }
        }

        view.findViewById<View>(R.id.fabAddSnack).setOnClickListener { showAddDialog() }

        refreshList()
        return view
    }

    private fun animateAndRefresh() {
        // Animujeme pouze karty (seznamy), ne pozadí!
        snackListsContainer.animate()
            .alpha(0f)
            .translationY(15f) // Jen mírný posun dolů
            .setDuration(150)
            .withEndAction {
                refreshList()
                snackListsContainer.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(300)
                    .setInterpolator(android.view.animation.DecelerateInterpolator())
                    .start()
            }.start()
    }

    private fun refreshList() {
        listCarbs.removeAllViews()
        listProteins.removeAllViews()

        // Najdeme globální maximum pro srovnatelnost všech pruhů
        val absoluteMax = defaultSnacks.flatMap { listOf(it.p, it.s, it.t) }.maxOrNull() ?: 1f
        // Přidáme 15% rezervu, aby grafy nebyly "narvané" [cite: 2026-02-19]
        val globalCeiling = absoluteMax * 1.15f

        defaultSnacks.filter { it.isPre == isPreSelected }.forEach { snack ->
            val card = layoutInflater.inflate(R.layout.item_snack_block, null)

            // Textové hodnoty v tvých barvách [cite: 2026-02-19]
            card.findViewById<TextView>(R.id.tvSnackName).text = snack.name
            card.findViewById<TextView>(R.id.valP).text = "B: ${snack.p.toInt()}g"
            card.findViewById<TextView>(R.id.valS).text = "S: ${snack.s.toInt()}g"
            card.findViewById<TextView>(R.id.valT).text = "T: ${snack.t.toInt()}g"

            // Dynamické váhy pro relativní délku pruhů
            val barP = card.findViewById<View>(R.id.barP)
            val barS = card.findViewById<View>(R.id.barS)
            val barT = card.findViewById<View>(R.id.barT)

            barP.layoutParams = (barP.layoutParams as LinearLayout.LayoutParams).apply {
                weight = snack.p / globalCeiling
            }
            barS.layoutParams = (barS.layoutParams as LinearLayout.LayoutParams).apply {
                weight = snack.s / globalCeiling
            }
            barT.layoutParams = (barT.layoutParams as LinearLayout.LayoutParams).apply {
                weight = snack.t / globalCeiling
            }

            // Třídění do sloupců podle tvého manuálu [cite: 2026-02-19]
            if (snack.p > snack.s) listProteins.addView(card) else listCarbs.addView(card)
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
        val swPre = v.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.cbIsPreWorkout)

        btnSave.setOnClickListener {
            val name = etName.text.toString()
            val weight = etWeight.text.toString()
            val p = etP.text.toString().toFloatOrNull() ?: 0f
            val s = etS.text.toString().toFloatOrNull() ?: 0f
            val t = etT.text.toString().toFloatOrNull() ?: 0f
            val isPre = swPre.isChecked

            if (name.isNotEmpty()) {
                defaultSnacks.add(SnackItem(name, "${weight}g", p, s, t, isPre))
                animateAndRefresh()
                dialog.dismiss()
            }
        }
        dialog.show()
    }
}