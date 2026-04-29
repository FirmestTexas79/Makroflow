package cz.uhk.macroflow.pokemon

import kotlin.math.floor
import kotlin.math.max
import kotlin.random.Random

enum class MakromonType {
    NORMAL, FIRE, WATER, GRASS, ELECTRIC, BUG, FLYING, GHOST, GROUND, PSYCHIC, DRAGON, POISON, FAIRY
}

data class Move(
    val name: String,
    val type: MakromonType,
    val power: Int,
    val accuracy: Int,
    val maxPp: Int,
    var pp: Int = maxPp,
    val statEffect: StatEffect? = null
)
enum class StatEffect { LOWER_ENEMY_ATK, LOWER_ENEMY_DEF }

data class Makromon(
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
    val player: Makromon,
    val enemy: Makromon,
    var phase: BattlePhase = BattlePhase.INTRO,
    var ballCount: Int = 5,
    var textLine1: String = "",
    var textLine2: String = "",
    var enemyAtkMod: Float = 1.0f,
    var enemyDefMod: Float = 1.0f,
    var enemyVisible: Boolean = true,
    var caught: Boolean = false,
    // Shiny zatím zakomentováno – nemáme shiny sprity Makromonů
    // var isEnemyShiny: Boolean = false,
    // var isPlayerShiny: Boolean = false,
    var isEnemyShiny: Boolean = false,   // zachováno pro kompatibilitu, vždy false
    var isPlayerShiny: Boolean = false,  // zachováno pro kompatibilitu, vždy false
    var selectedBallId: String = "poke_ball",
    var introOffset: Float = 1.0f,
    var shinyAnimFrame: Int = 0,
    var wobbleCount: Int = 0,
    var wobbleDone: Int = 0,
    var captureSuccess: Boolean = false
)

object BattleEngine {

    fun initializeStatsForLevel(baseMakromon: Makromon, targetLevel: Int): Makromon {
        val newMaxHp = ((2 * baseMakromon.maxHp * targetLevel) / 100) + targetLevel + 10
        val newAttack  = ((2 * baseMakromon.attack * targetLevel) / 100) + 5
        val newDefense = ((2 * baseMakromon.defense * targetLevel) / 100) + 5
        val newSpeed   = ((2 * baseMakromon.speed * targetLevel) / 100) + 5
        return baseMakromon.copy(
            level = targetLevel,
            maxHp = newMaxHp,
            currentHp = newMaxHp,
            attack = newAttack,
            defense = newDefense,
            speed = newSpeed
        )
    }

    fun getTypeEffectiveness(moveType: MakromonType, defenderType: MakromonType): Float {
        return when (moveType) {
            MakromonType.FIRE -> when (defenderType) {
                MakromonType.GRASS, MakromonType.BUG -> 2.0f
                MakromonType.WATER, MakromonType.FIRE -> 0.5f
                else -> 1.0f
            }
            MakromonType.WATER -> when (defenderType) {
                MakromonType.FIRE, MakromonType.GROUND -> 2.0f
                MakromonType.WATER, MakromonType.GRASS -> 0.5f
                else -> 1.0f
            }
            MakromonType.GRASS -> when (defenderType) {
                MakromonType.WATER, MakromonType.GROUND -> 2.0f
                MakromonType.FIRE, MakromonType.GRASS, MakromonType.FLYING, MakromonType.BUG -> 0.5f
                else -> 1.0f
            }
            MakromonType.ELECTRIC -> when (defenderType) {
                MakromonType.WATER, MakromonType.FLYING -> 2.0f
                MakromonType.GRASS, MakromonType.ELECTRIC -> 0.5f
                MakromonType.GROUND -> 0.0f
                else -> 1.0f
            }
            MakromonType.NORMAL -> when (defenderType) {
                MakromonType.GHOST -> 0.0f
                else -> 1.0f
            }
            MakromonType.FAIRY -> when (defenderType) {
                MakromonType.DRAGON -> 2.0f
                MakromonType.FIRE, MakromonType.POISON -> 0.5f
                else -> 1.0f
            }
            MakromonType.DRAGON -> when (defenderType) {
                MakromonType.DRAGON -> 2.0f
                MakromonType.FAIRY -> 0.0f
                else -> 1.0f
            }
            MakromonType.GHOST -> when (defenderType) {
                MakromonType.GHOST, MakromonType.PSYCHIC -> 2.0f
                MakromonType.NORMAL -> 0.0f
                else -> 1.0f
            }
            else -> 1.0f
        }
    }

