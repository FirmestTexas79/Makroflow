package cz.uhk.macroflow.pokemon

import android.content.Context
import android.graphics.*

object PokemonSprites {

    var DIGLETT_W = 28;  var DIGLETT_H = 28
    var MEW_W     = 36;  var MEW_H     = 38
    val POKEBALL_W = 7;  val POKEBALL_H = 7

    private var diglettBmp: Bitmap? = null
    private var mewBmp:     Bitmap? = null
    private var initialized = false

    // Veřejný přístup pro overlay (originální rozlišení PNG)
    val diglettBmpPublic: Bitmap? get() = diglettBmp
    val mewBmpPublic:     Bitmap? get() = mewBmp

    val DIGLETT:  IntArray get() = IntArray(0)
    val DIGLETT2: IntArray get() = IntArray(0)
    val MEW:      IntArray get() = IntArray(0)

    // Cílové rozměry v GB pixelech (plátno 160×144)
    private const val DIGLETT_TARGET_W = 28
    private const val DIGLETT_TARGET_H = 28
    private const val MEW_TARGET_W     = 36
    private const val MEW_TARGET_H     = 38

    fun init(context: Context) {
        if (initialized) return
        initialized = true

        val opts = BitmapFactory.Options().apply {
            inScaled = false                        // načti v originálním rozlišení
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        fun load(name: String): Bitmap? {
            val id = context.resources.getIdentifier(name, "drawable", context.packageName)
            return if (id != 0) BitmapFactory.decodeResource(context.resources, id, opts) else null
        }

        // Bitmapa zůstane v originálním rozlišení — škálujeme až při drawDiglett/drawMew
        load("pokemon_diglett")?.let {
            diglettBmp = it
            DIGLETT_W  = DIGLETT_TARGET_W
            DIGLETT_H  = DIGLETT_TARGET_H
        }
        load("pokemon_mew")?.let {
            mewBmp = it
            MEW_W  = MEW_TARGET_W
            MEW_H  = MEW_TARGET_H
        }
    }

    // nearest-neighbor (isFilterBitmap = false) → žádné rozmazání
    private val bmpPaint = Paint().apply {
        isAntiAlias    = false
        isFilterBitmap = false
        isDither       = false
    }

    fun drawDiglett(canvas: Canvas, ox: Int, oy: Int, @Suppress("UNUSED_PARAMETER") paint: Paint) {
        val bmp = diglettBmp ?: return
        val m = Matrix()
        m.setScale(DIGLETT_TARGET_W.toFloat() / bmp.width, DIGLETT_TARGET_H.toFloat() / bmp.height)
        m.postTranslate(ox.toFloat(), oy.toFloat())
        canvas.drawBitmap(bmp, m, bmpPaint)
    }

    fun drawMew(canvas: Canvas, ox: Int, oy: Int, @Suppress("UNUSED_PARAMETER") paint: Paint) {
        val bmp = mewBmp ?: return
        val m = Matrix()
        m.setScale(MEW_TARGET_W.toFloat() / bmp.width, MEW_TARGET_H.toFloat() / bmp.height)
        m.postTranslate(ox.toFloat(), oy.toFloat())
        canvas.drawBitmap(bmp, m, bmpPaint)
    }

    val POKEBALL: IntArray by lazy {
        val K = Color.BLACK; val R = Color.parseColor("#D01818")
        val W = Color.parseColor("#F8F8F8"); val T = 0
        intArrayOf(T,K,K,K,K,K,T, K,R,R,R,R,R,K, K,R,R,R,R,R,K,
            K,K,K,W,K,K,K, K,W,W,W,W,W,K, K,W,W,W,W,W,K, T,K,K,K,K,K,T)
    }

    val POKEBALL_CLOSED: IntArray by lazy {
        val K = Color.BLACK; val D = Color.parseColor("#484848"); val T = 0
        intArrayOf(T,K,K,K,K,K,T, K,D,D,D,D,D,K, K,D,D,D,D,D,K,
            K,K,K,D,K,K,K, K,D,D,D,D,D,K, K,D,D,D,D,D,K, T,K,K,K,K,K,T)
    }

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
        '-' to arrayOf("00000","00000","00000","11111","00000","00000","00000")
    )

    fun drawText(canvas: Canvas, text: String, x: Int, y: Int, color: Int, paint: Paint) {
        paint.color = color
        var cx = x
        for (ch in text.uppercase()) {
            val glyph = FONT[ch] ?: FONT[' ']!!
            for (row in glyph.indices) {
                for (col in 0 until 5) {
                    if (glyph[row][col] == '1') {
                        canvas.drawRect((cx+col).toFloat(),(y+row).toFloat(),
                            (cx+col+1).toFloat(),(y+row+1).toFloat(),paint)
                    }
                }
            }
            cx += 6
        }
    }
}