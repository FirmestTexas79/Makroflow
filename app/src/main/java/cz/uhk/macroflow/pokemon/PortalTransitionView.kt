package cz.uhk.macroflow.pokemon

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import androidx.core.content.res.ResourcesCompat
import cz.uhk.macroflow.R
import cz.uhk.macroflow.pokemon.species.SpeciesRegistry
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Krátký přechod do Makrosvěta (docs/adr/0070), stejná scéna jako průvod na webu.
 *  - [cover] (aplikace): pixelové dlaždice se od středu poskládají do noční scény s trávou a keři,
 *    naskočí MAKROSVĚT, keře zašustí a vyskočí z nich makromoni, kteří přeběhnou přes obrazovku
 *    (poskakování, prach od tlapek, lístky z keře),
 *  - [reveal] (mapa): stejná scéna bez makromonů se od středu rozpadne a odhalí svět.
 * Mřížka i scéna se počítají ze šířky obrazovky, takže obě poloviny na sebe navazují.
 */
class PortalTransitionView(context: Context, private val covering: Boolean) : View(context) {

    /** ms od začátku přechodu */
    var time = 0f
        set(v) { step(v - field); field = v; invalidate() }

    // ── Scéna ───────────────────────────────────────────────────────────────
    private val sky = Paint().apply { color = 0xFF131B0D.toInt() }
    private val skyAlt = Paint().apply { color = 0xFF161F0F.toInt() }
    private val ground = Paint().apply { color = 0xFF283618.toInt() }
    private val pixels = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
    private val smooth = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x59000000 }
    private val font = runCatching { ResourcesCompat.getFont(context, R.font.jersey_15) }.getOrNull()
    private val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFEFAE0.toInt(); typeface = font; textAlign = Paint.Align.CENTER }
    private val titleShadow = Paint(title).apply { color = 0xFF0A0F06.toInt() }
    private val accent = Paint().apply { color = 0xFFE9B072.toInt() }
    private val bit = Paint()

    private val grass = decode(R.drawable.portal_grass)
    private val bushL = decode(R.drawable.portal_bush_a)
    private val bushR = decode(R.drawable.portal_bush_b)
    private val tiles = Path()
    private val dst = RectF()

    private class Runner(val id: String, val right: Boolean, val start: Float, val run: Float, val size: Float, val mirror: Boolean) {
        @Volatile var bmp: Bitmap? = null
        var lastDust = 0f
        var burst = false
    }

    private class Bit(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val max: Float, val color: Int, val size: Float, val gravity: Boolean)

    private val runners = mutableListOf<Runner>()
    private val bits = mutableListOf<Bit>()

    init {
        isClickable = true   // během přechodu nic neprokliknout
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        if (covering) {
            val picks = SIDE.shuffled().take(DELAYS.size)
            picks.forEachIndexed { i, id ->
                val right = i % 3 != 1
                runners += Runner(id, right, DELAYS[i], 400f + Random.nextFloat() * 90f, 0.2f + Random.nextFloat() * 0.07f,
                    mirror = if (right) id in FACES_LEFT else id in FACES_RIGHT)
            }
            // spritů je 640 px – dekódovat mimo hlavní vlákno, dlaždice je zatím zakryjí
            Thread {
                runners.forEach { r ->
                    val name = SpeciesRegistry.byId("0${r.id}")?.sprite ?: return@forEach
                    val res = resources.getIdentifier(name, "drawable", context.packageName)
                    if (res != 0) r.bmp = BitmapFactory.decodeResource(resources, res, BitmapFactory.Options().apply { inSampleSize = 2; inScaled = false })
                }
            }.start()
        }
    }

    private fun decode(id: Int) = BitmapFactory.decodeResource(resources, id, BitmapFactory.Options().apply { inScaled = false })

    // ── Kreslení ────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f) return
        val p = if (covering) (time / TILES_MS).coerceIn(0f, 1f) else ((time - HOLD_MS) / REVEAL_MS).coerceIn(0f, 1f)

        // dlaždice jako ořez – uvnitř nich je celá scéna
        tiles.rewind()
        val cell = w / COLS
        val rows = ceil(h / cell).toInt()
        val cx = (COLS - 1) / 2f; val cy = (rows - 1) / 2f
        val maxD = cx + cy
        var full = true
        for (r in 0 until rows) for (c in 0 until COLS) {
            val d = (abs(c - cx) + abs(r - cy)) / maxD
            val t = ((p - d * SPREAD) / (1f - SPREAD)).coerceIn(0f, 1f)
            val size = if (covering) ease(t) else 1f - ease(t)
            if (size < 1f) full = false
            if (size <= 0f) continue
            val half = cell * size / 2f + if (size >= 1f) 0.5f else 0f
            val x = c * cell + cell / 2f; val y = r * cell + cell / 2f
            tiles.addRect(x - half, y - half, x + half, y + half, Path.Direction.CW)
        }
        canvas.save()
        if (!full) canvas.clipPath(tiles)
        drawScene(canvas, w, h, cell, rows)
        canvas.restore()

        if (covering) drawRunners(canvas, w, h)
    }

    private fun drawScene(canvas: Canvas, w: Float, h: Float, cell: Float, rows: Int) {
        // noční nebe s jemnou šachovnicí dlaždic
        canvas.drawRect(0f, 0f, w, h, sky)
        for (r in 0 until rows) for (c in 0 until COLS) if ((r + c) % 2 == 1) canvas.drawRect(c * cell, r * cell, (c + 1) * cell, (r + 1) * cell, skyAlt)

        val px = px(w)
        val top = groundTop(h)
        canvas.drawRect(0f, top + 6 * px, w, h, ground)
        val gw = grass.width * px; val gh = grass.height * px
        var x = 0f
        while (x < w) { dst.set(x, top, x + gw, top + gh); canvas.drawBitmap(grass, null, dst, pixels); x += gw }

        // nápis
        val a = if (covering) ((time - 220f) / 160f).coerceIn(0f, 1f) else 1f - ((time - HOLD_MS) / (REVEAL_MS * 0.4f)).coerceIn(0f, 1f)
        if (a > 0f) {
            val pop = if (covering) 1f + 0.25f * (1f - ease(((time - 220f) / 220f).coerceIn(0f, 1f))) else 1f
            val size = w / 6.4f * pop
            title.textSize = size; titleShadow.textSize = size
            val ty = h * 0.34f
            val sh = max(2f, size / 14f)
            val al = (a * 255).toInt(); title.alpha = al; titleShadow.alpha = al; accent.alpha = al
            canvas.drawText(TITLE, w / 2f + sh, ty + sh, titleShadow)
            canvas.drawText(TITLE, w / 2f, ty, title)
            val lw = title.measureText(TITLE) * 0.55f * a
            canvas.drawRect(w / 2f - lw / 2f, ty + sh * 3, w / 2f + lw / 2f, ty + sh * 5, accent)
        }

        bush(canvas, bushL, w, h, left = true)
        bush(canvas, bushR, w, h, left = false)
    }

    /** Keř šustí, dokud z něj někdo vybíhá (a lehce i mezi tím). */
    private fun bush(canvas: Canvas, bmp: Bitmap, w: Float, h: Float, left: Boolean) {
        val bw = w * BUSH_W; val bh = bw * bmp.height / bmp.width
        val bx = if (left) w * 0.1f else w * 0.9f
        val by = feet(h) + bh * 0.08f
        val busy = covering && runners.any { it.right == left && time in it.start - 140f..it.start + HOP_MS * 0.6f }
        val idle = covering && time > TILES_MS
        val amp = when { busy -> 3.2f; idle -> 0.8f; else -> 0f }
        val rot = amp * sin(time / 28f)
        canvas.save()
        canvas.rotate(rot, bx, by)
        canvas.scale(1f + rot / 120f, 1f - abs(rot) / 160f, bx, by)
        dst.set(bx - bw / 2f, by - bh, bx + bw / 2f, by)
        canvas.drawBitmap(bmp, null, dst, pixels)
        canvas.restore()
    }

    private fun drawRunners(canvas: Canvas, w: Float, h: Float) {
        // za keřem (začátek výskoku) → keř je překryje, proto keře ještě jednou přes ně
        var hidden = false
        runners.forEach { r -> if (phase(r) in 0f..HIDE) { drawRunner(canvas, r, w, h); hidden = true } }
        if (hidden) { bush(canvas, bushL, w, h, true); bush(canvas, bushR, w, h, false) }
        bits.forEach { b ->
            bit.color = b.color; bit.alpha = (255 * (b.life / b.max)).toInt().coerceIn(0, 255)
            canvas.drawRect(b.x - b.size / 2, b.y - b.size / 2, b.x + b.size / 2, b.y + b.size / 2, bit)
        }
        runners.forEach { r -> if (phase(r) > HIDE) drawRunner(canvas, r, w, h) }
    }

    /** 0 … HOP_MS výskok z keře, pak běh; −1 = ještě ne, >1 hotovo (v jednotkách celého běhu). */
    private fun phase(r: Runner): Float = (time - r.start) / (HOP_MS + r.run)

    private fun drawRunner(canvas: Canvas, r: Runner, w: Float, h: Float) {
        val bmp = r.bmp ?: return
        val t = time - r.start
        if (t < 0f || t > HOP_MS + r.run) return
        val s = w * r.size
        val dir = if (r.right) 1f else -1f
        val bushX = if (r.right) w * 0.1f else w * 0.9f
        val land = bushX + dir * w * 0.13f
        val base = feet(h)
        var x: Float; var y: Float; var rot = 0f; var sx = 1f; var sy = 1f
        if (t < HOP_MS) {
            val k = t / HOP_MS
            x = bushX + (land - bushX) * k
            y = base + s * 0.35f * (1f - k) - sin(k * PI.toFloat()) * s * 0.55f
            rot = -dir * 8f * (1f - k)
            sx = 0.94f; sy = 1.06f
        } else {
            val k = ((t - HOP_MS) / r.run).coerceIn(0f, 1f)
            val end = if (r.right) w + s else -s
            x = land + (end - land) * k.pow(1.55f)               // rozbíhá se
            val g = ((t - HOP_MS) % GAIT_MS) / GAIT_MS
            val bob = abs(sin(g * PI.toFloat()))
            y = base - bob * s * 0.16f
            rot = -dir * 5f * sin(g * 2f * PI.toFloat())
            sx = 1.05f - bob * 0.09f; sy = 0.95f + bob * 0.09f
            // stín
            val sw = s * (0.3f - bob * 0.08f)
            dst.set(x - sw, base - s * 0.035f, x + sw, base + s * 0.035f)
            canvas.drawOval(dst, shadowPaint)
        }
        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(rot)
        canvas.scale(sx * if (r.mirror) -1f else 1f, sy)
        dst.set(-s / 2f, -s * SPRITE_FOOT, s / 2f, s * (1f - SPRITE_FOOT))
        canvas.drawBitmap(bmp, null, dst, smooth)
        canvas.restore()
    }

    // ── Částice ─────────────────────────────────────────────────────────────

    private fun step(dt: Float) {
        if (!covering || width == 0 || dt <= 0f) return
        val w = width.toFloat(); val h = height.toFloat(); val px = px(w)
        runners.forEach { r ->
            val t = time + dt - r.start
            val bushX = if (r.right) w * 0.1f else w * 0.9f
            if (!r.burst && t >= 0f) {
                r.burst = true
                repeat(9) {
                    bits += Bit(bushX + (Random.nextFloat() - 0.5f) * w * 0.18f, feet(h) - w * BUSH_W * 0.45f,
                        (Random.nextFloat() - 0.5f) * w * 0.9f, -w * (0.4f + Random.nextFloat() * 0.5f), 620f, 620f,
                        if (Random.nextBoolean()) 0xFF68A03C.toInt() else 0xFF4F7F2A.toInt(), px * 1.6f, gravity = true)
                }
            }
            if (t > HOP_MS && t < HOP_MS + r.run && t - r.lastDust > 55f) {
                r.lastDust = t
                val k = ((t - HOP_MS) / r.run).coerceIn(0f, 1f)
                val land = bushX + (if (r.right) 1f else -1f) * w * 0.13f
                val end = if (r.right) w + w * r.size else -w * r.size
                val x = land + (end - land) * k.pow(1.55f) - (if (r.right) 1f else -1f) * w * r.size * 0.22f
                val back = if (r.right) -1f else 1f
                bits += Bit(x, feet(h) - px * 2, back * w * (0.06f + Random.nextFloat() * 0.06f), -w * (0.03f + Random.nextFloat() * 0.04f),
                    360f, 360f, 0xBFD6C8A0.toInt(), px * 2.2f, gravity = false)
            }
        }
        val sec = dt / 1000f
        bits.forEach { b ->
            b.x += b.vx * sec; b.y += b.vy * sec
            if (b.gravity) b.vy += w * 2.6f * sec
            b.life -= dt
        }
        bits.removeAll { it.life <= 0f }
    }

    private fun px(w: Float) = max(2f, (w / 170f).roundToInt().toFloat())
    private fun groundTop(h: Float) = h * 0.6f
    private fun feet(h: Float) = groundTop(h) + h * 0.045f
    private fun ease(t: Float) = 1f - (1f - t) * (1f - t)

    companion object {
        private const val COLS = 9
        private const val SPREAD = 0.55f
        private const val TITLE = "MAKROSVĚT"
        private const val BUSH_W = 0.34f
        /** kde je ve spritu (640 × 640) pata – podíl výšky od spodu */
        private const val SPRITE_FOOT = 0.94f
        private const val HIDE = 0.1f

        private const val TILES_MS = 360f
        private const val HOP_MS = 180f
        private const val GAIT_MS = 190f
        private val DELAYS = floatArrayOf(330f, 420f, 500f, 590f, 680f)
        private const val COVER_MS = 1300L
        private const val HOLD_MS = 70f
        private const val REVEAL_MS = 480f

        /** Kam sprite kouká (stejné rozdělení jako průvod na webu). */
        private val FACES_LEFT = listOf("01", "02", "03", "07", "10", "22", "23", "32")
        private val FACES_RIGHT = listOf("08", "12", "13", "14", "15", "16", "17", "18", "35")
        private val SIDE = FACES_LEFT + FACES_RIGHT

        /** Překryje [root] a po dokončení zavolá [then]; overlay zůstává, odstraní ho volající. */
        fun cover(root: ViewGroup, then: () -> Unit): PortalTransitionView {
            val v = PortalTransitionView(root.context, covering = true)
            root.addView(v, ViewGroup.LayoutParams(-1, -1))
            run(v, COVER_MS, 0) { then() }
            return v
        }

        /** Odhalí [root] (mapa), overlay se na konci sám odstraní. */
        fun reveal(root: ViewGroup) {
            val v = PortalTransitionView(root.context, covering = false)
            root.addView(v, ViewGroup.LayoutParams(-1, -1))
            v.post { run(v, (HOLD_MS + REVEAL_MS).toLong(), 0) { root.removeView(v) } }
        }

        private fun run(v: PortalTransitionView, ms: Long, delay: Long, end: () -> Unit) {
            ValueAnimator.ofFloat(0f, ms.toFloat()).apply {
                duration = ms; startDelay = delay; interpolator = LinearInterpolator()
                addUpdateListener { v.time = it.animatedValue as Float }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) = end()
                })
            }.start()
        }
    }
}
