package cz.uhk.macroflow.nutrition

import android.content.Context
import android.util.Log
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.data.ConsumedSnackEntity
import cz.uhk.macroflow.data.FirebaseRepository
import cz.uhk.macroflow.data.SnackUsageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Jediné místo, kudy jde jídlo do deníku: uloží záznam lokálně, pošle ho do cloudu a započítá
 * oblíbenost. Dřív Snacky a Složit jídlo ukládaly jen lokálně (na rozdíl od swipe výběru), takže
 * se po přeinstalaci nebo na jiném zařízení ztratily.
 */
object FoodLog {

    /** Zapíše snědenou potravinu; vrací timestamp záznamu (klíč pro „Zpět“). */
    suspend fun add(
        context: Context,
        name: String,
        scaled: SnackCatalog.Scaled,
        countUsageOf: String? = name
    ): Long = withContext(Dispatchers.IO) {
        val now = Date()
        val entity = ConsumedSnackEntity(
            timestamp = System.currentTimeMillis(),
            date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(now),
            time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now),
            name = name,
            p = scaled.p, s = scaled.s, t = scaled.t,
            calories = scaled.kcal, energyKj = scaled.kj, fiber = scaled.fiber
        )
        insert(context, entity)
        countUsageOf?.let { incrementUsage(context, it) }
        entity.timestamp
    }

    /** Uloží hotový záznam (např. složené jídlo) lokálně i do cloudu. */
    suspend fun insert(context: Context, entity: ConsumedSnackEntity) = withContext(Dispatchers.IO) {
        AppDatabase.getDatabase(context).consumedSnackDao().insertConsumed(entity)
        cz.uhk.macroflow.widget.MacroWidget.refresh(context)
        if (FirebaseRepository.isLoggedIn) {
            try { FirebaseRepository.uploadConsumedSnack(entity) }
            catch (e: Exception) { Log.e("FoodLog", "Sync záznamu selhal: ${e.message}") }
        }
    }

    /** Vrátí zápis (tlačítko „Zpět“). Oblíbenost se nevrací – jde jen o řazení. */
    suspend fun remove(context: Context, timestamp: Long) = withContext(Dispatchers.IO) {
        AppDatabase.getDatabase(context).consumedSnackDao().deleteConsumedByTimestamp(timestamp)
        cz.uhk.macroflow.widget.MacroWidget.refresh(context)
        if (FirebaseRepository.isLoggedIn) {
            try { FirebaseRepository.deleteConsumedSnack(timestamp) }
            catch (e: Exception) { Log.e("FoodLog", "Smazání v cloudu selhalo: ${e.message}") }
        }
    }

    private fun incrementUsage(context: Context, snackName: String) {
        val dao = AppDatabase.getDatabase(context).snackDao()
        val now = System.currentTimeMillis()
        val existing = dao.getUsageStats(snackName)
        dao.updateUsageMetadata(
            existing?.copy(usageCount = existing.usageCount + 1, lastUsedTimestamp = now)
                ?: SnackUsageEntity(snackName, 1, now)
        )
    }
}
