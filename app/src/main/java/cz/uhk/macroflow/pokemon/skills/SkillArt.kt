package cz.uhk.macroflow.pokemon.skills

import cz.uhk.macroflow.pokemon.balls.Makroball
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Pixel art dovedností a surovin (ARGB, 0 = průhledné), bez Androidu – kreslí se kódem,
 * aby šlo snadno ladit a testovat rozměry. Náhled: tools/skillart/Preview.kt.
 */
object SkillArt {

    class Px(val w: Int, val h: Int) {
        val data = IntArray(w * h)
        operator fun set(x: Int, y: Int, c: Int) { if (x in 0 until w && y in 0 until h) data[y * w + x] = c }
        operator fun get(x: Int, y: Int): Int = if (x in 0 until w && y in 0 until h) data[y * w + x] else 0
        fun rect(x0: Int, y0: Int, x1: Int, y1: Int, c: Int) { for (y in y0..y1) for (x in x0..x1) this[x, y] = c }
        /** Obrys kolem všeho neprůhledného (4 směry). */
        fun outline(c: Int) {
            val src = data.copyOf()
            for (y in 0 until h) for (x in 0 until w) {
                if (src[y * w + x] != 0) continue
                val n = listOf(x - 1 to y, x + 1 to y, x to y - 1, x to y + 1)
                    .any { (a, b) -> a in 0 until w && b in 0 until h && src[b * w + a] != 0 && src[b * w + a] != c }
                if (n) data[y * w + x] = c
            }
        }
        fun blit(src: IntArray, sw: Int, sh: Int, ox: Int, oy: Int) {
            for (y in 0 until sh) for (x in 0 until sw) { val c = src[y * sw + x]; if (c != 0) this[ox + x, oy + y] = c }
        }
    }

    private fun c(hex: Long) = hex.toInt()

    val OUTLINE = c(0xFF1E140C)
    private val WOOD_L = c(0xFFC48A4A)
    private val WOOD = c(0xFF93602C)
    private val WOOD_D = c(0xFF6C4420)
    private val WOOD_DD = c(0xFF4F3016)
    private val STEEL_L = c(0xFFE3E8EC)
    private val STEEL = c(0xFFA7B1BA)
    private val STEEL_D = c(0xFF6E7880)
    private val LEAF_D = c(0xFF3E5A1E)
    private val LEAF = c(0xFF5F8A2A)
    private val LEAF_L = c(0xFF9CC45A)
    private val SOIL_L = c(0xFF8A5A33)
    private val SOIL = c(0xFF6B4423)
    private val SOIL_D = c(0xFF4A2D16)
    private val GOLD = c(0xFFFFD54F)
    private val WHITE = c(0xFFFDFBF3)

    /** Barvy bobule: světlá, základní, tmavá. */
    fun berryColors(b: Berry): Triple<Int, Int, Int> = when (b) {
        Berry.GREEN -> Triple(c(0xFFE4F0A8), c(0xFFA9BC52), c(0xFF687A2C))
        Berry.BLUE -> Triple(c(0xFF9CD0FF), c(0xFF2E86DE), c(0xFF1B5FA6))
        Berry.BLACK -> Triple(c(0xFF6A6A6A), c(0xFF2B2B2B), c(0xFF121212))
    }

    // ── Ikony dovedností 16 × 16 ─────────────────────────────────────────────

    const val ICON = 16

    fun skillIcon(skill: Skill): IntArray = when (skill) {
        Skill.CATCHING -> catchingIcon()
        Skill.CRAFTING -> craftingIcon()
        Skill.HARVESTING -> harvestingIcon()
        Skill.MINING, Skill.LOGGING -> GearArt.skillIcon(skill)!!
    }

    /** Makroball v letu: čáry pohybu a jiskra. */
    private fun catchingIcon(): IntArray {
        val p = Px(ICON, ICON)
        p.blit(Makroball.MAKRO.pixels, Makroball.SIZE, Makroball.SIZE, 4, 3)
        for ((y, len) in listOf(6 to 3, 9 to 2, 12 to 3)) for (x in 0 until len) p[x, y] = if (x == len - 1) STEEL_L else STEEL
        // jiskra vpravo nahoře
        p[14, 1] = GOLD; p[13, 1] = GOLD; p[15, 1] = GOLD; p[14, 0] = GOLD; p[14, 2] = GOLD; p[14, 1] = WHITE
        return p.data
    }

