package cz.uhk.macroflow.pokemon.skills

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.random.Random

/**
 * Dovednosti Makrosvěta (docs/adr/0034) po vzoru Legends of IdleOn – svět 1 má tři:
 * Chytání, Výrobu a Pěstování. Čistý Kotlin, pokryto testy.
 */
enum class Skill(val id: String, val label: String, val verb: String) {
    CATCHING("catching", "Chytání", "chytáním Makromonů"),
    CRAFTING("crafting", "Výroba", "výrobou u pracovního stolu"),
    HARVESTING("harvesting", "Pěstování", "sklizní bobulí ze záhonů"),
    /** docs/adr/0035 – těží se krumpáčem v horách, i když jsi pryč (AFK). */
    MINING("mining", "Těžba", "těžbou rud krumpáčem"),
    LOGGING("logging", "Kácení", "kácením stromů sekerou");

    /** Předmět v user_items, jehož množství = celkové nasbírané XP (synchronizuje se s Firebase). */
    val xpItemId: String get() = "skill_xp_$id"

    companion object {
        fun from(id: String?): Skill? = entries.firstOrNull { it.id == id }
    }
}

object SkillMath {

    const val MAX_LEVEL = 200

    /**
     * XP potřebné z levelu [level] na další (vzorec profesí z IdleOn):
     * ⌊15 + 4·L + (1,5·L)^2,2 + 5·L·1,45^max(0, (L−20)/7)⌋
     */
    fun xpToNext(level: Int): Long {
        val l = level.toDouble()
        return floor(15 + 4 * l + (l * 1.5).pow(2.2) + 5 * l * 1.45.pow(max(0.0, (l - 20) / 7))).toLong()
    }

    data class Progress(val level: Int, val xpInLevel: Long, val xpNeeded: Long) {
        val fraction: Float get() = if (xpNeeded <= 0) 1f else (xpInLevel.toFloat() / xpNeeded).coerceIn(0f, 1f)
    }

    /** Level začíná na 1; [totalXp] = vše nasbírané od začátku. */
    fun progress(totalXp: Long): Progress {
        var level = 1
        var rest = totalXp.coerceAtLeast(0)
        while (level < MAX_LEVEL) {
            val need = xpToNext(level)
            if (rest < need) return Progress(level, rest, need)
            rest -= need; level++
        }
        return Progress(MAX_LEVEL, 0, 0)
    }

    fun levelOf(totalXp: Long): Int = progress(totalXp).level

    /** Celkové XP potřebné k dosažení levelu (level 1 = 0). */
    fun totalForLevel(level: Int): Long = (1 until level.coerceIn(1, MAX_LEVEL)).sumOf { xpToNext(it) }

    /** Levely, na kterých přibude dovednostní bod: 3, 6, 10, pak každých 5 (15, 20, 25…). */
    fun isSkillPointLevel(level: Int): Boolean = level == 3 || level == 6 || (level >= 10 && level % 5 == 0)

    fun skillPointsEarned(level: Int): Int = (2..level).count { isSkillPointLevel(it) }

    /** Další level, na kterém přibude bod. */
    fun nextSkillPointLevel(level: Int): Int = generateSequence(level + 1) { it + 1 }.first { isSkillPointLevel(it) }

    /**
     * Zisk XP: ⌊Základ × (1 + ΣAᵢ) × ΠMⱼ × Koeficient času⌋.
     * [additive] = procentní bonusy jako desetinná čísla (0,15 = +15 %) – nejdřív se sečtou;
     * [multipliers] = samostatné násobitele (2 = ×2) – násobí celý výsledek.
     */
    fun gain(base: Double, additive: List<Double> = emptyList(), multipliers: List<Double> = emptyList(), time: Double = 1.0): Int =
        // +1e-9: 100 × 1,15 = 114,999… by se jinak zaokrouhlilo na 114
        floor(base * (1 + additive.sum()) * multipliers.fold(1.0) { a, m -> a * m } * time + 1e-9).toInt().coerceAtLeast(0)

    /** Celkový násobitel XP (pro zobrazení): (1 + ΣA) × ΠM. */
    fun xpMultiplier(additive: List<Double>, multipliers: List<Double> = emptyList()): Double =
        (1 + additive.sum()) * multipliers.fold(1.0) { a, m -> a * m }

    /** Pasivní bonus za level: +1 % za každý level nad první (level 2 = 1 %), nejvýš 50 %. */
    fun passiveBonus(level: Int): Double = (0.01 * (level - 1)).coerceIn(0.0, 0.5)

    /**
     * Snížení šance jako v IdleOn: bonus se nebere z celkové hodnoty, ale z původní šance –
     * šance − šance × bonus (8 % s bonusem 10 % = 7,2 %, ne −2 %).
     */
    fun reduced(baseChance: Double, bonus: Double): Double = (baseChance - baseChance * bonus.coerceIn(0.0, 1.0)).coerceAtLeast(0.0)

