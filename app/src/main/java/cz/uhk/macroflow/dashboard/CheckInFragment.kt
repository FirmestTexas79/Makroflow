package cz.uhk.macroflow.dashboard

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_checkin, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val etWeight = view.findViewById<EditText>(R.id.etCheckInWeight)
        val userPrefs = requireContext().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)

        // Načti dnešní váhu z DB (priorita), fallback na SharedPrefs
        lifecycleScope.launch(Dispatchers.IO) {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val db = AppDatabase.Companion.getDatabase(requireContext())
            val todayCheckIn = db.checkInDao().getCheckInByDateSync(today)
            withContext(Dispatchers.Main) {
                val weightToShow = todayCheckIn?.weight?.toString()
                    ?: userPrefs.getString("weightAkt", "83.0")
                etWeight.setText(weightToShow)
            }
        }

        view.findViewById<MaterialButton>(R.id.btnSaveCheckIn).setOnClickListener {
            val weight = etWeight.text.toString().toDoubleOrNull() ?: 83.0
            val energy = view.findViewById<Slider>(R.id.sliderEnergy).value.toInt()
            val sleep = view.findViewById<Slider>(R.id.sliderSleep).value.toInt()
            val hunger = view.findViewById<Slider>(R.id.sliderHunger).value.toInt()

            // 1. Zápis do Prefs (pro kalkulačku)
            userPrefs.edit().putString("weightAkt", weight.toString()).apply()

            // 2. Zápis do DB
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
            val db = AppDatabase.Companion.getDatabase(requireContext())

            // 1. Zápis do lokálního Roomu
            db.checkInDao().insertCheckIn(checkInEntity)

            // 2. ✅ Okamžitý zápis do Firebase Cloudu
            if (FirebaseRepository.isLoggedIn) {
                try {
                    FirebaseRepository.uploadCheckIn(checkInEntity)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(
                    requireContext(),
                    "Rituál uložen lokálně i v cloudu!",
                    Toast.LENGTH_SHORT
                ).show()
                parentFragmentManager.popBackStack()
            }
        }
    }
}