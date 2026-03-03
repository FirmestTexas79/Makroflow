package cz.uhk.macroflow

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
        val btnSave = view.findViewById<MaterialButton>(R.id.btnSaveCheckIn)

        etWeight.setText("83.0") // Předvyplněno po tvém objemu [cite: 2026-02-02]

        btnSave.setOnClickListener {
            val weight = etWeight.text.toString().toDoubleOrNull() ?: 83.0
            val energy = sliderEnergy.value.toInt()
            val sleep = sliderSleep.value.toInt()

            saveToDatabase(weight, energy, sleep)
        }
    }

    private fun saveToDatabase(weight: Double, energy: Int, sleep: Int) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val today = sdf.format(Date())

        val checkIn = CheckInEntity(
            date = today,
            weight = weight,
            energyLevel = energy,
            sleepQuality = sleep,
            hungerLevel = 3
        )

        thread {
            try {
                val db = AppDatabase.getDatabase(requireContext())
                db.checkInDao().insertCheckIn(checkIn)

                activity?.runOnUiThread {
                    Toast.makeText(requireContext(), "Rituál uložen!", Toast.LENGTH_SHORT).show()
                    // ZAVŘENÍ FRAGMENTU A NÁVRAT NA DASHBOARD [cite: 2026-03-01]
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