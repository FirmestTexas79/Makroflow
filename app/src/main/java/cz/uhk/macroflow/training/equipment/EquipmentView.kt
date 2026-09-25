package cz.uhk.macroflow.training.equipment

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * Animovaný obrázek náčiní u zápisu série (docs/adr/0028): jednoručky rostou s vahou, na osu
 * a stroj se nakládají kotouče, na lanku kladky přibývají bloky závaží. Čistě 2D (Canvas) –
 * plynulé i na slabším telefonu. Co kreslit počítá [RigMath]; tady je jen kreslení a animace.
 */
class EquipmentView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {

    // ── Barvy (brand) ───────────────────────────────────────────────────────
    private val cDark = Color.parseColor("#283618")
    private val cDarkLight = Color.parseColor("#455A2D")
    private val cPrimary = Color.parseColor("#606C38")
    private val cWarm = Color.parseColor("#E9B072")
    private val cDeep = Color.parseColor("#BC6C25")
    private val cCream = Color.parseColor("#FEFAE0")
    private val cSteel = Color.parseColor("#B9BBAE")
    private val cSteelDark = Color.parseColor("#7D8072")

    private fun plateColor(kg: Double) = when {
        kg >= 20.0 -> cDark
        kg >= 10.0 -> cPrimary
        kg >= 5.0 -> cDeep
        kg >= 2.5 -> cWarm
        else -> cSteel
    }
    /** Průměr a tloušťka kotouče vůči výšce obrázku. */
    private fun plateDiameter(kg: Double) = when {
        kg >= 20.0 -> 0.80f; kg >= 10.0 -> 0.62f; kg >= 5.0 -> 0.47f; kg >= 2.5 -> 0.37f; else -> 0.29f
    }
    private fun plateThickness(kg: Double) = when {
        kg >= 20.0 -> 0.080f; kg >= 10.0 -> 0.062f; kg >= 5.0 -> 0.048f; kg >= 2.5 -> 0.038f; else -> 0.030f
    }

    // ── Stav ────────────────────────────────────────────────────────────────
    private var rig: Rig? = null
    private var weight = 0.0

    private class PlateSprite(val kg: Double, var appear: Float, var target: Float)
    private val sprites = mutableListOf<PlateSprite>()

