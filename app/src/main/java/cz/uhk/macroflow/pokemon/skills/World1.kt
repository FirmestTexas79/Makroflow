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
    GREEN(1, "green", "Olivová bobule", "Olivové semínko", Makroball.MAKRO, 15 * 60L, 15, 10, 15),
    BLUE(2, "blue", "Modrá bobule", "Modré semínko", Makroball.PROTEIN, 60 * 60L, 40, 25, 45),
    BLACK(3, "black", "Černozlatá bobule", "Černozlaté semínko", Makroball.KREATIN, 4 * 60 * 60L, 100, 60, 120);

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
    ORE_SILVER("ore_silver", "Stříbrná ruda", "Stříbrná žíla v horách chce lepší efektivitu krumpáče."),
    ORE_GOLD("ore_gold", "Zlatá ruda", "Nejvzácnější ruda hor – jen pro zkušené horníky."),
    LOG_OAK("log_oak", "Dubové poleno", "Pokácíš ho sekerou z dubu na louce."),
    LOG_BIRCH("log_birch", "Březové poleno", "Bříza na louce chce ostřejší sekeru."),
    LOG_MAPLE("log_maple", "Javorové poleno", "Tvrdé dřevo javoru – jen pro zkušené dřevorubce.");

    val berry: Berry? get() = when (this) {
        BERRY_GREEN, SEED_GREEN -> Berry.GREEN
        BERRY_BLUE, SEED_BLUE -> Berry.BLUE
        BERRY_BLACK, SEED_BLACK -> Berry.BLACK
        else -> null
    }
    val isSeed: Boolean get() = itemId.startsWith("seed_")

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

/** Kořist ze soubojů s divokými Makromony. */
object Drops {
    data class Drop(val itemId: String, val amount: Int)

    /** Šance na fragment energie: po výhře 35 % (+1 % za level, max 60 %), po chycení 25 %. */
    fun fragmentChance(level: Int, caught: Boolean): Double =
        if (caught) 0.25 else (0.35 + 0.01 * level).coerceAtMost(0.6)

    /** Semínko padá jen z travních Makromonů. */
    const val SEED_CHANCE = 0.25

    /** Vzácnost semínka: černozlaté 5 %, modré 20 %, jinak olivové. */
    fun seedTier(r: Double): Berry = when {
        r < 0.05 -> Berry.BLACK
        r < 0.25 -> Berry.BLUE
        else -> Berry.GREEN
    }

    fun roll(level: Int, isGrass: Boolean, caught: Boolean, rng: Random = Random.Default): List<Drop> {
        val out = mutableListOf<Drop>()
        if (rng.nextDouble() < fragmentChance(level, caught)) {
            val two = level >= 8 && rng.nextDouble() < 0.2
            out += Drop(Resource.ENERGY.itemId, if (two) 2 else 1)
        }
        if (isGrass && rng.nextDouble() < SEED_CHANCE) out += Drop(seedTier(rng.nextDouble()).seedItemId, 1)
        return out
    }
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
