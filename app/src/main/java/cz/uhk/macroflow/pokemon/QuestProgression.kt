package cz.uhk.macroflow.pokemon.quests

import cz.uhk.macroflow.energy.Adherence
import cz.uhk.macroflow.pokemon.QuestProgressEntity

/**
 * Čistá (bezstavová, bez Androidu) logika postupu questem.
 * Veškerá pravidla „kdy je fáze splněná a co se stane potom“ jsou tady,
 * aby šla pokrýt unit testy – QuestManager už jen dodává data a ukládá výsledek.
 */
object QuestProgression {

    /** HIT_TARGET se všemi třemi makry najednou; metadata = kolik z B/S/T je dnes trefeno (0–3). */
    const val ALL_MACROS = "macros"
    private val MACROS = listOf(Adherence.Nutrient.PROTEIN, Adherence.Nutrient.CARBS, Adherence.Nutrient.FAT)

    /** Kolik z cíle fáze je splněno podle metadat (pro deník i vyhodnocení). */
    fun currentValue(stage: QuestStage, metadata: String): Int = when (stage.requirementType) {
        RequirementType.VISIT_NODE -> visitedNodes(metadata).size
        else -> metadata.toIntOrNull() ?: 0
    }

    fun isStageSatisfied(stage: QuestStage, metadata: String): Boolean {
        if (stage.requirementType == RequirementType.HIT_TARGET) {
            if (stage.targetId == ALL_MACROS) return currentValue(stage, metadata) >= MACROS.size
            val n = Adherence.Nutrient.from(stage.targetId) ?: return false
            return Adherence.isHitPercent(n, currentValue(stage, metadata))
        }
        return currentValue(stage, metadata) >= stage.targetValue
    }

    /** Snědeno v % osobního cíle pro fázi HIT_TARGET; null pro jiné fáze. */
    fun targetPercent(stage: QuestStage, eaten: Adherence.Eaten, targets: Adherence.Targets): Int? {
        if (stage.requirementType != RequirementType.HIT_TARGET) return null
        if (stage.targetId == ALL_MACROS) return MACROS.count { Adherence.isHit(it, eaten, targets) }
        val n = Adherence.Nutrient.from(stage.targetId) ?: return null
        return Adherence.percent(n, eaten, targets)
    }

    private val nodeNames = mapOf(
        "domov" to "Domov", "pokedex" to "Makrodex", "obchod" to "Obchod",
        "camp" to "tábor", "cave" to "jeskyni"
    )

    /**
     * Krátká připomínka, co ještě chybí – NPC ji řekne, když s ním hráč mluví podruhé.
     * (Dřív se pokaždé přehrál celý úvodní dialog fáze.)
     */
    fun reminder(stage: QuestStage, metadata: String): String {
        val v = currentValue(stage, metadata)
        return when (stage.requirementType) {
            RequirementType.VISIT_NODE -> {
                val missing = (stage.targetId?.split(",")?.map { it.trim() } ?: emptyList()) - visitedNodes(metadata)
                if (missing.isEmpty()) "Už jsi všude byl – vrať se ke mně!"
                else "Ještě se podívej: " + missing.joinToString(", ") { nodeNames[it] ?: it } + "."
            }
            RequirementType.CAPTURE_SPECIFIC -> "Pořád tě někdo čeká v tom křoví! Běž se tam podívat."
            RequirementType.WALK_STEPS ->
                "Dnes máš $v kroků z ${stage.targetValue}. Ještě ${(stage.targetValue - v).coerceAtLeast(0)} – rozhýbej se!"
            RequirementType.LOG_MEAL -> "Dnes máš zapsáno $v jídel z ${stage.targetValue}. Zapiš je v sekci Jídlo."
            RequirementType.BATTLE_TYPE -> "Poraženo $v z ${stage.targetValue}. Pokračuj v soubojích!"
            RequirementType.BATTLE_BIOME -> "Výhry v horách: $v z ${stage.targetValue}. Ještě chvíli!"
            RequirementType.SCAN_BARCODE -> "Pořád čekám na čárový kód! Naskenuj ho u jídla v sekci Jídlo."
            RequirementType.HIT_TARGET -> {
                val n = Adherence.Nutrient.from(stage.targetId)
                if (stage.targetId == ALL_MACROS) "Dnes máš trefená $v ze 3 maker. Potřebuju bílkoviny, sacharidy i tuky – všechno v jednom dni!"
                else if (n == null) stage.text
                else "Dnes máš ${n.label} na $v % svého cíle. Potřebuješ ${n.minPct}–${n.maxPct} %."
            }
            RequirementType.TALK_TO_NPC -> stage.text
        }
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
