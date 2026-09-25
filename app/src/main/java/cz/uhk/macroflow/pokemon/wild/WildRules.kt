package cz.uhk.macroflow.pokemon.wild

import cz.uhk.macroflow.pokemon.BattleFactory
import cz.uhk.macroflow.pokemon.BiomeType
import cz.uhk.macroflow.pokemon.Makromon
import cz.uhk.macroflow.pokemon.MakromonGrowthManager
import cz.uhk.macroflow.pokemon.Move
import kotlin.random.Random

/**
 * Pravidla divokých Makromonů (docs/adr/0029), bez Androidu – pokryto testy:
 * level podle lokality, náhodná sada útoků z poolu druhu a odměna za výhru.
 */
object WildLevels {

    /** Level → váha. Město a louka 2–3, občas 4; Hvozd 3–6; Hory 4–8; jeskyně 6–12. */
    fun weights(biome: BiomeType): Map<Int, Int> = when (biome) {
        BiomeType.TOWN, BiomeType.MEADOW, BiomeType.LAKE, BiomeType.WATER -> mapOf(2 to 45, 3 to 45, 4 to 10)
        BiomeType.FOREST -> (3..6).associateWith { 1 }
        BiomeType.MOUNTAINS -> (4..8).associateWith { 1 }
        BiomeType.CAVE_OPEN, BiomeType.CAVE_MAZE -> (6..12).associateWith { 1 }
    }

    fun range(biome: BiomeType): IntRange = weights(biome).keys.let { it.min()..it.max() }

    fun roll(biome: BiomeType, rnd: Random = Random.Default): Int {
        val w = weights(biome)
        var pick = rnd.nextInt(w.values.sum())
        for ((level, weight) in w.toSortedMap()) {
            if (pick < weight) return level
            pick -= weight
        }
        return w.keys.max()
    }
}

/** Všechny útoky podle jména (sada útoků chyceného Makromona se ukládá jako jména). */
object MoveDex {

    private val SHARED: List<Move> by lazy {
        with(BattleFactory) {
            listOf(
                attackTackle(), attackScratch(), attackSlash(), attackGrowl(), attackHarden(), attackTailWhip(),
                attackLeer(), attackQuickAttack(), attackSlam(), attackBite(), attackCrunch(), attackSmokescreen(),
                attackHyperFang(), attackFuryAttack(), attackEmber(), attackFireFang(), attackFlamethrower(),
                attackFireBlast(), attackHeatWave(), attackWaterGun(), attackWaterPulse(), attackHydroPump(),
                attackAquaTail(), attackBubbleBeam(), attackVineWhip(), attackRazorLeaf(), attackSeedBomb(),
                attackSolarBeam(), attackLeafBlade(), attackSleepPowder(), attackShadowBall(), attackShadowPunch(),
                attackLick(), attackNightShade(), attackHex(), attackDazzlingGleam(), attackMoonblast(), attackCharm(),
                attackPlayRough(), attackBabyDollEyes(), attackDragonClaw(), attackDragonBreath(), attackDragonPulse(),
                attackOutrage(), attackSandAttack(), attackMudSlap(), attackDig(), attackEarthquake(), attackPsychic(),
                attackHypnosis(), attackThunderShock(), attackThunderbolt(), attackPoisonSting(), attackSludgeBomb(),
                attackToxic(), attackConfuseRay(), attackWillOWisp(), attackStringShot(), attackGust(), attackWingAttack()
            )
        }
    }

    /** Sdílené útoky + vlastní útoky druhů (SPITE, DARK PULSE …) + útoky z růstových křivek. */
    val byName: Map<String, Move> by lazy {
        val all = mutableListOf<Move>()
        all += SHARED
        SpeciesIds.ALL.forEach { id ->
            all += BattleFactory.createById(id).moves
            all += MakromonGrowthManager.getProfile(id)?.movesLearnedAt?.map { it.move }.orEmpty()
        }
        all.associateBy { it.name }
    }

    fun get(name: String): Move? = byName[name]?.let { it.copy(pp = it.maxPp) }
}

object SpeciesIds {
    val ALL: List<String> = (1..31).map { "%03d".format(it) }
}

