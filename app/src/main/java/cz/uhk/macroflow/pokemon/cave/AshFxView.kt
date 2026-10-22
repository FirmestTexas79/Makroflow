package cz.uhk.macroflow.pokemon.cave

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.SystemClock
import android.view.View

/**
 * Živá Popelavá pláň (docs/adr/0088): přes mapu se pomalu snáší popel a z ohniště stoupají jiskry.
 * Velikost = celý svět mapy, [scale] = násobek art pixelu; vše se kreslí po celých art pixelech.
 */
class AshFxView(context: Context, private val scale: Int) : View(context) {
    private val s = scale.toFloat()
    private val paint = Paint()
    private val t0 = SystemClock.uptimeMillis()
    private val W = AshPlain.MAP.artW; private val H = AshPlain.MAP.artH

    /** Vločka: start (x, y), rychlost (px/s), velikost, barva. */
    private data class Flake(val x: Float, val y: Float, val vx: Float, val vy: Float, val size: Int, val color: Int)
    private val flakes = List(70) { i ->
        val h = (i * 2654435761L).toInt() ushr 1
        Flake((h % W).toFloat(), ((h / W) % H).toFloat(), 2f + (h % 5), 6f + (h % 7), if (h % 9 == 0) 2 else 1,
            if (h % 3 == 0) 0xC8E6E2DA.toInt() else 0x96CFCAC0.toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val t = (SystemClock.uptimeMillis() - t0) / 1000f
        for (f in flakes) {
            val sway = kotlin.math.sin(t * 0.8f + f.x) * 3f
            val x = ((f.x + f.vx * t + sway) % W + W) % W
            val y = (f.y + f.vy * t) % H
            paint.color = f.color
            canvas.drawRect(x.toInt() * s, y.toInt() * s, (x.toInt() + f.size) * s, (y.toInt() + f.size) * s, paint)
        }
        // jiskry z ohniště (40, 218): stoupají, kmitají a hasnou
        for (i in 0 until 6) {
            val life = ((t * 0.7f + i / 6f) % 1f)
            val x = 40 + kotlin.math.sin(life * 9f + i) * 3f
            val y = 218 - life * 26f
            paint.color = if (life < 0.5f) 0xFFFFC66A.toInt() else ((((1 - life) * 2 * 255).toInt().coerceIn(0, 255) shl 24) or 0xFF8A3A)
            canvas.drawRect(x.toInt() * s, y.toInt() * s, (x.toInt() + 1) * s, (y.toInt() + 1) * s, paint)
        }
        postInvalidateOnAnimation()
    }
}
