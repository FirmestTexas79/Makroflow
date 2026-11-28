package cz.uhk.macroflow.pokemon.skills

// VYGENEROVÁNO: tools/skillart/gen_fruit_art.py – needitovat ručně, upravit generátor a spustit znovu.

/**
 * Pixel art Sadařství (region 2): ovoce 12 × 12, ikona dovednosti a česáky 16 × 16, Sadařův set 16 × 16
 * a malé ovoce na stromy v sadu (6 × 7, čtyři stadia růstu). ARGB, 0 = průhledné; obrys jako u ostatních ikon.
 */
object FruitArt {
    private class Grid(val w: Int, val h: Int, val pal: LongArray, val rows: List<String>)

    private const val KEYS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"

    private fun decode(g: Grid, outline: Boolean = true): IntArray {
        val p = SkillArt.Px(g.w, g.h)
        g.rows.forEachIndexed { y, r -> r.forEachIndexed { x, ch -> val i = KEYS.indexOf(ch); if (i >= 0) p[x, y] = g.pal[i].toInt() } }
        if (outline) p.outline(SkillArt.OUTLINE)
        return p.data
    }

    private val APPLE = Grid(12, 12, longArrayOf(0xFF4EA43A, 0xFF2E7A2A, 0xFF3E2612, 0xFF8AD05A, 0xFF6A4422, 0xFFFFD6C0, 0xFFF87E5C, 0xFF5A0E16, 0xFFC8262C, 0xFF961622, 0xFFE8483A, 0xFFDC3032, 0xFFAA2028),
        listOf(
            "............",
            ".........01.",
            ".......203..",
            "......4.1...",
            "...56789....",
            "..66aa897...",
            "..aab8c997..",
            "..a8b9c997..",
            "..98c9c97...",
            "...799977...",
            "....777.....",
            "............"))

    private val PEAR = Grid(12, 12, longArrayOf(0xFF2E7A2A, 0xFF3E2612, 0xFF8AD05A, 0xFF4EA43A, 0xFF6A4422, 0xFFECF28E, 0xFFA8C02C, 0xFF4A5810, 0xFF7A921A, 0xFF9A7A22, 0xFFFFFCDA, 0xFFD0DE52, 0xFFD86A30),
        listOf(
            "............",
            "....0..1....",
            "....234.....",
            "....567.....",
            "....687.....",
            "...888987...",
            "..5ab6689...",
            "..b566887...",
            "..8c68897...",
            "...cc9877...",
            "....777.....",
            "............"))

    private val ORANGE = Grid(12, 12, longArrayOf(0xFF4EA43A, 0xFF2E7A2A, 0xFF8AD05A, 0xFFFFC466, 0xFF6A4422, 0xFF1E5A1E, 0xFFFFECC4, 0xFF7A3006, 0xFFFA9C2C, 0xFFE67614, 0xFFBA520C, 0xFF601600, 0xFFCC5C00),
        listOf(
            "............",
            ".......001..",
            "......102...",
            "....345.....",
            "...637897...",
            "..33889ab...",
            "..8889caa7..",
            "..89c9aaa7..",
            "..99aaaa7...",
            "..77aaaa7...",
            "....777b....",
            "............"))

    private val BANANA = Grid(12, 12, longArrayOf(0xFF6A4422, 0xFF3E2612, 0xFFFFF08E, 0xFFF6DA48, 0xFFB88C16, 0xFFE2BA22, 0xFFFFFCDC, 0xFF8A601A, 0xFF7A5A0E),
        listOf(
            "............",
            "..........0.",
            ".........01.",
            "........231.",
            "........234.",
            ".......2254.",
            "......22354.",
            "....6223554.",
            "..66227544..",
            ".122355448..",
            ".1444888....",
            "............"))

    private val SKILL = Grid(16, 16, longArrayOf(0xFF4EA43A, 0xFF2E7A2A, 0xFF3E2612, 0xFF8AD05A, 0xFF6A4422, 0xFFFFD6C0, 0xFFF87E5C, 0xFFE8483A, 0xFF5A0E16, 0xFFC8262C, 0xFF961622, 0xFFFC5240, 0xFFDC3032, 0xFFAA2028),
        listOf(
            "................",
            "................",
            "..........01....",
            "........203.....",
            ".......4.1......",
            "....567879a.....",
            "...6677799a8....",
            "...67b79c9a8....",
            "..777b99caaa8...",
            "..777c99daaa8...",
            "..999caadaaa8...",
            "...99daadaa8....",
            "...8aaaaaaa8....",
            "....888a888.....",
            ".......8........",
            "................"))

