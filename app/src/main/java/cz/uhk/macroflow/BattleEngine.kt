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

    // Gen 1 damage formula
    fun calcDamage(level: Int, power: Int, atk: Int, def: Int): Int {
        if (power == 0) return 0
        val base = ((2.0 * level / 5.0 + 2.0) * power * (atk.toDouble() / def.toDouble()) / 50.0 + 2.0)
        val rng = 0.85 + Random.nextDouble() * 0.15
        return max(1, floor(base * rng).toInt())
    }

    // Capture rate — čím méně HP, tím vyšší šance
    // PŘIDÁN MULTIPLIER (pro ztížení chycení Gengara)
    fun calcCaptureResult(enemy: Pokemon, multiplier: Float = 1.0f): Pair<Boolean, Int> {
        val hpFraction = enemy.currentHp.toDouble() / enemy.maxHp.toDouble()
        val baseCatchRate = ((1.0 - hpFraction) * 220 + 20)

        // Aplikujeme násobič (pro Gengara 0.7f z BattleFactory)
        val catchRate = (baseCatchRate * multiplier).toInt().coerceIn(0, 255)

        val success = Random.nextInt(256) < catchRate
        val wobbles = when {
            success         -> 3
            catchRate > 150 -> 2
            catchRate > 80  -> 1
            else            -> 0
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

    // Mew jako hráčův pokémon
    fun createMew() = Pokemon(
        name     = "MEW",
        level    = 5,
        maxHp    = 35,
        attack   = 12,
        defense  = 10,
        speed    = 15,
        moves    = listOf(
            Move("BAFIKYBAF",  "NORMAL",  40, 100, 35),
            Move("MEGA PUNCH", "NORMAL",  80,  85, 20),
            Move("GROWL",      "NORMAL",   0, 100, 40, statEffect = StatEffect.LOWER_ENEMY_ATK),
            Move("TAIL WHIP",  "NORMAL",   0, 100, 30, statEffect = StatEffect.LOWER_ENEMY_DEF)
        )
    )

    // Diglett — slabší tahy
    fun createDiglett() = Pokemon(
        name     = "DIGLETT",
        level    = 5,
        maxHp    = 20,
        attack   = 7,
        defense  = 5,
        speed    = 14,
        moves    = listOf(
            Move("SCRATCH",  "NORMAL", 35, 100, 35),
            Move("GROWL",    "NORMAL",  0, 100, 40),
            Move("SAND ATK", "GROUND",  0,  85, 15)
        )
    )

    fun createGengar() = Pokemon(
        name     = "GENGAR",
        level    = 8,
        maxHp    = 30,
        attack   = 16,
        defense  = 7,
        speed    = 20,
        moves    = listOf(
            Move("SHADOW BALL", "GHOST",  65,  80, 15),
            Move("LICK",        "GHOST",  30, 100, 30),
            Move("HYPNOSIS",    "PSYCHIC", 0,  60, 20),
            Move("CURSE",       "GHOST",   0, 100, 10, statEffect = StatEffect.LOWER_ENEMY_DEF)
        )
    )

    const val GENGAR_SHOP_PRICE = 4
    const val GENGAR_CATCH_PENALTY = 0.7f
}