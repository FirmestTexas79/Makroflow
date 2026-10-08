package cz.uhk.macroflow.pokemon.zone

/**
 * Mapa „Zóna 1“ v deníku a teleport mezi lokacemi (docs/adr/0052). Čistá pravidla, pokryto testy.
 *
 * Rozvržení (obdélníky, spoje východ → vchod, pozice NPC) generuje tools/mapgen/gen_zone.py
 * do assets/zone/zone1.json; tady jsou jen pravidla: co je vidět, kam se dá teleportovat.
 *
 * Tajné lokace (Zapomenutý háj) se na mapě ukážou jen tehdy, když v nich hráč právě stojí –
 * platí pro všechny tajné lokace i do budoucna.
 */
object ZoneOne {
    const val TITLE = "Zóna 1"

    /** Záložky zón na stránce Mapa (název, vybraná) – další zóny přibydou sem. */
    val ZONES = listOf(TITLE to true)

    /** Příznak objevené lokace (synchronizuje se se StoryFlags). */
    const val SEEN_PREFIX = "zone_seen_"
    fun seenKey(biome: String) = SEEN_PREFIX + biome

    /** Lokace zóny v pořadí kreslení. */
    val LOCATIONS = listOf("TOWN", "MEADOW", "FOREST", "HIDDEN_GROVE", "MOUNTAINS", "CAVE_MAZE", "MINES", "CAVE_OPEN", "SKY_PASS")

    /** Tajné lokace – na mapě jen, když v nich hráč stojí. */
    val SECRET = setOf("HIDDEN_GROVE")

    val NAMES = mapOf(
        "TOWN" to "Město", "MEADOW" to "Louka", "FOREST" to "Hvozd", "HIDDEN_GROVE" to "Zapomenutý háj",
        "MOUNTAINS" to "Hory", "CAVE_MAZE" to "Starý důl", "MINES" to "Doly", "CAVE_OPEN" to "Mechová jeskyně",
        "SKY_PASS" to "Nebeský průsmyk"
    )

    /** Lokace, kam se chodí přes hory – teleport je hlídá stejný krokový zámek jako vstup do hor. */
    val BEHIND_MOUNTAINS = setOf("MOUNTAINS", "CAVE_MAZE", "MINES", "CAVE_OPEN", "SKY_PASS")

    /** Uzel, kam teleport postaví hráče (u lokací z CaveMap jejich východ). */
    val ARRIVAL = mapOf(
        "TOWN" to "spawn", "MEADOW" to "rozcesti", "MOUNTAINS" to "rozcesti_hory",
        "FOREST" to "vstup_z_louky", "CAVE_MAZE" to "vychod_dolu", "CAVE_OPEN" to "vychod_jeskyne",
        "MINES" to "zpet_do_stoly", "SKY_PASS" to "vstup_ze_svatyne", "HIDDEN_GROVE" to "vstup_z_hvozdu"
    )

    enum class Shown {
        /** Nekreslí se vůbec (tajná lokace, ve které hráč nestojí). */
        HIDDEN,
        /** Neobjevená – jen obrys v mlze a „???“. */
        FOG,
        /** Objevená. */
        KNOWN,
        /** Tady hráč právě stojí. */
        HERE
    }

    fun shown(biome: String, current: String, seen: Set<String>): Shown = when {
        biome == current -> Shown.HERE
        biome in SECRET -> Shown.HIDDEN
        biome in seen -> Shown.KNOWN
        else -> Shown.FOG
    }

    /** Spoj se kreslí, když jsou vidět oba konce (tajný spoj jen s tajnou lokací na mapě). */
    fun linkShown(a: String, b: String, current: String, seen: Set<String>): Boolean =
        shown(a, current, seen) != Shown.HIDDEN && shown(b, current, seen) != Shown.HIDDEN

    /** Proč se sem teleportovat nejde (null = jde). */
    sealed class Block {
        object Here : Block()
        object Unknown : Block()
        object Secret : Block()
        /** Chybí denní kroky pro cestu přes hory. */
        data class Steps(val missing: Int) : Block()
        /** Hvozd pustí dál až po splněných úkolech. */
        data class Forest(val done: Int, val need: Int) : Block()
        /** Na placený teleport nestačí energie (docs/adr/0065). */
        data class Energy(val need: Int, val have: Int) : Block()
    }

