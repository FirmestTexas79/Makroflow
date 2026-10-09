package cz.uhk.macroflow.pokemon.trainer

import android.content.Context
import cz.uhk.macroflow.pokemon.CapturedMakromonEntity
import cz.uhk.macroflow.pokemon.PokemonLevelCalc
import cz.uhk.macroflow.pokemon.Rarity
import cz.uhk.macroflow.pokemon.species.SpeciesRegistry
import cz.uhk.macroflow.pokemon.wild.MovePool
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Pravidla arény (docs/adr/0076): tvůj tým jako duch, AI trenéři, výběr soupeřů a odměny.
 */
object Arena {

    const val GHOSTS_SHOWN = 6

    /** Tým hráče jako trenér (to, co se nahraje do cloudu jako jeho duch). */
    fun snapshot(id: String, name: String, mons: List<CapturedMakromonEntity>, now: Long): Trainer =
        Trainer(id, name, Trainer.Kind.GHOST, mons.take(Trainers.MAX_TEAM).map {
            TrainerMon(it.makromonId, it.level, it.moveListStr.split(',').map(String::trim).filter(String::isNotEmpty), it.isShiny)
        }, now)

    /** Duchové nejbližší síle hráče (bez jeho vlastního). */
    fun pickGhosts(ghosts: List<Trainer>, myId: String?, myPower: Int, n: Int = GHOSTS_SHOWN): List<Trainer> =
        ghosts.filter { it.id != myId }.sortedBy { abs(it.power - myPower) }.take(n)

    // ── AI trenéři: tři obtížnosti, týmy se mění každý den a rostou s hráčem ──

    private class Template(val key: String, val name: String, val sizeDelta: Int, val levelDelta: Int, val rarities: Set<Rarity>)

    private val TEMPLATES = listOf(
        Template("kardio", "Kája Kardio", -1, -2, setOf(Rarity.COMMON)),
        Template("bencpres", "Béďa Benčpres", 0, 0, setOf(Rarity.COMMON, Rarity.RARE)),
        Template("mrtvytah", "Mistr Mrtvý tah", 1, 2, setOf(Rarity.COMMON, Rarity.RARE, Rarity.EPIC))
    )

    fun aiTrainers(my: List<TrainerMon>, day: Long): List<Trainer> {
        val avg = if (my.isEmpty()) 3 else my.map { it.level }.average().roundToInt()
        val size = my.size.coerceAtLeast(1)
        return TEMPLATES.map { t ->
            val rnd = Random(t.key.hashCode() * 31L + day)
            val level = (avg + t.levelDelta).coerceIn(1, PokemonLevelCalc.MAX_LEVEL)
            val pool = SpeciesRegistry.PLAYABLE.filter { sp -> sp.spawns.any { it.rarity in t.rarities } }.shuffled(rnd)
            val team = pool.take((size + t.sizeDelta).coerceIn(1, Trainers.MAX_TEAM)).map { sp ->
                TrainerMon(sp.id, level, MovePool.wildMoveset(sp.id, level, rnd).map { it.name })
            }
            Trainer("ai:${t.key}", t.name, Trainer.Kind.AI, team)
        }
    }

    // ── Odměny: penízky jen za první výhru nad daným trenérem v daný den ──

    fun coinsForWin(t: Trainer): Int = (5 + t.power / 2).coerceAtMost(40)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("GamePrefs", Context.MODE_PRIVATE)
    private const val WINS = "arena_wins"
    private const val LOSSES = "arena_losses"
    private const val PAID = "arena_paid"
    private const val NAME = "arena_name"

    data class Record(val wins: Int, val losses: Int)

    fun record(ctx: Context): Record = prefs(ctx).let { Record(it.getInt(WINS, 0), it.getInt(LOSSES, 0)) }

    /** Zapíše výsledek; vrátí penízky za výhru (0, pokud už dnes nad tímhle trenérem vyhrál). */
    fun recordResult(ctx: Context, t: Trainer, won: Boolean, today: LocalDate = LocalDate.now(), pay: Boolean = true): Int {
        val p = prefs(ctx)
        if (!won) { p.edit().putInt(LOSSES, p.getInt(LOSSES, 0) + 1).apply(); return 0 }
        val tag = "$today|${t.id}"
        val paid = p.getStringSet(PAID, emptySet()).orEmpty().filter { it.startsWith("$today|") }.toSet()
        val coins = if (!pay || tag in paid) 0 else coinsForWin(t)
        p.edit().putInt(WINS, p.getInt(WINS, 0) + 1).putStringSet(PAID, paid + tag).apply()
        return coins
    }

    /** Jméno v aréně (vidí ho ostatní hráči) – nikdy ne e-mail. */
    fun arenaName(ctx: Context, fallback: String?): String =
        prefs(ctx).getString(NAME, null) ?: fallback?.substringBefore(' ')?.takeIf { it.isNotBlank() }?.take(Trainers.NAME_MAX) ?: "Trenér"

    fun setArenaName(ctx: Context, name: String) { prefs(ctx).edit().putString(NAME, name.trim().take(Trainers.NAME_MAX)).apply() }
}
