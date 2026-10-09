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
    val statEffect: StatEffect? = null,
    /** Stavový efekt (spánek, paralýza…) a jeho šance – viz pokemon/status. */
    val effect: cz.uhk.macroflow.pokemon.status.MoveEffect? = null
) {
    /**
     * Efekt, se kterým souboj počítá: explicitní [effect], jinak starší [statEffect]
     * převedený na snížení útoku / obrany soupeře o 1 stupeň (100 %).
     */
    val fullEffect: cz.uhk.macroflow.pokemon.status.MoveEffect?
        get() = effect ?: when (statEffect) {
            StatEffect.LOWER_ENEMY_ATK -> cz.uhk.macroflow.pokemon.status.MoveEffect(cz.uhk.macroflow.pokemon.status.EffectKind.LOWER_ATK)
            StatEffect.LOWER_ENEMY_DEF -> cz.uhk.macroflow.pokemon.status.MoveEffect(cz.uhk.macroflow.pokemon.status.EffectKind.LOWER_DEF)
            null -> null
        }
}
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
    var alive: Boolean = true,
    /** Typ druhu (docs/adr/0029); null = typ prvního útoku (dřívější chování). */
    val type: MakromonType? = null
) {
    /** Typ pro výpočet účinnosti – s náhodnými útoky už nejde brát první útok. */
    val speciesType: MakromonType get() = type ?: moves.firstOrNull()?.type ?: MakromonType.NORMAL
}

enum class BattlePhase {
    INTRO, MAIN_MENU, FIGHT_MENU, ITEM_MENU,
    ANIMATING, TEXT_WAIT, BALL_THROW, BALL_WOBBLE,
    CAUGHT, ESCAPED, ENEMY_FAINTED, PLAYER_FAINTED
}

data class BattleState(
    var player: Makromon,
    var enemy: Makromon,
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

    /**
     * Náhodný útok s PP. Čistě stavový útok nepoužije, když by nic neudělal
     * (cíl už hlavní stav má, nebo je už zmatený).
     */
    fun enemyChooseMove(
        enemy: Makromon,
        target: cz.uhk.macroflow.pokemon.status.Condition? = null,
        self: cz.uhk.macroflow.pokemon.status.Condition? = null
    ): Move {
        val available = enemy.moves.filter { it.pp > 0 }
        if (available.isEmpty()) return enemy.moves[0]
        val useful = available.filter { mv ->
            val e = mv.fullEffect
            val k = e?.kind
            val S = cz.uhk.macroflow.pokemon.status.StatStages
            if (mv.power > 0 || e == null || target == null) true
            else if (k == cz.uhk.macroflow.pokemon.status.EffectKind.CONFUSE) !target.confused
            else if (k == cz.uhk.macroflow.pokemon.status.EffectKind.FLINCH) true
            // Snížení/zvýšení statistik jen do limitu −6 / +6
            else if (k == cz.uhk.macroflow.pokemon.status.EffectKind.LOWER_ATK) target.atkStage > S.MIN
            else if (k == cz.uhk.macroflow.pokemon.status.EffectKind.LOWER_DEF) target.defStage > S.MIN
            else if (k == cz.uhk.macroflow.pokemon.status.EffectKind.RAISE_ATK) (self?.atkStage ?: 0) < S.MAX
            else if (k == cz.uhk.macroflow.pokemon.status.EffectKind.RAISE_DEF) (self?.defStage ?: 0) < S.MAX
            else target.major == null
        }
        return (useful.ifEmpty { available }).random()
    }
}

object BattleFactory {

    // ── SDÍLENÉ ÚTOKY ─────────────────────────────────────────────────

