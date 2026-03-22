package cz.uhk.macroflow

import android.animation.ObjectAnimator
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

class DashboardFragment : Fragment() {

    private lateinit var today: String
    private lateinit var waterPill: cz.uhk.macroflow.WaterPillView
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

        // Spuštění trenéra
        view.findViewById<MaterialCardView>(R.id.cardStartTraining).setOnClickListener {
            parentFragmentManager.beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(R.id.nav_host_fragment, TrainerFragment())
                .addToBackStack(null)
                .commit()
        }

        // V DashboardFragment.kt -> v onViewCreated() při kliknutí na coachCard

        coachCard.setOnClickListener {
            lifecycleScope.launch(Dispatchers.Main) {
                val db = AppDatabase.getDatabase(requireContext())

                val todayCheckIn = withContext(Dispatchers.IO) {
                    db.checkInDao().getCheckInByDateSync(today)
                }

                // Vždy naplnit pole váhy — z dnešního check-inu, nebo posledního, nebo SharedPrefs
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

        // Uložení rituálu
        btnSave.setOnClickListener {
            saveCheckInData(view)
            ritualOverlay.animate().alpha(0f).setDuration(200).withEndAction {
                ritualOverlay.visibility = View.GONE
            }.start()
        }

        // Pill — seznam jídla (vedle kruhu)
        view.findViewById<android.widget.TextView>(R.id.btnFoodLog)?.setOnClickListener {
            val sheet = ConsumedFoodSheet()
            sheet.onFoodDeleted = { refreshAllData(requireView()) }
            sheet.show(parentFragmentManager, "ConsumedFoodSheet")
        }

        // Inicializace water pillu
        waterPill = view.findViewById(R.id.waterPillView)
        waterPill.setOnClickListener {
            val dialog = WaterDialog()
            dialog.onWaterLogged = { addedMl ->
                waterCurrentMl += addedMl
                updateWaterPill(view)
            }
            dialog.show(parentFragmentManager, "WaterDialog")
        }

        // Zobraz jméno / email přihlášeného uživatele pokud je k dispozici TextView
        // Pozdrav — přihlášený uživatel nebo výchozí podtitulek
        val greetingTv = view.findViewById<TextView>(R.id.tvUserGreeting)
        FirebaseRepository.currentUser?.let { user ->
            greetingTv?.text = "Ahoj, ${user.displayName?.substringBefore(" ") ?: "sportovče"}! 👋"
        } ?: run {
            greetingTv?.text = "Sleduj svůj den. Každé sousto se počítá."
        }
        greetingTv?.visibility = android.view.View.VISIBLE
    }

    override fun onResume() {
        super.onResume()
        view?.let { refreshAllData(it) }
    }

    // ----------------------------------------------------------------
    // Refresh všech dat na dashboardu
    // ----------------------------------------------------------------
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

            val displayWeight = checkIn?.weight ?: withContext(Dispatchers.IO) {
                db.checkInDao().getAllCheckInsSync().firstOrNull()?.weight ?: 83.0
            }
            // etWeight odstraněn - váha se zobrazuje jen v overlay rituálu
        }
    }

    // ----------------------------------------------------------------
    // Uložení check-inu — Room + Firebase
    // ----------------------------------------------------------------
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

            // 1. Uložíme lokálně + zkontrolujeme achievementy
            val newAchievements = withContext(Dispatchers.IO) {
                db.checkInDao().insertCheckIn(checkInEntity)
                AchievementEngine.checkAll(requireContext())
            }

            // 2. Refreshneme UI OKAMŽITĚ z lokálních dat v Roomu
            refreshAllData(requireView())

            Toast.makeText(
                context,
                "Rituál úspěšně uložen! 🏋️‍♂️",
                Toast.LENGTH_SHORT
            ).show()

            // 3. Notifikace nově odemčených achievementů
            newAchievements.forEach { ach ->
                Toast.makeText(
                    context,
                    "🏆 Achievement odemčen: ${ach.titleCs}",
                    Toast.LENGTH_LONG
                ).show()
            }

            // 3. Firebase synchronizaci odsuneme na úplně vedlejší kolej, ať nebrzdí UI
            if (FirebaseRepository.isLoggedIn) {
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        FirebaseRepository.uploadCheckIn(checkInEntity)
                    } catch (e: Exception) {
                        // Ignorujeme PERMISSION_DENIED z Logcatu
                    }
                }
            }
        }
    }

    // ----------------------------------------------------------------
    // UI update metody
    // ----------------------------------------------------------------
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

            // Sleduj dehydrataci — 4h+ bez pití
            val lastTs = withContext(Dispatchers.IO) {
                db.waterDao().getLastDrinkTimestamp(today)
            }
            if (lastTs != null) {
                val hoursSince = (System.currentTimeMillis() - lastTs) / 3_600_000f
                if (::waterPill.isInitialized) waterPill.isDehydrated = hoursSince >= 4f
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
        val sdf = SimpleDateFormat("EEEE", Locale.ENGLISH)
        val dayName = sdf.format(Date())
        val prefs = context.getSharedPreferences("TrainingPrefs", Context.MODE_PRIVATE)
        val type = prefs.getString("type_$dayName", "rest")?.uppercase() ?: "REST"
        view.findViewById<TextView>(R.id.tvTrainingStatus)?.text = "DNES: $type"
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
}