    fun calcDamage(level: Int, power: Int, atk: Int, def: Int, moveType: MakromonType, defenderType: MakromonType): Int {
        if (power == 0) return 0
        val base = ((2.0 * level / 5.0 + 2.0) * power * (atk.toDouble() / def.toDouble()) / 50.0 + 2.0)
        val rng = 0.85 + Random.nextDouble() * 0.15
        val typeMultiplier = getTypeEffectiveness(moveType, defenderType)
        return max(1, floor(base * rng * typeMultiplier).toInt())
    }

    fun calcCaptureResult(enemy: Makromon, multiplier: Float = 1.0f): Pair<Boolean, Int> {
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

    fun enemyChooseMove(enemy: Makromon): Move {
        val available = enemy.moves.filter { it.pp > 0 }
        return if (available.isEmpty()) enemy.moves[0] else available.random()
    }
}

object BattleFactory {

    // ── SDÍLENÉ ÚTOKY ─────────────────────────────────────────────────

    // NORMAL
    fun attackTackle()       = Move("TACKLE",       MakromonType.NORMAL,  40, 100, 35)
    fun attackScratch()      = Move("SCRATCH",      MakromonType.NORMAL,  40, 100, 35)
    fun attackSlash()        = Move("SLASH",        MakromonType.NORMAL,  70, 100, 20)
    fun attackGrowl()        = Move("GROWL",        MakromonType.NORMAL,   0, 100, 40, statEffect = StatEffect.LOWER_ENEMY_ATK)
    fun attackHarden()       = Move("HARDEN",       MakromonType.NORMAL,   0, 100, 30)
    fun attackTailWhip()     = Move("TAIL WHIP",    MakromonType.NORMAL,   0, 100, 30, statEffect = StatEffect.LOWER_ENEMY_DEF)
    fun attackLeer()         = Move("LEER",         MakromonType.NORMAL,   0, 100, 30, statEffect = StatEffect.LOWER_ENEMY_DEF)
    fun attackQuickAttack()  = Move("QUICK ATTACK", MakromonType.NORMAL,  40, 100, 30)
    fun attackSlam()         = Move("SLAM",         MakromonType.NORMAL,  80,  75, 20)
    fun attackBite()         = Move("BITE",         MakromonType.NORMAL,  60, 100, 25)
    fun attackCrunch()       = Move("CRUNCH",       MakromonType.NORMAL,  80, 100, 15)
    fun attackSmokescreen()  = Move("SMOKESCREEN",  MakromonType.NORMAL,   0, 100, 20)
    fun attackHyperFang()    = Move("HYPER FANG",   MakromonType.NORMAL,  80,  90, 15)
    fun attackFuryAttack()   = Move("FURY ATTACK",  MakromonType.NORMAL,  15,  85, 20)

    // FIRE
    fun attackEmber()        = Move("EMBER",        MakromonType.FIRE,  40, 100, 25)
    fun attackFireFang()     = Move("FIRE FANG",    MakromonType.FIRE,  65,  95, 15)
    fun attackFlamethrower() = Move("FLAMETHROWER", MakromonType.FIRE,  90, 100, 15)
    fun attackFireBlast()    = Move("FIRE BLAST",   MakromonType.FIRE, 110,  85,  5)
    fun attackHeatWave()     = Move("HEAT WAVE",    MakromonType.FIRE,  95,  90, 10)

