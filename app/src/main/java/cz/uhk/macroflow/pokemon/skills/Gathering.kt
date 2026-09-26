package cz.uhk.macroflow.pokemon.skills

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToLong
import kotlin.random.Random

/**
 * Vybavení a AFK těžba/kácení (docs/adr/0035). Čistý Kotlin, pokryto testy.
 */

/** Záložky vybavení v okně postavy – každá ukáže jeden sloupec čtyř slotů. */
enum class GearTab(val label: String) { EQUIPS("EQUIPS"), ACCESS("ACCESS"), TOOLS("TOOLS") }

enum class GearSlot(val id: String, val label: String, val tab: GearTab, val locked: Boolean = false) {
    HELMET("helmet", "Přilba", GearTab.EQUIPS),
    CHEST("chest", "Hrudní plát", GearTab.EQUIPS),
    LEGS("legs", "Kalhoty", GearTab.EQUIPS),
    BOOTS("boots", "Boty", GearTab.EQUIPS),
    TRINKET("trinket", "Talisman", GearTab.ACCESS),
    PENDANT("pendant", "Přívěsek", GearTab.ACCESS),
    RING_1("ring1", "Prsten", GearTab.ACCESS),
    RING_2("ring2", "Prsten", GearTab.ACCESS),
    AXE("axe", "Sekera", GearTab.TOOLS),
    PICKAXE("pickaxe", "Krumpáč", GearTab.TOOLS),
    NET("net", "Síťka", GearTab.TOOLS),
    MYSTERY("mystery", "Přijde později…", GearTab.TOOLS, locked = true);

    /** user_items „equip_<slot>“ = kód nasazeného předmětu (0 = prázdno). */
    val itemId: String get() = "equip_$id"

    companion object {
        fun of(tab: GearTab) = entries.filter { it.tab == tab }
    }
}

/** Předměty vybavení. Kód je trvalý (ukládá se do slotu), nikdy ho neměnit. */
enum class Gear(
    val id: String,
    val code: Int,
    val slot: GearSlot,
    val label: String,
    val description: String,
    /** Síla nástroje = základ efektivity těžby / kácení. */
    val power: Int
) {
    OLD_AXE("tool_axe_old", 1, GearSlot.AXE, "Stará sekera", "Otupená, ale pořád seká. Síla 10.", 10),
    OLD_PICKAXE("tool_pickaxe_old", 2, GearSlot.PICKAXE, "Starý krumpáč", "Rezavý, ale kámen rozbije. Síla 10.", 10);

    companion object {
        fun from(id: String?) = entries.firstOrNull { it.id == id }
        fun fromCode(code: Int) = entries.firstOrNull { it.code == code }
        /** Co se dá nasadit do slotu (prsteny do obou prstenových). */
        fun fitting(slot: GearSlot) = entries.filter { it.slot == slot || (it.slot == GearSlot.RING_1 && slot == GearSlot.RING_2) }
    }
}

/** Místa těžby a kácení na mapě. */
enum class GatherSpot(
    val code: Int,
    val node: String,
    val skill: Skill,
    val label: String,
    val resource: Resource,
    /** Potřebná efektivita – pod ní to nejde vůbec. */
    val required: Int,
    /** Sekundy na kus při efektivitě přesně [required]. */
    val baseSeconds: Long,
    val xp: Int,
    val biome: String
) {
    COPPER(1, "zila_med", Skill.MINING, "Měděná žíla", Resource.ORE_COPPER, 10, 180, 10, "MOUNTAINS"),
    SILVER(2, "zila_stribro", Skill.MINING, "Stříbrná žíla", Resource.ORE_SILVER, 30, 360, 25, "CAVE_MAZE"),
    GOLD(3, "zila_zlato", Skill.MINING, "Zlatá žíla", Resource.ORE_GOLD, 70, 720, 60, "CAVE_OPEN"),
    OAK(4, "strom_dub", Skill.LOGGING, "Dub", Resource.LOG_OAK, 10, 180, 10, "MEADOW"),
    BIRCH(5, "strom_briza", Skill.LOGGING, "Bříza", Resource.LOG_BIRCH, 30, 360, 25, "FOREST"),
    MAPLE(6, "strom_javor", Skill.LOGGING, "Javor", Resource.LOG_MAPLE, 70, 720, 60, "FOREST");

    /** Nástroj, bez kterého to nejde. */
    val toolSlot: GearSlot get() = if (skill == Skill.MINING) GearSlot.PICKAXE else GearSlot.AXE
    val verb: String get() = if (skill == Skill.MINING) "Těžit" else "Kácet"

    /** Kde to na mapě je (pro cedule a deník). */
    val placeLabel: String get() = when (biome) {
        "MOUNTAINS" -> "Hory"
        "MEADOW" -> "Louka"
        "CAVE_MAZE" -> "Starý důl (levá jeskyně v horách)"
        "CAVE_OPEN" -> "Mechová jeskyně (pravá jeskyně v horách)"
        "FOREST" -> "Hvozd nad loukou"
        else -> biome
    }

    companion object {
        fun fromNode(node: String) = entries.firstOrNull { it.node == node }
        fun fromCode(code: Int) = entries.firstOrNull { it.code == code }
    }
}

