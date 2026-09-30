package cz.uhk.macroflow.pokemon.cave

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import kotlin.math.roundToInt

/**
 * Živé Doly (docs/adr/0049): přes obrázek mapy kreslí tekoucí lávu (vodopád, jezírko, řeku),
 * její žár a mušky poletující u míst chytání. Velikost = celý svět mapy, [scale] = násobek
 * art pixelu. Pixely počítá MinesArt (čistý Kotlin); láva se přepočítává ~14× za sekundu,
 * mušky se hýbou plynule po celých art pixelech.
 */
class MinesFxView(context: Context, private val scale: Int) : View(context) {

    private val s = scale.toFloat()
    private val lavaPx = IntArray(MinesArt.lavaW * MinesArt.lavaH)
    private val lavaBmp = Bitmap.createBitmap(MinesArt.lavaW, MinesArt.lavaH, Bitmap.Config.ARGB_8888)
    private val lavaDst = RectF(
        MinesMap.LAVA_BOUNDS[0] * s, MinesMap.LAVA_BOUNDS[1] * s,
        (MinesMap.LAVA_BOUNDS[2] + 1) * s, (MinesMap.LAVA_BOUNDS[3] + 1) * s
    )
    private val blit = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var lastLava = -1L
    private val t0 = SystemClock.uptimeMillis()

    /** Hejna: druh mušky a střed v art px. */
    private val swarms = listOf(
        MinesArt.Bug.SPARK to (MinesMap.SPARK_X to MinesMap.SPARK_Y),
        MinesArt.Bug.CRYSTAL to (MinesMap.CRYSTAL_X to MinesMap.CRYSTAL_Y),
        MinesArt.Bug.MAGMA to (MinesMap.MAGMA_X to MinesMap.MAGMA_Y)
    )

    /** Předkreslené snímky mušek: [druh][křídla nahoře][svítí][alt]. */
    private val sprites = HashMap<Int, Bitmap>()
    private fun sprite(bug: MinesArt.Bug, up: Boolean, lit: Boolean, alt: Boolean): Bitmap =
        sprites.getOrPut(bug.ordinal * 8 + (if (up) 4 else 0) + (if (lit) 2 else 0) + (if (alt) 1 else 0)) {
            Bitmap.createBitmap(MinesArt.sprite(bug, up, lit, alt), MinesArt.SPRITE_W, MinesArt.SPRITE_H, Bitmap.Config.ARGB_8888)
        }
    private val spriteDst = Rect()

    /** Záře (radiální přechod) předpřipravená pro každou barvu a poloměr. */
    private fun glow(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int, alpha: Float) {
        if (alpha <= 0.01f || r <= 0f) return
        val a = (alpha.coerceIn(0f, 1f) * 255).toInt()
        glowPaint.shader = RadialGradient(cx, cy, r, (a shl 24) or (color and 0xFFFFFF), color and 0xFFFFFF, Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r, glowPaint)
    }

    override fun onDraw(canvas: Canvas) {
        val t = SystemClock.uptimeMillis() - t0
        if (lastLava < 0 || t - lastLava >= 70) {
            MinesArt.lavaFrame(t, lavaPx)
            lavaBmp.setPixels(lavaPx, 0, MinesArt.lavaW, 0, 0, MinesArt.lavaW, MinesArt.lavaH)
            lastLava = t
        }
        // žár nad lávou (pod ní, aby láva zůstala ostrá)
        val pulse = 0.5f + 0.5f * kotlin.math.sin(t / 900f)
        glow(canvas, (MinesMap.FALL_X0 + MinesMap.FALL_X1) / 2f * s, (MinesMap.FALL_Y0 + MinesMap.FALL_Y1) / 2f * s,
            30 * s, 0xFFFF6A1E.toInt(), 0.28f + 0.12f * pulse)
        glow(canvas, MinesMap.POOL_CX * s, MinesMap.POOL_CY * s, 26 * s, 0xFFFF8A2A.toInt(), 0.35f + 0.15f * pulse)
        for (x in MinesMap.RIVER_X0..MinesMap.RIVER_X1 step 20) {
            glow(canvas, (x + 8) * s, (MinesMap.RIVER_Y0 + MinesMap.RIVER_Y1) / 2f * s, 18 * s, 0xFFFF5A1A.toInt(),
                0.22f + 0.1f * kotlin.math.sin(t / 700f + x))
        }
        canvas.drawBitmap(lavaBmp, null, lavaDst, blit)

        // mušky: záře, pak tělíčko po celých art pixelech
        for ((bug, c) in swarms) {
            val (cx, cy) = c
            for (i in 0 until MinesArt.FLIES) {
                val (dx, dy) = MinesArt.flyOffset(bug, i, t)
                val ax = (cx + dx).roundToInt(); val ay = (cy + dy).roundToInt()
                val g = MinesArt.glow(bug, i, t)
                glow(canvas, (ax + 0.5f) * s, (ay + 2f) * s, 5.5f * s, bug.glow, 0.25f + 0.45f * g)
                val bmp = sprite(bug, MinesArt.wingsUp(i, t), MinesArt.lit(bug, i, t), (t / 300 + i) % 2 == 0L)
                val left = ((ax - MinesArt.SPRITE_W / 2) * s).toInt(); val top = (ay * s).toInt()
                spriteDst.set(left, top, left + (MinesArt.SPRITE_W * s).toInt(), top + (MinesArt.SPRITE_H * s).toInt())
                canvas.drawBitmap(bmp, null, spriteDst, blit)
            }
        }
        if (isAttachedToWindow && visibility == VISIBLE) postInvalidateOnAnimation()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        lavaBmp.recycle()
        sprites.values.forEach { it.recycle() }
        sprites.clear()
    }
}