    // WATER
    fun attackWaterGun()     = Move("WATER GUN",    MakromonType.WATER,  40, 100, 25)
    fun attackWaterPulse()   = Move("WATER PULSE",  MakromonType.WATER,  60, 100, 20)
    fun attackHydroPump()    = Move("HYDRO PUMP",   MakromonType.WATER, 110,  80,  5)
    fun attackAquaTail()     = Move("AQUA TAIL",    MakromonType.WATER,  90,  90, 10)
    fun attackBubbleBeam()   = Move("BUBBLE BEAM",  MakromonType.WATER,  65, 100, 20)

    // GRASS
    fun attackVineWhip()     = Move("VINE WHIP",    MakromonType.GRASS,  45, 100, 25)
    fun attackRazorLeaf()    = Move("RAZOR LEAF",   MakromonType.GRASS,  55,  95, 25)
    fun attackSeedBomb()     = Move("SEED BOMB",    MakromonType.GRASS,  80, 100, 15)
    fun attackSolarBeam()    = Move("SOLAR BEAM",   MakromonType.GRASS, 120, 100, 10)
    fun attackLeafBlade()    = Move("LEAF BLADE",   MakromonType.GRASS,  90, 100, 15)
    fun attackSleepPowder()  = Move("SLEEP POWDER", MakromonType.GRASS,   0,  75, 20)

    // GHOST
    fun attackShadowBall()   = Move("SHADOW BALL",  MakromonType.GHOST,  65,  80, 15)
    fun attackShadowPunch()  = Move("SHADOW PUNCH", MakromonType.GHOST,  60, 100, 20)
    fun attackLick()         = Move("LICK",         MakromonType.GHOST,  30, 100, 30)
    fun attackNightShade()   = Move("NIGHT SHADE",  MakromonType.GHOST,  40,  95, 15)
    fun attackHex()          = Move("HEX",          MakromonType.GHOST,  65, 100, 10)

    // FAIRY
    fun attackDazzlingGleam() = Move("DAZZL.GLEAM", MakromonType.FAIRY,  80, 100, 10)
    fun attackMoonblast()     = Move("MOONBLAST",   MakromonType.FAIRY,  95, 100, 15)
    fun attackCharm()         = Move("CHARM",       MakromonType.FAIRY,   0, 100, 20, statEffect = StatEffect.LOWER_ENEMY_ATK)
    fun attackPlayRough()     = Move("PLAY ROUGH",  MakromonType.FAIRY,  90,  90, 10)
    fun attackBabyDollEyes()  = Move("BABY-DOLL",   MakromonType.FAIRY,   0, 100, 30, statEffect = StatEffect.LOWER_ENEMY_ATK)

    // DRAGON
    fun attackDragonClaw()   = Move("DRAGON CLAW",  MakromonType.DRAGON,  80, 100, 15)
    fun attackDragonBreath() = Move("DRAGONBREATH", MakromonType.DRAGON,  60, 100, 20)
    fun attackDragonPulse()  = Move("DRAGON PULSE", MakromonType.DRAGON,  85,100, 10)
    fun attackOutrage()      = Move("OUTRAGE",      MakromonType.DRAGON, 120, 100, 10)

    // GROUND
    fun attackSandAttack()   = Move("SAND ATTACK",  MakromonType.GROUND,   0, 100, 15, statEffect = StatEffect.LOWER_ENEMY_ATK)
    fun attackMudSlap()      = Move("MUD-SLAP",     MakromonType.GROUND,  20, 100, 10)
    fun attackDig()          = Move("DIG",          MakromonType.GROUND,  80, 100, 10)
    fun attackEarthquake()   = Move("EARTHQUAKE",   MakromonType.GROUND, 100, 100, 10)

    // PSYCHIC
    fun attackPsychic()      = Move("PSYCHIC",      MakromonType.PSYCHIC, 90,  90, 10)
    fun attackHypnosis()     = Move("HYPNOSIS",     MakromonType.PSYCHIC,  0,  60, 20)