    // ── Teleport a energie (docs/adr/0065) ─────────────────────────────────

    /** Teleportů za den zdarma; další stojí jako cesta pěšky. */
    const val FREE_TELEPORTS = 10
    /** Cena jednoho přechodu mezi lokacemi (Stamina.Action.TRANSITION). */
    const val HOP_COST = 3

    /** Spoje lokací – stejné jako „links“ v assets/zone/zone1.json (hlídá test). */
    val LINKS = listOf(
        "TOWN" to "MEADOW", "MEADOW" to "FOREST", "MEADOW" to "MOUNTAINS", "MOUNTAINS" to "SKY_PASS",
        "MOUNTAINS" to "CAVE_OPEN", "MOUNTAINS" to "CAVE_MAZE", "CAVE_MAZE" to "MINES", "FOREST" to "HIDDEN_GROVE"
    )

    /** Počet přechodů po nejkratší cestě (BFS); null = nespojeno. */
    fun hops(from: String, to: String): Int? {
        if (from == to) return 0
        val next = LINKS.flatMap { (a, b) -> listOf(a to b, b to a) }.groupBy({ it.first }, { it.second })
        val dist = mutableMapOf(from to 0)
        val queue = ArrayDeque(listOf(from))
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            for (n in next[cur].orEmpty()) if (n !in dist) {
                dist[n] = dist.getValue(cur) + 1
                if (n == to) return dist[n]
                queue.addLast(n)
            }
        }
        return null
    }

    /** Cena teleportu: prvních [FREE_TELEPORTS] za den zdarma, pak [HOP_COST] za každý přechod cesty. */
    fun teleportCost(from: String, to: String, usedToday: Int): Int =
        if (usedToday < FREE_TELEPORTS) 0 else (hops(from, to) ?: 1) * HOP_COST

    /**
     * [missingMountainSteps] = kolik kroků dnes chybí do zámku hor (0 = otevřeno),
     * [forestDone] / [forestNeed] = splněné úkoly pro vstup do Hvozdu.
     */
    fun teleportBlock(
        target: String, current: String, seen: Set<String>,
        missingMountainSteps: Int, forestDone: Int, forestNeed: Int,
        usedToday: Int = 0, energy: Int = Int.MAX_VALUE
    ): Block? = when {
        target == current -> Block.Here
        target in SECRET -> Block.Secret
        target !in seen -> Block.Unknown
        target in BEHIND_MOUNTAINS && missingMountainSteps > 0 -> Block.Steps(missingMountainSteps)
        target == "FOREST" && forestDone < forestNeed -> Block.Forest(forestDone, forestNeed)
        teleportCost(current, target, usedToday) > energy -> Block.Energy(teleportCost(current, target, usedToday), energy)
        else -> null
    }

    /**
     * Objevené lokace u starších uložených her (před touto mapou se návštěvy nezapisovaly):
     * odvozeno z příběhových příznaků a z lokací, jejichž quest už běží ([questBiomes]).
     */
    fun inferSeen(flags: Set<String>, questBiomes: Set<String>): Set<String> {
        val s = HashSet<String>()
        s += "TOWN"
        flags.filter { it.startsWith(SEEN_PREFIX) }.forEach { s += it.removePrefix(SEEN_PREFIX) }
        s += questBiomes.filter { it in LOCATIONS }
        fun any(vararg k: String) = k.any { it in flags }
        if (any("boss_defeated_BLUE", "crystal_BLUE")) s += "CAVE_OPEN"
        if (any("boss_defeated_RED", "crystal_RED", "mines_visited")) s += "CAVE_MAZE"
        if (any("mines_visited")) s += "MINES"
        if (any("sky_pass_visited")) s += "SKY_PASS"
        if (any("forest_rot_defeated", "grove_found")) s += "FOREST"
        if (any("grove_found")) s += "HIDDEN_GROVE"
        // kdo byl za horami, prošel horami i loukou; kdo byl v Hvozdu, prošel loukou
        if (s.any { it in BEHIND_MOUNTAINS }) s += "MOUNTAINS"
        if ("MOUNTAINS" in s || "FOREST" in s) s += "MEADOW"
        return s
    }
}
