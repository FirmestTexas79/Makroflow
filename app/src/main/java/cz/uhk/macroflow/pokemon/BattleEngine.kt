package cz.uhk.macroflow.pokemon

import kotlin.math.floor
import kotlin.math.max
import kotlin.random.Random

enum class PokemonType {
    NORMAL, FIRE, WATER, GRASS, ELECTRIC, BUG, FLYING, GHOST, GROUND, PSYCHIC, DRAGON, POISON
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

    // --- NOVÉ PROMĚNNÉ PRO SHINY A MECHANIKY ---
    var isEnemyShiny: Boolean = false,  // Musíme vědět, zda je tenhle konkrétní nepřítel shiny
    var isPlayerShiny: Boolean = false, // Pro případné efekty u tvého pokémona
    var selectedBallId: String = "poke_ball", // Abychom věděli, co hráč zrovna hází

    var wobbleCount: Int = 0,
    var wobbleDone: Int = 0,
    var captureSuccess: Boolean = false
)

object BattleEngine {

    fun initializeStatsForLevel(basePokemon: Pokemon, targetLevel: Int): Pokemon {
        // Vzorec pro HP: ((2 * Base * Level) / 100) + Level + 10
        val newMaxHp = ((2 * basePokemon.maxHp * targetLevel) / 100) + targetLevel + 10

        // Vzorec pro ostatní: ((2 * Base * Level) / 100) + 5
        val newAttack  = ((2 * basePokemon.attack * targetLevel) / 100) + 5
        val newDefense = ((2 * basePokemon.defense * targetLevel) / 100) + 5
        val newSpeed   = ((2 * basePokemon.speed * targetLevel) / 100) + 5

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


    // V BattleEngine.kt -> objekt BattleFactory

    // --- NORMAL MOVES ---
    fun attackScratch()    = Move("SCRATCH",    PokemonType.NORMAL, 40, 100, 35)
    fun attackSlash()      = Move("SLASH",      PokemonType.NORMAL, 70, 100, 20)
    fun attackGrowl()      = Move("GROWL",      PokemonType.NORMAL,  0, 100, 40, statEffect = StatEffect.LOWER_ENEMY_ATK)
    fun attackSmokescreen() = Move("SMOKESCREEN", PokemonType.NORMAL,  0, 100, 20)
    fun attackScaryFace()  = Move("SCARY FACE",  PokemonType.NORMAL,  0, 100, 10, statEffect = StatEffect.LOWER_ENEMY_DEF)

    // --- FIRE MOVES ---
    fun attackEmber()        = Move("EMBER",        PokemonType.FIRE, 40, 100, 25)
    fun attackFireFang()     = Move("FIRE FANG",     PokemonType.FIRE, 65,  95, 15)
    fun attackFlamethrower() = Move("FLAMETHROWER", PokemonType.FIRE, 90, 100, 15)
    fun attackFireBlast()    = Move("FIRE BLAST",    PokemonType.FIRE, 110, 85, 5)

    // --- FLYING & DRAGON MOVES ---
    fun attackWingAttack() = Move("WING ATTACK", PokemonType.FLYING, 60, 100, 35)
    fun attackDragonClaw() = Move("DRAGON CLAW", PokemonType.FLYING, 80, 100, 15)

    // --- GROUND MOVES ---
    fun attackMudSlap()    = Move("MUD-SLAP",    PokemonType.GROUND, 20, 100, 10)
    fun attackMagnitude()  = Move("MAGNITUDE",   PokemonType.GROUND, 50, 100, 30)
    fun attackDig()        = Move("DIG",         PokemonType.GROUND, 80, 100, 10)
    fun attackEarthquake() = Move("EARTHQUAKE",  PokemonType.GROUND, 100, 100, 10)
    fun attackSandAttack() = Move("SAND ATTACK", PokemonType.GROUND,  0, 100, 15, statEffect = StatEffect.LOWER_ENEMY_ATK)

    fun attackVineWhip()    = Move("VINE WHIP",    PokemonType.GRASS,  45, 100, 25)
    fun attackRazorLeaf()   = Move("RAZOR LEAF",   PokemonType.GRASS,  55,  95, 25)
    fun attackSolarBeam()   = Move("SOLAR BEAM",   PokemonType.GRASS, 120, 100, 10)
    fun attackSleepPowder() = Move("SLEEP POWDER", PokemonType.GRASS,   0,  75, 20) // Status útok
    fun attackSeedBomb()    = Move("SEED BOMB",    PokemonType.GRASS,  80, 100, 15)


    fun attackTailWhip()    = Move("TAIL WHIP",    PokemonType.NORMAL,   0, 100, 30, statEffect = StatEffect.LOWER_ENEMY_DEF)
    fun attackWaterGun()    = Move("WATER GUN",    PokemonType.WATER,   40, 100, 25)
    fun attackBite()        = Move("BITE",         PokemonType.NORMAL,  60, 100, 25)
    fun attackWaterPulse()  = Move("WATER PULSE",  PokemonType.WATER,   60, 100, 20)
    fun attackHydroPump()   = Move("HYDRO PUMP",   PokemonType.WATER,  110,  80,  5)

    fun attackPoisonSting() = Move("POISON STING", PokemonType.BUG, 15, 100, 35)
    fun attackFuryAttack()  = Move("FURY ATTACK",  PokemonType.NORMAL, 15, 85, 20)
    fun attackTwineedle()   = Move("TWINEEDLE",    PokemonType.BUG, 25, 100, 20)
    fun attackPinMissile()  = Move("PIN MISSILE",  PokemonType.BUG, 25, 95, 20)

    fun attackAirSlash()    = Move("AIR SLASH",     PokemonType.FLYING, 75,  95, 15)
    fun attackHurricane()   = Move("HURRICANE",     PokemonType.FLYING, 110, 70, 10)

    fun attackHyperFang()  = Move("HYPER FANG",  PokemonType.NORMAL, 80,  90, 15)
    fun attackSuperFang()  = Move("SUPER FANG",  PokemonType.NORMAL, 1,   90, 10) // V engine by měl brát 50% HP
    fun attackCrunch()     = Move("CRUNCH",      PokemonType.NORMAL, 80, 100, 15)
    fun attackPeck()       = Move("PECK",         PokemonType.FLYING, 35, 100, 35)
    fun attackDrillPeck()  = Move("DRILL PECK",   PokemonType.FLYING, 80, 100, 20)
    fun attackAcid()       = Move("ACID",         PokemonType.POISON, 40, 100, 30)
    fun attackLeer()       = Move("LEER",         PokemonType.NORMAL,  0, 100, 30, statEffect = StatEffect.LOWER_ENEMY_DEF)
    fun attackSludgeBomb() = Move("SLUDGE BOMB",   PokemonType.POISON, 90, 100, 10)
    // ── COMMON ────────────────────────────────────────────────────────

    // --- 🌿 BULBASAUR RODINA ---

    fun createBulbasaur() = Pokemon(
        name = "BULBASAUR", level = 1,
        maxHp = 45, attack = 49, defense = 49, speed = 45,
        moves = listOf(attackTackle(), attackGrowl())
    )

    fun createIvysaur() = Pokemon(
        name = "IVYSAUR", level = 1,
        maxHp = 60, attack = 62, defense = 63, speed = 60,
        moves = listOf(attackTackle(), attackGrowl(), attackVineWhip())
    )

    fun createVenusaur() = Pokemon(
        name = "VENUSAUR", level = 1,
        maxHp = 80, attack = 82, defense = 83, speed = 80,
        moves = listOf(attackVineWhip(), attackRazorLeaf(), attackSleepPowder())
    )

    fun createSquirtle() = Pokemon(
        name = "SQUIRTLE", level = 1,
        maxHp = 44, attack = 48, defense = 65, speed = 43,
        moves = listOf(attackTackle(), attackTailWhip())
    )

    fun createWartortle() = Pokemon(
        name = "WARTORTLE", level = 1,
        maxHp = 59, attack = 63, defense = 80, speed = 58,
        moves = listOf(attackTackle(), attackTailWhip(), attackWaterGun())
    )

    fun createBlastoise() = Pokemon(
        name = "BLASTOISE", level = 1,
        maxHp = 79, attack = 83, defense = 100, speed = 78,
        moves = listOf(attackWaterGun(), attackBite(), attackWaterPulse())
    )

    fun createCharmander() = Pokemon(
        name = "CHARMANDER", level = 1,
        maxHp = 39, attack = 52, defense = 43, speed = 65,
        moves = listOf(attackScratch(), attackGrowl())
    )

    fun createCharmeleon() = Pokemon(
        name = "CHARMELEON", level = 1,
        maxHp = 58, attack = 64, defense = 58, speed = 80,
        moves = listOf(attackScratch(), attackGrowl(), attackEmber())
    )

    fun createCharizard() = Pokemon(
        name = "CHARIZARD", level = 1,
        maxHp = 78, attack = 84, defense = 78, speed = 100,
        moves = listOf(attackEmber(), attackWingAttack(), attackDragonClaw())
    )

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

    // --- 🐛 WEEDLE RODINA ---
    fun createWeedle() = Pokemon(
        name = "WEEDLE", level = 1,
        maxHp = 40, attack = 35, defense = 30, speed = 50,
        moves = listOf(attackPoisonSting(), attackStringShot())
    )
    fun createKakuna() = Pokemon(
        name = "KAKUNA", level = 1,
        maxHp = 45, attack = 25, defense = 50, speed = 35,
        moves = listOf(attackHarden())
    )
    fun createBeedrill() = Pokemon(
        name = "BEEDRILL", level = 1,
        maxHp = 65, attack = 90, defense = 40, speed = 75,
        moves = listOf(attackFuryAttack(), attackTwineedle())
    )

    fun createPidgey() = Pokemon(
        name = "PIDGEY", level = 1,
        maxHp = 40, attack = 45, defense = 40, speed = 56,
        moves = listOf(attackTackle(), attackSandAttack())
    )

    fun createPidgeotto() = Pokemon(
        name = "PIDGEOTTO", level = 1,
        maxHp = 63, attack = 60, defense = 55, speed = 71,
        moves = listOf(attackTackle(), attackGust(), attackSandAttack())
    )

    fun createPidgeot() = Pokemon(
        name = "PIDGEOT", level = 1,
        maxHp = 83, attack = 80, defense = 75, speed = 101,
        moves = listOf(attackGust(), attackSandAttack(), attackWingAttack(), attackDragonClaw())
    )

    // --- 🐀 RATTATA RODINA ---
    fun createRattata() = Pokemon(
        name = "RATTATA", level = 1,
        maxHp = 30, attack = 56, defense = 35, speed = 72,
        moves = listOf(attackTackle(), attackTailWhip())
    )

    fun createRaticate() = Pokemon(
        name = "RATICATE", level = 1,
        maxHp = 55, attack = 81, defense = 60, speed = 97,
        moves = listOf(attackTackle(), attackQuickAttack(), attackHyperFang())
    )

    fun createSpearow() = Pokemon(
        name = "SPEAROW", level = 1,
        maxHp = 40, attack = 60, defense = 30, speed = 70,
        moves = listOf(attackPeck(), attackGrowl())
    )

    fun createFearow() = Pokemon(
        name = "FEAROW", level = 1,
        maxHp = 65, attack = 90, defense = 65, speed = 100,
        moves = listOf(attackPeck(), attackLeer(), attackFuryAttack())
    )

    // --- 🐍 EKANS RODINA (Poison) ---
    fun createEkans() = Pokemon(
        name = "EKANS", level = 1,
        maxHp = 35, attack = 60, defense = 44, speed = 55,
        moves = listOf(Move("WRAP", PokemonType.NORMAL, 15, 90, 20), attackPoisonSting())
    )

    fun createArbok() = Pokemon(
        name = "ARBOK", level = 1,
        maxHp = 60, attack = 95, defense = 69, speed = 80,
        moves = listOf(attackBite(), attackAcid())
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



    // --- 🪨 DIGLETT LINE ---
    fun createDiglett(level: Int = 5) = Pokemon(
        name = "DIGLETT",
        level = level,
        maxHp = 20,
        attack = 10,  // Zvýšen útok pro agresivnější gameplay
        defense = 5,
        speed = 14,
        moves = mutableListOf(attackScratch(), attackSandAttack(), attackMudSlap())
    )

    fun createDugtrio(level: Int = 10) = Pokemon(
        name = "DUGTRIO",
        level = level,
        maxHp = 45,
        attack = 22, // Pořádná síla
        defense = 12,
        speed = 25,  // Velmi rychlý
        moves = mutableListOf(attackMagnitude(), attackDig(), attackSlash(), attackSandAttack())
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

    fun createLapras(level: Int = 15) = Pokemon(
        name = "LAPRAS",
        level = level,
        maxHp = 55, // Vysoké základní HP
        attack = 15,
        defense = 14,
        speed = 12,
        moves = listOf(
            Move("WATER GUN", PokemonType.WATER, 40, 100, 25),
            Move("BODY SLAM", PokemonType.NORMAL, 85, 85, 15),
            Move("SING",      PokemonType.NORMAL,  0,  55, 15), // Uspávací útok (v budoucnu můžeš přidat efekt)
            Move("HYDRO PUMP", PokemonType.WATER, 110, 80, 5)
        )
    )


    fun createDitto(level: Int = 10) = Pokemon(
        name = "DITTO",
        level = level,
        maxHp = 48,
        attack = 15,
        defense = 15,
        speed = 15,
        moves = listOf(
            Move("TRANSFORM", PokemonType.NORMAL, 0, 100, 10)
        )
    )

    // ── LEGENDARY ─────────────────────────────────────────────────────


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

    const val CHARMELEON_CATCH_PENALTY = 0.75f
    const val SNORLAX_CATCH_PENALTY  = 0.6f
    const val MEW_CATCH_PENALTY      = 0.3f   // nejtěžší

    /** Vrátí catch multiplier pro daného nepřítele */
    fun catchMultiplier(pokemon: Pokemon): Float = when (pokemon.name) {
        "GENGAR"    -> GENGAR_CATCH_PENALTY
        "MEWTWO"    -> MEWTWO_CATCH_PENALTY
        "CHARIZARD" -> CHARIZARD_CATCH_PENALTY
        "SNORLAX"   -> SNORLAX_CATCH_PENALTY
        "MEW"       -> MEW_CATCH_PENALTY
        "CHARMELEON"->CHARMELEON_CATCH_PENALTY
        else        -> 1.0f
    }

    /** Vrátí pokédex ID pro daného nepřítele */
    /** Vrátí pokédex ID pro daného nepřítele */
    fun pokedexId(pokemon: Pokemon): String = when (pokemon.name) {




        "BULBASAUR" -> "001"
        "IVYSAUR"   -> "002"
        "VENUSAUR"  -> "003"
        "CHARMANDER"-> "004"
        "CHARMELEON"-> "005"
        "CHARIZARD" -> "006"
        "SQUIRTLE"  -> "007"
        "WARTORTLE" -> "008"
        "BLASTOISE" -> "009"
        "CATERPIE"  -> "010"
        "METAPOD"   -> "011"
        "BUTTERFREE"-> "012"
        "WEEDLE"    -> "013"
        "KAKUNA"    -> "014"
        "BEEDRILL"  -> "015"
        "PIDGEY"    -> "016"
        "PIDGEOTTO" -> "017"
        "PIDGEOT"   -> "018"
        "RATTATA"   -> "019"
        "RATTICATE" -> "020"
        "SPEAROW"   -> "021"
        "FEAROW"    -> "022"
        "EKANS"     -> "023"
        "ARBOK"     -> "024"
        "PIKACHU"   -> "025"
        "RAICHU"    -> "026"
        "DIGLETT"   -> "050"
        "DUGTRIO"   -> "051"
        "GASTLY"    -> "092"
        "HAUNTER"   -> "093"
        "GENGAR"    -> "094"
        "KANGASKHAN"-> "115"
        "LAPRAS"    -> "131"
        "DITTO"     -> "132"
        "EEVEE"     -> "133"
        "PORYGON"   -> "137"
        "SNORLAX"   -> "143"
        "MEWTWO"    -> "150"
        "MEW"       -> "151"
        else        -> "000"
    }

    /** Vrátí webName pro načítání sprite z pokemondb.net */
    fun webName(pokemon: Pokemon): String = when (pokemon.name) {

        "BULBASAUR"  -> "bulbasaur"
        "IVYSAUR"    -> "ivysaur"
        "VENUSAUR"   -> "venusaur"
        "CHARMANDER" -> "charmander"
        "CHARMELEON" -> "charmeleon"
        "CHARIZARD"  -> "charizard"
        "SQUIRTLE"   -> "squirtle"
        "WARTORTLE"  -> "wartortle"
        "BLASTOISE"  -> "blastoise"
        "CATERPIE"   -> "caterpie"
        "METAPOD"    -> "metapod"
        "BUTTERFREE" -> "butterfree"
        "WEEDLE"     -> "weedle"
        "KAKUNA"     -> "kakuna"
        "BEEDRILL"   -> "beedrill"
        "PIDGEY"     -> "pidgey"
        "PIDGEOTTO"  -> "pidgeotto"
        "PIDGEOT"    -> "pidgeot"
        "RATTATA"    -> "rattata"
        "RATTICATE"  -> "raticate"
        "SPEAROW"    -> "spearow"
        "FEAROW"     -> "fearow"
        "EKANS"      -> "ekans"
        "ARBOK"      -> "arbok"


        "PORYGON"    -> "porygon"

        "DIGLETT"    -> "diglett"
        "DUGTRIO"    -> "dugtrio"
        "PIKACHU"    -> "pikachu"
        "RAICHU"     -> "raichu"
        "EEVEE"      -> "eevee"


        "GASTLY"    -> "gastly"
        "HAUNTER"   -> "haunter"
        "GENGAR"    -> "gengar"
        "KANGASKHAN"-> "kangaskhan"
        "LAPRAS"    -> "lapras"
        "DITTO"     -> "ditto"
        "SNORLAX"   -> "snorlax"

        "MEWTWO"    -> "mewtwo"
        "MEW"       -> "mew"
        else        -> "missingno"
    }
}