    // ELECTRIC
    fun attackThunderShock() = Move("THUNDER SHOCK",MakromonType.ELECTRIC,  40, 100, 30)
    fun attackThunderbolt()  = Move("THUNDERBOLT",  MakromonType.ELECTRIC,  90, 100, 15)

    // POISON
    fun attackPoisonSting()  = Move("POISON STING", MakromonType.POISON,  15, 100, 35)
    fun attackSludgeBomb()   = Move("SLUDGE BOMB",  MakromonType.POISON,  90, 100, 10)

    // BUG
    fun attackStringShot()   = Move("STRING SHOT",  MakromonType.BUG,      0,  95, 40, statEffect = StatEffect.LOWER_ENEMY_DEF)

    // FLYING
    fun attackGust()         = Move("GUST",         MakromonType.FLYING,  40, 100, 35)
    fun attackWingAttack()   = Move("WING ATTACK",  MakromonType.FLYING,  60, 100, 35)

    // ── STARTEŘI (01-09) ──────────────────────────────────────────────

    // 01 - Ignar (ohnivá ještěrka)
    fun createIgnar() = Makromon(
        name = "IGNAR", level = 1,
        maxHp = 39, attack = 52, defense = 43, speed = 65,
        moves = listOf(attackScratch(), attackGrowl())
    )

    // 02 - Ignaroc (střední evoluce) – sprite zatím chybí, placeholder
    fun createIgnaroc() = Makromon(
        name = "IGNAROC", level = 1,
        maxHp = 58, attack = 64, defense = 58, speed = 80,
        moves = listOf(attackScratch(), attackGrowl(), attackEmber())
    )

    // 03 - Ignaroth (finální dračí forma) – sprite zatím chybí, placeholder
    fun createIgnaroth() = Makromon(
        name = "IGNAROTH", level = 1,
        maxHp = 78, attack = 84, defense = 78, speed = 100,
        moves = listOf(attackEmber(), attackDragonClaw(), attackFlamethrower())
    )

    // 04 - Aqulin (malý vydří s ploutví)
    fun createAqulin() = Makromon(
        name = "AQULIN", level = 1,
        maxHp = 44, attack = 48, defense = 65, speed = 55,
        moves = listOf(attackTackle(), attackWaterGun())
    )

    // 05 - Aqulind (střední evoluce) – sprite zatím chybí, placeholder
    fun createAqlind() = Makromon(
        name = "AQULIND", level = 1,
        maxHp = 59, attack = 63, defense = 80, speed = 68,
        moves = listOf(attackWaterGun(), attackBubbleBeam(), attackBite())
    )

    // 06 - Aqulinox (bojovná vodní forma) – sprite zatím chybí, placeholder
    fun createAqulinox() = Makromon(
        name = "AQULINOX", level = 1,
        maxHp = 79, attack = 83, defense = 100, speed = 78,
        moves = listOf(attackWaterPulse(), attackAquaTail(), attackHydroPump())
    )

    // 07 - Flori (jelínek s listovou korunou)
    fun createFlori() = Makromon(
        name = "FLORI", level = 1,
        maxHp = 45, attack = 49, defense = 49, speed = 45,
        moves = listOf(attackTackle(), attackGrowl())
    )

    // 08 - Florind (střední evoluce) – sprite zatím chybí, placeholder
    fun createFlorind() = Makromon(
        name = "FLORIND", level = 1,
        maxHp = 60, attack = 62, defense = 63, speed = 60,
        moves = listOf(attackTackle(), attackVineWhip(), attackRazorLeaf())
    )

    // 09 - Florindra (finální stromová forma) – sprite zatím chybí, placeholder
    fun createFlorindra() = Makromon(
        name = "FLORINDRA", level = 1,
        maxHp = 80, attack = 82, defense = 83, speed = 80,
        moves = listOf(attackRazorLeaf(), attackLeafBlade(), attackSolarBeam())
    )

