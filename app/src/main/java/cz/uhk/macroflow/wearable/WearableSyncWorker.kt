package cz.uhk.macroflow.wearable

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Hodinová synchronizace na pozadí: Garmin klíč funguje vždy, Health Connect jen s povolením čtení na pozadí.
 * Chyba nikdy neshodí práci, další běh to zkusí znovu.
 */
class WearableSyncWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        runCatching { WearableHub.runIfStale(applicationContext, 30 * 60_000L) }
        Result.success()
    }

    companion object {
        private const val NAME = "wearable_sync"
        /** Bezpečné volat opakovaně: už naplánovaná práce se nemění. */
        fun schedule(ctx: Context) {
            if (!WearableHub.hasAny(ctx)) { WorkManager.getInstance(ctx).cancelUniqueWork(NAME); return }
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<WearableSyncWorker>(1, TimeUnit.HOURS).build())
        }
    }
}
