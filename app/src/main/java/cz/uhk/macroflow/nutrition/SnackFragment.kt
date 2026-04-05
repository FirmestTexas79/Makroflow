package cz.uhk.macroflow.nutrition

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
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputLayout
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.R
import cz.uhk.macroflow.achievements.AchievementEngine
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

class SnackFragment : Fragment() {

    private lateinit var listCarbs: LinearLayout
    private lateinit var listProteins: LinearLayout
    private lateinit var snackListsContainer: View
    private lateinit var deleteOverlay: View
    private lateinit var btnDeleteAction: View
    private lateinit var btnCancelAction: View
    private lateinit var etSearch: EditText
    private lateinit var ivClearSearch: ImageView

    private var isPreSelected = true
    private val db by lazy { AppDatabase.getDatabase(requireContext()) }
    private val longPressHandler = Handler(Looper.getMainLooper())
    private var isSelectionMode = false
    private var startX = 0f

    // Pomocné proměnné pro přepočet z barcode (hodnoty na 1g)
    private var baseP = 0f
    private var baseS = 0f
    private var baseT = 0f
    private var baseKj = 0f
    private var baseFiber = 0f

    private var currentSearchQuery = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_snack, container, false)

        // Inicializace UI prvků z tvého nového XML
        etSearch            = view.findViewById(R.id.etSnackSearch)
        ivClearSearch       = view.findViewById(R.id.ivClearSearch)
        listCarbs           = view.findViewById(R.id.listCarbs)
        listProteins        = view.findViewById(R.id.listProteins)
        snackListsContainer = view.findViewById(R.id.snackListsContainer)
        deleteOverlay       = view.findViewById(R.id.deleteOverlay)
        btnDeleteAction     = view.findViewById(R.id.btnDeleteAction)
        btnCancelAction     = view.findViewById(R.id.btnCancelAction)

        // ✅ Oprava: Křížek vymaže text
        ivClearSearch.setOnClickListener {
            etSearch.setText("")
        }

        // ✅ Real-time vyhledávání
        etSearch.addTextChangedListener { text ->
            currentSearchQuery = text.toString().trim()
            ivClearSearch.visibility = if (currentSearchQuery.isEmpty()) View.GONE else View.VISIBLE

            lifecycleScope.launch {
                val allSnacks = db.snackDao().getAllSnacks().first()
                displaySnacks(allSnacks)
            }
        }

        view.findViewById<MaterialButtonToggleGroup>(R.id.toggleSnackTiming)
            .addOnButtonCheckedListener { _, id, isChecked ->
                if (isChecked) {
                    isPreSelected = (id == R.id.btnPreWorkout)
                    animateAndRefresh()
                }
            }

        view.findViewById<View>(R.id.btnOpenTinder)?.setOnClickListener {
            FoodSwipeDialog().show(parentFragmentManager, "FoodSwipe")
        }

        view.findViewById<View>(R.id.fabAddSnack)?.setOnClickListener {
            showAddDialog()
        }

        observeSnacks()
        return view
    }

    private fun observeSnacks() {
        lifecycleScope.launch {
            // 1. Nejdřív získáme aktuální stav (jednorázově přes .first())
            val currentSnacks = db.snackDao().getAllSnacks().first()

            // 2. Pokud je prázdno, nasypeme tam defaulty
            if (currentSnacks.isEmpty()) {
                seedDatabase()
            }

            // 3. Pak teprve začneme collectovat (sledovat) změny pro UI
            db.snackDao().getAllSnacks().collect { snacks ->
                displaySnacks(snacks)
            }
        }
    }

    private fun seedDatabase() {
        lifecycleScope.launch(Dispatchers.IO) {
            val defaultItems = listOf(
                SnackEntity(
                    name = "Hovězí steak (Sirloin)",
                    weight = "150g",
                    p = 35f, s = 0f, t = 12f,
                    energyKj = 1050f, fiber = 0f,
                    isPre = false
                ),
                SnackEntity(
                    name = "Syrovátkový Izolát",
                    weight = "30g",
                    p = 26f, s = 2f, t = 1f,
                    energyKj = 480f, fiber = 0.5f,
                    isPre = true
                ),
                SnackEntity(
                    name = "Grilované kuřecí prso",
                    weight = "150g",
                    p = 32f, s = 0f, t = 3f,
                    energyKj = 650f, fiber = 0f,
                    isPre = false
                ),
                SnackEntity(
                    name = "Řecký jogurt (Skyr)",
                    weight = "140g",
                    p = 16f, s = 5f, t = 0f,
                    energyKj = 350f, fiber = 0f,
                    isPre = true
                ),
                SnackEntity(
                    name = "Tvaroh s ořechy",
                    weight = "200g",
                    p = 24f, s = 8f, t = 10f,
                    energyKj = 920f, fiber = 2f,
                    isPre = false
                ),
                SnackEntity(
                    name = "Tuňák ve vl. šťávě",
                    weight = "130g",
                    p = 28f, s = 0f, t = 1f,
                    energyKj = 510f, fiber = 0f,
                    isPre = false
                ),
                SnackEntity(
                    name = "Tofu na pánvi",
                    weight = "150g",
                    p = 18f, s = 4f, t = 9f,
                    energyKj = 720f, fiber = 1.5f,
                    isPre = true
                ),
                SnackEntity(
                    name = "Vajíčka natvrdo",
                    weight = "120g",
                    p = 15f, s = 1f, t = 11f,
                    energyKj = 680f, fiber = 0f,
                    isPre = false
                ),
                SnackEntity(
                    name = "Krůtí šunka",
                    weight = "100g",
                    p = 20f, s = 1f, t = 2f,
                    energyKj = 420f, fiber = 0f,
                    isPre = false
                ),
                SnackEntity(
                    name = "Cottage cheese",
                    weight = "180g",
                    p = 22f, s = 6f, t = 8f,
                    energyKj = 760f, fiber = 0f,
                    isPre = true
                ),
                SnackEntity(
                    name = "Banán s medem",
                    weight = "150g",
                    p = 1.5f, s = 38f, t = 0.5f,
                    energyKj = 680f, fiber = 3.5f,
                    isPre = true
                ),
                SnackEntity(
                    name = "Ovesná kaše s jablkem",
                    weight = "250g",
                    p = 8f, s = 45f, t = 6f,
                    energyKj = 1120f, fiber = 7f,
                    isPre = true
                ),
                SnackEntity(
                    name = "Rýžové chlebíčky",
                    weight = "40g",
                    p = 3f, s = 32f, t = 1f,
                    energyKj = 630f, fiber = 1.2f,
                    isPre = true
                ),
                SnackEntity(
                    name = "Těstoviny s pestem",
                    weight = "200g",
                    p = 12f, s = 55f, t = 14f,
                    energyKj = 1650f, fiber = 4f,
                    isPre = false
                ),
                SnackEntity(
                    name = "Toast s džemem",
                    weight = "80g",
                    p = 5f, s = 42f, t = 3f,
                    energyKj = 910f, fiber = 2.5f,
                    isPre = true
                ),
                SnackEntity(
                    name = "Kuskus se zeleninou",
                    weight = "200g",
                    p = 9f, s = 48f, t = 4f,
                    energyKj = 1100f, fiber = 5.5f,
                    isPre = false
                ),
                SnackEntity(
                    name = "Sušené datle",
                    weight = "50g",
                    p = 1f, s = 35f, t = 0f,
                    energyKj = 600f, fiber = 4f,
                    isPre = true
                ),
                SnackEntity(
                    name = "Gnocchi s rajčaty",
                    weight = "220g",
                    p = 7f, s = 52f, t = 5f,
                    energyKj = 1210f, fiber = 3.8f,
                    isPre = false
                ),
                SnackEntity(
                    name = "Palačinky s ovocem",
                    weight = "180g",
                    p = 10f, s = 40f, t = 8f,
                    energyKj = 1150f, fiber = 3f,
                    isPre = true
                ),
                SnackEntity(
                    name = "Pečená brambora",
                    weight = "200g",
                    p = 4f, s = 40f, t = 0f,
                    energyKj = 760f, fiber = 4.2f,
                    isPre = false
                ),

                SnackEntity(name="Artyčoky", weight="100g", p=2.4f, s=2.6f, t=0.1f, energyKj=170f, fiber=10.8f, isPre=false),
                SnackEntity(name="Brambory rané", weight="100g", p=1.7f, s=16.6f, t=0.2f, energyKj=300f, fiber=1.3f, isPre=false),
                SnackEntity(name="Brambory zimní", weight="100g", p=1.8f, s=18.2f, t=0.3f, energyKj=330f, fiber=1.6f, isPre=false),
                SnackEntity(name="Brokolice, kedlubna", weight="100g", p=4.4f, s=2.9f, t=0.9f, energyKj=140f, fiber=2.8f, isPre=false),
                SnackEntity(name="Brukev", weight="100g", p=2.1f, s=5.8f, t=0.2f, energyKj=130f, fiber=2.2f, isPre=false),
                SnackEntity(name="Celer-bulva", weight="100g", p=1.7f, s=9.9f, t=0.3f, energyKj=210f, fiber=3.7f, isPre=false),
                SnackEntity(name="Celer - řapíkatý", weight="100g", p=1.3f, s=3.7f, t=0.2f, energyKj=140f, fiber=2.4f, isPre=false),
                SnackEntity(name="Cibule", weight="100g", p=1.7f, s=9.6f, t=0.3f, energyKj=200f, fiber=1.4f, isPre=false),
                SnackEntity(name="Cibule - raná", weight="100g", p=2f, s=5.8f, t=0.2f, energyKj=140f, fiber=1.3f, isPre=false),
                SnackEntity(name="Cuketa", weight="100g", p=1.6f, s=2.1f, t=0.4f, energyKj=80f, fiber=0.9f, isPre=false),
                SnackEntity(name="Čekanka salátová", weight="100g", p=1.5f, s=4f, t=0.1f, energyKj=90f, fiber=1.4f, isPre=false),
                SnackEntity(name="Černý kořen", weight="100g", p=1.4f, s=13.3f, t=0.4f, energyKj=260f, fiber=5.3f, isPre=false),
                SnackEntity(name="Červená řepa", weight="100g", p=1.8f, s=10.6f, t=0.1f, energyKj=200f, fiber=2.3f, isPre=false),
                SnackEntity(name="Česnek", weight="100g", p=6.6f, s=26.9f, t=0.2f, energyKj=450f, fiber=0.9f, isPre=false),
                SnackEntity(name="Fazolky", weight="100g", p=2.3f, s=7.1f, t=0.3f, energyKj=160f, fiber=3f, isPre=false),
                SnackEntity(name="Fenykl", weight="100g", p=2.4f, s=6.1f, t=0.3f, energyKj=110f, fiber=3.3f, isPre=false),
                SnackEntity(name="Hrášek", weight="100g", p=6.5f, s=13.3f, t=0.5f, energyKj=320f, fiber=5.2f, isPre=false),
                SnackEntity(name="Chřest", weight="100g", p=2.2f, s=3.5f, t=0.2f, energyKj=90f, fiber=1.8f, isPre=false),
                SnackEntity(name="Kapusta hlávková", weight="100g", p=3.1f, s=6.7f, t=0.5f, energyKj=180f, fiber=3.1f, isPre=false),
                SnackEntity(name="Kapusta kadeřavá", weight="100g", p=4.3f, s=2.1f, t=0.9f, energyKj=140f, fiber=3.3f, isPre=false),
                SnackEntity(name="Kapusta růžičková", weight="100g", p=5.2f, s=7.6f, t=0.6f, energyKj=210f, fiber=1.6f, isPre=false),
                SnackEntity(name="Kopr", weight="100g", p=2.4f, s=7.8f, t=0.2f, energyKj=160f, fiber=1.6f, isPre=false),
                SnackEntity(name="Křen", weight="100g", p=3.9f, s=22.4f, t=0.5f, energyKj=440f, fiber=6.2f, isPre=false),
                SnackEntity(name="Kukuřice cukrová", weight="100g", p=3.5f, s=18.8f, t=2.2f, energyKj=440f, fiber=0.5f, isPre=false),
                SnackEntity(name="Květák", weight="100g", p=2.4f, s=4.4f, t=0.3f, energyKj=120f, fiber=1.8f, isPre=false),
                SnackEntity(name="Lilek", weight="100g", p=1.3f, s=8.2f, t=0.3f, energyKj=160f, fiber=2.3f, isPre=false),
                SnackEntity(name="Mangold listy", weight="100g", p=2.1f, s=2.8f, t=0.3f, energyKj=60f, fiber=2f, isPre=false),
                SnackEntity(name="Meloun červený", weight="100g", p=0.6f, s=5f, t=0.2f, energyKj=110f, fiber=0.3f, isPre=false),
                SnackEntity(name="Meloun žlutý", weight="100g", p=0.5f, s=6.5f, t=0.1f, energyKj=120f, fiber=0.9f, isPre=false),
                SnackEntity(name="Mrkev", weight="100g", p=1.4f, s=9.7f, t=0.3f, energyKj=190f, fiber=3f, isPre=false),
                SnackEntity(name="Okurka nakládačka", weight="100g", p=1f, s=1.8f, t=0.2f, energyKj=50f, fiber=1f, isPre=false),
                SnackEntity(name="Okurka salátová", weight="100g", p=0.7f, s=2.6f, t=0.2f, energyKj=70f, fiber=0.9f, isPre=false),
                SnackEntity(name="Paprika červená", weight="100g", p=1.2f, s=5.2f, t=0.5f, energyKj=120f, fiber=1.6f, isPre=false),
                SnackEntity(name="Paprika zelená", weight="100g", p=0.8f, s=2.6f, t=0.3f, energyKj=70f, fiber=1.9f, isPre=false),
                SnackEntity(name="Patizony", weight="100g", p=2.3f, s=10.2f, t=0.3f, energyKj=210f, fiber=0f, isPre=false),
                SnackEntity(name="Pažitka", weight="100g", p=3.3f, s=8.1f, t=0.7f, energyKj=210f, fiber=2f, isPre=false),
                SnackEntity(name="Pekingské zelí", weight="100g", p=1.1f, s=1f, t=0.3f, energyKj=50f, fiber=1.6f, isPre=false),
                SnackEntity(name="Petržel kořen", weight="100g", p=2.9f, s=12.2f, t=0.6f, energyKj=260f, fiber=1.8f, isPre=false),
                SnackEntity(name="Petržel nať", weight="100g", p=3.7f, s=9f, t=1f, energyKj=240f, fiber=5f, isPre=false),
                SnackEntity(name="Polníček", weight="100g", p=1.8f, s=1.4f, t=0.4f, energyKj=70f, fiber=1.5f, isPre=false),
                SnackEntity(name="Pór", weight="100g", p=2.5f, s=8.6f, t=0.3f, energyKj=190f, fiber=1.5f, isPre=false),
                SnackEntity(name="Rajčata", weight="100g", p=1.1f, s=4.6f, t=0.3f, energyKj=100f, fiber=1.5f, isPre=false),
                SnackEntity(name="Reveň", weight="100g", p=1.3f, s=3.6f, t=0.1f, energyKj=80f, fiber=1.4f, isPre=false),
                SnackEntity(name="Ředkev", weight="100g", p=1.5f, s=5f, t=0.1f, energyKj=90f, fiber=1.1f, isPre=false),
                SnackEntity(name="Ředkvičky", weight="100g", p=1.1f, s=3.7f, t=0.1f, energyKj=80f, fiber=1f, isPre=false),
                SnackEntity(name="Řeřicha zahradní", weight="100g", p=1.6f, s=0.4f, t=0.6f, energyKj=60f, fiber=3.3f, isPre=false),
                SnackEntity(name="Salát hlávkový", weight="100g", p=1.5f, s=2.7f, t=0.3f, energyKj=80f, fiber=0.9f, isPre=false),
                SnackEntity(name="Salát ledový", weight="100g", p=0.7f, s=1.9f, t=0.3f, energyKj=50f, fiber=1.2f, isPre=false),
                SnackEntity(name="Salát římský", weight="100g", p=1f, s=1.7f, t=0.6f, energyKj=70f, fiber=1.2f, isPre=false),
                SnackEntity(name="Sójové výhonky", weight="100g", p=5.5f, s=4.7f, t=1f, energyKj=210f, fiber=2.6f, isPre=false),
                SnackEntity(name="Špenát", weight="100g", p=3.4f, s=4.1f, t=0.6f, energyKj=140f, fiber=2.1f, isPre=false),
                SnackEntity(name="Topinambury", weight="100g", p=1.9f, s=17f, t=0.2f, energyKj=330f, fiber=3.5f, isPre=false),
                SnackEntity(name="Tykev velkoplodá", weight="100g", p=0.8f, s=9f, t=0.1f, energyKj=170f, fiber=2.3f, isPre=false),
                SnackEntity(name="Výhonky vojtěšky (alfalfa)", weight="100g", p=4f, s=0.4f, t=0.7f, energyKj=100f, fiber=1.6f, isPre=false),
                SnackEntity(name="Zelí bílé hlávkové", weight="100g", p=1.5f, s=4.5f, t=0.2f, energyKj=120f, fiber=2.7f, isPre=false),
                SnackEntity(name="Zelí červené hlávkové", weight="100g", p=1.6f, s=6.1f, t=0.3f, energyKj=130f, fiber=3.1f, isPre=false),
                SnackEntity(name="Čočka", weight="100g", p=26.9f, s=59.2f, t=1.2f, energyKj=1440f, fiber=10.6f, isPre=false),
                SnackEntity(name="Fazole", weight="100g", p=23.5f, s=59.8f, t=1.6f, energyKj=1400f, fiber=17f, isPre=false),
                SnackEntity(name="Hrách", weight="100g", p=23.7f, s=61.5f, t=1.4f, energyKj=1420f, fiber=16.6f, isPre=false),
                SnackEntity(name="Sója", weight="100g", p=43.8f, s=16.3f, t=23f, energyKj=1860f, fiber=21f, isPre=false),
                SnackEntity(name="Ananas", weight="100g", p=0.4f, s=10.1f, t=0.2f, energyKj=180f, fiber=1.3f, isPre=false),
                SnackEntity(name="Angrešt", weight="100g", p=0.9f, s=10.6f, t=0.5f, energyKj=210f, fiber=2.8f, isPre=false),
                SnackEntity(name="Avokádo", weight="100g", p=1.9f, s=0.4f, t=23.5f, energyKj=930f, fiber=6.3f, isPre=false),
                SnackEntity(name="Banány", weight="100g", p=0.3f, s=23f, t=0.3f, energyKj=400f, fiber=3.1f, isPre=false),
                SnackEntity(name="Borůvky", weight="100g", p=0.8f, s=14.7f, t=0.7f, energyKj=280f, fiber=2.2f, isPre=false),
                SnackEntity(name="Broskve", weight="100g", p=0.8f, s=12.5f, t=0.2f, energyKj=220f, fiber=1.4f, isPre=false),
                SnackEntity(name="Citrony", weight="100g", p=0.7f, s=10.5f, t=0.5f, energyKj=200f, fiber=1.8f, isPre=false),
                SnackEntity(name="Jablka", weight="100g", p=0.4f, s=14.4f, t=0.4f, energyKj=260f, fiber=1.8f, isPre=false),
                SnackEntity(name="Jahody", weight="100g", p=0.9f, s=8.8f, t=0.6f, energyKj=180f, fiber=1.3f, isPre=false),
                SnackEntity(name="Mandarinky", weight="100g", p=0.9f, s=10.6f, t=0.3f, energyKj=200f, fiber=1.5f, isPre=false),
                SnackEntity(name="Pomeranče", weight="100g", p=0.9f, s=11.7f, t=0.3f, energyKj=200f, fiber=1.8f, isPre=false),
                SnackEntity(name="Arašídy", weight="100g", p=26.9f, s=23.6f, t=44.2f, energyKj=2510f, fiber=6.2f, isPre=false),
                SnackEntity(name="Vlašské ořechy", weight="100g", p=18.4f, s=14.6f, t=60f, energyKj=2820f, fiber=2.7f, isPre=false)
            )
            defaultItems.forEach { db.snackDao().insertSnack(it) }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun displaySnacks(allSnacks: List<SnackEntity>) {
        listCarbs.removeAllViews()
        listProteins.removeAllViews()

        val filtered = allSnacks.filter { snack ->
            val matchesTiming = snack.isPre == isPreSelected
            val matchesSearch = currentSearchQuery.isEmpty() ||
                    snack.name.lowercase().contains(currentSearchQuery.lowercase())
            matchesTiming && matchesSearch
        }

        val absoluteMax = allSnacks.flatMap { listOf(it.p, it.s, it.t) }.maxOrNull() ?: 1f
        val globalCeiling = absoluteMax * 1.1f

        filtered.forEach { snack ->
            val card = layoutInflater.inflate(R.layout.item_snack_block, null)

            card.findViewById<TextView>(R.id.tvSnackName).text   = snack.name
            card.findViewById<TextView>(R.id.tvSnackWeight).text = snack.weight

            card.findViewById<TextView>(R.id.valP).text = "B: ${snack.p.toInt()}g"
            card.findViewById<TextView>(R.id.valS).text = "S: ${snack.s.toInt()}g"
            card.findViewById<TextView>(R.id.valT).text = "T: ${snack.t.toInt()}g"

            // Zobrazení vlákniny, pokud existuje (v listu)
            val tvFiber = card.findViewById<TextView>(R.id.valFiber)
            val dividerFiber = card.findViewById<View>(R.id.dividerFiber)

            if (snack.fiber > 0.05f) {
                tvFiber?.visibility = View.VISIBLE
                tvFiber?.text = "V: ${"%.1f".format(snack.fiber)}g"
                dividerFiber?.visibility = View.VISIBLE
            } else {
                tvFiber?.visibility = View.GONE
                dividerFiber?.visibility = View.GONE
            }

            setupMacroBar(card.findViewById(R.id.barP), snack.p, globalCeiling)
            setupMacroBar(card.findViewById(R.id.barS), snack.s, globalCeiling)
            setupMacroBar(card.findViewById(R.id.barT), snack.t, globalCeiling)

            card.setOnClickListener { consumeSnack(snack) }
            card.setOnTouchListener { v, event -> handleDeleteGesture(v, event, snack) }

            if (snack.p > snack.s) listProteins.addView(card) else listCarbs.addView(card)
        }
    }

    private fun setupMacroBar(bar: View, value: Float, max: Float) {
        val params = bar.layoutParams as LinearLayout.LayoutParams
        params.weight = if (value > 0) ((value / max) * 30f) else 0f
        bar.layoutParams = params
    }

    private fun consumeSnack(snack: SnackEntity) {
        val today    = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val nowTime  = SimpleDateFormat("HH:mm",      Locale.getDefault()).format(Date())
        val calories = ((snack.p * 4) + (snack.s * 4) + (snack.t * 9)).toInt()

        val consumed = ConsumedSnackEntity(
            date = today, time = nowTime, name = snack.name,
            p = snack.p, s = snack.s, t = snack.t,
            calories = calories, energyKj = snack.energyKj,
            fiber = snack.fiber
        )

        lifecycleScope.launch(Dispatchers.IO) {
            db.consumedSnackDao().insertConsumed(consumed)
            withContext(Dispatchers.Main) {
                view?.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                Toast.makeText(requireContext(), "${snack.name} přidáno! 🔥", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showAddDialog() {
        val dialog   = BottomSheetDialog(requireContext())
        val v        = layoutInflater.inflate(R.layout.dialog_add_snack, null)
        dialog.setContentView(v)

        val btnSave      = v.findViewById<Button>(R.id.btnSaveSnack)
        val etName       = v.findViewById<EditText>(R.id.etSnackName)
        val etWeight     = v.findViewById<EditText>(R.id.etSnackWeight)
        val etP          = v.findViewById<EditText>(R.id.etSnackP)
        val etS          = v.findViewById<EditText>(R.id.etSnackS)
        val etT          = v.findViewById<EditText>(R.id.etSnackT)
        val etFiber      = v.findViewById<EditText>(R.id.etSnackFiber)
        val etChol       = v.findViewById<EditText>(R.id.etSnackCholesterol)
        val switchPre    = v.findViewById<SwitchMaterial>(R.id.cbIsPreWorkout)
        val tilName      = v.findViewById<TextInputLayout>(R.id.tilSnackName)

        // Reset base hodnot při otevření nového dialogu
        baseP = 0f; baseS = 0f; baseT = 0f; baseKj = 0f; baseFiber = 0f

        btnSave.setOnClickListener {
            val name   = etName.text.toString()
            val p      = etP.text.toString().replace(",", ".").toFloatOrNull() ?: 0f
            val s      = etS.text.toString().replace(",", ".").toFloatOrNull() ?: 0f
            val t      = etT.text.toString().replace(",", ".").toFloatOrNull() ?: 0f

            // Dobrovolné hodnoty (pokud jsou prázdné, použijeme 0)
            val fiber = etFiber.text.toString().replace(",", ".").toFloatOrNull() ?: 0f
            val chol  = etChol.text.toString().replace(",", ".").toFloatOrNull() ?: 0f

            // Automatický výpočet kJ (B*17 + S*17 + T*38)
            val energy = (p * 17f) + (s * 17f) + (t * 38f)

            if (name.isNotEmpty()) {
                lifecycleScope.launch(Dispatchers.IO) {
                    db.snackDao().insertSnack(
                        SnackEntity(
                            name = name,
                            weight = "${etWeight.text}g",
                            p = p, s = s, t = t,
                            isPre = switchPre.isChecked,
                            energyKj = energy,
                            fiber = fiber
                        )
                    )
                    withContext(Dispatchers.Main) { dialog.dismiss() }
                }
            } else {
                etName.error = "Zadej název"
            }
        }

        tilName.setEndIconOnClickListener {
            startBarcodeScanner(etName, etWeight, etP, etS, etT, etFiber, etChol)
        }

        // Live přepočet při změně váhy
        etWeight.addTextChangedListener { s ->
            val weight = s.toString().toFloatOrNull() ?: 100f
            if (baseP > 0 || baseS > 0 || baseT > 0) {
                etP.setText("%.1f".format(baseP * weight).replace(",", "."))
                etS.setText("%.1f".format(baseS * weight).replace(",", "."))
                etT.setText("%.1f".format(baseT * weight).replace(",", "."))
                etFiber.setText("%.1f".format(baseFiber * weight).replace(",", "."))
            }
        }

        dialog.show()
    }

    private fun startBarcodeScanner(
        etName: EditText, etWeight: EditText, etP: EditText,
        etS: EditText, etT: EditText, etF: EditText, etC: EditText
    ) {
        val scanner = GmsBarcodeScanning.getClient(requireContext())
        scanner.startScan().addOnSuccessListener { barcode ->
            barcode.rawValue?.let { fetchFoodData(it, etName, etWeight, etP, etS, etT, etF, etC) }
        }
    }

    private fun fetchFoodData(
        barcode: String, etName: EditText, etWeight: EditText,
        etP: EditText, etS: EditText, etT: EditText, etF: EditText, etC: EditText
    ) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = URL("https://world.openfoodfacts.org/api/v2/product/$barcode.json").readText()
                val json = JSONObject(response)

                if (json.optInt("status") == 1) {
                    val product = json.getJSONObject("product")
                    val nutriments = product.optJSONObject("nutriments")
                    val name = product.optString("product_name_cs").ifEmpty { product.optString("product_name", "Neznámý") }

                    nutriments?.let { n ->
                        withContext(Dispatchers.Main) {
                            baseP = (n.optDouble("proteins_100g", 0.0) / 100.0).toFloat()
                            baseS = (n.optDouble("carbohydrates_100g", 0.0) / 100.0).toFloat()
                            baseT = (n.optDouble("fat_100g", 0.0) / 100.0).toFloat()
                            baseFiber = (n.optDouble("fiber_100g", 0.0) / 100.0).toFloat()

                            etName.setText(name)
                            etWeight.setText("100") // Reset na 100g pro začátek
                            etP.setText("%.1f".format(baseP * 100).replace(",", "."))
                            etS.setText("%.1f".format(baseS * 100).replace(",", "."))
                            etT.setText("%.1f".format(baseT * 100).replace(",", "."))
                            etF.setText("%.1f".format(baseFiber * 100).replace(",", "."))
                        }
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun handleDeleteGesture(v: View, event: MotionEvent, snack: SnackEntity): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.rawX
                longPressHandler.postDelayed({
                    isSelectionMode = true
                    deleteOverlay.visibility = View.VISIBLE
                    v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                }, 800)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isSelectionMode) {
                    btnDeleteAction.alpha = if (event.rawX < startX - 100) 1.0f else 0.4f
                    btnCancelAction.alpha = if (event.rawX > startX + 100) 1.0f else 0.4f
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                longPressHandler.removeCallbacksAndMessages(null)
                if (isSelectionMode) {
                    if (event.rawX < startX - 100) deleteSnackFromDb(snack)
                    deleteOverlay.visibility = View.GONE
                    isSelectionMode = false
                } else if (event.action == MotionEvent.ACTION_UP) { v.performClick() }
                return true
            }
        }
        return false
    }

    private fun deleteSnackFromDb(snack: SnackEntity) {
        lifecycleScope.launch(Dispatchers.IO) {
            db.snackDao().deleteSnack(snack)
        }
    }

    private fun animateAndRefresh() {
        snackListsContainer.animate()
            .alpha(0f).translationY(15f).setDuration(150)
            .withEndAction {
                lifecycleScope.launch {
                    val currentSnacks = db.snackDao().getAllSnacks().first()
                    displaySnacks(currentSnacks)
                    snackListsContainer.animate()
                        .alpha(1f).translationY(0f).setDuration(300).start()
                }
            }.start()
    }
}