    // ── SPECIÁLNÍ (10-11) ─────────────────────────────────────────────

    // 10 - Umbex (temná kulička – smutná ale dobrá)
    fun createUmbex() = Makromon(
        name = "UMBEX", level = 8,
        maxHp = 30, attack = 14, defense = 12, speed = 16,
        moves = listOf(
            attackShadowBall(),
            attackLick(),
            attackNightShade(),
            Move("SPITE", MakromonType.GHOST, 0, 100, 10)
        )
    )

    // 11 - Lumex (světlá kulička – zářivá ale zlá) – sprite zatím chybí, placeholder
    fun createLumex() = Makromon(
        name = "LUMEX", level = 8,
        maxHp = 28, attack = 16, defense = 10, speed = 20,
        moves = listOf(
            attackDazzlingGleam(),
            attackHex(),
            attackCharm(),
            Move("DARK PULSE", MakromonType.GHOST, 80, 100, 15)
        )
    )

    // ── SPIRRA RODINA (12-19) ─────────────────────────────────────────

    // 12 - Spirra (béžová veverka – základ)
    fun createSpirra() = Makromon(
        name = "SPIRRA", level = 1,
        maxHp = 35, attack = 40, defense = 35, speed = 55,
        moves = listOf(attackTackle(), attackGrowl())
    )

    // 13 - Flamirra (ohnivá veverka)
    fun createFlamirra() = Makromon(
        name = "FLAMIRRA", level = 1,
        maxHp = 38, attack = 55, defense = 35, speed = 65,
        moves = listOf(attackTackle(), attackEmber(), attackFireFang())
    )

    // 14 - Aquirra (vodní veverka)
    fun createAquirra() = Makromon(
        name = "AQUIRRA", level = 1,
        maxHp = 42, attack = 45, defense = 50, speed = 55,
        moves = listOf(attackTackle(), attackWaterGun(), attackBubbleBeam())
    )

    // 15 - Verdirra (grass veverka)
    fun createVerdirra() = Makromon(
        name = "VERDIRRA", level = 1,
        maxHp = 40, attack = 48, defense = 45, speed = 50,
        moves = listOf(attackTackle(), attackVineWhip(), attackRazorLeaf())
    )

    // 16 - Shadirra (dark/ghost veverka)
    fun createShadirra() = Makromon(
        name = "SHADIRRA", level = 1,
        maxHp = 35, attack = 50, defense = 30, speed = 65,
        moves = listOf(attackTackle(), attackShadowBall(), attackLick())
    )

    // 17 - Charmirra (fairy veverka)
    fun createCharmirra() = Makromon(
        name = "CHARMIRRA", level = 1,
        maxHp = 38, attack = 42, defense = 42, speed = 55,
        moves = listOf(attackTackle(), attackCharm(), attackDazzlingGleam())
    )

    // 18 - Glacirra (ledová veverka) – sprite zatím chybí, placeholder
    fun createGlacirra() = Makromon(
        name = "GLACIRRA", level = 1,
        maxHp = 40, attack = 45, defense = 48, speed = 50,
        moves = listOf(
            attackTackle(),
            Move("ICE SHARD", MakromonType.NORMAL, 40, 100, 30),
            Move("BLIZZARD",  MakromonType.WATER,  110, 70, 5)
        )
    )

    // 19 - Drakirra (dragon veverka – skrytá evoluce)
    fun createDrakirra() = Makromon(
        name = "DRAKIRRA", level = 1,
        maxHp = 45, attack = 60, defense = 45, speed = 60,
        moves = listOf(attackDragonBreath(), attackDragonClaw(), attackOutrage())
    )

    // ── OSTATNÍ MAKROMONI (20-31) ─────────────────────────────────────

