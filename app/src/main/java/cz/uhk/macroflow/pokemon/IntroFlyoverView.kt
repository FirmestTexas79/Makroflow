package cz.uhk.macroflow.pokemon

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import androidx.core.content.res.ResourcesCompat
import cz.uhk.macroflow.R
import cz.uhk.macroflow.pokemon.skills.ui.WoodPanelDrawable
import cz.uhk.macroflow.pokemon.species.SpeciesRegistry
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Dlouhý úvod při prvním vstupu do Makrosvěta (docs/adr/0072): kamera prolétá nad našimi mapami
 * od města na sever až k Nebeskému průsmyku.
 *  - Město: Gudwin poskočí a pozdraví.
 *  - Louka: poutník kácí strom (třísky), z keře vyskočí makromon a uteče.
 *  - Hory: kamera se ponoří do štoly → Starý důl, poutník kope rudu (jiskry) → zpět ven.
 *  - Hory: socha Krále Mlsáka promluví (dialog s jeho podobiznou).
 *  - Průsmyk: výhled do nového kraje a nápis MAKROMON. Pak se scéna rozpadne do mapy.
 * Mapy jsou poskládané pod sebe na šířku obrazovky (sever nahoře), švy zakrývají mraky.
 * Vše je funkce času – žádný stav kromě načtených obrázků. Klepnutí na „Přeskočit“ úvod ukončí.
 */
class IntroFlyoverView(context: Context) : View(context) {

    var time = 0f
        set(v) { field = v; invalidate() }

    private val res = resources
    private val dp = res.displayMetrics.density
    private val font = runCatching { ResourcesCompat.getFont(context, R.font.jersey_15) }.getOrNull()

    // ── Obrázky (načítají se mimo hlavní vlákno) ────────────────────────────
    @Volatile private var ready = false
    private val maps = arrayOfNulls<Bitmap>(4)          // SKY, MOUNTAINS, MEADOW, TOWN – shora dolů
    private var mines: Bitmap? = null
    private var gudwin: Bitmap? = null
    private var bush: Bitmap? = null
    private var runner: Bitmap? = null
    private var axe: Bitmap? = null
    private var mining: Bitmap? = null
    private var cloud: Bitmap? = null
    private var king: Drawable? = null

    // ── Svět ────────────────────────────────────────────────────────────────
    private val tops = FloatArray(4)
    private var worldH = 0f
    private var minesH = 0f

