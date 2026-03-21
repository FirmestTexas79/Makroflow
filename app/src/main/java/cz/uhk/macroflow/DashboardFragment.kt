package cz.uhk.macroflow

import android.animation.ObjectAnimator
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class DashboardFragment : Fragment() {

    private lateinit var today: String

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return inflater.inflate(R.layout.activity_dashboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val ritualOverlay = view.findViewById<MaterialCardView>(R.id.cardRitualOverlay)
        val coachCard = view.findViewById<MaterialCardView>(R.id.cardCoachAdvice)
        val btnSave = view.findViewById<MaterialButton>(R.id.btnSaveRitual)

        // Spuštění tréninku [cite: 2026-03-01]
        view.findViewById<MaterialCardView>(R.id.cardStartTraining).setOnClickListener {
            parentFragmentManager.beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(R.id.nav_host_fragment, TrainerFragment())
                .addToBackStack(null)
                .commit()
        }

        coachCard.setOnClickListener {
            ritualOverlay.visibility = View.VISIBLE
            ritualOverlay.alpha = 0f
            ritualOverlay.animate().alpha(1f).setDuration(300).start()
        }

        btnSave.setOnClickListener {
            saveCheckInData(view)
            ritualOverlay.animate().alpha(0f).setDuration(200).withEndAction {
                ritualOverlay.visibility = View.GONE
            }.start()
        }
    }

    // KLÍČOVÁ ZMĚNA: Refresh dat pokaždé, když se fragment stane aktivním
    override fun onResume() {
        super.onResume()
        view?.let { refreshAllData(it) }
    }

    private fun refreshAllData(view: View) {
        // Použijeme Main.immediate, aby se UI update nezpožďoval v frontě
        lifecycleScope.launch(Dispatchers.Main.immediate) {
            val context = context ?: return@launch
            val db = AppDatabase.getDatabase(context)

            // Načtení dat přesuneme do IO, ale výsledek zpracujeme hned
            val consumedList = withContext(Dispatchers.IO) {
                db.consumedSnackDao().getConsumedByDate(today).first()
            }
            val checkIn = withContext(Dispatchers.IO) {
                db.checkInDao().getCheckInByDateSync(today)
            }

            // 2. Výpočet (vše v rámci jednoho bloku, aby se to nemohlo "rozbít")
            val status = MacroFlowEngine.calculateDailyStatus(context, consumedList)
            val advice = MacroFlowEngine.getCoachAdvice(status, checkIn)

            // 3. Update UI - voláme přímo a hned
            updateCoachUI(advice)
            updateMacrosUI(view, status)

            // Update váhy v poli
            val displayWeight = checkIn?.weight ?: withContext(Dispatchers.IO) {
                db.checkInDao().getAllCheckInsSync().firstOrNull()?.weight ?: 83.0
            }
            view.findViewById<EditText>(R.id.etWeight)?.setText(displayWeight.toString())
        }
    }

    private fun saveCheckInData(view: View) {
        val weightVal = view.findViewById<EditText>(R.id.etWeight).text.toString().toDoubleOrNull() ?: 83.0
        val energy = view.findViewById<Slider>(R.id.sliderEnergy).value.toInt()
        val sleep = view.findViewById<Slider>(R.id.sliderSleep).value.toInt()
        val hunger = view.findViewById<Slider>(R.id.sliderHunger).value.toInt()

        // Uložení váhy pro MacroCalculator [cite: 2026-02-02, 2026-03-10]
        requireContext().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
            .edit().putString("weightAkt", weightVal.toString()).apply()

        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(requireContext())
            db.checkInDao().insertCheckIn(CheckInEntity(
                date = today, weight = weightVal, energyLevel = energy, sleepQuality = sleep, hungerLevel = hunger
            ))

            withContext(Dispatchers.Main) {
                refreshAllData(view) // Překreslí rady i makra
                Toast.makeText(context, "Denní report uložen!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateCoachUI(advice: String) {
        val tvMessage = view?.findViewById<TextView>(R.id.tvCoachMessage)
        tvMessage?.text = advice
    }

    private fun updateMacrosUI(view: View, status: DailyStatus) {
        // Texty [cite: 2026-03-04]
        view.findViewById<TextView>(R.id.tvCalories).text = "${status.eatenCal.toInt()} / ${status.target.calories.toInt()}"
        view.findViewById<TextView>(R.id.tvValueProtein).text = "${status.eatenP.toInt()}g / ${status.target.protein.toInt()}g"
        view.findViewById<TextView>(R.id.tvValueCarbs).text = "${status.eatenS.toInt()}g / ${status.target.carbs.toInt()}g"
        view.findViewById<TextView>(R.id.tvValueFat).text = "${status.eatenT.toInt()}g / ${status.target.fat.toInt()}g"

        // Kruhy
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

        if (totalWeightTarget > 0) {
            val fProp = (status.target.fat / totalWeightTarget).toFloat()
            val cProp = (status.target.carbs / totalWeightTarget).toFloat()
            val startAngle = -90f

            val fTarget = (fProp * 1000).toInt()
            val cTarget = (cProp * 1000).toInt()
            val pTarget = 1000 - (fTarget + cTarget)

            val fatRot = startAngle
            val carbsRot = startAngle - (fProp * 360f)
            val proteinRot = carbsRot - (cProp * 360f)

            pbFT.rotation = fatRot; pbFE.rotation = fatRot
            pbCT.rotation = carbsRot; pbCE.rotation = carbsRot
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
}