    // 20 - Finlet (malá akvarijní rybka)
    fun createFinlet() = Makromon(
        name = "FINLET", level = 1,
        maxHp = 20, attack = 8, defense = 8, speed = 40,
        moves = listOf(attackTackle(), attackWaterGun())
    )

    // 21 - Serpfin (obří rybohadí monstrum) – sprite zatím chybí, placeholder
    fun createSerpfin() = Makromon(
        name = "SERPFIN", level = 1,
        maxHp = 65, attack = 25, defense = 20, speed = 15,
        moves = listOf(attackAquaTail(), attackHydroPump(), attackSandAttack())
    )

    // 22 - Mycit (malá koloniální myška s krystalky)
    fun createMycit() = Makromon(
        name = "MYCIT", level = 1,
        maxHp = 28, attack = 35, defense = 30, speed = 45,
        moves = listOf(
            attackTackle(),
            Move("CRYSTAL SHARD", MakromonType.NORMAL, 35, 100, 30)
        )
    )

    // 23 - Mydrus (druidský vůdce, pozřený jedem) – sprite zatím chybí, placeholder
    fun createMydrus() = Makromon(
        name = "MYDRUS", level = 1,
        maxHp = 55, attack = 60, defense = 50, speed = 35,
        moves = listOf(
            attackPoisonSting(),
            attackSludgeBomb(),
            Move("TOXIC AURA", MakromonType.POISON, 70, 90, 15),
            attackSmokescreen()
        )
    )

    // 24 - Soulu – sprite zatím chybí, placeholder
    fun createSoulu() = Makromon(
        name = "SOULU", level = 1,
        maxHp = 25, attack = 12, defense = 8, speed = 18,
        moves = listOf(attackLick(), attackNightShade())
    )

    // 25 - Soulex – sprite zatím chybí, placeholder
    fun createSoulex() = Makromon(
        name = "SOULEX", level = 1,
        maxHp = 35, attack = 16, defense = 12, speed = 22,
        moves = listOf(attackShadowPunch(), attackLick(), attackHypnosis())
    )

    // 26 - Soulord – sprite zatím chybí, placeholder
    fun createSoulord() = Makromon(
        name = "SOULORD", level = 1,
        maxHp = 50, attack = 22, defense = 18, speed = 28,
        moves = listOf(attackShadowBall(), attackShadowPunch(), attackHex(), attackPsychic())
    )

    // 27 - Phantil – sprite zatím chybí, placeholder
    fun createPhantil() = Makromon(
        name = "PHANTIL", level = 1,
        maxHp = 22, attack = 10, defense = 8, speed = 20,
        moves = listOf(attackTackle(), attackWaterGun())
    )

    // 28 - Phantius – sprite zatím chybí, placeholder
    fun createPhantius() = Makromon(
        name = "PHANTIUS", level = 1,
        maxHp = 38, attack = 16, defense = 14, speed = 25,
        moves = listOf(attackWaterPulse(), attackShadowBall(), attackBite())
    )

    // 29 - Phantiax – sprite zatím chybí, placeholder
    fun createPhantiax() = Makromon(
        name = "PHANTIAX", level = 1,
        maxHp = 60, attack = 25, defense = 20, speed = 30,
        moves = listOf(attackHydroPump(), attackShadowBall(), attackDragonPulse(), attackHex())
    )

    // 30 - Gudwin (tlustý medvěd s váčkem – moudrý rádce, ale i bojovník)
    fun createGudwin() = Makromon(
        name = "GUDWIN", level = 10,
        maxHp = 55, attack = 14, defense = 18, speed = 8,
        moves = listOf(
            Move("BODY SLAM",  MakromonType.NORMAL, 85, 85, 15),
            attackTackle(),
            Move("LULLABY",    MakromonType.NORMAL,  0, 80, 15, statEffect = StatEffect.LOWER_ENEMY_ATK),
            Move("WISE WORDS", MakromonType.NORMAL,  0, 100, 20, statEffect = StatEffect.LOWER_ENEMY_DEF)
        )
    )

