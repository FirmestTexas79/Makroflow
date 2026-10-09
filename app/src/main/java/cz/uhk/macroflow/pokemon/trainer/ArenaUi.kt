package cz.uhk.macroflow.pokemon.trainer

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup

/** Grafické prvky obrazovky Arény (docs/adr/0080): erb ranku, barevné dlaždice, stuhy, nástup. */
object ArenaUi {

    fun darker(c: Int, k: Float) = Color.rgb((Color.red(c) * k).toInt(), (Color.green(c) * k).toInt(), (Color.blue(c) * k).toInt())

    /** Dlaždice s přechodem, tmavým obrysem a světlou horní hranou (herní tlačítko). */
    fun tile(top: Int, bottom: Int, dp: Float, radius: Float = 10f) = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(top, bottom)).apply {
        cornerRadius = radius * dp
        setStroke((2 * dp).toInt(), 0xFF2A1D12.toInt())
    }

    /** Tmavá deska (karta tvého týmu, žebříček). */
    fun plaque(dp: Float) = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xFF4A3524.toInt(), 0xFF2A1D12.toInt())).apply {
        cornerRadius = 10 * dp
        setStroke((2 * dp).toInt(), 0xFFC9A26B.toInt())
    }

    /** Stuha nadpisu sekce. */
    fun ribbon(color: Int, dp: Float) = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(color, darker(color, 0.8f))).apply {
        cornerRadii = floatArrayOf(2 * dp, 2 * dp, 14 * dp, 14 * dp, 14 * dp, 14 * dp, 2 * dp, 2 * dp)
    }

    fun pill(color: Int, dp: Float) = GradientDrawable().apply { cornerRadius = 50 * dp; setColor(color) }

    /** Jemné „dýchání“ dlaždice (hodnocený zápas láká ke hře). */
    fun pulse(v: View) = ObjectAnimator.ofPropertyValuesHolder(v,
        PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.025f), PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.025f)).apply {
        duration = 900; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE; start()
    }

    /** Postupný nástup prvků panelu zdola. */
    fun cascade(box: ViewGroup, dp: Float) {
        for (i in 0 until box.childCount) {
            val c = box.getChildAt(i)
            c.alpha = 0f; c.translationY = 18 * dp
            c.animate().alpha(1f).translationY(0f).setStartDelay(40L * i).setDuration(260).start()
        }
    }

    /** Erb ranku: štít v barvě ranku s lesklým horním okrajem a zlatým lemem. */
    class Emblem(private val color: Int, private val dp: Float) : Drawable() {
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xFFF2C94A.toInt() }
        private val shine = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x40FFFFFF }
        private val path = Path()
        override fun draw(c: Canvas) {
            val b = bounds; val w = b.width().toFloat(); val h = b.height().toFloat()
            val l = b.left.toFloat(); val t = b.top.toFloat()
            path.reset()
            path.moveTo(l + w * 0.5f, t + h * 0.03f)
            path.lineTo(l + w * 0.93f, t + h * 0.18f)
            path.cubicTo(l + w * 0.93f, t + h * 0.62f, l + w * 0.75f, t + h * 0.84f, l + w * 0.5f, t + h * 0.97f)
            path.cubicTo(l + w * 0.25f, t + h * 0.84f, l + w * 0.07f, t + h * 0.62f, l + w * 0.07f, t + h * 0.18f)
            path.close()
            fill.shader = LinearGradient(0f, t, 0f, t + h, color, darker(color, 0.55f), Shader.TileMode.CLAMP)
            c.drawPath(path, fill)
            c.save(); c.clipPath(path); c.drawRect(l, t, l + w, t + h * 0.38f, shine); c.restore()
            rim.strokeWidth = 3 * dp
            c.drawPath(path, rim)
        }
        override fun setAlpha(alpha: Int) { fill.alpha = alpha }
        override fun setColorFilter(cf: ColorFilter?) { fill.colorFilter = cf }
        @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
    }
}
