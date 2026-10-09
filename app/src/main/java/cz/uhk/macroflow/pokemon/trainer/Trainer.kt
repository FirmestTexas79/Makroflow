package cz.uhk.macroflow.pokemon.trainer

import cz.uhk.macroflow.pokemon.BattleEngine
import cz.uhk.macroflow.pokemon.BattleFactory
import cz.uhk.macroflow.pokemon.Makromon
import cz.uhk.macroflow.pokemon.PokemonLevelCalc
import cz.uhk.macroflow.pokemon.species.SpeciesRegistry
import cz.uhk.macroflow.pokemon.wild.MovePool
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer

/**
 * Trenér v aréně (docs/adr/0076): duch skutečného hráče, nebo trenér řízený AI.
 *
 * Makromon trenéra nese jen to, co se nedá odvodit – druh, level a jména útoků. Statistiky se vždy
 * dopočítají z druhu a levelu (stejně jako u hráčových Makromonů), takže upravená data v cloudu
 * nemůžou poslat silnějšího Makromona, než jaký odpovídá jeho levelu.
 */
data class TrainerMon(
    val speciesId: String,
    val level: Int,
    val moves: List<String> = emptyList(),
    val shiny: Boolean = false
)

data class Trainer(
    /** Duch: uid hráče. AI: "ai:<klíč>". */
    val id: String,
    /** Jméno pro obrazovku arény (s diakritikou). */
    val name: String,
    val kind: Kind,
    val team: List<TrainerMon>,
    val updatedAt: Long = 0L,
    /** Hodnocený zápas – výsledek mění body v ranku (docs/adr/0079). */
    val ranked: Boolean = false,
    /** Body v ranku (u duchů z cloudu, pro žebříček). */
    val points: Int = 0
) {
    enum class Kind { GHOST, AI }

    /** Síla týmu pro párování soupeřů: součet levelů. */
    val power: Int get() = team.sumOf { it.level }

    /** Jméno v souboji: velká písmena bez diakritiky (pixelové písmo umí jen ASCII), max. 15 znaků. */
    val battleName: String get() = Trainers.ascii(name).take(Trainers.BATTLE_NAME_MAX).trim().ifEmpty { "TRAINER" }
}

object Trainers {

    const val MAX_TEAM = 6
    const val NAME_MAX = 16
    const val BATTLE_NAME_MAX = 15

    fun ascii(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
            .uppercase().replace(Regex("[^A-Z0-9 .'-]"), "").replace(Regex("\\s+"), " ").trim()

    /**
     * Ochrana před upravenými daty z cloudu: jen hratelné druhy (ne strážci), level 1…max,
     * útoky jen ty, které druh na svém levelu umí, nejvýš 6 Makromonů a krátké jméno.
     * Prázdný tým → null (takového soupeře nezobrazíme).
     */
    fun sanitize(t: Trainer): Trainer? {
        val team = t.team.mapNotNull { m ->
            val sp = SpeciesRegistry.byId(m.speciesId)?.takeIf { it.guardianOf == null } ?: return@mapNotNull null
            val level = m.level.coerceIn(1, PokemonLevelCalc.MAX_LEVEL)
            val allowed = MovePool.pool(sp.id, level).map { it.name }.toSet()
            TrainerMon(sp.id, level, m.moves.filter { it in allowed }.distinct().take(MovePool.MAX_MOVES), m.shiny)
        }.take(MAX_TEAM)
        if (team.isEmpty()) return null
        val name = t.name.replace(Regex("[\\p{Cntrl}]"), "").trim().take(NAME_MAX).ifEmpty { "Trenér" }
        return t.copy(name = name, team = team)
    }

    /** Makromon do souboje: statistiky z druhu a levelu, útoky z uložených jmen (jinak základní). */
    fun toBattle(m: TrainerMon): Makromon {
        val base = BattleFactory.createById(m.speciesId)
        return BattleEngine.initializeStatsForLevel(base, m.level).copy(
            moves = MovePool.resolve(m.moves.joinToString(","), base.moves),
            type = base.speciesType
        )
    }

    // ── Uložení: mapa pro Firestore, JSON pro argumenty fragmentu ──

    fun toMap(t: Trainer): Map<String, Any> = mapOf(
        "name" to t.name,
        "code" to Arena.trainerCode(t.id),
        "kind" to t.kind.name,
        "team" to t.team.map { mapOf("s" to it.speciesId, "l" to it.level, "m" to it.moves, "sh" to it.shiny) },
        "power" to t.power,
        "updatedAt" to t.updatedAt,
        "v" to 1
    )

    fun fromMap(id: String, map: Map<String, Any?>?): Trainer? {
        map ?: return null
        val team = (map["team"] as? List<*>).orEmpty().mapNotNull { e ->
            val m = e as? Map<*, *> ?: return@mapNotNull null
            TrainerMon(
                speciesId = m["s"] as? String ?: return@mapNotNull null,
                level = (m["l"] as? Number)?.toInt() ?: 1,
                moves = (m["m"] as? List<*>).orEmpty().filterIsInstance<String>(),
                shiny = m["sh"] as? Boolean ?: false
            )
        }
        val kind = runCatching { Trainer.Kind.valueOf(map["kind"] as? String ?: "") }.getOrDefault(Trainer.Kind.GHOST)
        return Trainer(id, map["name"] as? String ?: "", kind, team, (map["updatedAt"] as? Number)?.toLong() ?: 0L,
            points = (map["points"] as? Number)?.toInt()?.coerceIn(0, 100_000) ?: 0)
    }

    fun toJson(t: Trainer): String = JSONObject().apply {
        put("id", t.id); put("name", t.name); put("kind", t.kind.name); put("updatedAt", t.updatedAt)
        put("ranked", t.ranked); put("points", t.points)
        put("team", JSONArray(t.team.map { m ->
            JSONObject().put("s", m.speciesId).put("l", m.level).put("m", JSONArray(m.moves)).put("sh", m.shiny)
        }))
    }.toString()

    fun fromJson(json: String?): Trainer? = runCatching {
        val o = JSONObject(json ?: return null)
        val arr = o.getJSONArray("team")
        val team = (0 until arr.length()).map { i ->
            val m = arr.getJSONObject(i)
            val mv = m.optJSONArray("m") ?: JSONArray()
            TrainerMon(m.getString("s"), m.getInt("l"), (0 until mv.length()).map { mv.getString(it) }, m.optBoolean("sh"))
        }
        Trainer(o.getString("id"), o.getString("name"), Trainer.Kind.valueOf(o.getString("kind")), team, o.optLong("updatedAt"),
            ranked = o.optBoolean("ranked"), points = o.optInt("points"))
    }.getOrNull()
}
