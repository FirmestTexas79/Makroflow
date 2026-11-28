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
    private var relics: List<Bitmap>? = null

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

    /** Relikvie z přetavení 40 × 40 (tools/mapgen/forge_relics.py): Srdce výhně, Kovadlina předků, Kladivo mistra. */
    const val RELIC = 40
    const val BG_FURNACE_X = 136          // kam v pozadí padne levý horní roh pece (tools/mapgen/gen_forge.py)
    const val BG_FURNACE_Y = 298
    const val BG_FLOOR_ROW = 436          // řádek pozadí, na kterém stojí sokl pece
    fun relic(ctx: Context, i: Int): Bitmap = (relics ?: decode(ctx, R.drawable.forge_relics).let { a ->
        (0 until a.width / RELIC).map { Bitmap.createBitmap(a, it * RELIC, 0, RELIC, RELIC) }
    }.also { relics = it })[i]

    /** Pixel art bez vyhlazení. */
    val pixel = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
}

/**
 * Karta vylepšení (předloha: 3 × 3 dřevěné karty): ikona, úroveň, název, popis a cena s mincí.
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
    /** Zamčená karta (vylepšení se odemkne až po vydělání prahu): ztmavená, místo popisu a ceny ukazuje [lockText]. */
    var locked = false
    var lockText = ""
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
        // karta je na výšku úzká (3 vedle sebe): písmo a ikona se škálují podle velikosti karty
        val k = min(w / (104 * dp), h / (118 * dp)).coerceIn(0.8f, 1.5f)
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
        val pad = 7 * dp * k
        val left = pad + inset
        // horní řádek: ikona vlevo, úroveň vpravo
        val iconS = 26 * dp * k
        icon?.let {
            val m = max(1, (iconS / it.width).toInt())
            val s = it.width * m.toFloat()
            c.drawBitmap(it, Rect(0, 0, it.width, it.height), RectF(left, left, left + s, left + s), ForgeArt.pixel)
        }
        text.color = Color.parseColor("#FFF2D8")
        text.textSize = 17 * dp * k
        text.textAlign = Paint.Align.RIGHT
        drawShadowed(c, if (locked) "ZAMČENO" else "Lv $level", w - left, left + text.textSize, w - left - iconS - 4 * dp)
        // název pod ikonou
        text.textSize = 17 * dp * k
        text.textAlign = Paint.Align.CENTER
        val titleY = left + iconS + 3 * dp * k + text.textSize
        drawShadowed(c, title.uppercase(), w / 2, titleY, w - 2 * pad)
        val costTop: Float
        if (!locked) {
            // dole cena s mincí
            text.textSize = 17 * dp * k
            text.textAlign = Paint.Align.RIGHT
            val by = h - pad - inset
            val coinS = text.textSize * 1.1f
            val cx = w - pad - inset
            costTop = by - text.textSize
            costIcon?.let { c.drawBitmap(it, Rect(0, 0, it.width, it.height), RectF(cx - coinS, by - coinS * 0.85f, cx, by + coinS * 0.15f), ForgeArt.pixel) }
            text.color = if (affordable) Color.parseColor("#FFF2D8") else Color.parseColor("#FF8A7A")
            drawShadowed(c, cost, cx - coinS - 3 * dp, by, w)
        } else costTop = h - pad - inset
        // popis (u zamčené karty podmínka odemčení) mezi názvem a cenou, kolik řádků se vejde
        text.textSize = 13.5f * dp * k
        text.color = Color.parseColor("#F4DEC0")
        text.textAlign = Paint.Align.CENTER
        val lineH = text.textSize * 1.1f
        var y = titleY + 4 * dp * k + text.textSize
        for (l in wrap(if (locked) lockText else desc, w - 2 * pad - 2 * inset)) {
            if (y > costTop - dp) break
            c.drawText(l, w / 2, y, text)
            y += lineH
        }
        if (locked) {                                           // ztmavení: karta je vidět, ale nedá se koupit
            fill.color = Color.argb(110, 20, 8, 4)
            r.set(inset, inset, w - inset, h - inset)
            c.drawRoundRect(r, 6 * dp, 6 * dp, fill)
        }
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
 * Bar mezi pecí a spodním panelem (docs/adr/0118): kolik uhlíků je vydělaných v tomto kole a kolik chybí do dalšího
 * prahu, který odemkne další vylepšení. Číslo je uvnitř baru („100 / 1 000“), nad ním název toho, co se odemkne.
 */
