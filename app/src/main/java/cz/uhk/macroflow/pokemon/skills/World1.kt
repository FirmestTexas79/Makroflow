package cz.uhk.macroflow.pokemon.skills

import cz.uhk.macroflow.pokemon.balls.Makroball
import kotlin.math.roundToLong
import kotlin.random.Random

/**
 * Obsah světa 1 pro dovednosti (docs/adr/0034): bobule, semínka, fragment energie, výroba
 * Makroballů, záhony a kořist ze soubojů. Čistý Kotlin, pokryto testy.
 */

/** Bobule – ze stejné barvy se vyrábí příslušný Makroball. Čím silnější ball, tím vzácnější bobule. */
enum class Berry(
    val code: Int,
    val id: String,
    val label: String,
    val seedLabel: String,
    val ball: Makroball,
    /** Doba růstu na záhonu v sekundách. */
    val growSeconds: Long,
    val harvestXp: Int,
    val craftXp: Int,
    /** Cena semínka v obchodě (makro penízky). */
    val seedPrice: Int
) {
    GREEN(1, "green", "Olivová bobule", "Olivové semínko", Makroball.MAKRO, 15 * 60L, 15, 10, 10),
    BLUE(2, "blue", "Modrá bobule", "Modré semínko", Makroball.PROTEIN, 60 * 60L, 40, 25, 20),
    BLACK(3, "black", "Černozlatá bobule", "Černozlaté semínko", Makroball.KREATIN, 4 * 60 * 60L, 100, 60, 50);

    val berryItemId: String get() = "berry_$id"
    val seedItemId: String get() = "seed_$id"

    companion object {
        fun fromCode(code: Int): Berry? = entries.firstOrNull { it.code == code }
        fun forBall(ball: Makroball): Berry = entries.first { it.ball == ball }
    }
}

/** Suroviny v deníku (strana Suroviny) a v inventáři. */
enum class Resource(val itemId: String, val label: String, val description: String) {
    ENERGY("energy_fragment", "Fragment energie", "Padá z poražených i chycených Makromonů. S bobulí z něj u pracovního stolu vyrobíš Makroball."),
    BERRY_GREEN(Berry.GREEN.berryItemId, Berry.GREEN.label, "Z ní se vyrábí Makroball."),
    BERRY_BLUE(Berry.BLUE.berryItemId, Berry.BLUE.label, "Z ní se vyrábí Proteinball."),
    BERRY_BLACK(Berry.BLACK.berryItemId, Berry.BLACK.label, "Z ní se vyrábí Kreatinball."),
    SEED_GREEN(Berry.GREEN.seedItemId, Berry.GREEN.seedLabel, "Zasaď na záhon na louce – roste 15 minut."),
    SEED_BLUE(Berry.BLUE.seedItemId, Berry.BLUE.seedLabel, "Zasaď na záhon na louce – roste 1 hodinu."),
    SEED_BLACK(Berry.BLACK.seedItemId, Berry.BLACK.seedLabel, "Zasaď na záhon na louce – roste 4 hodiny."),
    // Těžba a kácení (docs/adr/0035)
    ORE_COPPER("ore_copper", "Měděná ruda", "Vytěžíš ji krumpáčem z měděné žíly v horách."),
    ORE_SILVER("ore_silver", "Stříbrná ruda", "Stříbrná žíla ve Starém dole chce lepší efektivitu krumpáče."),
    ORE_GOLD("ore_gold", "Zlatá ruda", "Nejvzácnější ruda – v Mechové jeskyni, jen pro zkušené horníky."),
    LOG_OAK("log_oak", "Dubové poleno", "Pokácíš ho sekerou z dubu na louce."),
    LOG_BIRCH("log_birch", "Březové poleno", "Bříza v Hvozdu chce ostřejší sekeru."),
    LOG_MAPLE("log_maple", "Javorové poleno", "Tvrdé dřevo javoru – jen pro zkušené dřevorubce."),

    // Materiály z Makromonů (docs/adr/0040)
    LEAF_DRY("mat_leaf_dry", "Suchý list", "Šustivý list z listových Makromonů (Flori, Verdirra)."),
    LEAF_LIVING("mat_leaf_living", "Živý list", "Pořád zelený a teplý na dotek – vylepšený suchý list z Florinda a Florindry."),
    EMBER("mat_ember", "Chudý plamínek", "Poslední jiskřička ohnivého Makromona (Ignar, Flamirra)."),
    FIRE_STONE("mat_fire_stone", "Žhnoucí kámen", "Kámen, ze kterého tu a tam vyšlehne plamínek. Padá z Ignaroca."),
    MAGMA_ORB("mat_magma_orb", "Koule magmatu", "Celá koule rozžhaveného magmatu. Padá z Ignarotha."),
    WATER_PEARL("mat_water_pearl", "Vodní perla", "Hladká perla z vodních Makromonů."),
    SOUL_WISP("mat_soul_wisp", "Malá dušička", "Tichý chladný obláček z duchů."),
    DRAGON_SCALE("mat_dragon_scale", "Dračí šupina", "Tvrdá lesklá šupina dračích Makromonů."),
    PIXIE_DUST("mat_pixie_dust", "Pixie prach", "Třpytivý prach vílích Makromonů.");