    /** Kladivo opřené o kovadlinu. */
    private fun craftingIcon(): IntArray {
        val p = Px(ICON, ICON)
        // kovadlina
        p.rect(1, 8, 13, 9, STEEL)            // horní plocha
        p.rect(1, 8, 13, 8, STEEL_L)
        p.rect(0, 8, 0, 8, STEEL)            // roh
        p.rect(4, 10, 10, 11, STEEL_D)       // krk
        p.rect(2, 12, 12, 13, STEEL)         // podstava
        p.rect(2, 13, 12, 13, STEEL_D)
        p[13, 9] = STEEL_D; p[12, 9] = STEEL_D
        // kladivo: násada a hlava
        for (i in 0..5) p[9 - i / 2, 7 - i] = WOOD
        p[9, 7] = WOOD_D; p[8, 5] = WOOD_D
        p.rect(4, 1, 9, 2, STEEL)
        p.rect(4, 1, 9, 1, STEEL_L)
        p[3, 1] = STEEL_D; p[3, 2] = STEEL_D; p[10, 2] = STEEL_D
        // jiskry
        p[12, 5] = GOLD; p[13, 4] = GOLD; p[11, 3] = GOLD
        p.outline(OUTLINE)
        return p.data
    }

    /** Klíček se dvěma lístky v hroudě hlíny. */
    private fun harvestingIcon(): IntArray {
        val p = Px(ICON, ICON)
        // hrouda
        for (y in 11..13) {
            val half = when (y) { 11 -> 4; 12 -> 6; else -> 5 }
            for (x in 7 - half..7 + half) p[x, y] = if (y == 11) SOIL_L else if ((x + y) % 5 == 0) SOIL_D else SOIL
        }
        // stonek
        for (y in 5..10) p[7, y] = if (y % 2 == 0) LEAF_D else LEAF
        // lístky (elipsy)
        fun leaf(cx: Float, cy: Float, dir: Int) {
            for (y in 0 until ICON) for (x in 0 until ICON) {
                val dx = (x - cx) / 3.2f; val dy = (y - cy) / 1.8f
                if (dx * dx + dy * dy <= 1f) p[x, y] = if (dy < -0.2f && dx * dir < 0.3f) LEAF_L else LEAF
            }
        }
        leaf(4f, 4f, -1); leaf(11f, 2.5f, 1)
        p[7, 4] = LEAF_D
        p.outline(OUTLINE)
        return p.data
    }

    // ── Suroviny 12 × 12 ─────────────────────────────────────────────────────

    const val ITEM = 12

    fun resourceIcon(r: Resource): IntArray = when (r) {
        Resource.ENERGY -> energyFragment()
        else -> GearArt.resourceIcon(r) ?: if (r.isSeed) seed(r.berry!!) else berry(r.berry!!)
    }

    /** Zářící úlomek energie. */
    fun energyFragment(): IntArray {
        val p = Px(ITEM, ITEM)
        val core = c(0xFFFFF3B0); val y1 = c(0xFFFFD54F); val y2 = c(0xFFF2A516); val y3 = c(0xFFB86E0A)
        // kosočtverec nakloněný doprava
        for (y in 1..10) for (x in 0 until ITEM) {
            val dx = abs(x - (5.5f + (y - 5.5f) * -0.25f)); val dy = abs(y - 5.5f)
            if (dx / 3.3f + dy / 5.0f <= 1f) {
                p[x, y] = when {
                    x < 5.5f + (y - 5.5f) * -0.25f - 0.5f -> if (dy < 2.5f) core else y1
                    dy > 3.5f -> y3
                    else -> y2
                }
            }
        }
        p.outline(OUTLINE)
        // jiskry
        p[1, 2] = GOLD; p[10, 8] = GOLD; p[10, 1] = WHITE
        return p.data
    }

    /** Trs tří kulatých bobulí s lístkem (černozlatá má zlaté tečky). */
    fun berry(b: Berry): IntArray {
        val p = Px(ITEM, ITEM)
        val (light, base, dark) = berryColors(b)
        // Každá kulička má vlastní obrys, kreslí se odzadu dopředu
        fun ball(cx: Float, cy: Float, r: Float) {
            for (y in 0 until ITEM) for (x in 0 until ITEM) {
                val d = hypot(x - cx, y - cy)
                if (d <= r + 0.9f) p[x, y] = when {
                    d > r -> OUTLINE
                    x - cx < -0.5f && y - cy < -0.5f -> light
                    x - cx + (y - cy) > 1.0f -> dark
                    else -> base
                }
            }
            p[(cx - 1).toInt(), (cy - 1).toInt()] = if (b == Berry.BLACK) c(0xFF9A9A9A) else WHITE
        }
        ball(6f, 3.8f, 2.1f); ball(3.3f, 7.6f, 2.2f); ball(8.4f, 7.6f, 2.2f)
        if (b == Berry.BLACK) { p[3, 8] = GOLD; p[9, 8] = GOLD; p[6, 4] = GOLD; p[8, 6] = GOLD }
        // lístek
        p[7, 0] = LEAF_L; p[8, 0] = LEAF; p[9, 1] = LEAF_D
        p.outline(OUTLINE)
        return p.data
    }