    // 31 - Axlu (růžový axolotl – tvář aplikace)
    fun createAxlu() = Makromon(
        name = "AXLU", level = 5,
        maxHp = 35, attack = 12, defense = 12, speed = 15,
        moves = listOf(
            attackTackle(),
            attackWaterGun(),
            attackCharm(),
            Move("REGENERATE", MakromonType.NORMAL, 0, 100, 10)
        )
    )

    // ── POMOCNÉ FUNKCE ────────────────────────────────────────────────

    /** Vrátí catch multiplier – těžší Makromoni mají nižší hodnotu */
    fun catchMultiplier(makromon: Makromon): Float = when (makromon.name) {
        "UMBEX"     -> 0.7f
        "LUMEX"     -> 0.7f
        "DRAKIRRA"  -> 0.55f
        "SERPFIN"   -> 0.65f
        "MYDRUS"    -> 0.65f
        "SOULORD"   -> 0.6f
        "PHANTIAX"  -> 0.6f
        "GUDWIN"    -> 0.75f
        "AXLU"      -> 0.3f  // Nejtěžší – tvář aplikace
        else        -> 1.0f
    }

    /** Vrátí Makrodex ID pro daného Makromona */
    fun makrodexId(makromon: Makromon): String = when (makromon.name) {
        "IGNAR"     -> "001"
        "IGNAROC"   -> "002"
        "IGNAROTH"  -> "003"
        "AQULIN"    -> "004"
        "AQULIND"   -> "005"
        "AQULINOX"  -> "006"
        "FLORI"     -> "007"
        "FLORIND"   -> "008"
        "FLORINDRA" -> "009"
        "UMBEX"     -> "010"
        "LUMEX"     -> "011"
        "SPIRRA"    -> "012"
        "FLAMIRRA"  -> "013"
        "AQUIRRA"   -> "014"
        "VERDIRRA"  -> "015"
        "SHADIRRA"  -> "016"
        "CHARMIRRA" -> "017"
        "GLACIRRA"  -> "018"
        "DRAKIRRA"  -> "019"
        "FINLET"    -> "020"
        "SERPFIN"   -> "021"
        "MYCIT"     -> "022"
        "MYDRUS"    -> "023"
        "SOULU"     -> "024"
        "SOULEX"    -> "025"
        "SOULORD"   -> "026"
        "PHANTIL"   -> "027"
        "PHANTIUS"  -> "028"
        "PHANTIAX"  -> "029"
        "GUDWIN"    -> "030"
        "AXLU"      -> "031"
        else        -> "000"
    }

    /**
     * Vrátí název drawable zdroje pro daného Makromona.
     * Formát: "makromon_ID_jmeno" (např. "makromon_18_drakirra")
     */
    fun drawableName(makromon: Makromon): String {
        // 1. Získáme ID (např. "001" nebo "018")
        val fullId = makrodexId(makromon)

        // 2. Ořízneme ID na dvě cifry (např. "018" -> "18")
        // Pokud máš ID 1-9 jako "001", "002", tak takeLast(2) udělá "01", "02"
        val shortId = if (fullId.length >= 3) fullId.takeLast(2) else fullId

        // 3. Jméno převedeme na malá písmena
        val namePart = makromon.name.lowercase().trim()

        // 4. Seznam jmen, pro která už máš v res/drawable složce fyzicky soubor
        val existingSprites = listOf(
            "ignar", "aqulin", "flori", "umbex", "spirra",
            "flamirra", "aquirra", "verdirra", "shadirra",
            "charmirra", "drakirra", "finlet", "mycit",
            "gudwin", "axlu"
        )

        // Pokud ho máš v seznamu, složíme název: makromon_18_drakirra
        return if (existingSprites.contains(namePart)) {
            "makromon_${shortId}_${namePart}"
        } else {
            // Fallback, pokud sprite ještě neexistuje
            "ic_home"
        }
    }
}