package cz.uhk.macroflow

import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.slider.Slider
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.thread

class DashboardFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.activity_dashboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val ritualOverlay = view.findViewById<MaterialCardView>(R.id.cardRitualOverlay)
        val coachCard = view.findViewById<MaterialCardView>(R.id.cardCoachAdvice)
        val btnSave = view.findViewById<MaterialButton>(R.id.btnSaveRitual)

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

        displayMacros(view)
        loadCoachAdvice(view)
    }

    private fun saveCheckInData(view: View) {
        val weight = view.findViewById<EditText>(R.id.etWeight).text.toString().toDoubleOrNull() ?: 83.0
        val energy = view.findViewById<Slider>(R.id.sliderEnergy).value.toInt()
        val sleep = view.findViewById<Slider>(R.id.sliderSleep).value.toInt()

        thread {
            val db = AppDatabase.getDatabase(requireContext())
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

            val newCheckIn = CheckInEntity(
                date = today,
                weight = weight,
                energyLevel = energy,
                sleepQuality = sleep,
                hungerLevel = 3 // Fix pro tvůj Entity error
            )

            db.checkInDao().insertCheckIn(newCheckIn)

            activity?.runOnUiThread {
                loadCoachAdvice(view)
            }
        }
    }

    private fun loadCoachAdvice(view: View) {
        val tvMessage = view.findViewById<TextView>(R.id.tvCoachMessage)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        thread {
            val db = AppDatabase.getDatabase(requireContext())
            val checkIn = db.checkInDao().getCheckInByDate(today)

            activity?.runOnUiThread {
                if (checkIn != null) {
                    tvMessage.text = when {
                        checkIn.energyLevel <= 2 -> "Dneska zvolni. Tvých ${checkIn.weight}kg potřebuje rest."
                        checkIn.energyLevel >= 4 -> "Energie top! Jdi do toho naplno."
                        else -> "Standardní režim. Drž se plánu!"
                    }
                } else {
                    tvMessage.text = "Ještě jsi neudělal ranní rituál. Klikni zde!"
                }
            }
        }
    }

    private fun displayMacros(view: View) {
        val data = MacroCalculator.calculate(requireContext())

        // Textové hodnoty
        view.findViewById<TextView>(R.id.tvCalories).text = "${data.calories.toInt()}"
        view.findViewById<TextView>(R.id.tvValueProtein).text = "${data.protein.toInt()}g"
        view.findViewById<TextView>(R.id.tvValueCarbs).text = "${data.carbs.toInt()}g"
        view.findViewById<TextView>(R.id.tvValueFat).text = "${data.fat.toInt()}g"
        view.findViewById<TextView>(R.id.tvTrainingStatus).text = "DNES: ${data.trainingType.uppercase()}"

        // --- OPRAVA KRUHŮ (VRSTVENÍ) ---
        val pbF = view.findViewById<ProgressBar>(R.id.progressFat)
        val pbC = view.findViewById<ProgressBar>(R.id.progressCarbs)
        val pbP = view.findViewById<ProgressBar>(R.id.progressProtein)

        // Výpočet poměrů pro animaci (předpokládáme max 1000 pro plynulost)
        val pCal = data.protein * 4; val cCal = data.carbs * 4; val fCal = data.fat * 9
        val total = pCal + cCal + fCal

        val fProp = (fCal / total).toFloat()
        val cProp = (cCal / total).toFloat()

        // Nastavení startovní rotace, aby na sebe kruhy navazovaly (každý začíná tam, kde předchozí končí)
        val startAngle = -90f
        pbF.rotation = startAngle
        pbC.rotation = startAngle - (fProp * 360f)
        pbP.rotation = startAngle - (fProp * 360f) - (cProp * 360f)

        // Animace každého prstence zvlášť
        pbF.max = 1000; pbC.max = 1000; pbP.max = 1000

        ObjectAnimator.ofInt(pbF, "progress", 0, (fProp * 1000).toInt()).setDuration(800).start()
        ObjectAnimator.ofInt(pbC, "progress", 0, (cProp * 1000).toInt()).setDuration(1000).start()
        ObjectAnimator.ofInt(pbP, "progress", 0, (1000 - (fProp * 1000 + cProp * 1000).toInt())).setDuration(1200).start()
    }
}