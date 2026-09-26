package cz.uhk.macroflow.pokemon.skills

/**
 * Pixel art materiálů z Makromonů (12 × 12) a legendárních artefaktů z Gudwina (16 × 16),
 * docs/adr/0040. ARGB, 0 = průhledné.
 */
object MaterialArt {
    private fun c(hex: Long) = hex.toInt()
    private val K = SkillArt.OUTLINE

    private val COLORS = mapOf(
        // listy
        'b' to c(0xFF7A4E28), 'B' to c(0xFFC08A4A), 'n' to c(0xFF5A3A1E),
        'g' to c(0xFF2E7A2A), 'G' to c(0xFF62C04A), 'v' to c(0xFF1E5A1E), 'x' to c(0xFFB8F08A),
        // oheň
        'o' to c(0xFFD8541E), 'O' to c(0xFFF2921E), 'Y' to c(0xFFFFD54F), 'W' to c(0xFFFFF6C8),
        // kámen a magma
        's' to c(0xFF4E4A48), 'S' to c(0xFF7C7672), 'L' to c(0xFFA6A09A), 'r' to c(0xFFFF7A2A),
        'm' to c(0xFF2A0E0A), 'M' to c(0xFF6A2016), 'h' to c(0xFF9A3A26), 'R' to c(0xFFF26A1E),
        // perla
        'p' to c(0xFF5E8AB8), 'P' to c(0xFF9EC4E6), 'e' to c(0xFFE8F0F8), 'w' to c(0xFFFFFFFF),
        // dušička
        'u' to c(0xFF8A7AC8), 'U' to c(0xFFC8BEF0), 'q' to c(0xFFF4F0FF), 'k' to c(0xFF3A2A5A),
        // šupina
        'd' to c(0xFF1E6A5A), 'D' to c(0xFF3AAA8A), 'l' to c(0xFF9AF0D0),
        // pixie prach
        'i' to c(0xFFD86A9A), 'I' to c(0xFFF4A6C8), '*' to c(0xFFFFFFFF), '+' to c(0xFFFFE8F4)
    )

    private val ROWS: Map<Resource, List<String>> = mapOf(
        Resource.LEAF_DRY to listOf(
            "............", ".......bbb..", ".....bBBBb..", "....bBBnBb..", "...bBBnBBb..", "..bBBnBBb...",
            "..bBnBBb....", "..bnBBb.....", "..nbbb......", ".n..........", "n...........", "............"),
        Resource.LEAF_LIVING to listOf(
            "..........x.", ".......ggg..", ".....gGGGg..", "....gGGvGg.x", "...gGGvGGg..", "..gGGvGGg...",
            "..gGvGGg....", "..gvGGg.....", "..vggg......", ".v..........", "v...........", "............"),
        Resource.EMBER to listOf(
            "............", ".....o......", ".....oo.....", "....ooo.....", "....oOo.o...", "...oOOoo....",
            "...oOYOo....", "..oOYYYOo...", "..oOYWYOo...", "...oOYOo....", "....ooo.....", "............"),
        Resource.FIRE_STONE to listOf(
            "....o.......", "...oOo..o...", "...oYo.oO...", "..sSSSSsO...", ".sSSLSSSSs..", ".sSLSrSSSs..",
            "sSSSSrrSSSs.", "sSrSSSSrSSs.", "sSSrrSSSSSs.", ".sSSSSSSSs..", "..ssssssss..", "............"),
        Resource.MAGMA_ORB to listOf(
            "....mmmm....", "..mhhRMMm...", ".mhRRMMMMm..", ".mhMMYRMMm..", "mMMMMRMMRMm.", "mMRMMMMRRMm.",
            "mMRRMMMMYMm.", "mMMYRMMRMMm.", ".mMMRRMMMm..", ".mMMMMRMMm..", "..mmMMMmm...", "....mmm....."),
        Resource.WATER_PEARL to listOf(
            "............", "....pppp....", "...pPPPPp...", "..pPewePPp..", "..pPwwePPp..", "..pPeePPPp..",
            "..pPPPPPPp..", "..pPPPPPPp..", "...pPPPPp...", "....pppp....", "............", "............"),
        Resource.SOUL_WISP to listOf(
            "....uuuu....", "...uUUUUu...", "..uUqqUUUu..", "..uUqkUkUu..", "..uUUUUUUu..", "..uUUUUUUu..",
            "...uUUUUUu..", "....uUUUu...", ".....uUUu...", "......uUu...", ".......uu...", "............"),
        Resource.DRAGON_SCALE to listOf(
            ".....dd.....", "....dDDd....", "...dDlDDd...", "...dDlDDd...", "..dDlDDDDd..", "..dDDlDDDd..",
            "..dDDDDDDd..", "..dDdddDDd..", "..dDDDDDDd..", "...dDddDd...", "....dDDd....", ".....dd....."),
        Resource.PIXIE_DUST to listOf(
            "......+.....", ".....+*+....", "......+..+..", "..+.......+.", "............", ".....iii....",
            "...iIIIIi...", "..iII*IIIi..", ".iIIIIII*Ii.", ".iiiiiiiiii.", "............", "............")
    )

