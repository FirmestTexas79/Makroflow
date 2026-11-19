package cz.uhk.macroflow.pokemon.story

import android.content.Context
import android.widget.FrameLayout
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.pokemon.cave.TowerLobby
import cz.uhk.macroflow.pokemon.skills.ui.WorkshopMenus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Věž Řádu Váhy (docs/adr/0103): Sept, Spis Subjektu a výtah. Data čte z DB mimo hlavní vlákno. */
object TowerUi {

    /** Pravidelnost za 30 dní + běžící kalibrace od [stageStartMs] (null = kalibrace neběží). */
    fun stats(ctx: Context, stageStartMs: Long?, calibrated: Boolean): Calibration.Stats {
        val db = AppDatabase.getDatabase(ctx)
        val today = LocalDate.now()
        val since = today.minusDays(29)
        val meals = db.consumedSnackDao().getConsumedSinceSync(since.toString()).map { it.date }.toSet()
        val rituals = db.checkInDao().getAllCheckInsSync().filter { it.date >= since.toString() && it.mood != "__demo__" }.map { it.date }.toSet()
        val goal = runCatching {
            cz.uhk.macroflow.settings.WaterGoal.ml(cz.uhk.macroflow.dashboard.MacroCalculator.calculateForDate(ctx, java.util.Date()).water)
        }.getOrDefault(2500)
        val water = db.waterDao().getWaterSinceSync(since.toString()).groupBy { it.date }.count { (_, l) -> l.sumOf { it.amountMl } >= goal }
        val steps = db.stepsDao().getAllStepsSync().filter { it.date >= since.toString() && it.count > 0 }.map { it.count }
        val streak = stageStartMs?.let {
            val start = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
            val logged = db.consumedSnackDao().getConsumedSinceSync(start.toString()).mapNotNull { m -> runCatching { LocalDate.parse(m.date) }.getOrNull() }.toSet()
            Calibration.streak(logged, start, today)
        }
        return Calibration.Stats(meals.size, rituals.size, water, if (steps.isEmpty()) 0 else steps.average().toInt(),
            MastersStore.trainingsLast7(ctx), streak, calibrated)
    }

    fun showSept(root: FrameLayout, owner: LifecycleOwner, hasInvite: Boolean, calibrated: Boolean, stageStartMs: Long?) {
        val ctx = root.context.applicationContext
        owner.lifecycleScope.launch {
            val st = withContext(Dispatchers.IO) { runCatching { stats(ctx, stageStartMs, calibrated) }.getOrNull() }
            val insight = withContext(Dispatchers.IO) { runCatching { StoryFlags.insight(ctx) }.getOrDefault(0) }
            val line = when {
                !hasInvite -> Calibration.SEPT_BEFORE
                calibrated -> Calibration.SEPT_DONE
                st?.streak != null -> Calibration.septProgress(st.streak)
                else -> Calibration.SEPT_START
            }
            WorkshopMenus.show(root, TowerLobby.SEPT_NAME, "Vestibul · Věž Řádu Váhy") { ui, body, close ->
                body.addView(ui.text(line, 16f))
                body.addView(ui.spacer(12f))
                if (hasInvite && st != null) body.addView(ui.button("Spis Subjektu") { close(); showFile(root, st, insight) })
            }
        }
    }

    fun showFile(root: FrameLayout, st: Calibration.Stats, insight: Int) {
        WorkshopMenus.show(root, "Spis Subjektu", "Okruh 213 · jen pro služební potřebu") { ui, body, _ ->
            body.addView(ui.text(Calibration.fileText(st, insight), 13f, 0xFF1E1A16.toInt()).apply {
                typeface = android.graphics.Typeface.MONOSPACE
                setBackgroundColor(0xFFF2ECDC.toInt())
                setPadding(ui.px(12f), ui.px(12f), ui.px(12f), ui.px(14f))
                setLineSpacing(ui.px(2f).toFloat(), 1f)
            })
        }
    }

    /** [onFloor1] = po kalibraci jede výtah do Archivu okruhů (1. patro); jinak jen ukazatel. */
    fun showElevator(root: FrameLayout, insight: Int, onFloor1: (() -> Unit)? = null) {
        val sub = if (onFloor1 != null) cz.uhk.macroflow.pokemon.cave.TowerArchive.ELEVATOR_READY else "Ručička ukazatele stojí na nule."
        WorkshopMenus.show(root, "Výtah", sub) { ui, body, close ->
            body.addView(ui.text(Calibration.elevatorText(insight), 15f).apply { typeface = android.graphics.Typeface.MONOSPACE })
            if (onFloor1 != null) body.addView(ui.button(cz.uhk.macroflow.pokemon.cave.TowerArchive.ELEVATOR_UP) { close(); onFloor1() })
        }
    }
}