    private val PICKER_OLD = Grid(16, 16, longArrayOf(0xFFC8C0B0, 0xFF8C8478, 0xFF9C5A2E, 0xFF5A524A, 0xFF8A6A40, 0xFFC4A070, 0xFFC8262C, 0xFFF87E5C, 0xFF5A3E22, 0xFFA87A48, 0xFF6C4420),
        listOf(
            "................",
            "..........0.0.0.",
            ".........0121010",
            ".........1.....3",
            ".........2456743",
            ".........1468643",
            "..........14443.",
            "...........333..",
            "..........9a....",
            ".........9a.....",
            "........9a......",
            ".......9a.......",
            "......9a........",
            ".....9a.........",
            "....9a..........",
            "...9a..........."))

    private val PICKER_COPPER = Grid(16, 16, longArrayOf(0xFFF2B07A, 0xFFC8743C, 0xFF7E4220, 0xFF4A7A34, 0xFF7EB05A, 0xFFC8262C, 0xFFF87E5C, 0xFFC48A4A, 0xFF6C4420),
        listOf(
            "................",
            "..........0.0.0.",
            ".........0101010",
            ".........1.....2",
            ".........1345632",
            ".........1356532",
            "..........13332.",
            "...........222..",
            "..........78....",
            ".........78.....",
            "........78......",
            ".......78.......",
            "......78........",
            ".....78.........",
            "....78..........",
            "...78..........."))

    private val PICKER_SILVER = Grid(16, 16, longArrayOf(0xFFF8FBFF, 0xFFC4CCD8, 0xFF78828E, 0xFF4E88A8, 0xFF8EC8E0, 0xFFC8262C, 0xFFF87E5C, 0xFFE4DECE, 0xFF8E8676),
        listOf(
            "................",
            "..........0.0.0.",
            ".........0101010",
            ".........1.....2",
            ".........1345632",
            ".........1356532",
            "..........13332.",
            "...........222..",
            "..........78....",
            ".........78.....",
            "........78......",
            ".......78.......",
            "......78........",
            ".....78.........",
            "....78..........",
            "...78..........."))

    private val PICKER_GOLD = Grid(16, 16, longArrayOf(0xFFFFF2A8, 0xFFE8B42E, 0xFF9A6A10, 0xFF9A263A, 0xFFE85A6A, 0xFFC8262C, 0xFFF87E5C, 0xFFC0643A, 0xFF6E2E18),
        listOf(
            "................",
            "..........0.0.0.",
            ".........0101010",
            ".........1.....2",
            ".........1345632",
            ".........1356532",
            "..........13332.",
            "...........222..",
            "..........78....",
            ".........78.....",
            "........78......",
            ".......78.......",
            "......78........",
            ".....78.........",
            "....78..........",
            "...78..........."))

    private val SET_HAT = Grid(16, 16, longArrayOf(0xFF96722E, 0xFFF0D47A, 0xFFC8A048, 0xFFC8262C, 0xFFF05A48, 0xFFFFE0EA, 0xFFFFD84A),
        listOf(
            "................",
            "................",
            "................",
            "................",
            "......0000......",
            ".....011112.....",
            "....01121112....",
            "....01111212....",
            "....344444435...",
            "....3333333565..",
            "..002111111250..",
            ".01112111211112.",
            "0112111111121112",
            ".02222222222222.",
            "................",
            "................"))

    private val SET_APRON = Grid(16, 16, longArrayOf(0xFFD8C8A0, 0xFF6EB44A, 0xFF3E7E2E, 0xFFD0DE52, 0xFF8EA81E, 0xFFA8946A, 0xFF2A5822),
        listOf(
            "................",
            "................",
            "...0........0...",
            "....0......0....",
            ".....1111112....",
            ".....1341112....",
            ".....1431112....",
            "..0552111112550.",
            "....211111112...",
            "....2111111126..",
            "...21166666112..",
            "...21161116112..",
            "...211666661126.",
            "...211111111126.",
            "...666666666666.",
            "................"))

