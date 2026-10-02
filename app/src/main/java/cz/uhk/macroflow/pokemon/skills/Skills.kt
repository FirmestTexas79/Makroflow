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
    LOGGING("logging", "Kácení", "kácením stromů sekerou"),
    /** docs/adr/0049 – chytá se síťkou v Dolech, i když jsi pryč (AFK). */
    BUG_CATCHING("bugcatching", "Chytání hmyzu", "chytáním hmyzu síťkou v Dolech");

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

    /** Do tohoto levelu přibývá dovednostní bod (docs/adr/0059); nad ním už jen pasivní bonus. */
    const val POINT_CAP = 50

    /** Bod přibude za každý level od 2 do [POINT_CAP] – celkem 49. */
    fun isSkillPointLevel(level: Int): Boolean = level in 2..POINT_CAP

    fun skillPointsEarned(level: Int): Int = (level.coerceAtMost(POINT_CAP) - 1).coerceAtLeast(0)

    /** Další level, na kterém přibude bod (null = všechny body už jsou). */
    fun nextSkillPointLevel(level: Int): Int? = (level + 1).takeIf { it <= POINT_CAP }

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
 * Strom dovedností po vzoru talentů z IdleOn (docs/adr/0059): za každý level dovednosti přibude
 * jeden bod (do levelu [SkillMath.POINT_CAP]). Většina uzlů má víc úrovní (např. 5×), každá úroveň
 * stojí [Node.cost] bodů a přidá svůj efekt znovu. Další stupeň (II, III) se otevře, až má předchozí
 * uzel potřebnou úroveň a dovednost potřebný level. Bodů je do levelu 50 méně, než strom unese –
 * hráč si vybírá, co vylepší. Přeučit (vrátit body) jde za mince.
 */
object SkillTree {

    sealed class Effect {
        /** Místo v aktivním týmu (výchozí je jedno, celkem až 6). */
        object TeamSlot : Effect()
        /** Aditivní bonus k XP dovednosti uzlu (0,05 = +5 %). */
        data class XpBonus(val add: Double) : Effect()
        /** Bonus k XP jiné dovednosti (synergie jako v IdleOn). */
        data class XpFor(val skill: Skill, val add: Double) : Effect()
        /** Bonus k XP všech ostatních dovedností. */
        data class XpOthers(val add: Double) : Effect()
        object MorePlots : Effect()
        object BasicEquipment : Effect()
        /** Kratší růst bobulí (0,05 = o 5 % rychleji). */
        data class FasterGrowth(val by: Double) : Effect()
        /** Efektivita nástroje dovednosti uzlu (0,1 = +10 %). */
        data class Efficiency(val add: Double) : Effect()
        /** Efektivita všech nástrojů (krumpáč, sekera, síťka). */
        data class EfficiencyAll(val add: Double) : Effect()
        /** Delší AFK – kolik hodin navíc se počítá, když jsi pryč. */
        data class AfkHours(val hours: Int) : Effect()
        /** Šance na dvojitý kus (těžba, kácení, hmyz, výroba, sklizeň). */
        data class MultiChance(val add: Double) : Effect()
        /** Kořist z Makromonů padá častěji (0,05 = +5 %). */
        data class DropRate(val add: Double) : Effect()
        /** Menší šance, že Makromon uteče z ballu (0,02 = −2 %). */
        data class CatchBonus(val add: Double) : Effect()
        /** Víc efektů najednou (vrcholné uzly). */
        data class Many(val list: List<Effect>) : Effect()
    }

    /**
     * [maxRank] = kolikrát jde uzel vylepšit, [cost] = body za jednu úroveň,
     * [requires] + [needs] = předchozí uzel a jeho potřebná úroveň (výchozí: maximální),
     * [minLevel] = potřebný level dovednosti.
     */
    data class Node(
        val id: String,
        val skill: Skill,
        val title: String,
        val description: String,
        val cost: Int,
        val requires: String? = null,
        val effect: Effect,
        val maxRank: Int = 1,
        val minLevel: Int = 1,
        val needs: Int? = null
    ) {
        val itemId: String get() = "skill_node_$id"
        /** Struktura hry (tým, záhony, vybavení) – přeučení ji nevrací. */
        val keepOnReset: Boolean get() = effect is Effect.TeamSlot || effect is Effect.MorePlots || effect is Effect.BasicEquipment
        val totalCost: Int get() = cost * maxRank
    }