    /** Pytlík semínek s kolečkem barvy bobule. */
    fun seed(b: Berry): IntArray {
        val p = Px(ITEM, ITEM)
        val sack = c(0xFFD9B77E); val sackD = c(0xFFA9824C); val sackL = c(0xFFF0DDB0)
        for (y in 3..10) {
            val half = when (y) { 3 -> 2; 4 -> 3; 10 -> 3; else -> 4 }
            for (x in 5 - half..6 + half) p[x, y] = when {
                x == 5 - half -> sackL
                x == 6 + half || y == 10 -> sackD
                else -> sack
            }
        }
        // šňůrka a cípy
        p[4, 2] = sackD; p[7, 2] = sackD; p[5, 1] = sack; p[6, 1] = sack
        p[4, 3] = WOOD_D; p[5, 3] = WOOD_D; p[6, 3] = WOOD_D; p[7, 3] = WOOD_D
        // štítek s barvou bobule
        val (light, base, _) = berryColors(b)
        p.rect(4, 6, 7, 8, base); p[4, 6] = light
        if (b == Berry.BLACK) p[6, 7] = GOLD
        p.outline(OUTLINE)
        return p.data
    }

    // ── Záhony a rostliny ─────────────────────────────────────────────────────

    const val PLOT_W = 18
    const val PLOT_H = 15

    /** Obdělaný záhon: prkenný rámeček a hlína s brázdami. */
    fun plot(broken: Boolean): IntArray {
        val p = Px(PLOT_W, PLOT_H)
        for (y in 0 until PLOT_H) for (x in 0 until PLOT_W) {
            val border = x == 0 || y == 0 || x == PLOT_W - 1 || y == PLOT_H - 1
            val plank = !border && (x == 1 || y == 1 || x == PLOT_W - 2 || y == PLOT_H - 2)
            p[x, y] = when {
                border -> OUTLINE
                plank -> when {
                    y == 1 -> WOOD_L
                    y == PLOT_H - 2 -> WOOD_D
                    x == 1 -> WOOD
                    else -> WOOD_D
                }
                broken -> if ((x * 7 + y * 3) % 11 == 0) SOIL_L else c(0xFF7D6446)      // vyschlá hlína
                (y - 2) % 3 == 0 -> SOIL_D                                          // brázda
                (y - 2) % 3 == 1 -> SOIL_L
                else -> SOIL
            }
        }
        // zaoblené rohy
        for ((cx, cy) in listOf(0 to 0, PLOT_W - 1 to 0, 0 to PLOT_H - 1, PLOT_W - 1 to PLOT_H - 1)) p[cx, cy] = 0
        if (broken) {
            // rozbitá prkna: mezery
            for (x in 5..7) p[x, 1] = c(0xFF7D6446)
            for (y in 8..10) p[PLOT_W - 2, y] = c(0xFF7D6446)
            p[5, 0] = 0; p[6, 0] = 0; p[PLOT_W - 1, 9] = 0
            // plevel
            for ((x, y) in listOf(3 to 4, 12 to 3, 6 to 10, 14 to 11, 9 to 7)) {
                p[x, y] = LEAF; p[x - 1, y - 1] = LEAF_L; p[x + 1, y - 1] = LEAF_D; p[x, y - 1] = LEAF
            }
            // kameny
            for ((x, y) in listOf(11 to 9, 4 to 12)) { p[x, y] = STEEL; p[x + 1, y] = STEEL_D; p[x, y - 1] = STEEL_L }
            // prasklina
            for ((x, y) in listOf(7 to 4, 8 to 5, 8 to 6, 9 to 6, 10 to 7)) p[x, y] = SOIL_D
        }
        return p.data
    }

    const val PLANT = 12

