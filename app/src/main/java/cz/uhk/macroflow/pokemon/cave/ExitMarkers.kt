package cz.uhk.macroflow.pokemon.cave

/**
 * Šipky východů ve všech lokacích (jeskyně i grafové mapy Města, Louky a Hor): u každého přechodu do jiné lokace se vznáší malá zlatá pixelová
 * šipka natočená tam, kam se tudy jde, pohupuje se o 2 px ve směru šipky a jde na ni klepnout. U okraje mapy nesedí
 * přímo na uzlu (schová ji lišta nebo spodní okraj), ale kus dovnitř. Zamčený přechod má šedou šipku.
 */
object ExitMarkers {
    enum class Dir { UP, DOWN, LEFT, RIGHT }

    /** Přechod: uzel na mapě, název cíle (popis pro čtečku) a kam se tudy jde – tam šipka míří. */
    data class Exit(val node: String, val label: String, val dir: Dir)

    private val U = Dir.UP
    private val D = Dir.DOWN
    private val R = Dir.RIGHT
    private val L = Dir.LEFT

    /** Patra Věže: jediný východ je výtah (vzadu nahoře). */
    private val FLOORS = mapOf(
        "TOWER_ARCHIVE" to TowerArchive.ELEVATOR_NODE, "TOWER_MEASURE" to TowerMeasure.ELEVATOR_NODE, "TOWER_PLAIN" to TowerPlain.ELEVATOR_NODE,
        "TOWER_FOREST" to TowerForest.ELEVATOR_NODE, "TOWER_ROCK" to TowerRock.ELEVATOR_NODE, "TOWER_CAVE" to TowerCave.ELEVATOR_NODE,
        "TOWER_WATER" to TowerWater.ELEVATOR_NODE, "TOWER_DORM" to TowerDorm.ELEVATOR_NODE, "TOWER_ICE" to TowerIce.ELEVATOR_NODE,
        "TOWER_FIRE" to TowerFire.ELEVATOR_NODE, "TOWER_OFFICE" to TowerOffice.ELEVATOR_NODE, "TOWER_FAIRY" to TowerFairy.ELEVATOR_NODE,
        "TOWER_POISON" to TowerPoison.ELEVATOR_NODE, "TOWER_HIDEOUT" to TowerHideout.ELEVATOR_NODE
    ).mapValues { (_, n) -> listOf(Exit(n, "Výtah", U)) }

