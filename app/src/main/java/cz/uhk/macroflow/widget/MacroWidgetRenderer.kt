package cz.uhk.macroflow.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import cz.uhk.macroflow.R
import cz.uhk.macroflow.widget.MacroWidgetModel.Macro
import kotlin.math.cos
import kotlin.math.sin

/**
 * Vykreslí widget do bitmapy (RemoteViews neumí vlastní View). Rozměry jsou relativní ke straně
 * čtverce, takže widget vypadá stejně na 2×2 i po zvětšení. Světlý i tmavý motiv podle systému.
 */
object MacroWidgetRenderer {

    private class Palette(
        val background: Int, val text: Int, val muted: Int,
        val protein: Int, val carbs: Int, val fat: Int, val track: Float
    )

    private val LIGHT = Palette(
        background = Color.parseColor("#FEFAE0"), text = Color.parseColor("#283618"),
        muted = Color.parseColor("#99283618"),
        protein = Color.parseColor("#606C38"), carbs = Color.parseColor("#E9B072"), fat = Color.parseColor("#BC6C25"),
        track = 0.20f
    )
    private val DARK = Palette(
        background = Color.parseColor("#283618"), text = Color.parseColor("#FEFAE0"),
        muted = Color.parseColor("#B3FEFAE0"),
        protein = Color.parseColor("#A3B46A"), carbs = Color.parseColor("#E9B072"), fat = Color.parseColor("#D9853A"),
        track = 0.22f
    )

    fun isNight(ctx: Context) =
        (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    fun render(ctx: Context, state: MacroWidgetModel.State, mode: MacroWidgetModel.Mode, sizePx: Int, night: Boolean = isNight(ctx)): Bitmap {
        val s = sizePx.coerceIn(96, 1024).toFloat()
        val p = if (night) DARK else LIGHT
        val bmp = Bitmap.createBitmap(s.toInt(), s.toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)

        // Podklad – zaoblený čtverec jako ostatní karty aplikace
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.background }
        c.drawRoundRect(RectF(0f, 0f, s, s), s * 0.2f, s * 0.2f, bg)

        // Prstenec
        val cx = s / 2; val cy = s * 0.455f
        val r = s * 0.335f; val w = s * 0.085f
        val oval = RectF(cx - r, cy - r, cx + r, cy + r)
        val capDeg = Math.toDegrees((w / 2 / r).toDouble()).toFloat()   // o kolik zaoblený konec přesahuje
        val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = w; strokeCap = Paint.Cap.ROUND }

        fun colorOf(m: Macro) = when (m) { Macro.PROTEIN -> p.protein; Macro.CARBS -> p.carbs; Macro.FAT -> p.fat }

        state.segments.forEach { seg ->
            val col = colorOf(seg.macro)
            // podklad celého oblouku
            arc.color = ColorUtils.setAlphaComponent(col, (255 * p.track).toInt())
            c.drawArc(oval, seg.startDeg + capDeg, seg.sweepDeg - 2 * capDeg, false, arc)
            // vyplněná část; konce zasunuté o zaoblení, velmi malá hodnota = tečka
            if (seg.fraction > 0f) {
                arc.color = col
                val len = kotlin.math.abs(seg.fillSweepDeg)
                val dir = if (seg.fillSweepDeg < 0) -1f else 1f
                val drawn = (len - 2 * capDeg).coerceAtLeast(0.01f)
                c.drawArc(oval, seg.fillStartDeg + dir * capDeg, dir * drawn, false, arc)
            }
        }

        // Logo uprostřed
        ContextCompat.getDrawable(ctx, R.drawable.ic_logo_black)?.mutate()?.let { d ->
            d.setTint(p.text)
            val half = s * 0.125f
            d.setBounds((cx - half).toInt(), (cy - half).toInt(), (cx + half).toInt(), (cy + half).toInt())
            d.draw(c)
        }

        // Popisky oblouků mezi prstencem a logem: písmeno v barvě makra, pod ním gramy
        val letter = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER; typeface = Typeface.create("sans-serif-black", Typeface.NORMAL); textSize = s * 0.058f
        }
        val grams = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER; typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            textSize = s * 0.068f; color = p.text
        }
        val labelR = r - w / 2 - s * 0.095f
        state.segments.forEach { seg ->
            // bílkoviny přímo nahoře, boční popisky trochu níž než střed oblouku (víc místa vedle loga)
            val a = Math.toRadians(if (seg.macro == Macro.PROTEIN) 270.0 else seg.midDeg.toDouble() + if (seg.macro == Macro.CARBS) -12.0 else 12.0)
            val lx = cx + (labelR * cos(a)).toFloat()
            val ly = cy + (labelR * sin(a)).toFloat()
            letter.color = colorOf(seg.macro)
            val label = MacroWidgetModel.segmentLabel(seg, mode, state.hasTargets)
            c.drawText(seg.macro.letter, lx, ly - s * 0.005f, letter)
            grams.color = if (seg.over && mode == MacroWidgetModel.Mode.REMAINING) p.fat else p.text
            c.drawText(label, lx, ly + s * 0.062f, grams)
        }

        // Kalorie ve spodní mezeře
        val big = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER; typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
            textSize = s * 0.125f; color = p.text
        }
        val small = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            textSize = s * 0.058f; color = p.muted
        }
        fitText(big, state.kcalText, s * 0.5f)
        fitText(small, state.kcalCaption, s * 0.62f)
        c.drawText(state.kcalText, cx, s * 0.855f, big)
        c.drawText(state.kcalCaption, cx, s * 0.935f, small)
        return bmp
    }

    /** Zmenší písmo, aby se text vešel do [maxWidth]. */
    private fun fitText(paint: Paint, text: String, maxWidth: Float) {
        val wdt = paint.measureText(text)
        if (wdt > maxWidth) paint.textSize *= maxWidth / wdt
    }
}