    val berry: Berry? get() = when (this) {
        BERRY_GREEN, SEED_GREEN -> Berry.GREEN
        BERRY_BLUE, SEED_BLUE -> Berry.BLUE
        BERRY_BLACK, SEED_BLACK -> Berry.BLACK
        else -> null
    }
    val isSeed: Boolean get() = itemId.startsWith("seed_")
    /** Materiál z Makromonů. */
    val isMonsterMaterial: Boolean get() = itemId.startsWith("mat_")

    companion object {
        fun from(itemId: String?): Resource? = entries.firstOrNull { it.itemId == itemId }
    }
}

/** Výroba Makroballů: 1 fragment energie + 1 bobule příslušné barvy. */
object Crafting {
    fun recipe(ball: Makroball): Map<String, Int> =
        mapOf(Resource.ENERGY.itemId to 1, Berry.forBall(ball).berryItemId to 1)

    /** Kolikrát to jde vyrobit z toho, co máš. */
    fun maxCraftable(ball: Makroball, owned: Map<String, Int>): Int =
        recipe(ball).minOf { (id, n) -> (owned[id] ?: 0) / n }

    fun baseXp(ball: Makroball): Int = Berry.forBall(ball).craftXp

    /** Jedna výroba: 1 ball, s šancí [multicraft] dva. */
    fun roll(multicraft: Double, rng: Random = Random.Default): Int = SkillMath.rollDouble(multicraft, rng)
}

/** Rodina Makromona pro kořist (typ se v bitvě bere z prvního útoku, proto podle druhu). */
enum class DropFamily(val material: Resource?) {
    NORMAL(null), FIRE(Resource.EMBER), WATER(Resource.WATER_PEARL), GRASS(Resource.LEAF_DRY),
    GHOST(Resource.SOUL_WISP), DRAGON(Resource.DRAGON_SCALE), FAIRY(Resource.PIXIE_DUST)
}

/** Kořist ze soubojů s divokými Makromony (docs/adr/0034, 0040). */
object Drops {
    data class Drop(val itemId: String, val amount: Int)

    private val FAMILY: Map<String, DropFamily> = buildMap {
        listOf("IGNAR", "IGNAROC", "IGNAROTH", "FLAMIRRA").forEach { put(it, DropFamily.FIRE) }
        listOf("AQULIN", "AQULIND", "AQULINOX", "AQUIRRA", "FINLET", "SERPFIN", "GLACIRRA").forEach { put(it, DropFamily.WATER) }
        listOf("FLORI", "FLORIND", "FLORINDRA", "VERDIRRA").forEach { put(it, DropFamily.GRASS) }
        listOf("UMBEX", "LUMEX", "SOULU", "SOULEX", "SOULORD", "PHANTIL", "PHANTIUS", "PHANTIAX", "SHADIRRA").forEach { put(it, DropFamily.GHOST) }
        put("DRAKIRRA", DropFamily.DRAGON)
        put("CHARMIRRA", DropFamily.FAIRY)
    }

    /** Vylepšené materiály z evolucí. */
    val UPGRADE: Map<String, Resource> = mapOf(
        "IGNAROC" to Resource.FIRE_STONE, "IGNAROTH" to Resource.MAGMA_ORB,
        "FLORIND" to Resource.LEAF_LIVING, "FLORINDRA" to Resource.LEAF_LIVING
    )

    fun family(species: String): DropFamily = FAMILY[species.uppercase()] ?: DropFamily.NORMAL

    /** Šance na fragment energie: po výhře 35 % (+1 % za level, max 60 %), po chycení 25 %. Normální Makromoni +20 % (max 80 %). */
    fun fragmentChance(level: Int, caught: Boolean, family: DropFamily = DropFamily.WATER): Double {
        val base = if (caught) 0.25 else (0.35 + 0.01 * level).coerceAtMost(0.6)
        return if (family == DropFamily.NORMAL) (base + 0.2).coerceAtMost(0.8) else base
    }

    /** Šance na materiál rodiny: po výhře 40 % (+1 % za level, max 65 %), po chycení 25 %. */
    fun materialChance(level: Int, caught: Boolean): Double = if (caught) 0.25 else (0.4 + 0.01 * level).coerceAtMost(0.65)

    /** Šance na vylepšený materiál z evoluce: po výhře 35 %, po chycení 20 %. */
    fun upgradeChance(caught: Boolean): Double = if (caught) 0.2 else 0.35

    /** Semínko padá jen z listových Makromonů. */
    const val SEED_CHANCE = 0.25

    /** Makromonova sekera / krumpáč z poraženého Gudwina – každý zvlášť. */
    const val ARTIFACT_CHANCE = 0.05

