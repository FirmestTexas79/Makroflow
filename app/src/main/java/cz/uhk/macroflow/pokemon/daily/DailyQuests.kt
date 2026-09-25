package cz.uhk.macroflow.pokemon.daily

import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Denní úkoly v deníku (docs/adr/0029), bez Androidu – pokryto testy.
 *
 * Každý den 3 úkoly: jeden z Makrosvěta (souboje, chytání), jeden z jídla nebo pití a jeden
 * z pohybu či zapisování. Výběr je deterministický podle data (stejné úkoly po restartu,
 * na jiném zařízení i po přeinstalaci). Odměna 10–50 makro penízků podle náročnosti.
 * Cíle jídla a pití se odvozují z osobních cílů uživatele, aby šly splnit zdravě.
 */
object DailyQuests {

    enum class Group { GAME, FOOD, ACTIVITY }

    enum class Kind(val group: Group) {
        WIN_TYPE(Group.GAME), WIN_ANY(Group.GAME), CATCH(Group.GAME), WIN_IN_BIOME(Group.GAME),
        WATER(Group.FOOD), PROTEIN(Group.FOOD), FIBER(Group.FOOD), MEALS(Group.FOOD),
        STEPS(Group.ACTIVITY), WORKOUT_SETS(Group.ACTIVITY), CHECK_IN(Group.ACTIVITY)
    }

    data class Quest(
        val index: Int,
        val kind: Kind,
        val target: Int,
        /** Typ Makromona (WIN_TYPE) nebo biom (WIN_IN_BIOME). */
        val param: String? = null,
        val reward: Int,
        val title: String,
        val description: String,
        val unit: String = ""
    )

    /** Co se dnes stalo – z databáze a z počítadel soubojů. */
    data class Facts(
        val winsByType: Map<String, Int> = emptyMap(),
        val winsByBiome: Map<String, Int> = emptyMap(),
        val catches: Int = 0,
        val waterMl: Int = 0,
        val proteinG: Int = 0,
        val fiberG: Int = 0,
        val meals: Int = 0,
        val steps: Int = 0,
        val workoutSets: Int = 0,
        val checkIn: Boolean = false
    ) {
        val wins: Int get() = winsByType.values.sum()
    }

    /** Osobní cíle pro odvození náročnosti (null = rozumné výchozí hodnoty). */
    data class Targets(val waterMl: Int? = null, val proteinG: Int? = null, val stepGoal: Int? = null)

    /** Typy běžných Makromonů (ne jen noční nebo legendární) – česky 4. pád množného čísla. */
    val TYPE_NAMES = linkedMapOf(
        "WATER" to "vodní", "FIRE" to "ohnivé", "GRASS" to "travní", "NORMAL" to "normální"
    )
    val BIOME_NAMES = linkedMapOf(
        "MEADOW" to "na Louce", "MOUNTAINS" to "v Horách", "FOREST" to "ve Hvozdu", "CAVE" to "v jeskyni"
    )

    private fun roundTo(v: Double, step: Int) = ((v / step).roundToInt() * step).coerceAtLeast(step)

