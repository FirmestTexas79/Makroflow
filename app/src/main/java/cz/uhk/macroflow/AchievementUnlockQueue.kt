package cz.uhk.macroflow

import android.app.Activity
import android.app.Dialog
import android.os.Handler
import android.os.Looper

/**
 * Fronta achievementů čekající na zobrazení.
 *
 * Použití:
 *   AchievementUnlockQueue.enqueue(activity, listOfDefs)
 *
 * Fronta čeká IDLE_DELAY_MS ms po posledním "busy" signálu,
 * pak zobrazí dialogy jeden po druhém.
 *
 * Kdykoli je aktivita zaneprázdněna (zápis jídla, check-in apod.),
 * zavolej:  AchievementUnlockQueue.busy()
 */
object AchievementUnlockQueue {

    private const val IDLE_DELAY_MS = 3_000L   // čekej 3s po busy signálu
    private const val BETWEEN_DIALOGS_MS = 600L // pauza mezi dialogy

    private val handler = Handler(Looper.getMainLooper())
    private val queue = ArrayDeque<AchievementDef>()
    private var isShowingDialog = false
    private var lastBusyTime = 0L

    /** Zavolej když aplikace dělá něco důležitého (zápis, atd.) */
    fun busy() {
        lastBusyTime = System.currentTimeMillis()
    }

    /** Přidej nové achievementy do fronty a naplánuj zobrazení */
    fun enqueue(activity: Activity, defs: List<AchievementDef>) {
        if (defs.isEmpty()) return
        queue.addAll(defs)
        scheduleNext(activity)
    }

    private fun scheduleNext(activity: Activity) {
        if (isShowingDialog || queue.isEmpty()) return

        val elapsed = System.currentTimeMillis() - lastBusyTime
        val delay = if (elapsed < IDLE_DELAY_MS) IDLE_DELAY_MS - elapsed else 0L

        handler.postDelayed({
            showNext(activity)
        }, delay)
    }

    private fun showNext(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) {
            queue.clear()
            return
        }
        val def = queue.removeFirstOrNull() ?: return
        isShowingDialog = true

        val dialog = AchievementUnlockDialog(activity, def)
        dialog.setOnDismissListener {
            isShowingDialog = false
            if (queue.isNotEmpty()) {
                handler.postDelayed({ showNext(activity) }, BETWEEN_DIALOGS_MS)
            }
        }
        dialog.show()
    }
}