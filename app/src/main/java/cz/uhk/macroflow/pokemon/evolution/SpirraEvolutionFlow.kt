package cz.uhk.macroflow.pokemon.evolution

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Kontrola vývoje aktivní Spirry (docs/adr/0031, 0055): zapíše dnešek do pouta, a když má
 * Spirra level 12+ a splněnou aspoň jednu cestu, spustí vývojovou animaci do té, kterou
 * splnila první. Volá se po návratu do aplikace i po souboji na mapě (tam se nejčastěji zvedne level).
 */
object SpirraEvolutionFlow {

    private var showing = false

    fun check(activity: AppCompatActivity, onDone: () -> Unit = {}) {
        val app = activity.applicationContext
        activity.lifecycleScope.launch {
            val found = withContext(Dispatchers.IO) {
                runCatching {
                    val sp = SpirraBond.activeSpirra(app) ?: return@runCatching null
                    SpirraBond.markToday(app, sp.id)
                    val branch = SpirraEvolution.evolveInto(SpirraBond.days(app, sp.id), sp.level) ?: return@runCatching null
                    sp to branch
                }.getOrNull()
            } ?: return@launch
            if (showing || activity.isFinishing || activity.isDestroyed) return@launch
            showing = true
            val (spirra, branch) = found
            val move = cz.uhk.macroflow.pokemon.MakromonGrowthManager.getNewMoveForLevel(branch.id, 1)
            cz.uhk.macroflow.pokemon.EvolutionDialog(
                context = activity,
                capturedMakromonId = spirra.id,
                oldId = SpirraEvolution.SPIRRA_ID,
                newId = branch.id,
                newMoveToLearn = move
            ) {
                activity.lifecycleScope.launch(Dispatchers.IO) { SpirraBond.clear(app, spirra.id) }
                showing = false
                onDone()
            }.show()
        }
    }
}
