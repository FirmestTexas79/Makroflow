package cz.uhk.macroflow.nutrition

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
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

    private var currentSearchQuery = ""

    private var photoUri: android.net.Uri? = null

    // ── Runtime oprávnění pro kameru ──────────────────────────────────
    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCamera()
        } else {
            Toast.makeText(
                requireContext(),
                "Fotoaparát není povolen — povol ho v nastavení telefonu.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // ── Launcher pro výsledek z kamery ───────────────────────────────
    private val takePhotoLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = photoUri
            if (uri != null) {
                try {
                    val inputStream = requireContext().contentResolver.openInputStream(uri)
                    val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()

                    if (bitmap != null) {
                        val scaledBitmap = getResizedBitmap(bitmap, 1024)
                        analyzeImageWithAi(scaledBitmap)
                    } else {
                        Toast.makeText(requireContext(), "Nepodařilo se načíst fotku.", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Chyba při načítání fotky: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(requireContext(), "Nepodařilo se načíst fotku.", Toast.LENGTH_SHORT).show()
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
            when {
                requireContext().checkSelfPermission(android.Manifest.permission.CAMERA)
                        == PackageManager.PERMISSION_GRANTED -> {
                    launchCamera()
                }
                shouldShowRequestPermissionRationale(android.Manifest.permission.CAMERA) -> {
                    Toast.makeText(
                        requireContext(),
                        "Fotoaparát potřebujeme pro AI analýzu nutričních hodnot jídla.",
                        Toast.LENGTH_LONG
                    ).show()
                    requestCameraPermission.launch(android.Manifest.permission.CAMERA)
                }
                else -> {
                    requestCameraPermission.launch(android.Manifest.permission.CAMERA)
                }
            }
        }

        view.findViewById<View>(R.id.btnOpenMealBuilder)?.setOnClickListener {
            // Spustíme tvou novou třídu, kterou jsi vytvořil
            // Předáváme isPreSelected, aby jídlo vědělo, jestli je PRE nebo POST workout
            MealBuilderSheet(isPreSelected).show(parentFragmentManager, "MealBuilder")
        }

        observeSnacks()
        return view
    }

    private fun launchCamera() {
        try {
            val photoFile = java.io.File.createTempFile(
                "food_${System.currentTimeMillis()}",
                ".jpg",
                requireContext().cacheDir
            )
            val uri = androidx.core.content.FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                photoFile
            )
            photoUri = uri

            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, uri)
            }
            takePhotoLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Nepodařilo se spustit kameru: ${e.message}", Toast.LENGTH_SHORT).show()
        }
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
                SnackEntity(name="Vlašské ořechy", weight="100g", p=18.4f, s=14.6f, t=60f, energyKj=2820f, fiber=2.7f, isPre=false),
                // === MASO ===
                SnackEntity(name = "Hovězí kližka", weight = "100g", p = 20f, s = 0f, t = 8.1f, energyKj = 640f, fiber = 0f, isPre = false),
                SnackEntity(name = "Hovězí maso přední", weight = "100g", p = 18f, s = 0f, t = 17.5f, energyKj = 960f, fiber = 0f, isPre = false),
                SnackEntity(name = "Hovězí maso zadní", weight = "100g", p = 19.2f, s = 0f, t = 11.7f, energyKj = 760f, fiber = 0f, isPre = false),
                SnackEntity(name = "Hovězí svíčková (raw)", weight = "100g", p = 20f, s = 0f, t = 7.4f, energyKj = 620f, fiber = 0f, isPre = false),
                SnackEntity(name = "Vepřová krkovice", weight = "100g", p = 15.4f, s = 0f, t = 25f, energyKj = 1200f, fiber = 0f, isPre = false),
                SnackEntity(name = "Vepřová kýta na řízky", weight = "100g", p = 18.2f, s = 0f, t = 14.2f, energyKj = 850f, fiber = 0f, isPre = false),
                SnackEntity(name = "Vepřové libové", weight = "100g", p = 18.2f, s = 0f, t = 18.2f, energyKj = 1000f, fiber = 0f, isPre = false),
                SnackEntity(name = "Telecí kýta na řízky", weight = "100g", p = 20.8f, s = 0f, t = 6.1f, energyKj = 580f, fiber = 0f, isPre = false),
                SnackEntity(name = "Jehněčí", weight = "100g", p = 20.4f, s = 0.1f, t = 22.6f, energyKj = 1140f, fiber = 0f, isPre = false),
                SnackEntity(name = "Králičí", weight = "100g", p = 19.7f, s = 0.3f, t = 9.2f, energyKj = 680f, fiber = 0f, isPre = false),

                // === DRŮBEŽ ===
                SnackEntity(name = "Kuřecí prsa (raw)", weight = "100g", p = 23.3f, s = 0.4f, t = 0.9f, energyKj = 430f, fiber = 0f, isPre = false),
                SnackEntity(name = "Krůtí prsa", weight = "100g", p = 24.1f, s = 0f, t = 1f, energyKj = 450f, fiber = 0f, isPre = false),
                SnackEntity(name = "Husí prsa", weight = "100g", p = 15.4f, s = 0.2f, t = 8.4f, energyKj = 580f, fiber = 0f, isPre = false),
                SnackEntity(name = "Krůta (průměr)", weight = "100g", p = 21.9f, s = 0.2f, t = 4.7f, energyKj = 550f, fiber = 0f, isPre = false),

                // === ZVĚŘINA ===
                SnackEntity(name = "Srnčí kýta", weight = "100g", p = 22.1f, s = 0.4f, t = 1.5f, energyKj = 430f, fiber = 0f, isPre = false),
                SnackEntity(name = "Srnčí hřbet", weight = "100g", p = 22.6f, s = 0.4f, t = 3.6f, energyKj = 520f, fiber = 0f, isPre = false),

                // === VNITŘNOSTI ===
                SnackEntity(name = "Játra hovězí", weight = "100g", p = 19f, s = 4.5f, t = 3.9f, energyKj = 540f, fiber = 0f, isPre = false),
                SnackEntity(name = "Játra vepřová", weight = "100g", p = 20.6f, s = 1.5f, t = 4.8f, energyKj = 550f, fiber = 0f, isPre = false),
                SnackEntity(name = "Játra drůbeží", weight = "100g", p = 22.9f, s = 1.2f, t = 4.5f, energyKj = 570f, fiber = 0f, isPre = false),

                // === RYBY A MOŘSKÉ PLODY ===
                SnackEntity(name = "Losos (raw)", weight = "100g", p = 23f, s = 0f, t = 7f, energyKj = 650f, fiber = 0f, isPre = false),
                SnackEntity(name = "Pstruh", weight = "100g", p = 19.7f, s = 0.1f, t = 4.6f, energyKj = 500f, fiber = 0f, isPre = false),
                SnackEntity(name = "Makrela", weight = "100g", p = 18.2f, s = 0.1f, t = 10.5f, energyKj = 700f, fiber = 0f, isPre = false),
                SnackEntity(name = "Sleď", weight = "100g", p = 17.7f, s = 0.1f, t = 12.5f, energyKj = 770f, fiber = 0f, isPre = false),
                SnackEntity(name = "Kapr", weight = "100g", p = 17.5f, s = 0.1f, t = 6.1f, energyKj = 530f, fiber = 0f, isPre = false),
                SnackEntity(name = "Treska (filé)", weight = "100g", p = 16.2f, s = 0.1f, t = 0.6f, energyKj = 300f, fiber = 0f, isPre = false),
                SnackEntity(name = "Krevety", weight = "100g", p = 16.5f, s = 0.1f, t = 0.8f, energyKj = 310f, fiber = 0f, isPre = false),
                SnackEntity(name = "Sardinky v oleji", weight = "100g", p = 21.5f, s = 1.2f, t = 17.8f, energyKj = 1050f, fiber = 0f, isPre = false),
                SnackEntity(name = "Tuňák v oleji", weight = "100g", p = 21f, s = 0.1f, t = 12f, energyKj = 830f, fiber = 0f, isPre = false),
                SnackEntity(name = "Makrela uzená", weight = "100g", p = 23.3f, s = 0.1f, t = 17.7f, energyKj = 1060f, fiber = 0f, isPre = false),

                // === VEJCE ===
                SnackEntity(name = "Vejce celé (60g)", weight = "60g", p = 7.6f, s = 0.8f, t = 6.6f, energyKj = 340f, fiber = 0f, isPre = false),
                SnackEntity(name = "Bílek", weight = "30g", p = 3f, s = 0.5f, t = 0.2f, energyKj = 70f, fiber = 0f, isPre = false),
                SnackEntity(name = "Žloutek", weight = "20g", p = 4.6f, s = 0.3f, t = 6.4f, energyKj = 270f, fiber = 0f, isPre = false),

                // === MLÉČNÉ VÝROBKY ===
                SnackEntity(name = "Mléko plnotučné", weight = "250ml", p = 7.5f, s = 11.5f, t = 8.25f, energyKj = 650f, fiber = 0f, isPre = false),
                SnackEntity(name = "Mléko polotučné", weight = "250ml", p = 8f, s = 11.75f, t = 3.75f, energyKj = 475f, fiber = 0f, isPre = false),
                SnackEntity(name = "Tvaroh bez tuku", weight = "100g", p = 18.8f, s = 4.4f, t = 0.8f, energyKj = 290f, fiber = 0f, isPre = false),
                SnackEntity(name = "Tvaroh jemný", weight = "100g", p = 17.5f, s = 4.2f, t = 2.5f, energyKj = 460f, fiber = 0f, isPre = false),
                SnackEntity(name = "Tvaroh tučný", weight = "100g", p = 12.3f, s = 3.3f, t = 13.5f, energyKj = 770f, fiber = 0f, isPre = false),
                SnackEntity(name = "Zakysaná smetana", weight = "100g", p = 2.6f, s = 4.5f, t = 15f, energyKj = 690f, fiber = 0f, isPre = false),
                SnackEntity(name = "Kefír", weight = "200ml", p = 6.4f, s = 7.4f, t = 3.6f, energyKj = 360f, fiber = 0f, isPre = false),

                // === SÝRY ===
                SnackEntity(name = "Eidam 30%", weight = "100g", p = 30.3f, s = 1.4f, t = 14f, energyKj = 1100f, fiber = 0f, isPre = false),
                SnackEntity(name = "Eidam 45%", weight = "100g", p = 26f, s = 1f, t = 26.1f, energyKj = 1460f, fiber = 0f, isPre = false),
                SnackEntity(name = "Gouda 30%", weight = "100g", p = 26f, s = 1f, t = 14f, energyKj = 1030f, fiber = 0f, isPre = false),
                SnackEntity(name = "Ementál 45%", weight = "100g", p = 28.2f, s = 2.3f, t = 28.4f, energyKj = 1620f, fiber = 0f, isPre = false),
                SnackEntity(name = "Parmezán", weight = "100g", p = 35.6f, s = 0f, t = 25.8f, energyKj = 1560f, fiber = 0f, isPre = false),
                SnackEntity(name = "Mozzarela 45%", weight = "100g", p = 14.6f, s = 1.1f, t = 20f, energyKj = 1010f, fiber = 0f, isPre = false),
                SnackEntity(name = "Camembert 30%", weight = "100g", p = 23.5f, s = 0f, t = 13.5f, energyKj = 900f, fiber = 0f, isPre = false),
                SnackEntity(name = "Niva 50%", weight = "100g", p = 20.7f, s = 1.4f, t = 27f, energyKj = 1550f, fiber = 0f, isPre = false),
                SnackEntity(name = "Olomoucké tvarůžky", weight = "100g", p = 29.7f, s = 2f, t = 0.8f, energyKj = 560f, fiber = 0f, isPre = false),
                SnackEntity(name = "Ricotta", weight = "100g", p = 15f, s = 1f, t = 15f, energyKj = 850f, fiber = 0f, isPre = false),
                SnackEntity(name = "Mascarpone", weight = "100g", p = 4.5f, s = 3.5f, t = 47f, energyKj = 1890f, fiber = 0f, isPre = false),

                // === UZENINY ===
                SnackEntity(name = "Šunka dušená strojová", weight = "100g", p = 17.6f, s = 1.4f, t = 9f, energyKj = 670f, fiber = 0f, isPre = false),
                SnackEntity(name = "Debrecínka", weight = "100g", p = 20.7f, s = 0.1f, t = 20.8f, energyKj = 1130f, fiber = 0f, isPre = false),
                SnackEntity(name = "Lovecký salám", weight = "100g", p = 28.9f, s = 0.2f, t = 37.2f, energyKj = 1090f, fiber = 0f, isPre = false),

                // === OBILOVINY A MOUKY ===
                SnackEntity(name = "Rýže bílá (syrová)", weight = "100g", p = 7f, s = 77f, t = 1f, energyKj = 1470f, fiber = 1.4f, isPre = false),
                SnackEntity(name = "Vločky ovesné", weight = "100g", p = 11.7f, s = 59.8f, t = 7.1f, energyKj = 1480f, fiber = 5.5f, isPre = true),
                SnackEntity(name = "Pohanka", weight = "100g", p = 11f, s = 60f, t = 3f, energyKj = 1480f, fiber = 3.7f, isPre = false),
                SnackEntity(name = "Jáhly", weight = "100g", p = 10.2f, s = 68.2f, t = 4.3f, energyKj = 1530f, fiber = 0f, isPre = false),
                SnackEntity(name = "Mouka pšeničná bílá", weight = "100g", p = 9.8f, s = 70.7f, t = 1.1f, energyKj = 1410f, fiber = 4f, isPre = false),
                SnackEntity(name = "Mouka celozrnná", weight = "100g", p = 11.7f, s = 61f, t = 2f, energyKj = 1310f, fiber = 2.1f, isPre = false),
                SnackEntity(name = "Mouka žitná", weight = "100g", p = 8.3f, s = 67.8f, t = 1.3f, energyKj = 1340f, fiber = 8f, isPre = false),
                SnackEntity(name = "Pšeničné klíčky", weight = "100g", p = 26.6f, s = 30.6f, t = 9.2f, energyKj = 1310f, fiber = 17f, isPre = false),
                SnackEntity(name = "Cornflakes", weight = "50g", p = 3.6f, s = 39.85f, t = 0.3f, energyKj = 750f, fiber = 2f, isPre = true),

                // === PEČIVO A CHLÉB ===
                SnackEntity(name = "Žitný chléb", weight = "60g", p = 3.84f, s = 27.78f, t = 0.6f, energyKj = 558f, fiber = 3.6f, isPre = false),
                SnackEntity(name = "Celozrnný chléb", weight = "60g", p = 4.26f, s = 24.84f, t = 0.54f, energyKj = 516f, fiber = 4.86f, isPre = false),
                SnackEntity(name = "Knäckebrot original", weight = "30g", p = 2.7f, s = 20.1f, t = 0.42f, energyKj = 405f, fiber = 4.38f, isPre = false),
                SnackEntity(name = "Bílý rohlík", weight = "50g", p = 3.9f, s = 25.6f, t = 0.7f, energyKj = 525f, fiber = 1.5f, isPre = false),

                // === HOUBY ===
                SnackEntity(name = "Žampiony", weight = "100g", p = 3.3f, s = 4.8f, t = 0.6f, energyKj = 70f, fiber = 2f, isPre = false),
                SnackEntity(name = "Hřiby", weight = "100g", p = 5.1f, s = 5.2f, t = 0.3f, energyKj = 80f, fiber = 6f, isPre = false),
                SnackEntity(name = "Houby sušené", weight = "20g", p = 7.4f, s = 8.22f, t = 0.54f, energyKj = 282f, fiber = 9.2f, isPre = false),

                // === ZBÝVAJÍCÍ OVOCE ===
                SnackEntity(name = "Avokádo", weight = "100g", p = 1.9f, s = 0.4f, t = 23.5f, energyKj = 930f, fiber = 6.3f, isPre = false),
                SnackEntity(name = "Hrušky", weight = "100g", p = 0.5f, s = 15.8f, t = 0.4f, energyKj = 280f, fiber = 2.4f, isPre = false),
                SnackEntity(name = "Maliny", weight = "100g", p = 1f, s = 11.6f, t = 0.8f, energyKj = 230f, fiber = 5.2f, isPre = false),
                SnackEntity(name = "Kiwi", weight = "100g", p = 1f, s = 9.1f, t = 0.5f, energyKj = 210f, fiber = 1.1f, isPre = false),
                SnackEntity(name = "Mango", weight = "100g", p = 0.6f, s = 16f, t = 0.3f, energyKj = 290f, fiber = 1.7f, isPre = false),
                SnackEntity(name = "Meruňky", weight = "100g", p = 1f, s = 13.4f, t = 0.3f, energyKj = 240f, fiber = 1f, isPre = false),
                SnackEntity(name = "Švestky", weight = "100g", p = 0.8f, s = 16.2f, t = 0.3f, energyKj = 280f, fiber = 1.5f, isPre = false),
                SnackEntity(name = "Třešně", weight = "100g", p = 0.9f, s = 14.7f, t = 0.5f, energyKj = 270f, fiber = 0.5f, isPre = false),
                SnackEntity(name = "Hrozny", weight = "100g", p = 0.7f, s = 18.2f, t = 0.5f, energyKj = 290f, fiber = 1.5f, isPre = false),
                SnackEntity(name = "Brusinky", weight = "100g", p = 0.4f, s = 13.7f, t = 0.8f, energyKj = 260f, fiber = 1.5f, isPre = false),

                // === ZBÝVAJÍCÍ OŘECHY ===
                SnackEntity(name = "Mandle", weight = "30g", p = 5.25f, s = 5.7f, t = 15.72f, energyKj = 777f, fiber = 1.8f, isPre = false),
                SnackEntity(name = "Kešu", weight = "30g", p = 4.8f, s = 8.1f, t = 13.8f, energyKj = 759f, fiber = 0.96f, isPre = false),
                SnackEntity(name = "Pistácie", weight = "30g", p = 6.12f, s = 5.13f, t = 16.41f, energyKj = 798f, fiber = 1.83f, isPre = false),
                SnackEntity(name = "Lískové ořechy", weight = "30g", p = 4.14f, s = 3.27f, t = 19.5f, energyKj = 861f, fiber = 1.05f, isPre = false),
                SnackEntity(name = "Slunečnicová semínka", weight = "30g", p = 7.95f, s = 3.6f, t = 14.7f, energyKj = 750f, fiber = 1.89f, isPre = false),
                SnackEntity(name = "Mák", weight = "20g", p = 3.74f, s = 4.6f, t = 7.8f, energyKj = 436f, fiber = 4f, isPre = false),

                // === TUKY A OLEJE ===
                SnackEntity(name = "Olej (olivový/řepkový)", weight = "15ml", p = 0f, s = 0f, t = 15f, energyKj = 555f, fiber = 0f, isPre = false),
                SnackEntity(name = "Máslo", weight = "10g", p = 0.1f, s = 0.06f, t = 8.05f, energyKj = 301f, fiber = 0f, isPre = false),

                // === PŘÍLOHY (vařené) ===
                SnackEntity(name = "Rýže vařená bílá", weight = "150g", p = 3.6f, s = 41.4f, t = 0.45f, energyKj = 780f, fiber = 0.3f, isPre = false),
                SnackEntity(name = "Rýže vařená natural", weight = "150g", p = 4.05f, s = 40.95f, t = 1.2f, energyKj = 810f, fiber = 1.95f, isPre = false),
                SnackEntity(name = "Těstoviny vařené bezvaječné", weight = "200g", p = 10f, s = 47.8f, t = 2.2f, energyKj = 1100f, fiber = 3f, isPre = false),
                SnackEntity(name = "Houskové knedlíky", weight = "150g", p = 10.65f, s = 76.5f, t = 1.65f, energyKj = 1545f, fiber = 0f, isPre = false),
                SnackEntity(name = "Brambory vařené", weight = "200g", p = 4f, s = 29.6f, t = 0.2f, energyKj = 580f, fiber = 4.2f, isPre = false),

                // === SUŠENÉ OVOCE ===
                SnackEntity(name = "Sušené meruňky", weight = "50g", p = 2.5f, s = 24f, t = 0.25f, energyKj = 500f, fiber = 4.3f, isPre = true),
                SnackEntity(name = "Hrozinky", weight = "30g", p = 0.69f, s = 21.36f, t = 0.15f, energyKj = 288f, fiber = 1.62f, isPre = false),
                SnackEntity(name = "Sušené fíky", weight = "50g", p = 2f, s = 34.2f, t = 0.8f, energyKj = 485f, fiber = 6.45f, isPre = false),
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
                    snack.name.lowercase().contains(currentSearchQuery.lowercase(), ignoreCase = true)
            matchesTiming && matchesSearch
        }

        // Výpočet stropu pro progress bary
        val absoluteMax = allSnacks.flatMap { listOf(it.p, it.s, it.t) }.maxOrNull() ?: 1f
        val globalCeiling = absoluteMax * 1.1f

        filtered.forEach { snack ->
            val card = layoutInflater.inflate(R.layout.item_snack_block, null)

            // Základní textové pole
            card.findViewById<TextView>(R.id.tvSnackName).text = snack.name
            card.findViewById<TextView>(R.id.tvSnackWeight).text = snack.weight

            // --- ZÁCHRANNÁ SÍŤ A VÝPOČET ENERGIE ---
            // Pokud energyKj chybí (je <= 0.1), vypočítáme ho z maker (B=17kJ, S=17kJ, T=38kJ)
            val finalEnergyKj = if (snack.energyKj > 0.1f) {
                snack.energyKj
            } else {
                (snack.p * 17f) + (snack.s * 17f) + (snack.t * 38f)
            }

            // Převod na kcal (1 kcal = 4.184 kJ) a zobrazení v pillu
            val kcalValue = (finalEnergyKj / 4.184).toInt()
            card.findViewById<TextView>(R.id.tvSnackKcal).text = "$kcalValue kcal"

            // Makra texty
            card.findViewById<TextView>(R.id.valP).text = "B: ${snack.p.toInt()}g"
            card.findViewById<TextView>(R.id.valS).text = "S: ${snack.s.toInt()}g"
            card.findViewById<TextView>(R.id.valT).text = "T: ${snack.t.toInt()}g"

            // Vláknina - podmíněné zobrazení
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

            // Progress bary
            setupMacroBar(card.findViewById(R.id.barP), snack.p, globalCeiling)
            setupMacroBar(card.findViewById(R.id.barS), snack.s, globalCeiling)
            setupMacroBar(card.findViewById(R.id.barT), snack.t, globalCeiling)

            // Interakce
            card.setOnClickListener { consumeSnack(snack) }
            card.setOnTouchListener { v, event -> handleDeleteGesture(v, event, snack) }

            // Třídění do sloupců (Bílkoviny vs Sacharidy)
            if (snack.p > snack.s) {
                listProteins.addView(card)
            } else {
                listCarbs.addView(card)
            }
        }
    }

    private fun setupMacroBar(bar: View, value: Float, max: Float) {
        val params = bar.layoutParams as LinearLayout.LayoutParams
        params.weight = if (value > 0) ((value / max) * 30f) else 0f
        bar.layoutParams = params
    }

    private fun consumeSnack(snack: SnackEntity) {
        showConsumeDialog(snack)
    }

    private fun showConsumeDialog(snack: SnackEntity) {
        val dialog = BottomSheetDialog(requireContext())
        val v      = layoutInflater.inflate(R.layout.dialog_consume_snack, null)
        dialog.setContentView(v)

        val tvName         = v.findViewById<EditText>(R.id.tvConsumeName)
        val etWeight       = v.findViewById<EditText>(R.id.etConsumeWeight)
        val tvP            = v.findViewById<EditText>(R.id.tvConsumeP)
        val tvS            = v.findViewById<EditText>(R.id.tvConsumeS)
        val tvT            = v.findViewById<EditText>(R.id.tvConsumeT)
        val tvFiber        = v.findViewById<EditText>(R.id.tvConsumeFiber)
        val tvCalories     = v.findViewById<EditText>(R.id.tvConsumeCalories)
        val tvPercentP     = v.findViewById<TextView>(R.id.tvConsumePercentP)
        val tvPercentS     = v.findViewById<TextView>(R.id.tvConsumePercentS)
        val tvPercentT     = v.findViewById<TextView>(R.id.tvConsumePercentT)
        val tvPercentFiber = v.findViewById<TextView>(R.id.tvConsumePercentFiber)
        val btnConfirm     = v.findViewById<Button>(R.id.btnConsumeConfirm)

        // Výchozí váha ze snacku (číslo před "g")
        val defaultGrams = snack.weight.filter { it.isDigit() }.toFloatOrNull() ?: 100f

        // Per-gram hodnoty z uloženého snacku
        val perGramP     = if (defaultGrams > 0) snack.p     / defaultGrams else 0f
        val perGramS     = if (defaultGrams > 0) snack.s     / defaultGrams else 0f
        val perGramT     = if (defaultGrams > 0) snack.t     / defaultGrams else 0f
        val perGramFiber = if (defaultGrams > 0) snack.fiber / defaultGrams else 0f

        tvName.setText(snack.name)

        // Cílové a zkonzumované hodnoty pro percent helper
        var targetP = 0.0; var targetS = 0.0; var targetT = 0.0; var targetFiber = 0.0
        var alreadyP = 0.0; var alreadyS = 0.0; var alreadyT = 0.0; var alreadyFiber = 0.0

        fun currentGrams() = etWeight.text.toString().toFloatOrNull() ?: defaultGrams

        fun recalcAndDisplay() {
            val g = currentGrams()
            val p     = perGramP     * g
            val s     = perGramS     * g
            val t     = perGramT     * g
            val fiber = perGramFiber * g
            val kcal  = (p * 4) + (s * 4) + (t * 9)

            tvP.setText("%.1f".format(p).replace(",", "."))
            tvS.setText("%.1f".format(s).replace(",", "."))
            tvT.setText("%.1f".format(t).replace(",", "."))
            tvFiber.setText("%.1f".format(fiber).replace(",", "."))
            tvCalories.setText("%.0f".format(kcal))

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

            applyPercent(tvPercentP,     alreadyP,     p.toDouble(),     targetP)
            applyPercent(tvPercentS,     alreadyS,     s.toDouble(),     targetS)
            applyPercent(tvPercentT,     alreadyT,     t.toDouble(),     targetT)
            applyPercent(tvPercentFiber, alreadyFiber, fiber.toDouble(), targetFiber)
        }

        // Načteme cíle a zkonzumované, pak zobrazíme výchozí hodnoty
        lifecycleScope.launch {
            val today    = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val target   = withContext(Dispatchers.IO) { MacroCalculator.calculate(requireContext()) }
            val consumed = withContext(Dispatchers.IO) { db.consumedSnackDao().getConsumedByDate(today).first() }

            targetP = target.protein; targetS = target.carbs
            targetT = target.fat;     targetFiber = target.fiber

            alreadyP     = consumed.sumOf { it.p.toDouble() }
            alreadyS     = consumed.sumOf { it.s.toDouble() }
            alreadyT     = consumed.sumOf { it.t.toDouble() }
            alreadyFiber = consumed.sumOf { it.fiber.toDouble() }

            etWeight.setText(defaultGrams.toInt().toString())
            recalcAndDisplay()
        }

        etWeight.addTextChangedListener { recalcAndDisplay() }

        btnConfirm.setOnClickListener {
            val g     = currentGrams()
            val p     = perGramP     * g
            val s     = perGramS     * g
            val t     = perGramT     * g
            val fiber = perGramFiber * g
            val kcal  = ((p * 4) + (s * 4) + (t * 9)).toInt()
            val kj    = (p * 17f) + (s * 17f) + (t * 38f)

            val today   = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val nowTime = SimpleDateFormat("HH:mm",      Locale.getDefault()).format(Date())

            lifecycleScope.launch(Dispatchers.IO) {
                db.consumedSnackDao().insertConsumed(
                    ConsumedSnackEntity(
                        date = today, time = nowTime, name = snack.name,
                        p = p, s = s, t = t,
                        calories = kcal, energyKj = kj, fiber = fiber
                    )
                )
                withContext(Dispatchers.Main) {
                    view?.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    Toast.makeText(requireContext(), "${snack.name} přidáno! 🔥", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
            }
        }

        dialog.show()
    }

    private fun showAddDialog(aiResult: FoodAIResult? = null) {
        val dialog = BottomSheetDialog(requireContext())
        val v      = layoutInflater.inflate(R.layout.dialog_add_snack, null)
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

        // ── Per-gram základy — sdílené closure pro AI i barcode ───────
        var perGramP     = 0f
        var perGramS     = 0f
        var perGramT     = 0f
        var perGramFiber = 0f

        // Přepočítá makra podle aktuální váhy — volá se z weight listeneru
        fun recalcMacrosForWeight() {
            val grams = etWeight.text.toString().toFloatOrNull() ?: return
            if (perGramP == 0f && perGramS == 0f && perGramT == 0f) return
            etP.setText("%.1f".format(perGramP * grams).replace(",", "."))
            etS.setText("%.1f".format(perGramS * grams).replace(",", "."))
            etT.setText("%.1f".format(perGramT * grams).replace(",", "."))
            etFiber.setText("%.1f".format(perGramFiber * grams).replace(",", "."))
        }

        // ── Cílové a již zkonzumované hodnoty pro percent helper ─────
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

        // ── Listenery na makra (procenta) + váha (přepočet) ──────────
        etP.addTextChangedListener     { updatePercents() }
        etS.addTextChangedListener     { updatePercents() }
        etT.addTextChangedListener     { updatePercents() }
        etFiber.addTextChangedListener { updatePercents() }
        etWeight.addTextChangedListener { recalcMacrosForWeight() }

        // ── Načtení cílů a již zkonzumovaného ─────────────────────────
        lifecycleScope.launch {
            val today    = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
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

        // ── Předvyplnění z AI výsledku ────────────────────────────────
        // Spočítáme per-gram hodnoty z váhy kterou AI vrátila,
        // aby weight listener mohl okamžitě přepočítat.
        aiResult?.let {
            val grams = it.weight.filter { c -> c.isDigit() }.toFloatOrNull()?.takeIf { g -> g > 0 } ?: 100f
            perGramP     = it.p     / grams
            perGramS     = it.s     / grams
            perGramT     = it.t     / grams
            perGramFiber = it.fiber / grams

            etName.setText(it.name)
            etWeight.setText(grams.toInt().toString())
            etP.setText(it.p.toString())
            etS.setText(it.s.toString())
            etT.setText(it.t.toString())
            etFiber.setText(it.fiber.toString())
        }

        // ── Uložení snacku ────────────────────────────────────────────
        btnSave.setOnClickListener {
            val name  = etName.text.toString()
            val p     = etP.text.toString().replace(",", ".").toFloatOrNull() ?: 0f
            val s     = etS.text.toString().replace(",", ".").toFloatOrNull() ?: 0f
            val t     = etT.text.toString().replace(",", ".").toFloatOrNull() ?: 0f
            val fiber = etFiber.text.toString().replace(",", ".").toFloatOrNull() ?: 0f
            val energy = (p * 17f) + (s * 17f) + (t * 38f)

            if (name.isNotEmpty()) {
                lifecycleScope.launch(Dispatchers.IO) {
                    db.snackDao().insertSnack(
                        SnackEntity(
                            name = name, weight = "${etWeight.text}g",
                            p = p, s = s, t = t,
                            isPre = isPreSelected, energyKj = energy, fiber = fiber
                        )
                    )
                    withContext(Dispatchers.Main) { dialog.dismiss() }
                }
            } else {
                etName.error = "Zadej název"
            }
        }

        // ── Barcode scanner — inline closure sdílí perGram proměnné ──
        tilName.setEndIconOnClickListener {
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
                                        // Uložíme per-gram hodnoty — weight listener pak přepočítá
                                        perGramP     = (n.optDouble("proteins_100g",      0.0) / 100.0).toFloat()
                                        perGramS     = (n.optDouble("carbohydrates_100g", 0.0) / 100.0).toFloat()
                                        perGramT     = (n.optDouble("fat_100g",           0.0) / 100.0).toFloat()
                                        perGramFiber = (n.optDouble("fiber_100g",         0.0) / 100.0).toFloat()

                                        etName.setText(name)
                                        etWeight.setText("100")
                                        // Nastavíme makra pro 100g — změna váhy pak přepočítá automaticky
                                        etP.setText("%.1f".format(perGramP * 100).replace(",", "."))
                                        etS.setText("%.1f".format(perGramS * 100).replace(",", "."))
                                        etT.setText("%.1f".format(perGramT * 100).replace(",", "."))
                                        etFiber.setText("%.1f".format(perGramFiber * 100).replace(",", "."))
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }

        dialog.show()
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
                } else if (event.action == MotionEvent.ACTION_UP) {
                    v.performClick()
                }
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
