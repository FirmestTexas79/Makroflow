package cz.uhk.macroflow.pokemon.skills.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.View
import androidx.core.content.res.ResourcesCompat
import cz.uhk.macroflow.R
import cz.uhk.macroflow.pokemon.skills.Berry
import cz.uhk.macroflow.pokemon.skills.Garden
import cz.uhk.macroflow.pokemon.skills.MeadowLayout
import cz.uhk.macroflow.pokemon.skills.SkillArt
import cz.uhk.macroflow.pokemon.ui.StepBarArt
import kotlin.math.sin

/**
 * Zahrada na louce (docs/adr/0034): cesta ve tvaru plus, čtyři záhony (dva zničené, dokud
 * se neodemknou), rostliny podle fáze růstu, nad rostoucí bobulí dřevěná cedulka
 * s přesýpacími hodinami a zbývajícím časem, nad hotovou dřevěný čtvereček s fajfkou.
 *
 * View pokrývá [MeadowLayout.GARDEN] rozšířený o [TOP_MARGIN] nahoru (místo na cedulky);
 * [scale] = pixely světa na pixel obrázku mapy.
 */
class GardenView(context: Context, private val scale: Float) : View(context) {

    companion object {
        /** Místo nad horními záhony na cedulky (px obrázku). */
        const val TOP_MARGIN = 34
    }

    var plots: List<Garden.Plot> = List(Garden.PLOTS) { Garden.Plot(null, 0) }
        set(v) { field = v; invalidate() }
    var plotsOpen = 2
        set(v) { field = v; invalidate() }
    var speedup = 0.0
        set(v) { field = v; invalidate() }

