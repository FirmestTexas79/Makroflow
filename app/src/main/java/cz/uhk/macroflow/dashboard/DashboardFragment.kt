package cz.uhk.macroflow.dashboard

import android.animation.ObjectAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.slider.Slider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import androidx.core.content.edit
import androidx.core.widget.doAfterTextChanged
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.nutrition.ConsumedFoodSheet
import cz.uhk.macroflow.data.FirebaseRepository
import cz.uhk.macroflow.common.MainActivity
import cz.uhk.macroflow.common.MakroflowNotifications
import cz.uhk.macroflow.common.MakroflowTimePicker
import cz.uhk.macroflow.R
import cz.uhk.macroflow.training.TrainerFragment
import cz.uhk.macroflow.training.TrainingTimeManager
import cz.uhk.macroflow.achievements.AchievementEngine
import cz.uhk.macroflow.data.CheckInEntity
import cz.uhk.macroflow.data.StepsEntity
import cz.uhk.macroflow.data.UserProfileEntity
import cz.uhk.macroflow.pokemon.EvolutionDialog
import cz.uhk.macroflow.pokemon.PokedexStatusEntity
import cz.uhk.macroflow.pokemon.PokemonLevelCalc
import cz.uhk.macroflow.pokemon.PokemonXpEntity
import cz.uhk.macroflow.pokemon.PokemonGrowthManager
import cz.uhk.macroflow.pokemon.XpRewards

class DashboardFragment : Fragment() {

