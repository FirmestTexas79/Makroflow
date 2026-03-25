package cz.uhk.macroflow.pokemon

import kotlin.math.floor
import kotlin.math.max
import kotlin.random.Random

data class Move(
    val name: String,
    val type: String,
    val power: Int,
    val accuracy: Int,
    val maxPp: Int,
    var pp: Int = maxPp,
    val statEffect: StatEffect? = null
)

enum class StatEffect { LOWER_ENEMY_ATK, LOWER_ENEMY_DEF }

data class Pokemon(
    val name: String,
    val level: Int,
    val maxHp: Int,
    var currentHp: Int = maxHp,
    val attack: Int,
    val defense: Int,
    val speed: Int,
    val moves: List<Move>,
    var alive: Boolean = true
)

enum class BattlePhase {
    INTRO, MAIN_MENU, FIGHT_MENU, ITEM_MENU,
    ANIMATING, TEXT_WAIT, BALL_THROW, BALL_WOBBLE,
    CAUGHT, ESCAPED, ENEMY_FAINTED, PLAYER_FAINTED
}

data class BattleState(
    val player: Pokemon,
    val enemy: Pokemon,
    var phase: BattlePhase = BattlePhase.INTRO,
    var ballCount: Int = 5,
    var textLine1: String = "",
    var textLine2: String = "",
    var enemyAtkMod: Float = 1.0f,
    var enemyDefMod: Float = 1.0f,
    var enemyVisible: Boolean = true,
    var caught: Boolean = false,
    var wobbleCount: Int = 0,
    var wobbleDone: Int = 0,
    var captureSuccess: Boolean = false
)

object BattleEngine {

    fun calcDamage(level: Int, power: Int, atk: Int, def: Int): Int {
        if (power == 0) return 0
        val base = ((2.0 * level / 5.0 + 2.0) * power * (atk.toDouble() / def.toDouble()) / 50.0 + 2.0)
        val rng = 0.85 + Random.nextDouble() * 0.15
        return max(1, floor(base * rng).toInt())
    }

    /** multiplier < 1.0 = těžší chytit (Gengar 0.7), Great Ball > 1.0 */
    fun calcCaptureResult(enemy: Pokemon, multiplier: Float = 1.0f): Pair<Boolean, Int> {
        val hpFraction = enemy.currentHp.toDouble() / enemy.maxHp.toDouble()
        val catchRate = ((1.0 - hpFraction) * 220 + 20) * multiplier
        val catchInt = catchRate.toInt().coerceIn(0, 255)
        val success = Random.nextInt(256) < catchInt
        val wobbles = when {
            success        -> 3
            catchInt > 150 -> 2
            catchInt > 80  -> 1
            else           -> 0
        }
        return Pair(success, wobbles)
    }

    fun tryEscape(playerSpeed: Int, enemySpeed: Int): Boolean {
        if (playerSpeed > enemySpeed) return true
        return Random.nextInt(256) < ((playerSpeed * 32) / (enemySpeed + 1) + 30) % 256
    }

    fun enemyChooseMove(enemy: Pokemon): Move {
        val available = enemy.moves.filter { it.pp > 0 }
        return if (available.isEmpty()) enemy.moves[0] else available.random()
    }
}

object BattleFactory {

    // ── HRÁČŮV POKÉMON ────────────────────────────────────────────────
    fun createMew() = Pokemon(
        name = "MEW", level = 5, maxHp = 35, attack = 12, defense = 10, speed = 15,
        moves = listOf(
            Move("BAFIKYBAF",  "NORMAL",  40, 100, 35),
            Move("MEGA PUNCH", "NORMAL",  80,  85, 20),
            Move("GROWL",      "NORMAL",   0, 100, 40, statEffect = StatEffect.LOWER_ENEMY_ATK),
            Move("TAIL WHIP",  "NORMAL",   0, 100, 30, statEffect = StatEffect.LOWER_ENEMY_DEF)
        )
    )

    // ── COMMON ────────────────────────────────────────────────────────

    fun createDiglett() = Pokemon(
        name = "DIGLETT", level = 5, maxHp = 20, attack = 7, defense = 5, speed = 14,
        moves = listOf(
            Move("SCRATCH",  "NORMAL", 35, 100, 35),
            Move("GROWL",    "NORMAL",  0, 100, 40),
            Move("SAND ATK", "GROUND",  0,  85, 15)
        )
    )