    val EXITS: Map<String, List<Exit>> = FLOORS + mapOf(
        // zóna 1 (grafové mapy na celou obrazovku)
        "TOWN" to listOf(Exit("les", "Louka", U)),
        "MEADOW" to listOf(Exit("vstup_z_town", "Město", D), Exit("hory", "Hory", R), Exit("les_sever", "Hvozd", U)),
        "SMITHY" to listOf(Exit(Smithy.ENTRY_NODE, "Hory", D)),
        "MOUNTAINS" to listOf(Exit("vstup_z_meadow", "Louka", D), Exit("mine", "Starý důl", L), Exit("cave", "Jeskyně", R),
            Exit("peak", "Svatyně", U), Exit(Smithy.DOOR_NODE, "Kovárna", U)),
        "CAVE_OPEN" to listOf(Exit("vychod_jeskyne", "Hory", D)),
        "CAVE_MAZE" to listOf(Exit("vychod_dolu", "Hory", D), Exit(MinesMap.MAZE_NODE, "Doly", L)),
        "MINES" to listOf(Exit(MinesMap.EXIT_NODE, "Starý důl", D)),
        "FOREST" to listOf(Exit("vstup_z_louky", "Louka", D)),
        "HIDDEN_GROVE" to listOf(Exit(cz.uhk.macroflow.pokemon.story.SecretGrove.EXIT_NODE, "Hvozd", D)),
        "SKY_PASS" to listOf(Exit("vstup_ze_svatyne", "Svatyně", D), Exit(SkyPass.GATE_NODE, "Brána světů", U)),
        // zóna 2
        "ASH_PLAIN" to listOf(Exit(AshPlain.ENTRY_NODE, "Brána světů", U), Exit(QuietOrchard.PLAIN_NODE, "Ztichlý sad", L),
            Exit(EchoCanyon.PLAIN_NODE, "Ozvěnový kaňon", R), Exit(AshPlain.CITY_GATE_NODE, "Ozvěna", D)),
        "QUIET_ORCHARD" to listOf(Exit(QuietOrchard.ENTRY_NODE, "Popelavá pláň", R)),
        "ECHO_CANYON" to listOf(Exit(EchoCanyon.ENTRY_NODE, "Popelavá pláň", L)),
        "OZVENA" to listOf(Exit(OzvenaCity.ENTRY_NODE, "Popelavá pláň", U), Exit(OzvenaCity.ARENA_NODE, "Aréna", U),
            Exit(OzvenaCity.TOWER_NODE, "Věž Řádu Váhy", U), Exit(OzvenaCity.LIFT_NODE, "Výtah nad propastí", D)),
        "ARENA_HALL" to listOf(Exit(ArenaHall.ENTRY_NODE, "Ozvěna", D)),
        "TOWER_LOBBY" to listOf(Exit(TowerLobby.ENTRY_NODE, "Ozvěna", D), Exit(TowerLobby.ELEVATOR_NODE, "Výtah", U)),
        // Věž −2 a zóna 3
        "TOWER_ABYSS" to listOf(Exit(TowerAbyss.ELEVATOR_NODE, "Výtah", U), Exit(TowerAbyss.CAGE_NODE, "Stanice 8", D),
            Exit(TowerAbyss.LIFT_NODE, "Výtah do Ozvěny", U)),
        "ROOT_CAMP" to listOf(Exit(RootCamp.CAGE_NODE, "Věž −2", U), Exit(RootCamp.DOWN_NODE, "Pozorovatelna B", D)),
        "ROOT_WATCH" to listOf(Exit(RootWatch.ENTRY_NODE, "Stanice 8", U), Exit(RootWatch.RAVINE_NODE, "Signální útes", R),
            Exit(RootWatch.DOWN_NODE, "Tábor na svahu", D)),
        "ROOT_SIGNAL" to listOf(Exit(RootSignal.ENTRY_NODE, "Pozorovatelna B", D), Exit("su_lanovka", "Gondola – Kořenový most", R)),
        "ROOT_BRIDGE" to listOf(Exit(RootBridge.ENTRY_NODE, "Gondola – Signální útes", D)),
        "ROOT_SLOPECAMP" to listOf(Exit(RootSlopeCamp.ENTRY_NODE, "Pozorovatelna B", U), Exit(RootSlopeCamp.DOWN_NODE, "Svah bezejmenných", D)),
        "ROOT_NAMELESS" to listOf(Exit(RootNameless.ENTRY_NODE, "Tábor na svahu", U), Exit("sb_chram", "Chrám Lumenců", D),
            Exit("sb_les", "Les pod korunou", D)),
        "ROOT_TEMPLE" to listOf(Exit(RootTemple.ENTRY_NODE, "Svah bezejmenných", D)),
        "ROOT_FOREST" to listOf(Exit(RootForest.ENTRY_NODE, "Svah bezejmenných", U), Exit(RootForest.THRESHOLD_NODE, "Práh", D)),
        "ROOT_THRESHOLD" to listOf(Exit(RootThreshold.ENTRY_NODE, "Les pod korunou", D), Exit(RootThreshold.BRANCH_NODE, "Kmen", U)),
        "ROOT_TRUNK" to listOf(Exit(RootTrunk.ENTRY_NODE, "Práh", D), Exit(RootTrunk.CRACK_NODE, "Střed kořenů", D)),
        "ROOT_HEART" to listOf(Exit(RootHeart.ENTRY_NODE, "Kmen", U))
    )

    /**
     * Střed šipky v art px. U okraje mapy kus dovnitř (ať ji nezakryje lišta ani spodní okraj obrazovky), uvnitř mapy
     * těsně před uzlem ve směru cesty (šipka nad schody vzhůru, vedle gondoly vpravo…).
     */
    fun anchor(x: Int, y: Int, d: Dir, artW: Int, artH: Int): Pair<Int, Int> = when (d) {
        Dir.UP -> x to (if (y < artH * 0.15f) maxOf(y + 22, 34) else y - 22)
        Dir.DOWN -> x to (if (y > artH * 0.85f) minOf(y - 22, artH - 40) else y - 22)
        Dir.LEFT -> (if (x < artW * 0.15f) maxOf(x + 18, 12) else x - 16) to y - 10
        Dir.RIGHT -> (if (x > artW * 0.85f) minOf(x - 18, artW - 12) else x + 16) to y - 10
    }

    const val SIZE = 9

    private val ARROW_DOWN = listOf(
        "..ooooo..",
        "..oyyyo..",
        "..oyYyo..",
        "oooyYyooo",
        "oyyyYyyyo",
        ".oyyYyyo.",
        "..oyYyo..",
        "...oyo...",
        "....o...."
    )

    /** Šipka dolů (9 × 9, ARGB): zlatá, zamčená šedá; tmavý obrys, světlý hřbet. */
    fun arrowPixels(d: Dir, locked: Boolean): IntArray {
        val fill = if (locked) 0xFF8A8A90.toInt() else 0xFFF2C94C.toInt()
        val hi = if (locked) 0xFFC8C8CE.toInt() else 0xFFFFF4C0.toInt()
        val line = 0xFF1A1410.toInt()
        val out = IntArray(SIZE * SIZE)
        for (y in 0 until SIZE) for (x in 0 until SIZE) {
            val (sx, sy) = when (d) {                                       // otočení předlohy (míří dolů)
                Dir.DOWN -> x to y
                Dir.UP -> x to SIZE - 1 - y
                Dir.RIGHT -> y to x
                Dir.LEFT -> y to SIZE - 1 - x
            }
            out[y * SIZE + x] = when (ARROW_DOWN[sy][sx]) {
                'o' -> line
                'y' -> fill
                'Y' -> hi
                else -> 0
            }
        }
        return out
    }
}