    /** Třpytky, které se neobtahují (prach, jiskry). */
    private val GLOW = setOf('+', 'x')

    private fun fromRows(rows: List<String>, size: Int): IntArray {
        val p = SkillArt.Px(size, size)
        val off = (size - rows.size) / 2
        rows.forEachIndexed { y, r -> r.forEachIndexed { x, ch -> if (ch !in GLOW) COLORS[ch]?.let { p[x, y + off] = it } } }
        p.outline(K)
        rows.forEachIndexed { y, r -> r.forEachIndexed { x, ch -> if (ch in GLOW) COLORS[ch]?.let { p[x, y + off] = it } } }
        return p.data
    }

    fun icon(r: Resource): IntArray? = ROWS[r]?.let { fromRows(it, SkillArt.ITEM) }

    // ── Legendární artefakty 16 × 16: zkamenělá kostěná násada se zlatými obručemi,
    //    čepel z nefritového krystalu se zářícími runami a zlatým jádrem ──

    private val ART_COLORS = mapOf(
        'h' to c(0xFF5A5664), 'H' to c(0xFFD2CCBC), 'j' to c(0xFF9A9486),                 // kost / kámen
        'g' to c(0xFF8A5A10), 'G' to c(0xFFE0A91E), 'Y' to c(0xFFFFE27A),                 // zlato
        'T' to c(0xFF2FA898), 'N' to c(0xFF1C6E68), 't' to c(0xFF9FFFE8), 'c' to c(0xFF5FE8FF), // nefrit a runy
        'r' to c(0xFFD84838), 'w' to c(0xFFFFFFFF)
    )

    private val AXE = listOf(
        "................",
        "..........NTTN..",
        ".........NTtTTN.",
        "........NTTcTTTN",
        ".......gNTTTtTTN",
        "......gGGgTcTTTN",
        "......gYrgTTtTTN",
        "......gGGgTTTTN.",
        ".....jHg.NTcTN..",
        "....jHj...NNN...",
        "...jYj..........",
        "..jHj...........",
        ".jHj............",
        "jYj.............",
        "Hj..............",
        "................")

    private val PICKAXE = listOf(
        "................",
        "..NT........TN..",
        "..NTT......TTN..",
        "...NTc....cTN...",
        "....NTTgGgTTN...",
        ".....NgYrYgN....",
        "......gGrGg.....",
        "......jgGgj.....",
        ".....jHj........",
        "....jHj.........",
        "...jYj..........",
        "..jHj...........",
        ".jHj............",
        "jYj.............",
        "Hj..............",
        "................")

    /** Záblesky na hranách čepele (bez obrysu). */
    private val AXE_GLINT = listOf(15 to 4, 13 to 1)
    private val PICK_GLINT = listOf(2 to 1, 13 to 1)

    fun artifact(g: Gear): IntArray? {
        val (rows, glint) = when (g) {
            Gear.MAKRO_AXE -> AXE to AXE_GLINT
            Gear.MAKRO_PICKAXE -> PICKAXE to PICK_GLINT
            else -> return null
        }
        val p = SkillArt.Px(16, 16)
        rows.forEachIndexed { y, r -> r.forEachIndexed { x, ch -> ART_COLORS[ch]?.let { p[x, y] = it } } }
        p.outline(K)
        for ((x, y) in glint) p[x, y] = ART_COLORS.getValue('w')
        return p.data
    }
}
