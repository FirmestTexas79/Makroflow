package cz.uhk.macroflow.dashboard

import android.animation.ObjectAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.slider.Slider
import kotlinx.coroutines.Dispatchers
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
import cz.uhk.macroflow.energy.FoodEnergy
import cz.uhk.macroflow.training.TrainerFragment
import cz.uhk.macroflow.training.TrainingTimeManager
import cz.uhk.macroflow.achievements.AchievementEngine
import cz.uhk.macroflow.data.CheckInEntity
import cz.uhk.macroflow.data.UserProfileEntity
import cz.uhk.macroflow.pokemon.PokemonXpEngine

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

        val coachCard = view.findViewById<MaterialCardView>(R.id.cardCoachAdvice)
        val btnSave = view.findViewById<MaterialButton>(R.id.btnSaveRitual)

        val tvTodaySteps = view.findViewById<TextView>(R.id.tvTotalStepsCount)
        val db = AppDatabase.getDatabase(requireContext())

        viewLifecycleOwner.lifecycleScope.launch {
            db.stepsDao().getStepsForDateFlow(today).collect { entity ->
                val stepsToday = entity?.count ?: 0
                tvTodaySteps?.text = num(stepsToday.toDouble())
            }
        }

        setupHeader(view)
        if (savedInstanceState == null) playEntrance(view)

        setupEliteToggle(view)

        view.findViewById<View>(R.id.cardStartTraining).setOnClickListener {
            parentFragmentManager.beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(R.id.nav_host_fragment, TrainerFragment())
                .addToBackStack(null)
                .commit()
        }

        view.findViewById<View>(R.id.tvCoachCta)?.setOnClickListener { coachCard.performClick() }
        view.findViewById<View>(R.id.ritualScrim)?.setOnClickListener { showRitual(view, false) }
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
                    view.findViewById<Slider>(R.id.sliderSleep).value  = todayCheckIn.sleepQuality.toFloat()
                    view.findViewById<Slider>(R.id.sliderHunger).value = todayCheckIn.hungerLevel.toFloat()
                }

                showRitual(view, true)
            }
        }

        btnSave.setOnClickListener {
            saveCheckInData(view)
            showRitual(view, false)
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
                        } catch (e: Exception) { e.printStackTrace() }
                    }
                }
            }
            dialog.show(parentFragmentManager, "WaterDialog")
        }

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
                db.consumedSnackDao().getConsumedByDateSync(today)
            }
            val checkIn = withContext(Dispatchers.IO) {
                db.checkInDao().getCheckInByDateSync(today)
            }

            val status = MacroFlowEngine.calculateDailyStatus(context, consumedList)
            val advice = MacroFlowEngine.getCoachAdvice(status, checkIn)

            updateCoachUI(advice)
            updateCoachCta(view, checkIn != null)
            updateStreak(view)
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
            date         = today,
            weight       = weightVal,
            energyLevel  = energy,
            sleepQuality = sleep,
            hungerLevel  = hunger
        )

        lifecycleScope.launch(Dispatchers.Main) {
            val db = AppDatabase.getDatabase(requireContext())

            val newAchievements = withContext(Dispatchers.IO) {
                db.checkInDao().insertCheckIn(checkInEntity)

                val currentProfile = db.userProfileDao().getProfileSync() ?: UserProfileEntity(id = 1)
                val updatedProfile = currentProfile.copy(weight = weightVal)
                db.userProfileDao().saveProfile(updatedProfile)

                val history = db.checkInDao().getAllCheckInsSync()
                val analyticsResult = try {
                    cz.uhk.macroflow.analytics.BioLogicEngine.calculateFullAnalytics(history)
                } catch (e: Exception) { null }

                analyticsResult?.let { db.analyticsDao().insertAnalytics(it) }

                // Adaptivní výdej (fáze B) – přepočet po novém vážení
                try { AdaptiveTdeeRepository.recompute(requireContext().applicationContext) }
                catch (e: Exception) { android.util.Log.e("AdaptiveTDEE", "Přepočet selhal: ${e.message}") }

                if (FirebaseRepository.isLoggedIn) {
                    try {
                        FirebaseRepository.uploadCheckIn(checkInEntity)
                        FirebaseRepository.uploadProfile(updatedProfile)
                        analyticsResult?.let { FirebaseRepository.uploadAnalytics(it) }
                    } catch (e: Exception) { e.printStackTrace() }
                }

                AchievementEngine.checkAll(requireContext())
            }

            refreshAllData(requireView())
            Toast.makeText(context, "Rituál úspěšně uložen!", Toast.LENGTH_SHORT).show()

            newAchievements.forEach { ach ->
                Toast.makeText(context, "🏆 Achievement odemčen: ${ach.titleCs}", Toast.LENGTH_LONG).show()
            }

            // XP za check-in přes MakromonXpEngine
            lifecycleScope.launch(Dispatchers.IO) {
                val xp = PokemonXpEngine.tryAwardGoalXp(requireContext(), PokemonXpEngine.XpGoal.CHECK_IN)
                if (xp > 0) {
                    withContext(Dispatchers.Main) {
                        (activity as? MainActivity)?.addXpToActiveMakromonRealTime(xp)
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
        waterPill.goalReached = fraction >= 1f
        waterPill.tvMain = "${waterCurrentMl} ml"
        waterPill.tvSub = "z ${waterGoalMl} ml · 💧"
        waterPill.invalidate()
    }

    private fun updateTrainingStatusUI(view: View, context: Context) {
        val dayName = SimpleDateFormat("EEEE", Locale.ENGLISH).format(Date())
        val prefs = context.getSharedPreferences("TrainingPrefs", Context.MODE_PRIVATE)
        val type = prefs.getString("type_$dayName", "rest")?.uppercase() ?: "REST"
        val time = TrainingTimeManager.getTrainingTimeForToday(context)
        val rest = type == "REST"
        view.findViewById<TextView>(R.id.tvTrainingStatus)?.text =
            if (rest) "🌿  ODPOČINEK" else "🏋  $type" + (time?.let { "  ·  $it" } ?: "")
        view.findViewById<TextView>(R.id.tvDashSub)?.text = when {
            rest -> "Dnes regeneruješ. Jídlo a spánek dělají svaly."
            time != null -> "Dnes tě čeká ${type.lowercase().replaceFirstChar { it.uppercase() }} v $time. Ať to stojí za to."
            else -> "Dnes je ${type.lowercase().replaceFirstChar { it.uppercase() }} den. Nastav si čas tréninku."
        }
        updateWorkoutCard(view)
    }

    private fun setupWorkoutCard(view: View) {
        val ctx = context ?: return
        val card = view.findViewById<MaterialCardView>(R.id.cardTodayWorkout) ?: return
        val dayName = SimpleDateFormat("EEEE", Locale.ENGLISH).format(Date())
        updateWorkoutCard(view)
        card.setOnClickListener {
            val existing = TrainingTimeManager.getTrainingTimeForToday(ctx)
            val initH = existing?.split(":")?.getOrNull(0)?.toIntOrNull() ?: 7
            val initM = existing?.split(":")?.getOrNull(1)?.toIntOrNull() ?: 0
            MakroflowTimePicker.Companion.show(
                parentFragmentManager, initH, initM, "Čas dnešního tréninku"
            ) { h, m ->
                TrainingTimeManager.setTrainingTime(ctx, dayName, String.format("%02d:%02d", h, m))
                updateWorkoutCard(view)
                updateTrainingStatusUI(view, ctx)
                MakroflowNotifications.rescheduleWorkout(ctx)
                card.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            }
        }
    }

    private fun updateWorkoutCard(view: View) {
        val ctx = context ?: return
        val tvTime  = view.findViewById<TextView>(R.id.tvTodayWorkoutPill) ?: return
        val tvLabel = (tvTime.parent as? ViewGroup)?.getChildAt(0) as? TextView
        val timeStr = TrainingTimeManager.getTrainingTimeForToday(ctx)
        if (timeStr != null) {
            val h = timeStr.split(":")[0].toIntOrNull() ?: 0
            val m = timeStr.split(":")[1].toIntOrNull() ?: 0
            val endH = (h + (m + 75) / 60) % 24
            val endM = (m + 75) % 60
            tvTime.text = timeStr
            tvTime.textSize = 22f
            tvTime.setTextColor(Color.parseColor("#DDA15E"))
            tvLabel?.text = "%02d:%02d — %02d:%02d".format(h, m, endH, endM)
        } else {
            tvTime.text = "Nastavit čas"
            tvTime.textSize = 16f
            tvTime.setTextColor(Color.parseColor("#80DDA15E"))
            tvLabel?.text = "Dnes cvičíš v:"
        }
    }

    private fun updateMacrosUI(view: View, status: DailyStatus) {
        val t = status.target
        val left = t.calories - status.eatenCal
        countUp(view.findViewById(R.id.tvCalories), kotlin.math.abs(left))
        view.findViewById<TextView>(R.id.tvCaloriesLabel)?.text = if (left >= 0) "KCAL ZBÝVÁ" else "KCAL NAD CÍLEM"
        view.findViewById<TextView>(R.id.tvCaloriesSub)?.text = "${num(status.eatenCal)} / ${num(t.calories)} kcal"

        fun macro(valueId: Int, leftId: Int, eaten: Double, goal: Double) {
            view.findViewById<TextView>(valueId)?.text = "${eaten.toInt()} / ${goal.toInt()} g"
            val rest = goal - eaten
            view.findViewById<TextView>(leftId)?.text = when {
                goal <= 0 -> ""
                rest > 0.5 -> "zbývá ${rest.toInt()} g"
                rest > -goal * 0.1 -> "splněno ✓"
                else -> "+${(-rest).toInt()} g navíc"
            }
        }
        macro(R.id.tvValueProtein, R.id.tvLeftProtein, status.eatenP, t.protein)
        macro(R.id.tvValueCarbs, R.id.tvLeftCarbs, status.eatenS, t.carbs)
        macro(R.id.tvValueFat, R.id.tvLeftFat, status.eatenT, t.fat)

        fun ratio(e: Double, g: Double) = if (g > 0) (e / g).toFloat() else 0f
        view.findViewById<MacroRingsView>(R.id.ringsMacro)?.setProgress(
            ratio(status.eatenP, t.protein), ratio(status.eatenS, t.carbs), ratio(status.eatenT, t.fat))
        view.findViewById<View>(R.id.cardEnergy)?.contentDescription =
            "Energie dnes: ${num(status.eatenCal)} z ${num(t.calories)} kcal. Bílkoviny ${status.eatenP.toInt()} z ${t.protein.toInt()} gramů, " +
                "sacharidy ${status.eatenS.toInt()} z ${t.carbs.toInt()}, tuky ${status.eatenT.toInt()} z ${t.fat.toInt()}."

        view.findViewById<TextView>(R.id.tvValueFiber)?.text = String.format(Locale("cs"), "%.0f / %.0f g", status.eatenFiber, t.fiber)
        view.findViewById<ProgressBar>(R.id.progressFiber)?.let { pb ->
            val target = ((status.eatenFiber / t.fiber.coerceAtLeast(1.0)).coerceIn(0.0, 1.0) * 1000).toInt()
            ObjectAnimator.ofInt(pb, "progress", pb.progress, target).setDuration(900).start()
        }
    }

    // ── Hlavička a drobnosti (docs/adr/0074) ────────────────────────────────

    private fun setupHeader(view: View) {
        val now = Calendar.getInstance()
        val hour = now.get(Calendar.HOUR_OF_DAY)
        val date = SimpleDateFormat("EEEE · d. MMMM", Locale("cs")).format(now.time).uppercase(Locale("cs"))
        view.findViewById<TextView>(R.id.tvDashDate)?.text = date
        view.findViewById<android.widget.ImageView>(R.id.ivDayIcon)?.setImageResource(
            if (hour in 6..19) R.drawable.ic_line_sun else R.drawable.ic_line_moon)
        view.findViewById<TextView>(R.id.tvUserGreeting)?.text = when (hour) {
            in 4..9 -> "Dobré ráno"
            in 10..11 -> "Dobré dopoledne"
            in 12..17 -> "Dobré odpoledne"
            in 18..21 -> "Dobrý večer"
            else -> "Dobrou noc"
        }
    }

    /** Výzva pod tipem trenéra: rituál ještě čeká, nebo je hotový. */
    private fun updateCoachCta(view: View, done: Boolean) {
        val cta = view.findViewById<TextView>(R.id.tvCoachCta) ?: return
        val ctx = cta.context
        if (done) {
            cta.text = "Rituál hotový  ✓"
            cta.backgroundTintList = ColorStateList.valueOf(ctx.getColor(R.color.brand_dark_alpha10))
            cta.setTextColor(ctx.getColor(R.color.brand_primary))
        } else {
            cta.text = "Ranní rituál  →"
            cta.backgroundTintList = ColorStateList.valueOf(ctx.getColor(R.color.brand_dark))
            cta.setTextColor(ctx.getColor(R.color.brand_cream))
        }
    }

    /** Kolik dní v řadě je hotový ranní rituál (dnešek, nebo do včerejška, když dnes ještě ne). */
    private fun updateStreak(view: View) {
        val chip = view.findViewById<TextView>(R.id.tvDashStreak) ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val days = withContext(Dispatchers.IO) {
                AppDatabase.getDatabase(chip.context).checkInDao().getAllCheckInsSync().map { it.date }.toSet()
            }
            var d = java.time.LocalDate.now()
            if (d.toString() !in days) d = d.minusDays(1)
            var n = 0
            while (d.toString() in days) { n++; d = d.minusDays(1) }
            chip.visibility = if (n >= 2) View.VISIBLE else View.GONE
            chip.text = "🔥  $n ${if (n in 2..4) "dny" else "dní"} v řadě"
        }
    }

    /** Okno ranního rituálu se ztmavením pozadí; okno lehce naskočí. */
    private fun showRitual(view: View, show: Boolean) {
        val card = view.findViewById<View>(R.id.cardRitualOverlay) ?: return
        val scrim = view.findViewById<View>(R.id.ritualScrim)
        if (show) {
            listOf(scrim, card).forEach { it?.visibility = View.VISIBLE; it?.alpha = 0f }
            scrim?.animate()?.alpha(1f)?.setDuration(220)?.start()
            card.scaleX = 0.94f; card.scaleY = 0.94f
            card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(280)
                .setInterpolator(android.view.animation.OvershootInterpolator(1.4f)).start()
        } else {
            scrim?.animate()?.alpha(0f)?.setDuration(200)?.withEndAction { scrim.visibility = View.GONE }?.start()
            card.animate().alpha(0f).scaleX(0.96f).scaleY(0.96f).setDuration(200).withEndAction { card.visibility = View.GONE }.start()
        }
    }

    /** Karty při otevření postupně vyjedou zespodu. */
    private fun playEntrance(view: View) {
        val box = view.findViewById<ViewGroup>(R.id.dashboardScroll) ?: return
        val dy = 28 * resources.displayMetrics.density
        for (i in 0 until box.childCount) {
            val c = box.getChildAt(i)
            if (c.visibility != View.VISIBLE) continue
            c.alpha = 0f; c.translationY = dy
            c.animate().alpha(1f).translationY(0f).setStartDelay(60L * i).setDuration(420)
                .setInterpolator(android.view.animation.DecelerateInterpolator(2f)).start()
        }
    }

    /** Číslo se dopočítá z předchozí hodnoty (mezery v tisících). */
    private fun countUp(tv: TextView?, value: Double) {
        tv ?: return
        val from = (tv.tag as? Double) ?: 0.0
        tv.tag = value
        android.animation.ValueAnimator.ofFloat(from.toFloat(), value.toFloat()).apply {
            duration = 900; interpolator = android.view.animation.DecelerateInterpolator(1.8f)
            addUpdateListener { tv.text = num((it.animatedValue as Float).toDouble()) }
            start()
        }
    }

    private fun num(v: Double) = String.format(Locale("cs"), "%,d", v.toInt()).replace('\u00A0', ' ').replace('\u202F', ' ')

    private fun setupEliteToggle(view: View) {
        val cardEliteOptions = view.findViewById<MaterialCardView>(R.id.cardEliteOptions)
        val switchElite = view.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchEliteMode)
        val etWrist     = view.findViewById<EditText>(R.id.etEliteWrist)
        val etBodyFat   = view.findViewById<EditText>(R.id.etEliteBF)
        val autoDietType = view.findViewById<AutoCompleteTextView>(R.id.autoDietType)

        val db = AppDatabase.getDatabase(requireContext())
        var isInitialLoading = true

        applySwitchStyles(switchElite)
        setupDietAdapter(autoDietType)

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
                if (profile.bodyFatPercentage > 0)    etBodyFat?.setText(profile.bodyFatPercentage.toString())

                val diet = profile.dietType ?: "Vyvážená"
                autoDietType?.setText(diet, false)
                updateMacroPreview(view, diet)

                isInitialLoading = false
                setupSwitchListener(view, switchElite, cardEliteOptions)
            }
        }

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
                updateMacroPreview(view, selected)
                saveEliteField(true) { it.copy(dietType = selected) }
            }
        }
    }

    private fun updateMacroPreview(view: View, dietType: String) {
        val pieChart = view.findViewById<PieChartView>(R.id.pieChartMacro) ?: return
        val tvP = view.findViewById<TextView>(R.id.tvProteinPct)
        val tvS = view.findViewById<TextView>(R.id.tvCarbPct)
        val tvT = view.findViewById<TextView>(R.id.tvFatPct)

        // Skutečné rozložení z modelu (dřív pevné procento, které neodpovídalo výpočtu cílů)
        val preview = MacroCalculator.previewForDiet(requireContext(), dietType)
        val kcalP = preview.protein * FoodEnergy.KCAL_PER_G_PROTEIN
        val kcalS = preview.carbs * FoodEnergy.KCAL_PER_G_CARBS
        val kcalT = preview.fat * FoodEnergy.KCAL_PER_G_FAT
        val sum = (kcalP + kcalS + kcalT).coerceAtLeast(1.0)
        val p = (kcalP / sum * 100).toFloat()
        val s = (kcalS / sum * 100).toFloat()
        val t = (kcalT / sum * 100).toFloat()

        pieChart.setRatios(p, s, t)
        tvP?.text = "B: ${p.toInt()}%"
        tvS?.text = "S: ${s.toInt()}%"
        tvT?.text = "T: ${t.toInt()}%"
    }

    private fun itToDouble(text: Any?): Double =
        text.toString().replace(",", ".").toDoubleOrNull() ?: 0.0

    private fun applySwitchStyles(switch: com.google.android.material.switchmaterial.SwitchMaterial) {
        val states = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(-android.R.attr.state_checked)
        )
        val trackColors = intArrayOf(Color.parseColor("#DDA15E"), Color.parseColor("#DAD7CD"))
        val thumbColors = intArrayOf(Color.parseColor("#283618"), Color.parseColor("#606C38"))
        switch.trackTintList = ColorStateList(states, trackColors)
        switch.thumbTintList = ColorStateList(states, thumbColors)
    }

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

    private fun setupSwitchListener(
        view: View,
        switch: com.google.android.material.switchmaterial.SwitchMaterial,
        optionsCard: View
    ) {
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

    private fun saveEliteField(
        shouldRefreshUI: Boolean,
        updateBlock: (UserProfileEntity) -> UserProfileEntity
    ) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(requireContext())
            val currentProfile = db.userProfileDao().getProfileSync() ?: UserProfileEntity(id = 1)
            val updatedProfile = updateBlock(currentProfile)

            db.userProfileDao().saveProfile(updatedProfile)

            if (FirebaseRepository.isLoggedIn) {
                try { FirebaseRepository.uploadProfile(updatedProfile) } catch (e: Exception) { }
            }

            withContext(Dispatchers.Main) {
                if (isAdded && view != null && shouldRefreshUI) refreshAllData(requireView())
            }
        }
    }
}