    /** Rostlina na záhonu: 0 klíček, 1 keřík, 2 keřík s plody (hotovo). */
    fun plant(b: Berry, stage: Int): IntArray {
        val p = Px(PLANT, PLANT)
        when (stage.coerceIn(0, 2)) {
            0 -> {
                for (y in 7..11) p[6, y] = if (y % 2 == 0) LEAF_D else LEAF
                p.rect(3, 6, 5, 7, LEAF); p[3, 6] = LEAF_L; p[4, 6] = LEAF_L
                p.rect(7, 5, 9, 6, LEAF); p[8, 5] = LEAF_L; p[9, 5] = LEAF_L; p[10, 5] = LEAF_D
            }
            else -> {
                for (y in 0 until PLANT) for (x in 0 until PLANT) {
                    val d = hypot((x - 5.5f) / 5f, (y - 6.5f) / 4.6f)
                    if (d <= 1f && y <= 10) p[x, y] = if (y < 5 && x < 6) LEAF_L else if (d > 0.75f && y > 7) LEAF_D else LEAF
                }
                p[5, 11] = WOOD_D; p[6, 11] = WOOD_D
                if (stage == 2) {
                    val (light, base, dark) = berryColors(b)
                    for ((x, y) in listOf(3 to 5, 7 to 4, 5 to 8, 9 to 7, 2 to 8)) {
                        p[x, y] = base; p[x + 1, y] = dark; p[x, y - 1] = light
                        if (b == Berry.BLACK) p[x + 1, y] = GOLD
                    }
                }
            }
        }
        p.outline(OUTLINE)
        return p.data
    }

    // ── Pracovní stůl ────────────────────────────────────────────────────────

    const val TABLE_W = 24
    const val TABLE_H = 18

    /** Dřevěný ponk s kovadlinkou, kladivem, bobulí a úlomkem energie. */
    fun craftingTable(): IntArray {
        val p = Px(TABLE_W, TABLE_H)
        // deska
        p.rect(1, 8, 22, 9, WOOD_L)
        p.rect(1, 10, 22, 11, WOOD)
        p.rect(1, 11, 22, 11, WOOD_D)
        for (x in 4..20 step 6) p[x, 10] = WOOD_D
        // nohy a příčka
        p.rect(2, 12, 3, 17, WOOD_D); p.rect(20, 12, 21, 17, WOOD_D)
        p.rect(3, 12, 3, 17, WOOD_DD); p.rect(21, 12, 21, 17, WOOD_DD)
        p.rect(4, 15, 19, 15, WOOD)
        // kovadlinka
        p.rect(3, 4, 9, 5, STEEL); p.rect(3, 4, 9, 4, STEEL_L); p[2, 4] = STEEL
        p.rect(5, 6, 7, 6, STEEL_D); p.rect(4, 7, 8, 7, STEEL)
        // kladivo ležící na desce
        p.rect(11, 6, 17, 6, WOOD); p.rect(11, 7, 17, 7, WOOD_D)
        p.rect(17, 4, 19, 7, STEEL); p.rect(17, 4, 19, 4, STEEL_L)
        // bobule a úlomek
        val frag = energyFragment()
        for (y in 0 until ITEM) for (x in 0 until ITEM) {
            val col = frag[y * ITEM + x]
            if (col != 0 && x in 2..9 && y in 1..10) p[10 + (x - 2) / 2, (y - 1) / 2 + 2] = col
        }
        p.outline(OUTLINE)
        return p.data
    }

    // ── Přesýpací hodiny pro časovač ─────────────────────────────────────────

    const val GLASS_W = 7
    const val GLASS_H = 9

    fun hourglass(fraction: Float): IntArray {
        val p = Px(GLASS_W, GLASS_H)
        val sand = GOLD
        val glass = c(0xFFE8F4FF)
        p.rect(0, 0, 6, 0, WOOD_D); p.rect(0, 8, 6, 8, WOOD_D)
        for (y in 1..7) {
            val half = when (y) { 1, 7 -> 3; 2, 6 -> 2; 3, 5 -> 1; else -> 0 }
            for (x in 3 - half..3 + half) p[x, y] = glass
        }
        // písek: nahoře ubývá, dole přibývá
        val f = fraction.coerceIn(0f, 1f)
        val topRows = when { f < 0.34f -> listOf(2, 3); f < 0.67f -> listOf(3); else -> emptyList() }
        val bottomRows = when { f < 0.34f -> listOf(7); f < 0.67f -> listOf(6, 7); else -> listOf(5, 6, 7) }
        for (y in topRows + bottomRows) {
            val half = when (y) { 1, 7 -> 3; 2, 6 -> 2; 3, 5 -> 1; else -> 0 }
            for (x in 3 - half..3 + half) p[x, y] = sand
        }
        if (f < 1f) p[3, 4] = sand
        return p.data
    }
}
