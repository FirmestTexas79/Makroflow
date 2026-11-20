package cz.uhk.macroflow.pokemon.story

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import cz.uhk.macroflow.R
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Plánek Věže vedle tlačítek výtahu (docs/adr/0104): cihlová věž z tools/mapgen/gen_tower_lift.py od věžičky
 * přes patra 13 → 0 až do podzemí (−1, −2). Zamčená patra jsou ztmavená (podzemí skoro neviditelné), patro,
 * kde hráč stojí, má zlatou šipku a pulzující rámeček. Klepnutí na schválené patro = jízda tam.
 * Rozměry jsou ZDROJ PRAVDY spolu s generátorem.
 */
class TowerLiftView(
    context: Context,
    private val approved: Set<Int>,
    private val at: Int,
    private val onPick: (Int) -> Unit
) : View(context) {

    companion object {
        const val W = 40
        const val TURRET = 34
        const val FH = 14
        const val GROUND = 4
        private const val X0 = 8
        private const val X1 = 31

        /** Horní řádek patra f v obrázku (stejně v gen_tower_lift.py). */
        fun topOf(f: Int): Int = if (f >= 0) TURRET + (13 - f) * FH else TURRET + 14 * FH + GROUND + (-f - 1) * FH

        fun floorAt(y: Int): Int? = (13 downTo -2).firstOrNull { y in topOf(it) until topOf(it) + FH }
    }

    private val bmp: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.tower_lift_map, BitmapFactory.Options().apply { inScaled = false })
    private val blit = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
    private val fill = Paint()
    /** Celočíselné zvětšení: pixely věže ostré jako na mapách (≈ 3 dp na pixel). */
    private val scale = (3f * resources.displayMetrics.density).roundToInt().coerceAtLeast(2)
    private val dst = Rect()
    private val start = SystemClock.uptimeMillis()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) =
        setMeasuredDimension(bmp.width * scale, bmp.height * scale)

    override fun onDraw(canvas: Canvas) {
        dst.set(0, 0, bmp.width * scale, bmp.height * scale)
        canvas.drawBitmap(bmp, null, dst, blit)
        val t = (SystemClock.uptimeMillis() - start) / 1000f
        for (f in 13 downTo -2) {
            if (f in approved || f == at) continue
            // zamčené patro: tma; podzemí, o kterém hráč neví, skoro splyne s hlínou
            fill.color = if (f < 0) 0xD80C0A08.toInt() else 0x9E0C0A10.toInt()
            canvas.drawRect((X0 - 1f) * scale, topOf(f) * scale.toFloat(), (X1 + 2f) * scale, (topOf(f) + FH) * scale.toFloat(), fill)
        }
        // tady jsi: pulzující zlatý rámeček a šipka, která se pohupuje
        val y0 = topOf(at) * scale.toFloat(); val y1 = (topOf(at) + FH) * scale.toFloat()
        val a = (150 + 90 * sin(t * 4f)).toInt().coerceIn(0, 255)
        fill.color = (a shl 24) or 0xFFD860
        val s = scale.toFloat()
        canvas.drawRect((X0 - 1) * s, y0, (X1 + 2) * s, y0 + s, fill)
        canvas.drawRect((X0 - 1) * s, y1 - s, (X1 + 2) * s, y1, fill)
        canvas.drawRect((X0 - 1) * s, y0, X0 * s, y1, fill)
        canvas.drawRect((X1 + 1) * s, y0, (X1 + 2) * s, y1, fill)
        fill.color = 0xFFFFD860.toInt()
        val bob = (sin(t * 5f) * 1.2f).roundToInt()
        val cy = topOf(at) + FH / 2
        for (i in 0..2) canvas.drawRect((1 + bob + i) * s, (cy - 2 + i) * s, (2 + bob + i) * s, (cy + 3 - i) * s, fill)
        postInvalidateOnAnimation()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action == MotionEvent.ACTION_DOWN) return true
        if (e.action == MotionEvent.ACTION_UP) {
            val f = floorAt((e.y / scale).toInt()) ?: return true
            if (f in approved && f != at) onPick(f)
            return true
        }
        return super.onTouchEvent(e)
    }
}
