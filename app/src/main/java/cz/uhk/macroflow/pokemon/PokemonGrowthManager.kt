package cz.uhk.macroflow.pokemon

// 📋 1. Třída pro jeden záznam učení útoku
data class LearnableMove(
    val level: Int,
    val move: Move
)

// 📋 2. Třída pro růstovou křivku konkrétního Pokémona
data class PokemonGrowthProfile(
    val pokedexId: String,
    val evolutionLevel: Int = 0,    // 0 = nevyvíjí se
    val evolutionToId: String = "", // Kam se vyvíjí
    val movesLearnedAt: List<LearnableMove> = emptyList() // Kdy a jaké útoky se učí
)

// 🧠 3. Centrální registr pro všechny Pokémony ve hře
object PokemonGrowthManager {

    private val GROWTH_DATABASE: Map<String, PokemonGrowthProfile> = mapOf(

        // --- 🌿 BULBASAUR RODINA (001 -> 002 -> 003) ---

        "001" to PokemonGrowthProfile(
            pokedexId = "001",
            evolutionLevel = 4,
            evolutionToId = "002",
            movesLearnedAt = listOf(
                LearnableMove(1, BattleFactory.attackTackle()),
                LearnableMove(1, BattleFactory.attackGrowl()),
                LearnableMove(3, BattleFactory.attackVineWhip())
            )
        ),

        "002" to PokemonGrowthProfile(
            pokedexId = "002",
            evolutionLevel = 10,
            evolutionToId = "003",
            movesLearnedAt = listOf(
                LearnableMove(4, Move("GROWTH", PokemonType.NORMAL, 0, 100, 20)),
                LearnableMove(7, BattleFactory.attackRazorLeaf()),
                LearnableMove(9, BattleFactory.attackSleepPowder())
            )
        ),

        "003" to PokemonGrowthProfile(
            pokedexId = "003",
            evolutionLevel = 0,
            evolutionToId = "",
            movesLearnedAt = listOf(
                LearnableMove(10, Move("PETAL DANCE", PokemonType.GRASS, 120, 100, 10)),
                LearnableMove(12, BattleFactory.attackSeedBomb()),
                LearnableMove(15, Move("TAKE DOWN", PokemonType.NORMAL, 90, 85, 20)),
                LearnableMove(20, Move("SWEET SCENT", PokemonType.NORMAL, 0, 100, 20)),
                LearnableMove(25, Move("DOUBLE-EDGE", PokemonType.NORMAL, 120, 100, 15)),
                LearnableMove(30, BattleFactory.attackSolarBeam()) // Nejsilnější útok na konec
            )
        ),


        // --- 🔥 CHARMANDER RODINA ---
        "004" to PokemonGrowthProfile(
            pokedexId = "004",
            evolutionLevel = 4,
            evolutionToId = "005",
            movesLearnedAt = listOf(
                LearnableMove(1, BattleFactory.attackScratch()),
                LearnableMove(1, BattleFactory.attackGrowl()),
                LearnableMove(3, BattleFactory.attackEmber())
            )
        ),

        "005" to PokemonGrowthProfile(
            pokedexId = "005",
            evolutionLevel = 10,
            evolutionToId = "006",
            movesLearnedAt = listOf(
                LearnableMove(4, BattleFactory.attackSmokescreen()),
                LearnableMove(7, BattleFactory.attackFireFang()),
                LearnableMove(9, BattleFactory.attackSlash())
            )
        ),

        "006" to PokemonGrowthProfile(
            pokedexId = "006",
            evolutionLevel = 0,
            movesLearnedAt = listOf(
                LearnableMove(10, BattleFactory.attackWingAttack()),
                LearnableMove(11, BattleFactory.attackFlamethrower()),
                LearnableMove(13, BattleFactory.attackDragonClaw()),
                LearnableMove(15, BattleFactory.attackFireBlast())
            )
        ),

        // SQUIRTLE LINE
        "007" to PokemonGrowthProfile(
            "007", 4, "008", listOf(
            LearnableMove(1, BattleFactory.attackTackle()),
            LearnableMove(3, Move("WATER GUN", PokemonType.WATER, 40, 100, 25))
        )),
        "008" to PokemonGrowthProfile(
            "008", 10, "009", listOf(
            LearnableMove(4, Move("BITE", PokemonType.NORMAL, 60, 100, 25)),
            LearnableMove(7, Move("WATER PULSE", PokemonType.WATER, 60, 100, 20))
        )),
        "009" to PokemonGrowthProfile(
            "009", 0, "", listOf(
            LearnableMove(10, Move("FLASH CANNON", PokemonType.NORMAL, 80, 100, 10)),
            LearnableMove(30, Move("HYDRO PUMP", PokemonType.WATER, 110, 80, 5))
        )),

        // --- 🐛 CATERPIE RODINA ---
        "010" to PokemonGrowthProfile(
            pokedexId = "010",
            evolutionLevel = 7,
            evolutionToId = "011",
            movesLearnedAt = listOf(
                LearnableMove(1, BattleFactory.attackTackle()),
                LearnableMove(1, BattleFactory.attackStringShot())
            )
        ),
        "011" to PokemonGrowthProfile(
            pokedexId = "011",
            evolutionLevel = 10,
            evolutionToId = "012",
            movesLearnedAt = listOf(
                LearnableMove(7, BattleFactory.attackHarden()) // Naučí se při evoluci na Metapoda
            )
        ),
        "012" to PokemonGrowthProfile(
            pokedexId = "012",
            movesLearnedAt = listOf(
                LearnableMove(10, BattleFactory.attackGust()) // Naučí se při evoluci na Butterfree
            )
        ),

        "013" to PokemonGrowthProfile("013", 3, "014", listOf(
            LearnableMove(1, BattleFactory.attackPoisonSting()),
            LearnableMove(1, BattleFactory.attackStringShot())
        )),
        "014" to PokemonGrowthProfile("014", 7, "015", listOf(
            LearnableMove(3, BattleFactory.attackHarden())
        )),
        "015" to PokemonGrowthProfile("015", 0, "", listOf(
            LearnableMove(7, BattleFactory.attackFuryAttack()),
            LearnableMove(9, BattleFactory.attackTwineedle()),
            LearnableMove(15, Move("RAGE", PokemonType.NORMAL, 20, 100, 20)),
            LearnableMove(25, BattleFactory.attackPinMissile())
        )),

        // PIDGEY LINE (016 -> 017 -> 018)
        "016" to PokemonGrowthProfile("016", 4, "017", listOf(
            LearnableMove(1, BattleFactory.attackTackle()),
            LearnableMove(2, Move("SAND ATTACK", PokemonType.GROUND, 0, 100, 15)),
            LearnableMove(3, BattleFactory.attackGust())
        )),
        "017" to PokemonGrowthProfile("017", 10, "018", listOf(
            LearnableMove(4, BattleFactory.attackQuickAttack()),
            LearnableMove(7, Move("TWISTER", PokemonType.DRAGON, 40, 100, 20)),
            LearnableMove(9, BattleFactory.attackWingAttack())
        )),
        "018" to PokemonGrowthProfile("018", 0, "", listOf(
            LearnableMove(10, BattleFactory.attackAirSlash()),
            LearnableMove(15, Move("ROOST", PokemonType.FLYING, 0, 100, 10)), // Heal mechanika (volitelně)
            LearnableMove(25, Move("AIR CUTTER", PokemonType.FLYING, 60, 95, 25)),
            LearnableMove(30, BattleFactory.attackHurricane())
        )),

        // RATTATA LINE (019 -> 020)
        "019" to PokemonGrowthProfile("019", 4, "020", listOf(
            LearnableMove(1, BattleFactory.attackTackle()),
            LearnableMove(1, BattleFactory.attackTailWhip()),
            LearnableMove(2, BattleFactory.attackQuickAttack()),
            LearnableMove(3, Move("BITE", PokemonType.NORMAL, 60, 100, 25))
        )),
        "020" to PokemonGrowthProfile("020", 0, "", listOf(
            LearnableMove(4, BattleFactory.attackHyperFang()),
            LearnableMove(7, BattleFactory.attackCrunch()),
            LearnableMove(15, BattleFactory.attackSuperFang()),
            LearnableMove(25, Move("DOUBLE-EDGE", PokemonType.NORMAL, 120, 100, 15))
        )),

        // SPEAROW LINE (021 -> 022)
        "021" to PokemonGrowthProfile("021", 4, "022", listOf(
            LearnableMove(1, BattleFactory.attackPeck()),
            LearnableMove(1, BattleFactory.attackGrowl()),
            LearnableMove(3, Move("LEER", PokemonType.NORMAL, 0, 100, 30, statEffect = StatEffect.LOWER_ENEMY_DEF))
        )),
        "022" to PokemonGrowthProfile("022", 0, "", listOf(
            LearnableMove(4, BattleFactory.attackFuryAttack()),
            LearnableMove(10, Move("MIRROR MOVE", PokemonType.FLYING, 0, 100, 20)),
            LearnableMove(20, BattleFactory.attackDrillPeck())
        )),

// EKANS LINE (023 -> 024) - Evoluce na lvl 4
        "023" to PokemonGrowthProfile("023", 4, "024", listOf(
            LearnableMove(1, Move("WRAP", PokemonType.NORMAL, 15, 90, 20)),
            LearnableMove(2, BattleFactory.attackPoisonSting()),
            LearnableMove(3, BattleFactory.attackBite())
        )),
        "024" to PokemonGrowthProfile("024", 0, "", listOf(
            LearnableMove(4, Move("CRUNCH", PokemonType.NORMAL, 80, 100, 15)),
            LearnableMove(7, BattleFactory.attackAcid()),
            LearnableMove(15, Move("SCREECH", PokemonType.NORMAL, 0, 85, 40, statEffect = StatEffect.LOWER_ENEMY_DEF)),
            LearnableMove(25, BattleFactory.attackSludgeBomb())
        )),

        "025" to PokemonGrowthProfile(
            pokedexId = "025",
            evolutionLevel = 6, // Přirozená evoluce
            evolutionToId = "026",
            movesLearnedAt = listOf(
                LearnableMove(1, BattleFactory.attackTackle()),
                LearnableMove(6, BattleFactory.attackThunderShock()),
                LearnableMove(11, BattleFactory.attackQuickAttack())
            )
        ),

        "026" to PokemonGrowthProfile(
            pokedexId = "026",
            evolutionLevel = 0,
            evolutionToId = "",
            movesLearnedAt = listOf(
                LearnableMove(1, BattleFactory.attackThunderShock()), // 👈 Naučí se hned při evoluci!
                LearnableMove(8, BattleFactory.attackSlam()),
                LearnableMove(10, BattleFactory.attackThunderbolt()),
                LearnableMove(11, BattleFactory.attackThunder())
            )
        ),

        // --- 🪨 DIGLETT RODINA ---
        "050" to PokemonGrowthProfile(
            pokedexId = "050",
            evolutionLevel = 6, // Evoluce na Dugtria
            evolutionToId = "051",
            movesLearnedAt = listOf(
                LearnableMove(1, BattleFactory.attackScratch()), // Základ
                LearnableMove(1, Move("SAND ATTACK", PokemonType.NORMAL, 0, 100, 15, statEffect = StatEffect.LOWER_ENEMY_ATK)),
                LearnableMove(4, Move("MUD-SLAP", PokemonType.GROUND, 20, 100, 10)), // Zemní útok před evolucí
                LearnableMove(6, Move("MAGNITUDE", PokemonType.GROUND, 50, 100, 30)) // První silnější útok při evoluci
            )
        ),

        "051" to PokemonGrowthProfile(
            pokedexId = "051",
            evolutionLevel = 0, // Finální stádium
            evolutionToId = "",
            movesLearnedAt = listOf(
                LearnableMove(1, Move("MAGNITUDE", PokemonType.GROUND, 50, 100, 30)),
                LearnableMove(8, Move("DIG", PokemonType.GROUND, 80, 100, 10)), // 👈 Klasika pro Dugtria
                LearnableMove(10, Move("SLASH", PokemonType.NORMAL, 70, 100, 20)), // Využije drápy
                LearnableMove(12, Move("EARTHQUAKE", PokemonType.GROUND, 100, 100, 10)) // 👈 Ultimátní zemětřesení
            )
        ),

        "115" to PokemonGrowthProfile(
            pokedexId = "115",
            evolutionLevel = 0,    // ❌ Nevyvíjí se
            evolutionToId = "",    // ❌ Žádné další ID
            movesLearnedAt = listOf(
                LearnableMove(1, Move("TACKLE", PokemonType.NORMAL, 40, 100, 35)),
                LearnableMove(1, Move("TAIL WHIP", PokemonType.NORMAL, 0, 100, 30, statEffect = StatEffect.LOWER_ENEMY_DEF)),
                LearnableMove(10, Move("MEGA PUNCH", PokemonType.NORMAL, 80, 85, 20)) // Naučí se na lvl 10
            )
        )
        // ➕ Sem budeš jednoduše pod sebe dopisovat všechny ostatní Pokémony a jejich křivky!
    )

    fun getProfile(pokedexId: String): PokemonGrowthProfile? {
        return GROWTH_DATABASE[pokedexId]
    }

    // Zjistí, jestli se Pokémon na daném levelu má naučit nový útok
    fun getNewMoveForLevel(pokedexId: String, level: Int): Move? {
        val profile = getProfile(pokedexId) ?: return null
        return profile.movesLearnedAt.find { it.level == level }?.move
    }
}