class ForgeProgressView(ctx: Context) : View(ctx) {
    var caption = ""
    var value = ""
    var progress = 0f

    private val dp = resources.displayMetrics.density
    private val font: Typeface? = ResourcesCompat.getFont(ctx, R.font.jersey_15)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = font; textAlign = Paint.Align.CENTER }
    private val r = RectF()

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        text.textSize = 17 * dp; text.color = Color.parseColor("#F4DEC0")
        c.drawText(caption, w / 2, 17 * dp, text)
        val top = 24 * dp; val bottom = h - 3 * dp
        r.set(3 * dp, top, w - 3 * dp, bottom)
        fill.color = Color.parseColor("#2A120A"); c.drawRoundRect(r, 8 * dp, 8 * dp, fill)
        val inner = RectF(r.left + 3 * dp, r.top + 3 * dp, r.right - 3 * dp, r.bottom - 3 * dp)
        fill.color = Color.parseColor("#4A2414"); c.drawRoundRect(inner, 5 * dp, 5 * dp, fill)
        val fw = inner.width() * progress.coerceIn(0f, 1f)
        if (fw > 0f) {
            val bar = RectF(inner.left, inner.top, inner.left + max(fw, 2 * 5 * dp), inner.bottom)
            fill.shader = LinearGradient(0f, bar.top, 0f, bar.bottom, Color.parseColor("#FFB04A"), Color.parseColor("#C0601E"), Shader.TileMode.CLAMP)
            c.drawRoundRect(bar, 5 * dp, 5 * dp, fill); fill.shader = null
        }
        stroke.strokeWidth = 3 * dp; stroke.color = Color.parseColor("#2A120A")
        c.drawRoundRect(r, 8 * dp, 8 * dp, stroke)
        text.textSize = 19 * dp
        val ty = r.centerY() + text.textSize * 0.32f
        text.color = Color.parseColor("#2A120A"); c.drawText(value, w / 2 + dp, ty + dp, text)
        text.color = Color.parseColor("#FFF2D8"); c.drawText(value, w / 2, ty, text)
    }
}