    fun createPikachu() = Pokemon(
        name = "PIKACHU", level = 6, maxHp = 25, attack = 11, defense = 6, speed = 18,
        moves = listOf(
            Move("THUNDER SHOCK", "ELECTRIC", 40, 100, 30),
            Move("QUICK ATTACK",  "NORMAL",   40, 100, 30),
            Move("TAIL WHIP",     "NORMAL",    0, 100, 30, statEffect = StatEffect.LOWER_ENEMY_DEF),
            Move("GROWL",         "NORMAL",    0, 100, 40, statEffect = StatEffect.LOWER_ENEMY_ATK)
        )
    )

    fun createEevee() = Pokemon(
        name = "EEVEE", level = 5, maxHp = 24, attack = 9, defense = 8, speed = 13,
        moves = listOf(
            Move("TACKLE",    "NORMAL", 40, 100, 35),
            Move("SAND ATK",  "NORMAL",  0,  85, 15),
            Move("GROWL",     "NORMAL",  0, 100, 40, statEffect = StatEffect.LOWER_ENEMY_ATK),
            Move("TAIL WHIP", "NORMAL",  0, 100, 30, statEffect = StatEffect.LOWER_ENEMY_DEF)
        )
    )

    // ── RARE ──────────────────────────────────────────────────────────

    fun createBulbasaur() = Pokemon(
        name = "BULBASAUR", level = 7, maxHp = 28, attack = 10, defense = 10, speed = 10,
        moves = listOf(
            Move("VINE WHIP",  "GRASS",  45, 100, 25),
            Move("TACKLE",     "NORMAL", 40, 100, 35),
            Move("GROWL",      "NORMAL",  0, 100, 40, statEffect = StatEffect.LOWER_ENEMY_ATK),
            Move("LEECH SEED", "GRASS",   0,  90, 10)
        )
    )

    fun createSquirtle() = Pokemon(
        name = "SQUIRTLE", level = 7, maxHp = 27, attack = 9, defense = 12, speed = 11,
        moves = listOf(
            Move("WATER GUN", "WATER",  40, 100, 25),
            Move("TACKLE",    "NORMAL", 40, 100, 35),
            Move("TAIL WHIP", "NORMAL",  0, 100, 30, statEffect = StatEffect.LOWER_ENEMY_DEF),
            Move("BUBBLE",    "WATER",  40, 100, 30)
        )
    )

    fun createCharmander() = Pokemon(
        name = "CHARMANDER", level = 7, maxHp = 26, attack = 12, defense = 8, speed = 14,
        moves = listOf(
            Move("EMBER",      "FIRE",   40, 100, 25),
            Move("SCRATCH",    "NORMAL", 40, 100, 35),
            Move("GROWL",      "NORMAL",  0, 100, 40, statEffect = StatEffect.LOWER_ENEMY_ATK),
            Move("SMOKESCREEN","NORMAL",  0, 100, 20)
        )
    )

    fun createGastly() = Pokemon(
        name = "GASTLY", level = 7, maxHp = 22, attack = 13, defense = 5, speed = 16,
        moves = listOf(
            Move("LICK",      "GHOST",   30, 100, 30),
            Move("HYPNOSIS",  "PSYCHIC",  0,  60, 20),
            Move("NIGHT SHADE","GHOST",  40,  95, 15),
            Move("SPITE",     "GHOST",    0, 100, 10)
        )
    )

    // ── EPIC ──────────────────────────────────────────────────────────

    fun createHaunter() = Pokemon(
        name = "HAUNTER", level = 9, maxHp = 26, attack = 15, defense = 6, speed = 18,
        moves = listOf(
            Move("SHADOW PUNCH", "GHOST",   60, 100, 20),
            Move("LICK",         "GHOST",   30, 100, 30),
            Move("HYPNOSIS",     "PSYCHIC",  0,  60, 20),
            Move("CURSE",        "GHOST",    0, 100, 10, statEffect = StatEffect.LOWER_ENEMY_DEF)
        )
    )

    fun createGengar() = Pokemon(
        name = "GENGAR", level = 10, maxHp = 30, attack = 16, defense = 7, speed = 20,
        moves = listOf(
            Move("SHADOW BALL", "GHOST",   65,  80, 15),
            Move("LICK",        "GHOST",   30, 100, 30),
            Move("HYPNOSIS",    "PSYCHIC",  0,  60, 20),
            Move("CURSE",       "GHOST",    0, 100, 10, statEffect = StatEffect.LOWER_ENEMY_DEF)
        )
    )

