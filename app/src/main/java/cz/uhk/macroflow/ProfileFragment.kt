package cz.uhk.macroflow

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
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
        etAge    = view.findViewById(R.id.etAge)
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

        loadUserData()

        tvDesc.visibility = View.INVISIBLE

        // Lifestyle výběr
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

        view.findViewById<MaterialButton>(R.id.btnSave).setOnClickListener {
            saveAllData()
        }

        view.findViewById<MaterialCardView>(R.id.cardOptionalMetrics).setOnClickListener {
            showMetricsBottomSheet()
        }

        // --- Uživatelský účet ---
        setupAccountSection(view)

        return view
    }

    // ----------------------------------------------------------------
    // ÚČET — zobrazení emailu + odhlášení
    // ----------------------------------------------------------------
    private fun setupAccountSection(view: View) {
        // Views jsou uvnitř cardAccountInfo, musíme je najít správně
        val tvEmail    = view.findViewById<TextView>(R.id.tvUserEmail)
        val btnSignOut = view.findViewById<MaterialButton>(R.id.btnSignOut)

        val user = FirebaseRepository.currentUser
        if (user != null) {
            tvEmail?.text              = user.email ?: user.displayName ?: "Přihlášený uživatel"
            btnSignOut?.text           = "Odhlásit se"
            btnSignOut?.backgroundTintList = android.content.res.ColorStateList.valueOf(
                android.graphics.Color.parseColor("#BC6C25")
            )
        } else {
            tvEmail?.text    = "Offline režim — data se neukládají do cloudu"
            btnSignOut?.text = "Přihlásit se"
            btnSignOut?.backgroundTintList = android.content.res.ColorStateList.valueOf(
                android.graphics.Color.parseColor("#283618")
            )
        }

        btnSignOut?.setOnClickListener {
            if (FirebaseRepository.isLoggedIn) {
                FirebaseRepository.signOut()
            }
            startActivity(Intent(requireContext(), LoginActivity::class.java))
            requireActivity().finish()
        }
    }

    // ----------------------------------------------------------------
    // Načtení dat (DB → fallback SharedPrefs)
    // ----------------------------------------------------------------
    private fun loadUserData() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(requireContext())
            val profile = withContext(Dispatchers.IO) {
                db.userProfileDao().getProfileSync()
            }

            if (profile != null) {
                etWeight.setText(profile.weight.toString())
                etHeight.setText(profile.height.toString())
                etAge.setText(profile.age.toString())
                toggleGender.check(
                    if (profile.gender == "male") R.id.btnMale else R.id.btnFemale
                )
                selectedMultiplier = profile.activityMultiplier
            } else {
                val prefs = requireContext()
                    .getSharedPreferences("UserPrefs", android.content.Context.MODE_PRIVATE)
                etWeight.setText(prefs.getString("weightAkt", "83"))
                etHeight.setText(prefs.getString("height", "175"))
                etAge.setText(prefs.getString("age", "22"))
                val gender = prefs.getString("gender", "male")
                toggleGender.check(if (gender == "male") R.id.btnMale else R.id.btnFemale)
                selectedMultiplier = prefs.getFloat("multiplier", 1.2f)
            }

            val colorDark = android.graphics.Color.parseColor("#283618")
            listOf(etWeight, etHeight, etAge).forEach { it.setTextColor(colorDark) }
            updateCircleVisuals(selectedMultiplier)
        }
    }

    // ----------------------------------------------------------------
    // Uložení profilu — Room + SharedPrefs + Firebase
    // ----------------------------------------------------------------
    private fun saveAllData() {
        val weight = etWeight.text.toString().toDoubleOrNull()
        val height = etHeight.text.toString().toDoubleOrNull()
        val age    = etAge.text.toString().toIntOrNull()

        if (weight == null || height == null || age == null) {
            Toast.makeText(context, "Doplň prosím všechny údaje", Toast.LENGTH_SHORT).show()
            return
        }

        val gender = if (toggleGender.checkedButtonId == R.id.btnMale) "male" else "female"

        val profileEntity = UserProfileEntity(
            id = 1,
            weight = weight,
            height = height,
            age = age,
            gender = gender,
            activityMultiplier = selectedMultiplier
        )

        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(requireContext())

            // 1. Ulož lokálně do Room
            db.userProfileDao().saveProfile(profileEntity)

            // 2. Synchronizuj SharedPrefs (weightAkt pro MacroCalculator)
            requireContext()
                .getSharedPreferences("UserPrefs", android.content.Context.MODE_PRIVATE)
                .edit().putString("weightAkt", weight.toString()).apply()

            // 3. Nahraj do Firebase (jen pokud je přihlášen)
            if (FirebaseRepository.isLoggedIn) {
                try {
                    FirebaseRepository.uploadProfile(profileEntity)
                } catch (e: Exception) {
                    // Tiché selhání — data jsou uložena lokálně, sync proběhne příště
                }
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    if (FirebaseRepository.isLoggedIn)
                        "Profil uložen a synchronizován ☁️"
                    else
                        "Profil uložen lokálně 💪",
                    Toast.LENGTH_SHORT
                ).show()
                if (isExpanded) shrinkCircle()
            }
        }
    }

    // ----------------------------------------------------------------
    // Bottom sheet — tělesné míry (+ Firebase upload)
    // ----------------------------------------------------------------
    private fun showMetricsBottomSheet() {
        val dialog    = BottomSheetDialog(requireContext())
        val sheetView = layoutInflater.inflate(R.layout.layout_metrics_sheet, null)
        dialog.setContentView(sheetView)

        val sdf      = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val calendar = Calendar.getInstance()
        val today    = Calendar.getInstance()

        val tvDate  = sheetView.findViewById<TextView>(R.id.tvSelectedDate)
        val btnPrev = sheetView.findViewById<ImageButton>(R.id.btnPrevDay)
        val btnNext = sheetView.findViewById<ImageButton>(R.id.btnNextDay)

        fun refreshSheet() {
            val dateKey = sdf.format(calendar.time)
            val isToday = dateKey == sdf.format(today.time)
            tvDate.text = if (isToday) "Dnes" else dateKey

            btnNext.isEnabled = !isToday && calendar.before(today)
            btnNext.alpha = if (btnNext.isEnabled) 1.0f else 0.3f

            lifecycleScope.launch {
                val db = AppDatabase.getDatabase(requireContext())
                val metrics = withContext(Dispatchers.IO) {
                    db.bodyMetricsDao().getByDateSync(dateKey)
                }
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

        btnPrev.setOnClickListener { calendar.add(Calendar.DAY_OF_YEAR, -1); refreshSheet() }
        btnNext.setOnClickListener {
            if (calendar.before(today)) { calendar.add(Calendar.DAY_OF_YEAR, 1); refreshSheet() }
        }

        sheetView.findViewById<Button>(R.id.btnConfirmMetrics).setOnClickListener {
            val dateKey = sdf.format(calendar.time)

            val metricsEntity = BodyMetricsEntity(
                date     = dateKey,
                neck     = sheetView.findViewById<EditText>(R.id.etNeck).text.toString().toFloatOrNull() ?: 0f,
                chest    = sheetView.findViewById<EditText>(R.id.etChest).text.toString().toFloatOrNull() ?: 0f,
                bicep    = sheetView.findViewById<EditText>(R.id.etBicep).text.toString().toFloatOrNull() ?: 0f,
                forearm  = sheetView.findViewById<EditText>(R.id.etForearm).text.toString().toFloatOrNull() ?: 0f,
                waist    = sheetView.findViewById<EditText>(R.id.etWaist).text.toString().toFloatOrNull() ?: 0f,
                abdomen  = sheetView.findViewById<EditText>(R.id.etAbdomen).text.toString().toFloatOrNull() ?: 0f,
                thigh    = sheetView.findViewById<EditText>(R.id.etThigh).text.toString().toFloatOrNull() ?: 0f,
                calf     = sheetView.findViewById<EditText>(R.id.etCalf).text.toString().toFloatOrNull() ?: 0f
            )

            lifecycleScope.launch(Dispatchers.IO) {
                val db = AppDatabase.getDatabase(requireContext())

                // 1. Ulož lokálně
                db.bodyMetricsDao().save(metricsEntity)

                // 2. Nahraj do Firebase
                if (FirebaseRepository.isLoggedIn) {
                    try {
                        FirebaseRepository.uploadBodyMetrics(metricsEntity)
                    } catch (e: Exception) {
                        // Tiché selhání
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        if (FirebaseRepository.isLoggedIn)
                            "Uloženo a synchronizováno ☁️"
                        else
                            "Uloženo lokálně",
                        Toast.LENGTH_SHORT
                    ).show()
                    dialog.dismiss()
                }
            }
        }

        dialog.show()
    }

    // ----------------------------------------------------------------
    // UI helpers — lifestyle kruh
    // ----------------------------------------------------------------
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
        listOf(1.2f, 1.4f, 1.6f).forEachIndexed { index, value ->
            val selected = value == m
            partsCircle[index].animate()
                .alpha(if (selected) 1.0f else 0.2f).setDuration(300).start()
            iconsCenter[index].animate()
                .alpha(if (selected) 1.0f else 0.0f)
                .scaleX(if (selected) 1.1f else 0.8f)
                .scaleY(if (selected) 1.1f else 0.8f)
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
}