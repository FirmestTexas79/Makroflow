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
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class ProfileFragment : Fragment() {

    private var selectedMultiplier: Float = 1.2f
    private var isExpanded = false

    private lateinit var circleContainer: FrameLayout
    private lateinit var tvDesc: TextView
    private lateinit var etWeight: EditText
    private lateinit var etHeight: EditText
    private lateinit var etAge: EditText
    private lateinit var toggleGender: MaterialButtonToggleGroup
    private lateinit var iconsCenter: List<ImageView>
    private lateinit var partsCircle: List<ImageView>

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.activity_setup, container, false)
        view.setPadding(0, 44, 0, 0)

        etWeight = view.findViewById(R.id.etWeight)
        etHeight = view.findViewById(R.id.etHeight)
        etAge = view.findViewById(R.id.etAge)
        toggleGender = view.findViewById(R.id.toggleGender)
        tvDesc = view.findViewById(R.id.tvLifestyleDesc)
        circleContainer = view.findViewById(R.id.circleLifestyle)

        iconsCenter = listOf(
            view.findViewById(R.id.iconCenterLow),
            view.findViewById(R.id.iconCenterMed),
            view.findViewById(R.id.iconCenterHigh)
        )
        partsCircle = listOf(
            view.findViewById(R.id.partLezerni),
            view.findViewById(R.id.partAktivni),
            view.findViewById(R.id.partSportovec)
        )

        // Načteme profil z DB (s fallbackem na SharedPrefs pro plynulou migraci)
        loadUserData()

        tvDesc.visibility = View.INVISIBLE

        view.findViewById<View>(R.id.clickLezerni).setOnClickListener {
            selectMode(1.2f, "Ležérní - Minimum pohybu")
        }
        view.findViewById<View>(R.id.clickAktivni).setOnClickListener {
            selectMode(1.4f, "Aktivní - Práce v pohybu")
        }
        view.findViewById<View>(R.id.clickSportovec).setOnClickListener {
            selectMode(1.6f, "Sportovec - Těžké tréninky")
        }

        view.findViewById<View>(R.id.setupMainLayout).setOnClickListener {
            if (isExpanded) shrinkCircle()
        }
        view.findViewById<Button>(R.id.btnSave).setOnClickListener { saveAllData() }
        view.findViewById<MaterialCardView>(R.id.cardOptionalMetrics).setOnClickListener {
            showMetricsBottomSheet()
        }

        return view
    }

    private fun loadUserData() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(requireContext())

            // Zkusíme načíst z DB
            val profile = withContext(Dispatchers.IO) {
                db.userProfileDao().getProfileSync()
            }

            if (profile != null) {
                // Data jsou v DB – použijeme je
                etWeight.setText(profile.weight.toString())
                etHeight.setText(profile.height.toString())
                etAge.setText(profile.age.toString())
                toggleGender.check(
                    if (profile.gender == "male") R.id.btnMale else R.id.btnFemale
                )
                selectedMultiplier = profile.activityMultiplier
            } else {
                // Fallback: načteme ze starých SharedPrefs (pro první spuštění po migraci)
                val legacyPrefs = requireContext()
                    .getSharedPreferences("UserPrefs", android.content.Context.MODE_PRIVATE)
                etWeight.setText(legacyPrefs.getString("weightAkt", "83"))
                etHeight.setText(legacyPrefs.getString("height", "175"))
                etAge.setText(legacyPrefs.getString("age", "22"))
                val gender = legacyPrefs.getString("gender", "male")
                toggleGender.check(if (gender == "male") R.id.btnMale else R.id.btnFemale)
                selectedMultiplier = legacyPrefs.getFloat("multiplier", 1.2f)
            }

            val colorDark = android.graphics.Color.parseColor("#283618")
            listOf(etWeight, etHeight, etAge).forEach { it.setTextColor(colorDark) }
            updateCircleVisuals(selectedMultiplier)
        }
    }

    private fun selectMode(multiplier: Float, description: String) {
        selectedMultiplier = multiplier
        updateCircleVisuals(multiplier)

        if (!isExpanded) {
            isExpanded = true
            circleContainer.animate()
                .scaleX(1.4f).scaleY(1.4f).translationY(-60f).setDuration(450).start()
            tvDesc.text = description
            tvDesc.visibility = View.VISIBLE
            tvDesc.alpha = 0f
            tvDesc.animate().alpha(1f).translationY(-10f).setDuration(450).start()
        } else {
            tvDesc.text = description
        }
    }

    private fun updateCircleVisuals(m: Float) {
        val targets = listOf(1.2f, 1.4f, 1.6f)
        targets.forEachIndexed { index, value ->
            val isSelected = value == m
            partsCircle[index].animate()
                .alpha(if (isSelected) 1.0f else 0.2f).setDuration(300).start()
            iconsCenter[index].animate()
                .alpha(if (isSelected) 1.0f else 0.0f)
                .scaleX(if (isSelected) 1.1f else 0.8f)
                .scaleY(if (isSelected) 1.1f else 0.8f)
                .setDuration(300).start()
        }
    }

    private fun shrinkCircle() {
        isExpanded = false
        circleContainer.animate()
            .scaleX(1.0f).scaleY(1.0f).translationY(0f).setDuration(300).start()
        tvDesc.animate().alpha(0f).translationY(0f).setDuration(300)
            .withEndAction { tvDesc.visibility = View.INVISIBLE }.start()
    }

    private fun saveAllData() {
        val weight = etWeight.text.toString().toDoubleOrNull()
        val height = etHeight.text.toString().toDoubleOrNull()
        val age = etAge.text.toString().toIntOrNull()

        if (weight == null || height == null || age == null) {
            Toast.makeText(context, "Doplň prosím všechny údaje", Toast.LENGTH_SHORT).show()
            return
        }

        val gender = if (toggleGender.checkedButtonId == R.id.btnMale) "male" else "female"

        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(requireContext())
            db.userProfileDao().saveProfile(
                UserProfileEntity(
                    id = 1,
                    weight = weight,
                    height = height,
                    age = age,
                    gender = gender,
                    activityMultiplier = selectedMultiplier
                )
            )

            // Zároveň aktualizujeme weightAkt v SharedPrefs, aby DashboardFragment
            // a MacroCalculator (runBlocking fallback) měly vždy konzistentní data
            // dokud se celá app plně nepřepíše na DB
            requireContext()
                .getSharedPreferences("UserPrefs", android.content.Context.MODE_PRIVATE)
                .edit().putString("weightAkt", weight.toString()).apply()

            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Profil sportovce aktualizován! 💪", Toast.LENGTH_SHORT).show()
                if (isExpanded) shrinkCircle()
            }
        }
    }

    private fun showMetricsBottomSheet() {
        val dialog = BottomSheetDialog(requireContext())
        val sheetView = layoutInflater.inflate(R.layout.layout_metrics_sheet, null)
        dialog.setContentView(sheetView)

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val calendar = Calendar.getInstance()
        val today = Calendar.getInstance()

        val tvDate = sheetView.findViewById<TextView>(R.id.tvSelectedDate)
        val btnPrev = sheetView.findViewById<ImageButton>(R.id.btnPrevDay)
        val btnNext = sheetView.findViewById<ImageButton>(R.id.btnNextDay)

        // Mapování view ID → název sloupce v DB
        val metricMap = mapOf(
            R.id.etNeck to "neck",
            R.id.etChest to "chest",
            R.id.etBicep to "bicep",
            R.id.etForearm to "forearm",
            R.id.etWaist to "waist",
            R.id.etAbdomen to "abdomen",
            R.id.etThigh to "thigh",
            R.id.etCalf to "calf"
        )

        fun refreshSheet() {
            val dateKey = sdf.format(calendar.time)
            val isToday = dateKey == sdf.format(today.time)
            tvDate.text = if (isToday) "Dnes" else dateKey

            btnNext.isEnabled = !isToday && calendar.before(today)
            btnNext.alpha = if (btnNext.isEnabled) 1.0f else 0.3f

            // Načteme uložené míry z DB pro vybraný den
            lifecycleScope.launch {
                val db = AppDatabase.getDatabase(requireContext())
                val metrics = withContext(Dispatchers.IO) {
                    db.bodyMetricsDao().getByDateSync(dateKey)
                }

                // Vyplníme pole hodnotami z DB (nebo prázdné, pokud záznam neexistuje)
                sheetView.findViewById<EditText>(R.id.etNeck)
                    .setText(if (metrics?.neck != null && metrics.neck > 0) metrics.neck.toString() else "")
                sheetView.findViewById<EditText>(R.id.etChest)
                    .setText(if (metrics?.chest != null && metrics.chest > 0) metrics.chest.toString() else "")
                sheetView.findViewById<EditText>(R.id.etBicep)
                    .setText(if (metrics?.bicep != null && metrics.bicep > 0) metrics.bicep.toString() else "")
                sheetView.findViewById<EditText>(R.id.etForearm)
                    .setText(if (metrics?.forearm != null && metrics.forearm > 0) metrics.forearm.toString() else "")
                sheetView.findViewById<EditText>(R.id.etWaist)
                    .setText(if (metrics?.waist != null && metrics.waist > 0) metrics.waist.toString() else "")
                sheetView.findViewById<EditText>(R.id.etAbdomen)
                    .setText(if (metrics?.abdomen != null && metrics.abdomen > 0) metrics.abdomen.toString() else "")
                sheetView.findViewById<EditText>(R.id.etThigh)
                    .setText(if (metrics?.thigh != null && metrics.thigh > 0) metrics.thigh.toString() else "")
                sheetView.findViewById<EditText>(R.id.etCalf)
                    .setText(if (metrics?.calf != null && metrics.calf > 0) metrics.calf.toString() else "")
            }
        }

        refreshSheet()

        btnPrev.setOnClickListener {
            calendar.add(Calendar.DAY_OF_YEAR, -1)
            refreshSheet()
        }
        btnNext.setOnClickListener {
            if (calendar.before(today)) {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
                refreshSheet()
            }
        }

        sheetView.findViewById<Button>(R.id.btnConfirmMetrics).setOnClickListener {
            val dateKey = sdf.format(calendar.time)

            lifecycleScope.launch(Dispatchers.IO) {
                val db = AppDatabase.getDatabase(requireContext())
                db.bodyMetricsDao().save(
                    BodyMetricsEntity(
                        date = dateKey,
                        neck = sheetView.findViewById<EditText>(R.id.etNeck)
                            .text.toString().toFloatOrNull() ?: 0f,
                        chest = sheetView.findViewById<EditText>(R.id.etChest)
                            .text.toString().toFloatOrNull() ?: 0f,
                        bicep = sheetView.findViewById<EditText>(R.id.etBicep)
                            .text.toString().toFloatOrNull() ?: 0f,
                        forearm = sheetView.findViewById<EditText>(R.id.etForearm)
                            .text.toString().toFloatOrNull() ?: 0f,
                        waist = sheetView.findViewById<EditText>(R.id.etWaist)
                            .text.toString().toFloatOrNull() ?: 0f,
                        abdomen = sheetView.findViewById<EditText>(R.id.etAbdomen)
                            .text.toString().toFloatOrNull() ?: 0f,
                        thigh = sheetView.findViewById<EditText>(R.id.etThigh)
                            .text.toString().toFloatOrNull() ?: 0f,
                        calf = sheetView.findViewById<EditText>(R.id.etCalf)
                            .text.toString().toFloatOrNull() ?: 0f
                    )
                )

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Uloženo pro $dateKey", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
            }
        }

        dialog.show()
    }
}