object Gathering {

    /** Stejná epocha jako záhony – čas se vejde do Int. */
    const val EPOCH = Garden.EPOCH

    /** Efektivita = (síla nástroje + 2 za každý level nad první) × (1 + bonus ze stromu). */
    fun efficiency(toolPower: Int, level: Int, treeBonus: Double): Int =
        if (toolPower <= 0) 0 else floor((toolPower + 2 * (level - 1)) * (1 + treeBonus)).toInt()

    /**
     * Sekundy na jeden kus: základ × potřebná / tvoje efektivita, nejvýš 10× rychleji.
     * Pod potřebnou efektivitou null (nejde).
     */
    fun secondsPerUnit(spot: GatherSpot, eff: Int): Long? {
        if (eff < spot.required) return null
        return max(spot.baseSeconds / 10, (spot.baseSeconds.toDouble() * spot.required / eff).roundToLong())
    }

    /** Probíhající činnost: kde a od kdy se ještě nevybralo (sekundy od epochy Unixu). */
    data class Activity(val spot: GatherSpot, val since: Long)

    /** user_items „gather_spot“ = kód místa, „gather_since“ = sekundy od [EPOCH]. */
    const val SPOT_ITEM = "gather_spot"
    const val SINCE_ITEM = "gather_since"

    fun decode(spotCode: Int, sinceQ: Int): Activity? {
        val spot = GatherSpot.fromCode(spotCode) ?: return null
        return Activity(spot, EPOCH + sinceQ)
    }
    fun encodeSince(epochSec: Long): Int = (epochSec - EPOCH).coerceAtLeast(0).toInt()

    data class Claim(
        val units: Int,
        /** Kusů celkem včetně dvojitých (multiore / multilog). */
        val amount: Int,
        /** Nový začátek počítání – zbytek rozpracovaného kusu se nezahodí. */
        val newSince: Long,
        /** Kolik sekund se započítalo (po omezení AFK). */
        val countedSeconds: Long,
        /** Uplynulo víc než strop AFK. */
        val capped: Boolean
    )

    /**
     * Vybere hotové kusy od [Activity.since] do [now]. AFK se počítá nejvýš [capHours];
     * co je nad strop, propadne. Každý kus má šanci [multi] na dvojnásobek.
     */
    fun claim(a: Activity, now: Long, secPerUnit: Long, multi: Double, capHours: Int, rng: Random = Random.Default): Claim {
        val elapsed = (now - a.since).coerceAtLeast(0)
        val cap = capHours * 3600L
        val capped = elapsed > cap
        val counted = minOf(elapsed, cap)
        val units = (counted / secPerUnit.coerceAtLeast(1)).toInt()
        val amount = (0 until units).sumOf { SkillMath.rollDouble(multi, rng) }
        // Zbytek rozpracovaného kusu zůstane; po stropu se počítá od teď
        val newSince = if (capped) now - (counted % secPerUnit) else a.since + units * secPerUnit
        return Claim(units, amount, newSince, counted, capped)
    }

    /** Postup rozpracovaného kusu 0–1 a sekundy do dalšího. */
    fun partial(a: Activity, now: Long, secPerUnit: Long): Pair<Float, Long> {
        val into = ((now - a.since).coerceAtLeast(0)) % secPerUnit.coerceAtLeast(1)
        return (into.toFloat() / secPerUnit) to (secPerUnit - into)
    }

    /** Hotové, ještě nevybrané kusy (bez hodu na dvojnásobek, jen pro zobrazení). */
    fun pending(a: Activity, now: Long, secPerUnit: Long, capHours: Int): Int =
        (minOf((now - a.since).coerceAtLeast(0), capHours * 3600L) / secPerUnit.coerceAtLeast(1)).toInt()

    /** „38 min“, „2 h 5 min“. */
    fun awayText(sec: Long): String {
        val h = sec / 3600; val m = (sec % 3600) / 60
        return when {
            h > 0 && m > 0 -> "$h h $m min"
            h > 0 -> "$h h"
            m > 0 -> "$m min"
            else -> "${sec.coerceAtLeast(0)} s"
        }
    }
}
