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
    val power: Int,
    /** Bonus k XP dovedností (0,1 = +10 %), sčítá se s bonusy ze stromu. */
    val xpBonus: Map<Skill, Double> = emptyMap(),
    /** Plochý bonus k efektivitě těžby / kácení (jen s nástrojem v ruce). */
    val efficiencyBonus: Map<Skill, Int> = emptyMap(),
    /** Bonus k šanci na dvojitý kus (multiore / multilog), 0,1 = +10 %. */
    val multiBonus: Map<Skill, Double> = emptyMap(),
    /** Legendární artefakt – v UI zlatý nápis. */
    val legendary: Boolean = false,
    /** O kolik častěji padá kořist z Makromonů (0,1 = ×1,1). */
    val dropRate: Double = 0.0,
    /** Snížení šance na útěk z ballu (0,2 = −20 %, počítá se jako pasivní bonus Chytání). */
    val catchBonus: Double = 0.0
) {
    OLD_AXE("tool_axe_old", 1, GearSlot.AXE, "Stará sekera", "Otupená, ale pořád seká. Síla 10.", 10),
    OLD_PICKAXE("tool_pickaxe_old", 2, GearSlot.PICKAXE, "Starý krumpáč", "Rezavý, ale kámen rozbije. Síla 10.", 10),

    // ── Dobrodruhův set (docs/adr/0039) ──
    ADV_CAP("gear_adv_cap", 3, GearSlot.HELMET, "Dobrodruhova čepice",
        "Zelená čepice s pérem. +10 % XP za chytání.", 0, xpBonus = mapOf(Skill.CATCHING to 0.10)),
    ADV_TUNIC("gear_adv_tunic", 4, GearSlot.CHEST, "Dobrodruhova tunika",
        "Pevná tunika s opaskem. +10 % XP za výrobu.", 0, xpBonus = mapOf(Skill.CRAFTING to 0.10)),
    ADV_PANTS("gear_adv_pants", 5, GearSlot.LEGS, "Dobrodruhovy tepláky",
        "Pohodlné tepláky s pruhem. +10 % XP za pěstování.", 0, xpBonus = mapOf(Skill.HARVESTING to 0.10)),
    ADV_SLIPPERS("gear_adv_slippers", 6, GearSlot.BOOTS, "Dobrodruhovy pantofle",
        "Huňaté pantofle – kdo by řekl, že se v nich tak dobře těží. +50 k efektivitě těžby a kácení, +15 % XP za těžbu a kácení.", 0,
        xpBonus = mapOf(Skill.MINING to 0.15, Skill.LOGGING to 0.15),
        efficiencyBonus = mapOf(Skill.MINING to 50, Skill.LOGGING to 50)),

    // ── Artefakty z Gudwina (docs/adr/0040) ──
    MAKRO_AXE("tool_axe_makro", 7, GearSlot.AXE, "Makromonova sekera",
        "Artefakt z dob, kdy svět patřil jen Makromonům. Síla 500, +50 % XP za kácení, +10 % šance na dvojité poleno.", 500,
        xpBonus = mapOf(Skill.LOGGING to 0.5), multiBonus = mapOf(Skill.LOGGING to 0.10), legendary = true),
    MAKRO_PICKAXE("tool_pickaxe_makro", 8, GearSlot.PICKAXE, "Makromonův krumpáč",
        "Artefakt z dob, kdy svět patřil jen Makromonům. Síla 500, +50 % XP za těžbu, +10 % šance na dvojitou rudu.", 500,
        xpBonus = mapOf(Skill.MINING to 0.5), multiBonus = mapOf(Skill.MINING to 0.10), legendary = true),

    // ── Doplňky (docs/adr/0041) ──
    GRASS_RING("acc_ring_grass", 9, GearSlot.RING_1, "Travní prsten",
        "Prsten z živé révy se smaragdovým lístkem. +15 % XP za chytání.", 0, xpBonus = mapOf(Skill.CATCHING to 0.15)),
    FIRE_RING("acc_ring_fire", 10, GearSlot.RING_1, "Ohnivý prsten",
        "Zlatý prsten s jiskrou uvnitř kamene. +5 % XP za výrobu, pěstování, těžbu a kácení.", 0,
        xpBonus = mapOf(Skill.CRAFTING to 0.05, Skill.HARVESTING to 0.05, Skill.MINING to 0.05, Skill.LOGGING to 0.05)),
    ADV_NECKLACE("acc_necklace_adv", 11, GearSlot.PENDANT, "Dobrodruhův náhrdelník",
        "Perla, dušička a pixie prach na jednom řetízku. Kořist z Makromonů padá o 10 % častěji.", 0, dropRate = 0.10),
    FIRE_SOUL("acc_trinket_fire_soul", 12, GearSlot.TRINKET, "Duše ohně",
        "Uvnitř pořád žhne kousek magmatu. O 20 % menší šance, že Makromon uteče z ballu.", 0, catchBonus = 0.20);

    /** Dá se vyrobit u pracovního stolu. */
    val craftable: Boolean get() = GearCrafting.recipe(this) != null

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
    fun efficiency(toolPower: Int, level: Int, treeBonus: Double, gearFlat: Int = 0): Int =
        if (toolPower <= 0) 0 else floor((toolPower + 2 * (level - 1)) * (1 + treeBonus)).toInt() + gearFlat

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

/** Recepty na vybavení u pracovního stolu (docs/adr/0039). */
object GearCrafting {
    val SET = listOf(Gear.ADV_CAP, Gear.ADV_TUNIC, Gear.ADV_PANTS, Gear.ADV_SLIPPERS)
    val ACCESSORIES = listOf(Gear.GRASS_RING, Gear.FIRE_RING, Gear.ADV_NECKLACE, Gear.FIRE_SOUL)

    fun recipe(g: Gear): Map<String, Int>? = when (g) {
        Gear.ADV_CAP -> linkedMapOf(Resource.ENERGY.itemId to 5, Resource.BERRY_BLUE.itemId to 3)
        Gear.ADV_TUNIC -> linkedMapOf(Resource.LOG_OAK.itemId to 15, Resource.BERRY_GREEN.itemId to 10)
        Gear.ADV_PANTS -> linkedMapOf(Resource.ORE_COPPER.itemId to 15, Resource.BERRY_BLACK.itemId to 1)
        Gear.ADV_SLIPPERS -> linkedMapOf(Resource.ORE_SILVER.itemId to 5, Resource.LOG_BIRCH.itemId to 5)
        Gear.GRASS_RING -> linkedMapOf(Resource.LEAF_DRY.itemId to 3, Resource.LEAF_LIVING.itemId to 1, Resource.ENERGY.itemId to 5)
        Gear.FIRE_RING -> linkedMapOf(Resource.EMBER.itemId to 3, Resource.FIRE_STONE.itemId to 1, Resource.ENERGY.itemId to 5)
        Gear.ADV_NECKLACE -> linkedMapOf(Resource.WATER_PEARL.itemId to 3, Resource.SOUL_WISP.itemId to 3, Resource.PIXIE_DUST.itemId to 3)
        Gear.FIRE_SOUL -> linkedMapOf(Resource.BERRY_BLACK.itemId to 3, Resource.MAGMA_ORB.itemId to 2, Resource.ENERGY.itemId to 20)
        else -> null
    }

    /** XP Výroby za kus – těžší recept, víc XP. */
    fun xp(g: Gear): Int = when (g) {
        Gear.ADV_CAP -> 60; Gear.ADV_TUNIC -> 90; Gear.ADV_PANTS -> 110; Gear.ADV_SLIPPERS -> 150
        Gear.GRASS_RING -> 120; Gear.FIRE_RING -> 150; Gear.ADV_NECKLACE -> 180; Gear.FIRE_SOUL -> 250
        else -> 0
    }

    fun canCraft(g: Gear, owned: Map<String, Int>): Boolean =
        recipe(g)?.all { (id, n) -> (owned[id] ?: 0) >= n } ?: false
}
