package cz.uhk.macroflow.pokemon.skills.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import androidx.core.content.res.ResourcesCompat
import cz.uhk.macroflow.R
import cz.uhk.macroflow.pokemon.skills.Gathering
import cz.uhk.macroflow.pokemon.skills.SkillArt

/**
 * Dřevěná cedulka nad místem, kde se právě těží / kácí (docs/adr/0035): přesýpací hodiny,
 * počet hotových kusů a čas do dalšího. Obnovuje se každou sekundu.
 */
class GatherPlaqueView(context: Context, private val unit: Float) : View(context) {

    var activity: Gathering.Activity? = null
        set(v) { field = v; invalidate() }
    var secPerUnit: Long = 0
    var capHours: Int = 12
    /** Ikona suroviny (12 × 12). */
    var icon: IntArray? = null
        set(v) { field = v; iconBmp = v?.let { Bitmap.createBitmap(it, SkillArt.ITEM, SkillArt.ITEM, Bitmap.Config.ARGB_8888) }; invalidate() }

    private var iconBmp: Bitmap? = null
    private val px = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
    private val fill = Paint()
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFEFAE0.toInt(); typeface = ResourcesCompat.getFont(context, R.font.jersey_15); textAlign = Paint.Align.LEFT
    }
    private val glass = HashMap<Int, Bitmap>()

    val wantedWidth: Int get() = (46 * unit).toInt()
    val wantedHeight: Int get() = (13 * unit).toInt()

    override fun onDraw(c: Canvas) {
        val a = activity ?: return
        val u = unit
        val w = width.toFloat(); val h = height.toFloat()
        fun r(x0: Float, y0: Float, x1: Float, y1: Float, col: Long) { fill.color = col.toInt(); c.drawRect(x0, y0, x1, y1, fill) }
        r(u, 0f, w - u, h, 0xFF2E1B0E); r(0f, u, w, h - u, 0xFF2E1B0E)
        r(u, u, w - u, h - u, 0xFF93602C); r(u, u, w - u, 2 * u, 0xFFC48A4A); r(u, h - 2 * u, w - u, h - u, 0xFF4F3016)
        val now = System.currentTimeMillis() / 1000
        val (f, left) = Gathering.partial(a, now, secPerUnit)
        val k = when { f < 0.34f -> 0; f < 0.67f -> 1; else -> 2 }
        val g = glass.getOrPut(k) { Bitmap.createBitmap(SkillArt.hourglass(k / 3f + 0.1f), SkillArt.GLASS_W, SkillArt.GLASS_H, Bitmap.Config.ARGB_8888) }
        val gh = SkillArt.GLASS_H * u * 0.9f; val gw = SkillArt.GLASS_W * u * 0.9f
        c.drawBitmap(g, null, RectF(2.2f * u, (h - gh) / 2, 2.2f * u + gw, (h + gh) / 2), px)
        val pending = Gathering.pending(a, now, secPerUnit, capHours)
        var x = 3.2f * u + gw
        iconBmp?.let { b ->
            val s = 9 * u
            c.drawBitmap(b, null, RectF(x, (h - s) / 2, x + s, (h + s) / 2), px); x += s + u
        }
        text.textSize = h * 0.62f
        c.drawText("$pending · ${cz.uhk.macroflow.pokemon.skills.Garden.clock(left)}", x, h * 0.7f, text)
        postInvalidateDelayed(1000)
    }
}