    /** Kandidáti skupiny pro daný den (náhoda řídí jen parametry). */
    private fun candidates(group: Group, t: Targets, rnd: Random, unlockedBiomes: Set<String>): List<Quest> = when (group) {
        Group.GAME -> {
            val type = TYPE_NAMES.keys.elementAt(rnd.nextInt(TYPE_NAMES.size))
            val typeCount = 2 + rnd.nextInt(2)                                  // 2–3
            val biomes = BIOME_NAMES.keys.filter { it in unlockedBiomes }.ifEmpty { listOf("MEADOW") }
            val biome = biomes[rnd.nextInt(biomes.size)]
            val biomeReward = when (biome) { "CAVE" -> 50; "MOUNTAINS" -> 40; "FOREST" -> 35; else -> 25 }
            listOf(
                Quest(0, Kind.WIN_TYPE, typeCount, type, 20 + typeCount * 5,
                    "Lovec: ${TYPE_NAMES.getValue(type)}", "Poraz $typeCount ${TYPE_NAMES.getValue(type)} Makromony"),
                Quest(0, Kind.WIN_ANY, 5, null, 25, "Aréna", "Vyhraj 5 soubojů s divokými Makromony"),
                Quest(0, Kind.CATCH, 2, null, 35, "Sběratel", "Chyť 2 Makromony"),
                Quest(0, Kind.WIN_IN_BIOME, 2, biome, biomeReward,
                    "Průzkumník", "Vyhraj 2 souboje ${BIOME_NAMES.getValue(biome)}")
            )
        }
        Group.FOOD -> {
            val water = roundTo((t.waterMl ?: 2500).toDouble(), 250)
            val protein = roundTo((t.proteinG ?: 120) * 0.9, 5)
            listOf(
                Quest(0, Kind.WATER, water, null, 20, "Hydratace", "Vypij ${liters(water)} l vody", "ml"),
                Quest(0, Kind.PROTEIN, protein, null, 30, "Stavební kameny", "Sněz $protein g bílkovin", "g"),
                Quest(0, Kind.FIBER, 25, null, 25, "Vláknina", "Sněz 25 g vlákniny", "g"),
                Quest(0, Kind.MEALS, 4, null, 15, "Pravidelnost", "Zapiš 4 jídla")
            )
        }
        Group.ACTIVITY -> {
            val steps = roundTo((t.stepGoal ?: 8000).toDouble(), 500)
            listOf(
                Quest(0, Kind.STEPS, steps, null, if (steps >= 10000) 40 else 30, "Chodec", "Ujdi ${thousands(steps)} kroků", "kroků"),
                Quest(0, Kind.WORKOUT_SETS, 12, null, 40, "Železo", "Zapiš 12 sérií do tréninkového deníku"),
                Quest(0, Kind.CHECK_IN, 1, null, 10, "Ranní rituál", "Udělej ranní check-in")
            )
        }
    }

    /**
     * Tři úkoly na den [epochDay]. [unlockedBiomes] = kam hráč smí (úkol „vyhraj v Horách“
     * nedostane, dokud Hory nemá odemčené).
     */
    fun forDay(epochDay: Long, targets: Targets = Targets(), unlockedBiomes: Set<String> = setOf("MEADOW")): List<Quest> {
        val rnd = Random(epochDay * 7919 + 17)
        return Group.entries.mapIndexed { i, g ->
            val list = candidates(g, targets, rnd, unlockedBiomes)
            list[rnd.nextInt(list.size)].copy(index = i)
        }
    }

    fun progress(q: Quest, f: Facts): Int = when (q.kind) {
        Kind.WIN_TYPE -> f.winsByType[q.param] ?: 0
        Kind.WIN_ANY -> f.wins
        Kind.CATCH -> f.catches
        Kind.WIN_IN_BIOME -> if (q.param == "CAVE") (f.winsByBiome["CAVE_OPEN"] ?: 0) + (f.winsByBiome["CAVE_MAZE"] ?: 0)
            else f.winsByBiome[q.param] ?: 0
        Kind.WATER -> f.waterMl
        Kind.PROTEIN -> f.proteinG
        Kind.FIBER -> f.fiberG
        Kind.MEALS -> f.meals
        Kind.STEPS -> f.steps
        Kind.WORKOUT_SETS -> f.workoutSets
        Kind.CHECK_IN -> if (f.checkIn) 1 else 0
    }.coerceAtLeast(0)

    fun isDone(q: Quest, f: Facts) = progress(q, f) >= q.target

    fun fraction(q: Quest, f: Facts): Float = (progress(q, f).toFloat() / q.target).coerceIn(0f, 1f)

    /** „1 250 / 2 500 ml“, „2 / 3“. */
    fun progressText(q: Quest, f: Facts): String {
        val p = progress(q, f).coerceAtMost(q.target)
        return when (q.kind) {
            Kind.WATER -> "${liters(p)} / ${liters(q.target)} l"
            Kind.STEPS -> "${thousands(p)} / ${thousands(q.target)}"
            Kind.CHECK_IN -> if (p >= 1) "hotovo" else "zatím ne"
            else -> "$p / ${q.target}" + if (q.unit.isNotEmpty()) " ${q.unit}" else ""
        }
    }

    fun liters(ml: Int): String {
        val l = ml / 1000.0
        return if (ml % 1000 == 0) "${ml / 1000}" else String.format(java.util.Locale.US, "%.2f", l).trimEnd('0').trimEnd('.').replace('.', ',')
    }

    fun thousands(n: Int): String = n.toString().reversed().chunked(3).joinToString(" ").reversed()
}