    /** Řada zkušeností I–III (5 %, 10 %, 25 % za úroveň, po 5 úrovních), společná všem dovednostem. */
    private fun xpLine(skill: Skill, id: String, names: Triple<String, String, String>, what: String): List<Node> = listOf(
        Node(id, skill, names.first, "XP za $what.", 1, effect = Effect.XpBonus(0.05), maxRank = 5),
        Node("${id}2", skill, names.second, "XP za $what.", 1, id, Effect.XpBonus(0.10), maxRank = 5, minLevel = 15),
        Node("${id}3", skill, names.third, "XP za $what.", 2, "${id}2", Effect.XpBonus(0.25), maxRank = 5, minLevel = 30)
    )

    /** Strom sběrné dovednosti (těžba, kácení, hmyz) – stejná kostra, jiná jména a synergie. */
    private fun gathering(
        skill: Skill, p: String, tool: String, what: String,
        xpNames: Triple<String, String, String>,
        eff: Pair<String, String>, multi: Pair<String, String>, afk: Pair<String, String>,
        synergy: Node, master: String
    ): List<Node> = listOf(
        Node("${p}_eff", skill, eff.first, "Efektivita $tool.", 1, effect = Effect.Efficiency(0.10), maxRank = 5),
        Node("${p}_eff2", skill, eff.second, "Efektivita $tool.", 2, "${p}_eff", Effect.Efficiency(0.20), maxRank = 5, minLevel = 20),
        Node("${p}_multi", skill, multi.first, "Šance na dvojitý kus.", 1, "${p}_eff", Effect.MultiChance(0.02), maxRank = 5, minLevel = 8, needs = 2),
        Node("${p}_multi2", skill, multi.second, "Šance na dvojitý kus.", 2, "${p}_multi", Effect.MultiChance(0.03), maxRank = 5, minLevel = 35),
        Node("${p}_afk", skill, afk.first, "Když jsi pryč, pracuje se déle.", 1, "${p}_eff", Effect.AfkHours(3), maxRank = 4, minLevel = 5, needs = 1),
        Node("${p}_afk2", skill, afk.second, "Když jsi pryč, pracuje se déle.", 2, "${p}_afk", Effect.AfkHours(4), maxRank = 3, minLevel = 25),
        Node("${p}_master", skill, master, "Vrchol stromu: efektivita +50 % a XP +25 %.", 5, "${p}_eff2",
            Effect.Many(listOf(Effect.Efficiency(0.50), Effect.XpBonus(0.25))), minLevel = 50, needs = 1)
    ) + xpLine(skill, "${p}_xp", xpNames, what) + synergy

