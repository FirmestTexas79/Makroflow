package cz.uhk.macroflow.dashboard

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import cz.uhk.macroflow.common.AppPreferences
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.data.FirebaseRepository
import cz.uhk.macroflow.R
import cz.uhk.macroflow.data.CheckInEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CheckInFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_checkin, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val etWeight = view.findViewById<EditText>(R.id.etCheckInWeight)

        // Načti dnešní váhu z DB, fallback na DataStore
        lifecycleScope.launch(Dispatchers.IO) {
            val today        = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val db           = AppDatabase.getDatabase(requireContext())
            val todayCheckIn = db.checkInDao().getCheckInByDateSync(today)
            val fallbackWeight = AppPreferences.getWeightAktSync(requireContext())
            withContext(Dispatchers.Main) {
                etWeight.setText(todayCheckIn?.weight?.toString() ?: fallbackWeight)
            }
        }

        view.findViewById<MaterialButton>(R.id.btnSaveCheckIn).setOnClickListener {
            val weight = etWeight.text.toString().toDoubleOrNull() ?: 83.0
            val energy = view.findViewById<Slider>(R.id.sliderEnergy).value.toInt()
            val sleep  = view.findViewById<Slider>(R.id.sliderSleep).value.toInt()
            val hunger = view.findViewById<Slider>(R.id.sliderHunger).value.toInt()
            lifecycleScope.launch(Dispatchers.IO) {
                AppPreferences.setWeightAkt(requireContext(), weight.toString())
            }
            saveToDatabase(weight, energy, sleep, hunger)
        }
    }

    private fun saveToDatabase(weight: Double, energy: Int, sleep: Int, hunger: Int) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val checkInEntity = CheckInEntity(
            date = today,
            weight = weight,
            energyLevel = energy,
            sleepQuality = sleep,
            hungerLevel = hunger
        )

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(requireContext())
                db.checkInDao().insertCheckIn(checkInEntity)

                // --- PŘIDÁNO: Výpočet a upload analytiky ---
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
                // --------------------------------------------

                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Zápis dokončen!", Toast.LENGTH_SHORT).show()
                    parentFragmentManager.popBackStack()
                }
            } catch (e: Exception) {
                Log.e("CHECKIN", "Chyba: ${e.message}")
            }
        }
    }
}