    /** Vzácnost semínka: černozlaté 5 %, modré 20 %, jinak olivové. */
    fun seedTier(r: Double): Berry = when {
        r < 0.05 -> Berry.BLACK
        r < 0.25 -> Berry.BLUE
        else -> Berry.GREEN
    }

    fun roll(species: String, level: Int, caught: Boolean, rng: Random = Random.Default): List<Drop> {
        val fam = family(species)
        val out = mutableListOf<Drop>()
        if (rng.nextDouble() < fragmentChance(level, caught, fam)) {
            val two = level >= 8 && rng.nextDouble() < 0.2
            out += Drop(Resource.ENERGY.itemId, if (two) 2 else 1)
        }
        fam.material?.let { m ->
            if (rng.nextDouble() < materialChance(level, caught)) out += Drop(m.itemId, if (level >= 10 && rng.nextDouble() < 0.25) 2 else 1)
        }
        UPGRADE[species.uppercase()]?.let { u -> if (rng.nextDouble() < upgradeChance(caught)) out += Drop(u.itemId, 1) }
        if (fam == DropFamily.GRASS && rng.nextDouble() < SEED_CHANCE) out += Drop(seedTier(rng.nextDouble()).seedItemId, 1)
        if (species.uppercase() == "GUDWIN" && !caught) {
            if (rng.nextDouble() < ARTIFACT_CHANCE) out += Drop(Gear.MAKRO_AXE.id, 1)
            if (rng.nextDouble() < ARTIFACT_CHANCE) out += Drop(Gear.MAKRO_PICKAXE.id, 1)
        }
        return out
    }

    /** Kteří Makromoni dávají daný materiál (pro ceduli v deníku). */
    fun speciesFor(r: Resource): List<String> =
        FAMILY.filter { it.value.material == r }.keys.toList() + UPGRADE.filter { it.value == r }.keys
}

/** Chytání: XP a šance na útěk po vyskočení z ballu. */
object CatchRules {
    /** Základní XP za chyceného Makromona – vyšší level = víc XP. */
    fun baseXp(level: Int): Double = 10.0 + 3.0 * level

    /** Shiny je samostatný násobitel ×2. */
    fun multipliers(shiny: Boolean): List<Double> = if (shiny) listOf(2.0) else emptyList()

    /** Šance, že Makromon po vyskočení z ballu uteče – pasivní bonus Chytání ji snižuje. */
    fun fleeChance(base: Double, catchingPassive: Double): Double = SkillMath.reduced(base, catchingPassive)
}

/**
 * Záhony na louce. Stav záhonu se ukládá jako user_items „garden_<i>“:
 * množství = (sekundy od [EPOCH]) × 4 + kód bobule; 0 = prázdný.
 */
object Garden {
    const val PLOTS = 4
    /** 2026-01-01 00:00 UTC – krátká epocha, aby se čas vešel do Int i s kódem. */
    const val EPOCH = 1_767_225_600L

    data class Plot(val berry: Berry?, val plantedAt: Long) {
        val isEmpty: Boolean get() = berry == null
    }

    fun itemId(index: Int) = "garden_$index"

    fun encode(berry: Berry, plantedEpochSec: Long): Int = ((plantedEpochSec - EPOCH).coerceAtLeast(0) * 4 + berry.code).toInt()

    fun decode(q: Int): Plot {
        if (q <= 0) return Plot(null, 0)
        val berry = Berry.fromCode(q and 3) ?: return Plot(null, 0)
        return Plot(berry, EPOCH + (q ushr 2))
    }

    /** Doba růstu se zrychlením ze stromu (0,15 = o 15 % rychleji). */
    fun growSeconds(berry: Berry, speedup: Double): Long =
        (berry.growSeconds * (1 - speedup.coerceIn(0.0, 0.9))).roundToLong()

    fun remaining(plot: Plot, nowEpochSec: Long, speedup: Double): Long {
        val b = plot.berry ?: return 0
        return (plot.plantedAt + growSeconds(b, speedup) - nowEpochSec).coerceAtLeast(0)
    }

    fun isReady(plot: Plot, nowEpochSec: Long, speedup: Double) = !plot.isEmpty && remaining(plot, nowEpochSec, speedup) == 0L

    fun fraction(plot: Plot, nowEpochSec: Long, speedup: Double): Float {
        val b = plot.berry ?: return 0f
        val total = growSeconds(b, speedup).coerceAtLeast(1)
        return ((nowEpochSec - plot.plantedAt).toFloat() / total).coerceIn(0f, 1f)
    }

    /** Záhony 0 a 1 jsou otevřené od začátku, 2 a 3 až po uzlu „Nové záhony“. */
    fun isOpen(index: Int, plotsOpen: Int) = index < plotsOpen

    /** „4:59“, „1:02:03“. */
    fun clock(sec: Long): String {
        val h = sec / 3600; val m = (sec % 3600) / 60; val s = sec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }
}