    private lateinit var today: String
    private lateinit var waterPill: WaterPillView
    private var waterGoalMl: Int = 2500
    private var waterCurrentMl: Int = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return inflater.inflate(R.layout.activity_dashboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val ritualOverlay = view.findViewById<MaterialCardView>(R.id.cardRitualOverlay)
        val coachCard     = view.findViewById<MaterialCardView>(R.id.cardCoachAdvice)
        val btnSave       = view.findViewById<MaterialButton>(R.id.btnSaveRitual)

        // 👣 ✅ ŽIVÝ ODBĚR KROKŮ PŘES FLOW — Čte to, co MainActivity zapisuje
        val tvTodaySteps = view.findViewById<TextView>(R.id.tvTotalStepsCount)
        val db = AppDatabase.getDatabase(requireContext())

        viewLifecycleOwner.lifecycleScope.launch {
            db.stepsDao().getStepsForDateFlow(today).collect { entity ->
                val stepsToday = entity?.count ?: 0
                tvTodaySteps?.text = stepsToday.toString()
            }
        }

        setupEliteToggle(view)

        setupEasterEgg(view)

        view.findViewById<MaterialCardView>(R.id.cardStartTraining).setOnClickListener {
            parentFragmentManager.beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(R.id.nav_host_fragment, TrainerFragment())
                .addToBackStack(null)
                .commit()
        }

        coachCard.setOnClickListener {
            lifecycleScope.launch(Dispatchers.Main) {
                val todayCheckIn = withContext(Dispatchers.IO) {
                    db.checkInDao().getCheckInByDateSync(today)
                }

                val weightToShow = todayCheckIn?.weight
                    ?: withContext(Dispatchers.IO) {
                        db.checkInDao().getAllCheckInsSync().firstOrNull()?.weight
                    }
                    ?: requireContext()
                        .getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
                        .getString("weightAkt", "83.0")?.toDoubleOrNull()
                    ?: 83.0

                view.findViewById<EditText>(R.id.etCheckInWeight)?.setText(weightToShow.toString())

                if (todayCheckIn != null) {
                    view.findViewById<Slider>(R.id.sliderEnergy).value = todayCheckIn.energyLevel.toFloat()
                    view.findViewById<Slider>(R.id.sliderSleep).value = todayCheckIn.sleepQuality.toFloat()
                    view.findViewById<Slider>(R.id.sliderHunger).value = todayCheckIn.hungerLevel.toFloat()
                }

                ritualOverlay.visibility = View.VISIBLE
                ritualOverlay.alpha = 0f
                ritualOverlay.animate().alpha(1f).setDuration(300).start()
            }
        }

        btnSave.setOnClickListener {
            saveCheckInData(view)
            ritualOverlay.animate().alpha(0f).setDuration(200).withEndAction {
                ritualOverlay.visibility = View.GONE
            }.start()
        }

        view.findViewById<TextView>(R.id.btnFoodLog)?.setOnClickListener {
            val sheet = ConsumedFoodSheet()
            sheet.onFoodDeleted = { refreshAllData(requireView()) }
            sheet.show(parentFragmentManager, "ConsumedFoodSheet")
        }

        waterPill = view.findViewById(R.id.waterPillView)
        waterPill.setOnClickListener {
            val dialog = cz.uhk.macroflow.dashboard.WaterDialog()
            dialog.onWaterLogged = { addedMl ->
                waterCurrentMl += addedMl
                updateWaterPill(view)

                if (FirebaseRepository.isLoggedIn) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            val waterEntity = cz.uhk.macroflow.data.WaterEntity(
                                date = today,
                                amountMl = addedMl,
                                timestamp = System.currentTimeMillis()
                            )
                            FirebaseRepository.uploadWater(waterEntity)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
            dialog.show(parentFragmentManager, "WaterDialog")
        }

        val greetingTv = view.findViewById<TextView>(R.id.tvUserGreeting)
        FirebaseRepository.currentUser?.let { user ->
            greetingTv?.text = "Ahoj, ${user.displayName?.substringBefore(" ") ?: "sportovče"}! 👋"
        } ?: run {
            greetingTv?.text = "Sleduj svůj den. Každé sousto se počítá."
        }
        greetingTv?.visibility = View.VISIBLE

        setupWorkoutCard(view)
    }

    override fun onResume() {
        super.onResume()
        view?.let { refreshAllData(it) }
    }

    private fun refreshAllData(view: View) {
        lifecycleScope.launch(Dispatchers.Main.immediate) {
            val context = context ?: return@launch
            val db = AppDatabase.getDatabase(context)

            val consumedList = withContext(Dispatchers.IO) {
                db.consumedSnackDao().getConsumedByDate(today).first()
            }
            val checkIn = withContext(Dispatchers.IO) {
                db.checkInDao().getCheckInByDateSync(today)
            }

            val status = MacroFlowEngine.calculateDailyStatus(context, consumedList)
            val advice = MacroFlowEngine.getCoachAdvice(status, checkIn)

            updateCoachUI(advice)
            updateMacrosUI(view, status)
            updateWaterUI(view, status)
            updateTrainingStatusUI(view, context)
            updateWorkoutCard(view)
        }
    }

    private fun saveCheckInData(view: View) {
        val weightVal = view.findViewById<EditText>(R.id.etCheckInWeight)?.text.toString()
            .toDoubleOrNull() ?: 83.0
        val energy = view.findViewById<Slider>(R.id.sliderEnergy).value.toInt()
        val sleep  = view.findViewById<Slider>(R.id.sliderSleep).value.toInt()
        val hunger = view.findViewById<Slider>(R.id.sliderHunger).value.toInt()

        requireContext()
            .getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
            .edit { putString("weightAkt", weightVal.toString()) }

        val checkInEntity = CheckInEntity(
            date = today,
            weight = weightVal,
            energyLevel = energy,
            sleepQuality = sleep,
            hungerLevel = hunger
        )

        lifecycleScope.launch(Dispatchers.Main) {
            val db = AppDatabase.getDatabase(requireContext())

            // 1. Zápis, Analytika a Achievementy (Vše v IO, jak to bylo)
            val newAchievements = withContext(Dispatchers.IO) {
                db.checkInDao().insertCheckIn(checkInEntity)

                // --- PŘIDÁNO: Výpočet a upload analytiky, aby se vytvořila tabulka ---
                val history = db.checkInDao().getAllCheckInsSync()
                val analyticsResult = try {
                    cz.uhk.macroflow.analytics.BioLogicEngine.calculateFullAnalytics(history)
                } catch (e: Exception) { null }

                analyticsResult?.let { db.analyticsDao().insertAnalytics(it) }

                if (FirebaseRepository.isLoggedIn) {
                    try {
                        FirebaseRepository.uploadCheckIn(checkInEntity)
                        analyticsResult?.let { FirebaseRepository.uploadAnalytics(it) }
                    } catch (e: Exception) { e.printStackTrace() }
                }
                // --------------------------------------------------------------------

                AchievementEngine.checkAll(requireContext())
            }

            refreshAllData(requireView())
            Toast.makeText(context, "Rituál úspěšně uložen!", Toast.LENGTH_SHORT).show()

            // Tady už newAchievements bude zase fungovat normálně
            newAchievements.forEach { ach ->
                Toast.makeText(context, "🏆 Achievement odemčen: ${ach.titleCs}", Toast.LENGTH_LONG).show()
            }

            // Pokémon XP logika (beze změny, tak jak ti fungovala)
            lifecycleScope.launch(Dispatchers.IO) {
                val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                val prefs = requireContext().getSharedPreferences("GamePrefs", Context.MODE_PRIVATE)
                val activeId = prefs.getString("currentOnBarId", "050") ?: "050"
                val xpEntity = db.pokemonXpDao().getXp(activeId) ?: PokemonXpEntity(activeId)

                if (xpEntity.lastDailyRewardDate != todayStr) {
                    db.pokemonXpDao().setXp(xpEntity.copy(lastDailyRewardDate = todayStr))
                    withContext(Dispatchers.Main) {
                        (activity as? MainActivity)?.addXpToActivePokemonRealTime(XpRewards.CHECK_IN)
                    }
                    if (FirebaseRepository.isLoggedIn) {
                        val updatedXp = db.pokemonXpDao().getXp(activeId)
                        if (updatedXp != null) { FirebaseRepository.uploadPokedexStatus(activeId) }
                    }
                }
            }
        }
    }

    private fun updateCoachUI(advice: String) {
        view?.findViewById<TextView>(R.id.tvCoachMessage)?.text = advice
    }

    private fun updateWaterUI(view: View, status: DailyStatus) {
        waterGoalMl = (status.target.water * 1000).toInt()
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(requireContext())
            waterCurrentMl = withContext(Dispatchers.IO) {
                db.waterDao().getTotalMlForDateSync(today)
            }
            updateWaterPill(view)

            val lastTs = withContext(Dispatchers.IO) {
                db.waterDao().getLastDrinkTimestamp(today)
            }
            if (::waterPill.isInitialized) {
                val hoursSince = if (lastTs != null)
                    (System.currentTimeMillis() - lastTs) / 3_600_000f
                else 999f
                waterPill.isDehydrated = hoursSince >= 4f
            }
        }
    }

