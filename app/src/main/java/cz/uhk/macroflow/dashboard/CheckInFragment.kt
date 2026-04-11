package cz.uhk.macroflow.dashboard

import android.content.Context
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
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.data.FirebaseRepository
import cz.uhk.macroflow.R
import cz.uhk.macroflow.data.CheckInEntity
import cz.uhk.macroflow.data.UserProfileEntity
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
        val appContext = requireContext().applicationContext

        // Načti váhu: Priorita dnešní záznam > Profil > Fallback
        lifecycleScope.launch(Dispatchers.IO) {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val db = AppDatabase.getDatabase(appContext)
            val todayCheckIn = db.checkInDao().getCheckInByDateSync(today)
            val profile = db.userProfileDao().getProfileSync()

            withContext(Dispatchers.Main) {
                val lastWeight = todayCheckIn?.weight ?: profile?.weight ?: 83.0
                etWeight.setText(lastWeight.toString())
            }
        }

        view.findViewById<MaterialButton>(R.id.btnSaveCheckIn).setOnClickListener {
            val weight = etWeight.text.toString().toDoubleOrNull() ?: 83.0
            val energy = view.findViewById<Slider>(R.id.sliderEnergy).value.toInt()
            val sleep  = view.findViewById<Slider>(R.id.sliderSleep).value.toInt()
            val hunger = view.findViewById<Slider>(R.id.sliderHunger).value.toInt()

            saveToDatabase(weight, energy, sleep, hunger)
        }
    }


    private fun saveToDatabase(weight: Double, energy: Int, sleep: Int, hunger: Int) {
        val appContext = requireContext().applicationContext
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
                val db = AppDatabase.getDatabase(appContext)

                // 1. Uložíme rituál
                db.checkInDao().insertCheckIn(checkInEntity)

                // 2. PROPSÁNÍ DO PROFILU (Hierarchie: Rituál aktualizuje Profil)
                val currentProfile = db.userProfileDao().getProfileSync() ?: UserProfileEntity(id = 1)
                val updatedProfile = currentProfile.copy(weight = weight)
                db.userProfileDao().saveProfile(updatedProfile)

                // 3. Analytika
                val history = db.checkInDao().getAllCheckInsSync()
                val analyticsResult = try {
                    cz.uhk.macroflow.analytics.BioLogicEngine.calculateFullAnalytics(history)
                } catch (e: Exception) { null }

                analyticsResult?.let { db.analyticsDao().insertAnalytics(it) }

                // 4. Cloud Sync
                if (FirebaseRepository.isLoggedIn) {
                    try {
                        FirebaseRepository.uploadCheckIn(checkInEntity)
                        FirebaseRepository.uploadProfile(updatedProfile)
                        analyticsResult?.let { FirebaseRepository.uploadAnalytics(it) }
                    } catch (e: Exception) { e.printStackTrace() }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(appContext, "Zápis dokončen a profil aktualizován!", Toast.LENGTH_SHORT).show()
                    parentFragmentManager.popBackStack()
                }
            } catch (e: Exception) {
                Log.e("CHECKIN", "Chyba: ${e.message}")
            }
        }
    }
}