/**
 * Horní polovina Výhně: dílna (pozadí ve stejném měřítku pixelů jako pec), stará pec s plamenem v ústí, záře a jiskry při
 * šlápnutí na měch, kouř z komína, bublina s uhlíky, tlačítko přetavení a velké relikvie vpravo od pece (klepnutí ukáže bonus).
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

    private val dp = resources.displayMetrics.density
    private val font: Typeface? = ResourcesCompat.getFont(ctx, R.font.jersey_15)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = font; textAlign = Paint.Align.CENTER }
    private val furnaceRect = RectF()
    private val rebornRect = RectF()
    private val bgRect = RectF()
    private val slotRects = List(Forge.Artifact.entries.size) { RectF() }
    private var scale = 1f
    private var heat = 0f                                  // záře po šlápnutí (0..1, opadá)
    private var selected = -1                              // vybraná relikvie (panel s bonusem), -1 = žádná
    private val start = System.currentTimeMillis()

    private class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val size: Float, val color: Int, val text: String? = null)

    /** Probíhající ukování relikvie po přetavení (docs/adr/0118); během něj se na pec neklepe. */
    private class ForgeAnim(val index: Int, val start: Long, val onDone: () -> Unit) { var landed = false; var flashed = false }
    private var forgeAnim: ForgeAnim? = null
    val forging get() = forgeAnim != null
    private val GLOW = intArrayOf(Color.parseColor("#FF8A2A"), Color.parseColor("#5FE8F0"), Color.parseColor("#FFD25A"))

    /**
     * Animace ukování: pec se rozžhaví do běla a otřese, z ústí vyletí relikvie, nad pecí se zvětší v paprscích s nápisem
     * „Ukováno“, pak odletí obloukem do svého slotu vpravo, kde se rozzáří a otevře se její popis. [onDone] při dopadu.
     */
    fun playForge(index: Int, onDone: () -> Unit) {
        selected = -1
        forgeAnim = ForgeAnim(index, System.currentTimeMillis(), onDone)
    }
    private val parts = mutableListOf<Particle>()

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val f = ForgeArt.furnace(context)
        val topPad = 84 * dp                                 // bublina s uhlíky a řádek s výnosy nad pecí
        val below = 6 * dp + 40 * dp + 8 * dp                // tlačítko přetavení pod pecí
        val gap = 10 * dp
        val slotPad = 10 * dp
        // pec je přesně uprostřed; sloupec relikvií se musí vejít do pravé poloviny vedle ní:
        // 64·s (půl pece) + mezera + slot (40·s + okraj) + mezera ≤ w / 2. Měřítko pixelů je společné pro pec, pozadí i relikvie.
        val byWidth = ((w / 2f - 2 * gap - slotPad) / (f.width / 2f + ForgeArt.RELIC)).toInt()
        val byHeight = ((h - topPad - below) / f.height).toInt()
        scale = max(1, min(byWidth, byHeight)).toFloat()
        val slot = ForgeArt.RELIC * scale + slotPad
        val fw = f.width * scale; val fh = f.height * scale
        val left = Math.round((w - fw) / 2f).toFloat()
        val top = Math.round(topPad + max(0f, h - topPad - below - fh) / 2).toFloat()
        furnaceRect.set(left, top, left + fw, top + fh)
        // pozadí: stejné celočíselné měřítko, sokl pece přesně na řádku podlahy
        val bg = ForgeArt.bg(context)
        val bgLeft = left - ForgeArt.BG_FURNACE_X * scale
        val bgTop = top - ForgeArt.BG_FURNACE_Y * scale
        bgRect.set(bgLeft, bgTop, bgLeft + bg.width * scale, bgTop + bg.height * scale)
        // relikvie: svislý sloupec vpravo od pece, vystředěný na výšku pece; v pravé polovině vystředěný mezi pecí a okrajem
        val freeL = furnaceRect.right; val freeR = w.toFloat()
        val sx = max(freeL + gap, (freeL + freeR) / 2f - slot / 2f).coerceAtMost(w - gap - slot)
        // přetavení: jen pod pecí (stejná šířka jako pec), aby nezasahovalo pod sloupec relikvií
        rebornRect.set(furnaceRect.left, h - below + 4 * dp, furnaceRect.right, h - 10 * dp)
        val vgap = 8 * dp
        val totalH = slotRects.size * slot + (slotRects.size - 1) * vgap
        val sy0 = max(topPad - 10 * dp, min(furnaceRect.top + (fh - totalH) / 2f, rebornRect.top - 6 * dp - totalH))
        slotRects.forEachIndexed { i, r -> r.set(sx, sy0 + i * (slot + vgap), sx + slot, sy0 + i * (slot + vgap) + slot) }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked != MotionEvent.ACTION_DOWN) return true
        if (forgeAnim != null) return true
        val x = e.x; val y = e.y
        when {
            rebornRect.contains(x, y) -> onReborn()
            slotRects.any { it.contains(x, y) } -> {                       // relikvie: klepnutí ukáže její bonus, druhé ho schová
                val i = slotRects.indexOfFirst { it.contains(x, y) }
                selected = if (selected == i) -1 else i
                performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            }
            furnaceRect.contains(x, y) || y > height * 0.1f -> {
                selected = -1
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
                scale * (1 + Random.nextInt(2)), if (Random.nextBoolean()) Color.parseColor("#FFD254") else Color.parseColor("#FF8A2A"))
        }
        val (cx, cy) = chimney()
        parts += Particle(cx, cy, Random.nextFloat() - 0.5f, -1.2f, 1.6f, scale * 3, Color.parseColor("#B8B0A8"))
        if (gain > 0) parts += Particle(x, y - 10 * dp, 0f, -2.2f, 1.2f, 0f, Color.WHITE, "+" + Forge.amount(gain).second)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val t = (System.currentTimeMillis() - start) / 1000f
        // otřesy při rozžhavení (první fáze ukování)
        val fa = forgeAnim
        val ft = fa?.let { (System.currentTimeMillis() - it.start) / 1000f } ?: -1f
        c.save()
        if (ft in 0f..1.6f) {
            val k = min(1f, ft / 1.4f) * (if (ft > 1.4f) (1.6f - ft) / 0.2f else 1f)
            c.translate(kotlin.math.sin(ft * 63f) * 4 * dp * k, kotlin.math.cos(ft * 51f) * 3 * dp * k)
        }
        // dílna: pozadí ve stejném měřítku pixelů jako pec; co pozadí nepokryje, je ve stejné barvě jako jeho okraj
        val bg = ForgeArt.bg(context)
        paint.shader = null; paint.color = Color.parseColor("#17100E"); c.drawRect(0f, 0f, w, h, paint)
        c.save(); c.clipRect(0f, 0f, w, h)
        c.drawBitmap(bg, Rect(0, 0, bg.width, bg.height), bgRect, ForgeArt.pixel)
        c.restore()
        // záře výhně kolem ústí (mění se s oheň a šlápnutím)
        val (mx, my) = mouth()
        val glow = 0.35f + 0.07f * kotlin.math.sin(t * 6f) + heat * 0.5f
        paint.shader = RadialGradient(mx, my, furnaceRect.width() * 0.95f, Color.argb((120 * glow).toInt().coerceIn(0, 255), 255, 140, 50), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, paint); paint.shader = null
        // pec + plamen
        val f = ForgeArt.furnace(context)
        c.drawBitmap(f, Rect(0, 0, f.width, f.height), furnaceRect, ForgeArt.pixel)
        val fires = ForgeArt.fire(context)
        val fr = fires[((t * (8 + heat * 10)).toInt()) % fires.size]
        val fx = furnaceRect.left + 44 * scale; val fy = furnaceRect.top + 82 * scale
        c.drawBitmap(fr, Rect(0, 0, fr.width, fr.height), RectF(fx, fy, fx + 40 * scale, fy + 30 * scale), ForgeArt.pixel)
        // kouř z komína: hranaté obláčky po celých pixelech pece
        val (cx, cy) = chimney()
        for (i in 0 until 4) {
            val p = ((t * 0.35f + i / 4f) % 1f)
            val s = Math.round(1 + p * 3) * scale
            val px = Math.round((cx + kotlin.math.sin(t + i) * 6 * dp) / scale) * scale
            val py = Math.round((cy - p * 60 * dp) / scale) * scale
            paint.color = Color.argb((110 * (1 - p)).toInt(), 190, 184, 176)
            c.drawRect(px - s / 2, py - s / 2, px + s / 2, py + s / 2, paint)
        }
        // částice
        val it = parts.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.x += p.vx * dp * 0.6f; p.y += p.vy * dp * 0.6f; p.vy += if (p.text == null && p.size < 5 * scale) 0.12f else 0f; p.life -= 0.025f
            if (p.life <= 0) { it.remove(); continue }
            if (p.text != null) {
                text.textSize = 26 * dp; text.color = Color.argb((255 * min(1f, p.life)).toInt(), 255, 240, 200)
                c.drawText(p.text, p.x, p.y, text)
            } else {
                paint.color = p.color; paint.alpha = (255 * min(1f, p.life)).toInt()
                c.drawRect(p.x, p.y, p.x + p.size, p.y + p.size, paint)
            }
        }
        // bublina s uhlíky (jako sova v předloze)
        text.textSize = 30 * dp
        val tw = text.measureText(amount) + 46 * dp
        val bx = w / 2f
        val br = RectF(bx - tw / 2, 10 * dp, bx + tw / 2, 10 * dp + 44 * dp)
        paint.color = Color.parseColor("#6E3418"); c.drawRoundRect(br, 22 * dp, 22 * dp, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 3 * dp; paint.color = Color.parseColor("#2A120A")
        c.drawRoundRect(br, 22 * dp, 22 * dp, paint); paint.style = Paint.Style.FILL
        amountIcon?.let { c.drawBitmap(it, Rect(0, 0, it.width, it.height), RectF(br.right - 38 * dp, br.top + 7 * dp, br.right - 8 * dp, br.top + 37 * dp), ForgeArt.pixel) }
        text.color = Color.parseColor("#FFF2D8"); text.textAlign = Paint.Align.CENTER
        c.drawText(amount, br.centerX() - 15 * dp, br.bottom - 12 * dp, text)
        text.textSize = 18 * dp; text.color = Color.parseColor("#F4DEC0")
        drawOutlined(c, rates, bx, br.bottom + 21 * dp)
        // relikvie vpravo od pece (jako peříčka v předloze): velké sloty, ukované svítí, ostatní jsou stíny
        Forge.Artifact.entries.forEachIndexed { i, a ->
            val r = slotRects[i]
            val own = a in artifacts
            paint.color = Color.parseColor(if (own) "#3A1C10" else "#241008"); c.drawRoundRect(r, 8 * dp, 8 * dp, paint)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 3 * dp
            paint.color = if (i == selected) Color.parseColor("#FFE08A") else if (own) Color.parseColor("#F0C050") else Color.parseColor("#7A4A2A")
            c.drawRoundRect(r, 8 * dp, 8 * dp, paint); paint.style = Paint.Style.FILL
            val ic = ForgeArt.relic(context, i)
            val pp = Paint(ForgeArt.pixel).apply {
                if (!own) colorFilter = android.graphics.PorterDuffColorFilter(Color.parseColor("#120804"), android.graphics.PorterDuff.Mode.SRC_ATOP)
                alpha = if (own) 255 else 150
            }
            val isz = ForgeArt.RELIC * scale
            val ix = r.centerX() - isz / 2; val iy = r.centerY() - isz / 2
            c.drawBitmap(ic, Rect(0, 0, ic.width, ic.height), RectF(ix, iy, ix + isz, iy + isz), pp)
            if (!own) { text.textSize = 28 * dp; text.color = Color.parseColor("#B8845A"); text.textAlign = Paint.Align.CENTER; c.drawText("?", r.centerX(), r.centerY() + 10 * dp, text) }
        }
        // panel s bonusem vybrané relikvie
        if (selected in slotRects.indices) drawRelicInfo(c, Forge.Artifact.entries[selected], selected < artifacts.size)
        // přetavení
        paint.color = if (rebornReady) Color.parseColor("#C0601E") else Color.parseColor("#5A3020")
        c.drawRoundRect(rebornRect, 8 * dp, 8 * dp, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 3 * dp; paint.color = Color.parseColor("#2A120A")
        c.drawRoundRect(rebornRect, 8 * dp, 8 * dp, paint); paint.style = Paint.Style.FILL
        text.textSize = 18 * dp; text.color = if (rebornReady) Color.parseColor("#FFF2D8") else Color.parseColor("#C8A890")
        val fit = rebornRect.width() - 16 * dp
        if (text.measureText(rebornLabel) > fit) text.textSize *= fit / text.measureText(rebornLabel)   // dlouhý popisek se zmenší
        c.drawText(rebornLabel, rebornRect.centerX(), rebornRect.centerY() + text.textSize * 0.34f, text)
        c.restore()
        if (fa != null) drawForgeAnim(c, fa, ft)
        heat = max(0f, heat - 0.02f)
        postInvalidateOnAnimation()
    }

    private fun ease(p: Float) = p * p * (3 - 2 * p)

    private fun drawForgeAnim(c: Canvas, a: ForgeAnim, t: Float) {
        val w = width.toFloat()
        val col = GLOW[a.index.coerceIn(0, GLOW.size - 1)]
        val (mx, my) = mouth()
        val big = ForgeArt.RELIC * scale * 1.7f
        val small = ForgeArt.RELIC * scale.toFloat()
        val center = w / 2f to (furnaceRect.top + furnaceRect.height() * 0.30f)
        val slot = slotRects.getOrNull(a.index) ?: slotRects.last()
        // 1) rozžhavení: pec do běla, jiskry z ústí
        if (t < 1.4f) {
            heat = 1f
            val k = t / 1.4f
            paint.shader = RadialGradient(mx, my, furnaceRect.width() * (0.4f + 0.8f * k), Color.argb((200 * k).toInt(), 255, 236, 190), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w, height.toFloat(), paint); paint.shader = null
            repeat(3) {
                parts += Particle(mx + (Random.nextFloat() - 0.5f) * 40 * dp, my, (Random.nextFloat() - 0.5f) * 7, -(3 + Random.nextFloat() * 7), 1f,
                    scale * (1 + Random.nextInt(2)), if (Random.nextBoolean()) Color.parseColor("#FFF2B0") else col)
            }
        }
        // záblesk ve chvíli, kdy relikvie vyletí
        if (t in 1.4f..1.9f) {
            if (!a.flashed) { a.flashed = true; performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS) }
            paint.color = Color.argb((230 * (1 - (t - 1.4f) / 0.5f)).toInt(), 255, 246, 220)
            c.drawRect(0f, 0f, w, height.toFloat(), paint)
        }
        if (t < 1.4f) return
        // pozice a velikost relikvie
        val (x, y, size) = when {
            t < 2.4f -> { val p = ease(((t - 1.4f) / 1.0f).coerceIn(0f, 1f)); Triple(mx + (center.first - mx) * p, my + (center.second - my) * p, big * (0.2f + 0.8f * p)) }
            t < 3.5f -> Triple(center.first, center.second + kotlin.math.sin((t - 2.4f) * 3f) * 3 * dp, big)
            else -> {
                val p = ease(((t - 3.5f) / 0.8f).coerceIn(0f, 1f))
                val cx = center.first; val cy = center.second; val ex = slot.centerX(); val ey = slot.centerY()
                val kx = (cx + ex) / 2f; val ky = min(cy, ey) - 50 * dp                    // oblouk přes kontrolní bod nahoře
                val q = 1 - p
                Triple(q * q * cx + 2 * q * p * kx + p * p * ex, q * q * cy + 2 * q * p * ky + p * p * ey, big + (small - big) * p)
            }
        }
        // paprsky a záře za relikvií (slábnou při odletu)
        val rays = if (t < 3.5f) min(1f, (t - 1.4f) / 0.6f) else max(0f, 1 - (t - 3.5f) / 0.5f)
        if (rays > 0f) {
            paint.shader = RadialGradient(x, y, size * 1.4f, (col and 0x00FFFFFF) or ((170 * rays).toInt() shl 24), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            c.drawCircle(x, y, size * 1.4f, paint); paint.shader = null
            c.save(); c.rotate(t * 35f, x, y)
            paint.color = (col and 0x00FFFFFF) or ((90 * rays).toInt() shl 24)
            val path = android.graphics.Path()
            for (i in 0 until 12) {
                val a0 = Math.toRadians(i * 30.0 - 5); val a1 = Math.toRadians(i * 30.0 + 5)
                path.moveTo(x, y)
                path.lineTo(x + kotlin.math.cos(a0).toFloat() * size * 1.6f, y + kotlin.math.sin(a0).toFloat() * size * 1.6f)
                path.lineTo(x + kotlin.math.cos(a1).toFloat() * size * 1.6f, y + kotlin.math.sin(a1).toFloat() * size * 1.6f)
                path.close()
            }
            c.drawPath(path, paint); c.restore()
        }
        // relikvie (ostře; při odletu zanechává jiskry)
        val ic = ForgeArt.relic(context, a.index.coerceIn(0, 2))
        c.drawBitmap(ic, Rect(0, 0, ic.width, ic.height), RectF(x - size / 2, y - size / 2, x + size / 2, y + size / 2), ForgeArt.pixel)
        if (t > 3.5f && t < 4.3f) parts += Particle(x, y, (Random.nextFloat() - 0.5f) * 2, (Random.nextFloat() - 0.5f) * 2, 0.8f, scale.toFloat(), col)
        // nápis
        if (t in 1.8f..3.6f) {
            val al = (min(1f, (t - 1.8f) / 0.4f) * min(1f, (3.6f - t) / 0.3f)).coerceIn(0f, 1f)
            val art = Forge.Artifact.entries[a.index.coerceIn(0, Forge.Artifact.entries.size - 1)]
            text.textAlign = Paint.Align.CENTER
            text.textSize = 18 * dp; text.color = Color.argb((255 * al).toInt(), 255, 236, 190)
            drawOutlined(c, "✦ UKOVÁNO ✦", w / 2f, center.second + big / 2 + 26 * dp)
            text.textSize = 30 * dp; text.color = (col and 0x00FFFFFF) or ((255 * al).toInt() shl 24)
            drawOutlined(c, art.label.uppercase(), w / 2f, center.second + big / 2 + 58 * dp)
        }
        // dopad do slotu: prstenec jisker, otevře se popis
        if (t >= 4.3f && !a.landed) {
            a.landed = true
            repeat(24) { i ->
                val ang = i / 24f * 2 * Math.PI.toFloat()
                parts += Particle(slot.centerX(), slot.centerY(), kotlin.math.cos(ang) * 5, kotlin.math.sin(ang) * 5, 1f, scale.toFloat() * 1.5f, col)
            }
            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            forgeAnim = null
            a.onDone()
            selected = a.index
        }
    }

    private fun drawOutlined(c: Canvas, s: String, x: Float, y: Float) {
        val col = text.color
        text.color = Color.parseColor("#1A0E0A"); c.drawText(s, x + dp, y + dp, text)
        text.color = col; c.drawText(s, x, y, text)
    }

    /** Dřevěný panel nad tlačítkem přetavení: název relikvie a její bonus (u neukované řekne, kdy se ukove); výška podle textu. */
    private fun drawRelicInfo(c: Canvas, a: Forge.Artifact, owned: Boolean) {
        val pad = 12 * dp
        val boxL = 8 * dp; val boxR = width - 8 * dp
        text.textAlign = Paint.Align.LEFT
        text.textSize = 17 * dp
        val body = if (owned) a.text else "${a.text}\nUkove se při ${a.ordinal + 1}. přetavení výhně."
        val lines = body.split("\n").flatMap { wrap(it, boxR - boxL - 2 * pad) }
        val lineH = 20 * dp
        val boxH = pad + 24 * dp + 6 * dp + lines.size * lineH + pad * 0.6f
        val bottom = rebornRect.top - 6 * dp
        val box = RectF(boxL, bottom - boxH, boxR, bottom)
        paint.color = Color.parseColor("#F2341A0E"); c.drawRoundRect(box, 10 * dp, 10 * dp, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 3 * dp; paint.color = Color.parseColor("#F0C050")
        c.drawRoundRect(box, 10 * dp, 10 * dp, paint); paint.style = Paint.Style.FILL
        text.textSize = 22 * dp; text.color = if (owned) Color.parseColor("#FFE08A") else Color.parseColor("#B8A890")
        drawLeft(c, a.label.uppercase(), box.left + pad, box.top + pad + 18 * dp)
        text.textSize = 17 * dp; text.color = Color.parseColor("#FFF2D8")
        var y = box.top + pad + 24 * dp + 6 * dp + 14 * dp
        for (l in lines) { drawLeft(c, l, box.left + pad, y); y += lineH }
        text.textAlign = Paint.Align.CENTER
    }

    private fun drawLeft(c: Canvas, s: String, x: Float, y: Float) {
        val col = text.color
        text.color = Color.parseColor("#1A0E0A"); c.drawText(s, x + dp, y + dp, text)
        text.color = col; c.drawText(s, x, y, text)
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