    val NODES: List<Node> = listOf(
        // ── Chytání: tým, pevný hod, kořist ───────────────────────────────────
        Node("team_2", Skill.CATCHING, "Parťák navíc", "V týmu můžeš mít až DVA Makromony.", 1, effect = Effect.TeamSlot, minLevel = 2),
        Node("team_3", Skill.CATCHING, "Trojice", "V týmu můžeš mít až TŘI Makromony.", 2, "team_2", Effect.TeamSlot, minLevel = 5),
        Node("team_4", Skill.CATCHING, "Čtveřice", "V týmu můžeš mít až ČTYŘI Makromony.", 3, "team_3", Effect.TeamSlot, minLevel = 12),
        Node("team_5", Skill.CATCHING, "Pětice", "V týmu můžeš mít až PĚT Makromonů.", 4, "team_4", Effect.TeamSlot, minLevel = 20),
        Node("team_6", Skill.CATCHING, "Plný tým", "V týmu můžeš mít až ŠEST Makromonů.", 5, "team_5", Effect.TeamSlot, minLevel = 30),
        Node("catch_grip", Skill.CATCHING, "Pevný hod", "Makromon z ballu uteče méně často.", 1, effect = Effect.CatchBonus(0.02), maxRank = 5, minLevel = 3),
        Node("catch_grip2", Skill.CATCHING, "Mistrovský hod", "Makromon z ballu uteče méně často.", 2, "catch_grip", Effect.CatchBonus(0.03), maxRank = 5, minLevel = 25),
        Node("catch_loot", Skill.CATCHING, "Lovec kořisti", "Kořist z Makromonů padá častěji.", 1, "catch_grip", Effect.DropRate(0.05), maxRank = 5, minLevel = 8, needs = 2),
        Node("catch_loot2", Skill.CATCHING, "Sběratel trofejí", "Kořist z Makromonů padá častěji.", 2, "catch_loot", Effect.DropRate(0.10), maxRank = 5, minLevel = 28),
        Node("catch_master", Skill.CATCHING, "Legendární lovec", "Vrchol stromu: kořist +25 % a útěk z ballu −10 %.", 5, "catch_loot2",
            Effect.Many(listOf(Effect.DropRate(0.25), Effect.CatchBonus(0.10))), minLevel = 50, needs = 1)
    ) + xpLine(Skill.CATCHING, "catch_xp", Triple("Zkušený lovec I", "Zkušený lovec II", "Zkušený lovec III"), "chytání") + listOf(

        // ── Výroba: vybavení, dvojitá výroba, nástroje a učitel ───────────────
        Node("basic_gear", Skill.CRAFTING, "Základní vybavení", "Můžeš vyrábět dobrodruhův set u pracovního stolu na louce.", 1, effect = Effect.BasicEquipment),
        Node("craft_multi", Skill.CRAFTING, "Dvojitá výroba", "Šance, že vyrobíš kus navíc.", 1, "basic_gear", Effect.MultiChance(0.03), maxRank = 5, minLevel = 4),
        Node("craft_multi2", Skill.CRAFTING, "Mistrovská dílna", "Šance, že vyrobíš kus navíc.", 2, "craft_multi", Effect.MultiChance(0.05), maxRank = 5, minLevel = 25),
        Node("craft_tools", Skill.CRAFTING, "Nástrojář", "Efektivita krumpáče, sekery i síťky.", 1, "basic_gear", Effect.EfficiencyAll(0.04), maxRank = 5, minLevel = 10),
        Node("craft_teach", Skill.CRAFTING, "Učitel", "XP všech ostatních dovedností.", 1, "craft_tools", Effect.XpOthers(0.02), maxRank = 5, minLevel = 18),
        Node("craft_master", Skill.CRAFTING, "Velmistr dílny", "Vrchol stromu: dvojitá výroba +10 % a XP ostatních dovedností +10 %.", 5, "craft_multi2",
            Effect.Many(listOf(Effect.MultiChance(0.10), Effect.XpOthers(0.10))), minLevel = 50, needs = 1)
    ) + xpLine(Skill.CRAFTING, "craft_xp", Triple("Zručné ruce I", "Zručné ruce II", "Zručné ruce III"), "výrobu") + listOf(

        // ── Pěstování: záhony, hnojivo, úroda, bylinkář ───────────────────────
        Node("more_plots", Skill.HARVESTING, "Nové záhony", "Opravíš dva zničené záhony – budeš jich mít čtyři.", 1, effect = Effect.MorePlots),
        Node("fast_growth", Skill.HARVESTING, "Hnojivo", "Bobule rostou rychleji.", 1, "more_plots", Effect.FasterGrowth(0.05), maxRank = 5, minLevel = 3),
        Node("fast_growth2", Skill.HARVESTING, "Zázračné hnojivo", "Bobule rostou rychleji.", 2, "fast_growth", Effect.FasterGrowth(0.07), maxRank = 5, minLevel = 25),
        Node("harvest_multi", Skill.HARVESTING, "Bohatá úroda", "Šance na dvojitou sklizeň.", 1, "more_plots", Effect.MultiChance(0.03), maxRank = 5, minLevel = 6),
        Node("harvest_multi2", Skill.HARVESTING, "Požehnaná úroda", "Šance na dvojitou sklizeň.", 2, "harvest_multi", Effect.MultiChance(0.05), maxRank = 5, minLevel = 30),
        Node("harvest_herb", Skill.HARVESTING, "Bylinkář", "Bobule do ballů – XP za výrobu.", 1, "harvest_multi", Effect.XpFor(Skill.CRAFTING, 0.04), maxRank = 5, minLevel = 12, needs = 1),
        Node("harvest_master", Skill.HARVESTING, "Strážce zahrady", "Vrchol stromu: růst o 10 % rychleji a dvojitá sklizeň +10 %.", 5, "fast_growth2",
            Effect.Many(listOf(Effect.FasterGrowth(0.10), Effect.MultiChance(0.10))), minLevel = 50, needs = 1)
    ) + xpLine(Skill.HARVESTING, "harvest_xp", Triple("Zelená ruka I", "Zelená ruka II", "Zelená ruka III"), "sklizeň") +

