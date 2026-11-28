package cz.uhk.macroflow.pokemon.forge

import cz.uhk.macroflow.pokemon.MakromonType
import cz.uhk.macroflow.pokemon.skills.Resource
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow

/**
 * Výheň – minihra regionu 1 (docs/adr/0118). Bez Androidu, pokryto testy.
 *
 *  - Měna: uhlíky. Klepnutím šlape hráč měch, výheň hoří i sama (i když hra neběží – Popelník).
 *  - Devět nekonečných vylepšení jen za uhlíky. Sleva: cena × 1 / (1 + 0,1 × úroveň).
 *    Odemykají se postupně: od začátku jen Silnější měch, každé další po vydělání určité částky v tomto kole
 *    ([UNLOCK], bar mezi pecí a panelem). Přetavení zamkne vše znovu.
 *  - Přetavení (reborn): vynuluje uhlíky a vylepšení za uhlíky, nechá pasivní bonusy; cena roste o řády
 *    (10 mil., 1 000 stříbrných, 10 000 stříbrných) a každé dá legendární artefakt.
 *  - Pasivní bonusy pro běžné hraní (obrana, HP, únik z ballu, odolnosti proti elementům) za uhlíky + suroviny
 *    tří povolání regionu 1 (rudy, dřevo, brouci). Přetavení je nesmaže.
 *  - Kroky a spánek ze skutečného dne zvyšují výdělek (vylepšení 8 a 9).
 */
object Forge {

    enum class Up(val icon: Int, val label: String, val base: Double, val growth: Double) {
        BELLOWS(0, "Silnější měch", 10.0, 1.15),
        SPARK(1, "Jiskra", 60.0, 1.16),
        DISCOUNT(2, "Sleva", 200.0, 1.17),
        AUTO(3, "Výheň hoří sama", 500.0, 1.18),
        HEARTH(4, "Druhé ohniště", 2_500.0, 1.19),
        HEAT(5, "Násobič žáru", 8_000.0, 1.20),
        ASH(6, "Popelník", 30_000.0, 1.22),
        STEPS(7, "Bonus za kroky", 120_000.0, 1.24),
        SLEEP(8, "Bonus za spánek", 400_000.0, 1.26);
    }

    /**
     * Kolik uhlíků je potřeba vydělat celkem v tomto kole, aby se odemklo vylepšení s daným pořadím
     * (index 0 = Jiskra, tj. druhé v pořadí; Silnější měch je odemčený od začátku).
     */
    val UNLOCK = listOf(100.0, 1_000.0, 5_000.0, 25_000.0, 50_000.0, 100_000.0, 500_000.0, 2_000_000.0)

    data class State(
        val coals: Double = 0.0,
        val levels: List<Int> = List(Up.entries.size) { 0 },
        val reborns: Int = 0,
        /** Úrovně pasivních bonusů (id → úroveň). */
        val passives: Map<String, Int> = emptyMap(),
        /** Kolik uhlíků se v tomto kole vydělalo celkem (útrata ho nesnižuje); řídí odemykání vylepšení. */
        val earned: Double = 0.0
    ) {
        fun lvl(u: Up) = levels[u.ordinal]
    }

    /** Přidá vydělané uhlíky (do zůstatku i do počtu vydělaných v tomto kole). */
    fun earn(st: State, v: Double) = if (v <= 0.0) st else st.copy(coals = st.coals + v, earned = st.earned + v)

    /** Je vylepšení odemčené? Silnější měch vždy, ostatní po vydělání prahu (nebo když už mají úroveň – starší uložení). */
    fun unlocked(st: State, u: Up) = u.ordinal == 0 || st.lvl(u) > 0 || st.earned >= UNLOCK[u.ordinal - 1]

    /** Práh, který se odemkne příště: vylepšení a počet uhlíků; null, když je odemčeno všechno. */
    fun nextUnlock(st: State): Pair<Up, Double>? =
        Up.entries.firstOrNull { !unlocked(st, it) }?.let { it to UNLOCK[it.ordinal - 1] }

    /** Skutečný den: kroky dnes a spánek poslední noci v hodinách. */
    data class Life(val steps: Int = 0, val sleepHours: Double = 0.0)

    // ── výdělek ──
    const val CRIT_MULT = 3.0
    const val STEPS_CAP = 20_000
    const val SLEEP_CAP_H = 9.0
    const val OFFLINE_CAP_H = 24

    /** Podíl, který úroveň odemkne: 1 − 1 / (1 + 0,1 × úroveň) – roste k 100 %, nikdy ho nepřekročí. */
    fun share(level: Int) = 1.0 - 1.0 / (1.0 + 0.1 * level)

    /** Kroky dnes: každých 10 000 kroků (nejvýš 20 000) až +100 % podle úrovně. */
    fun stepsMult(level: Int, steps: Int) = 1.0 + min(steps, STEPS_CAP).coerceAtLeast(0) / 10_000.0 * share(level)

    /** Spánek poslední noci: 8 h (nejvýš 9 h) až +100 % podle úrovně. */
    fun sleepMult(level: Int, hours: Double) = 1.0 + hours.coerceIn(0.0, SLEEP_CAP_H) / 8.0 * share(level)
    fun heatMult(level: Int) = 1.0 + 0.05 * level