    private val pixels = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
    private val smooth = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val fill = Paint()
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55000000 }
    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = font; color = 0xFFFEFAE0.toInt() }
    private val dark = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = font; color = 0xFF2E1B0E.toInt() }
    private val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = font; color = 0xFFFEFAE0.toInt(); textAlign = Paint.Align.CENTER }
    private val titleShadow = Paint(title).apply { color = 0xFF0A0F06.toInt() }
    private val dst = RectF()
    private val src = Rect()
    private val tiles = Path()
    private val skipRect = RectF()
    private val wood = WoodPanelDrawable(2f * dp)

    /** Kdy začne závěrečné rozpadnutí (po přeskočení dřív). */
    private var endAt = END_MS
    private var animator: ValueAnimator? = null
    private var onDone: (() -> Unit)? = null

    init {
        isClickable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = "Úvod do Makrosvěta. Klepni na Přeskočit."
        Thread {
            val opaque = BitmapFactory.Options().apply { inScaled = false; inPreferredConfig = Bitmap.Config.RGB_565 }
            val alpha = BitmapFactory.Options().apply { inScaled = false }
            fun dec(id: Int, o: BitmapFactory.Options = alpha) = BitmapFactory.decodeResource(res, id, o)
            fun asset(name: String) = runCatching { context.assets.open(name).use { BitmapFactory.decodeStream(it) } }.getOrNull()
            maps[0] = dec(R.drawable.sky_pass, opaque); maps[1] = dec(R.drawable.mountains, opaque)
            maps[2] = dec(R.drawable.meadow, opaque); maps[3] = dec(R.drawable.poketown, opaque)
            mines = dec(R.drawable.mines, opaque)
            gudwin = dec(R.drawable.gudwin_oliver)
            bush = dec(R.drawable.portal_bush_a)
            runner = SpeciesRegistry.byId("012")?.sprite?.let { n -> res.getIdentifier(n, "drawable", context.packageName) }
                ?.takeIf { it != 0 }?.let { BitmapFactory.decodeResource(res, it, BitmapFactory.Options().apply { inSampleSize = 2; inScaled = false }) }
            axe = asset("hero/axe_w.png"); mining = asset("hero/mining_e.png")
            cloud = makeCloud()
            king = runCatching {
                if (Build.VERSION.SDK_INT >= 28)
                    android.graphics.ImageDecoder.decodeDrawable(android.graphics.ImageDecoder.createSource(res, R.drawable.kral_mlsak))
                else null
            }.getOrNull() ?: runCatching { android.graphics.drawable.BitmapDrawable(res, dec(R.drawable.kral_mlsak)) }.getOrNull()
            post { ready = true; start() }
        }.start()
    }

    /** Pixelový mráček 32 × 14 ze dvou odstínů krému. */
    private fun makeCloud(): Bitmap {
        val b = Bitmap.createBitmap(32, 14, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint().apply { isAntiAlias = false }
        val blobs = listOf(Triple(8f, 9f, 5f), Triple(15f, 6f, 6f), Triple(23f, 8f, 5f), Triple(28f, 10f, 3.5f), Triple(4f, 11f, 3f))
        p.color = 0xFFD7CFA8.toInt(); blobs.forEach { (x, y, r) -> c.drawCircle(x, y + 1.5f, r, p) }
        p.color = 0xFFFEFAE0.toInt(); blobs.forEach { (x, y, r) -> c.drawCircle(x, y, r, p) }
        return b
    }

    private fun start() {
        val w = width.toFloat()
        if (w <= 0f) { post { start() }; return }
        var y = 0f
        maps.forEachIndexed { i, m -> tops[i] = y; y += (m?.height ?: 1) * w / (m?.width ?: 1) }
        worldH = y
        minesH = (mines?.height ?: 540) * w / (mines?.width ?: 150)
        if (Build.VERSION.SDK_INT >= 28) (king as? AnimatedImageDrawable)?.start()
        animator = ValueAnimator.ofFloat(0f, TOTAL_MS).apply {
            duration = TOTAL_MS.toLong(); interpolator = LinearInterpolator()
            addUpdateListener {
                time = it.animatedValue as Float
                if (time >= endAt + DISSOLVE_MS) finish()
            }
            start()
        }
    }

    private fun finish() {
        val done = onDone ?: return
        onDone = null
        animator?.cancel()
        if (Build.VERSION.SDK_INT >= 28) (king as? AnimatedImageDrawable)?.stop()
        done()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action == MotionEvent.ACTION_UP && skipRect.contains(e.x, e.y) && endAt == END_MS && time < END_MS) {
            endAt = time
            performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
        }
        return true
    }

    // ── Kamera ──────────────────────────────────────────────────────────────

    private class Key(val t: Float, val mines: Boolean, val x: Float, val y: Float, val z: Float)

    /** Klíčové záběry; x, y v jednotkách šířky obrazovky (W) ve světě. */
    private fun keys(w: Float): List<Key> {
        fun at(map: Int, fx: Float, fy: Float) = floatArrayOf(fx * w, tops[map] + fy * mapH(map, w))
        fun k(t: Float, p: FloatArray, z: Float, dx: Float = 0f, dy: Float = 0f) = Key(t, false, p[0] + dx * w, p[1] + dy * w, z)
        val gud = at(TOWN, 0.12f, 0.52f)
        val tree = at(MEADOW, TREE_X, TREE_Y)
        val bushP = at(MEADOW, BUSH_X, BUSH_Y)
        val mine = at(MOUNTAINS, 0.13f, 0.47f)
        val statue = at(MOUNTAINS, 0.5f, 0.6f)
        val silver = floatArrayOf(SILVER_X * w, SILVER_Y * minesH)
        return listOf(
            k(0f, at(TOWN, 0.5f, 0.8f), 1.15f),
            k(1400f, at(TOWN, 0.45f, 0.62f), 1.3f),
            k(2700f, gud, 2.4f, 0.14f, -0.05f),
            k(4300f, gud, 2.5f, 0.14f, -0.05f),
            k(5500f, floatArrayOf(0.5f * w, tops[TOWN]), 1.1f),
            k(6700f, tree, 2.6f, 0.09f, -0.03f),
            k(8600f, tree, 2.7f, 0.09f, -0.03f),
            k(9300f, bushP, 2.4f, 0.05f, -0.04f),
            k(10900f, bushP, 2.2f, 0.22f, -0.04f),
            k(11900f, at(MOUNTAINS, 0.3f, 0.75f), 1.4f),
            k(12500f, mine, 2.4f),
            k(12900f, mine, 7f),
            Key(12900f, true, silver[0], silver[1], 3.2f),
            Key(15800f, true, silver[0], silver[1], 2.6f),
            k(16000f, mine, 7f),
            k(16900f, mine, 2.2f, 0.05f),
            k(17900f, statue, 2.5f, 0f, -0.02f),
            k(21800f, statue, 2.6f, 0f, -0.02f),
            k(23300f, at(MOUNTAINS, 0.5f, 0.08f), 1.4f),
            k(24800f, floatArrayOf(0.5f * w, 0f), 1f),
            k(TOTAL_MS, floatArrayOf(0.5f * w, 0f), 1f)
        )
    }

    private fun mapH(i: Int, w: Float) = (maps[i]?.height ?: 1) * w / (maps[i]?.width ?: 1)

    private var keyCache: List<Key>? = null

    /** (mines?, cx, cy, zoom) pro čas [t]; mezi klíči plynule, změna světa jen ve tmě. */
    private fun camera(t: Float, w: Float, h: Float): Key {
        val ks = keyCache ?: keys(w).also { keyCache = it }
        val i = ks.indexOfLast { it.t <= t }.coerceAtLeast(0)
        val a = ks[i]; val b = ks.getOrNull(i + 1) ?: a
        var x = a.x; var y = a.y; var z = a.z
        if (b !== a && b.mines == a.mines && b.t > a.t) {
            val k = smooth(((t - a.t) / (b.t - a.t)).coerceIn(0f, 1f))
            x = a.x + (b.x - a.x) * k; y = a.y + (b.y - a.y) * k
            z = exp(ln(a.z) + (ln(b.z) - ln(a.z)) * k)
        }
        val worldBottom = if (a.mines) minesH else worldH
        val hw = w / (2 * z); val hh = h / (2 * z)
        x = x.coerceIn(hw, w - hw); y = y.coerceIn(hh, max(hh, worldBottom - hh))
        return Key(t, a.mines, x, y, z)
    }

    // ── Kreslení ────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (!ready || worldH == 0f) { canvas.drawColor(0xFF000000.toInt()); return }

        // závěr: scéna se rozpadne po dlaždicích do mapy
        val dissolve = ((time - endAt) / DISSOLVE_MS).coerceIn(0f, 1f)
        canvas.save()
        if (dissolve > 0f) { tilePath(w, h, dissolve); canvas.clipPath(tiles) }

        val cam = camera(time, w, h)
        canvas.drawColor(0xFF000000.toInt())
        canvas.save()
        canvas.translate(w / 2f, h / 2f); canvas.scale(cam.z, cam.z); canvas.translate(-cam.x, -cam.y)
        if (cam.mines) drawMines(canvas, w) else drawWorld(canvas, w)
        canvas.restore()

        if (!cam.mines) flyClouds(canvas, w, h)
        val black = blackAt(time)
        if (black > 0f) { fill.color = 0xFF000000.toInt(); fill.alpha = (black * 255).toInt(); canvas.drawRect(0f, 0f, w, h, fill) }

        narration(canvas, w, h)
        kingDialog(canvas, w, h)
        titleCard(canvas, w, h)
        skipButton(canvas, w)
        canvas.restore()
    }

    private fun drawWorld(c: Canvas, w: Float) {
        maps.forEachIndexed { i, m -> m ?: return@forEachIndexed; dst.set(0f, tops[i], w, tops[i] + mapH(i, w)); c.drawBitmap(m, null, dst, pixels) }
        townGudwin(c, w)
        lumberjack(c, w)
        bushAndRunner(c, w)
        kingMark(c, w)
        // švy mezi mapami pod mraky
        for (i in 1 until 4) seamClouds(c, w, tops[i], i)
    }

    private fun drawMines(c: Canvas, w: Float) {
        val m = mines ?: return
        dst.set(0f, 0f, w, minesH); c.drawBitmap(m, null, dst, pixels)
        val u = w / 150f
        val fx = SILVER_X * w - 13 * u; val fy = SILVER_Y * minesH + 12 * u
        val f = heroFrame(time, 75f)
        hero(c, mining, f, fx, fy, u)
        // jiskry při úderu (snímek 6)
        val loop = time % 750f
        if (loop in 450f..750f) sparks(c, fx + 11 * u, fy - 16 * u, (loop - 450f) / 300f, u, (time / 750f).toInt(), 0xFFFFE27A.toInt())
        // světlo lucerny: ztmavení okolí
        val shade = android.graphics.RadialGradient(fx, fy - 10 * u, 70 * u, intArrayOf(0x00000000, 0x00000000, 0xCC000000.toInt()),
            floatArrayOf(0f, 0.45f, 1f), android.graphics.Shader.TileMode.CLAMP)
        fill.color = 0xFF000000.toInt(); fill.shader = shade; fill.alpha = 255
        c.drawRect(0f, 0f, w, minesH, fill)
        fill.shader = null
    }

    private fun townGudwin(c: Canvas, w: Float) {
        val g = gudwin ?: return
        val x = 0.12f * w; val y = tops[TOWN] + 0.52f * mapH(TOWN, w)
        val s = w * 0.11f
        val hop = if (time in 3000f..3500f) sin((time - 3000f) / 500f * PI.toFloat()) * s * 0.35f else 0f
        dst.set(x - s * 0.32f, y - s * 0.04f, x + s * 0.32f, y + s * 0.04f); c.drawOval(dst, shadow)
        dst.set(x - s / 2, y - s - hop, x + s / 2, y - hop); c.drawBitmap(g, null, dst, pixels)
        if (time in 3150f..4500f) bubble(c, "Vítej, poutníku!", x + s * 0.2f, y - s * 1.15f, w, ((time - 3150f) / 200f).coerceIn(0f, 1f))
    }

    private fun lumberjack(c: Canvas, w: Float) {
        val tx = TREE_X * w; val ty = tops[MEADOW] + TREE_Y * mapH(MEADOW, w)
        val u = w / 205f
        val fx = tx + 0.1f * w; val fy = ty + 0.012f * w
        hero(c, axe, heroFrame(time, 75f), fx, fy, u)
        val loop = time % 750f
        if (loop in 420f..750f) chips(c, fx - 14 * u, fy - 10 * u, (loop - 420f) / 330f, u, (time / 750f).toInt())
    }

    private fun bushAndRunner(c: Canvas, w: Float) {
        val b = bush ?: return
        val bx = BUSH_X * w; val by = tops[MEADOW] + BUSH_Y * mapH(MEADOW, w)
        val bw = w * 0.16f; val bh = bw * b.height / b.width
        val rustle = (if (time in 9000f..9800f) 3.5f else 0f) * sin(time / 35f)
        // makromon: 9800 výskok (320 ms), pak běh doprava
        val r = runner
        val t = time - 9800f
        val s = w * 0.13f
        fun drawRunner() {
            r ?: return
            if (t < 0f || t > 1600f) return
            val x: Float; val y: Float; var rot = 0f
            if (t < 320f) {
                val k = t / 320f
                x = bx + k * w * 0.08f
                y = by + s * 0.3f * (1 - k) - sin(k * PI.toFloat()) * s * 0.6f
                rot = 8f * (1 - k)
            } else {
                val k = (t - 320f) / 1280f
                x = bx + w * 0.08f + k * k * w * 0.9f
                val g = ((t - 320f) % 240f) / 240f
                y = by - abs(sin(g * PI.toFloat())) * s * 0.15f
                rot = -5f * sin(g * 2 * PI.toFloat())
                dst.set(x - s * 0.28f, by - s * 0.035f, x + s * 0.28f, by + s * 0.035f); c.drawOval(dst, shadow)
            }
            c.save(); c.translate(x, y); c.rotate(rot)
            dst.set(-s / 2, -s * 0.94f, s / 2, s * 0.06f); c.drawBitmap(r, null, dst, smooth)
            c.restore()
        }
        if (t in 0f..110f) drawRunner()   // ještě za keřem
        c.save(); c.rotate(rustle, bx, by)
        dst.set(bx - bw / 2, by - bh, bx + bw / 2, by); c.drawBitmap(b, null, dst, pixels)
        c.restore()
        if (t > 110f) drawRunner()
        // lístky z keře
        if (t in 0f..700f) leaves(c, bx, by - bh * 0.6f, t / 700f, w)
    }

    private fun kingMark(c: Canvas, w: Float) {
        if (time !in 17600f..22000f) return
        val x = 0.5f * w; val y = tops[MOUNTAINS] + 0.6f * mapH(MOUNTAINS, w) - w * 0.24f
        val bob = sin(time / 180f) * w * 0.006f
        title.textSize = w * 0.07f; titleShadow.textSize = title.textSize
        title.alpha = 255; titleShadow.alpha = 255
        title.color = 0xFFFFE27A.toInt()
        c.drawText("!", x + w * 0.004f, y + bob + w * 0.004f, titleShadow)
        c.drawText("!", x, y + bob, title)
        title.color = 0xFFFEFAE0.toInt()
    }

    private fun seamClouds(c: Canvas, w: Float, y: Float, seed: Int) {
        val cl = cloud ?: return
        val cw = w * 0.42f; val ch = cw * cl.height / cl.width
        for (row in 0..1) for (n in -1..3) {
            val drift = ((time / (60f + seed * 7f) + n * cw * 0.9f + row * cw * 0.45f + seed * 40f) % (w + cw)) - cw * 0.5f
            val yy = y - ch * 0.8f + row * ch * 0.55f + sin(n + seed.toFloat()) * ch * 0.15f
            dst.set(drift - cw / 2, yy, drift + cw / 2, yy + ch); c.drawBitmap(cl, null, dst, pixels)
        }
    }

    /** Mraky, kterými kamera proletí při přesunu o mapu výš (obrazovkové souřadnice). */
    private fun flyClouds(c: Canvas, w: Float, h: Float) {
        val cl = cloud ?: return
        listOf(4600f to 6200f, 11000f to 12300f, 22200f to 24200f).forEachIndexed { s, (a, b) ->
            if (time !in a..b) return@forEachIndexed
            val k = (time - a) / (b - a)
            fill.alpha = 230
            for (n in 0..4) {
                val cw = w * (0.5f + 0.12f * ((n + s) % 3))
                val ch = cw * cl.height / cl.width
                val x = w * (0.1f + 0.2f * n) + sin((n + s) * 1.7f) * w * 0.12f
                val y = -ch + (h + ch * 2) * ((k * 1.6f - n * 0.15f).coerceIn(0f, 1f))
                dst.set(x - cw / 2, y, x + cw / 2, y + ch); c.drawBitmap(cl, null, dst, fill)
            }
        }
    }

    // ── Postava a částice ───────────────────────────────────────────────────

    private fun heroFrame(t: Float, frameMs: Float) = ((t / frameMs).toInt() % 10)

    private fun hero(c: Canvas, strip: Bitmap?, frame: Int, footX: Float, footY: Float, u: Float) {
        strip ?: return
        src.set(frame * 64, 0, frame * 64 + 64, 40)
        dst.set(footX - 32 * u, footY - 38 * u, footX + 32 * u, footY + 2 * u)
        c.drawBitmap(strip, src, dst, pixels)
    }

    /** Třísky od sekery: deterministické dráhy podle čísla úderu. */
    private fun chips(c: Canvas, x: Float, y: Float, k: Float, u: Float, hit: Int) {
        for (i in 0 until 5) {
            val a = ((hit * 37 + i * 71) % 100) / 100f
            val vx = (0.3f + a) * 30f * u * (if (i % 2 == 0) 1f else 0.6f)
            val vy = -(20f + 25f * ((i * 53 + hit * 11) % 100) / 100f) * u
            fill.color = if (i % 2 == 0) 0xFFC48A4A.toInt() else 0xFF93602C.toInt()
            fill.alpha = ((1f - k) * 255).toInt()
            val px = x + vx * k; val py = y + vy * k + 40f * u * k * k
            c.drawRect(px - u, py - u, px + u, py + u, fill)
        }
    }

    private fun sparks(c: Canvas, x: Float, y: Float, k: Float, u: Float, hit: Int, color: Int) {
        for (i in 0 until 6) {
            val ang = (((hit * 29 + i * 61) % 100) / 100f) * PI.toFloat() + PI.toFloat()
            val sp = (12f + i * 3f) * u
            fill.color = if (i % 3 == 0) 0xFFFFFFFF.toInt() else color
            fill.alpha = ((1f - k) * 255).toInt()
            val px = x + kotlin.math.cos(ang) * sp * k; val py = y + sin(ang) * sp * k + 14f * u * k * k
            c.drawRect(px - u * 0.6f, py - u * 0.6f, px + u * 0.6f, py + u * 0.6f, fill)
        }
    }

    private fun leaves(c: Canvas, x: Float, y: Float, k: Float, w: Float) {
        val u = w / 205f
        for (i in 0 until 8) {
            val vx = ((i * 37) % 100 / 100f - 0.5f) * 70f * u
            val vy = -(25f + (i * 53) % 100 / 100f * 30f) * u
            fill.color = if (i % 2 == 0) 0xFF68A03C.toInt() else 0xFF4F7F2A.toInt()
            fill.alpha = ((1f - k) * 255).toInt()
            val px = x + vx * k; val py = y + vy * k + 60f * u * k * k
            c.drawRect(px - u, py - u, px + u, py + u, fill)
        }
    }

    /** Bublina s textem nad postavou (ve světě). */
    private fun bubble(c: Canvas, s: String, x: Float, y: Float, w: Float, a: Float) {
        dark.textSize = w * 0.035f
        val tw = dark.measureText(s); val pad = w * 0.012f
        val r = RectF(x - tw / 2 - pad, y - dark.textSize - pad, x + tw / 2 + pad, y + pad * 0.6f)
        c.save(); c.scale(0.6f + 0.4f * a, 0.6f + 0.4f * a, x, r.bottom)
        fill.color = 0xFF2E1B0E.toInt(); fill.alpha = 255
        c.drawRoundRect(RectF(r.left - w * 0.003f, r.top - w * 0.003f, r.right + w * 0.003f, r.bottom + w * 0.003f), w * 0.01f, w * 0.01f, fill)
        fill.color = 0xFFFEFAE0.toInt()
        c.drawRoundRect(r, w * 0.008f, w * 0.008f, fill)
        c.drawText(s, r.left + pad, r.bottom - pad * 0.9f, dark)
        c.restore()
    }

    // ── Obrazovkové vrstvy ──────────────────────────────────────────────────

    private fun blackAt(t: Float): Float = when {
        t < 700f -> 1f - t / 700f
        t in 12400f..12900f -> (t - 12400f) / 500f
        t in 12900f..13300f -> 1f - (t - 12900f) / 400f
        t in 15600f..16000f -> (t - 15600f) / 400f
        t in 16000f..16400f -> 1f - (t - 16000f) / 400f
        else -> 0f
    }

    private class Line(val from: Float, val to: Float, val text: String)

    private val lines = listOf(
        Line(500f, 4300f, "Daleko za tvým telefonem leží Makrosvět."),
        Line(5600f, 8800f, "Na louce se poctivě pracuje…"),
        Line(9000f, 11200f, "…a v každém keři se může něco skrývat."),
        Line(13300f, 15600f, "Hluboko v dolech se kope vzácná ruda."),
        Line(16500f, 17900f, "Na horách vládne Král Mlsák."),
        Line(23000f, 25200f, "A za průsmykem čeká nový kraj…")
    )

    private fun narration(c: Canvas, w: Float, h: Float) {
        val l = lines.firstOrNull { time in it.from..it.to } ?: return
        val a = min(((time - l.from) / 300f), ((l.to - time) / 300f)).coerceIn(0f, 1f)
        val shown = l.text.take(((time - l.from) / CHAR_MS).toInt().coerceIn(0, l.text.length))
        text.textSize = w * 0.058f
        val pad = w * 0.05f
        val boxTop = h - w * 0.36f
        fill.color = 0xFF0A0F06.toInt(); fill.alpha = (a * 190).toInt()
        c.drawRoundRect(RectF(pad * 0.6f, boxTop, w - pad * 0.6f, boxTop + w * 0.22f), w * 0.03f, w * 0.03f, fill)
        text.alpha = (a * 255).toInt()
        layout(shown, text, (w - pad * 2).toInt()).let { lay ->
            c.save(); c.translate(pad, boxTop + (w * 0.22f - lay.height) / 2f); lay.draw(c); c.restore()
        }
    }

    private fun kingDialog(c: Canvas, w: Float, h: Float) {
        if (time !in 18000f..21900f) return
        val a = min((time - 18000f) / 250f, (21900f - time) / 250f).coerceIn(0f, 1f)
        val msg = "Kdo se opovažuje vstoupit do mého pohoří? … Á, nový trenér! Ukaž mi, co v tobě je."
        val shown = msg.take(((time - 18300f) / CHAR_MS).toInt().coerceIn(0, msg.length))
        val m = w * 0.04f
        val boxH = w * 0.42f
        val top = h - boxH - w * 0.12f + (1f - a) * w * 0.2f
        wood.setBounds(m.toInt(), top.toInt(), (w - m).toInt(), (top + boxH).toInt())
        wood.alpha = (a * 255).toInt()
        wood.draw(c)
        // podobizna vlevo, přečnívá nahoru
        val ph = boxH * 1.25f; val pw = ph / 2f
        king?.let { d ->
            d.setBounds((m + w * 0.02f).toInt(), (top + boxH - ph - w * 0.02f).toInt(), (m + w * 0.02f + pw).toInt(), (top + boxH - w * 0.02f).toInt())
            d.alpha = (a * 255).toInt(); d.draw(c)
        }
        val tx = m + w * 0.04f + pw
        dark.alpha = (a * 255).toInt()
        dark.textSize = w * 0.045f
        val name = Paint(dark).apply { color = 0xFFBC6C25.toInt(); alpha = (a * 255).toInt() }
        c.drawText("KRÁL MLSÁK", tx, top + w * 0.08f, name)
        layout(shown, dark, (w - m - w * 0.05f - tx).toInt()).let { lay ->
            c.save(); c.translate(tx, top + w * 0.1f); lay.draw(c); c.restore()
        }
    }

    private fun titleCard(c: Canvas, w: Float, h: Float) {
        if (time < 25200f) return
        val t = time - 25200f
        val drop = smooth((t / 650f).coerceIn(0f, 1f))
        val bounce = if (t in 650f..1000f) sin((t - 650f) / 350f * PI.toFloat()) * w * 0.02f else 0f
        val size = w / 4.6f
        title.textSize = size; titleShadow.textSize = size
        val y = -size + (h * 0.42f + size) * drop - bounce
        val sh = max(3f, size / 12f)
        title.alpha = 255; titleShadow.alpha = 255
        c.drawText("MAKROMON", w / 2 + sh, y + sh, titleShadow)
        c.drawText("MAKROMON", w / 2, y, title)
        val lineA = ((t - 650f) / 400f).coerceIn(0f, 1f)
        fill.color = 0xFFE9B072.toInt(); fill.alpha = 255
        val lw = title.measureText("MAKROMON") * 0.6f * lineA
        c.drawRect(w / 2 - lw / 2, y + sh * 3, w / 2 + lw / 2, y + sh * 5, fill)
        val subA = ((t - 1000f) / 500f).coerceIn(0f, 1f)
        if (subA > 0f) {
            val sub = Paint(title).apply { textSize = w * 0.06f; alpha = (subA * 255).toInt() }
            c.drawText("Tvoje dobrodružství začíná", w / 2, y + size * 0.55f, sub)
        }
        // jiskřičky kolem nápisu
        for (i in 0 until 10) {
            val k = ((t / 900f + i * 0.13f) % 1f)
            val sx = w * (0.1f + 0.8f * ((i * 37) % 100) / 100f)
            val sy = y - size * 0.9f + size * 1.3f * ((i * 53) % 100) / 100f
            fill.color = 0xFFFFE27A.toInt(); fill.alpha = (sin(k * PI.toFloat()) * 255).toInt().coerceIn(0, 255)
            val r = w * 0.008f * sin(k * PI.toFloat())
            c.drawRect(sx - r, sy - r * 0.3f, sx + r, sy + r * 0.3f, fill)
            c.drawRect(sx - r * 0.3f, sy - r, sx + r * 0.3f, sy + r, fill)
        }
    }

    private fun skipButton(c: Canvas, w: Float) {
        if (time > endAt) return
        text.textSize = w * 0.045f; text.alpha = 230
        val label = "Přeskočit >"
        val tw = text.measureText(label)
        val pad = w * 0.03f
        val top = w * 0.12f
        skipRect.set(w - tw - pad * 3, top, w - pad, top + text.textSize + pad * 1.4f)
        fill.color = 0xFF0A0F06.toInt(); fill.alpha = 150
        c.drawRoundRect(skipRect, skipRect.height() / 2, skipRect.height() / 2, fill)
        c.drawText(label, skipRect.left + pad, skipRect.bottom - pad * 0.95f, text)
    }

    private fun tilePath(w: Float, h: Float, p: Float) {
        tiles.rewind()
        val cell = w / 9f
        val rows = ceil(h / cell).toInt()
        val cx = 4f; val cy = (rows - 1) / 2f; val maxD = cx + cy
        for (r in 0 until rows) for (col in 0 until 9) {
            val d = (abs(col - cx) + abs(r - cy)) / maxD
            val t = ((p - d * 0.55f) / 0.45f).coerceIn(0f, 1f)
            val size = 1f - (1f - (1f - t) * (1f - t))
            if (size <= 0f) continue
            val half = cell * size / 2f + if (size >= 1f) 0.5f else 0f
            val x = col * cell + cell / 2f; val y = r * cell + cell / 2f
            tiles.addRect(x - half, y - half, x + half, y + half, Path.Direction.CW)
        }
    }

    private fun layout(s: String, p: TextPaint, width: Int): StaticLayout =
        StaticLayout.Builder.obtain(s, 0, s.length, p, max(1, width)).setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 0.95f).build()

    private fun smooth(k: Float) = k * k * (3 - 2 * k)

    companion object {
        private const val SKY = 0
        private const val MOUNTAINS = 1
        private const val MEADOW = 2
        private const val TOWN = 3

        /** Strom u louky (pata kmene) a keř s makromonem – podíl rozměrů meadow.png (688 × 1536). */
        private const val TREE_X = 255f / 688f
        private const val TREE_Y = 668f / 1536f
        private const val BUSH_X = 0.62f
        private const val BUSH_Y = 0.47f
        /** Stříbrná žíla ve Starém dole (GatherLayout: 100, 338 v 150 × 540). */
        private const val SILVER_X = 100f / 150f
        private const val SILVER_Y = 338f / 540f

        private const val CHAR_MS = 32f
        private const val END_MS = 27600f
        private const val DISSOLVE_MS = 800f
        private const val TOTAL_MS = END_MS + DISSOLVE_MS + 200f

        private const val PREF = "intro_seen"

        fun shouldPlay(ctx: Context) = !ctx.getSharedPreferences("GamePrefs", Context.MODE_PRIVATE).getBoolean(PREF, false)

        /** Přehraje úvod přes [root] (mapa už je pod ním) a na konci ho odebere. */
        fun play(root: ViewGroup) {
            root.context.getSharedPreferences("GamePrefs", Context.MODE_PRIVATE).edit().putBoolean(PREF, true).apply()
            val v = IntroFlyoverView(root.context)
            v.onDone = { root.removeView(v) }
            root.addView(v, ViewGroup.LayoutParams(-1, -1))
        }
    }
}
