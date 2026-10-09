package cz.uhk.macroflow.pokemon.trade

import cz.uhk.macroflow.pokemon.CapturedMakromonEntity
import cz.uhk.macroflow.pokemon.PokemonLevelCalc
import cz.uhk.macroflow.pokemon.species.SpeciesRegistry
import cz.uhk.macroflow.pokemon.wild.MovePool
import kotlin.random.Random

/**
 * Výměna Makromonů mezi dvěma hráči (docs/adr/0077).
 *
 * Výměna je dokument `trades/{kód}`. Hráč A ho založí se svou nabídkou a ukáže kód, hráč B se
 * kódem připojí se svou nabídkou. Pak každý potvrdí svou stranu; jakmile jsou potvrzené obě, je
 * výměna nevratná a každý telefon si ji provede u sebe (odebere svého, přidá cizího) a zapíše, že
 * to udělal. Pravidla Firestore hlídají, že každý mění jen svou část a že potvrzenou výměnu už
 * nejde zrušit.
 */
data class TradeOffer(
    /** Stálé ID Makromona (CapturedMakromonEntity.uid). */
    val uid: String,
    val speciesId: String,
    val level: Int,
    val xp: Int,
    val moves: List<String>,
    val shiny: Boolean
)

data class Trade(
    val code: String,
    val a: String,
    val aName: String,
    val offerA: TradeOffer?,
    val b: String? = null,
    val bName: String = "",
    val offerB: TradeOffer? = null,
    val state: String = Trading.OPEN,
    val confirmA: Boolean = false,
    val confirmB: Boolean = false,
    val appliedA: Boolean = false,
    val appliedB: Boolean = false,
    val createdAt: Long = 0L
)

object Trading {

    const val OPEN = "open"
    const val OFFERED = "offered"
    const val CANCELLED = "cancelled"

    /** Bez snadno zaměnitelných znaků (0/O, 1/I/L). */
    const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    const val CODE_LEN = 6

    enum class Phase { OPEN, OFFERED, DONE, CANCELLED }
    enum class Role { A, B }

    fun newCode(rnd: Random = Random.Default): String = (1..CODE_LEN).map { ALPHABET[rnd.nextInt(ALPHABET.length)] }.joinToString("")

    /** Co hráč napsal → kód (velká písmena, bez mezer a pomlček). */
    fun normalizeCode(input: String): String = input.uppercase().filter { it in ALPHABET }.take(CODE_LEN)

    fun pretty(code: String): String = if (code.length == CODE_LEN) code.take(3) + " " + code.drop(3) else code

    fun phase(t: Trade): Phase = when {
        t.state == CANCELLED -> Phase.CANCELLED
        t.confirmA && t.confirmB -> Phase.DONE
        t.state == OFFERED -> Phase.OFFERED
        else -> Phase.OPEN
    }

    fun role(t: Trade, me: String): Role? = when (me) { t.a -> Role.A; t.b -> Role.B; else -> null }

    fun myOffer(t: Trade, r: Role) = if (r == Role.A) t.offerA else t.offerB
    fun theirOffer(t: Trade, r: Role) = if (r == Role.A) t.offerB else t.offerA
    fun theirName(t: Trade, r: Role) = if (r == Role.A) t.bName else t.aName
    fun confirmed(t: Trade, r: Role) = if (r == Role.A) t.confirmA else t.confirmB
    fun applied(t: Trade, r: Role) = if (r == Role.A) t.appliedA else t.appliedB

    fun offerOf(m: CapturedMakromonEntity) = TradeOffer(m.uid, m.makromonId, m.level, m.xp,
        m.moveListStr.split(',').map(String::trim).filter(String::isNotEmpty), m.isShiny)

    /** Stejná ochrana jako u duchů v aréně: hratelný druh, level 1…30, jen útoky, které druh umí. */
    fun sanitize(o: TradeOffer?): TradeOffer? {
        o ?: return null
        if (o.uid.isBlank() || o.uid.length > 64) return null
        val sp = SpeciesRegistry.byId(o.speciesId)?.takeIf { it.guardianOf == null } ?: return null
        val level = o.level.coerceIn(1, PokemonLevelCalc.MAX_LEVEL)
        val allowed = MovePool.pool(sp.id, level).map { it.name }.toSet()
        val maxXp = if (level < PokemonLevelCalc.MAX_LEVEL) PokemonLevelCalc.xpForLevel(level + 1) - 1 else PokemonLevelCalc.xpForLevel(level)
        return o.copy(speciesId = sp.id, level = level, xp = o.xp.coerceIn(PokemonLevelCalc.xpForLevel(level), maxXp),
            moves = o.moves.filter { it in allowed }.distinct().take(MovePool.MAX_MOVES))
    }

    /** Druh po výměně (u některých druhů výměna spustí vývoj). */
    fun speciesAfterTrade(speciesId: String): String =
        SpeciesRegistry.byId(speciesId)?.tradeEvolvesTo?.takeIf { SpeciesRegistry.byId(it) != null } ?: speciesId

    /** Makromon, který hráč výměnou dostane. */
    fun received(o: TradeOffer, otName: String, now: Long): CapturedMakromonEntity {
        val species = SpeciesRegistry.byId(speciesAfterTrade(o.speciesId))!!
        return CapturedMakromonEntity(
            makromonId = species.id, name = species.name, isShiny = o.shiny, caughtDate = now,
            moveListStr = o.moves.joinToString(","), level = o.level, xp = o.xp, uid = o.uid, otName = otName.take(16)
        )
    }

    // ── Firestore ──

    fun offerToMap(o: TradeOffer?): Map<String, Any>? = o?.let {
        mapOf("uid" to it.uid, "s" to it.speciesId, "l" to it.level, "xp" to it.xp, "m" to it.moves, "sh" to it.shiny)
    }

    fun offerFromMap(m: Any?): TradeOffer? {
        val map = m as? Map<*, *> ?: return null
        return TradeOffer(
            uid = map["uid"] as? String ?: return null,
            speciesId = map["s"] as? String ?: return null,
            level = (map["l"] as? Number)?.toInt() ?: 1,
            xp = (map["xp"] as? Number)?.toInt() ?: 0,
            moves = (map["m"] as? List<*>).orEmpty().filterIsInstance<String>(),
            shiny = map["sh"] as? Boolean ?: false
        )
    }

    /** Nový dokument výměny (hráč A). */
    fun createMap(a: String, aName: String, offer: TradeOffer, now: Long): Map<String, Any?> = mapOf(
        "a" to a, "aName" to aName.take(16), "offerA" to offerToMap(offer),
        "b" to null, "bName" to "", "offerB" to null,
        "state" to OPEN, "confirmA" to false, "confirmB" to false,
        "appliedA" to false, "appliedB" to false, "createdAt" to now, "v" to 1
    )

    fun fromMap(code: String, m: Map<String, Any?>?): Trade? {
        m ?: return null
        return Trade(
            code = code,
            a = m["a"] as? String ?: return null,
            aName = m["aName"] as? String ?: "",
            offerA = sanitize(offerFromMap(m["offerA"])),
            b = m["b"] as? String,
            bName = m["bName"] as? String ?: "",
            offerB = sanitize(offerFromMap(m["offerB"])),
            state = m["state"] as? String ?: OPEN,
            confirmA = m["confirmA"] as? Boolean ?: false,
            confirmB = m["confirmB"] as? Boolean ?: false,
            appliedA = m["appliedA"] as? Boolean ?: false,
            appliedB = m["appliedB"] as? Boolean ?: false,
            createdAt = (m["createdAt"] as? Number)?.toLong() ?: 0L
        )
    }
}