/**
 * Pool útoků druhu a náhodná sada pro divokého Makromona.
 *
 *  - základní útoky druhu (z BattleFactory) – silné jsou až od vyššího levelu:
 *    síla ≥ 80 od levelu [STRONG_LEVEL], síla ≥ 95 od levelu [VERY_STRONG_LEVEL],
 *  - útoky z růstové křivky druhu i jeho předchozích vývojových stupňů do aktuálního levelu,
 *  - první základní útok (typ druhu) je v sadě vždy, aby Makromon zůstal „svým“ typem.
 */
object MovePool {

    const val STRONG_LEVEL = 6
    const val VERY_STRONG_LEVEL = 9
    const val MAX_MOVES = 4

    fun defaultUnlocked(m: Move, level: Int): Boolean = when {
        m.power >= 95 -> level >= VERY_STRONG_LEVEL
        m.power >= 80 -> level >= STRONG_LEVEL
        else -> true
    }

    /** Druhy, ze kterých se [id] vyvinul (Ignaroc → Ignar). */
    fun ancestors(id: String): List<String> {
        val out = mutableListOf<String>()
        var cur = id
        repeat(5) {
            val parent = SpeciesIds.ALL.firstOrNull { MakromonGrowthManager.getProfile(it)?.evolutionToId == cur } ?: return out
            out += parent; cur = parent
        }
        return out
    }

    /** Útoky, které druh na daném levelu může umět (bez duplicit, v pořadí: základní, pak učené). */
    fun pool(id: String, level: Int): List<Move> {
        val defaults = BattleFactory.createById(id).moves
        val signature = defaults.firstOrNull()
        val base = defaults.filter { it == signature || defaultUnlocked(it, level) }
        val learned = (listOf(id) + ancestors(id)).flatMap { sp ->
            MakromonGrowthManager.getProfile(sp)?.movesLearnedAt.orEmpty().filter { it.level <= level }.map { it.move }
        }
        return (base + learned).distinctBy { it.name }
    }

    /**
     * Náhodná sada až 4 útoků: podpisový útok druhu + aspoň jeden útočný (se zraněním),
     * zbytek náhodně, novější (výše učené) útoky mají větší šanci.
     */
    fun wildMoveset(id: String, level: Int, rnd: Random = Random.Default): List<Move> {
        val pool = pool(id, level)
        if (pool.size <= MAX_MOVES) return pool
        val signature = BattleFactory.createById(id).moves.firstOrNull()?.let { s -> pool.firstOrNull { it.name == s.name } }
        val chosen = mutableListOf<Move>()
        signature?.let { chosen += it }
        if (chosen.none { it.power > 0 }) {
            pool.filter { it.power > 0 && it !in chosen }.randomOrNullWith(rnd)?.let { chosen += it }
        }
        val rest = pool.filter { c -> chosen.none { it.name == c.name } }.toMutableList()
        while (chosen.size < MAX_MOVES && rest.isNotEmpty()) {
            // váha podle pořadí v poolu – učené (pozdější) útoky jsou na konci
            val weights = rest.indices.map { it + 2 }
            var pick = rnd.nextInt(weights.sum())
            var idx = 0
            while (pick >= weights[idx]) { pick -= weights[idx]; idx++ }
            chosen += rest.removeAt(idx)
        }
        return chosen
    }

    private fun <T> List<T>.randomOrNullWith(rnd: Random): T? = if (isEmpty()) null else this[rnd.nextInt(size)]

    /** Útoky chyceného Makromona z uložených jmen; prázdné / neznámé → základní útoky druhu. */
    fun resolve(moveListStr: String, fallback: List<Move>): List<Move> {
        val names = moveListStr.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val moves = names.mapNotNull { MoveDex.get(it) }.distinctBy { it.name }.take(MAX_MOVES)
        return moves.ifEmpty { fallback }
    }

    fun namesOf(moves: List<Move>): String = moves.joinToString(",") { it.name }
}

/** Odměny za souboj. */
object BattleRewards {
    const val MIN_COINS = 1
    const val MAX_COINS = 5

    /** Makro penízky za poraženého divokého Makromona: 1–5. */
    fun coinsForWin(rnd: Random = Random.Default): Int = rnd.nextInt(MIN_COINS, MAX_COINS + 1)
}
