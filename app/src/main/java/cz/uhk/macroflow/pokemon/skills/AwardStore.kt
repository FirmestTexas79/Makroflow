package cz.uhk.macroflow.pokemon.skills

import android.content.Context
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.pokemon.Rarity
import cz.uhk.macroflow.pokemon.SpawnManager

/**
 * Ocenění (docs/adr/0037): počítadla a splněná ocenění leží v user_items, takže se zálohují
 * spolu se vším ostatním. `stat_*` = počítadla, `award_<id>` = den splnění (dny od 1970).
 * U chytání se bere větší z počítadla a toho, co je v tabulce chycených – starší úlovky se tak
 * započítají i bez počítadla. Volat mimo hlavní vlákno.
 */
object AwardStore {
    const val CAUGHT = "stat_caught"
    const val SHINY = "stat_shiny"
    const val LEGEND = "stat_legend"
    const val CRAFTED = "stat_crafted"
    const val HARVESTED = "stat_harvested"
    const val HARVESTED_BLACK = "stat_harvest_black"
    const val DAILY = "stat_daily_claimed"
    const val VISIT_FOREST = "stat_visit_forest"
    fun gatheredId(resourceItemId: String) = "stat_gather_$resourceItemId"

    private fun isLegend(makromonId: String) =
        SpawnManager.allEntries.firstOrNull { it.id == makromonId }?.rarity.let { it == Rarity.LEGENDARY || it == Rarity.MYTHIC }

    fun recordCatch(ctx: Context, makromonId: String, shiny: Boolean) {
        SkillStore.add(ctx, CAUGHT, 1)
        if (shiny) SkillStore.add(ctx, SHINY, 1)
        if (isLegend(makromonId)) SkillStore.add(ctx, LEGEND, 1)
    }

    fun recordOnce(ctx: Context, itemId: String) {
        if (SkillStore.count(ctx, itemId) <= 0) SkillStore.add(ctx, itemId, 1)
    }

    fun facts(ctx: Context): AwardFacts {
        val db = AppDatabase.getDatabase(ctx)
        val all = SkillStore.counts(ctx)
        val st = SkillStore.state(ctx)
        val caught = runCatching { db.capturedMakromonDao().getAllCaught() }.getOrDefault(emptyList())
        val dex = runCatching { db.makrodexStatusDao().getUnlockedIds() }.getOrDefault(emptyList())
        val speciesAll = SpawnManager.allEntries.map { it.id }.toSet()
        val quests = runCatching {
            cz.uhk.macroflow.pokemon.cave.ForestMap.completedTasks(db.questDao().getAllQuests().map { p ->
                val total = cz.uhk.macroflow.pokemon.quests.QuestRegistry.byId(p.questId)?.stages?.size ?: 0
                Triple(total, p.currentStageIndex, p.isCompleted)
            })
        }.getOrDefault(0)
        val gear = SkillStore.equippedAll(ctx)
        fun n(id: String) = all[id] ?: 0
        return AwardFacts(
            questTasks = quests,
            dailyClaimed = n(DAILY),
            skillLevels = Skill.entries.associateWith { st.level(it) },
            visitedForest = n(VISIT_FOREST) > 0,
            caught = maxOf(n(CAUGHT), caught.size),
            shinyCaught = maxOf(n(SHINY), caught.count { it.isShiny }),
            legendCaught = maxOf(n(LEGEND), caught.count { isLegend(it.makromonId) }),
            speciesCaught = ((caught.map { it.makromonId } + dex).toSet() intersect speciesAll).size,
            speciesTotal = speciesAll.size,
            teamSize = runCatching { SkillStore.team(ctx).size }.getOrDefault(0),
            crafted = n(CRAFTED),
            equipsFilled = gear.keys.count { it.tab == GearTab.EQUIPS },
            accessFilled = gear.keys.count { it.tab == GearTab.ACCESS },
            harvested = n(HARVESTED),
            harvestedBlack = n(HARVESTED_BLACK),
            plotsOpen = st.plotsOpen,
            gathered = Resource.entries.associate { it.itemId to n(gatheredId(it.itemId)) }
        )
    }

    /** Den splnění ocenění (dny od 1970), nebo null. */
    fun unlockedDays(ctx: Context): Map<String, Int> {
        val all = SkillStore.counts(ctx)
        return Awards.ALL.mapNotNull { a -> all[a.itemId]?.takeIf { it > 0 }?.let { a.id to it } }.toMap()
    }

    private fun today() = (System.currentTimeMillis() / 86_400_000L).toInt()

    /** Zapíše nově splněná ocenění a vrátí je (kvůli oznámení). */
    fun check(ctx: Context, f: AwardFacts = facts(ctx)): List<Award> {
        val have = unlockedDays(ctx)
        val fresh = Awards.ALL.filter { it.id !in have && Awards.isDone(it, f) }
        fresh.forEach { SkillStore.add(ctx, it.itemId, today()) }
        return fresh
    }
}
