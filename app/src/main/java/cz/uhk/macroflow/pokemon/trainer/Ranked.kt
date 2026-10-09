package cz.uhk.macroflow.pokemon.trainer

import android.content.Context
import cz.uhk.macroflow.pokemon.PokemonLevelCalc
import cz.uhk.macroflow.pokemon.Rarity
import cz.uhk.macroflow.pokemon.species.SpeciesRegistry
import cz.uhk.macroflow.pokemon.wild.MovePool
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Hodnocené zápasy v Aréně (docs/adr/0079): body, ranky a soupeři, kteří s rankem sílí.
 *
 * Výhra +20, prohra −12; na začátku ranku se nespadne níž (jen v Bronzu jde až na 0).
 * Soupeř má tolik Makromonů, kolikátý rank to je (Bronz 1 … Mistr 6), level podle průměru
 * tvého týmu posunutý s rankem (−2 … +3) a od Zlata i vzácnější druhy.
 */
object Ranked {

    const val WIN = 20
    const val LOSS = 12
    /** Víc než tolik bodů za jeden zápas nejde (hlídají to i pravidla Firestore). */
    const val MAX_GAIN = 25

    enum class Tier(val label: String, val ascii: String, val emoji: String, val min: Int, val color: Int,
                    val levelDelta: Int, val rarities: Set<Rarity>) {
        BRONZE("Bronz", "BRONZE", "🥉", 0, 0xFFB07A45.toInt(), -2, setOf(Rarity.COMMON)),
        SILVER("Stříbro", "SILVER", "🥈", 100, 0xFF8E98A3.toInt(), -1, setOf(Rarity.COMMON)),
        GOLD("Zlato", "GOLD", "🥇", 250, 0xFFD9A322.toInt(), 0, setOf(Rarity.COMMON, Rarity.RARE)),
        PLATINUM("Platina", "PLATINUM", "💠", 450, 0xFF3FA6A0.toInt(), 1, setOf(Rarity.COMMON, Rarity.RARE)),
        DIAMOND("Diamant", "DIAMOND", "💎", 700, 0xFF4F86E0.toInt(), 2, setOf(Rarity.COMMON, Rarity.RARE, Rarity.EPIC)),
        MASTER("Mistr", "MASTER", "👑", 1000, 0xFF9B4FD6.toInt(), 3, setOf(Rarity.RARE, Rarity.EPIC));

        /** Počet Makromonů soupeře. */
        val teamSize: Int get() = ordinal + 1
        val next: Tier? get() = entries.getOrNull(ordinal + 1)
    }

    fun tierOf(points: Int): Tier = Tier.entries.last { points >= it.min }

    /** Postup v ranku 0..1 (Mistr = 1). */
    fun progress(points: Int): Float {
        val t = tierOf(points); val n = t.next ?: return 1f
        return ((points - t.min).toFloat() / (n.min - t.min)).coerceIn(0f, 1f)
    }

    data class Result(val before: Int, val after: Int) {
        val delta: Int get() = after - before
        val promoted: Boolean get() = tierOf(after).ordinal > tierOf(before).ordinal
        val demoted: Boolean get() = tierOf(after).ordinal < tierOf(before).ordinal
    }

    /** Nové body po zápase; prohra neshodí pod začátek současného ranku. */
    fun apply(points: Int, won: Boolean): Result {
        val p = points.coerceAtLeast(0)
        val after = if (won) p + WIN else maxOf(tierOf(p).min, p - LOSS)
        return Result(p, after)
    }

    // ── Soupeři ──

    private val NAMES = listOf(
        "Pepa Pumpa", "Lenka Leg Day", "Ivan Izolace", "Bára Biceps", "Olda Objem", "Rudla Rep",
        "Šárka Šprint", "Tonda Triceps", "Vlasta Výpad", "Hanka HIIT", "Dan Deadlift", "Míla Makro",
        "Kryštof Kreatin", "Zuzka Zádová", "Filip Fázka", "Radek Rovnováha"
    )

    /**
     * Hodnocený soupeř pro [points] a hráčův tým. [seed] = počet odehraných zápasů, takže po každém
     * zápase je jiný, ale do té doby pořád stejný (náhled v menu = soupeř v souboji).
     */
    fun opponent(points: Int, my: List<TrainerMon>, seed: Int): Trainer {
        val tier = tierOf(points)
        val rnd = Random(seed * 7919L + tier.ordinal * 31L + 5)
        val avg = if (my.isEmpty()) 3 else my.map { it.level }.average().roundToInt()
        val level = (avg + tier.levelDelta).coerceIn(1, PokemonLevelCalc.MAX_LEVEL)
        val pool = SpeciesRegistry.PLAYABLE.filter { sp -> sp.spawns.any { it.rarity in tier.rarities } }.shuffled(rnd)
        val team = pool.take(tier.teamSize).map { sp ->
            // mírný rozptyl levelů v týmu, vedoucí Makromon nejsilnější
            TrainerMon(sp.id, (level - rnd.nextInt(0, 2)).coerceAtLeast(1), MovePool.wildMoveset(sp.id, level, rnd).map { it.name })
        }.sortedByDescending { it.level }
        return Trainer("ai:ranked:${tier.name}:$seed", NAMES[rnd.nextInt(NAMES.size)], Trainer.Kind.AI, team, ranked = true)
    }

    /** Odměna za hodnocenou výhru (každá výhra) a jednorázově za nový rank. */
    fun coinsForWin(t: Tier): Int = 3 + t.ordinal * 2
    fun promotionCoins(t: Tier): Int = 40 * t.ordinal

    // ── Uložení v telefonu (cloud viz FirebaseRepository.updateArenaPoints) ──

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("GamePrefs", Context.MODE_PRIVATE)
    private const val POINTS = "ranked_points"
    private const val MATCHES = "ranked_matches"
    private const val BEST = "ranked_best_tier"

    fun points(ctx: Context) = prefs(ctx).getInt(POINTS, 0)
    fun matches(ctx: Context) = prefs(ctx).getInt(MATCHES, 0)

    /** Body z cloudu (jiný telefon / reinstalace) – vezme se vyšší. */
    fun syncFromCloud(ctx: Context, cloud: Int?) {
        if (cloud != null && cloud > points(ctx)) prefs(ctx).edit().putInt(POINTS, cloud).apply()
    }

    data class Outcome(val result: Result, val coins: Int)

    fun record(ctx: Context, won: Boolean): Outcome {
        val p = prefs(ctx)
        val r = apply(points(ctx), won)
        val tier = tierOf(r.after)
        val best = p.getInt(BEST, 0)
        var coins = if (won) coinsForWin(tier) else 0
        val e = p.edit().putInt(POINTS, r.after).putInt(MATCHES, matches(ctx) + 1)
        if (tier.ordinal > best) { coins += promotionCoins(tier); e.putInt(BEST, tier.ordinal) }
        e.apply()
        return Outcome(r, coins)
    }
}