    /** Hod na dvojitý výsledek (multicraft / multiharvest). */
    fun rollDouble(chance: Double, rng: Random = Random.Default): Int = if (rng.nextDouble() < chance) 2 else 1
}

/**
 * Strom dovedností: uzly za dovednostní body. Body se počítají pro každou dovednost zvlášť.
 */
object SkillTree {

    sealed class Effect {
        /** Místo v aktivním týmu (výchozí je jedno, celkem až 6). */
        object TeamSlot : Effect()
        /** Aditivní bonus k XP dané dovednosti (0,15 = +15 %). */
        data class XpBonus(val add: Double) : Effect()
        object MorePlots : Effect()
        object BasicEquipment : Effect()
        /** Kratší růst bobulí (0,15 = o 15 % rychleji). */
        data class FasterGrowth(val by: Double) : Effect()
        /** Efektivita nástroje dané dovednosti (0,2 = +20 %). */
        data class Efficiency(val add: Double) : Effect()
        /** Delší AFK – kolik hodin navíc se počítá, když jsi pryč. */
        data class AfkHours(val hours: Int) : Effect()
    }

    data class Node(
        val id: String,
        val skill: Skill,
        val title: String,
        val description: String,
        val cost: Int,
        val requires: String? = null,
        val effect: Effect
    ) {
        val itemId: String get() = "skill_node_$id"
    }

    val NODES: List<Node> = listOf(
        Node("team_2", Skill.CATCHING, "Parťák navíc", "Můžeš mít v týmu až DVA Makromony.", 1, effect = Effect.TeamSlot),
        Node("catch_xp", Skill.CATCHING, "Zkušený lovec", "+15 % XP za chytání.", 1, "team_2", Effect.XpBonus(0.15)),
        Node("team_3", Skill.CATCHING, "Trojice", "Můžeš mít v týmu až TŘI Makromony.", 1, "team_2", Effect.TeamSlot),
        Node("team_4", Skill.CATCHING, "Čtveřice", "Můžeš mít v týmu až ČTYŘI Makromony.", 2, "team_3", Effect.TeamSlot),
        Node("team_5", Skill.CATCHING, "Pětice", "Můžeš mít v týmu až PĚT Makromonů.", 2, "team_4", Effect.TeamSlot),
        Node("team_6", Skill.CATCHING, "Plný tým", "Můžeš mít v týmu až ŠEST Makromonů.", 3, "team_5", Effect.TeamSlot),

        Node("basic_gear", Skill.CRAFTING, "Základní vybavení", "Můžeš vyrábět dobrodruhův set u pracovního stolu na louce.", 1, effect = Effect.BasicEquipment),
        Node("craft_xp", Skill.CRAFTING, "Zručné ruce", "+15 % XP za výrobu.", 1, "basic_gear", Effect.XpBonus(0.15)),

        Node("more_plots", Skill.HARVESTING, "Nové záhony", "Zpřístupnilo se ti více záhonů (opravíš dva zničené).", 1, effect = Effect.MorePlots),
        Node("harvest_xp", Skill.HARVESTING, "Zelená ruka", "+15 % XP za sklizeň.", 1, "more_plots", Effect.XpBonus(0.15)),
        Node("fast_growth", Skill.HARVESTING, "Hnojivo", "Bobule rostou o 15 % rychleji.", 2, "more_plots", Effect.FasterGrowth(0.15)),

        Node("mine_eff", Skill.MINING, "Pevný úchop", "+20 % efektivita krumpáče.", 1, effect = Effect.Efficiency(0.20)),
        Node("mine_xp", Skill.MINING, "Horník", "+15 % XP za těžbu.", 1, "mine_eff", Effect.XpBonus(0.15)),
        Node("mine_afk", Skill.MINING, "Dlouhá směna", "Když jsi pryč, těží se o 12 h déle.", 2, "mine_eff", Effect.AfkHours(12)),

        Node("log_eff", Skill.LOGGING, "Nabroušené ostří", "+20 % efektivita sekery.", 1, effect = Effect.Efficiency(0.20)),
        Node("log_xp", Skill.LOGGING, "Dřevorubec", "+15 % XP za kácení.", 1, "log_eff", Effect.XpBonus(0.15)),
        Node("log_afk", Skill.LOGGING, "Celodenní šichta", "Když jsi pryč, kácí se o 12 h déle.", 2, "log_eff", Effect.AfkHours(12))
    )

    fun node(id: String): Node? = NODES.firstOrNull { it.id == id }
    fun of(skill: Skill): List<Node> = NODES.filter { it.skill == skill }
}

