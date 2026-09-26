package cz.uhk.macroflow.pokemon.skills

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Pixel art vybavení, rud, polen a míst těžby (docs/adr/0035). ARGB, 0 = průhledné.
 * Náhled: tools/skillart/Preview.kt.
 */
object GearArt {
    private fun c(hex: Long) = hex.toInt()
    private val K = SkillArt.OUTLINE
    private val WOOD_L = c(0xFFC48A4A); private val WOOD = c(0xFF93602C); private val WOOD_D = c(0xFF6C4420)
    private val STEEL_L = c(0xFFE3E8EC); private val STEEL = c(0xFFA7B1BA); private val STEEL_D = c(0xFF6E7880)
    private val RUST = c(0xFF9C5A2E)
    private val STONE_L = c(0xFFB9B1A6); private val STONE = c(0xFF8C8378); private val STONE_D = c(0xFF5F574E)

    const val ICON = SkillArt.ICON
    const val ITEM = SkillArt.ITEM

    private fun px(w: Int, h: Int) = SkillArt.Px(w, h)

    /** Tlustá čára (násada). */
    private fun line(p: SkillArt.Px, x0: Int, y0: Int, x1: Int, y1: Int, col: Int, dark: Int) {
        val n = maxOf(abs(x1 - x0), abs(y1 - y0))
        for (i in 0..n) {
            val x = (x0 + (x1 - x0) * i / n.toFloat()).roundToInt(); val y = (y0 + (y1 - y0) * i / n.toFloat()).roundToInt()
            p[x, y] = col; p[x + 1, y] = dark
        }
    }

    // ── Nástroje 16 × 16 ────────────────────────────────────────────────────

    fun axe(rusty: Boolean = true): IntArray {
        val p = px(ICON, ICON)
        line(p, 2, 14, 10, 3, WOOD_L, WOOD_D)
        // hlava: rovná strana u násady, zaoblené ostří vpravo, malý týl vlevo
        val rows = mapOf(1 to 11..13, 2 to 11..14, 3 to 10..15, 4 to 10..15, 5 to 10..15, 6 to 11..14, 7 to 12..13)
        for ((y, xs) in rows) for (x in xs) p[x, y] = when {
            x == xs.last -> STEEL_L                     // ostří
            x <= 11 -> STEEL_D
            rusty && (x + y) % 4 == 0 -> RUST
            else -> STEEL
        }
        p[8, 4] = STEEL_D; p[8, 5] = STEEL_D; p[9, 4] = STEEL; p[9, 5] = STEEL   // týl
        p.outline(K)
        return p.data
    }

    fun pickaxe(rusty: Boolean = true): IntArray {
        val p = px(ICON, ICON)
        line(p, 3, 14, 10, 5, WOOD_L, WOOD_D)
        // zahnutá hlava kolmo na násadu
        val pts = listOf(2 to 5, 3 to 4, 4 to 3, 5 to 2, 6 to 2, 7 to 2, 8 to 2, 9 to 3, 10 to 3, 11 to 4, 12 to 5, 13 to 6, 14 to 8)
        pts.forEachIndexed { i, (x, y) ->
            p[x, y] = if (rusty && i % 4 == 2) RUST else STEEL
            p[x, y + 1] = STEEL_D
            if (i in 3..7) p[x, y - 1] = STEEL_L
        }
        p[1, 6] = STEEL_D; p[14, 9] = STEEL_D
        p.outline(K)
        return p.data
    }

    fun net(): IntArray {
        val p = px(ICON, ICON)
        line(p, 2, 14, 7, 9, WOOD_L, WOOD_D)
        for (y in 0 until ICON) for (x in 0 until ICON) {
            val d = hypot(x - 10.5f, y - 5.5f)
            if (d in 4.2f..5.2f) p[x, y] = STEEL
            else if (d < 4.2f && ((x + y) % 3 == 0 || (x - y + 30) % 3 == 0)) p[x, y] = c(0xFFE8E0C8)
        }
        p.outline(K)
        return p.data
    }

    fun gearIcon(g: Gear): IntArray = when (g) {
        Gear.OLD_AXE -> axe()
        Gear.OLD_PICKAXE -> pickaxe()
        Gear.ADV_CAP -> fromRows(CAP)
        Gear.ADV_TUNIC -> fromRows(TUNIC)
        Gear.ADV_PANTS -> fromRows(PANTS)
        Gear.ADV_SLIPPERS -> fromRows(SLIPPERS)
        Gear.MAKRO_AXE, Gear.MAKRO_PICKAXE -> MaterialArt.artifact(g)!!
    }

