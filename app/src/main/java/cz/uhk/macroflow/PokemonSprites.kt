package cz.uhk.macroflow.pokemon

import android.graphics.Color

object PokemonSprites {

    // Decode: string řádky → IntArray barev
    // Každý znak = 1 pixel, šířka musí být přesně W
    fun decode(w: Int, raw: String): IntArray {
        val rows = raw.trimIndent().lines().filter { it.isNotEmpty() }
        val out = mutableListOf<Int>()
        for (row in rows) {
            val r = if (row.length < w) row.padEnd(w, '.') else row.substring(0, w)
            for (ch in r) out.add(PAL[ch] ?: Color.TRANSPARENT)
        }
        return out.toIntArray()
    }

    // ── Paleta ──────────────────────────────────────────────────────
    private val PAL = mapOf(
        '.' to Color.TRANSPARENT,
        // Mew
        'K' to 0xFF101010.toInt(), // černá outline
        'B' to 0xFF5878D0.toInt(), // modrá (oko)
        'b' to 0xFF98C0F8.toInt(), // světlá modrá (oko highlight)
        'L' to 0xFFF8B8C0.toInt(), // světle růžová
        'M' to 0xFFE87090.toInt(), // střední růžová
        'D' to 0xFFC03050.toInt(), // tmavá růžová/červená
        // Diglett
        '1' to 0xFF181010.toInt(), // černá outline
        '2' to 0xFF7B4218.toInt(), // tmavě hnědá
        '3' to 0xFFD03030.toInt(), // červená nos
        '4' to 0xFFD4A060.toInt(), // světlá hnědá (tvář)
        '5' to 0xFFA86830.toInt(), // střední hnědá
        '6' to 0xFF786040.toInt(), // tmavší hnědá
        '7' to 0xFF686858.toInt(), // šedohnědá (tělo v zemi)
        '8' to 0xFF383028.toInt(), // velmi tmavá (spodek)
        '9' to 0xFFF0F0F0.toInt(), // bílá (oko highlight)
        // Pokéball
        'R' to 0xFFD01818.toInt(), // červená horní polovina
        'W' to 0xFFF8F8F8.toInt(), // bílá spodní polovina
        'G' to 0xFF808080.toInt(), // šedá středová linie
        'g' to 0xFF404040.toInt()  // tmavší šedá
    )

    // ════════════════════════════════════════════════════════════════
    // MEW — 35×37 px (přesně z gridu)
    // ════════════════════════════════════════════════════════════════
    val MEW_W = 35
    val MEW_H = 37
    val MEW: IntArray by lazy { decode(MEW_W, MEW_RAW) }

    private val MEW_RAW = """
...........................KKKK....
........................KLLLMMMMK..
.......................KLLLMMMMMMK.
..........KK...........KLLLMMMMMK.
..........KLLK........KLLLLMMMMMK.
......KKKKKLLK........KLLLLMMMMMK.
.....KLLLLLLLLK.......KLLLMMMMMK..
....KLLLLLLLLLLK......KLLLLMMMMK..
...KKKKLLLLLLLLLK.....KLLLMMMK....
.KKLLLLLLLLLLLLLLLbK..KLLMMK.....
KLLLLLLLLLLLLDLLLLLLbK.KLLMK.....
KMLLLLLLLLLLLLDLLLLLLbKKLLK......
.KMMMLLLKKKLLLLLLLLLLLLbK........
.KMMMKBbBKLLLLLLLLLLLMMK.........
.KMMLK bbbKLLLLLLLLLLLMK.........
.KMLKbbbKLLLLLLLLLLLMMK..........
.KMMKDDKLLLLLLLLLLLMMMMbKK.......
.KMMMMLLLLLLLLLLLLDKKKLLLKK......
..KKMMLLLDKKKKLLLKKLLLKLLbK......
....KKDMMMMLMMMKLLLMLbKLMbK......
.....KLLLLMMMMMKKMMbK.KLMbK......
.....KKKKLLLLLLLLLLKKKLMMbKK.....
.....KMLLLLLLLLLLLLLLLLMMbK......
....KLLLLLLLLLLLLLLLLMMbKMMK.....
...KLLLLbKLLLLLLLLLLMMbKMKK..KLLbK
..KMMbKKKDMMMMLLLLLLMKDMMbKK.KLLbK
..KKK.KMLLLbLLLLLLMMbKMMbKK..KLLbK
......KLLLLKLLLLLLMMbK........KLLbK
......KLLLLKLLLLLMbKDMMbK.....KLLbK
......KLLLLLLLLMbKDMbK.KK.....KLLbK
.......KLLLLLLbK.KMbK..........KLLbK
........KMMMMMDMMbK............KLLbK
.........KKMMMMDbK.............KLLbK
............KMMbKKK.............KLLLbK
...............KMMMMbKKKKMMMMK..KLbK.
................KMMMMMMMMMMMMbKKKK...
..................KKKKKKKKK..........
""" // 35 chars wide