        // ── Těžba, kácení, hmyz ───────────────────────────────────────────────
        gathering(Skill.MINING, "mine", "krumpáče", "těžbu",
            Triple("Horník I", "Horník II", "Horník III"),
            "Pevný úchop" to "Diamantový hrot", "Bohatá žíla" to "Zlatá žíla", "Dlouhá směna" to "Noční směna",
            Node("mine_smith", Skill.MINING, "Kovářova ruda", "Ruda pro dílnu – XP za výrobu.", 1, "mine_xp", Effect.XpFor(Skill.CRAFTING, 0.05), maxRank = 3, minLevel = 12, needs = 3),
            "Mistr horník") +
        gathering(Skill.LOGGING, "log", "sekery", "kácení",
            Triple("Dřevorubec I", "Dřevorubec II", "Dřevorubec III"),
            "Nabroušené ostří" to "Ostří z ocele", "Dvojitý řez" to "Pila z hor", "Celodenní šichta" to "Přesčas",
            Node("log_compost", Skill.LOGGING, "Kompost z pilin", "Piliny na záhony – XP za sklizeň.", 1, "log_xp", Effect.XpFor(Skill.HARVESTING, 0.05), maxRank = 3, minLevel = 12, needs = 3),
            "Mistr dřevorubec") +
        // Chytání hmyzu síťkou v Dolech (docs/adr/0049)
        gathering(Skill.BUG_CATCHING, "net", "síťky", "chytání hmyzu",
            Triple("Entomolog I", "Entomolog II", "Entomolog III"),
            "Lehká ruka" to "Bleskový švih", "Plná síťka" to "Roj v síťce", "Noční lov" to "Lov do rána",
            Node("net_bait", Skill.BUG_CATCHING, "Návnada", "Hmyz láká Makromony – XP za chytání.", 1, "net_xp", Effect.XpFor(Skill.CATCHING, 0.05), maxRank = 3, minLevel = 12, needs = 3),
            "Mistr entomolog")

    fun node(id: String): Node? = NODES.firstOrNull { it.id == id }
    fun of(skill: Skill): List<Node> = NODES.filter { it.skill == skill }

    /** Mince za přeučení stromu jedné dovednosti (vrátí body, kromě týmu, záhonů a vybavení). */
    const val RESET_COINS = 200

    /** Efekt uzlu na dané úrovni jako text („+15 % XP“), pro kartu uzlu. */
    fun effectText(e: Effect, rank: Int, own: Skill): String {
        fun pct(v: Double) = "${Math.round(v * rank * 100)} %"
        return when (e) {
            is Effect.TeamSlot -> "+1 místo v týmu"
            is Effect.MorePlots -> "+2 záhony"
            is Effect.BasicEquipment -> "výroba dobrodruhova setu"
            is Effect.XpBonus -> "+${pct(e.add)} XP ${own.label}"
            is Effect.XpFor -> "+${pct(e.add)} XP ${e.skill.label}"
            is Effect.XpOthers -> "+${pct(e.add)} XP ostatních dovedností"
            is Effect.FasterGrowth -> "růst o ${pct(e.by)} rychleji"
            is Effect.Efficiency -> "+${pct(e.add)} efektivita"
            is Effect.EfficiencyAll -> "+${pct(e.add)} efektivita všech nástrojů"
            is Effect.AfkHours -> "+${e.hours * rank} h AFK"
            is Effect.MultiChance -> "+${pct(e.add)} dvojitý kus"
            is Effect.DropRate -> "+${pct(e.add)} kořist"
            is Effect.CatchBonus -> "−${pct(e.add)} útěk z ballu"
            is Effect.Many -> e.list.joinToString(", ") { effectText(it, rank, own) }
        }
    }
}

