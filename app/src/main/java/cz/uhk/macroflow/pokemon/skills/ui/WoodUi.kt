package cz.uhk.macroflow.pokemon.skills.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import cz.uhk.macroflow.R

/**
 * Dřevěné pixelové menu (docs/adr/0034): rámeček ve stylu ukazatele kroků – tmavý obrys,
 * světlá horní hrana dřeva, zapuštěný pergamen. Kreslí se po „art pixelech“ velikosti [u].
 */
class WoodPanelDrawable(private val u: Float, private val parchment: Boolean = true) : Drawable() {
    private val p = Paint().apply { isAntiAlias = false }
    private fun c(hex: Long) = hex.toInt()

    override fun draw(canvas: Canvas) {
        val b = bounds
        val l = b.left.toFloat(); val t = b.top.toFloat(); val r = b.right.toFloat(); val btm = b.bottom.toFloat()
        fun rect(x0: Float, y0: Float, x1: Float, y1: Float, col: Int) { p.color = col; canvas.drawRect(x0, y0, x1, y1, p) }
        // obrys se zaoblenými rohy (schod o 1 art pixel)
        rect(l + u, t, r - u, btm, c(0xFF2E1B0E))
        rect(l, t + u, r, btm - u, c(0xFF2E1B0E))
        // dřevo
        rect(l + u, t + u, r - u, btm - u, c(0xFF93602C))
        rect(l + u, t + u, r - u, t + 2 * u, c(0xFFC48A4A))        // světlo nahoře
        rect(l + u, t + u, l + 2 * u, btm - u, c(0xFFB27A3E))
        rect(l + u, btm - 2 * u, r - u, btm - u, c(0xFF4F3016))   // stín dole
        rect(r - 2 * u, t + 2 * u, r - u, btm - u, c(0xFF6C4420))
        // léta dřeva
        var x = l + 6 * u
        while (x < r - 6 * u) { rect(x, t + 2 * u, x + u, t + 3 * u, c(0xFF7A4D24)); rect(x + 3 * u, btm - 3 * u, x + 4 * u, btm - 2 * u, c(0xFF6C4420)); x += 9 * u }
        // vnitřek
        val i = 4 * u
        rect(l + i - u, t + i - u, r - i + u, btm - i + u, c(0xFF2E1B0E))
        if (parchment) {
            rect(l + i, t + i, r - i, btm - i, c(0xFFF6E7C8))
            rect(l + i, t + i, r - i, t + i + u, c(0xFFD9C29A))          // vnitřní stín
            rect(l + i, t + i, l + i + u, btm - i, c(0xFFE5D0A8))
        } else {
            rect(l + i, t + i, r - i, btm - i, c(0xFF6C4420))
            rect(l + i, t + i, r - i, t + i + u, c(0xFF4F3016))
        }
    }

    override fun getPadding(padding: Rect): Boolean {
        val pad = (6 * u).toInt(); padding.set(pad, pad, pad, pad); return true
    }
    override fun setAlpha(alpha: Int) { p.alpha = alpha }
    override fun setColorFilter(cf: ColorFilter?) { p.colorFilter = cf }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/** Malé pomocníky pro stavbu dřevěných menu v kódu. */
class WoodUi(val ctx: Context) {
    val dp = ctx.resources.displayMetrics.density
    val font = ResourcesCompat.getFont(ctx, R.font.jersey_15)
    val ink = 0xFF3B2A1A.toInt()
    val inkSoft = 0xFF7A5C3E.toInt()
    val cream = 0xFFFEFAE0.toInt()
    val olive = 0xFF606C38.toInt()
    val rust = 0xFFBC6C25.toInt()

    fun px(v: Float) = (v * dp).toInt()

    fun text(s: CharSequence, size: Float, color: Int = ink, gravity: Int = Gravity.START) = TextView(ctx).apply {
        text = s; textSize = size; setTextColor(color); typeface = font; this.gravity = gravity
        includeFontPadding = false
    }