    private val SET_PANTS = Grid(16, 16, longArrayOf(0xFF8A5E30, 0xFFA68E62, 0xFFD8C49A, 0xFF746040, 0xFFF28C1E, 0xFFB8520C),
        listOf(
            "................",
            "................",
            "................",
            "...0000000000...",
            "...1222222223...",
            "...1222222223...",
            "...1244222223...",
            "...1254221223...",
            "...122221.1223..",
            "...12223..1223..",
            "...12223..12453.",
            "..122223..125423",
            "..12223....12223",
            "..33333....33333",
            "................",
            "................"))

    private val SET_BOOTS = Grid(16, 16, longArrayOf(0xFFF6DA48, 0xFF5EA046, 0xFF346E2C, 0xFF2A2420),
        listOf(
            "................",
            "................",
            "................",
            "................",
            "....0000..0000..",
            "....1112..1112..",
            "....1122..1122..",
            "....1112..1112..",
            "....1122..1122..",
            "...11112.11112..",
            "..111112111112..",
            "..222222222222..",
            "..333333.333333.",
            "................",
            "................",
            "................"))

    private val TINY_APPLE_0 = Grid(6, 7, longArrayOf(0xFFF4B8C8, 0xFFFFF4F6, 0xFFFFD84A),
        listOf(
            "......",
            ".01...",
            ".121..",
            "..1...",
            "......",
            "......",
            "......"))

    private val TINY_APPLE_1 = Grid(6, 7, longArrayOf(0xFF6A4422, 0xFFB8DA6A, 0xFF86B438, 0xFF5A8A24),
        listOf(
            "...0..",
            "..12..",
            "..23..",
            "......",
            "......",
            "......",
            "......"))

    private val TINY_APPLE_2 = Grid(6, 7, longArrayOf(0xFF6A4422, 0xFFF87E5C, 0xFFE8483A, 0xFFC8262C, 0xFF86B438),
        listOf(
            "..0...",
            ".123..",
            ".234..",
            "..4...",
            "......",
            "......",
            "......"))

    private val TINY_APPLE_3 = Grid(6, 7, longArrayOf(0xFF6A4422, 0xFF4EA43A, 0xFFF87E5C, 0xFFE8483A, 0xFFC8262C, 0xFF961622),
        listOf(
            "..01..",
            ".223..",
            "22345.",
            "33455.",
            ".455..",
            "......",
            "......"))

    private val TINY_PEAR_0 = Grid(6, 7, longArrayOf(0xFFF4B8C8, 0xFFFFF4F6, 0xFFFFD84A),
        listOf(
            "......",
            ".01...",
            ".121..",
            "..1...",
            "......",
            "......",
            "......"))

    private val TINY_PEAR_1 = Grid(6, 7, longArrayOf(0xFF6A4422, 0xFFB8DA6A, 0xFF86B438, 0xFF5A8A24),
        listOf(
            "..0...",
            "..1...",
            "..12..",
            "..23..",
            "......",
            "......",
            "......"))

    private val TINY_PEAR_2 = Grid(6, 7, longArrayOf(0xFF6A4422, 0xFFD0DE52, 0xFFECF28E, 0xFFA8C02C, 0xFF86B438),
        listOf(
            "..0...",
            "..1...",
            ".213..",
            ".134..",
            "..4...",
            "......",
            "......"))

    private val TINY_PEAR_3 = Grid(6, 7, longArrayOf(0xFF6A4422, 0xFFECF28E, 0xFFD0DE52, 0xFFA8C02C, 0xFF7A921A),
        listOf(
            "..0...",
            "..1...",
            ".112..",
            ".123..",
            "11234.",
            "22344.",
            ".344.."))

    private val TINY_ORANGE_0 = Grid(6, 7, longArrayOf(0xFFFFFFF0, 0xFFFFD84A),
        listOf(
            "......",
            ".00...",
            ".010..",
            "..0...",
            "......",
            "......",
            "......"))

    private val TINY_ORANGE_1 = Grid(6, 7, longArrayOf(0xFF6A4422, 0xFFB8DA6A, 0xFF86B438, 0xFF5A8A24),
        listOf(
            "...0..",
            "..12..",
            "..23..",
            "......",
            "......",
            "......",
            "......"))