/** Stav dovedností hráče (XP a odemčené uzly) + všechno, co se z něj počítá. */
data class SkillState(
    val xp: Map<Skill, Long> = emptyMap(),
    /** Úroveň uzlů stromu (id → 1..maxRank); chybějící = 0 (docs/adr/0059). */
    val ranks: Map<String, Int> = emptyMap(),
    /** Nasazené vybavení – jeho bonusy se přičítají (docs/adr/0039). */
    val gear: Set<Gear> = emptySet()
) {
    fun totalXp(skill: Skill): Long = xp[skill] ?: 0L
    fun level(skill: Skill): Int = SkillMath.levelOf(totalXp(skill))
    fun progress(skill: Skill): SkillMath.Progress = SkillMath.progress(totalXp(skill))

    fun rank(id: String): Int = (ranks[id] ?: 0).coerceAtLeast(0)
    /** Uzly s aspoň jednou úrovní. */
    val unlocked: Set<String> get() = ranks.filterValues { it > 0 }.keys

    fun spentPoints(skill: Skill): Int = SkillTree.of(skill).sumOf { it.cost * rank(it.id).coerceAtMost(it.maxRank) }
    /** Volné body (nikdy záporné – u starých uložených her mohly být uzly levnější). */
    fun availablePoints(skill: Skill): Int = (SkillMath.skillPointsEarned(level(skill)) - spentPoints(skill)).coerceAtLeast(0)

    /**
     * MAXED = všechny úrovně; AVAILABLE = jde přidat úroveň; NO_POINTS = chybí body;
     * LOW_LEVEL = dovednost má nízký level; LOCKED = předchozí uzel nemá potřebnou úroveň.
     */
    enum class NodeStatus { MAXED, AVAILABLE, NO_POINTS, LOW_LEVEL, LOCKED }

    /** Potřebná úroveň předchozího uzlu (výchozí: jeho maximum). */
    fun needed(node: SkillTree.Node): Int = node.needs ?: node.requires?.let { SkillTree.node(it)?.maxRank } ?: 0

    fun status(node: SkillTree.Node): NodeStatus = when {
        rank(node.id) >= node.maxRank -> NodeStatus.MAXED
        node.requires != null && rank(node.requires) < needed(node) -> NodeStatus.LOCKED
        level(node.skill) < node.minLevel -> NodeStatus.LOW_LEVEL
        availablePoints(node.skill) < node.cost -> NodeStatus.NO_POINTS
        else -> NodeStatus.AVAILABLE
    }

    fun canUnlock(node: SkillTree.Node) = status(node) == NodeStatus.AVAILABLE

    /** Všechny efekty s úrovní (vrcholné uzly rozbalené). */
    private val active: List<Triple<SkillTree.Node, SkillTree.Effect, Int>> by lazy {
        fun flat(e: SkillTree.Effect): List<SkillTree.Effect> = if (e is SkillTree.Effect.Many) e.list.flatMap { flat(it) } else listOf(e)
        SkillTree.NODES.mapNotNull { n -> rank(n.id).coerceAtMost(n.maxRank).takeIf { it > 0 }?.let { r -> n to r } }
            .flatMap { (n, r) -> flat(n.effect).map { Triple(n, it, r) } }
    }

    private inline fun <reified E : SkillTree.Effect> sum(filter: (SkillTree.Node, E) -> Boolean = { _, _ -> true }, value: (E) -> Double): Double =
        active.sumOf { (n, e, r) -> if (e is E && filter(n, e)) value(e) * r else 0.0 }

    /** Počet míst v týmu: 1 + odemčená místa, nejvýš 6. */
    val teamSlots: Int get() = (1 + active.count { it.second is SkillTree.Effect.TeamSlot }).coerceAtMost(6)

    val plotsOpen: Int get() = if (active.any { it.second is SkillTree.Effect.MorePlots }) 4 else 2

    val basicEquipment: Boolean get() = active.any { it.second is SkillTree.Effect.BasicEquipment }

    /** Zrychlení růstu bobulí ze stromu, nejvýš 75 %. */
    val growthSpeedup: Double get() = sum<SkillTree.Effect.FasterGrowth> { it.by }.coerceAtMost(0.75)

    /** XP bonusy dané dovednosti ze stromu: vlastní uzly + synergie odjinud + Učitel (ostatní dovednosti). */
    fun treeXp(skill: Skill): Double =
        sum<SkillTree.Effect.XpBonus>({ n, _ -> n.skill == skill }) { it.add } +
            sum<SkillTree.Effect.XpFor>({ _, e -> e.skill == skill }) { it.add } +
            sum<SkillTree.Effect.XpOthers>({ n, _ -> n.skill != skill }) { it.add }

    /** Aditivní XP bonusy dané dovednosti (ze stromu a z nasazeného vybavení). */
    fun xpAdditive(skill: Skill): List<Double> =
        listOf(treeXp(skill)).filter { it > 0 } +
            gear.mapNotNull { it.xpBonus[skill] } +
            GearSet.complete(gear).mapNotNull { it.xp[skill] }          // bonus celé sady (docs/adr/0053)

    /** Násobitel šance na kořist z Makromonů: vybavení + strom Chytání. */
    val dropRate: Double get() = 1.0 + gear.sumOf { it.dropRate } + sum<SkillTree.Effect.DropRate> { it.add }

    /** Snížení šance na útěk z ballu: pasivní bonus Chytání + vybavení + strom, nejvýš 90 %. */
    val catchReduction: Double get() =
        (passive(Skill.CATCHING) + gear.sumOf { it.catchBonus } + sum<SkillTree.Effect.CatchBonus> { it.add }).coerceAtMost(0.9)

    /** Bonus vybavení k šanci na dvojitý kus (Makromonova sekera / krumpáč). */
    fun gearMulti(skill: Skill): Double = gear.sumOf { it.multiBonus[skill] ?: 0.0 } +
        GearSet.complete(gear).sumOf { it.multi[skill] ?: 0.0 }

    /** Plochý bonus k efektivitě z vybavení (pantofle +50). */
    fun gearEfficiency(skill: Skill): Int = gear.sumOf { it.efficiencyBonus[skill] ?: 0 }

    /** Bonus efektivity nástroje ze stromu (0,2 = +20 %): vlastní uzly + Nástrojář z Výroby. */
    fun efficiencyBonus(skill: Skill): Double =
        sum<SkillTree.Effect.Efficiency>({ n, _ -> n.skill == skill }) { it.add } +
            (if (skill in GATHERING) sum<SkillTree.Effect.EfficiencyAll> { it.add } else 0.0)

    /** Kolik hodin AFK se nejvýš započítá (základ 12 h + strom). */
    fun afkCapHours(skill: Skill): Int = 12 + sum<SkillTree.Effect.AfkHours>({ n, _ -> n.skill == skill }) { it.hours.toDouble() }.toInt() +
        gear.sumOf { it.afkHours[skill] ?: 0 } + GearSet.complete(gear).sumOf { it.afkHours[skill] ?: 0 }

    /** Šance na dvojitý kus ze stromu (Plná síťka, Dvojitá výroba, Bohatá úroda…). */
    fun treeMulti(skill: Skill): Double = sum<SkillTree.Effect.MultiChance>({ n, _ -> n.skill == skill }) { it.add }

    /** Celková šance na dvojitý kus (těžba, kácení, hmyz, výroba, sklizeň): pasivní bonus + vybavení + strom. */
    fun multiChance(skill: Skill): Double = passive(skill) + gearMulti(skill) + treeMulti(skill)

    fun xpMultiplier(skill: Skill): Double = SkillMath.xpMultiplier(xpAdditive(skill))

    /** Zisk XP pro dovednost podle vzorce se všemi bonusy. */
    fun gain(skill: Skill, base: Double, multipliers: List<Double> = emptyList()): Int =
        SkillMath.gain(base, xpAdditive(skill), multipliers)

    /** Pasivní bonus dovednosti za level (šance na útěk ↓ / multicraft / multiharvest). */
    fun passive(skill: Skill): Double = SkillMath.passiveBonus(level(skill))

    fun withXp(skill: Skill, add: Int): SkillState = copy(xp = xp + (skill to totalXp(skill) + add.coerceAtLeast(0)))
    /** O jednu úroveň výš (bez kontroly bodů – tu dělá [canUnlock]). */
    fun withNode(id: String): SkillState = copy(ranks = ranks + (id to rank(id) + 1))

    private companion object {
        val GATHERING = setOf(Skill.MINING, Skill.LOGGING, Skill.BUG_CATCHING)
    }
}