    /** Pixelová ikona zvětšená bez vyhlazení. */
    fun icon(pixels: IntArray, w: Int, h: Int, sizeDp: Float): ImageView = ImageView(ctx).apply {
        // Předem zvětšeno celočíselným násobkem: dřív se při necelém měřítku bez vyhlazení
        // ztrácela horní řada pixelů (obrys ikon ve skladu)
        val box = px(sizeDp)
        val k = maxOf(1, box / maxOf(w, h))
        val src = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
        setImageBitmap(Bitmap.createScaledBitmap(src, w * k, h * k, false))
        scaleType = ImageView.ScaleType.CENTER
        layoutParams = LinearLayout.LayoutParams(box, px(sizeDp * h / maxOf(w, h)).coerceAtLeast(h * k))
    }

    /** Dřevěné tlačítko (malý dřevěný rámeček bez pergamenu). */
    fun button(label: String, enabled: Boolean = true, onClick: () -> Unit): TextView = text(label, 18f, cream, Gravity.CENTER).apply {
        background = WoodPanelDrawable(1.5f * dp, parchment = false)
        setPadding(px(12f), px(7f), px(12f), px(8f))
        alpha = if (enabled) 1f else 0.4f
        isEnabled = enabled
        setOnClickListener { if (enabled) onClick() }
    }

    fun row(): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
    }

    fun column(): LinearLayout = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

    fun lp(w: Int, h: Int, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)

    fun spacer(hDp: Float): View = View(ctx).apply { layoutParams = LinearLayout.LayoutParams(1, px(hDp)) }
}

/**
 * Políčko ve stylu IdleOn: vystouplá dlaždice s obrysem, světlou horní/levou a tmavou
 * spodní/pravou hranou. [selected] = zlatý rámeček.
 */
class BevelDrawable(
    private val u: Float,
    private val base: Int,
    private val light: Int,
    private val dark: Int,
    private val outline: Int = 0xFF101828.toInt(),
    var selected: Boolean = false
) : Drawable() {
    private val p = Paint().apply { isAntiAlias = false }
    override fun draw(canvas: Canvas) {
        val b = bounds
        val l = b.left.toFloat(); val t = b.top.toFloat(); val r = b.right.toFloat(); val btm = b.bottom.toFloat()
        fun rect(x0: Float, y0: Float, x1: Float, y1: Float, col: Int) { p.color = col; canvas.drawRect(x0, y0, x1, y1, p) }
        val o = if (selected) 0xFFFFD54F.toInt() else outline
        rect(l + u, t, r - u, btm, o); rect(l, t + u, r, btm - u, o)
        if (selected) { rect(l + u, t + u, r - u, btm - u, 0xFFB8860B.toInt()) }
        val i = if (selected) 2 * u else u
        rect(l + i, t + i, r - i, btm - i, base)
        rect(l + i, t + i, r - i, t + i + u, light)
        rect(l + i, t + i, l + i + u, btm - i, light)
        rect(l + i, btm - i - u, r - i, btm - i, dark)
        rect(r - i - u, t + i + u, r - i, btm - i, dark)
    }
    override fun setAlpha(alpha: Int) { p.alpha = alpha }
    override fun setColorFilter(cf: ColorFilter?) { p.colorFilter = cf }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT

    companion object {
        /** Tmavě modrá dlaždice okna postavy. */
        fun navy(u: Float, selected: Boolean = false) = BevelDrawable(u, 0xFF22345C.toInt(), 0xFF3E5A8C.toInt(), 0xFF14203A.toInt(), selected = selected)
        /** Světlé dřevěné políčko skladu. */
        fun slot(u: Float) = BevelDrawable(u, 0xFFC9A26B.toInt(), 0xFFE8CFA0.toInt(), 0xFF8E6A3E.toInt(), 0xFF3B2A1A.toInt())
    }
}
