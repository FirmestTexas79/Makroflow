package cz.uhk.macroflow.pokemon.forge

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import androidx.core.content.res.ResourcesCompat
import cz.uhk.macroflow.R
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** Grafika Výhně z tools/mapgen/gen_forge.py (pixel art, kreslí se bez vyhlazení). */
object ForgeArt {
    const val ICON = 20
    private var icons: List<Bitmap>? = null
    private var furnace: Bitmap? = null
    private var fire: List<Bitmap>? = null
    private var bg: Bitmap? = null

    private fun decode(ctx: Context, id: Int) = BitmapFactory.decodeResource(ctx.resources, id, BitmapFactory.Options().apply { inScaled = false })

    fun icons(ctx: Context): List<Bitmap> = icons ?: decode(ctx, R.drawable.forge_icons).let { a ->
        (0 until a.width / ICON).map { Bitmap.createBitmap(a, it * ICON, 0, ICON, ICON) }
    }.also { icons = it }

    fun icon(ctx: Context, i: Int) = icons(ctx)[i]
    fun furnace(ctx: Context) = furnace ?: decode(ctx, R.drawable.forge_furnace).also { furnace = it }
    fun fire(ctx: Context): List<Bitmap> = fire ?: decode(ctx, R.drawable.forge_fire).let { a ->
        (0 until 4).map { Bitmap.createBitmap(a, it * 40, 0, 40, 30) }
    }.also { fire = it }
    fun bg(ctx: Context) = bg ?: decode(ctx, R.drawable.forge_bg).also { bg = it }

    /** Pixel art bez vyhlazení. */
    val pixel = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
}

/**
 * Karta vylepšení (předloha: 3 × 3 dřevěné karty): ikona, název, popis, úroveň a cena s mincí.
 * Klepnutí = koupit jednou, podržení = koupit, kolik jde.
 */
@SuppressLint("ViewConstructor")
class ForgeCardView(ctx: Context, private val onBuy: (max: Boolean) -> Unit) : View(ctx) {
    var icon: Bitmap? = null
    var title = ""
    var desc = ""
    var level = 0
    var costIcon: Bitmap? = null
    var cost = ""
    var affordable = false
    private var pressed = false