    fun multiplier(st: State, life: Life): Double =
        heatMult(st.lvl(Up.HEAT)) * stepsMult(st.lvl(Up.STEPS), life.steps) * sleepMult(st.lvl(Up.SLEEP), life.sleepHours) *
            (if (st.reborns >= 1) Artifact.HEART.factor else 1.0)

    fun perTap(st: State, life: Life) = (1 + st.lvl(Up.BELLOWS)) * multiplier(st, life)
    fun perSecond(st: State, life: Life) = (1.0 * st.lvl(Up.AUTO) + 8.0 * st.lvl(Up.HEARTH)) * multiplier(st, life)

    /** Šance na jiskru (trojnásobek klepnutí): 1 − 1 / (1 + 0,02 × úroveň) – blíží se 100 %, nikdy ji nedosáhne. */
    fun critChance(level: Int) = 1.0 - 1.0 / (1.0 + 0.02 * level)

    /** Kolik výheň vydělá, když hra neběží: 10 % → skoro 100 % s Popelníkem. */
    fun offlineFraction(level: Int) = 0.1 + 0.9 * share(level)

    fun offlineGain(st: State, life: Life, elapsedMs: Long): Double {
        val sec = min(elapsedMs.coerceAtLeast(0), OFFLINE_CAP_H * 3_600_000L) / 1000.0
        return perSecond(st, life) * sec * offlineFraction(st.lvl(Up.ASH))
    }

    // ── ceny ──
    fun discountMult(level: Int) = 1.0 / (1.0 + 0.10 * level)
    fun discountPct(level: Int) = 100.0 * (1.0 - discountMult(level))

    /** Kladivo mistra: −1 % za každou úroveň Silnějšího měchu (násobně, takže nikdy nula). */
    fun hammerMult(st: State) = if (st.reborns >= 3) 0.99.pow(st.lvl(Up.BELLOWS)) else 1.0

    fun cost(st: State, u: Up): Double = u.base * u.growth.pow(st.lvl(u)) * discountMult(st.lvl(Up.DISCOUNT)) * hammerMult(st)

    fun buy(st: State, u: Up): State? {
        if (!unlocked(st, u)) return null
        val c = cost(st, u)
        if (st.coals < c) return null
        return st.copy(coals = st.coals - c, levels = st.levels.toMutableList().also { it[u.ordinal]++ })
    }

    /** Koupí, kolik jde; vrací nový stav a počet koupených úrovní. */
    fun buyMax(st: State, u: Up, limit: Int = 10_000): Pair<State, Int> {
        var s = st; var n = 0
        while (n < limit) { s = buy(s, u) ?: break; n++ }
        return s to n
    }

    // ── přetavení a artefakty ──
    enum class Artifact(val icon: Int, val label: String, val text: String, val factor: Double) {
        HEART(21, "Srdce výhně", "Výheň generuje 10× víc uhlíků.", 10.0),
        ANVIL(22, "Kovadlina předků", "Všechny pasivní bonusy z výhně jsou dvojnásobné.", 2.0),
        HAMMER(23, "Kladivo mistra", "Každá úroveň Silnějšího měchu zlevní všechna vylepšení o 1 %.", 0.99)
    }

    /** 10 mil., 1 000 stříbrných, 10 000 stříbrných – simulace při ~10 min hraní denně: ~1,5 dne, ~5,5 dne, ~40 dní. */
    val REBORN_COSTS = listOf(1e7, 1e9, 1e10)

    fun rebornCost(st: State): Double? = REBORN_COSTS.getOrNull(st.reborns)
    fun canReborn(st: State) = rebornCost(st)?.let { st.coals >= it } == true

    fun reborn(st: State): State? =
        if (!canReborn(st)) null else State(reborns = st.reborns + 1, passives = st.passives)   // earned = 0: vylepšení se zamknou

    fun artifacts(st: State) = Artifact.entries.take(st.reborns.coerceAtMost(Artifact.entries.size))

    // ── pasivní bonusy (uhlíky + suroviny regionu 1) ──
    sealed interface Effect {
        data class Defense(val add: Double) : Effect
        data class Hp(val add: Double) : Effect
        data class Escape(val add: Double) : Effect
        data class Resist(val type: MakromonType, val add: Double) : Effect
    }

