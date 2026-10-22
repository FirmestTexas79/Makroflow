package cz.uhk.macroflow.pokemon.cave

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.SystemClock
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/**
 * Živý Ztichlý sad (docs/adr/0089): světlušky pomalu krouží a blikají, nad jezírkem jich je víc.
 * Velikost = celý svět mapy, [scale] = násobek art pixelu; kreslí se po celých art pixelech.
 */
class OrchardFxView(context: Context, private val scale: Int) : View(context) {
    private val s = scale.toFloat()
    private val paint = Paint()
    private val t0 = SystemClock.uptimeMillis()

    /** Světluška: střed kroužení (art px), poloměr, rychlost a fáze blikání. */
    private data class Fly(val cx: Float, val cy: Float, val r: Float, val speed: Float, val phase: Float)
    private val flies = List(26) { i ->
        val h = (i * 2654435761L).toInt() ushr 1
        val nearPond = i < 8
        Fly(if (nearPond) 20f + h % 32 else 20f + h % 120, if (nearPond) 196f + (h / 7) % 26 else 60f + (h / 7) % 240,
            4f + h % 9, 0.2f + (h % 5) * 0.08f, (h % 100) / 16f)
    }

    override fun onDraw(canvas: Canvas) {
        val t = (SystemClock.uptimeMillis() - t0) / 1000f
        for (f in flies) {
            val x = f.cx + cos(t * f.speed + f.phase) * f.r
            val y = f.cy + sin(t * f.speed * 1.3f + f.phase) * f.r * 0.6f
            val blink = (sin(t * 2.2f + f.phase * 3) + 1) / 2
            if (blink < 0.25f) continue
            val a = (blink * 255).toInt().coerceIn(0, 255)
            paint.color = (a shl 24) or 0xD8FF7A
            canvas.drawRect(x.toInt() * s, y.toInt() * s, (x.toInt() + 1) * s, (y.toInt() + 1) * s, paint)
            if (blink > 0.8f) {                                   // slabá svatozář
                paint.color = ((a / 4) shl 24) or 0xD8FF7A
                canvas.drawRect((x.toInt() - 1) * s, (y.toInt() - 1) * s, (x.toInt() + 2) * s, (y.toInt() + 2) * s, paint)
            }
        }
        postInvalidateOnAnimation()
    }
}