    private val dp = resources.displayMetrics.density
    private val font: Typeface? = ResourcesCompat.getFont(ctx, R.font.jersey_15)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = font }
    private val r = RectF()

    init {
        isClickable = true
        isLongClickable = true
        setOnClickListener { onBuy(false) }
        setOnLongClickListener { onBuy(true); true }
        contentDescription = title
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { pressed = true; invalidate() }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { pressed = false; invalidate() }
        }
        return super.onTouchEvent(e)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val inset = if (pressed) 2 * dp else 0f
        r.set(inset, inset, w - inset, h - inset)
        // dřevěná karta: tmavý okraj, teplý přechod, světlejší vnitřní lem
        fill.shader = LinearGradient(0f, 0f, 0f, h, Color.parseColor("#A0562A"), Color.parseColor("#6E3418"), Shader.TileMode.CLAMP)
        c.drawRoundRect(r, 6 * dp, 6 * dp, fill)
        fill.shader = null
        stroke.strokeWidth = 3 * dp; stroke.color = Color.parseColor("#2A120A")
        c.drawRoundRect(r, 6 * dp, 6 * dp, stroke)
        stroke.strokeWidth = 1.2f * dp; stroke.color = Color.parseColor("#D88A4A")
        r.inset(3.5f * dp, 3.5f * dp)
        c.drawRoundRect(r, 4 * dp, 4 * dp, stroke)
        // vnitřní pole s popisem
        val pad = 7 * dp
        val iconS = min(h * 0.30f, 34 * dp)
        icon?.let {
            val k = max(1, (iconS / it.width).toInt())
            val s = it.width * k.toFloat()
            c.drawBitmap(it, Rect(0, 0, it.width, it.height), RectF(pad + inset, pad + inset, pad + inset + s, pad + inset + s), ForgeArt.pixel)
        }
        text.color = Color.parseColor("#FFF2D8")
        text.textSize = min(h * 0.15f, 17 * dp)
        text.textAlign = Paint.Align.LEFT
        val tx = pad + iconS + 6 * dp + inset
        drawShadowed(c, title.uppercase(), tx, pad + inset + text.textSize, w - tx - pad)
        // popis
        text.textSize = min(h * 0.12f, 13.5f * dp)
        text.color = Color.parseColor("#F4DEC0")
        val lines = wrap(desc, w - 2 * pad - 2 * inset)
        val top = pad + iconS + 6 * dp + inset
        lines.take(3).forEachIndexed { i, l ->
            text.textAlign = Paint.Align.CENTER
            c.drawText(l, w / 2, top + text.textSize * (i + 0.9f), text)
        }
        // úroveň a cena
        text.textSize = min(h * 0.13f, 15 * dp)
        text.textAlign = Paint.Align.LEFT
        text.color = Color.parseColor("#FFF2D8")
        val by = h - pad - inset
        drawShadowed(c, "Lv $level", pad + inset, by, w)
        text.textAlign = Paint.Align.RIGHT
        val coinS = text.textSize * 1.1f
        val cx = w - pad - inset
        costIcon?.let { c.drawBitmap(it, Rect(0, 0, it.width, it.height), RectF(cx - coinS, by - coinS * 0.85f, cx, by + coinS * 0.15f), ForgeArt.pixel) }
        text.color = if (affordable) Color.parseColor("#FFF2D8") else Color.parseColor("#FF8A7A")
        drawShadowed(c, cost, cx - coinS - 3 * dp, by, w)
    }

    private fun drawShadowed(c: Canvas, s: String, x: Float, y: Float, maxW: Float) {
        var t = s
        while (t.length > 3 && text.measureText(t) > maxW) t = t.dropLast(2) + "…"
        val col = text.color
        text.color = Color.parseColor("#2A120A"); c.drawText(t, x + dp, y + dp, text)
        text.color = col; c.drawText(t, x, y, text)
    }

    private fun wrap(s: String, maxW: Float): List<String> {
        val out = mutableListOf<String>(); var cur = ""
        for (word in s.split(" ")) {
            val t = if (cur.isEmpty()) word else "$cur $word"
            if (text.measureText(t) <= maxW) cur = t else { if (cur.isNotEmpty()) out += cur; cur = word }
        }
        if (cur.isNotEmpty()) out += cur
        return out
    }
}

/**
 * Pravá strana Výhně: dílna, stará pec s plamenem v ústí, záře a jiskry při šlápnutí na měch, kouř z komína,
 * bublina s uhlíky, tabulka přetavení a artefakty pod pecí (jako peříčka v předloze).
 */
class FurnaceView(ctx: Context) : View(ctx) {
    var amount = ""
    var amountIcon: Bitmap? = null
    var rates = ""
    var rebornLabel = ""
    var rebornReady = false
    var artifacts: List<Forge.Artifact> = emptyList()
    var onTap: (x: Float, y: Float) -> Double = { _, _ -> 0.0 }
    var onReborn: () -> Unit = {}
    var onArtifact: (Forge.Artifact?) -> Unit = {}

