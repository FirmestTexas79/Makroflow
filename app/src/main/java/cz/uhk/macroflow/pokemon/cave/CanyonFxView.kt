package cz.uhk.macroflow.pokemon.cave

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.SystemClock
import android.view.View

/**
 * Živý Ozvěnový kaňon (docs/adr/0090): kaňonem fouká vítr – prach letí vodorovně v pruzích
 * a z průrvy pod mostem stoupá teplý vzduch (chvějící se světlé pixely).
 */
class CanyonFxView(context: Context, private val scale: Int) : View(context) {
    private val s = scale.toFloat()
    private val paint = Paint()
    private val t0 = SystemClock.uptimeMillis()
    private val W = EchoCanyon.MAP.artW

    override fun onDraw(canvas: Canvas) {
        val t = (SystemClock.uptimeMillis() - t0) / 1000f
        for (i in 0 until 40) {
            val h = (i * 2654435761L).toInt() ushr 1
            val y = 20 + (h / 13) % 340
            val speed = 18f + h % 22
            val x = ((h % W) + t * speed) % (W + 20) - 10
            val len = 1 + h % 3
            paint.color = if (h % 2 == 0) 0x78F0E0C8 else 0x50E8D4B0
            canvas.drawRect(x.toInt() * s, y * s, (x.toInt() + len) * s, (y + 1) * s, paint)
        }
        for (i in 0 until 10) {                                   // teplý vzduch z průrvy
            val life = (t * 0.5f + i / 10f) % 1f
            val x = 20 + (i * 13) % 120 + kotlin.math.sin(t * 3 + i) * 1.5f
            val y = 197 - life * 18
            paint.color = (((1 - life) * 110).toInt() shl 24) or 0xFFD8A0
            canvas.drawRect(x.toInt() * s, y.toInt() * s, (x.toInt() + 1) * s, (y.toInt() + 1) * s, paint)
        }
        postInvalidateOnAnimation()
    }
}