    // ════════════════════════════════════════════════════════════════
    // DIGLETT — 15×14 px (přesně z gridu)
    // ════════════════════════════════════════════════════════════════
    val DIGLETT_W = 15
    val DIGLETT_H = 14
    val DIGLETT: IntArray by lazy { decode(DIGLETT_W, DIGLETT_RAW) }

    private val DIGLETT_RAW = """
.....111111....
....1244442 1..
...14444444 41.
..1444499444 41
..153444444445 1
..152444444251.
.11225555552211
115755555557511
17766666666 771
17657766677 671
.17655555567 1.
.11866666681 1.
..11888888811..
...11111111 1..
""" // 15 chars wide — pozor: spaces jsou jen pro čitelnost, decode je ignoruje... NE! musíme použít přesné znaky

    // ── DIGLETT přepsaný bez mezer ───────────────────────────────
    // Opravená verze
    val DIGLETT2: IntArray by lazy { decode(15, """
.....111111....
....124444421..
...1444444441..
..14444994441..
..153444444451.
..152444444251.
.1122555555221.
1157555555 7511
17766666666771.
17657766677671.
.1765555556 71.
.118666666811..
..118888881 1..
...111111111...
""") }

    // ════════════════════════════════════════════════════════════════
    // POKÉBALL — 7×7 px
    // ════════════════════════════════════════════════════════════════
    val POKEBALL_W = 7
    val POKEBALL_H = 7
    val POKEBALL: IntArray by lazy { decode(7, """
.KRRRK.
KRRRRRk
KRRRRRk
KKKgKKK
KWWWWWk
KWWWWWk
.KWWWK.
""") }

    val POKEBALL_CLOSED: IntArray by lazy { decode(7, """
.K111K.
K11111K
K11111K
KKKgKKK
K11111K
K11111K
.K111K.
""") }

