package cz.uhk.macroflow.nutrition

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputLayout
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.R
import cz.uhk.macroflow.data.ConsumedSnackEntity
import cz.uhk.macroflow.data.SnackEntity
import cz.uhk.macroflow.data.GeminiRepository
import cz.uhk.macroflow.data.FoodAIResult
import cz.uhk.macroflow.dashboard.MacroCalculator
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
    private lateinit var btnAiScanner: View

    private var isPreSelected = true
    private val db by lazy { AppDatabase.getDatabase(requireContext()) }
    private val longPressHandler = Handler(Looper.getMainLooper())
    private var isSelectionMode = false
    private var startX = 0f

    private var baseP = 0f
    private var baseS = 0f
    private var baseT = 0f
    private var baseFiber = 0f

    private var currentSearchQuery = ""

    // Launcher pro fotoaparát - AI analýza
    private val takePhotoLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val bitmap = result.data?.extras?.get("data") as? Bitmap
            bitmap?.let {
                val scaledBitmap = getResizedBitmap(it, 1024)
                analyzeImageWithAi(scaledBitmap)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_snack, container, false)

        etSearch            = view.findViewById(R.id.etSnackSearch)
        ivClearSearch       = view.findViewById(R.id.ivClearSearch)
        listCarbs           = view.findViewById(R.id.listCarbs)
        listProteins        = view.findViewById(R.id.listProteins)
        snackListsContainer = view.findViewById(R.id.snackListsContainer)
        deleteOverlay       = view.findViewById(R.id.deleteOverlay)
        btnDeleteAction     = view.findViewById(R.id.btnDeleteAction)
        btnCancelAction     = view.findViewById(R.id.btnCancelAction)
        btnAiScanner        = view.findViewById(R.id.btnAiScanner)

        ivClearSearch.setOnClickListener { etSearch.setText("") }

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

        btnAiScanner.setOnClickListener {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            takePhotoLauncher.launch(intent)
        }

        observeSnacks()
        return view
    }

    private fun analyzeImageWithAi(bitmap: Bitmap) {
        val progressView = layoutInflater.inflate(R.layout.layout_ai_progress, null)
        val progressDialog = AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog)
            .setView(progressView)
            .setCancelable(false)
            .create()

        progressDialog.show()

        lifecycleScope.launch {
            val result = GeminiRepository.analyzeFood(bitmap)

            withContext(Dispatchers.Main) {
                progressDialog.dismiss()
                if (result != null) {
                    showAddDialog(result)
                } else {
                    showAiErrorDialog()
                }
            }
        }
    }

    private fun showAiErrorDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("AI se zamyslela...")
            .setMessage("Nepodařilo se mi z fotky určit nutriční hodnoty. Zkus:\n\n1. Lepší světlo\n2. Vyfotit jídlo zblízka\n3. Ujistit se, že jídlo dobře vidět")
            .setPositiveButton("Zkusit znovu") { _, _ -> btnAiScanner.performClick() }
            .setNegativeButton("Zrušit", null)
            .show()
    }

    private fun getResizedBitmap(bm: Bitmap, newWidth: Int): Bitmap {
        val width = bm.width
        val height = bm.height
        val scale = newWidth.toFloat() / width
        val matrix = Matrix()
        matrix.postScale(scale, scale)
        return Bitmap.createBitmap(bm, 0, 0, width, height, matrix, false)
    }

    private fun observeSnacks() {
        lifecycleScope.launch {
            val currentSnacks = db.snackDao().getAllSnacks().first()
            if (currentSnacks.isEmpty()) seedDatabase()

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
                SnackEntity(name = "Pečená brambora", weight = "200g", p = 4f, s = 40f, t = 0f, energyKj = 760f, fiber = 4.2f, isPre = false),
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

    private fun showAddDialog(aiResult: FoodAIResult? = null) {
        val dialog   = BottomSheetDialog(requireContext())
        val v        = layoutInflater.inflate(R.layout.dialog_add_snack, null)
        dialog.setContentView(v)

        val btnSave        = v.findViewById<Button>(R.id.btnSaveSnack)
        val etName         = v.findViewById<EditText>(R.id.etSnackName)
        val etWeight       = v.findViewById<EditText>(R.id.etSnackWeight)
        val etP            = v.findViewById<EditText>(R.id.etSnackP)
        val etS            = v.findViewById<EditText>(R.id.etSnackS)
        val etT            = v.findViewById<EditText>(R.id.etSnackT)
        val etFiber        = v.findViewById<EditText>(R.id.etSnackFiber)
        val tilName        = v.findViewById<TextInputLayout>(R.id.tilSnackName)
        val tvPercentP     = v.findViewById<TextView>(R.id.tvPercentP)
        val tvPercentS     = v.findViewById<TextView>(R.id.tvPercentS)
        val tvPercentT     = v.findViewById<TextView>(R.id.tvPercentT)
        val tvPercentFiber = v.findViewById<TextView>(R.id.tvPercentFiber)

        // Stav pro živý výpočet procent (načteno asynchronně)
        var targetP = 0.0; var targetS = 0.0; var targetT = 0.0; var targetFiber = 0.0
        var alreadyP = 0.0; var alreadyS = 0.0; var alreadyT = 0.0; var alreadyFiber = 0.0

        fun updatePercents() {
            if (targetP <= 0) return
            val colorNormal = requireContext().getColor(R.color.brand_primary)
            val colorOver   = requireContext().getColor(R.color.brand_accent_deep)

            fun applyPercent(tv: TextView, already: Double, adding: Double, target: Double) {
                if (adding <= 0 || target <= 0) { tv.visibility = View.GONE; return }
                val addPct   = (adding / target * 100).toInt()
                val totalPct = ((already + adding) / target * 100).toInt()
                tv.text = if (already > 0.1) "+$addPct% ($totalPct%)" else "+$addPct%"
                tv.setTextColor(if (totalPct > 100) colorOver else colorNormal)
                tv.visibility = View.VISIBLE
            }

            val p = etP.text.toString().replace(",", ".").toDoubleOrNull() ?: 0.0
            val s = etS.text.toString().replace(",", ".").toDoubleOrNull() ?: 0.0
            val t = etT.text.toString().replace(",", ".").toDoubleOrNull() ?: 0.0
            val f = etFiber.text.toString().replace(",", ".").toDoubleOrNull() ?: 0.0

            applyPercent(tvPercentP,     alreadyP,     p, targetP)
            applyPercent(tvPercentS,     alreadyS,     s, targetS)
            applyPercent(tvPercentT,     alreadyT,     t, targetT)
            applyPercent(tvPercentFiber, alreadyFiber, f, targetFiber)
        }

        etP.addTextChangedListener { updatePercents() }
        etS.addTextChangedListener { updatePercents() }
        etT.addTextChangedListener { updatePercents() }
        etFiber.addTextChangedListener { updatePercents() }

        // Načtení denního cíle a dnešního příjmu
        lifecycleScope.launch {
            val today  = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val target   = withContext(Dispatchers.IO) { MacroCalculator.calculate(requireContext()) }
            val consumed = withContext(Dispatchers.IO) { db.consumedSnackDao().getConsumedByDate(today).first() }

            targetP = target.protein; targetS = target.carbs
            targetT = target.fat;     targetFiber = target.fiber

            alreadyP     = consumed.sumOf { it.p.toDouble() }
            alreadyS     = consumed.sumOf { it.s.toDouble() }
            alreadyT     = consumed.sumOf { it.t.toDouble() }
            alreadyFiber = consumed.sumOf { it.fiber.toDouble() }

            updatePercents()
        }

        aiResult?.let {
            etName.setText(it.name)
            etWeight.setText(it.weight.filter { char -> char.isDigit() }.ifEmpty { "100" })
            etP.setText(it.p.toString())
            etS.setText(it.s.toString())
            etT.setText(it.t.toString())
            etFiber.setText(it.fiber.toString())
        }

        btnSave.setOnClickListener {
            val name = etName.text.toString()
            val p = etP.text.toString().replace(",", ".").toFloatOrNull() ?: 0f
            val s = etS.text.toString().replace(",", ".").toFloatOrNull() ?: 0f
            val t = etT.text.toString().replace(",", ".").toFloatOrNull() ?: 0f
            val fiber = etFiber.text.toString().replace(",", ".").toFloatOrNull() ?: 0f
            val energy = (p * 17f) + (s * 17f) + (t * 38f)

            if (name.isNotEmpty()) {
                lifecycleScope.launch(Dispatchers.IO) {
                    db.snackDao().insertSnack(SnackEntity(name = name, weight = "${etWeight.text}g", p = p, s = s, t = t, isPre = isPreSelected, energyKj = energy, fiber = fiber))
                    withContext(Dispatchers.Main) { dialog.dismiss() }
                }
            } else { etName.error = "Zadej název" }
        }

        tilName.setEndIconOnClickListener {
            startBarcodeScanner(etName, etWeight, etP, etS, etT, etFiber)
        }

        dialog.show()
    }

    private fun startBarcodeScanner(etName: EditText, etWeight: EditText, etP: EditText, etS: EditText, etT: EditText, etF: EditText) {
        val scanner = GmsBarcodeScanning.getClient(requireContext())
        scanner.startScan().addOnSuccessListener { barcode ->
            barcode.rawValue?.let { fetchFoodData(it, etName, etWeight, etP, etS, etT, etF) }
        }
    }

    private fun fetchFoodData(barcode: String, etName: EditText, etWeight: EditText, etP: EditText, etS: EditText, etT: EditText, etF: EditText) {
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
                            etName.setText(name); etWeight.setText("100")
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
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
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
        lifecycleScope.launch(Dispatchers.IO) { db.snackDao().deleteSnack(snack) }
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