    private fun updateWaterPill(view: View) {
        if (!::waterPill.isInitialized) return
        val fraction = if (waterGoalMl > 0) waterCurrentMl.toFloat() / waterGoalMl else 0f
        waterPill.progressFraction = fraction
        waterPill.goalReached      = fraction >= 1f
        waterPill.tvMain = "${waterCurrentMl} ml"
        waterPill.tvSub  = "z ${waterGoalMl} ml · 💧"
        waterPill.invalidate()
    }

    private fun updateTrainingStatusUI(view: View, context: Context) {
        val dayName = SimpleDateFormat("EEEE", Locale.ENGLISH).format(Date())
        val prefs = context.getSharedPreferences("TrainingPrefs", Context.MODE_PRIVATE)
        val type = prefs.getString("type_$dayName", "rest")?.uppercase() ?: "REST"
        view.findViewById<TextView>(R.id.tvTrainingStatus)?.text = "DNES: $type"
        updateWorkoutCard(view)
    }

    private fun setupWorkoutCard(view: View) {
        val ctx  = context ?: return
        val card = view.findViewById<MaterialCardView>(R.id.cardTodayWorkout) ?: return
        val dayName = SimpleDateFormat("EEEE", Locale.ENGLISH).format(Date())
        updateWorkoutCard(view)
        card.setOnClickListener {
            val existing = TrainingTimeManager.getTrainingTimeForToday(ctx)
            val initH = existing?.split(":")?.getOrNull(0)?.toIntOrNull() ?: 7
            val initM = existing?.split(":")?.getOrNull(1)?.toIntOrNull() ?: 0
            MakroflowTimePicker.Companion.show(parentFragmentManager, initH, initM, "Čas dnešního tréninku") { h, m ->
                TrainingTimeManager.setTrainingTime(ctx, dayName, String.format("%02d:%02d", h, m))
                updateWorkoutCard(view)
                updateTrainingStatusUI(view, ctx)
                MakroflowNotifications.rescheduleWorkout(ctx)
                card.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            }
        }
    }

