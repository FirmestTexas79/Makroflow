package cz.uhk.macroflow

import android.os.Bundle
import android.view.LayoutInflater
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
    private var isPreSelected = true

    // Inicializace databáze přes lazy
    private val db by lazy { AppDatabase.getDatabase(requireContext()) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_snack, container, false)

        listCarbs = view.findViewById(R.id.listCarbs)
        listProteins = view.findViewById(R.id.listProteins)
        snackListsContainer = view.findViewById(R.id.snackListsContainer)

        // Toggle pro Pre/Post workout snacky
        view.findViewById<MaterialButtonToggleGroup>(R.id.toggleSnackTiming).addOnButtonCheckedListener { _, id, isChecked ->
            if (isChecked) {
                isPreSelected = (id == R.id.btnPreWorkout)
                animateAndRefresh()
            }
        }

        // FAB pro přidání nového snacku
        view.findViewById<View>(R.id.fabAddSnack).setOnClickListener { showAddDialog() }

        // Hlavní sledování databáze
        observeSnacks()

        return view
    }

    private fun observeSnacks() {
        lifecycleScope.launch {
            // Sledujeme Flow z DB (automaticky se aktualizuje při změně)
            db.snackDao().getAllSnacks().collect { snacks ->
                if (snacks.isEmpty()) {
                    seedDatabase()
                } else {
                    displaySnacks(snacks)
                }
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

    private fun displaySnacks(allSnacks: List<SnackEntity>) {
        listCarbs.removeAllViews()
        listProteins.removeAllViews()

        val filtered = allSnacks.filter { it.isPre == isPreSelected }
        if (filtered.isEmpty()) return

        // Najdeme max hodnotu pro relativní délku barů
        val absoluteMax = allSnacks.flatMap { listOf(it.p, it.s, it.t) }.maxOrNull() ?: 1f
        val globalCeiling = absoluteMax * 1.15f

        filtered.forEach { snack ->
            val card = layoutInflater.inflate(R.layout.item_snack_block, null)

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

            // Mazání snacku podržením (na pozadí)
            card.setOnLongClickListener {
                lifecycleScope.launch(Dispatchers.IO) {
                    db.snackDao().deleteSnack(snack)
                }
                Toast.makeText(requireContext(), "Snack smazán", Toast.LENGTH_SHORT).show()
                true
            }

            // Rozdělení do sloupců podle převládající složky
            if (snack.p > snack.s) listProteins.addView(card) else listCarbs.addView(card)
        }
    }

    private fun animateAndRefresh() {
        snackListsContainer.animate()
            .alpha(0f)
            .translationY(15f)
            .setDuration(150)
            .withEndAction {
                lifecycleScope.launch {
                    val currentSnacks = db.snackDao().getAllSnacks().first()
                    displaySnacks(currentSnacks)
                    snackListsContainer.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setDuration(300)
                        .start()
                }
            }.start()
    }

    private fun showAddDialog() {
        val dialog = BottomSheetDialog(requireContext())
        val v = layoutInflater.inflate(R.layout.dialog_add_snack, null)
        dialog.setContentView(v)

        v.findViewById<Button>(R.id.btnSaveSnack).setOnClickListener {
            val name = v.findViewById<EditText>(R.id.etSnackName).text.toString()
            val weight = v.findViewById<EditText>(R.id.etSnackWeight).text.toString()
            val p = v.findViewById<EditText>(R.id.etSnackP).text.toString().toFloatOrNull() ?: 0f
            val s = v.findViewById<EditText>(R.id.etSnackS).text.toString().toFloatOrNull() ?: 0f
            val t = v.findViewById<EditText>(R.id.etSnackT).text.toString().toFloatOrNull() ?: 0f
            val isPre = v.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.cbIsPreWorkout).isChecked

            if (name.isNotEmpty()) {
                lifecycleScope.launch(Dispatchers.IO) {
                    db.snackDao().insertSnack(SnackEntity(name = name, weight = "${weight}g", p = p, s = s, t = t, isPre = isPre))
                    withContext(Dispatchers.Main) {
                        dialog.dismiss()
                    }
                }
            }
        }
        dialog.show()
    }
}