    // NORMAL
    fun attackTackle()       = Move("TACKLE",       MakromonType.NORMAL,  40, 100, 35)
    fun attackScratch()      = Move("SCRATCH",      MakromonType.NORMAL,  40, 100, 35)
    fun attackSlash()        = Move("SLASH",        MakromonType.NORMAL,  70, 100, 20)
    fun attackGrowl()        = Move("GROWL",        MakromonType.NORMAL,   0, 100, 40, statEffect = StatEffect.LOWER_ENEMY_ATK)
    fun attackHarden()       = Move("HARDEN",       MakromonType.NORMAL,   0, 100, 30, effect = cz.uhk.macroflow.pokemon.status.MoveEffect(cz.uhk.macroflow.pokemon.status.EffectKind.RAISE_DEF))
    fun attackTailWhip()     = Move("TAIL WHIP",    MakromonType.NORMAL,   0, 100, 30, statEffect = StatEffect.LOWER_ENEMY_DEF)
    fun attackLeer()         = Move("LEER",         MakromonType.NORMAL,   0, 100, 30, statEffect = StatEffect.LOWER_ENEMY_DEF)
    fun attackQuickAttack()  = Move("QUICK ATTACK", MakromonType.NORMAL,  40, 100, 30)
    fun attackSlam()         = Move("SLAM",         MakromonType.NORMAL,  80,  75, 20)
    fun attackBite()         = Move("BITE",         MakromonType.NORMAL,  60, 100, 25, effect = cz.uhk.macroflow.pokemon.status.MoveEffect(cz.uhk.macroflow.pokemon.status.EffectKind.FLINCH, 30))
    fun attackCrunch()       = Move("CRUNCH",       MakromonType.NORMAL,  80, 100, 15, effect = cz.uhk.macroflow.pokemon.status.MoveEffect(cz.uhk.macroflow.pokemon.status.EffectKind.LOWER_DEF, 20))
    fun attackSmokescreen()  = Move("SMOKESCREEN",  MakromonType.NORMAL,   0, 100, 20, effect = cz.uhk.macroflow.pokemon.status.MoveEffect(cz.uhk.macroflow.pokemon.status.EffectKind.LOWER_ATK))
    fun attackHyperFang()    = Move("HYPER FANG",   MakromonType.NORMAL,  80,  90, 15, effect = cz.uhk.macroflow.pokemon.status.MoveEffect(cz.uhk.macroflow.pokemon.status.EffectKind.FLINCH, 10))
    fun attackFuryAttack()   = Move("FURY ATTACK",  MakromonType.NORMAL,  15,  85, 20)

    // FIRE
    fun attackEmber()        = Move("EMBER",        MakromonType.FIRE,  40, 100, 25, effect = cz.uhk.macroflow.pokemon.status.MoveEffect(cz.uhk.macroflow.pokemon.status.EffectKind.BURN, 10))
    fun attackFireFang()     = Move("FIRE FANG",    MakromonType.FIRE,  65,  95, 15, effect = cz.uhk.macroflow.pokemon.status.MoveEffect(cz.uhk.macroflow.pokemon.status.EffectKind.BURN, 10))
    fun attackFlamethrower() = Move("FLAMETHROWER", MakromonType.FIRE,  90, 100, 15, effect = cz.uhk.macroflow.pokemon.status.MoveEffect(cz.uhk.macroflow.pokemon.status.EffectKind.BURN, 10))
    fun attackFireBlast()    = Move("FIRE BLAST",   MakromonType.FIRE, 110,  85,  5, effect = cz.uhk.macroflow.pokemon.status.MoveEffect(cz.uhk.macroflow.pokemon.status.EffectKind.BURN, 10))
    fun attackHeatWave()     = Move("HEAT WAVE",    MakromonType.FIRE,  95,  90, 10, effect = cz.uhk.macroflow.pokemon.status.MoveEffect(cz.uhk.macroflow.pokemon.status.EffectKind.BURN, 10))

    // WATER
    fun attackWaterGun()     = Move("WATER GUN",    MakromonType.WATER,  40, 100, 25)
    fun attackWaterPulse()   = Move("WATER PULSE",  MakromonType.WATER,  60, 100, 20, effect = cz.uhk.macroflow.pokemon.status.MoveEffect(cz.uhk.macroflow.pokemon.status.EffectKind.CONFUSE, 20))
    fun attackHydroPump()    = Move("HYDRO PUMP",   MakromonType.WATER, 110,  80,  5)
    fun attackAquaTail()     = Move("AQUA TAIL",    MakromonType.WATER,  90,  90, 10)
    fun attackBubbleBeam()   = Move("BUBBLE BEAM",  MakromonType.WATER,  65, 100, 20, effect = cz.uhk.macroflow.pokemon.status.MoveEffect(cz.uhk.macroflow.pokemon.status.EffectKind.LOWER_ATK, 10))