    private val TINY_ORANGE_2 = Grid(6, 7, longArrayOf(0xFF6A4422, 0xFF4EA43A, 0xFFFFC466, 0xFFFA9C2C, 0xFFE67614, 0xFF86B438),
        listOf(
            "..01..",
            ".234..",
            ".345..",
            "..5...",
            "......",
            "......",
            "......"))

    private val TINY_ORANGE_3 = Grid(6, 7, longArrayOf(0xFF6A4422, 0xFF4EA43A, 0xFFFFC466, 0xFFFA9C2C, 0xFFE67614, 0xFFBA520C),
        listOf(
            "..01..",
            ".223..",
            "22344.",
            "32445.",
            ".445..",
            "......",
            "......"))

    private val TINY_BANANA_0 = Grid(6, 7, longArrayOf(0xFF6A4422, 0xFF8A2A5A, 0xFFB84A7A, 0xFF6A1E46),
        listOf(
            "..0...",
            "..1...",
            "..21..",
            "..3...",
            "......",
            "......",
            "......"))

    private val TINY_BANANA_1 = Grid(6, 7, longArrayOf(0xFF6A4422, 0xFF3E2612, 0xFFB8DA6A, 0xFF86B438, 0xFF5A8A24, 0xFF3A5E1A),
        listOf(
            "..01..",
            ".234..",
            ".234..",
            ".55.5.",
            "......",
            "......",
            "......"))

    private val TINY_BANANA_2 = Grid(6, 7, longArrayOf(0xFF6A4422, 0xFF3E2612, 0xFFF6DA48, 0xFFE2BA22, 0xFF86B438, 0xFF5A8A24),
        listOf(
            "..01..",
            ".234..",
            ".234..",
            ".234..",
            ".55.5.",
            "......",
            "......"))

    private val TINY_BANANA_3 = Grid(6, 7, longArrayOf(0xFF6A4422, 0xFF3E2612, 0xFFFFF08E, 0xFFF6DA48, 0xFFE2BA22, 0xFFB88C16),
        listOf(
            "..01..",
            ".234..",
            ".234..",
            ".234..",
            ".234..",
            ".55.5.",
            "......"))

    fun fruit(r: Resource): IntArray? = when (r) {
        Resource.FRUIT_APPLE -> decode(APPLE)
        Resource.FRUIT_PEAR -> decode(PEAR)
        Resource.FRUIT_ORANGE -> decode(ORANGE)
        Resource.FRUIT_BANANA -> decode(BANANA)
        else -> null
    }

    fun skillIcon(): IntArray = decode(SKILL)

    fun picker(g: Gear): IntArray? = when (g) {
        Gear.OLD_PICKER -> decode(PICKER_OLD)
        Gear.COPPER_PICKER -> decode(PICKER_COPPER)
        Gear.SILVER_PICKER -> decode(PICKER_SILVER)
        Gear.GOLD_PICKER -> decode(PICKER_GOLD)
        Gear.ORCHARD_HAT -> decode(SET_HAT)
        Gear.ORCHARD_APRON -> decode(SET_APRON)
        Gear.ORCHARD_PANTS -> decode(SET_PANTS)
        Gear.ORCHARD_BOOTS -> decode(SET_BOOTS)
        else -> null
    }

    fun oldPicker(): IntArray = decode(PICKER_OLD)

    const val TINY_W = 6
    const val TINY_H = 7

    /** Ovoce na stromě: [stage] 0 květ, 1 malé zelené, 2 nalévá se, 3 zralé. */
    fun tiny(r: Resource, stage: Int): IntArray {
        val s = stage.coerceIn(0, 3)
        val g = when (r) {
            Resource.FRUIT_PEAR -> listOf(TINY_PEAR_0, TINY_PEAR_1, TINY_PEAR_2, TINY_PEAR_3)
            Resource.FRUIT_ORANGE -> listOf(TINY_ORANGE_0, TINY_ORANGE_1, TINY_ORANGE_2, TINY_ORANGE_3)
            Resource.FRUIT_BANANA -> listOf(TINY_BANANA_0, TINY_BANANA_1, TINY_BANANA_2, TINY_BANANA_3)
            else -> listOf(TINY_APPLE_0, TINY_APPLE_1, TINY_APPLE_2, TINY_APPLE_3)
        }[s]
        return decode(g, outline = s > 0)
    }
}
