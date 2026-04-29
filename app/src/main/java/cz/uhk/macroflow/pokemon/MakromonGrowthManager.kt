package cz.uhk.macroflow.pokemon

// 📋 1. Třída pro jeden záznam učení útoku
data class LearnableMove(
    val level: Int,
    val move: Move
)

// 📋 2. Třída pro růstovou křivku konkrétního Makromona
data class MakromonGrowthProfile(
    val makrodexId: String,
    val evolutionLevel: Int = 0,
    val evolutionToId: String = "",
    val movesLearnedAt: List<LearnableMove> = emptyList()
)

// 🧠 3. Centrální registr růstových křivek všech Makromonů
object MakromonGrowthManager {

    private val GROWTH_DATABASE: Map<String, MakromonGrowthProfile> = mapOf(

        // ── IGNAR RODINA (001 -> 002 -> 003) ─────────────────────────
        "001" to MakromonGrowthProfile(
            makrodexId = "001",
            evolutionLevel = 4,
            evolutionToId = "002",
            movesLearnedAt = listOf(
                LearnableMove(1, BattleFactory.attackScratch()),
                LearnableMove(1, BattleFactory.attackGrowl()),
                LearnableMove(3, BattleFactory.attackEmber())
            )
        ),
        "002" to MakromonGrowthProfile(
            makrodexId = "002",
            evolutionLevel = 10,
            evolutionToId = "003",
            movesLearnedAt = listOf(
                LearnableMove(4, BattleFactory.attackSmokescreen()),
                LearnableMove(7, BattleFactory.attackFireFang()),
                LearnableMove(9, BattleFactory.attackSlash())
            )
        ),
        "003" to MakromonGrowthProfile(
            makrodexId = "003",
            evolutionLevel = 0,
            evolutionToId = "",
            movesLearnedAt = listOf(
                LearnableMove(10, BattleFactory.attackFlamethrower()),
                LearnableMove(13, BattleFactory.attackDragonClaw()),
                LearnableMove(15, BattleFactory.attackFireBlast()),
                LearnableMove(20, BattleFactory.attackHeatWave()),
                LearnableMove(25, BattleFactory.attackOutrage())
            )
        ),

        // ── AQULIN RODINA (004 -> 005 -> 006) ────────────────────────
        "004" to MakromonGrowthProfile(
            makrodexId = "004",
            evolutionLevel = 4,
            evolutionToId = "005",
            movesLearnedAt = listOf(
                LearnableMove(1, BattleFactory.attackTackle()),
                LearnableMove(1, BattleFactory.attackGrowl()),
                LearnableMove(3, BattleFactory.attackWaterGun())
            )
        ),
        "005" to MakromonGrowthProfile(
            makrodexId = "005",
            evolutionLevel = 10,
            evolutionToId = "006",
            movesLearnedAt = listOf(
                LearnableMove(4, BattleFactory.attackBite()),
                LearnableMove(7, BattleFactory.attackBubbleBeam()),
                LearnableMove(9, BattleFactory.attackWaterPulse())
            )
        ),
        "006" to MakromonGrowthProfile(
            makrodexId = "006",
            evolutionLevel = 0,
            evolutionToId = "",
            movesLearnedAt = listOf(
                LearnableMove(10, BattleFactory.attackAquaTail()),
                LearnableMove(15, BattleFactory.attackCrunch()),
                LearnableMove(20, BattleFactory.attackHydroPump())
            )
        ),

        // ── FLORI RODINA (007 -> 008 -> 009) ─────────────────────────
        "007" to MakromonGrowthProfile(
            makrodexId = "007",
            evolutionLevel = 4,
            evolutionToId = "008",
            movesLearnedAt = listOf(
                LearnableMove(1, BattleFactory.attackTackle()),
                LearnableMove(1, BattleFactory.attackGrowl()),
                LearnableMove(3, BattleFactory.attackVineWhip())
            )
        ),
        "008" to MakromonGrowthProfile(
            makrodexId = "008",
            evolutionLevel = 10,
            evolutionToId = "009",
            movesLearnedAt = listOf(
                LearnableMove(4, BattleFactory.attackRazorLeaf()),
                LearnableMove(7, BattleFactory.attackSeedBomb()),
                LearnableMove(9, BattleFactory.attackSleepPowder())
            )
        ),
        "009" to MakromonGrowthProfile(
            makrodexId = "009",
            evolutionLevel = 0,
            evolutionToId = "",
            movesLearnedAt = listOf(
                LearnableMove(10, BattleFactory.attackLeafBlade()),
                LearnableMove(15, Move("PETAL DANCE", MakromonType.GRASS, 120, 100, 10)),
                LearnableMove(20, BattleFactory.attackSolarBeam()),
                LearnableMove(25, Move("WOOD HAMMER", MakromonType.GRASS, 120, 100, 15))
            )
        ),

        // ── UMBEX / LUMEX (010, 011) – nevyvíjejí se ─────────────────
        "010" to MakromonGrowthProfile(
            makrodexId = "010",
            evolutionLevel = 0,
            evolutionToId = "",
            movesLearnedAt = listOf(
                LearnableMove(1,  BattleFactory.attackLick()),
                LearnableMove(1,  BattleFactory.attackNightShade()),
                LearnableMove(5,  BattleFactory.attackShadowBall()),
                LearnableMove(10, BattleFactory.attackHex()),
                LearnableMove(15, Move("PAIN SPLIT", MakromonType.GHOST, 0, 100, 20)),
                LearnableMove(20, Move("MEMENTO",    MakromonType.GHOST, 0, 100, 10, statEffect = StatEffect.LOWER_ENEMY_ATK))
            )
        ),
        "011" to MakromonGrowthProfile(
            makrodexId = "011",
            evolutionLevel = 0,
            evolutionToId = "",
            movesLearnedAt = listOf(
                LearnableMove(1,  BattleFactory.attackDazzlingGleam()),
                LearnableMove(1,  BattleFactory.attackHex()),
                LearnableMove(5,  BattleFactory.attackCharm()),
                LearnableMove(10, BattleFactory.attackShadowBall()),
                LearnableMove(15, Move("DARK PULSE",  MakromonType.GHOST, 80, 100, 15)),
                LearnableMove(20, BattleFactory.attackMoonblast())
            )
        ),

        // ── SPIRRA RODINA (012 → evoluce dle živlu) ──────────────────
        // Spirra se nevyvíjí přirozeně – evoluce se volí hráčem
        "012" to MakromonGrowthProfile(
            makrodexId = "012",
            evolutionLevel = 0,
            evolutionToId = "",
            movesLearnedAt = listOf(
                LearnableMove(1, BattleFactory.attackTackle()),
                LearnableMove(1, BattleFactory.attackGrowl()),
                LearnableMove(3, BattleFactory.attackQuickAttack()),
                LearnableMove(6, BattleFactory.attackSlash()),
                LearnableMove(10, Move("SPIRAL SPIN", MakromonType.NORMAL, 60, 100, 20)),
                LearnableMove(15, BattleFactory.attackSlam())
            )
        ),
        "013" to MakromonGrowthProfile(
            makrodexId = "013",
            evolutionLevel = 0,
            evolutionToId = "",
            movesLearnedAt = listOf(
                LearnableMove(1,  BattleFactory.attackEmber()),
                LearnableMove(5,  BattleFactory.attackFireFang()),
                LearnableMove(10, BattleFactory.attackFlamethrower()),
                LearnableMove(15, BattleFactory.attackHeatWave()),
                LearnableMove(20, BattleFactory.attackFireBlast())
            )
        ),
        "014" to MakromonGrowthProfile(
            makrodexId = "014",
            evolutionLevel = 0,
            evolutionToId = "",
            movesLearnedAt = listOf(
                LearnableMove(1,  BattleFactory.attackWaterGun()),
                LearnableMove(5,  BattleFactory.attackBubbleBeam()),
                LearnableMove(10, BattleFactory.attackWaterPulse()),
                LearnableMove(15, BattleFactory.attackAquaTail()),
                LearnableMove(20, BattleFactory.attackHydroPump())
            )
        ),
        "015" to MakromonGrowthProfile(
            makrodexId = "015",
            evolutionLevel = 0,
            evolutionToId = "",
            movesLearnedAt = listOf(
                LearnableMove(1,  BattleFactory.attackVineWhip()),
                LearnableMove(5,  BattleFactory.attackRazorLeaf()),
                LearnableMove(10, BattleFactory.attackSeedBomb()),
                LearnableMove(15, BattleFactory.attackLeafBlade()),
                LearnableMove(20, BattleFactory.attackSolarBeam())
            )
        ),
        "016" to MakromonGrowthProfile(
            makrodexId = "016",
            evolutionLevel = 0,
            evolutionToId = "",
            movesLearnedAt = listOf(
                LearnableMove(1,  BattleFactory.attackLick()),
                LearnableMove(5,  BattleFactory.attackShadowBall()),
                LearnableMove(10, BattleFactory.attackShadowPunch()),
                LearnableMove(15, BattleFactory.attackHex()),
                LearnableMove(20, Move("PHANTOM FORCE", MakromonType.GHOST, 90, 100, 10))
            )
        ),
        "017" to MakromonGrowthProfile(
            makrodexId = "017",
            evolutionLevel = 0,
            evolutionToId = "",
            movesLearnedAt = listOf(
                LearnableMove(1,  BattleFactory.attackCharm()),
                LearnableMove(5,  BattleFactory.attackDazzlingGleam()),
                LearnableMove(10, BattleFactory.attackBabyDollEyes()),
                LearnableMove(15, BattleFactory.attackPlayRough()),
                LearnableMove(20, BattleFactory.attackMoonblast())
            )
        ),
        "018" to MakromonGrowthProfile(
            makrodexId = "018",
            evolutionLevel = 0,
            evolutionToId = "",
            movesLearnedAt = listOf(
                LearnableMove(1,  Move("ICE SHARD",  MakromonType.NORMAL, 40, 100, 30)),
                LearnableMove(5,  Move("ICE FANG",   MakromonType.WATER,  65, 95,  15)),
                LearnableMove(10, Move("AURORA BEAM", MakromonType.WATER, 65, 100, 20)),
                LearnableMove(15, Move("BLIZZARD",    MakromonType.WATER, 110, 70,  5)),
                LearnableMove(20, Move("FREEZE-DRY",  MakromonType.WATER, 70, 100, 20))
            )
        ),
        "019" to MakromonGrowthProfile(
            makrodexId = "019",
            evolutionLevel = 0,
            evolutionToId = "",
            movesLearnedAt = listOf(
                LearnableMove(1,  BattleFactory.attackDragonBreath()),
                LearnableMove(5,  BattleFactory.attackDragonClaw()),
                LearnableMove(10, BattleFactory.attackDragonPulse()),
                LearnableMove(15, BattleFactory.attackOutrage()),
                LearnableMove(20, Move("DRAGON DANCE", MakromonType.DRAGON, 0, 100, 20))
            )
        ),

        // ── FINLET / SERPFIN (020 -> 021) ────────────────────────────
        "020" to MakromonGrowthProfile(
            makrodexId = "020",
            evolutionLevel = 8,
            evolutionToId = "021",
            movesLearnedAt = listOf(
                LearnableMove(1, BattleFactory.attackTackle()),
                LearnableMove(1, BattleFactory.attackWaterGun()),
                LearnableMove(4, BattleFactory.attackBubbleBeam()),
                LearnableMove(7, BattleFactory.attackSandAttack())
            )
        ),
        "021" to MakromonGrowthProfile(
            makrodexId = "021",
            evolutionLevel = 0,
            evolutionToId = "",
            movesLearnedAt = listOf(
                LearnableMove(8,  BattleFactory.attackAquaTail()),
                LearnableMove(12, BattleFactory.attackHydroPump()),
                LearnableMove(18, Move("AQUA RING",   MakromonType.WATER, 0, 100, 20)),
                LearnableMove(25, Move("HYDRO CANNON", MakromonType.WATER, 150, 90, 5))
            )
        ),

        // ── MYCIT / MYDRUS (022 -> 023) ───────────────────────────────
        "022" to MakromonGrowthProfile(
            makrodexId = "022",
            evolutionLevel = 7,
            evolutionToId = "023",
            movesLearnedAt = listOf(
                LearnableMove(1, BattleFactory.attackTackle()),
                LearnableMove(1, Move("CRYSTAL SHARD", MakromonType.NORMAL, 35, 100, 30)),
                LearnableMove(4, BattleFactory.attackGrowl()),
                LearnableMove(6, BattleFactory.attackPoisonSting())
            )
        ),
        "023" to MakromonGrowthProfile(
            makrodexId = "023",
            evolutionLevel = 0,
            evolutionToId = "",
            movesLearnedAt = listOf(
                LearnableMove(7,  BattleFactory.attackSludgeBomb()),
                LearnableMove(10, Move("TOXIC AURA",  MakromonType.POISON, 70, 90, 15)),
                LearnableMove(15, Move("ACID SPRAY",  MakromonType.POISON, 40, 100, 20, statEffect = StatEffect.LOWER_ENEMY_DEF)),
                LearnableMove(20, Move("BELCH",       MakromonType.POISON, 120, 90, 10)),
                LearnableMove(25, Move("POISON GAS",  MakromonType.POISON, 0, 90, 40, statEffect = StatEffect.LOWER_ENEMY_ATK))
            )
        ),

        // ── SOULU RODINA (024 -> 025 -> 026) ─────────────────────────
        "024" to MakromonGrowthProfile(
            makrodexId = "024",
            evolutionLevel = 5,
            evolutionToId = "025",
            movesLearnedAt = listOf(
                LearnableMove(1, BattleFactory.attackLick()),
                LearnableMove(1, BattleFactory.attackNightShade()),
                LearnableMove(4, BattleFactory.attackHypnosis())
            )
        ),
        "025" to MakromonGrowthProfile(
            makrodexId = "025",
            evolutionLevel = 10,
            evolutionToId = "026",
            movesLearnedAt = listOf(
                LearnableMove(5,  BattleFactory.attackShadowPunch()),
                LearnableMove(8,  BattleFactory.attackShadowBall()),
                LearnableMove(9,  BattleFactory.attackHex())
            )
        ),
        "026" to MakromonGrowthProfile(
            makrodexId = "026",
            evolutionLevel = 0,
            evolutionToId = "",
            movesLearnedAt = listOf(
                LearnableMove(10, BattleFactory.attackPsychic()),
                LearnableMove(15, Move("DARK PULSE",    MakromonType.GHOST,   80, 100, 15)),
                LearnableMove(20, Move("PHANTOM FORCE", MakromonType.GHOST,   90, 100, 10)),
                LearnableMove(25, Move("SOUL DRAIN",    MakromonType.GHOST,  100,  90, 10))
            )
        ),

        // ── PHANTIL RODINA (027 -> 028 -> 029) ───────────────────────
        "027" to MakromonGrowthProfile(
            makrodexId = "027",
            evolutionLevel = 6,
            evolutionToId = "028",
            movesLearnedAt = listOf(
                LearnableMove(1, BattleFactory.attackTackle()),
                LearnableMove(1, BattleFactory.attackWaterGun()),
                LearnableMove(4, BattleFactory.attackLick()),
                LearnableMove(5, BattleFactory.attackBubbleBeam())
            )
        ),
        "028" to MakromonGrowthProfile(
            makrodexId = "028",
            evolutionLevel = 12,
            evolutionToId = "029",
            movesLearnedAt = listOf(
                LearnableMove(6,  BattleFactory.attackWaterPulse()),
                LearnableMove(9,  BattleFactory.attackShadowBall()),
                LearnableMove(11, BattleFactory.attackHex())
            )
        ),
        "029" to MakromonGrowthProfile(
            makrodexId = "029",
            evolutionLevel = 0,
            evolutionToId = "",
            movesLearnedAt = listOf(
                LearnableMove(12, BattleFactory.attackHydroPump()),
                LearnableMove(15, BattleFactory.attackDragonPulse()),
                LearnableMove(20, Move("SPECTRAL TIDE", MakromonType.GHOST, 90, 100, 10)),
                LearnableMove(25, Move("ORIGIN PULSE",  MakromonType.WATER, 110, 85, 10))
            )
        ),

        // ── GUDWIN (030) – nevyvíjí se ───────────────────────────────
        "030" to MakromonGrowthProfile(
            makrodexId = "030",
            evolutionLevel = 0,
            evolutionToId = "",
            movesLearnedAt = listOf(
                LearnableMove(1,  Move("BODY SLAM",  MakromonType.NORMAL, 85, 85, 15)),
                LearnableMove(1,  Move("LULLABY",    MakromonType.NORMAL,  0, 80, 15, statEffect = StatEffect.LOWER_ENEMY_ATK)),
                LearnableMove(5,  Move("WISE WORDS", MakromonType.NORMAL,  0, 100, 20, statEffect = StatEffect.LOWER_ENEMY_DEF)),
                LearnableMove(10, Move("HEAVY SLAM", MakromonType.NORMAL, 100, 100, 10)),
                LearnableMove(15, Move("SNORE",      MakromonType.NORMAL,  50, 100, 15)),
                LearnableMove(20, BattleFactory.attackSlam())
            )
        ),

        // ── AXLU (031) – nevyvíjí se, tvář aplikace ──────────────────
        "031" to MakromonGrowthProfile(
            makrodexId = "031",
            evolutionLevel = 0,
            evolutionToId = "",
            movesLearnedAt = listOf(
                LearnableMove(1,  BattleFactory.attackWaterGun()),
                LearnableMove(1,  BattleFactory.attackCharm()),
                LearnableMove(5,  Move("REGENERATE",  MakromonType.NORMAL,  0, 100, 10)),
                LearnableMove(10, BattleFactory.attackBubbleBeam()),
                LearnableMove(15, BattleFactory.attackDazzlingGleam()),
                LearnableMove(20, BattleFactory.attackHydroPump()),
                LearnableMove(25, BattleFactory.attackMoonblast())
            )
        )
    )

    fun getProfile(makrodexId: String): MakromonGrowthProfile? = GROWTH_DATABASE[makrodexId]

    fun getNewMoveForLevel(makrodexId: String, level: Int): Move? {
        val profile = getProfile(makrodexId) ?: return null
        return profile.movesLearnedAt.find { it.level == level }?.move
    }
}