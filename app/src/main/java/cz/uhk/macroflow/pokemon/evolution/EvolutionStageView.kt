package cz.uhk.macroflow.pokemon.evolution

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Scéna evoluce (docs/adr/0064), kreslená po pixelech jako přechody mezi lokacemi:
 * noční nebe s pomalu se točícími paprsky, zářící podstavec, starý Makromon se rozzáří,
 * jeho bílá silueta se střídá s obrysem nové formy čím dál rychleji, světlušky se stahují
 * dovnitř, bílý záblesk – a nová forma vyskočí s výbuchem jisker. Pak scéna jen tiše žije,
 * nic se samo nezavře.
 */
@SuppressLint("ViewConstructor")
class EvolutionStageView(ctx: Context) : View(ctx) {

    /** Zavolá se jednou, ve vrcholu záblesku (uložit evoluci, ukázat výsledek). */
    var onReveal: () -> Unit = {}

    var oldSprite: Bitmap? = null
    var newSprite: Bitmap? = null

    private val dp = resources.displayMetrics.density
    private val p = Paint().apply { isAntiAlias = false; isFilterBitmap = false }
    private val soft = Paint(Paint.ANTI_ALIAS_FLAG)
    private val white = Paint().apply { isFilterBitmap = false; colorFilter = PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN) }

    private var start = 0L
    private var revealed = false
    private var running = false

    // časová osa (ms)
    private val introEnd = 1200L
    private val evolveEnd = 6200L
    private val flashPeak = 6550L
    private val flashEnd = 7300L

    private data class Mote(val angle: Float, val radius: Float, val speed: Float, val size: Float, val phase: Float, val gold: Boolean)
    private val motes = List(70) { i ->
        val r = java.util.Random(i * 7919L)
        Mote(r.nextFloat() * 6.283f, 0.35f + r.nextFloat() * 0.65f, 0.4f + r.nextFloat() * 0.9f,
            (2 + r.nextInt(3)).toFloat(), r.nextFloat() * 6.283f, r.nextInt(3) == 0)
    }
    private val stars = List(60) { i ->
        val r = java.util.Random(i * 104729L)
        floatArrayOf(r.nextFloat(), r.nextFloat() * 0.75f, r.nextFloat() * 6.283f, (1 + r.nextInt(2)).toFloat())
    }

    fun begin() {
        start = System.currentTimeMillis()
        revealed = false
        running = true
        invalidate()
    }

    private fun t(): Long = if (start == 0L) 0 else System.currentTimeMillis() - start

    private fun rect(c: Canvas, l: Float, t: Float, r: Float, b: Float, col: Int) { p.color = col; c.drawRect(l, t, r, b, p) }

    override fun onDraw(c: Canvas) {
        val now = t()
        val w = width.toFloat(); val h = height.toFloat()
        val cx = w / 2f
        val cy = h * 0.40f
        val evolveP = ((now - introEnd).toFloat() / (evolveEnd - introEnd)).coerceIn(0f, 1f)
        val afterReveal = now >= flashPeak

        drawSky(c, w, h, now, evolveP, afterReveal)
        drawRays(c, cx, cy, w, h, now, evolveP, afterReveal)
        drawPedestal(c, cx, cy, now, evolveP, afterReveal)
        drawMotes(c, cx, cy, w, now, evolveP, afterReveal)
        drawSprite(c, cx, cy, w, h, now, evolveP, afterReveal)
        drawFlash(c, w, h, now)

        if (!revealed && now >= flashPeak && running) { revealed = true; post { onReveal() } }
        if (isAttachedToWindow && running) postInvalidateOnAnimation()
    }

    /** Pruhový přechod noční oblohy a blikající pixelové hvězdy; po evoluci se nebe zahřeje. */
    private fun drawSky(c: Canvas, w: Float, h: Float, now: Long, e: Float, after: Boolean) {
        val top = if (after) Color.parseColor("#2A1E3A") else Color.parseColor("#0E1022")
        val bottom = if (after) Color.parseColor("#4A2E1E") else Color.parseColor("#1C1430")
        val bands = 18
        for (i in 0 until bands) {
            p.color = mix(top, bottom, i / (bands - 1f))
            c.drawRect(0f, h * i / bands, w, h * (i + 1) / bands + 1, p)
        }
        stars.forEach { s ->
            val a = ((sin(now / 700.0 + s[2]) + 1) / 2 * 170 + 40).toInt()
            val size = s[3] * dp * 1.5f
            rect(c, s[0] * w, s[1] * h, s[0] * w + size, s[1] * h + size, Color.argb(a, 230, 230, 255))
        }
        // ztmavené okraje
        for (k in 0 until 6) {
            p.color = Color.argb(70 - k * 11, 0, 0, 0)
            val o = k * 6 * dp
            c.drawRect(0f, 0f, w, o + 6 * dp, p); c.drawRect(0f, h - o - 6 * dp, w, h, p)
        }
    }

    /** Paprsky světla za Makromonem: během evoluce zesilují a zrychlují, potom zlatě dotáčí. */
    private fun drawRays(c: Canvas, cx: Float, cy: Float, w: Float, h: Float, now: Long, e: Float, after: Boolean) {
        val n = 14
        val reach = max(w, h)
        val rot = (now / (if (after) 9000.0 else 6000.0 - 4000.0 * e)) * 2 * PI
        val alpha = if (after) 46 else (12 + 70 * e).toInt()
        val col = if (after) Color.argb(alpha, 255, 214, 120) else Color.argb(alpha, 200, 220, 255)
        soft.shader = null; soft.color = col
        val path = Path()
        for (i in 0 until n) {
            val a0 = rot + i * 2 * PI / n
            val a1 = a0 + PI / n * 0.55
            path.reset()
            path.moveTo(cx, cy)
            path.lineTo(cx + (cos(a0) * reach).toFloat(), cy + (sin(a0) * reach).toFloat())
            path.lineTo(cx + (cos(a1) * reach).toFloat(), cy + (sin(a1) * reach).toFloat())
            path.close()
            c.drawPath(path, soft)
        }
    }

    /** Kruhový pixelový podstavec s runovým okrajem, který se při evoluci rozsvítí. */
    private fun drawPedestal(c: Canvas, cx: Float, cy: Float, now: Long, e: Float, after: Boolean) {
        val u = 4 * dp
        val rw = 34; val rh = 9                       // v art pixelech
        val baseY = cy + min(width * 0.27f, 150 * dp)
        val glow = if (after) 1f else e
        for (y in -rh..rh) for (x in -rw..rw) {
            val d = (x * x) / (rw * rw).toFloat() + (y * y) / (rh * rh).toFloat()
            if (d > 1f) continue
            val rim = d > 0.72f
            val col = when {
                rim -> mix(Color.parseColor("#3A2E52"), Color.parseColor("#FFE9A0"), glow * (0.6f + 0.4f * sin(now / 180.0 + x * 0.4).toFloat()))
                y < 0 -> mix(Color.parseColor("#2A2440"), Color.parseColor("#5A4A80"), glow)
                else -> Color.parseColor("#1A1528")
            }
            rect(c, cx + x * u, baseY + y * u, cx + (x + 1) * u, baseY + (y + 1) * u, col)
        }
        // runy na okraji
        for (k in 0 until 8) {
            val a = k * 2 * PI / 8 + now / 2400.0
            val x = cx + (cos(a) * rw * u * 0.86f).toFloat(); val y = baseY + (sin(a) * rh * u * 0.86f).toFloat()
            rect(c, x - u, y - u / 2, x + u, y + u / 2, Color.argb((90 + 160 * glow).toInt(), 255, 240, 190))
        }
    }

    /** Světlušky: kolem dokola, během evoluce se stahují dovnitř, po odhalení vybuchnou ven. */
    private fun drawMotes(c: Canvas, cx: Float, cy: Float, w: Float, now: Long, e: Float, after: Boolean) {
        val maxR = w * 0.62f
        val burst = if (after) ((now - flashPeak) / 1100f).coerceIn(0f, 1f) else 0f
        motes.forEach { m ->
            val ang = m.angle + now / 1000f * m.speed * (1 + 3 * e)
            val r = when {
                after -> maxR * (0.15f + 1.2f * easeOut(burst) * m.radius)
                else -> maxR * m.radius * (1f - 0.85f * e)
            }
            val x = cx + cos(ang) * r; val y = cy + sin(ang) * r * 0.8f
            val a = if (after) ((1f - burst) * 255).toInt() else (140 + 115 * sin(now / 250.0 + m.phase)).toInt().coerceIn(0, 255)
            val col = if (m.gold || after) Color.argb(a, 255, 222, 120) else Color.argb(a, 190, 230, 255)
            val s = m.size * dp * (if (after) 1.6f else 1f)
            rect(c, x - s, y - s, x + s, y + s, col)
        }
        // po odhalení: blikající hvězdičky kolem nové formy
        if (after) for (k in 0 until 10) {
            val ang = k * 2 * PI / 10 + now / 3000.0
            val r = w * 0.30f + (sin(now / 400.0 + k) * 8 * dp).toFloat()
            val x = cx + (cos(ang) * r).toFloat(); val y = cy + (sin(ang) * r * 0.75f).toFloat()
            val a = ((sin(now / 220.0 + k * 1.7) + 1) / 2 * 255).toInt()
            star(c, x, y, 3 * dp, Color.argb(a, 255, 245, 200))
        }
    }

    private fun star(c: Canvas, x: Float, y: Float, s: Float, col: Int) {
        rect(c, x - s * 2, y - s / 2, x + s * 2, y + s / 2, col)
        rect(c, x - s / 2, y - s * 2, x + s / 2, y + s * 2, col)
    }

    /** Makromon: houpání, záře, střídání bílých silhuet a nakonec nová forma s odskokem. */
    private fun drawSprite(c: Canvas, cx: Float, cy: Float, w: Float, h: Float, now: Long, e: Float, after: Boolean) {
        val size = min(w * 0.58f, 300 * dp)
        // záře za Makromonem
        val glowR = size * (0.45f + (if (after) 0.25f else 0.5f * e))
        val glowA = if (after) 120 else (40 + 180 * e).toInt()
        soft.shader = RadialGradient(cx, cy, glowR, intArrayOf(Color.argb(glowA, 255, 250, 230), Color.argb(0, 255, 250, 230)), null, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, glowR, soft)
        soft.shader = null

        val bob = (sin(now / 420.0) * 5 * dp).toFloat()
        if (!after) {
            val old = oldSprite ?: return
            if (now < introEnd) {
                drawBmp(c, old, cx, cy + bob, size, 1f, null)
                return
            }
            // střídání starého a nového obrysu: perioda z 560 ms na 45 ms
            val period = 560f - 515f * e * e
            val local = (now - introEnd) % 100000
            val showNew = ((local / period).toInt() % 2 == 1) && newSprite != null
            val bmp = if (showNew) newSprite!! else old
            val pulse = 1f + 0.06f * sin(now / 90.0).toFloat() * e
            // nejdřív barevný sprite pomalu přechází do bílé, pak už jen silueta
            if (e < 0.18f) {
                drawBmp(c, old, cx, cy + bob, size, pulse, null)
                white.alpha = (e / 0.18f * 255).toInt().coerceIn(0, 255)
                drawBmp(c, old, cx, cy + bob, size, pulse, white)
                white.alpha = 255
            } else drawBmp(c, bmp, cx, cy + bob * (1 - e), size, pulse, white)
        } else {
            val nb = newSprite ?: oldSprite ?: return
            val k = ((now - flashPeak) / 700f).coerceIn(0f, 1f)
            val scale = overshoot(k)
            drawBmp(c, nb, cx, cy + bob, size, scale, null)
            // bílý dosvit, který z nové formy během chvilky odezní
            if (k < 1f) { white.alpha = ((1f - k) * 255).toInt(); drawBmp(c, nb, cx, cy + bob, size, scale, white); white.alpha = 255 }
        }
    }

    private fun drawBmp(c: Canvas, b: Bitmap, cx: Float, cy: Float, size: Float, scale: Float, paint: Paint?) {
        val s = size * scale
        val ratio = b.height.toFloat() / b.width
        val bw = if (ratio <= 1f) s else s / ratio; val bh = bw * ratio
        c.drawBitmap(b, null, RectF(cx - bw / 2, cy - bh / 2, cx + bw / 2, cy + bh / 2), paint ?: p)
    }

    private fun drawFlash(c: Canvas, w: Float, h: Float, now: Long) {
        val a = when {
            now < evolveEnd -> 0f
            now < flashPeak -> (now - evolveEnd).toFloat() / (flashPeak - evolveEnd)
            now < flashEnd -> 1f - (now - flashPeak).toFloat() / (flashEnd - flashPeak)
            else -> 0f
        }
        if (a > 0f) { p.color = Color.argb((a * 255).toInt(), 255, 255, 255); c.drawRect(0f, 0f, w, h, p) }
    }

    private fun easeOut(x: Float) = 1f - (1f - x) * (1f - x)
    private fun overshoot(x: Float): Float { val s = 2.2f; val y = x - 1f; return y * y * ((s + 1) * y + s) + 1f }

    private fun mix(a: Int, b: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        return Color.rgb((Color.red(a) + (Color.red(b) - Color.red(a)) * k).toInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * k).toInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * k).toInt())
    }

    override fun onDetachedFromWindow() { running = false; super.onDetachedFromWindow() }
}
