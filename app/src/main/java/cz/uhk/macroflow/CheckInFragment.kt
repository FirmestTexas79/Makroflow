package cz.uhk.macroflow

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.thread

class CheckInFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_checkin, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val etWeight = view.findViewById<EditText>(R.id.etCheckInWeight)
        val sliderEnergy = view.findViewById<Slider>(R.id.sliderEnergy)
        val sliderSleep = view.findViewById<Slider>(R.id.sliderSleep)
        val sliderHunger = view.findViewById<Slider>(R.id.sliderHunger)
        val btnSave = view.findViewById<MaterialButton>(R.id.btnSaveCheckIn)

        // Načteme aktuální váhu z profilu pro usnadnění
        val userPrefs = requireContext().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        etWeight.setText(userPrefs.getString("weightAkt", "83.0"))

        btnSave.setOnClickListener {
            val weight = etWeight.text.toString().toDoubleOrNull() ?: 83.0
            val energy = sliderEnergy.value.toInt()
            val sleep = sliderSleep.value.toInt()
            val hunger = sliderHunger.value.toInt()

            // 1. Uložíme váhu do globálního profilu
            userPrefs.edit().putString("weightAkt", weight.toString()).apply()

            // 2. Uložíme denní záznam do DB
            saveToDatabase(weight, energy, sleep, hunger)
        }
    }

    private fun saveToDatabase(weight: Double, energy: Int, sleep: Int, hunger: Int) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val today = sdf.format(Date())

        val checkIn = CheckInEntity(
            date = today,
            weight = weight,
            energyLevel = energy,
            sleepQuality = sleep,
            hungerLevel = hunger
        )

        thread {
            try {
                val db = AppDatabase.getDatabase(requireContext())
                db.checkInDao().insertCheckIn(checkIn)

                activity?.runOnUiThread {
                    Toast.makeText(requireContext(), "Rituál i váha v profilu uloženy!", Toast.LENGTH_SHORT).show()
                    parentFragmentManager.popBackStack()
                }
            } catch (e: Exception) {
                activity?.runOnUiThread {
                    Toast.makeText(requireContext(), "Chyba: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}