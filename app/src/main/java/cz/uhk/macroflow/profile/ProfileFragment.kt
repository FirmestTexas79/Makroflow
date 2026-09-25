package cz.uhk.macroflow.profile

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.slider.Slider
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.R
import cz.uhk.macroflow.common.LoginActivity
import cz.uhk.macroflow.data.BodyMetricsEntity
import cz.uhk.macroflow.data.FirebaseRepository
import cz.uhk.macroflow.data.UserProfileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ProfileFragment : Fragment() {

    private var selectedMultiplier: Float = 1.2f
    private var selectedGoal: String = "MAINTAIN" // ✅ Logika stavu cíle

    private lateinit var etWeight: EditText
    private lateinit var etHeight: EditText
    private lateinit var etAge: EditText
    private lateinit var toggleGender: MaterialButtonToggleGroup
    /** Řádky životního stylu (sedavá / v pohybu / náročná) a jejich zaškrtnutí. */
    private lateinit var lifestyleRows: List<View>
    private lateinit var lifestyleChecks: List<ImageView>
    private val lifestyleValues = listOf(1.2f, 1.4f, 1.6f)

    // 👣 Reference na UI prvky slideru
    private lateinit var sliderStepGoal: Slider
    private lateinit var tvStepGoalValue: TextView

    // 🎯 Tlačítka pro cíle
    private lateinit var btnCut: MaterialButton
    private lateinit var btnMaintain: MaterialButton
    private lateinit var btnBulk: MaterialButton

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.activity_setup, container, false)

        // Inicializace polí
        etWeight = view.findViewById(R.id.etWeight)
        etHeight = view.findViewById(R.id.etHeight)
        etAge    = view.findViewById(R.id.etAge)
        toggleGender = view.findViewById(R.id.toggleGender)

        // 🎯 Cíle (Tlačítka)
        btnCut = view.findViewById(R.id.btnCut)
        btnMaintain = view.findViewById(R.id.btnMaintain)
        btnBulk = view.findViewById(R.id.btnBulk)

        btnCut.setOnClickListener { selectGoal("CUT") }
        btnMaintain.setOnClickListener { selectGoal("MAINTAIN") }
        btnBulk.setOnClickListener { selectGoal("BULK") }

        // 👣 Slider kroků
        sliderStepGoal = view.findViewById(R.id.sliderStepGoal)
        tvStepGoalValue = view.findViewById(R.id.tvStepGoalValue)

        sliderStepGoal.addOnChangeListener { _, value, _ ->
            tvStepGoalValue.text = "${value.toInt()} kroků"
        }

        // Životní styl: tři srozumitelné volby (dřív kruh s ikonami bez popisu)
        lifestyleRows = listOf(R.id.clickLezerni, R.id.clickAktivni, R.id.clickSportovec).map { view.findViewById(it) }
        lifestyleChecks = listOf(R.id.checkLezerni, R.id.checkAktivni, R.id.checkSportovec).map { view.findViewById(it) }
        lifestyleRows.forEachIndexed { i, row -> row.setOnClickListener { selectMode(lifestyleValues[i]) } }

        loadUserData()

        view.findViewById<MaterialButton>(R.id.btnSave).setOnClickListener { saveAllData() }
        view.findViewById<MaterialCardView>(R.id.cardOptionalMetrics).setOnClickListener { showMetricsBottomSheet() }

        setupAccountSection(view)

        return view
    }

    // --- LOGIKA PRO CÍLE ---
    private fun selectGoal(goal: String) {
        selectedGoal = goal
        updateGoalVisuals()
    }

    private fun updateGoalVisuals() {
        val activeColor = Color.parseColor("#606C38")
        val inactiveColor = Color.parseColor("#FEFAE0")
        val activeText = Color.WHITE
        val inactiveText = Color.parseColor("#283618")

        val buttons = mapOf("CUT" to btnCut, "MAINTAIN" to btnMaintain, "BULK" to btnBulk)
        buttons.forEach { (key, btn) ->
            if (key == selectedGoal) {
                btn.backgroundTintList = ColorStateList.valueOf(activeColor)
                btn.setTextColor(activeText)
            } else {
                btn.backgroundTintList = ColorStateList.valueOf(inactiveColor)
                btn.setTextColor(inactiveText)
            }
        }
    }

    private fun setupAccountSection(view: View) {
        val tvEmail    = view.findViewById<TextView>(R.id.tvUserEmail)
        val btnSignOut = view.findViewById<MaterialButton>(R.id.btnSignOut)
        val user = FirebaseRepository.currentUser

        val tvSync = view.findViewById<TextView>(R.id.tvSyncStatus)
        if (user != null) {
            tvEmail?.text = user.email ?: user.displayName ?: "Přihlášený uživatel"
            tvSync?.text = "☁ Data se zálohují do cloudu"
            btnSignOut?.text = "Odhlásit se"
        } else {
            tvEmail?.text = "Offline režim"
            tvSync?.text = "Data jsou jen v telefonu – přihlas se pro zálohu"
            btnSignOut?.text = "Přihlásit se"
        }

        btnSignOut?.setOnClickListener {
            if (FirebaseRepository.isLoggedIn) FirebaseRepository.signOut()
            startActivity(Intent(requireContext(), LoginActivity::class.java))
            requireActivity().finish()
        }

        // Tlačítko smazání účtu — požadavek Google Play pro publikaci
        val btnDeleteAccount = view.findViewById<MaterialButton>(R.id.btnDeleteAccount)
        btnDeleteAccount?.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Smazat účet")
                .setMessage("Tato akce je nevratná. Všechna tvá data (profil, check-iny, Makromoni) budou trvale smazána.")
                .setPositiveButton("Smazat účet") { _, _ -> deleteAccount() }
                .setNegativeButton("Zrušit", null)
                .show()
        }
    }

    // --- NAČÍTÁNÍ (Profil určuje pravdu) ---
    private fun loadUserData() {
        lifecycleScope.launch {
            val appContext = requireContext().applicationContext
            val db = AppDatabase.getDatabase(appContext)

            // Bereme čistě data z profilu, ranní rituál nás při načítání profilu nezajímá
            val profile = withContext(Dispatchers.IO) { db.userProfileDao().getProfileSync() }

            if (profile != null) {
                etWeight.setText(profile.weight.toString())
                etHeight.setText(profile.height.toString())
                etAge.setText(profile.age.toString())
                toggleGender.check(if (profile.gender == "male") R.id.btnMale else R.id.btnFemale)
                selectedMultiplier = profile.activityMultiplier
                sliderStepGoal.value = profile.stepGoal.toFloat()
                tvStepGoalValue.text = "${profile.stepGoal} kroků"
                selectedGoal = profile.goal
                updateGoalVisuals()
            } else {
                // Totální fallback pro nové uživatele
                etWeight.setText("83.0")
                etHeight.setText("175")
                etAge.setText("22")
                selectedGoal = "MAINTAIN"
                updateGoalVisuals()
            }

            updateLifestyleVisuals(selectedMultiplier)
            refreshHero()
        }
    }

    /**
     * Tmavá souhrnná karta nahoře: jméno, základní údaje, cíl a dnešní cíl příjmu
     * (stejný výpočet jako Dashboard – MacroCalculator).
     */
    private fun refreshHero() {
        val v = view ?: return
        val appContext = requireContext().applicationContext
        val user = FirebaseRepository.currentUser
        val name = user?.displayName?.takeIf { it.isNotBlank() }
            ?: user?.email?.substringBefore('@')?.replaceFirstChar { it.uppercase() }
            ?: "Sportovec"
        v.findViewById<TextView>(R.id.tvHeroName).text = name
        v.findViewById<TextView>(R.id.tvHeroInitial).text = name.first().uppercase()
        fun clean(t: CharSequence?) = t.toString().removeSuffix(".0")      // 70.0 → 70
        val w = clean(etWeight.text); val h = clean(etHeight.text); val a = clean(etAge.text)
        v.findViewById<TextView>(R.id.tvHeroSub).text = "$w kg · $h cm · $a let"
        v.findViewById<TextView>(R.id.tvHeroGoal).text = selectedGoal
        lifecycleScope.launch {
            val t = withContext(Dispatchers.IO) {
                runCatching { cz.uhk.macroflow.dashboard.MacroCalculator.calculate(appContext) }.getOrNull()
            } ?: return@launch
            val root = view ?: return@launch
            root.findViewById<TextView>(R.id.tvHeroKcal).text = "%,d kcal".format(Locale("cs", "CZ"), t.calories.toInt())
            root.findViewById<TextView>(R.id.tvHeroProtein).text = "B ${t.protein.toInt()} g"
            root.findViewById<TextView>(R.id.tvHeroCarbs).text = "S ${t.carbs.toInt()} g"
            root.findViewById<TextView>(R.id.tvHeroFat).text = "T ${t.fat.toInt()} g"
        }
    }

    // --- UKLÁDÁNÍ (Profil přepisuje rituál, pokud existuje) ---
    private fun saveAllData() {
        val btnSave = view?.findViewById<MaterialButton>(R.id.btnSave)
        val weight = etWeight.text.toString().toDoubleOrNull()
        val height = etHeight.text.toString().toDoubleOrNull()
        val age    = etAge.text.toString().toIntOrNull()
        val stepGoal = sliderStepGoal.value.toInt()

        if (weight == null || height == null || age == null) {
            Toast.makeText(context, "Doplň prosím všechny údaje", Toast.LENGTH_SHORT).show()
            return
        }

        btnSave?.isEnabled = false // Prevence proti "zběsilému klikání"
        val gender = if (toggleGender.checkedButtonId == R.id.btnMale) "male" else "female"
        val appContext = requireContext().applicationContext

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val db = AppDatabase.getDatabase(appContext)
                    val currentProfile = db.userProfileDao().getProfileSync() ?: UserProfileEntity(id = 1)

                    val profileToSave = currentProfile.copy(
                        weight = weight,
                        height = height,
                        age = age,
                        gender = gender,
                        activityMultiplier = selectedMultiplier,
                        stepGoal = stepGoal,
                        goal = selectedGoal
                    )

                    // 1. Uložíme do Profilu (Hlavní pravda)
                    db.userProfileDao().saveProfile(profileToSave)

                    // 2. Synchronizace váhy do dnešního check-inu (pokud už existuje)
                    val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                    val todayCheckIn = db.checkInDao().getCheckInByDateSync(today)
                    if (todayCheckIn != null) {
                        db.checkInDao().insertCheckIn(todayCheckIn.copy(weight = weight))
                    }

                    // 3. SharedPrefs pro rychlý přístup jinde
                    appContext.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
                        .edit().putString("weightAkt", weight.toString()).apply()

                    // 4. Firebase (v samostatném launch, aby neblokoval UI)
                    if (FirebaseRepository.isLoggedIn) {
                        try { FirebaseRepository.uploadProfile(profileToSave) } catch (e: Exception) { e.printStackTrace() }
                    }
                }

                Toast.makeText(appContext, if (FirebaseRepository.isLoggedIn) "Synchronizováno ☁️" else "Uloženo lokálně 💪", Toast.LENGTH_SHORT).show()
                refreshHero()

            } catch (e: Exception) {
                Toast.makeText(appContext, "Chyba: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                btnSave?.isEnabled = true
            }
        }
    }

    // --- METRIKY (Původní logika zůstává) ---
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

        fun refreshSheet() {
            val dateKey = sdf.format(calendar.time)
            tvDate.text = if (dateKey == sdf.format(today.time)) "Dnes" else dateKey
            btnNext.isEnabled = ! (dateKey == sdf.format(today.time))
            btnNext.alpha = if (btnNext.isEnabled) 1.0f else 0.3f

            lifecycleScope.launch {
                val db = AppDatabase.getDatabase(requireContext())
                val m = withContext(Dispatchers.IO) { db.bodyMetricsDao().getByDateSync(dateKey) }
                sheetView.findViewById<EditText>(R.id.etNeck).setText(m?.neck?.takeIf { it > 0 }?.toString() ?: "")
                sheetView.findViewById<EditText>(R.id.etChest).setText(m?.chest?.takeIf { it > 0 }?.toString() ?: "")
                sheetView.findViewById<EditText>(R.id.etBicep).setText(m?.bicep?.takeIf { it > 0 }?.toString() ?: "")
                sheetView.findViewById<EditText>(R.id.etForearm).setText(m?.forearm?.takeIf { it > 0 }?.toString() ?: "")
                sheetView.findViewById<EditText>(R.id.etWaist).setText(m?.waist?.takeIf { it > 0 }?.toString() ?: "")
                sheetView.findViewById<EditText>(R.id.etAbdomen).setText(m?.abdomen?.takeIf { it > 0 }?.toString() ?: "")
                sheetView.findViewById<EditText>(R.id.etThigh).setText(m?.thigh?.takeIf { it > 0 }?.toString() ?: "")
                sheetView.findViewById<EditText>(R.id.etCalf).setText(m?.calf?.takeIf { it > 0 }?.toString() ?: "")
            }
        }

        refreshSheet()
        btnPrev.setOnClickListener { calendar.add(Calendar.DAY_OF_YEAR, -1); refreshSheet() }

        btnNext.setOnClickListener {

            if (calendar.before(today)) { calendar.add(Calendar.DAY_OF_YEAR, 1); refreshSheet() }

        }
        sheetView.findViewById<Button>(R.id.btnConfirmMetrics).setOnClickListener {
            val metrics = BodyMetricsEntity(
                date = sdf.format(calendar.time),
                neck = sheetView.findViewById<EditText>(R.id.etNeck).text.toString().toFloatOrNull() ?: 0f,
                chest = sheetView.findViewById<EditText>(R.id.etChest).text.toString().toFloatOrNull() ?: 0f,
                bicep = sheetView.findViewById<EditText>(R.id.etBicep).text.toString().toFloatOrNull() ?: 0f,
                forearm = sheetView.findViewById<EditText>(R.id.etForearm).text.toString().toFloatOrNull() ?: 0f,
                waist = sheetView.findViewById<EditText>(R.id.etWaist).text.toString().toFloatOrNull() ?: 0f,
                abdomen = sheetView.findViewById<EditText>(R.id.etAbdomen).text.toString().toFloatOrNull() ?: 0f,
                thigh = sheetView.findViewById<EditText>(R.id.etThigh).text.toString().toFloatOrNull() ?: 0f,
                calf = sheetView.findViewById<EditText>(R.id.etCalf).text.toString().toFloatOrNull() ?: 0f
            )
            lifecycleScope.launch(Dispatchers.IO) {
                val db = AppDatabase.getDatabase(requireContext())
                db.bodyMetricsDao().save(metrics)
                if (FirebaseRepository.isLoggedIn) FirebaseRepository.uploadBodyMetrics(metrics)
                withContext(Dispatchers.Main) { Toast.makeText(context, "Uloženo!", Toast.LENGTH_SHORT).show(); dialog.dismiss() }
            }
        }
        dialog.show()
    }

    // --- ŽIVOTNÍ STYL ---
    private fun selectMode(multiplier: Float) {
        selectedMultiplier = multiplier
        updateLifestyleVisuals(multiplier)
    }

    private fun updateLifestyleVisuals(m: Float) {
        lifestyleValues.forEachIndexed { i, value ->
            val sel = value == m
            lifestyleRows[i].setBackgroundResource(if (sel) R.drawable.bg_option_selected else R.drawable.bg_option_idle)
            lifestyleChecks[i].visibility = if (sel) View.VISIBLE else View.INVISIBLE
        }
    }

    override fun onResume() {
        super.onResume()
        loadUserData()
    }

    private fun deleteAccount() {
        val appContext = requireContext().applicationContext
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 1. Smazání všech dat z Firestore
                if (FirebaseRepository.isLoggedIn) {
                    try { FirebaseRepository.deleteAllUserData() } catch (e: Exception) { e.printStackTrace() }
                }

                // 2. Smazání Firebase Auth účtu
                FirebaseRepository.currentUser?.delete()?.addOnCompleteListener { task ->
                    if (!task.isSuccessful) {
                        // Pokud selže (expired token) — přesměruj na re-autentizaci
                        lifecycleScope.launch(Dispatchers.Main) {
                            Toast.makeText(appContext,
                                "Pro smazání se prosím znovu přihlas.",
                                Toast.LENGTH_LONG).show()
                        }
                        return@addOnCompleteListener
                    }
                }

                // 3. Vyčištění lokální DB
                val db = AppDatabase.getDatabase(appContext)
                db.clearAllTables()

                // 4. Vyčištění SharedPreferences
                listOf("GamePrefs", "UserPrefs", "TrainingPrefs").forEach { name ->
                    appContext.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().apply()
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(appContext, "Účet byl smazán.", Toast.LENGTH_SHORT).show()
                    startActivity(
                        android.content.Intent(appContext, LoginActivity::class.java).apply {
                            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                                    android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                    )
                    requireActivity().finish()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(appContext, "Chyba: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}