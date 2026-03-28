package cz.uhk.macroflow.pokemon

import kotlin.math.floor
import kotlin.math.max
import kotlin.random.Random

enum class PokemonType {
    NORMAL, FIRE, WATER, GRASS, ELECTRIC, BUG, FLYING, GHOST, GROUND, PSYCHIC
}

// Uprav Move, aby využíval PokemonType místo String
data class Move(
    val name: String,
    val type: PokemonType, // 👈 Změna ze String na PokemonType
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

    fun initializeStatsForLevel(basePokemon: Pokemon, targetLevel: Int): Pokemon {
        val statMultiplier = 1.0f + (targetLevel - 1) * 0.10f

        val newMaxHp = (basePokemon.maxHp * statMultiplier).toInt()
        val newAttack = (basePokemon.attack * statMultiplier).toInt()
        val newDefense = (basePokemon.defense * statMultiplier).toInt()
        val newSpeed = (basePokemon.speed * statMultiplier).toInt()

        return basePokemon.copy(
            level = targetLevel,
            maxHp = newMaxHp,
            currentHp = newMaxHp,
            attack = newAttack,
            defense = newDefense,
            speed = newSpeed
        )
    }

    // Vrací 2.0f (Supereffektivní), 1.0f (Normální), 0.5f (Málo efektivní) nebo 0.0f (Žádný efekt)
    fun getTypeEffectiveness(moveType: PokemonType, defenderType: PokemonType): Float {
        return when (moveType) {
            PokemonType.FIRE -> when (defenderType) {
                PokemonType.GRASS, PokemonType.BUG -> 2.0f
                PokemonType.WATER, PokemonType.FIRE -> 0.5f
                else -> 1.0f
            }
            PokemonType.WATER -> when (defenderType) {
                PokemonType.FIRE, PokemonType.GROUND -> 2.0f
                PokemonType.WATER, PokemonType.GRASS -> 0.5f
                else -> 1.0f
            }
            PokemonType.GRASS -> when (defenderType) {
                PokemonType.WATER, PokemonType.GROUND -> 2.0f
                PokemonType.FIRE, PokemonType.GRASS, PokemonType.FLYING, PokemonType.BUG -> 0.5f
                else -> 1.0f
            }
            PokemonType.ELECTRIC -> when (defenderType) {
                PokemonType.WATER, PokemonType.FLYING -> 2.0f
                PokemonType.GRASS, PokemonType.ELECTRIC -> 0.5f
                PokemonType.GROUND -> 0.0f // Zemní typy jsou imunní!
                else -> 1.0f
            }
            PokemonType.NORMAL -> when (defenderType) {
                PokemonType.GHOST -> 0.0f // Duchové jsou imunní vůči normálním útokům!
                else -> 1.0f
            }
            // Sem můžeš postupně dopisovat další kombinace (GHOST vs PSYCHIC atd.)
            else -> 1.0f
        }
    }

    // Upravený výpočet damage o násobič typu
    fun calcDamage(level: Int, power: Int, atk: Int, def: Int, moveType: PokemonType, defenderType: PokemonType): Int {
        if (power == 0) return 0

        val base = ((2.0 * level / 5.0 + 2.0) * power * (atk.toDouble() / def.toDouble()) / 50.0 + 2.0)
        val rng = 0.85 + Random.nextDouble() * 0.15

        // Získáme násobič podle slabosti/odolnosti
        val typeMultiplier = getTypeEffectiveness(moveType, defenderType)

        return max(1, floor(base * rng * typeMultiplier).toInt())
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

    fun attackTackle()     = Move("TACKLE",      PokemonType.NORMAL,  40, 100, 35)
    fun attackStringShot() = Move("STRING SHOT", PokemonType.BUG,      0,  95, 40, statEffect = StatEffect.LOWER_ENEMY_DEF)
    fun attackHarden()     = Move("HARDEN",      PokemonType.NORMAL,   0, 100, 30)
    fun attackGust()       = Move("GUST",        PokemonType.FLYING,  40, 100, 35)
    // V BattleEngine.kt -> objekt BattleFactory
    fun attackThunderShock() = Move("THUNDER SHOCK", PokemonType.ELECTRIC, 40, 100, 30)
    fun attackQuickAttack()  = Move("QUICK ATTACK",  PokemonType.NORMAL,   40, 100, 30)
    fun attackSlam()         = Move("SLAM",          PokemonType.NORMAL,   80,  75, 20)
    fun attackThunderbolt()  = Move("THUNDERBOLT",   PokemonType.ELECTRIC, 90, 100, 15)
    fun attackThunder()      = Move("THUNDER",       PokemonType.ELECTRIC, 110, 70, 10)

    // --- 🐛 CATERPIE SHABLONY S PARAMETREM LEVEL ---
    fun createCaterpie(level: Int = 1) = Pokemon(
        name = "CATERPIE", level = level, maxHp = 22, attack = 6, defense = 7, speed = 9,
        moves = mutableListOf(attackTackle(), attackStringShot())
    )

    fun createMetapod(level: Int = 3) = Pokemon(
        name = "METAPOD", level = level, maxHp = 28, attack = 5, defense = 15, speed = 6,
        moves = mutableListOf(attackTackle(), attackHarden())
    )

    fun createButterfree(level: Int = 5) = Pokemon(
        name = "BUTTERFREE", level = level, maxHp = 35, attack = 12, defense = 10, speed = 16,
        moves = mutableListOf(attackGust(), attackHarden())
    )

    // ── HRÁČŮV POKÉMON ────────────────────────────────────────────────
    fun createMew() = Pokemon(
        name = "MEW", level = 5, maxHp = 35, attack = 12, defense = 10, speed = 15,
        moves = listOf(
            Move("BAFIKYBAF",  PokemonType.NORMAL,  40, 100, 35),
            Move("MEGA PUNCH", PokemonType.NORMAL,  80,  85, 20),
            Move("GROWL",      PokemonType.NORMAL,   0, 100, 40, statEffect = StatEffect.LOWER_ENEMY_ATK),
            Move("TAIL WHIP",  PokemonType.NORMAL,   0, 100, 30, statEffect = StatEffect.LOWER_ENEMY_DEF)
        )
    )

    // ── COMMON ────────────────────────────────────────────────────────

    fun createDiglett() = Pokemon(
        name = "DIGLETT", level = 5, maxHp = 20, attack = 7, defense = 5, speed = 14,
        moves = listOf(
            Move("SCRATCH",  PokemonType.NORMAL, 35, 100, 35),
            Move("GROWL",    PokemonType.NORMAL,  0, 100, 40),
            Move("SAND ATK", PokemonType.GROUND,  0,  85, 15)
        )
    )

    fun createPikachu() = Pokemon(
        name = "PIKACHU", level = 6, maxHp = 25, attack = 11, defense = 6, speed = 18,
        moves = listOf(
            Move("THUNDER SHOCK", PokemonType.ELECTRIC, 40, 100, 30),
            Move("QUICK ATTACK",  PokemonType.NORMAL,   40, 100, 30),
            Move("TAIL WHIP",     PokemonType.NORMAL,    0, 100, 30, statEffect = StatEffect.LOWER_ENEMY_DEF),
            Move("GROWL",         PokemonType.NORMAL,    0, 100, 40, statEffect = StatEffect.LOWER_ENEMY_ATK)
        )
    )

    fun createRaichu(level: Int = 10) = Pokemon(
        name = "RAICHU", level = level, maxHp = 35, attack = 16, defense = 11, speed = 21,
        moves = mutableListOf(
            Move("THUNDER SHOCK", PokemonType.ELECTRIC, 40, 100, 30),
            Move("QUICK ATTACK", PokemonType.NORMAL, 40, 100, 30)
        )
    )

    fun createEevee() = Pokemon(
        name = "EEVEE", level = 5, maxHp = 24, attack = 9, defense = 8, speed = 13,
        moves = listOf(
            Move("TACKLE",    PokemonType.NORMAL, 40, 100, 35),
            Move("SAND ATK",  PokemonType.NORMAL,  0,  85, 15),
            Move("GROWL",     PokemonType.NORMAL,  0, 100, 40, statEffect = StatEffect.LOWER_ENEMY_ATK),
            Move("TAIL WHIP", PokemonType.NORMAL,  0, 100, 30, statEffect = StatEffect.LOWER_ENEMY_DEF)
        )
    )

    // ── RARE ──────────────────────────────────────────────────────────

    fun createBulbasaur() = Pokemon(
        name = "BULBASAUR", level = 7, maxHp = 28, attack = 10, defense = 10, speed = 10,
        moves = listOf(
            Move("VINE WHIP",  PokemonType.GRASS,  45, 100, 25),
            Move("TACKLE",     PokemonType.NORMAL, 40, 100, 35),
            Move("GROWL",      PokemonType.NORMAL,  0, 100, 40, statEffect = StatEffect.LOWER_ENEMY_ATK),
            Move("LEECH SEED", PokemonType.GRASS,   0,  90, 10)
        )
    )

    fun createSquirtle() = Pokemon(
        name = "SQUIRTLE", level = 7, maxHp = 27, attack = 9, defense = 12, speed = 11,
        moves = listOf(
            Move("WATER GUN", PokemonType.WATER,  40, 100, 25),
            Move("TACKLE",    PokemonType.NORMAL, 40, 100, 35),
            Move("TAIL WHIP", PokemonType.NORMAL,  0, 100, 30, statEffect = StatEffect.LOWER_ENEMY_DEF),
            Move("BUBBLE",    PokemonType.WATER,  40, 100, 30)
        )
    )

    fun createCharmander() = Pokemon(
        name = "CHARMANDER", level = 7, maxHp = 26, attack = 12, defense = 8, speed = 14,
        moves = listOf(
            Move("EMBER",      PokemonType.FIRE,   40, 100, 25),
            Move("SCRATCH",    PokemonType.NORMAL, 40, 100, 35),
            Move("GROWL",      PokemonType.NORMAL,  0, 100, 40, statEffect = StatEffect.LOWER_ENEMY_ATK),
            Move("SMOKESCREEN",PokemonType.NORMAL,  0, 100, 20)
        )
    )

    fun createGastly() = Pokemon(
        name = "GASTLY", level = 7, maxHp = 22, attack = 13, defense = 5, speed = 16,
        moves = listOf(
            Move("LICK",      PokemonType.GHOST,   30, 100, 30),
            Move("HYPNOSIS",  PokemonType.PSYCHIC,  0,  60, 20),
            Move("NIGHT SHADE",PokemonType.GHOST,  40,  95, 15),
            Move("SPITE",     PokemonType.GHOST,    0, 100, 10)
        )
    )

    // ── EPIC ──────────────────────────────────────────────────────────


    fun createPorygon() = Pokemon(
        name = "PORYGON", level = 12, maxHp = 42, attack = 15, defense = 16, speed = 11,
        moves = listOf(
            Move("TRI ATTACK", PokemonType.NORMAL,  80, 100, 10),
            Move("PSYBEAM",    PokemonType.PSYCHIC, 65, 100, 20),
            Move("SHARPEN",    PokemonType.NORMAL,   0, 100, 30, statEffect = StatEffect.LOWER_ENEMY_DEF),
            Move("TACKLE",     PokemonType.NORMAL,  40, 100, 35)
        )
    )

    fun createHaunter() = Pokemon(
        name = "HAUNTER", level = 9, maxHp = 26, attack = 15, defense = 6, speed = 18,
        moves = listOf(
            Move("SHADOW PUNCH", PokemonType.GHOST,   60, 100, 20),
            Move("LICK",         PokemonType.GHOST,   30, 100, 30),
            Move("HYPNOSIS",     PokemonType.PSYCHIC,  0,  60, 20),
            Move("CURSE",        PokemonType.GHOST,    0, 100, 10, statEffect = StatEffect.LOWER_ENEMY_DEF)
        )
    )

    fun createGengar() = Pokemon(
        name = "GENGAR", level = 10, maxHp = 30, attack = 16, defense = 7, speed = 20,
        moves = listOf(
            Move("SHADOW BALL", PokemonType.GHOST,   65,  80, 15),
            Move("LICK",        PokemonType.GHOST,   30, 100, 30),
            Move("HYPNOSIS",    PokemonType.PSYCHIC,  0,  60, 20),
            Move("CURSE",       PokemonType.GHOST,    0, 100, 10, statEffect = StatEffect.LOWER_ENEMY_DEF)
        )
    )

    fun createKangaskhan(level: Int = 1) = Pokemon(
        name = "KANGASKHAN", level = level, maxHp = 45, attack = 15, defense = 12, speed = 14,
        moves = mutableListOf(
            Move("TACKLE", PokemonType.NORMAL, 40, 100, 35),
            Move("BITE", PokemonType.NORMAL, 60, 100, 25)
        )
    )

    fun createSnorlax() = Pokemon(
        name = "SNORLAX", level = 10, maxHp = 50, attack = 14, defense = 10, speed = 5,
        moves = listOf(
            Move("BODY SLAM", PokemonType.NORMAL, 85,  85, 15),
            Move("TACKLE",    PokemonType.NORMAL, 40, 100, 35),
            Move("AMNESIA",   PokemonType.PSYCHIC, 0, 100, 20),
            Move("YAWN",      PokemonType.NORMAL,  0, 100, 10, statEffect = StatEffect.LOWER_ENEMY_ATK)
        )
    )

    // ── LEGENDARY ─────────────────────────────────────────────────────

    fun createCharizard() = Pokemon(
        name = "CHARIZARD", level = 14, maxHp = 40, attack = 20, defense = 12, speed = 17,
        moves = listOf(
            Move("FLAMETHROWER", PokemonType.FIRE,   90,  85, 15),
            Move("WING ATTACK",  PokemonType.FLYING, 60, 100, 35),
            Move("SLASH",        PokemonType.NORMAL, 70, 100, 20),
            Move("GROWL",        PokemonType.NORMAL,  0, 100, 40, statEffect = StatEffect.LOWER_ENEMY_ATK)
        )
    )

    fun createMewtwo() = Pokemon(
        name = "MEWTWO", level = 18, maxHp = 45, attack = 24, defense = 14, speed = 22,
        moves = listOf(
            Move("PSYCHIC",   PokemonType.PSYCHIC, 90,  90, 10),
            Move("SWIFT",     PokemonType.NORMAL,  60, 100, 20),
            Move("BARRIER",   PokemonType.PSYCHIC,  0, 100, 30, statEffect = StatEffect.LOWER_ENEMY_DEF),
            Move("RECOVER",   PokemonType.NORMAL,   0, 100, 10)
        )
    )

    // ── MYTHIC ────────────────────────────────────────────────────────

    fun createWildMew() = Pokemon(
        name = "MEW", level = 20, maxHp = 42, attack = 18, defense = 18, speed = 20,
        moves = listOf(
            Move("PSYCHIC",    PokemonType.PSYCHIC, 90,  90, 10),
            Move("MEGA PUNCH", PokemonType.NORMAL,  80,  85, 20),
            Move("TRANSFORM",  PokemonType.NORMAL,   0, 100, 10),
            Move("SOFTBOILED", PokemonType.NORMAL,   0, 100, 10)
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
    /** Vrátí pokédex ID pro daného nepřítele */
    fun pokedexId(pokemon: Pokemon): String = when (pokemon.name) {




        "BULBASAUR" -> "001"
        "CHARMANDER"-> "004"
        "CHARIZARD" -> "006"
        "SQUIRTLE"  -> "007"
        "CATERPIE"  -> "010" // ✅ Doplněno
        "METAPOD"   -> "011" // ✅ Doplněno
        "BUTTERFREE"-> "012" // ✅ Doplněno
        "PIKACHU"   -> "025"
        "RAICHU"    -> "026"
        "DIGLETT"   -> "050"
        "GASTLY"    -> "092"
        "HAUNTER"   -> "093"
        "GENGAR"    -> "094"
        "KANGASKHAN" -> "115"
        "EEVEE"     -> "133"
        "PORYGON"   -> "137"
        "SNORLAX"   -> "143"
        "MEWTWO"    -> "150"
        "MEW"       -> "151"
        else        -> "000"
    }

    /** Vrátí webName pro načítání sprite z pokemondb.net */
    fun webName(pokemon: Pokemon): String = when (pokemon.name) {
        // ✅ TOTO TI ZDE CHYBĚLO A ROZBÍJELO TO CATERPIE!
        "CATERPIE"   -> "caterpie"
        "METAPOD"    -> "metapod"
        "BUTTERFREE" -> "butterfree"

        "PORYGON" -> "porygon"

        "DIGLETT"   -> "diglett"
        "PIKACHU"   -> "pikachu"
        "RAICHU"    -> "raichu"
        "EEVEE"     -> "eevee"
        "BULBASAUR" -> "bulbasaur"
        "SQUIRTLE"  -> "squirtle"
        "CHARMANDER"-> "charmander"
        "GASTLY"   -> "gastly"
        "HAUNTER"   -> "haunter"
        "GENGAR"    -> "gengar"
        "KANGASKHAN" -> "kangaskhan"
        "SNORLAX"   -> "snorlax"
        "CHARIZARD" -> "charizard"
        "MEWTWO"    -> "mewtwo"
        "MEW"       -> "mew"
        else        -> "missingno"
    }
}