/** Stav dovedností hráče (XP a odemčené uzly) + všechno, co se z něj počítá. */
data class SkillState(
    val xp: Map<Skill, Long> = emptyMap(),
    val unlocked: Set<String> = emptySet(),
    /** Nasazené vybavení – jeho bonusy se přičítají (docs/adr/0039). */
    val gear: Set<Gear> = emptySet()
) {
    fun totalXp(skill: Skill): Long = xp[skill] ?: 0L
    fun level(skill: Skill): Int = SkillMath.levelOf(totalXp(skill))
    fun progress(skill: Skill): SkillMath.Progress = SkillMath.progress(totalXp(skill))

    fun spentPoints(skill: Skill): Int = SkillTree.of(skill).filter { it.id in unlocked }.sumOf { it.cost }
    fun availablePoints(skill: Skill): Int = SkillMath.skillPointsEarned(level(skill)) - spentPoints(skill)

    enum class NodeStatus { UNLOCKED, AVAILABLE, NO_POINTS, LOCKED }

    fun status(node: SkillTree.Node): NodeStatus = when {
        node.id in unlocked -> NodeStatus.UNLOCKED
        node.requires != null && node.requires !in unlocked -> NodeStatus.LOCKED
        availablePoints(node.skill) < node.cost -> NodeStatus.NO_POINTS
        else -> NodeStatus.AVAILABLE
    }

    fun canUnlock(node: SkillTree.Node) = status(node) == NodeStatus.AVAILABLE

    private fun effects() = SkillTree.NODES.filter { it.id in unlocked }.map { it.effect }

    /** Počet míst v týmu: 1 + odemčená místa, nejvýš 6. */
    val teamSlots: Int get() = (1 + effects().count { it is SkillTree.Effect.TeamSlot }).coerceAtMost(6)

    val plotsOpen: Int get() = if (effects().any { it is SkillTree.Effect.MorePlots }) 4 else 2

    val basicEquipment: Boolean get() = effects().any { it is SkillTree.Effect.BasicEquipment }

    val growthSpeedup: Double get() = effects().filterIsInstance<SkillTree.Effect.FasterGrowth>().sumOf { it.by }

    /** Aditivní XP bonusy dané dovednosti (ze stromu a z nasazeného vybavení). */
    fun xpAdditive(skill: Skill): List<Double> =
        SkillTree.NODES.filter { it.id in unlocked && it.skill == skill }.map { it.effect }
            .filterIsInstance<SkillTree.Effect.XpBonus>().map { it.add } +
            gear.mapNotNull { it.xpBonus[skill] }

    /** Násobitel šance na kořist z Makromonů (Dobrodruhův náhrdelník ×1,1). */
    val dropRate: Double get() = 1.0 + gear.sumOf { it.dropRate }

    /** Snížení šance na útěk z ballu: pasivní bonus Chytání + vybavení (Duše ohně). */
    val catchReduction: Double get() = (passive(Skill.CATCHING) + gear.sumOf { it.catchBonus }).coerceAtMost(0.9)

    /** Bonus vybavení k šanci na dvojitý kus (Makromonova sekera / krumpáč). */
    fun gearMulti(skill: Skill): Double = gear.sumOf { it.multiBonus[skill] ?: 0.0 }

    /** Plochý bonus k efektivitě z vybavení (pantofle +50). */
    fun gearEfficiency(skill: Skill): Int = gear.sumOf { it.efficiencyBonus[skill] ?: 0 }

    private fun effectsOf(skill: Skill) = SkillTree.NODES.filter { it.id in unlocked && it.skill == skill }.map { it.effect }

    /** Bonus efektivity nástroje ze stromu (0,2 = +20 %). */
    fun efficiencyBonus(skill: Skill): Double = effectsOf(skill).filterIsInstance<SkillTree.Effect.Efficiency>().sumOf { it.add }

    /** Kolik hodin AFK se nejvýš započítá (základ 12 h + strom). */
    fun afkCapHours(skill: Skill): Int = 12 + effectsOf(skill).filterIsInstance<SkillTree.Effect.AfkHours>().sumOf { it.hours }

    fun xpMultiplier(skill: Skill): Double = SkillMath.xpMultiplier(xpAdditive(skill))

    /** Zisk XP pro dovednost podle vzorce se všemi bonusy. */
    fun gain(skill: Skill, base: Double, multipliers: List<Double> = emptyList()): Int =
        SkillMath.gain(base, xpAdditive(skill), multipliers)

    /** Pasivní bonus dovednosti za level (šance na útěk ↓ / multicraft / multiharvest). */
    fun passive(skill: Skill): Double = SkillMath.passiveBonus(level(skill))

    fun withXp(skill: Skill, add: Int): SkillState = copy(xp = xp + (skill to totalXp(skill) + add.coerceAtLeast(0)))
    fun withNode(id: String): SkillState = copy(unlocked = unlocked + id)
}