    private val g = MeadowLayout.GARDEN
    private val px = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
    private val fill = Paint().apply { isAntiAlias = false }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFEFAE0.toInt()
        typeface = ResourcesCompat.getFont(context, R.font.jersey_15)
        textAlign = Paint.Align.CENTER
    }
    private val bitmaps = HashMap<String, Bitmap>()

    private fun bmp(key: String, w: Int, h: Int, make: () -> IntArray): Bitmap =
        bitmaps.getOrPut(key) { Bitmap.createBitmap(make(), w, h, Bitmap.Config.ARGB_8888) }

    /** Pixely obrázku mapy → souřadnice view. */
    private fun vx(ix: Float) = (ix - g.x0) * scale
    private fun vy(iy: Float) = (iy - g.y0 + TOP_MARGIN) * scale

    val viewWidth: Int get() = (g.w * scale).toInt()
    val viewHeight: Int get() = ((g.h + TOP_MARGIN) * scale).toInt()

    private fun nowSec() = System.currentTimeMillis() / 1000

    override fun onDraw(canvas: Canvas) {
        drawPath(canvas)
        val now = nowSec()
        var anyGrowing = false
        var anyReady = false
        MeadowLayout.PLOTS.forEachIndexed { i, r ->
            val open = Garden.isOpen(i, plotsOpen)
            val dst = RectF(vx(r.x0.toFloat()), vy(r.y0.toFloat()), vx(r.x1.toFloat()), vy(r.y1.toFloat()))
            canvas.drawBitmap(bmp("plot$open", SkillArt.PLOT_W, SkillArt.PLOT_H) { SkillArt.plot(!open) }, null, dst, px)
            val plot = plots.getOrNull(i) ?: return@forEachIndexed
            val berry = plot.berry
            if (!open || berry == null) return@forEachIndexed
            val f = Garden.fraction(plot, now, speedup)
            val ready = Garden.isReady(plot, now, speedup)
            val stage = when { ready -> 2; f < 0.34f -> 0; else -> 1 }
            // rostlina: art pixel stejný jako u záhonu
            val u = dst.width() / SkillArt.PLOT_W
            val pw = SkillArt.PLANT * u
            val pl = dst.centerX() - pw / 2; val pt = dst.centerY() - pw * 0.62f
            canvas.drawBitmap(bmp("plant${berry.id}$stage", SkillArt.PLANT, SkillArt.PLANT) { SkillArt.plant(berry, stage) },
                null, RectF(pl, pt, pl + pw, pt + pw), px)
            if (ready) { anyReady = true; drawCheck(canvas, dst) }
            else { anyGrowing = true; drawTimer(canvas, dst, Garden.remaining(plot, now, speedup), f) }
        }
        when {
            anyReady -> postInvalidateOnAnimation()
            anyGrowing -> postInvalidateDelayed(1000)
        }
    }

    /** Hliněná cesta ve tvaru plus s tmavším lemem. */
    private fun drawPath(c: Canvas) {
        val edge = 2f * scale
        for (r in listOf(MeadowLayout.PATH_H, MeadowLayout.PATH_V)) {
            fill.color = 0xFFB08652.toInt()
            c.drawRect(vx(r.x0.toFloat()), vy(r.y0.toFloat()), vx(r.x1.toFloat()), vy(r.y1.toFloat()), fill)
        }
        for (r in listOf(MeadowLayout.PATH_H, MeadowLayout.PATH_V)) {
            fill.color = 0xFFCDB078.toInt()
            c.drawRect(vx(r.x0.toFloat()) + edge, vy(r.y0.toFloat()) + edge, vx(r.x1.toFloat()) - edge, vy(r.y1.toFloat()) - edge, fill)
        }
        // oblázky
        fill.color = 0xFFE3CC98.toInt()
        val v = MeadowLayout.PATH_V; val h = MeadowLayout.PATH_H
        for (k in 0 until 9) {
            val y = v.y0 + 8 + k * 20f
            c.drawRect(vx(v.x0 + 5f + (k % 3) * 5), vy(y), vx(v.x0 + 8f + (k % 3) * 5), vy(y + 2), fill)
            val x = h.x0 + 10 + k * 19f
            c.drawRect(vx(x), vy(h.y0 + 6f + (k % 2) * 8), vx(x + 3), vy(h.y0 + 8f + (k % 2) * 8), fill)
        }
    }

    /** Dřevěná cedulka nad záhonem: přesýpací hodiny + zbývající čas. */
    private fun drawTimer(c: Canvas, plot: RectF, remaining: Long, f: Float) {
        val u = plot.width() / SkillArt.PLOT_W * 0.55f
        val h = 12 * u
        val w = plot.width() * 0.96f
        val left = plot.centerX() - w / 2
        val top = plot.top - h - 2 * u
        woodBox(c, left, top, w, h, u)
        val gw = SkillArt.GLASS_W * u; val gh = SkillArt.GLASS_H * u
        val k = when { f < 0.34f -> 0; f < 0.67f -> 1; else -> 2 }
        c.drawBitmap(bmp("glass$k", SkillArt.GLASS_W, SkillArt.GLASS_H) { SkillArt.hourglass(k / 3f + 0.1f) },
            null, RectF(left + 2.2f * u, top + (h - gh) / 2, left + 2.2f * u + gw, top + (h + gh) / 2), px)
        text.textSize = h * 0.72f
        c.drawText(Garden.clock(remaining), left + (w + gw + 2.2f * u) / 2, top + h * 0.74f, text)
    }

    private fun woodBox(c: Canvas, l: Float, t: Float, w: Float, h: Float, u: Float) {
        fun r(x0: Float, y0: Float, x1: Float, y1: Float, col: Long) { fill.color = col.toInt(); c.drawRect(x0, y0, x1, y1, fill) }
        r(l + u, t, l + w - u, t + h, 0xFF2E1B0E); r(l, t + u, l + w, t + h - u, 0xFF2E1B0E)
        r(l + u, t + u, l + w - u, t + h - u, 0xFF93602C)
        r(l + u, t + u, l + w - u, t + 2 * u, 0xFFC48A4A)
        r(l + u, t + h - 2 * u, l + w - u, t + h - u, 0xFF4F3016)
    }

    /** Hotovo: dřevěný čtvereček se zelenou fajfkou, jemně poskakuje. */
    private fun drawCheck(c: Canvas, plot: RectF) {
        val size = plot.width() * 0.46f
        val bob = sin(SystemClock.uptimeMillis() / 260.0).toFloat() * size * 0.06f
        val left = plot.centerX() - size / 2
        val top = plot.top - size - size * 0.12f + bob
        c.drawBitmap(bmp("check", StepBarArt.SQUARE, StepBarArt.H) { StepBarArt.check() }, null, RectF(left, top, left + size, top + size), px)
    }

    /** Index záhonu pod bodem ve view (s rezervou na cedulku nad ním), nebo null. */
    fun plotAt(x: Float, y: Float): Int? = MeadowLayout.PLOTS.indexOfFirst { r ->
        x >= vx(r.x0.toFloat()) && x <= vx(r.x1.toFloat()) && y >= vy(r.y0 - 30f) && y <= vy(r.y1.toFloat())
    }.takeIf { it >= 0 }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        bitmaps.values.forEach { it.recycle() }; bitmaps.clear()
    }
}