    // ── Dobrodruhův set 16 × 16 (docs/adr/0039) ─────────────────────────────

    private val SET_COLORS = mapOf(
        // čepice
        'G' to c(0xFF4E8A3A), 'L' to c(0xFF7CB85A), 'g' to c(0xFF2E5A24), 'R' to c(0xFFD84838), 'r' to c(0xFF8E2A20), 'B' to c(0xFF6B4423), 'D' to c(0xFF3E6B2E),
        // tunika
        'T' to c(0xFF8E6038), 'U' to c(0xFFB8864E), 't' to c(0xFF5E3E22), 'k' to c(0xFF3A2A18), 'Y' to c(0xFFE8C04A),
        // tepláky
        'P' to c(0xFF8A8F98), 'Q' to c(0xFFA8ADB6), 'S' to c(0xFFF0F0F0), 'b' to c(0xFF5A5E66), 'w' to c(0xFFE8E4DA),
        // pantofle
        'H' to c(0xFFC08A5A), 'h' to c(0xFFE0B080), 'F' to c(0xFFF4ECDC), 'f' to c(0xFFD8CCB8), 'O' to c(0xFF5A3A22), 'p' to c(0xFFE87A9A)
    )

    private val CAP = listOf(
        "................",
        "...........RR...",
        "..........RRr...",
        ".........RRr....",
        ".....GGGGRr.....",
        "....GGLLGGGG....",
        "...GGLLGGGGGg...",
        "...GLLGGGGGGg...",
        "..GGGGGGGGGGGg..",
        "..BBBBBBBBBBBB..",
        "..DDDDDDDDDDDDDD",
        "...gggggggggggg.")

    private val TUNIC = listOf(
        "................",
        "....TT....TT....",
        "...TUTT..TTUT...",
        "..TUTTTTTTTTUT..",
        ".TUTTTUTTTTTTTT.",
        ".TTT.TUTTTTT.TTT",
        ".TTT.TUTTTTT.TTt",
        ".ttt.TTTTTTT.ttt",
        ".....kkkYYkkk...",
        ".....TUTTTTT....",
        "....TUTTTTTTT...",
        "....TTTtTTtTT...",
        "...TTTTTTTTTTT..",
        "...tttttttttttt.")

    private val PANTS = listOf(
        "................",
        "....bbbbbbbb....",
        "....bwbbbbwb....",
        "...SQPPPPPPPS...",
        "...SQPPPPPPPS...",
        "...SQPP..PPPS...",
        "...SQPP..PPPS...",
        "...SQPP..PPPS...",
        "...SQPP..PPPS...",
        "...SQPP..PPPS...",
        "...SQPP..PPPS...",
        "...bbbb..bbbb...",
        "...bbbb..bbbb...")

    private val SLIPPERS = listOf(
        "................",
        "..........pp....",
        ".........fFFf...",
        "........FFfFFF..",
        ".......HHHHHHHH.",
        "......HHhHHHHHH.",
        "..FfF.HhHHHHHHHH",
        ".FhhhhhHHHHHHHHH",
        ".HhhhhhHHHHHHHHH",
        ".HHHHHHHHHHHHHHH",
        ".OOOOOOOOOOOOOO.")

    private fun fromRows(rows: List<String>): IntArray {
        val p = px(ICON, ICON)
        val off = (ICON - rows.size) / 2 + 1
        rows.forEachIndexed { y, r -> r.forEachIndexed { x, ch -> SET_COLORS[ch]?.let { p[x, y + off] = it } } }
        p.outline(K)
        return p.data
    }

    fun skillIcon(s: Skill): IntArray? = when (s) {
        Skill.MINING -> pickaxe(rusty = false)
        Skill.LOGGING -> axe(rusty = false)
        else -> null
    }

    // ── Prázdné sloty: světlé siluety jako v IdleOn ─────────────────────────