    fun createSnorlax() = Pokemon(
        name = "SNORLAX", level = 10, maxHp = 50, attack = 14, defense = 10, speed = 5,
        moves = listOf(
            Move("BODY SLAM", "NORMAL", 85,  85, 15),
            Move("TACKLE",    "NORMAL", 40, 100, 35),
            Move("AMNESIA",   "PSYCHIC", 0, 100, 20),
            Move("YAWN",      "NORMAL",  0, 100, 10, statEffect = StatEffect.LOWER_ENEMY_ATK)
        )
    )

    // ── LEGENDARY ─────────────────────────────────────────────────────

    fun createCharizard() = Pokemon(
        name = "CHARIZARD", level = 14, maxHp = 40, attack = 20, defense = 12, speed = 17,
        moves = listOf(
            Move("FLAMETHROWER", "FIRE",   90,  85, 15),
            Move("WING ATTACK",  "FLYING", 60, 100, 35),
            Move("SLASH",        "NORMAL", 70, 100, 20),
            Move("GROWL",        "NORMAL",  0, 100, 40, statEffect = StatEffect.LOWER_ENEMY_ATK)
        )
    )

    fun createMewtwo() = Pokemon(
        name = "MEWTWO", level = 18, maxHp = 45, attack = 24, defense = 14, speed = 22,
        moves = listOf(
            Move("PSYCHIC",   "PSYCHIC", 90,  90, 10),
            Move("SWIFT",     "NORMAL",  60, 100, 20),
            Move("BARRIER",   "PSYCHIC",  0, 100, 30, statEffect = StatEffect.LOWER_ENEMY_DEF),
            Move("RECOVER",   "NORMAL",   0, 100, 10)
        )
    )

    // ── MYTHIC ────────────────────────────────────────────────────────

    /** Divoký Mew — odlišný od hráčova Mewa jménem */
    fun createWildMew() = Pokemon(
        name = "MEW", level = 20, maxHp = 42, attack = 18, defense = 18, speed = 20,
        moves = listOf(
            Move("PSYCHIC",    "PSYCHIC", 90,  90, 10),
            Move("MEGA PUNCH", "NORMAL",  80,  85, 20),
            Move("TRANSFORM",  "NORMAL",   0, 100, 10),
            Move("SOFTBOILED", "NORMAL",   0, 100, 10)
        )
    )

    // ── Konstanty ─────────────────────────────────────────────────────
    const val GENGAR_CATCH_PENALTY   = 0.7f   // těžší chytit
    const val MEWTWO_CATCH_PENALTY   = 0.4f   // velmi těžké
    const val CHARIZARD_CATCH_PENALTY = 0.55f
    const val SNORLAX_CATCH_PENALTY  = 0.6f
    const val MEW_CATCH_PENALTY      = 0.3f   // nejtěžší

    /** Vrátí catch multiplier pro daného nepřítele */
    fun catchMultiplier(pokemon: Pokemon): Float = when (pokemon.name) {
        "GENGAR"    -> GENGAR_CATCH_PENALTY
        "MEWTWO"    -> MEWTWO_CATCH_PENALTY
        "CHARIZARD" -> CHARIZARD_CATCH_PENALTY
        "SNORLAX"   -> SNORLAX_CATCH_PENALTY
        "MEW"       -> MEW_CATCH_PENALTY
        else        -> 1.0f
    }

    /** Vrátí pokédex ID pro daného nepřítele */
    fun pokedexId(pokemon: Pokemon): String = when (pokemon.name) {
        "DIGLETT"   -> "050"
        "PIKACHU"   -> "025"
        "EEVEE"     -> "133"
        "BULBASAUR" -> "001"
        "SQUIRTLE"  -> "007"
        "CHARMANDER"-> "004"
        "GASTLY"    -> "092"
        "HAUNTER"   -> "093"
        "GENGAR"    -> "094"
        "SNORLAX"   -> "143"
        "CHARIZARD" -> "006"
        "MEWTWO"    -> "150"
        "MEW"       -> "151"
        else        -> "000"
    }

    /** Vrátí webName pro načítání sprite z pokemondb.net */
    fun webName(pokemon: Pokemon): String = when (pokemon.name) {
        "DIGLETT"   -> "diglett"
        "PIKACHU"   -> "pikachu"
        "EEVEE"     -> "eevee"
        "BULBASAUR" -> "bulbasaur"
        "SQUIRTLE"  -> "squirtle"
        "CHARMANDER"-> "charmander"
        "GASTLY"    -> "gastly"
        "HAUNTER"   -> "haunter"
        "GENGAR"    -> "gengar"
        "SNORLAX"   -> "snorlax"
        "CHARIZARD" -> "charizard"
        "MEWTWO"    -> "mewtwo"
        "MEW"       -> "mew"
        else        -> "missingno"
    }
}