    // GRASS
    fun attackVineWhip()     = Move("VINE WHIP",    MakromonType.GRASS,  45, 100, 25)
    fun attackRazorLeaf()    = Move("RAZOR LEAF",   MakromonType.GRASS,  55,  95, 25)
    fun attackSeedBomb()     = Move("SEED BOMB",    MakromonType.GRASS,  80, 100, 15)
    fun attackSolarBeam()    = Move("SOLAR BEAM",   MakromonType.GRASS, 120, 100, 10)
    fun attackLeafBlade()    = Move("LEAF BLADE",   MakromonType.GRASS,  90, 100, 15)
    fun attackSleepPowder()  = Move("SLEEP POWDER", MakromonType.GRASS,   0,  75, 20, effect = cz.uhk.macroflow.pokemon.status.MoveEffect(cz.uhk.macroflow.pokemon.status.EffectKind.SLEEP, 100))

    // GHOST
    fun attackShadowBall()   = Move("SHADOW BALL",  MakromonType.GHOST,  65,  80, 15, effect = cz.uhk.macroflow.pokemon.status.MoveEffect(cz.uhk.macroflow.pokemon.status.EffectKind.LOWER_DEF, 20))
    fun attackShadowPunch()  = Move("SHADOW PUNCH", MakromonType.GHOST,  60, 100, 20)
    fun attackLick()         = Move("LICK",         MakromonType.GHOST,  30, 100, 30, effect = cz.uhk.macroflow.pokemon.status.MoveEffect(cz.uhk.macroflow.pokemon.status.EffectKind.PARALYZE, 30))
    fun attackNightShade()   = Move("NIGHT SHADE",  MakromonType.GHOST,  40,  95, 15)
    fun attackHex()          = Move("HEX",          MakromonType.GHOST,  65, 100, 10)

    // FAIRY
    fun attackDazzlingGleam() = Move("DAZZL.GLEAM", MakromonType.FAIRY,  80, 100, 10)
    fun attackMoonblast()     = Move("MOONBLAST",   MakromonType.FAIRY,  95, 100, 15, effect = cz.uhk.macroflow.pokemon.status.MoveEffect(cz.uhk.macroflow.pokemon.status.EffectKind.LOWER_ATK, 30))
    // CHARM sníží útok o 2 stupně („sharply“)
    fun attackCharm()         = Move("CHARM",       MakromonType.FAIRY,   0, 100, 20, effect = cz.uhk.macroflow.pokemon.status.MoveEffect(cz.uhk.macroflow.pokemon.status.EffectKind.LOWER_ATK, 100, stages = 2))
    fun attackPlayRough()     = Move("PLAY ROUGH",  MakromonType.FAIRY,  90,  90, 10, effect = cz.uhk.macroflow.pokemon.status.MoveEffect(cz.uhk.macroflow.pokemon.status.EffectKind.LOWER_ATK, 10))
    fun attackBabyDollEyes()  = Move("BABY-DOLL",   MakromonType.FAIRY,   0, 100, 30, statEffect = StatEffect.LOWER_ENEMY_ATK)

    // DRAGON
    fun attackDragonClaw()   = Move("DRAGON CLAW",  MakromonType.DRAGON,  80, 100, 15)
    fun attackDragonBreath() = Move("DRAGONBREATH", MakromonType.DRAGON,  60, 100, 20, effect = cz.uhk.macroflow.pokemon.status.MoveEffect(cz.uhk.macroflow.pokemon.status.EffectKind.PARALYZE, 30))
    fun attackDragonPulse()  = Move("DRAGON PULSE", MakromonType.DRAGON,  85,100, 10)
    fun attackOutrage()      = Move("OUTRAGE",      MakromonType.DRAGON, 120, 100, 10)

    // GROUND
    fun attackSandAttack()   = Move("SAND ATTACK",  MakromonType.GROUND,   0, 100, 15, statEffect = StatEffect.LOWER_ENEMY_ATK)
    fun attackMudSlap()      = Move("MUD-SLAP",     MakromonType.GROUND,  20, 100, 10, effect = cz.uhk.macroflow.pokemon.status.MoveEffect(cz.uhk.macroflow.pokemon.status.EffectKind.LOWER_ATK, 100))
    fun attackDig()          = Move("DIG",          MakromonType.GROUND,  80, 100, 10)
    fun attackEarthquake()   = Move("EARTHQUAKE",   MakromonType.GROUND, 100, 100, 10)

    // PSYCHIC
    fun attackPsychic()      = Move("PSYCHIC",      MakromonType.PSYCHIC, 90,  90, 10, effect = cz.uhk.macroflow.pokemon.status.MoveEffect(cz.uhk.macroflow.pokemon.status.EffectKind.LOWER_DEF, 10))
    fun attackHypnosis()     = Move("HYPNOSIS",     MakromonType.PSYCHIC,  0,  60, 20, effect = cz.uhk.macroflow.pokemon.status.MoveEffect(cz.uhk.macroflow.pokemon.status.EffectKind.SLEEP, 100))