    enum class Passive(
        val id: String, val icon: Int, val label: String, val text: String, val maxLevel: Int,
        val coals: Double, val coalGrowth: Double, val materials: Map<Resource, Int>, val effect: Effect
    ) {
        BELT("pas", 12, "Kovaný pás", "+5 % obrana týmu", 1, 5_000.0, 1.0, mapOf(Resource.ORE_COPPER to 20), Effect.Defense(0.05)),
        OAK_SHIELD("dub", 13, "Dubový štít", "−10 % poškození od trávy", 1, 10_000.0, 1.0, mapOf(Resource.LOG_OAK to 30), Effect.Resist(MakromonType.GRASS, 0.10)),
        SPARK_NET("sit", 14, "Jiskrová síť", "−5 % šance na únik z ballu", 1, 20_000.0, 1.0, mapOf(Resource.BUG_SPARK to 25), Effect.Escape(0.05)),
        TEMPER("kaleni", 15, "Kalení", "+2 % obrana týmu za stupeň", 5, 50_000.0, 4.0, emptyMap(), Effect.Defense(0.02)),
        SILVER_AMULET("amulet", 16, "Stříbrný amulet", "−10 % poškození od země", 1, 200_000.0, 1.0,
            mapOf(Resource.ORE_SILVER to 30, Resource.LOG_BIRCH to 30), Effect.Resist(MakromonType.GROUND, 0.10)),
        CRYSTAL_SHIELD("krystal", 17, "Krystalový štít", "−10 % poškození od elektřiny", 1, 500_000.0, 1.0,
            mapOf(Resource.BUG_CRYSTAL to 40), Effect.Resist(MakromonType.ELECTRIC, 0.10)),
        EMBER_HEART("srdce", 18, "Žárové srdce", "+5 % HP týmu", 1, 2_000_000.0, 1.0,
            mapOf(Resource.ORE_GOLD to 25, Resource.LOG_MAPLE to 25), Effect.Hp(0.05)),
        MAGMA_CLOAK("magma", 19, "Magmový plášť", "−15 % poškození od ohně", 1, 5_000_000.0, 1.0,
            mapOf(Resource.BUG_MAGMA to 40, Resource.ORE_GOLD to 10), Effect.Resist(MakromonType.FIRE, 0.15)),
        MASTER("mistr", 20, "Mistrovská výheň", "−3 % šance na únik z ballu za stupeň", 5, 10_000_000.0, 3.0, emptyMap(), Effect.Escape(0.03));

        val itemId: String get() = "forge_p_$id"
    }

    fun passiveLevel(st: State, p: Passive) = st.passives[p.id] ?: 0
    fun passiveCoals(st: State, p: Passive) = p.coals * p.coalGrowth.pow(passiveLevel(st, p))

    /** Odolnosti a obrana mají strop, aby souboje nepřestaly být těžké. */
    const val RESIST_CAP = 0.40
    const val DEFENSE_CAP = 0.50
    const val HP_CAP = 0.30

    data class Bonuses(val defense: Double, val hp: Double, val escape: Double, val resist: Map<MakromonType, Double>) {
        val defenseMult get() = 1.0 + defense
        val hpMult get() = 1.0 + hp
        fun damageMult(type: MakromonType) = 1.0 - (resist[type] ?: 0.0)
    }

    fun bonuses(st: State): Bonuses {
        val k = if (st.reborns >= 2) Artifact.ANVIL.factor else 1.0
        var def = 0.0; var hp = 0.0; var esc = 0.0
        val res = mutableMapOf<MakromonType, Double>()
        for (p in Passive.entries) {
            val l = passiveLevel(st, p).takeIf { it > 0 } ?: continue
            when (val e = p.effect) {
                is Effect.Defense -> def += e.add * l
                is Effect.Hp -> hp += e.add * l
                is Effect.Escape -> esc += e.add * l
                is Effect.Resist -> res[e.type] = (res[e.type] ?: 0.0) + e.add * l
            }
        }
        return Bonuses((def * k).coerceAtMost(DEFENSE_CAP), (hp * k).coerceAtMost(HP_CAP), esc * k,
            res.mapValues { (it.value * k).coerceAtMost(RESIST_CAP) })
    }

    // ── měny: od 100 mil. stříbrné uhlíky (1 = 1 000 000), od 100 mil. stříbrných zlaté ──
    enum class Coin(val icon: Int, val unit: Double, val label: String) {
        COAL(9, 1.0, "uhlíků"), SILVER(10, 1e6, "stříbrných"), GOLD(11, 1e12, "zlatých")
    }

    const val SWITCH = 1e8

    fun coinFor(v: Double): Coin = when {
        v >= SWITCH * Coin.SILVER.unit -> Coin.GOLD     // 100 mil. stříbrných
        v >= SWITCH -> Coin.SILVER
        else -> Coin.COAL
    }

    /** Částka v nejvhodnější měně: „12 345“, „100,5“ (stříbrných), čísla se mezerou po tisících. */
    fun amount(v: Double): Pair<Coin, String> {
        val c = coinFor(v)
        val x = v / c.unit
        val s = when {
            c == Coin.COAL -> group(floor(x).toLong())
            x < 1000 -> trim1(x)
            x < 1e15 -> group(floor(x).toLong())
            else -> String.format(java.util.Locale.US, "%.2e", x)
        }
        return c to s
    }

    fun format(v: Double): String = amount(v).let { (c, s) -> "$s ${c.label}" }

    private fun trim1(x: Double): String {
        val t = floor(x * 10) / 10
        return if (t == floor(t)) group(t.toLong()) else group(floor(t).toLong()) + "," + ((t * 10).toLong() % 10)
    }

    private fun group(n: Long): String = n.toString().reversed().chunked(3).joinToString(" ").reversed()
}
