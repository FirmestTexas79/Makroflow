package cz.uhk.macroflow.pokemon.quests

import cz.uhk.macroflow.pokemon.QuestProgressEntity

/**
 * Čistá (bezstavová, bez Androidu) logika postupu questem.
 * Veškerá pravidla „kdy je fáze splněná a co se stane potom“ jsou tady,
 * aby šla pokrýt unit testy – QuestManager už jen dodává data a ukládá výsledek.
 */
object QuestProgression {

    /** Kolik z cíle fáze je splněno podle metadat (pro deník i vyhodnocení). */
    fun currentValue(stage: QuestStage, metadata: String): Int = when (stage.requirementType) {
        RequirementType.VISIT_NODE -> visitedNodes(metadata).size
        else -> metadata.toIntOrNull() ?: 0
    }

    fun isStageSatisfied(stage: QuestStage, metadata: String): Boolean =
        currentValue(stage, metadata) >= stage.targetValue

    /**
     * Hodnota odvozené výživové fáze z dnešních součtů, null pokud fáze není výživová.
     * Gramy se zaokrouhlují dolů – „80 g“ znamená skutečně aspoň 80 g.
     */
    fun nutritionValue(stage: QuestStage, totals: NutritionTotals): Int? = when (stage.requirementType) {
        RequirementType.LOG_CALORIES -> totals.kcal
        RequirementType.LOG_MACROS -> when (stage.targetId) {
            "protein" -> totals.proteinG.toInt()
            "carbs" -> totals.carbsG.toInt()
            "fat" -> totals.fatG.toInt()
            else -> null
        }
        else -> null
    }

    fun visitedNodes(metadata: String): Set<String> =
        metadata.split(",").map { it.trim() }.filter { it.isNotEmpty() && it.toIntOrNull() == null }.toSet()

    /**
     * Zapíše nová metadata do aktuální fáze a případně quest posune.
     * @return nový stav + příznak, zda se fáze právě dokončila.
     */
    fun apply(
        progress: QuestProgressEntity,
        quest: QuestDefinition,
        newMetadata: String,
        now: Long
    ): Result {
        if (progress.isCompleted) return Result(progress, stageCompleted = false)
        val stage = quest.stages.getOrNull(progress.currentStageIndex)
            ?: return Result(progress, stageCompleted = false)

        if (!isStageSatisfied(stage, newMetadata)) {
            return Result(progress.copy(metadata = newMetadata, lastUpdated = now), stageCompleted = false)
        }

        val nextIndex = progress.currentStageIndex + 1
        val advanced = progress.copy(
            currentStageIndex = nextIndex,
            isCompleted = nextIndex >= quest.stages.size,
            metadata = "0",
            lastUpdated = now,
            stageStartedAt = now
        )
        return Result(advanced, stageCompleted = true)
    }

    data class Result(val progress: QuestProgressEntity, val stageCompleted: Boolean)
}