    // ════════════════════════════════════════════════════════════════
    // GB FONT — 5×7 px pro každý znak (A-Z, 0-9, základní interpunkce)
    // Kreslíme text pixelově přímo na Canvas
    // ════════════════════════════════════════════════════════════════
    // Každý znak = 5×7 pole bitů (1=pixel, 0=prázdno)
    // Uloženo jako List<String> 7 řádků × 5 znaků
    val FONT: Map<Char, Array<String>> = mapOf(
        'A' to arrayOf("01110","10001","10001","11111","10001","10001","10001"),
        'B' to arrayOf("11110","10001","10001","11110","10001","10001","11110"),
        'C' to arrayOf("01110","10001","10000","10000","10000","10001","01110"),
        'D' to arrayOf("11100","10010","10001","10001","10001","10010","11100"),
        'E' to arrayOf("11111","10000","10000","11110","10000","10000","11111"),
        'F' to arrayOf("11111","10000","10000","11110","10000","10000","10000"),
        'G' to arrayOf("01110","10001","10000","10111","10001","10001","01110"),
        'H' to arrayOf("10001","10001","10001","11111","10001","10001","10001"),
        'I' to arrayOf("01110","00100","00100","00100","00100","00100","01110"),
        'J' to arrayOf("00111","00010","00010","00010","10010","10010","01100"),
        'K' to arrayOf("10001","10010","10100","11000","10100","10010","10001"),
        'L' to arrayOf("10000","10000","10000","10000","10000","10000","11111"),
        'M' to arrayOf("10001","11011","10101","10001","10001","10001","10001"),
        'N' to arrayOf("10001","11001","10101","10011","10001","10001","10001"),
        'O' to arrayOf("01110","10001","10001","10001","10001","10001","01110"),
        'P' to arrayOf("11110","10001","10001","11110","10000","10000","10000"),
        'Q' to arrayOf("01110","10001","10001","10001","10101","10010","01101"),
        'R' to arrayOf("11110","10001","10001","11110","10100","10010","10001"),
        'S' to arrayOf("01111","10000","10000","01110","00001","00001","11110"),
        'T' to arrayOf("11111","00100","00100","00100","00100","00100","00100"),
        'U' to arrayOf("10001","10001","10001","10001","10001","10001","01110"),
        'V' to arrayOf("10001","10001","10001","10001","10001","01010","00100"),
        'W' to arrayOf("10001","10001","10001","10101","10101","11011","10001"),
        'X' to arrayOf("10001","10001","01010","00100","01010","10001","10001"),
        'Y' to arrayOf("10001","10001","01010","00100","00100","00100","00100"),
        'Z' to arrayOf("11111","00001","00010","00100","01000","10000","11111"),
        '0' to arrayOf("01110","10011","10011","10101","11001","11001","01110"),
        '1' to arrayOf("00100","01100","00100","00100","00100","00100","01110"),
        '2' to arrayOf("01110","10001","00001","00110","01000","10000","11111"),
        '3' to arrayOf("11110","00001","00001","01110","00001","00001","11110"),
        '4' to arrayOf("00010","00110","01010","10010","11111","00010","00010"),
        '5' to arrayOf("11111","10000","10000","11110","00001","00001","11110"),
        '6' to arrayOf("01110","10000","10000","11110","10001","10001","01110"),
        '7' to arrayOf("11111","00001","00010","00100","01000","01000","01000"),
        '8' to arrayOf("01110","10001","10001","01110","10001","10001","01110"),
        '9' to arrayOf("01110","10001","10001","01111","00001","00001","01110"),
        ':' to arrayOf("00000","00100","00100","00000","00100","00100","00000"),
        '/' to arrayOf("00001","00001","00010","00100","01000","10000","10000"),
        '!' to arrayOf("00100","00100","00100","00100","00100","00000","00100"),
        '?' to arrayOf("01110","10001","00001","00110","00100","00000","00100"),
        ' ' to arrayOf("00000","00000","00000","00000","00000","00000","00000"),
        '.' to arrayOf("00000","00000","00000","00000","00000","00100","00100"),
        '-' to arrayOf("00000","00000","00000","11111","00000","00000","00000"),
        '\'' to arrayOf("00100","00100","00000","00000","00000","00000","00000")
    )

    // Vykreslí text GB fontem na Canvas
    // x,y = pozice v GB pixelech, scale = kolik px = 1 GB pixel
    fun drawText(
        canvas: android.graphics.Canvas,
        text: String,
        x: Int, y: Int,
        color: Int,
        paint: android.graphics.Paint
    ) {
        paint.color = color
        var cx = x
        for (ch in text.uppercase()) {
            val glyph = FONT[ch] ?: FONT[' ']!!
            for (row in glyph.indices) {
                for (col in 0 until 5) {
                    if (glyph[row][col] == '1') {
                        canvas.drawRect(
                            (cx + col).toFloat(), (y + row).toFloat(),
                            (cx + col + 1).toFloat(), (y + row + 1).toFloat(),
                            paint
                        )
                    }
                }
            }
            cx += 6 // 5px glyph + 1px mezera
        }
    }
}