    private val GHOSTS = mapOf(
        GearSlot.HELMET to listOf(
            "................", "................", ".....######.....", "...##########...", "..############..", "..############..",
            ".##############.", ".######..######.", ".####......####.", ".###........###.", ".###........###.", ".##..........##."),
        GearSlot.CHEST to listOf(
            "................", "...###....###...", "..#####..#####..", ".##############.", ".##############.", ".###.######.###.",
            ".###.######.###.", "..#..######..#..", ".....######.....", ".....######.....", "....########....", "....########....", ".....######....."),
        GearSlot.LEGS to listOf(
            "................", "....########....", "....########....", "....###..###....", "....###..###....", "....###..###....",
            "....###..###....", "...####..####...", "...###....###...", "...###....###...", "...###....###...", "...###....###..."),
        GearSlot.BOOTS to listOf(
            "................", "................", "....#####.......", "....#####.......", "....#####.......", "....#####.......",
            "....#####.......", "....######......", "....#########...", "...###########..", "...###########..", "...###########.."),
        GearSlot.TRINKET to listOf(
            "................", ".......##.......", ".......##.......", "......####......", "..############..", "...##########...",
            "....########....", "....###..###....", "...###....###...", "...##......##..."),
        GearSlot.PENDANT to listOf(
            "................", "..#..........#..", "...#........#...", "....#......#....", ".....#....#.....", "......#..#......",
            ".......##.......", "......####......", ".....######.....", ".....######.....", "......####......", ".......##......."),
        GearSlot.RING_1 to listOf(
            "................", "................", "......###.......", ".....#####......", "......###.......", "....#######.....",
            "...##.....##....", "..##.......##...", "..#.........#...", "..#.........#...", "..##.......##...", "...##.....##....", "....#######....."),
        GearSlot.MYSTERY to listOf(
            "................", "................", ".....######.....", "....##....##....", "....##....##....", "..........##....",
            ".........##.....", "........##......", ".......##.......", ".......##.......", "................", ".......##.......", ".......##.......")
    )

    /** Silueta prázdného slotu (poloprůhledná světlá). */
    fun ghost(slot: GearSlot): IntArray {
        val tint = c(0x5CFFF3DC)
        return when (slot) {
            GearSlot.AXE -> silhouette(axe(), tint)
            GearSlot.PICKAXE -> silhouette(pickaxe(), tint)
            GearSlot.NET -> silhouette(net(), tint)
            else -> {
                val rows = GHOSTS[if (slot == GearSlot.RING_2) GearSlot.RING_1 else slot].orEmpty()
                val p = px(ICON, ICON)
                val off = (ICON - rows.size) / 2
                rows.forEachIndexed { y, r -> r.forEachIndexed { x, ch -> if (ch == '#') p[x, y + off] = tint } }
                p.data
            }
        }
    }

    private fun silhouette(src: IntArray, tint: Int) = IntArray(src.size) { if (src[it] != 0) tint else 0 }

    // ── Rudy a polena 12 × 12 ──────────────────────────────────────────────

    fun oreColors(r: Resource): Triple<Int, Int, Int> = when (r) {
        Resource.ORE_SILVER -> Triple(c(0xFFFFFFFF), c(0xFFD2D8E0), c(0xFF8E98A4))
        Resource.ORE_GOLD -> Triple(c(0xFFFFF3B0), c(0xFFFFC928), c(0xFFB8860B))
        else -> Triple(c(0xFFF2B07A), c(0xFFD9824B), c(0xFF9A4F24))   // měď
    }

    /** Kus horniny s lesklými zrny rudy. */
    fun ore(r: Resource): IntArray {
        val p = px(ITEM, ITEM)
        for (y in 0 until ITEM) for (x in 0 until ITEM) {
            val d = hypot((x - 5.5f) / 5.2f, (y - 6.5f) / 4.4f)
            if (d <= 1f) p[x, y] = if (y < 5 && x < 7) STONE_L else if (d > 0.7f && (x > 6 || y > 8)) STONE_D else STONE
        }
        val (l, b, dk) = oreColors(r)
        for ((x, y) in listOf(3 to 5, 7 to 4, 5 to 8, 8 to 7, 2 to 8)) { p[x, y] = b; p[x + 1, y] = dk; p[x, y - 1] = l }
        p.outline(K)
        return p.data
    }

    fun logColors(r: Resource): Triple<Int, Int, Int> = when (r) {
        Resource.LOG_BIRCH -> Triple(c(0xFFF1EFE6), c(0xFF2B2B2B), c(0xFFEAD7A8))    // kůra, pruhy, řez
        Resource.LOG_MAPLE -> Triple(c(0xFF8A3E22), c(0xFF5E2614), c(0xFFE0A86A))
        else -> Triple(c(0xFF6B4423), c(0xFF4A2D16), c(0xFFD9B77E))                 // dub
    }