    private val dp = resources.displayMetrics.density
    private val font: Typeface? = ResourcesCompat.getFont(ctx, R.font.jersey_15)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = font; textAlign = Paint.Align.CENTER }
    private val furnaceRect = RectF()
    private val rebornRect = RectF()
    private val slotRects = List(3) { RectF() }
    private var scale = 1f
    private var heat = 0f                                  // záře po šlápnutí (0..1, opadá)
    private val start = System.currentTimeMillis()

    private class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val size: Float, val color: Int, val text: String? = null)
    private val parts = mutableListOf<Particle>()

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val f = ForgeArt.furnace(context)
        scale = max(1, min((h * 0.66f) / f.height, (w * 0.86f) / f.width).toInt()).toFloat()
        val fw = f.width * scale; val fh = f.height * scale
        val left = (w - fw) / 2; val top = h * 0.13f
        furnaceRect.set(left, top, left + fw, top + fh)
        val slot = 26 * dp
        val gap = 8 * dp
        val sy = furnaceRect.bottom + 6 * dp
        val sx0 = w / 2f - (3 * slot + 2 * gap) / 2
        slotRects.forEachIndexed { i, r -> r.set(sx0 + i * (slot + gap), sy, sx0 + i * (slot + gap) + slot, sy + slot) }
        val bw = min(w * 0.8f, 230 * dp)
        rebornRect.set(w / 2f - bw / 2, sy + slot + 6 * dp, w / 2f + bw / 2, min(h - 4 * dp, sy + slot + 40 * dp))
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked != MotionEvent.ACTION_DOWN) return true
        val x = e.x; val y = e.y
        when {
            rebornRect.contains(x, y) -> onReborn()
            slotRects.any { it.contains(x, y) } -> onArtifact(artifacts.getOrNull(slotRects.indexOfFirst { it.contains(x, y) }))
            furnaceRect.contains(x, y) || y > height * 0.1f -> {
                val gain = onTap(x, y)
                heat = min(1f, heat + 0.35f)
                performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                burst(x, y, gain)
            }
        }
        return true
    }

    private fun mouth(): Pair<Float, Float> = (furnaceRect.left + 64 * scale) to (furnaceRect.top + 98 * scale)
    private fun chimney(): Pair<Float, Float> = (furnaceRect.left + 80 * scale) to (furnaceRect.top + 2 * scale)

    private fun burst(x: Float, y: Float, gain: Double) {
        val (mx, my) = mouth()
        repeat(10) {
            parts += Particle(mx + Random.nextFloat() * 30 * dp - 15 * dp, my, Random.nextFloat() * 4 - 2, -(3 + Random.nextFloat() * 5), 1f,
                (1.5f + Random.nextFloat() * 2f) * dp, if (Random.nextBoolean()) Color.parseColor("#FFD254") else Color.parseColor("#FF8A2A"))
        }
        val (cx, cy) = chimney()
        parts += Particle(cx, cy, Random.nextFloat() - 0.5f, -1.2f, 1.6f, 6 * dp, Color.parseColor("#B8B0A8"))
        if (gain > 0) parts += Particle(x, y - 10 * dp, 0f, -2.2f, 1.2f, 0f, Color.WHITE, "+" + Forge.amount(gain).second)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val t = (System.currentTimeMillis() - start) / 1000f
        // dílna: pozadí přes celou plochu, ztmavené
        val bg = ForgeArt.bg(context)
        val k = max(w / bg.width, h / bg.height)
        val bw = bg.width * k; val bh = bg.height * k
        c.drawBitmap(bg, Rect(0, 0, bg.width, bg.height), RectF((w - bw) / 2, (h - bh) / 2, (w + bw) / 2, (h + bh) / 2), ForgeArt.pixel)
        paint.shader = null; paint.color = Color.argb(120, 12, 6, 6); c.drawRect(0f, 0f, w, h, paint)
        // záře výhně kolem pece
        val (mx, my) = mouth()
        val glow = 0.45f + 0.08f * kotlin.math.sin(t * 6f) + heat * 0.55f
        paint.shader = RadialGradient(mx, my, furnaceRect.width() * 0.9f, Color.argb((150 * glow).toInt().coerceIn(0, 255), 255, 140, 50), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, paint); paint.shader = null
        // pec + plamen
        val f = ForgeArt.furnace(context)
        c.drawBitmap(f, Rect(0, 0, f.width, f.height), furnaceRect, ForgeArt.pixel)
        val fires = ForgeArt.fire(context)
        val fr = fires[((t * (8 + heat * 10)).toInt()) % fires.size]
        val fx = furnaceRect.left + 44 * scale; val fy = furnaceRect.top + 82 * scale
        c.drawBitmap(fr, Rect(0, 0, fr.width, fr.height), RectF(fx, fy, fx + 40 * scale, fy + 30 * scale), ForgeArt.pixel)
        // kouř z komína (pomalé obláčky)
        val (cx, cy) = chimney()
        for (i in 0 until 4) {
            val p = ((t * 0.35f + i / 4f) % 1f)
            paint.color = Color.argb((110 * (1 - p)).toInt(), 190, 184, 176)
            c.drawCircle(cx + kotlin.math.sin(t + i) * 6 * dp, cy - p * 60 * dp, (4 + p * 10) * dp, paint)
        }
        // částice
        val it = parts.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.x += p.vx * dp * 0.6f; p.y += p.vy * dp * 0.6f; p.vy += if (p.text == null && p.size < 5 * dp) 0.12f else 0f; p.life -= 0.025f
            if (p.life <= 0) { it.remove(); continue }
            if (p.text != null) {
                text.textSize = 22 * dp; text.color = Color.argb((255 * min(1f, p.life)).toInt(), 255, 240, 200)
                c.drawText(p.text, p.x, p.y, text)
            } else {
                paint.color = p.color; paint.alpha = (255 * min(1f, p.life)).toInt()
                c.drawRect(p.x, p.y, p.x + p.size, p.y + p.size, paint)
            }
        }
        // bublina s uhlíky (jako sova v předloze)
        text.textSize = 26 * dp
        val tw = text.measureText(amount) + 40 * dp
        val br = RectF(w / 2 - tw / 2, 10 * dp, w / 2 + tw / 2, 10 * dp + 40 * dp)
        paint.color = Color.parseColor("#6E3418"); c.drawRoundRect(br, 20 * dp, 20 * dp, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 3 * dp; paint.color = Color.parseColor("#2A120A")
        c.drawRoundRect(br, 20 * dp, 20 * dp, paint); paint.style = Paint.Style.FILL
        amountIcon?.let { c.drawBitmap(it, Rect(0, 0, it.width, it.height), RectF(br.right - 34 * dp, br.top + 6 * dp, br.right - 6 * dp, br.top + 34 * dp), ForgeArt.pixel) }
        text.color = Color.parseColor("#FFF2D8"); text.textAlign = Paint.Align.CENTER
        c.drawText(amount, br.centerX() - 14 * dp, br.bottom - 12 * dp, text)
        text.textSize = 15 * dp; text.color = Color.parseColor("#F4DEC0")
        c.drawText(rates, w / 2, br.bottom + 18 * dp, text)
        // artefakty pod pecí
        Forge.Artifact.entries.forEachIndexed { i, a ->
            val r = slotRects[i]
            paint.color = Color.parseColor("#3A1C10"); c.drawRoundRect(r, 4 * dp, 4 * dp, paint)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 2 * dp; paint.color = if (a in artifacts) Color.parseColor("#F0C050") else Color.parseColor("#7A4A2A")
            c.drawRoundRect(r, 4 * dp, 4 * dp, paint); paint.style = Paint.Style.FILL
            val ic = ForgeArt.icon(context, a.icon)
            val p = Paint(ForgeArt.pixel).apply { alpha = if (a in artifacts) 255 else 60 }
            c.drawBitmap(ic, Rect(0, 0, ic.width, ic.height), RectF(r.left + 3 * dp, r.top + 3 * dp, r.right - 3 * dp, r.bottom - 3 * dp), p)
        }
        // přetavení
        paint.color = if (rebornReady) Color.parseColor("#C0601E") else Color.parseColor("#5A3020")
        c.drawRoundRect(rebornRect, 8 * dp, 8 * dp, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 3 * dp; paint.color = Color.parseColor("#2A120A")
        c.drawRoundRect(rebornRect, 8 * dp, 8 * dp, paint); paint.style = Paint.Style.FILL
        text.textSize = 17 * dp; text.color = if (rebornReady) Color.parseColor("#FFF2D8") else Color.parseColor("#C8A890")
        c.drawText(rebornLabel, rebornRect.centerX(), rebornRect.centerY() + 6 * dp, text)
        heat = max(0f, heat - 0.02f)
        postInvalidateOnAnimation()
    }
}