    private fun updateWorkoutCard(view: View) {
        val ctx    = context ?: return
        val tvTime = view.findViewById<TextView>(R.id.tvTodayWorkoutPill) ?: return
        val tvLabel = (tvTime.parent as? ViewGroup)?.getChildAt(0) as? TextView
        val timeStr = TrainingTimeManager.getTrainingTimeForToday(ctx)
        if (timeStr != null) {
            val h = timeStr.split(":")[0].toIntOrNull() ?: 0
            val m = timeStr.split(":")[1].toIntOrNull() ?: 0
            val endH = (h + (m + 75) / 60) % 24
            val endM = (m + 75) % 60
            tvTime.text     = timeStr
            tvTime.textSize = 22f
            tvTime.setTextColor(Color.parseColor("#DDA15E"))
            tvLabel?.text   = "%02d:%02d — %02d:%02d".format(h, m, endH, endM)
        } else {
            tvTime.text     = "Nastavit čas"
            tvTime.textSize = 16f
            tvTime.setTextColor(Color.parseColor("#80DDA15E"))
            tvLabel?.text   = "Dnes cvičíš v:"
        }
    }

    private fun updateMacrosUI(view: View, status: DailyStatus) {
        view.findViewById<TextView>(R.id.tvCalories)?.text =
            "${status.eatenCal.toInt()} / ${status.target.calories.toInt()}"
        view.findViewById<TextView>(R.id.tvValueProtein)?.text =
            "${status.eatenP.toInt()}g / ${status.target.protein.toInt()}g"
        view.findViewById<TextView>(R.id.tvValueCarbs)?.text =
            "${status.eatenS.toInt()}g / ${status.target.carbs.toInt()}g"
        view.findViewById<TextView>(R.id.tvValueFat)?.text =
            "${status.eatenT.toInt()}g / ${status.target.fat.toInt()}g"

        animateProgressCircles(view, status)
    }

    private fun animateProgressCircles(view: View, status: DailyStatus) {
        val pbPT = view.findViewById<ProgressBar>(R.id.progressProtein_Target)
        val pbPE = view.findViewById<ProgressBar>(R.id.progressProtein_Eaten)
        val pbCT = view.findViewById<ProgressBar>(R.id.progressCarbs_Target)
        val pbCE = view.findViewById<ProgressBar>(R.id.progressCarbs_Eaten)
        val pbFT = view.findViewById<ProgressBar>(R.id.progressFat_Target)
        val pbFE = view.findViewById<ProgressBar>(R.id.progressFat_Eaten)

        val totalWeightTarget = status.target.protein + status.target.carbs + status.target.fat
        if (totalWeightTarget <= 0) return

        val fProp = (status.target.fat / totalWeightTarget).toFloat()
        val cProp = (status.target.carbs / totalWeightTarget).toFloat()
        val startAngle = -90f

        val fTarget = (fProp * 1000).toInt()
        val cTarget = (cProp * 1000).toInt()
        val pTarget = 1000 - (fTarget + cTarget)

        val fatRot     = startAngle
        val carbsRot   = startAngle - (fProp * 360f)
        val proteinRot = carbsRot - (cProp * 360f)

        pbFT.rotation = fatRot;     pbFE.rotation = fatRot
        pbCT.rotation = carbsRot;   pbCE.rotation = carbsRot
        pbPT.rotation = proteinRot; pbPE.rotation = proteinRot

        listOf(pbFT, pbCT, pbPT).forEach { it.max = 1000 }
        pbFT.secondaryProgress = fTarget
        pbCT.secondaryProgress = cTarget
        pbPT.secondaryProgress = pTarget

        val fCurrent = ((status.eatenT / status.target.fat).coerceAtMost(1.0) * fTarget).toInt()
        val cCurrent = ((status.eatenS / status.target.carbs).coerceAtMost(1.0) * cTarget).toInt()
        val pCurrent = ((status.eatenP / status.target.protein).coerceAtMost(1.0) * pTarget).toInt()

        listOf(pbFE, pbCE, pbPE).forEach { it.max = 1000 }
        ObjectAnimator.ofInt(pbFE, "progress", pbFE.progress, fCurrent).setDuration(800).start()
        ObjectAnimator.ofInt(pbCE, "progress", pbCE.progress, cCurrent).setDuration(1000).start()
        ObjectAnimator.ofInt(pbPE, "progress", pbPE.progress, pCurrent).setDuration(1200).start()
    }