    private var sizeNow = 0f; private var sizeTarget = 0f
    private var blocksNow = 0f; private var blocksTarget = 0f
    private var extraNow = 0f; private var extraTarget = 0f
    private var fade = 1f
    private var bounceY = 0f; private var bounceVel = 0f
    private var lastFrame = 0L

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER; typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
    }
    private val r = RectF()
    private val path = Path()

    /** Nastaví náčiní a váhu; změna se plynule animuje. */
    fun set(newRig: Rig?, newWeight: Double) {
        val rigChanged = newRig != rig
        val oldWeight = weight
        val weightChanged = abs(newWeight - oldWeight) > 1e-6
        rig = newRig; weight = newWeight
        if (newRig == null) { invalidate(); return }

        sizeTarget = RigMath.size(newRig, newWeight).toFloat()
        val st = RigMath.stack(newWeight)
        blocksTarget = st.blocks.toFloat(); extraTarget = if (st.extraKg > 0) 1f else 0f

        val plates = RigMath.plates(newRig, newWeight).perSide
        if (rigChanged) {
            sprites.clear(); plates.forEach { sprites += PlateSprite(it, 0f, 1f) }
            sizeNow = sizeTarget * 0.6f; blocksNow = 0f; extraNow = 0f; fade = 0f
        } else {
            // společný začátek zůstává, zbytek odjede a nové kotouče najedou
            val active = sprites.filter { it.target > 0f }
            var keep = 0
            while (keep < active.size && keep < plates.size && active[keep].kg == plates[keep]) keep++
            active.drop(keep).forEach { it.target = 0f }
            plates.drop(keep).forEach { sprites += PlateSprite(it, 0f, 1f) }
        }
        if (weightChanged && !rigChanged) bounceVel += if (newWeight > oldWeight) -height * 1.2f else -height * 0.6f
        contentDescription = RigMath.summary(newRig, newWeight)
        lastFrame = 0L
        postInvalidateOnAnimation()
    }

    // ── Animace ─────────────────────────────────────────────────────────────

    private fun step(): Boolean {
        val now = System.nanoTime()
        val dt = if (lastFrame == 0L) 0.016f else min((now - lastFrame) / 1e9f, 0.05f)
        lastFrame = now
        val k = 1f - exp(-dt * 11f)
        var moving = false
        fun approach(cur: Float, target: Float): Float {
            val v = cur + (target - cur) * k
            if (abs(target - v) > 0.002f) moving = true
            return if (abs(target - v) <= 0.002f) target else v
        }
        sizeNow = approach(sizeNow, sizeTarget)
        blocksNow = approach(blocksNow, blocksTarget)
        extraNow = approach(extraNow, extraTarget)
        fade = approach(fade, 1f)
        sprites.forEach { it.appear = approach(it.appear, it.target) }
        sprites.removeAll { it.target == 0f && it.appear <= 0.01f }
        // pružina „žuchnutí“ po změně váhy
        val acc = -420f * bounceY - 22f * bounceVel
        bounceVel += acc * dt; bounceY += bounceVel * dt
        if (abs(bounceY) > 0.3f || abs(bounceVel) > 3f) moving = true else { bounceY = 0f; bounceVel = 0f }
        return moving
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val rg = rig ?: return
        val animating = step()
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        canvas.save()
        val sc = 0.94f + 0.06f * fade
        canvas.scale(sc, sc, w / 2, h * 0.9f)
        canvas.translate(0f, bounceY.coerceIn(-h * 0.08f, h * 0.08f))
        val alpha = (255 * fade).toInt().coerceIn(0, 255)

        // stín na zemi
        p.shader = null; p.style = Paint.Style.FILL; p.color = cDark; p.alpha = (28 * fade).toInt()
        r.set(w * 0.12f, h * 0.88f, w * 0.88f, h * 0.97f); canvas.drawOval(r, p)

        when (rg) {
            Rig.DUMBBELL_PAIR -> {
                drawDumbbell(canvas, w * 0.60f, h * 0.44f, h * 0.82f, alpha, back = true)
                drawDumbbell(canvas, w * 0.42f, h * 0.58f, h, alpha, back = false)
            }
            Rig.DUMBBELL_SINGLE -> drawDumbbell(canvas, w * 0.5f, h * 0.54f, h * 1.05f, alpha, back = false)
            Rig.STRAIGHT_BAR, Rig.EZ_BAR -> drawFixedBar(canvas, w, h, rg == Rig.EZ_BAR, alpha)
            Rig.OLYMPIC_BAR -> drawOlympic(canvas, w, h, alpha)
            Rig.MACHINE_BOTH, Rig.MACHINE_ONE -> drawMachine(canvas, w, h, rg == Rig.MACHINE_ONE, alpha)
            Rig.STACK -> drawStack(canvas, w, h, alpha)
        }
        canvas.restore()
        if (animating) postInvalidateOnAnimation()
    }

    // ── Kotouče ─────────────────────────────────────────────────────────────

    /**
     * Kotouče od [startX] směrem [dir] (−1 doleva, +1 doprava), střed na [cy]. [scale] zmenší
     * kotouče (stroj), [maxRun] = místo na rameni – když se nevejdou, ztenčí se.
     */
    private fun drawPlates(canvas: Canvas, startX: Float, cy: Float, dir: Int, h: Float, scale: Float, maxRun: Float, alpha: Int) {
        val need = sprites.sumOf { (plateThickness(it.kg) * h * scale * it.appear).toDouble() }.toFloat()
        val squeeze = if (need > maxRun && need > 0f) maxRun / need else 1f
        var x = startX
        for (s in sprites) {
            if (s.appear <= 0.01f) continue
            val t = plateThickness(s.kg) * h * scale * squeeze
            val d = plateDiameter(s.kg) * h * scale
            val slide = (1f - s.appear) * h * 0.5f * dir
            val left = if (dir < 0) x - t + slide else x + slide
            val a = (alpha * s.appear).toInt()
            val col = plateColor(s.kg)
            p.style = Paint.Style.FILL
            p.shader = LinearGradient(0f, cy - d / 2, 0f, cy + d / 2,
                intArrayOf(lighten(col, 0.28f), col, darken(col, 0.25f)), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
            p.alpha = a
            r.set(left, cy - d / 2, left + t, cy + d / 2)
            canvas.drawRoundRect(r, t * 0.35f, t * 0.35f, p)
            p.shader = null
            // okraj a střed kotouče
            p.style = Paint.Style.STROKE; p.strokeWidth = max(1f, t * 0.1f); p.color = darken(col, 0.4f); p.alpha = a
            canvas.drawRoundRect(r, t * 0.35f, t * 0.35f, p)
            p.style = Paint.Style.FILL; p.color = lighten(col, 0.35f); p.alpha = (a * 0.55f).toInt()
            r.set(left + t * 0.2f, cy - d * 0.42f, left + t * 0.45f, cy + d * 0.42f)
            canvas.drawRoundRect(r, t * 0.1f, t * 0.1f, p)
            // hodnota na kotouči (u silnějších)
            if (t > h * 0.045f) {
                canvas.save()
                canvas.rotate(-90f, left + t / 2, cy - d * 0.3f)
                text.textSize = min(t * 0.62f, h * 0.06f)
                text.color = if (col == cWarm || col == cSteel) cDark else cCream; text.alpha = a
                canvas.drawText(RigMath.kg(s.kg), left + t / 2, cy - d * 0.3f + text.textSize * 0.35f, text)
                canvas.restore()
            }
            x += dir * t * s.appear
        }
    }

    // ── Olympijská osa ──────────────────────────────────────────────────────

    private fun drawOlympic(canvas: Canvas, w: Float, h: Float, alpha: Int) {
        val cy = h * 0.52f
        val collarL = w * 0.30f; val collarR = w * 0.70f
        drawBarShaft(canvas, collarL, collarR, cy, h * 0.032f, alpha, knurl = true)
        // objímky (rukávy) až ke krajům
        drawSleeve(canvas, w * 0.02f, collarL, cy, h * 0.055f, alpha)
        drawSleeve(canvas, collarR, w * 0.98f, cy, h * 0.055f, alpha)
        drawPlates(canvas, collarL - h * 0.02f, cy, -1, h, 1f, collarL - w * 0.04f, alpha)
        drawPlates(canvas, collarR + h * 0.02f, cy, +1, h, 1f, w * 0.96f - collarR, alpha)
        // límce osy
        p.shader = null; p.style = Paint.Style.FILL; p.color = cSteelDark; p.alpha = alpha
        r.set(collarL - h * 0.02f, cy - h * 0.07f, collarL + h * 0.012f, cy + h * 0.07f); canvas.drawRoundRect(r, 4f, 4f, p)
        r.set(collarR - h * 0.012f, cy - h * 0.07f, collarR + h * 0.02f, cy + h * 0.07f); canvas.drawRoundRect(r, 4f, 4f, p)
    }

    private fun drawBarShaft(canvas: Canvas, x1: Float, x2: Float, cy: Float, thick: Float, alpha: Int, knurl: Boolean) {
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, cy - thick / 2, 0f, cy + thick / 2,
            intArrayOf(Color.WHITE, cSteel, cSteelDark), floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
        p.alpha = alpha
        r.set(x1, cy - thick / 2, x2, cy + thick / 2); canvas.drawRoundRect(r, thick / 2, thick / 2, p)
        p.shader = null
        if (knurl) {
            p.color = cSteelDark; p.alpha = (alpha * 0.5f).toInt(); p.strokeWidth = 1.2f
            var x = x1 + (x2 - x1) * 0.18f
            while (x < x2 - (x2 - x1) * 0.18f) {
                if (abs(x - (x1 + x2) / 2) > (x2 - x1) * 0.08f) canvas.drawLine(x, cy - thick / 2, x + thick * 0.4f, cy + thick / 2, p)
                x += thick * 0.45f
            }
        }
    }

    private fun drawSleeve(canvas: Canvas, x1: Float, x2: Float, cy: Float, thick: Float, alpha: Int) {
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, cy - thick / 2, 0f, cy + thick / 2,
            intArrayOf(Color.WHITE, cSteel, cSteelDark), floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
        p.alpha = alpha
        r.set(x1, cy - thick / 2, x2, cy + thick / 2); canvas.drawRoundRect(r, thick * 0.3f, thick * 0.3f, p)
        p.shader = null
    }

    // ── Pevná činka (rovná / EZ) ────────────────────────────────────────────

    private fun drawFixedBar(canvas: Canvas, w: Float, h: Float, ez: Boolean, alpha: Int) {
        val cy = h * 0.54f
        val headH = h * (0.30f + 0.44f * sizeNow)
        val headW = h * (0.10f + 0.12f * sizeNow)
        val inner = w * 0.22f
        val thick = h * 0.036f
        if (ez) {
            // lomená „S“ část uprostřed
            val a = h * 0.055f
            val xs = floatArrayOf(0.22f, 0.33f, 0.39f, 0.45f, 0.55f, 0.61f, 0.67f, 0.78f)
            val ys = floatArrayOf(0f, 0f, -a, a, a, -a, 0f, 0f)
            path.reset(); path.moveTo(w * xs[0], cy + ys[0])
            for (i in 1 until xs.size) path.lineTo(w * xs[i], cy + ys[i])
            p.shader = LinearGradient(0f, cy - a, 0f, cy + a, intArrayOf(Color.WHITE, cSteel, cSteelDark), null, Shader.TileMode.CLAMP)
            p.style = Paint.Style.STROKE; p.strokeWidth = thick; p.strokeJoin = Paint.Join.ROUND; p.strokeCap = Paint.Cap.ROUND
            p.alpha = alpha
            canvas.drawPath(path, p)
            p.shader = null; p.style = Paint.Style.FILL
        } else {
            drawBarShaft(canvas, inner, w - inner, cy, thick, alpha, knurl = true)
        }
        // pevné konce
        drawFixedHead(canvas, inner - headW, cy, headW, headH, alpha)
        drawFixedHead(canvas, w - inner, cy, headW, headH, alpha)
        // krátké přesahy osy za konci
        drawSleeve(canvas, inner - headW - h * 0.04f, inner - headW, cy, thick * 1.1f, alpha)
        drawSleeve(canvas, w - inner + headW, w - inner + headW + h * 0.04f, cy, thick * 1.1f, alpha)
        // hodnota na pravém konci
        text.textSize = min(headW * 0.5f, h * 0.075f); text.color = cCream; text.alpha = alpha
        canvas.save(); canvas.rotate(-90f, w - inner + headW / 2, cy)
        canvas.drawText(RigMath.kg(weight), w - inner + headW / 2, cy + text.textSize * 0.35f, text)
        canvas.restore()
    }

    private fun drawFixedHead(canvas: Canvas, x: Float, cy: Float, hw: Float, hh: Float, alpha: Int) {
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(x, 0f, x + hw, 0f, intArrayOf(cDarkLight, cDark, darken(cDark, 0.3f)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        p.alpha = alpha
        r.set(x, cy - hh / 2, x + hw, cy + hh / 2); canvas.drawRoundRect(r, hw * 0.35f, hw * 0.35f, p)
        p.shader = null
        p.style = Paint.Style.STROKE; p.strokeWidth = max(1.5f, hw * 0.06f); p.color = cDeep; p.alpha = alpha
        r.inset(hw * 0.16f, hh * 0.06f); canvas.drawRoundRect(r, hw * 0.25f, hw * 0.25f, p)
        p.style = Paint.Style.FILL
    }

    // ── Jednoručka ──────────────────────────────────────────────────────────

    private fun drawDumbbell(canvas: Canvas, cx: Float, cy: Float, h: Float, alpha: Int, back: Boolean) {
        val s = sizeNow
        val headH = h * (0.24f + 0.34f * s)
        val headW = h * (0.08f + 0.13f * s)
        val handle = h * 0.20f
        val a = if (back) (alpha * 0.55f).toInt() else alpha
        drawBarShaft(canvas, cx - handle / 2 - headW * 0.2f, cx + handle / 2 + headW * 0.2f, cy, h * 0.045f, a, knurl = true)
        for (dir in intArrayOf(-1, 1)) {
            val x = if (dir < 0) cx - handle / 2 - headW else cx + handle / 2
            // šestihranná hlava ze strany: dvě plochy
            p.style = Paint.Style.FILL
            p.shader = LinearGradient(0f, cy - headH / 2, 0f, cy + headH / 2,
                intArrayOf(cDarkLight, cDark, darken(cDark, 0.35f)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            p.alpha = a
            r.set(x, cy - headH / 2, x + headW, cy + headH / 2); canvas.drawRoundRect(r, headW * 0.22f, headW * 0.22f, p)
            p.shader = null
            p.color = lighten(cDark, 0.25f); p.alpha = (a * 0.6f).toInt()
            r.set(x, cy - headH * 0.18f, x + headW, cy + headH * 0.18f); canvas.drawRect(r, p)
            // kovový lem u rukojeti
            p.color = cSteel; p.alpha = a
            val lx = if (dir < 0) x + headW - headW * 0.12f else x
            r.set(lx, cy - headH * 0.34f, lx + headW * 0.12f, cy + headH * 0.34f); canvas.drawRect(r, p)
        }
        if (!back && headW > h * 0.1f) {
            text.textSize = min(headW * 0.55f, h * 0.08f); text.color = cCream; text.alpha = a
            val x = cx + handle / 2 + headW / 2
            canvas.save(); canvas.rotate(-90f, x, cy)
            canvas.drawText(RigMath.kg(weight), x, cy + text.textSize * 0.35f, text)
            canvas.restore()
        }
    }

    // ── Stroj na kotouče ────────────────────────────────────────────────────

    private fun drawMachine(canvas: Canvas, w: Float, h: Float, oneSide: Boolean, alpha: Int) {
        p.shader = null; p.style = Paint.Style.FILL
        // základna a sloup
        p.color = cDark; p.alpha = alpha
        r.set(w * 0.16f, h * 0.84f, w * 0.84f, h * 0.89f); canvas.drawRoundRect(r, h * 0.02f, h * 0.02f, p)
        r.set(w * 0.47f, h * 0.10f, w * 0.53f, h * 0.86f); canvas.drawRoundRect(r, h * 0.02f, h * 0.02f, p)
        // opěrka
        p.color = cPrimary; p.alpha = alpha
        r.set(w * 0.425f, h * 0.06f, w * 0.575f, h * 0.44f); canvas.drawRoundRect(r, h * 0.05f, h * 0.05f, p)
        p.color = lighten(cPrimary, 0.25f); p.alpha = (alpha * 0.7f).toInt()
        r.set(w * 0.44f, h * 0.08f, w * 0.47f, h * 0.42f); canvas.drawRoundRect(r, h * 0.02f, h * 0.02f, p)
        // rameno stroje a madla
        val armY = h * 0.60f
        p.color = darken(cDark, 0.1f); p.alpha = alpha
        r.set(w * 0.22f, armY - h * 0.028f, w * 0.78f, armY + h * 0.028f); canvas.drawRoundRect(r, h * 0.028f, h * 0.028f, p)
        for (x in floatArrayOf(w * 0.31f, w * 0.69f)) {
            r.set(x - h * 0.018f, h * 0.28f, x + h * 0.018f, armY); canvas.drawRoundRect(r, h * 0.018f, h * 0.018f, p)
            p.color = cDeep; p.alpha = alpha
            r.set(x - h * 0.04f, h * 0.22f, x + h * 0.04f, h * 0.31f); canvas.drawRoundRect(r, h * 0.03f, h * 0.03f, p)
            p.color = darken(cDark, 0.1f); p.alpha = alpha
        }
        // trny na kotouče
        drawSleeve(canvas, w * 0.03f, w * 0.23f, armY, h * 0.05f, alpha)
        drawSleeve(canvas, w * 0.77f, w * 0.97f, armY, h * 0.05f, alpha)
        val scale = 0.74f
        if (!oneSide) drawPlates(canvas, w * 0.22f, armY, -1, h, scale, w * 0.18f, alpha)
        drawPlates(canvas, w * 0.78f, armY, +1, h, scale, w * 0.18f, alpha)
        if (oneSide) {
            text.textSize = h * 0.07f; text.color = cDark; text.alpha = (alpha * 0.45f).toInt()
            canvas.drawText("prázdná", w * 0.13f, armY - h * 0.1f, text)
        }
    }

    // ── Blok závaží na lanku ────────────────────────────────────────────────

    private fun drawStack(canvas: Canvas, w: Float, h: Float, alpha: Int) {
        val px = w * 0.5f; val py = h * 0.12f; val pr = h * 0.075f
        // úchyt kladky a kladka
        p.shader = null; p.style = Paint.Style.FILL; p.color = cDark; p.alpha = alpha
        r.set(px - pr * 0.35f, 0f, px + pr * 0.35f, py); canvas.drawRect(r, p)
        p.color = cSteelDark; canvas.drawCircle(px, py, pr, p)
        p.color = cSteel; canvas.drawCircle(px, py, pr * 0.55f, p)
        p.color = cDark; canvas.drawCircle(px, py, pr * 0.18f, p)
        // lanko: zleva přes kladku a dolů
        p.style = Paint.Style.STROKE; p.strokeWidth = h * 0.014f; p.color = cDark; p.alpha = alpha; p.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(w * 0.08f, py - pr, px, py - pr, p)
        val hookY = h * 0.27f
        canvas.drawLine(px + pr, py, px + pr, hookY, p)
        p.style = Paint.Style.FILL
        // závaží pod háčkem – přibývají dolů
        val n = blocksNow
        val maxH = h * 0.58f
        val bh = if (n > 0f) min(h * 0.075f, maxH / max(n, 1f)) else h * 0.075f
        val bw = w * 0.30f
        val cx = px + pr
        var y = hookY + h * 0.02f
        // přídavné malé závaží nahoře
        if (extraNow > 0.01f) {
            val eh = h * 0.035f * extraNow
            p.color = cWarm; p.alpha = (alpha * extraNow).toInt()
            r.set(cx - bw * 0.3f, y, cx + bw * 0.3f, y + eh); canvas.drawRoundRect(r, eh * 0.4f, eh * 0.4f, p)
            y += eh + h * 0.006f
        }
        val full = n.toInt()
        val frac = n - full
        for (i in 0 until full + if (frac > 0.01f) 1 else 0) {
            val part = if (i == full) frac else 1f
            val top = y + i * bh
            val col = if (i % 2 == 0) cDark else cDarkLight
            p.shader = LinearGradient(cx - bw / 2, 0f, cx + bw / 2, 0f, intArrayOf(lighten(col, 0.2f), col, darken(col, 0.3f)), null, Shader.TileMode.CLAMP)
            p.alpha = (alpha * part).toInt()
            r.set(cx - bw / 2 * (0.7f + 0.3f * part), top + bh * 0.06f, cx + bw / 2 * (0.7f + 0.3f * part), top + bh * 0.94f)
            canvas.drawRoundRect(r, bh * 0.25f, bh * 0.25f, p)
            p.shader = null
        }
        // vodicí tyč skrz bloky a kolík u posledního
        if (n > 0.05f) {
            val bottom = y + n * bh
            p.color = cSteel; p.alpha = (alpha * 0.8f).toInt()
            r.set(cx - h * 0.008f, hookY, cx + h * 0.008f, bottom); canvas.drawRect(r, p)
            p.color = cDeep; p.alpha = alpha
            canvas.drawCircle(cx + bw / 2 + h * 0.02f, bottom - bh / 2, h * 0.022f, p)
            r.set(cx, bottom - bh / 2 - h * 0.008f, cx + bw / 2 + h * 0.02f, bottom - bh / 2 + h * 0.008f); canvas.drawRect(r, p)
            // počet bloků vedle
            text.textSize = h * 0.075f; text.color = cDark; text.alpha = alpha
            text.textAlign = Paint.Align.LEFT
            canvas.drawText("${blocksTarget.toInt()} ×", cx + bw / 2 + h * 0.07f, y + min(n, 3f) * bh / 2 + text.textSize * 0.35f, text)
            text.textAlign = Paint.Align.CENTER
        } else {
            // prázdný háček
            p.color = cSteelDark; p.alpha = alpha
            canvas.drawCircle(cx, hookY + h * 0.02f, h * 0.02f, p)
        }
    }

    // ── Barvy ───────────────────────────────────────────────────────────────

    private fun lighten(c: Int, f: Float) = Color.rgb(
        (Color.red(c) + (255 - Color.red(c)) * f).toInt(),
        (Color.green(c) + (255 - Color.green(c)) * f).toInt(),
        (Color.blue(c) + (255 - Color.blue(c)) * f).toInt()
    )

    private fun darken(c: Int, f: Float) = Color.rgb(
        (Color.red(c) * (1 - f)).toInt(), (Color.green(c) * (1 - f)).toInt(), (Color.blue(c) * (1 - f)).toInt()
    )
}
