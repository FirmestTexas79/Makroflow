package cz.uhk.macroflow.common

import android.util.Log
import android.widget.Toast
import androidx.lifecycle.LifecycleCoroutineScope
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.data.FirebaseRepository
import cz.uhk.macroflow.pokemon.EvolutionDialog
import cz.uhk.macroflow.pokemon.MakromonGrowthManager
import cz.uhk.macroflow.pokemon.Move
import cz.uhk.macroflow.pokemon.PokemonLevelCalc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * MakromonXpController — řídí veškerou XP logiku aktivního Makromona.
 *
 * Zodpovědnosti:
 *   - Denní XP odměna (20 XP jednou za den při spuštění aplikace)
 *   - Real-time přidání XP (po check-inu, tréninku, splnění cíle)
 *   - Detekce level-upu a zobrazení EvolutionDialogu pokud je splněna podmínka
 *   - Upload změn na Firebase
 *
 * Obě metody jsou volány z MainActivity:
 *   - awardDailyXp() → voláme v onResume()
 *   - addXpRealTime(amount) → voláme z fragmentů přes (activity as MainActivity)
 */
class MakromonXpController(
    private val activity: MainActivity,
    private val lifecycleScope: LifecycleCoroutineScope
) {

    /**
     * Udělí 20 XP aktivnímu Makromonovi jednou za kalendářní den.
     * Použije SharedPrefs klíč "lastXpDay_{capturedId}" jako pojistku.
     * Pokud Makromon dosáhne úrovně evoluce, automaticky zobrazí EvolutionDialog.
     */
    fun awardDailyXp() {
        val prefs = activity.getSharedPreferences("GamePrefs", android.content.Context.MODE_PRIVATE)
        val activeCapturedId = prefs.getInt("currentOnBarCapturedId", -1)
        if (activeCapturedId == -1) return

        val today   = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        val lastDay = prefs.getInt("lastXpDay_$activeCapturedId", -1)
        if (today == lastDay) return

        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(activity)
            val makromon = db.capturedMakromonDao().getMakromonById(activeCapturedId) ?: return@launch

            val oldLevel = makromon.level
            makromon.xp += 20
            val newLevel = PokemonLevelCalc.levelFromXp(makromon.xp)
            makromon.level = newLevel

            db.capturedMakromonDao().updateMakromon(makromon)
            prefs.edit().putInt("lastXpDay_$activeCapturedId", today).apply()

            if (FirebaseRepository.isLoggedIn) {
                FirebaseRepository.uploadCapturedMakromon(makromon)
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(activity, "🎉 ${makromon.name} získal 20 XP!", Toast.LENGTH_SHORT).show()

                // Zkontrolujeme evolveLevel z Makrodexu
                val entry = withContext(Dispatchers.IO) {
                    db.makrodexEntryDao().getEntry(makromon.makromonId)
                }
                if (entry != null && entry.evolveLevel > 0
                    && newLevel >= entry.evolveLevel && newLevel > oldLevel) {

                    val growthProfile = MakromonGrowthManager.getProfile(makromon.makromonId)
                    val targetEvolveId = growthProfile?.evolutionToId ?: entry.evolveToId
                    val nextMove = MakromonGrowthManager.getNewMoveForLevel(targetEvolveId, entry.evolveLevel)
                        ?: MakromonGrowthManager.getNewMoveForLevel(targetEvolveId, 1)

                    showEvolutionDialog(makromon.id, makromon.makromonId, targetEvolveId, nextMove)
                }
            }
        }
    }

    /**
     * Okamžitě přidá zadané množství XP aktivnímu Makromonovi.
     * Volá se z fragmentů po splnění cíle (check-in, trénink, splnění maker).
     *
     * @param xpAmount Počet XP k přidání (závisí na typu akce, viz MakromonXpEngine)
     */
    fun addXpRealTime(xpAmount: Int) {
        val prefs = activity.getSharedPreferences("GamePrefs", android.content.Context.MODE_PRIVATE)
        val activeCapturedId = prefs.getInt("currentOnBarCapturedId", -1)
        if (activeCapturedId == -1) return

        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(activity)
            val makromon = db.capturedMakromonDao().getMakromonById(activeCapturedId) ?: return@launch

            val oldLevel = makromon.level
            makromon.xp += xpAmount
            val newLevel = PokemonLevelCalc.levelFromXp(makromon.xp)
            makromon.level = newLevel

            db.capturedMakromonDao().updateMakromon(makromon)

            if (FirebaseRepository.isLoggedIn) {
                try {
                    FirebaseRepository.uploadCapturedMakromon(makromon)
                } catch (e: Exception) {
                    Log.e("XP_CONTROLLER", "Chyba uploadu: ${e.message}")
                }
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(activity, "🎉 ${makromon.name} získal $xpAmount XP!", Toast.LENGTH_SHORT).show()

                // Zkontrolujeme evolveLevel přes MakromonGrowthManager
                val entry = withContext(Dispatchers.IO) {
                    db.makrodexEntryDao().getEntry(makromon.makromonId)
                }
                if (entry != null && entry.evolveLevel > 0
                    && newLevel >= entry.evolveLevel && newLevel > oldLevel) {

                    val growthProfile  = MakromonGrowthManager.getProfile(makromon.makromonId)
                    val targetEvolveId = growthProfile?.evolutionToId ?: ""

                    val nextMove = MakromonGrowthManager.getNewMoveForLevel(targetEvolveId, entry.evolveLevel)
                        ?: MakromonGrowthManager.getNewMoveForLevel(targetEvolveId, 1)

                    showEvolutionDialog(makromon.id, makromon.makromonId, targetEvolveId, nextMove)
                }
            }
        }
    }

    /**
     * Zobrazí EvolutionDialog a po jeho zavření refreshne Makromona na liště.
     */
    private fun showEvolutionDialog(
        capturedId: Int,
        oldId: String,
        newId: String,
        newMove: Move?
    ) {
        EvolutionDialog(
            context           = activity,
            capturedMakromonId = capturedId,
            oldId             = oldId,
            newId             = newId,
            newMoveToLearn    = newMove
        ) {
            activity.updateMakromonVisibility()
        }.show()
    }
}