    // ELECTRIC
    fun attackThunderShock() = Move("THUNDER SHOCK",MakromonType.ELECTRIC,  40, 100, 30, effect = cz.uhk.macroflow.pokemon.status.MoveEffect(cz.uhk.macroflow.pokemon.status.EffectKind.PARALYZE, 10))
    fun attackThunderbolt()  = Move("THUNDERBOLT",  MakromonType.ELECTRIC,  90, 100, 15, effect = cz.uhk.macroflow.pokemon.status.MoveEffect(cz.uhk.macroflow.pokemon.status.EffectKind.PARALYZE, 10))

    // POISON
    fun attackPoisonSting()  = Move("POISON STING", MakromonType.POISON,  15, 100, 35, effect = cz.uhk.macroflow.pokemon.status.MoveEffect(cz.uhk.macroflow.pokemon.status.EffectKind.POISON, 30))
    fun attackSludgeBomb()   = Move("SLUDGE BOMB",  MakromonType.POISON,  90, 100, 10, effect = cz.uhk.macroflow.pokemon.status.MoveEffect(cz.uhk.macroflow.pokemon.status.EffectKind.POISON, 30))

    // STAVOVÉ ÚTOKY (bez zranění, jen efekt)
    fun attackToxic()        = Move("TOXIC",        MakromonType.POISON,   0,  90, 10, effect = cz.uhk.macroflow.pokemon.status.MoveEffect(cz.uhk.macroflow.pokemon.status.EffectKind.POISON, 100))
    fun attackConfuseRay()   = Move("CONFUSE RAY",  MakromonType.GHOST,    0, 100, 10, effect = cz.uhk.macroflow.pokemon.status.MoveEffect(cz.uhk.macroflow.pokemon.status.EffectKind.CONFUSE, 100))
    fun attackWillOWisp()    = Move("WILL-O-WISP",  MakromonType.FIRE,     0,  85, 15, effect = cz.uhk.macroflow.pokemon.status.MoveEffect(cz.uhk.macroflow.pokemon.status.EffectKind.BURN, 100))

    // BUG
    fun attackStringShot()   = Move("STRING SHOT",  MakromonType.BUG,      0,  95, 40, statEffect = StatEffect.LOWER_ENEMY_DEF)

    // FLYING
    fun attackGust()         = Move("GUST",         MakromonType.FLYING,  40, 100, 35)
    fun attackWingAttack()   = Move("WING ATTACK",  MakromonType.FLYING,  60, 100, 35)

    // ── DRUHY ─────────────────────────────────────────────────────────
    // Statistiky, útoky a všechno ostatní o druzích je v registru species/Species.kt (docs/adr/0067).

    /** Základní (level 1) Makromon podle čísla v Makrodexu; neznámé číslo → Spirra. */
    fun createById(id: String): Makromon = cz.uhk.macroflow.pokemon.species.SpeciesRegistry.create(id)

    /** Násobič šance chycení – těžší Makromoni mají nižší hodnotu. */
    fun catchMultiplier(makromon: Makromon): Float =
        cz.uhk.macroflow.pokemon.species.SpeciesRegistry.byName(makromon.name)?.catchRate ?: 1f

    /** Číslo Makrodexu strážců (makrodexId u nich vrací druh, z něhož berou statistiky). */
    val GUARDIAN_DEX: Map<String, String> by lazy {
        cz.uhk.macroflow.pokemon.species.SpeciesRegistry.ALL.filter { it.guardianOf != null }.associate { it.name to it.id }
    }

    /** Číslo Makrodexu druhu (u strážců druh, z něhož berou statistiky); neznámé jméno → "000". */
    fun makrodexId(makromon: Makromon): String =
        cz.uhk.macroflow.pokemon.species.SpeciesRegistry.byName(makromon.name)?.let { it.guardianOf ?: it.id } ?: "000"

    /** Název obrázku, např. "makromon_18_drakirra"; neznámý druh → ic_home. Chybějící soubor řeší volající. */
    fun drawableName(makromon: Makromon): String =
        cz.uhk.macroflow.pokemon.species.SpeciesRegistry.byName(makromon.name)?.sprite ?: "ic_home"

}