    /** Poleno ležící šikmo s letokruhy na čele. */
    fun log(r: Resource): IntArray {
        val p = px(ITEM, ITEM)
        val (bark, stripe, cut) = logColors(r)
        for (i in 0..7) for (w in -2..2) {
            val x = 1 + i + (w + 2) / 3; val y = 9 - i + w
            p[x, y] = if (r == Resource.LOG_BIRCH && (i + w) % 3 == 0) stripe else if (w == -2) stripe else bark
        }
        // čelo polena (elipsa) vpravo nahoře
        for (y in 0 until ITEM) for (x in 0 until ITEM) {
            val d = hypot((x - 8.8f) / 2.6f, (y - 3.2f) / 2.6f)
            if (d <= 1f) p[x, y] = if (d > 0.8f) stripe else if (d in 0.35f..0.55f) WOOD_D else cut
        }
        p.outline(K)
        return p.data
    }

    fun resourceIcon(r: Resource): IntArray? = when (r) {
        Resource.ORE_COPPER, Resource.ORE_SILVER, Resource.ORE_GOLD -> ore(r)
        Resource.LOG_OAK, Resource.LOG_BIRCH, Resource.LOG_MAPLE -> log(r)
        else -> MaterialArt.icon(r)
    }

    // ── Místa na mapě ─────────────────────────────────────────────────────────

    const val ROCK_W = 20
    const val ROCK_H = 16
    const val TREE_W = 20
    const val TREE_H = 28

    /** Balvan s rudnou žílou. */
    fun rock(spot: GatherSpot): IntArray {
        val p = px(ROCK_W, ROCK_H)
        for (y in 0 until ROCK_H) for (x in 0 until ROCK_W) {
            val d = hypot((x - 9.5f) / 9.2f, (y - 9f) / 7f)
            if (d <= 1f && y < ROCK_H - 1) p[x, y] = when {
                y < 6 && x < 10 -> STONE_L
                d > 0.75f && (x > 11 || y > 11) -> STONE_D
                else -> STONE
            }
        }
        // prasklina a žíla
        for ((x, y) in listOf(6 to 5, 7 to 6, 8 to 6, 9 to 7, 10 to 8, 11 to 8)) p[x, y] = STONE_D
        val (l, b, dk) = oreColors(spot.resource)
        for ((x, y) in listOf(4 to 8, 8 to 10, 12 to 6, 14 to 10, 6 to 12, 11 to 12, 15 to 7, 9 to 4)) {
            p[x, y] = b; p[x + 1, y] = dk; p[x, y - 1] = l
        }
        p.outline(K)
        return p.data
    }

    private fun foliage(spot: GatherSpot): Triple<Int, Int, Int> = when (spot) {
        GatherSpot.BIRCH -> Triple(c(0xFFC7E27A), c(0xFF8DBA46), c(0xFF5E8A2A))
        GatherSpot.MAPLE -> Triple(c(0xFFFFB45A), c(0xFFE0662A), c(0xFFA83E1A))
        else -> Triple(c(0xFF7FB04A), c(0xFF4E7F2A), c(0xFF2F5518))                // dub
    }

    /** Strom ke kácení: kmen (bříza bílá s pruhy) a koruna v barvě druhu. */
    fun tree(spot: GatherSpot): IntArray {
        val p = px(TREE_W, TREE_H)
        val (bark, stripe, _) = logColors(spot.resource)
        for (y in 17 until TREE_H - 1) for (x in 8..11) p[x, y] = if (x == 8) stripe else if (spot == GatherSpot.BIRCH && (x + y) % 4 == 0) stripe else bark
        p[7, TREE_H - 2] = bark; p[12, TREE_H - 2] = bark; p[6, TREE_H - 2] = stripe
        val (l, b, d) = foliage(spot)
        fun blob(cx: Float, cy: Float, rx: Float, ry: Float) {
            for (y in 0 until TREE_H) for (x in 0 until TREE_W) {
                val dd = hypot((x - cx) / rx, (y - cy) / ry)
                if (dd <= 1f) p[x, y] = if (y - cy < -ry * 0.3f && x - cx < rx * 0.2f) l else if (dd > 0.72f && y > cy) d else b
            }
        }
        blob(9.5f, 12f, 9.2f, 6f); blob(6f, 8f, 5.5f, 5f); blob(13f, 8f, 5.5f, 5f); blob(9.5f, 5f, 6f, 4.6f)
        p.outline(K)
        return p.data
    }

    fun spot(spot: GatherSpot): Triple<IntArray, Int, Int> =
        if (spot.skill == Skill.MINING) Triple(rock(spot), ROCK_W, ROCK_H) else Triple(tree(spot), TREE_W, TREE_H)
}
