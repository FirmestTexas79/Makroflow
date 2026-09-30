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
        "camp" to "tábor", "cave" to "jeskyni",
        "jezirko_1" to "tiché jezírko", "houstina" to "houštinu", "stary_dub" to "Starý dub",
        "mural_1" to "západní kámen", "mural_2" to "východní kámen", "mural_3" to "severní kámen u oltáře"
    )

    private val biomeNames = mapOf("MOUNTAINS" to "v horách", "FOREST" to "ve Hvozdu", "MEADOW" to "na louce", "TOWN" to "ve městě")

    /** Kde se souboj fáze BATTLE_BIOME počítá (pro deník a připomínku). */
    fun biomeLabel(stage: QuestStage): String = biomeNames[stage.targetId] ?: "v této lokaci"

    /** Předměty fáze DELIVER_ITEMS: "berry_blue:5,log_birch:5" → [(berry_blue, 5), (log_birch, 5)]. */
    fun deliveryItems(stage: QuestStage): List<Pair<String, Int>> =
        stage.targetId.orEmpty().split(",").mapNotNull { part ->
            val bits = part.trim().split(":")
            val id = bits.getOrNull(0)?.trim().orEmpty()
            val n = bits.getOrNull(1)?.trim()?.toIntOrNull() ?: 1
            if (id.isEmpty() || n <= 0) null else id to n
        }

    /** Chybějící kusy podle toho, co hráč má; prázdné = může odevzdat. */
    fun missingItems(stage: QuestStage, owned: Map<String, Int>): List<Pair<String, Int>> =
        deliveryItems(stage).mapNotNull { (id, n) -> val lack = n - (owned[id] ?: 0); if (lack > 0) id to lack else null }

    private fun itemLabel(id: String): String = cz.uhk.macroflow.pokemon.skills.Resource.from(id)?.label ?: id

    /** Seznam předmětů s tím, kolik hráč má: „5× Modrá bobule (máš 2)“. */
    fun deliveryText(stage: QuestStage, owned: Map<String, Int>?): String =
        deliveryItems(stage).joinToString(", ") { (id, n) ->
            "$n× ${itemLabel(id)}" + (owned?.let { " (máš ${minOf(it[id] ?: 0, n)})" } ?: "")
        }

    /** Vypito v % osobního cíle vody (zaokrouhleno dolů); cíl ≤ 0 → 0 %. */
    fun waterPercent(drankMl: Int, targetMl: Int): Int =
        if (targetMl <= 0) 0 else (drankMl.toLong() * 100 / targetMl).toInt().coerceAtLeast(0)

    /**
     * Krátká připomínka, co ještě chybí – NPC ji řekne, když s ním hráč mluví podruhé.
     * (Dřív se pokaždé přehrál celý úvodní dialog fáze.)
     */
    fun reminder(stage: QuestStage, metadata: String): String {
        val v = currentValue(stage, metadata)
        stage.hint?.let { return it }
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
            RequirementType.BATTLE_BIOME -> "Výhry ${biomeLabel(stage)}: $v z ${stage.targetValue}. Ještě chvíli!"
            RequirementType.HIT_WATER -> "Dnes máš vypito $v % svého cíle vody. Potřebuju celých ${stage.targetValue} % – zapisuj vodu v aplikaci."
            RequirementType.DELIVER_ITEMS -> "Přines mi: ${deliveryText(stage, null)}."
            RequirementType.STORY_FLAG -> "Ještě to není hotové. Co přesně tě čeká, najdeš v deníku u téhle kapitoly."
            RequirementType.SCAN_BARCODE -> "Pořád čekám na čárový kód! Naskenuj ho u jídla v sekci Jídlo."
            RequirementType.HIT_TARGET -> {
                val n = Adherence.Nutrient.from(stage.targetId)
                if (stage.targetId == ALL_MACROS) "Dnes máš trefená $v ze 3 maker. Potřebuju bílkoviny, sacharidy i tuky – všechno v jednom dni!"
                else if (n == null) stage.text
                else "Dnes máš ${n.label} na $v % svého cíle. Potřebuješ ${n.minPct}–${n.maxPct} %."
            }
            RequirementType.TALK_TO_NPC -> stage.text
            RequirementType.HAVE_ITEM -> "Ještě ho nemáš. Až ho budeš mít, stav se."
            RequirementType.AFK_MINUTES -> "Nejméně máš zatím odpracováno ${clock(v)} z ${clock(stage.targetValue)}. " +
                "Počítá se kácení, těžba i chytání hmyzu – každé zvlášť."
        }
    }

    /** Minuty jako „2 h 5 min“ (AFK_MINUTES). */
    fun clock(minutes: Int): String {
        val h = minutes.coerceAtLeast(0) / 60; val m = minutes.coerceAtLeast(0) % 60
        return when { h > 0 && m > 0 -> "$h h $m min"; h > 0 -> "$h h"; else -> "$m min" }
    }

    /** Dovednosti fáze AFK_MINUTES (Skill.id). */
    fun afkSkills(stage: QuestStage): List<String> =
        stage.targetId.orEmpty().split(",").map { it.trim() }.filter { it.isNotEmpty() }

    /** Postup AFK_MINUTES = minuty nejslabší dovednosti. */
    fun afkProgress(minutesBySkill: Map<String, Int>, stage: QuestStage): Int =
        afkSkills(stage).minOfOrNull { minutesBySkill[it] ?: 0 } ?: 0

    /** Metadata, se kterými je fáze splněná (debug „splnit fázi“). */
    fun satisfyingMetadata(stage: QuestStage): String = when (stage.requirementType) {
        RequirementType.VISIT_NODE -> stage.targetId.orEmpty()
        RequirementType.HIT_TARGET -> if (stage.targetId == ALL_MACROS) "3" else "100"
        else -> stage.targetValue.toString()
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
