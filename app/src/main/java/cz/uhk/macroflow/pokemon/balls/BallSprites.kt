package cz.uhk.macroflow.pokemon.balls

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint

/**
 * Kreslení Makroballů z pixelů [Makroball.pixels]: ikonky (obchod, inventář) a sprite v souboji
 * včetně odklopeného víčka. Jeden zdroj pravdy – žádné obrázky z internetu.
 */
object BallSprites {

    private val iconCache = HashMap<Pair<Any, Int>, Bitmap>()
    private val px = Paint().apply { isAntiAlias = false; style = Paint.Style.FILL }

    /** Ikonka [sizePx] × [sizePx] se zvětšením bez vyhlazení (ostrý pixel art). */
    fun icon(ball: Makroball, sizePx: Int): Bitmap = pixelIcon(ball, ball.pixels, Makroball.SIZE, sizePx)

    /** Libovolný pixel art n × n (léky apod.) jako ostrá ikonka. */
    fun pixelIcon(key: Any, pixels: IntArray, n: Int, sizePx: Int): Bitmap = iconCache.getOrPut(key to sizePx) {
        val src = Bitmap.createBitmap(pixels, n, n, Bitmap.Config.ARGB_8888)
        Bitmap.createScaledBitmap(src, sizePx, sizePx, false)
    }

    /** Pixel art n × n na herní plátno (levý horní roh [x], [y], 1 jednotka = 1 pixel). */
    fun drawPixels(canvas: Canvas, pixels: IntArray, n: Int, x: Float, y: Float) {
        for (yy in 0 until n) for (xx in 0 until n) {
            val c = pixels[yy * n + xx]
            if (c == 0) continue
            px.color = c
            canvas.drawRect(x + xx, y + yy, x + xx + 1, y + yy + 1, px)
        }
    }

    /**
     * Nakreslí ball v souřadnicích herního plátna (1 jednotka = 1 pixel spritu).
     *
     * @param cx střed koule
     * @param cy střed koule
     * @param rotation natočení celé koule (třesení) kolem jejího spodku
     * @param openDeg úhel odklopení víčka (0 = zavřeno) na pantu vlevo
     * @param lift o kolik se víčko nadzvedne
     */
    fun draw(
        canvas: Canvas, ball: Makroball, cx: Float, cy: Float,
        rotation: Float = 0f, openDeg: Float = 0f, lift: Float = 0f
    ) {
        val n = Makroball.SIZE
        val ox = cx - n / 2f
        val oy = cy - n / 2f
        canvas.save()
        canvas.rotate(rotation, cx, oy + n)                      // pivot ve spodku = kolébání
        drawRows(canvas, ball, ox, oy, Makroball.SPLIT_ROW until n)
        canvas.save()
        if (openDeg != 0f || lift != 0f) {
            canvas.translate(0f, -lift)
            canvas.rotate(-openDeg, ox + 1f, oy + Makroball.SPLIT_ROW) // pant na levém okraji švu
        }
        drawRows(canvas, ball, ox, oy, 0 until Makroball.SPLIT_ROW)
        canvas.restore()
        canvas.restore()
    }

    private fun drawRows(canvas: Canvas, ball: Makroball, ox: Float, oy: Float, rows: IntRange) {
        val n = Makroball.SIZE
        val p = ball.pixels
        for (y in rows) for (x in 0 until n) {
            val c = p[y * n + x]
            if (c == 0) continue
            px.color = c
            canvas.drawRect(ox + x, oy + y, ox + x + 1, oy + y + 1, px)
        }
    }
}