    private fun setupEliteToggle(view: View) {
        val cardEliteOptions = view.findViewById<MaterialCardView>(R.id.cardEliteOptions)
        val switchElite = view.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchEliteMode)
        val etWrist = view.findViewById<EditText>(R.id.etEliteWrist)
        val etBodyFat = view.findViewById<EditText>(R.id.etEliteBF)
        val autoDietType = view.findViewById<AutoCompleteTextView>(R.id.autoDietType)

        val db = AppDatabase.getDatabase(requireContext())
        var isInitialLoading = true

        // 1. STYLOVÁNÍ
        applySwitchStyles(switchElite)
        setupDietAdapter(autoDietType)

        // 2. NAČTENÍ DAT
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val profile = db.userProfileDao().getProfileSync() ?: UserProfileEntity(id = 1)

            withContext(Dispatchers.Main) {
                if (!isAdded) return@withContext
                isInitialLoading = true

                switchElite.apply {
                    setOnCheckedChangeListener(null)
                    isChecked = profile.isEliteMode
                    jumpDrawablesToCurrentState()
                }

                cardEliteOptions.visibility = if (profile.isEliteMode) View.VISIBLE else View.GONE

                if (profile.lastWristMeasurement > 0) etWrist?.setText(profile.lastWristMeasurement.toString())
                if (profile.bodyFatPercentage > 0) etBodyFat?.setText(profile.bodyFatPercentage.toString())

                val diet = profile.dietType ?: "Vyvážená"
                autoDietType?.setText(diet, false)
                updateMacroPreview(view, diet) // Prvotní vykreslení grafu

                isInitialLoading = false
                setupSwitchListener(view, switchElite, cardEliteOptions)
            }
        }

        // 3. LISTENERY
        etWrist?.doAfterTextChanged {
            if (!isInitialLoading) {
                val value = itToDouble(it)
                saveEliteField(true) { p -> p.copy(lastWristMeasurement = value) }
            }
        }

        etBodyFat?.doAfterTextChanged {
            if (!isInitialLoading) {
                val value = itToDouble(it)
                saveEliteField(true) { p -> p.copy(bodyFatPercentage = value) }
            }
        }

        autoDietType?.setOnItemClickListener { parent, _, pos, _ ->
            if (!isInitialLoading) {
                val selected = parent.getItemAtPosition(pos).toString()
                updateMacroPreview(view, selected) // Okamžitá změna grafu
                saveEliteField(true) { it.copy(dietType = selected) }
            }
        }
    }

    /** Funkce, která dynamicky mění šířku barevných segmentů podle zvolené diety */
    private fun updateMacroPreview(view: View, dietType: String) {
        val pieChart = view.findViewById<PieChartView>(R.id.pieChartMacro) ?: return

        val tvP = view.findViewById<TextView>(R.id.tvProteinPct)
        val tvS = view.findViewById<TextView>(R.id.tvCarbPct)
        val tvT = view.findViewById<TextView>(R.id.tvFatPct)

        // Definice poměrů B-S-T
        val (p, s, t) = when (dietType) {
            "Keto" -> Triple(20f, 5f, 75f)
            "Low Carb" -> Triple(25f, 15f, 60f)
            "Vegan" -> Triple(15f, 60f, 25f)
            "High Protein" -> Triple(40f, 20f, 40f)
            else -> Triple(25f, 45f, 30f) // Vyvážená
        }

        // Aplikace poměrů do grafu. View se samo překreslí.
        pieChart.setRatios(p, s, t)

        // Update textových procent. Tady se text zaručeně obarví tvou barvou.
        tvP?.text = "B: ${p.toInt()}%"
        tvS?.text = "S: ${s.toInt()}%"
        tvT?.text = "T: ${t.toInt()}%"
    }

    /** Pomocná funkce pro konverzi textu na Double */
    private fun itToDouble(text: Any?): Double = text.toString().replace(",", ".").toDoubleOrNull() ?: 0.0

    /** Fixuje barvy switche přímo v kódu */
    private fun applySwitchStyles(switch: com.google.android.material.switchmaterial.SwitchMaterial) {
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf(-android.R.attr.state_checked))
        val trackColors = intArrayOf(Color.parseColor("#DDA15E"), Color.parseColor("#DAD7CD"))
        val thumbColors = intArrayOf(Color.parseColor("#283618"), Color.parseColor("#606C38"))

        switch.trackTintList = ColorStateList(states, trackColors)
        switch.thumbTintList = ColorStateList(states, thumbColors)
    }

    /** Vizuální nastavení dropdownu pro diety */
    private fun setupDietAdapter(autoComplete: AutoCompleteTextView?) {
        val options = listOf("Vyvážená", "Low Carb", "Keto", "Vegan", "High Protein")
        val adapter = object : ArrayAdapter<String>(requireContext(), android.R.layout.simple_list_item_1, options) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                return (super.getView(position, convertView, parent) as TextView).apply {
                    setTextColor(Color.parseColor("#283618"))
                    setPadding(48, 40, 48, 40)
                }
            }
        }
        autoComplete?.setAdapter(adapter)
    }

    /** Logika přepínání Elite módu a animace karty */
    private fun setupSwitchListener(view: View, switch: com.google.android.material.switchmaterial.SwitchMaterial, optionsCard: View) {
        switch.setOnCheckedChangeListener { _, isChecked ->
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)

            if (isChecked) {
                optionsCard.visibility = View.VISIBLE
                optionsCard.alpha = 0f
                optionsCard.animate().alpha(1f).setDuration(300).withEndAction {
                    val scrollView = view.findViewById<androidx.core.widget.NestedScrollView>(R.id.dashboardScrollView)
                    scrollView?.post { scrollView.smoothScrollTo(0, optionsCard.bottom) }
                }.start()
            } else {
                optionsCard.animate().alpha(0f).setDuration(250).withEndAction {
                    optionsCard.visibility = View.GONE
                }.start()
            }
            saveEliteField(true) { it.copy(isEliteMode = isChecked) }
        }
    }

    /** Hlavní funkce pro ukládání do DB a synchronizaci s UI */
    private fun saveEliteField(shouldRefreshUI: Boolean, updateBlock: (UserProfileEntity) -> UserProfileEntity) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(requireContext())
            val currentProfile = db.userProfileDao().getProfileSync() ?: UserProfileEntity(id = 1)
            val updatedProfile = updateBlock(currentProfile)

            db.userProfileDao().saveProfile(updatedProfile)

            if (FirebaseRepository.isLoggedIn) {
                try { FirebaseRepository.uploadProfile(updatedProfile) } catch (e: Exception) {}
            }

            withContext(Dispatchers.Main) {
                if (isAdded && view != null && shouldRefreshUI) {
                    // TADY se děje ten přepočet maker v celém dashboardu
                    refreshAllData(requireView())
                }
            }
        }
    }

    private fun setupEasterEgg(view: View) {
        val fab = activity?.findViewById<View>(R.id.fabHome) ?: return
        var holdStart = 0L
        val HOLD_MS = 3000L

        fab.setOnTouchListener { v, event ->
            val handled = v.onTouchEvent(event)
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    holdStart = System.currentTimeMillis()
                    v.postDelayed({
                        if (holdStart > 0L && System.currentTimeMillis() - holdStart >= HOLD_MS) {
                            holdStart = 0L
                            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            if (isAdded) {
                                (activity as? MainActivity)?.openPokemonBattle()
                            }
                        }
                    }, HOLD_MS)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    holdStart = 0L
                }
            }
            handled
        }
    }
}