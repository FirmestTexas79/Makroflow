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
        val weightVal = view.findViewById<EditText>(R.id.etWeight).text.toString().toDoubleOrNull() ?: 83.0
        val energy = view.findViewById<Slider>(R.id.sliderEnergy).value.toInt()
        val sleep = view.findViewById<Slider>(R.id.sliderSleep).value.toInt()
        val hunger = view.findViewById<Slider>(R.id.sliderHunger).value.toInt()

        // 1. Zápis do SharedPreferences, aby MacroCalculator hned viděl novou váhu
        val userPrefs = requireContext().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        userPrefs.edit().putString("weightAkt", weightVal.toString()).apply()

        thread {
            val db = AppDatabase.getDatabase(requireContext())
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

            val newCheckIn = CheckInEntity(
                date = today,
                weight = weightVal,
                energyLevel = energy,
                sleepQuality = sleep,
                hungerLevel = hunger
            )

            db.checkInDao().insertCheckIn(newCheckIn)

            activity?.runOnUiThread {
                // 2. Aktualizace grafů a maker
                displayMacros(view)
                // 3. Okamžitá aktualizace trenéra se všemi parametry
                updateCoachUI(weightVal, energy, sleep, hunger)

                Toast.makeText(context, "Váha ${weightVal}kg uložena!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadCoachAdvice(view: View) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        thread {
            val db = AppDatabase.getDatabase(requireContext())
            val todayCheckIn = db.checkInDao().getCheckInByDateSync(today)
            val allCheckIns = db.checkInDao().getAllCheckInsSync()

            activity?.runOnUiThread {
                if (todayCheckIn != null) {
                    // Načtení z databáze se všemi 4 parametry
                    updateCoachUI(
                        todayCheckIn.weight,
                        todayCheckIn.energyLevel,
                        todayCheckIn.sleepQuality,
                        todayCheckIn.hungerLevel
                    )
                } else {
                    // Pokud dnes ještě není zápis, vezmeme poslední známou váhu
                    val lastWeight = allCheckIns.firstOrNull()?.weight ?: 83.0
                    view.findViewById<EditText>(R.id.etWeight)?.setText(lastWeight.toString())
                    view.findViewById<TextView>(R.id.tvCoachMessage).text = "Ještě jsi neudělal ranní rituál. Klikni zde!"
                }
            }
        }
    }

    // LOGICKÁ FUNKCE TRENÉRA
    private fun updateCoachUI(weight: Double, energy: Int, sleep: Int, hunger: Int) {
        val tvMessage = view?.findViewById<TextView>(R.id.tvCoachMessage) ?: return

        tvMessage.text = when {
            // Scénář: Spánek OK, ale únava a hlad -> Málo jídla
            sleep >= 4 && energy <= 3 && hunger >= 4 ->
                "Spánek byl top, ale motor je prázdný. Tvých ${weight} kg potřebuje dnes víc paliva! 🍝"

            // Scénář: Málo spánku, ale hrotíš to (kofeinový dluh)
            sleep <= 2 && energy >= 4 ->
                "Jedeš na dluh! Energie tam je, ale tělo ${weight} kg po špatné noci v šoku. Dneska bez kofeinu odpoledne! ☕🚫"

            // Scénář: Extrémní hlad
            hunger >= 5 ->
                "Pozor na vlčí hlad! Těch ${weight} kg dneska potřebuje pořádný objem jídla a bílkovin. 🥩"

            // Scénář: Totální vyčerpání (kombinace spánku a energie)
            energy <= 2 && sleep <= 2 ->
                "Krizový režim. Tělo ${weight} kg dneska potřebuje úplný rest a regeneraci. 🛌"

            // Scénář: Všechno skvělé
            energy >= 4 && sleep >= 4 ->
                "Ideální konstelace! Tvých ${weight} kg je připraveno na rekordy. Rozbij to! 🔥"

            else -> "Váha ${weight} kg v normě. Sleduj svůj hlad a drž se plánu!"
        }
    }

    private fun displayMacros(view: View) {
        val data = MacroCalculator.calculate(requireContext())

        // Nastavení textových hodnot
        view.findViewById<TextView>(R.id.tvCalories).text = "${data.calories.toInt()}"
        view.findViewById<TextView>(R.id.tvValueProtein).text = "${data.protein.toInt()}g"
        view.findViewById<TextView>(R.id.tvValueCarbs).text = "${data.carbs.toInt()}g"
        view.findViewById<TextView>(R.id.tvValueFat).text = "${data.fat.toInt()}g"
        view.findViewById<TextView>(R.id.tvTrainingStatus).text = "DNES: ${data.trainingType.uppercase()}"

        val tvWater = view.findViewById<TextView>(R.id.tvWaterValue)
        if (tvWater != null) {
            tvWater.text = String.format(Locale.US, "%.1f L", data.water)
        }

        // Animace kruhových progressbarů
        val pbF = view.findViewById<ProgressBar>(R.id.progressFat)
        val pbC = view.findViewById<ProgressBar>(R.id.progressCarbs)
        val pbP = view.findViewById<ProgressBar>(R.id.progressProtein)

        val pCal = data.protein * 4; val cCal = data.carbs * 4; val fCal = data.fat * 9
        val total = pCal + cCal + fCal

        if (total > 0) {
            val fProp = (fCal / total).toFloat()
            val cProp = (cCal / total).toFloat()

            val startAngle = -90f
            pbF.rotation = startAngle
            pbC.rotation = startAngle - (fProp * 360f)
            pbP.rotation = startAngle - (fProp * 360f) - (cProp * 360f)

            pbF.max = 1000; pbC.max = 1000; pbP.max = 1000
            ObjectAnimator.ofInt(pbF, "progress", 0, (fProp * 1000).toInt()).setDuration(800).start()
            ObjectAnimator.ofInt(pbC, "progress", 0, (cProp * 1000).toInt()).setDuration(1000).start()
            ObjectAnimator.ofInt(pbP, "progress", 0, (1000 - (fProp * 1000 + cProp * 1000).toInt())).setDuration(1200).start()
        }
    }
}