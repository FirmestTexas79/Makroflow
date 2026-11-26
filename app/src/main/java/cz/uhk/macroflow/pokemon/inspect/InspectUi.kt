package cz.uhk.macroflow.pokemon.inspect

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Prozkoumávání míst (docs/adr/0107): místa s příběhovým textem se neotevírají klepnutím (to jen dojde), ale mají
 * třpyt a hráč na nich podrží prst. Kolem prstu se vykresluje pixelový kruh; když se naplní, vyjede panel
 * „Prozkoumáno“ – zvláštní obdoba rozhovoru s NPC (ikona v rámečku, místo, text se vypisuje po písmenech).
 */
object Inspect {
    /** Jak dlouho držet prst (ms). */
    const val HOLD_MS = 2200L

    /** Ikona na začátku textu (emoji před první mezerou) a zbytek textu. */
    fun split(text: String): Pair<String?, String> {
        val sp = text.indexOf(' ')
        if (sp in 1..8) {
            val head = text.substring(0, sp)
            if (head.none { it.isLetterOrDigit() }) return head to text.substring(sp + 1)
        }
        return null to text
    }
}

/** Pixelový kruh kolem prstu: 24 dílků po obvodu, naplňuje se po směru hodin od dvanácti. */
class HoldRingView(context: Context) : View(context) {
    var progress = 0f
        set(v) { field = v; invalidate() }
    private val px = context.resources.displayMetrics.density * 3.5f
    private val paint = Paint()
    private val grid = 15                                                 // kruh na mřížce 15 × 15 „pixelů“
    private val cells: List<Triple<Int, Int, Float>> = buildList {
        val c = grid / 2f
        for (y in 0 until grid) for (x in 0 until grid) {
            val d = hypot(x + 0.5f - c, y + 0.5f - c)
            if (d in 5.2f..7.2f) {
                var a = atan2(x + 0.5f - c, -(y + 0.5f - c)) / (2 * Math.PI.toFloat())
                if (a < 0) a += 1f
                add(Triple(x, y, a))
            }
        }
    }
    val sizePx get() = (grid * px).toInt()

    override fun onDraw(canvas: Canvas) {
        for ((x, y, a) in cells) {                                         // tmavý podklad (obrys)
            paint.color = 0xCC0A0C10.toInt()
            canvas.drawRect((x - 0.35f) * px, (y - 0.35f) * px, (x + 1.35f) * px, (y + 1.35f) * px, paint)
        }
        for ((x, y, a) in cells) {
            val on = a <= progress
            paint.color = when {
                !on -> 0x66506068
                a > progress - 0.06f -> 0xFFFFFFFF.toInt()                // čerstvě naplněná špička svítí bíle
                else -> 0xFF8CF0E0.toInt()
            }
            canvas.drawRect(x * px, y * px, (x + 1) * px, (y + 1) * px, paint)
        }
        if (progress > 0.02f) {                                           // uprostřed se rozsvítí tečka
            paint.color = Color.argb((255 * progress).toInt(), 0xFF, 0xF4, 0xC0)
            val c = grid / 2
            canvas.drawRect(c * px, c * px, (c + 1) * px, (c + 1) * px, paint)
        }
    }
}

/**
 * Třpyt nad místy, která jde prozkoumat: u každého místa jedna čtyřcípá pixelová hvězdička, která pomalu
 * pulzuje, a dvě drobné jiskry těsně u ní – všechno v jedné barvě, ať jedno místo vypadá jako jedno místo
 * (dřív se bílé a azurové hvězdy rozlétly daleko od sebe a vypadaly jako dva sběratelské předměty).
 * Přečtená místa třpytí slaběji. Jeden pohled přes celý svět mapy.
 */
class InspectGlitterView(context: Context, private val scale: Int, private val spots: List<Spot>) : View(context) {
    data class Spot(val x: Float, val y: Float, val read: Boolean)

    private val paint = Paint()
    private val start = System.nanoTime()

    init { setWillNotDraw(false) }

    private fun star(canvas: Canvas, cx: Float, cy: Float, arm: Int, a: Int) {
        val s = scale.toFloat()
        paint.color = Color.argb(a, 0xE8, 0xFA, 0xFF)
        canvas.drawRect(cx, cy, cx + s, cy + s, paint)
        for (k in 1..arm) {                                                 // cípy po celých pixelech
            paint.alpha = (a * (if (k == 1) 0.75f else 0.4f)).toInt()
            canvas.drawRect(cx - k * s, cy, cx - (k - 1) * s, cy + s, paint)
            canvas.drawRect(cx + k * s, cy, cx + (k + 1) * s, cy + s, paint)
            canvas.drawRect(cx, cy - k * s, cx + s, cy - (k - 1) * s, paint)
            canvas.drawRect(cx, cy + k * s, cx + s, cy + (k + 1) * s, paint)
        }
    }

    override fun onDraw(canvas: Canvas) {
        val t = (System.nanoTime() - start) / 1e9f
        val s = scale.toFloat()
        spots.forEachIndexed { i, spot ->
            val peak = if (spot.read) 110 else 240
            val w = (t * 0.55f + i * 0.31f) % 1f
            val life = 0.35f + 0.65f * (if (w < 0.5f) w * 2 else (1 - w) * 2)  // hlavní hvězdička nikdy úplně nezhasne
            val cx = (spot.x * s).toInt().toFloat(); val cy = (spot.y * s).toInt().toFloat()
            star(canvas, cx, cy, if (life > 0.8f) 2 else 1, (peak * life).toInt())
            for (k in 0..1) {                                               // dvě jiskry těsně vedle (±3 px)
                val wk = (t * 1.3f + k * 0.5f + i * 0.17f) % 1f
                if (wk > 0.4f) continue
                val a = (peak * 0.8f * (1 - wk / 0.4f)).toInt()
                val dx = if (k == 0) -3 else 3; val dy = if (k == 0) -2 else 2
                paint.color = Color.argb(a, 0xE8, 0xFA, 0xFF)
                canvas.drawRect(cx + dx * s, cy + dy * s, cx + (dx + 1) * s, cy + (dy + 1) * s, paint)
            }
        }
        postInvalidateDelayed(70)
    }
}

/**
 * Sebrání předmětu na mapě (jmenovka, útržek, lucerna…): záblesk, rozlet pixelů po celých pixelech mapy a stoupající
 * popisek („Jmenovka 3/7“). Volitelně se sebraný sprite povyskočí a zmizí. [then] přijde, až efekt doběhne – teprve pak
 * vyjede panel, ať je vidět, že se něco právě stalo (a nevypadá to, jako bychom to měli odjakživa).
 */
object PickupFx {
    fun play(world: FrameLayout, cx: Float, cy: Float, px: Float, color: Int, label: String?, item: View? = null, then: () -> Unit = {}) {
        val ctx = world.context
        item?.animate()?.cancel()
        item?.animate()?.scaleX(1.7f)?.scaleY(1.7f)?.translationYBy(-6 * px)?.setDuration(200)?.withEndAction {
            item.animate().scaleX(0f).scaleY(0f).alpha(0f).setDuration(240).start()
        }?.start()
        val flashSize = (26 * px).toInt()
        val flash = View(ctx).apply {                                       // měkký záblesk pod částicemi
            layoutParams = FrameLayout.LayoutParams(flashSize, flashSize)
            background = android.graphics.drawable.GradientDrawable().apply {
                gradientType = android.graphics.drawable.GradientDrawable.RADIAL_GRADIENT
                gradientRadius = flashSize / 2f
                colors = intArrayOf((color and 0x00FFFFFF) or (0xCC shl 24), Color.TRANSPARENT)
            }
            x = cx - flashSize / 2f; y = cy - flashSize / 2f; elevation = 6f; scaleX = 0.2f; scaleY = 0.2f
        }
        world.addView(flash)
        flash.animate().scaleX(1.4f).scaleY(1.4f).alpha(0f).setDuration(520).withEndAction { world.removeView(flash) }.start()
        val parts = List(12) { k ->
            View(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(px.toInt().coerceAtLeast(2), px.toInt().coerceAtLeast(2))
                setBackgroundColor(if (k % 3 == 0) Color.WHITE else color)
                x = cx; y = cy; elevation = 6.2f
            }.also { world.addView(it) }
        }
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 560
            interpolator = android.view.animation.DecelerateInterpolator()
            addUpdateListener {
                val f = it.animatedValue as Float
                parts.forEachIndexed { k, v ->
                    val ang = k * (Math.PI.toFloat() * 2 / parts.size) + (k % 2) * 0.2f
                    val r = (if (k % 2 == 0) 11f else 7f) * f
                    v.x = cx + (cos(ang) * r).toInt() * px                    // po celých pixelech mapy
                    v.y = cy + (sin(ang) * r - 3 * f * f).toInt() * px
                    v.alpha = 1 - f * f
                }
            }
            doOnEnd { parts.forEach { world.removeView(it) } }
            start()
        }
        label?.let { text ->
            val tv = TextView(ctx).apply {
                this.text = text
                textSize = 13f
                setTextColor(Color.WHITE); typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                setShadowLayer(4f, 0f, 1f, Color.BLACK)
                elevation = 6.5f; alpha = 0f
            }
            world.addView(tv)
            tv.post {
                tv.x = cx - tv.width / 2f; tv.y = cy - 10 * px
                tv.animate().alpha(1f).translationYBy(-6 * px).setDuration(260).withEndAction {
                    tv.animate().alpha(0f).translationYBy(-4 * px).setStartDelay(500).setDuration(300).withEndAction { world.removeView(tv) }.start()
                }.start()
            }
        }
        world.postDelayed({ then() }, 640)
    }

    private fun ValueAnimator.doOnEnd(f: () -> Unit) = addListener(object : android.animation.AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: android.animation.Animator) = f()
    })
}

/** Panel „Prozkoumáno“: tmavá kamenná deska s azurovým pixelovým lemem, ikona v rámečku, místo, text po písmenech. */
object InspectPanel {
    private const val TAG = "inspect_panel"

    fun isOpen(root: FrameLayout) = root.findViewWithTag<View>(TAG) != null

    fun show(root: FrameLayout, place: String, message: String, onClose: () -> Unit = {}) {
        root.findViewWithTag<View>(TAG)?.let { root.removeView(it) }
        val ctx = root.context
        val d = ctx.resources.displayMetrics.density
        fun dp(v: Float) = (v * d).toInt()
        val (icon, body) = Inspect.split(message)

        val dim = FrameLayout(ctx).apply {
            tag = TAG
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(0x99050709.toInt())
            elevation = 40f
            isClickable = true
        }
        val frame = FrameLayout(ctx).apply {                              // pixelový lem: tma – azur – tma
            setBackgroundColor(0xFF06080A.toInt())
            setPadding(dp(3f), dp(3f), dp(3f), dp(3f))
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply {
                setMargins(dp(12f), 0, dp(12f), dp(34f))
            }
        }
        val glow = FrameLayout(ctx).apply {
            setBackgroundColor(0xFF5ECFC0.toInt())
            setPadding(dp(2f), dp(2f), dp(2f), dp(2f))
        }
        val panel = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xF0141A1E.toInt())
            setPadding(dp(16f), dp(14f), dp(16f), dp(12f))
        }
        val head = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val iconBox = FrameLayout(ctx).apply {
            setBackgroundColor(0xFF0A0E10.toInt())
            setPadding(dp(2f), dp(2f), dp(2f), dp(2f))
            addView(TextView(ctx).apply {
                text = icon ?: "✦"
                textSize = 26f
                gravity = Gravity.CENTER
                setBackgroundColor(0xFF22303A.toInt())
                layoutParams = FrameLayout.LayoutParams(dp(52f), dp(52f))
            })
        }
        val titles = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12f), 0, 0, 0)
            addView(TextView(ctx).apply {
                text = "✦ PROZKOUMÁNO"
                textSize = 11f; letterSpacing = 0.18f
                setTextColor(0xFF8CF0E0.toInt()); typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            })
            addView(TextView(ctx).apply {
                text = place
                textSize = 17f
                setTextColor(0xFFF4E8C8.toInt()); typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            })
        }
        head.addView(iconBox); head.addView(titles)
        val rule = View(ctx).apply {                                       // tečkovaná čára pod hlavičkou
            setBackgroundColor(0xFF2E4A4E.toInt())
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(2f)).apply { setMargins(0, dp(10f), 0, dp(10f)) }
        }
        val textView = TextView(ctx).apply {
            textSize = 16f
            setLineSpacing(dp(3f).toFloat(), 1f)
            setTextColor(0xFFE6DFCB.toInt()); typeface = Typeface.SERIF
        }
        val footer = TextView(ctx).apply {
            text = "klepni ▸"
            textSize = 12f
            gravity = Gravity.END
            setTextColor(0xFF8CF0E0.toInt()); typeface = Typeface.MONOSPACE
            alpha = 0f
            setPadding(0, dp(8f), 0, 0)
        }
        panel.addView(head); panel.addView(rule); panel.addView(textView); panel.addView(footer)
        glow.addView(panel); frame.addView(glow); dim.addView(frame)
        root.addView(dim)

        // vjezd zespodu, text po písmenech, lem pulzuje
        frame.translationY = dp(60f).toFloat(); frame.alpha = 0f
        frame.animate().translationY(0f).alpha(1f).setDuration(260).start()
        val pulse = ValueAnimator.ofFloat(0f, 1f, 0f).apply {
            duration = 2200; repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                val v = it.animatedValue as Float
                glow.setBackgroundColor(Color.rgb((60 + 60 * v).toInt(), (160 + 80 * v).toInt(), (150 + 80 * v).toInt()))
            }
            start()
        }
        var shown = 0
        val typer = ValueAnimator.ofInt(0, body.length).apply {
            duration = (body.length * 22L).coerceIn(300L, 6000L)
            addUpdateListener { shown = it.animatedValue as Int; textView.text = body.substring(0, shown) }
            doOnEnd { footer.animate().alpha(1f).setDuration(300).start() }
            start()
        }
        dim.setOnClickListener {
            if (shown < body.length) { typer.cancel(); shown = body.length; textView.text = body; footer.animate().alpha(1f).setDuration(200).start(); return@setOnClickListener }
            pulse.cancel()
            frame.animate().translationY(dp(40f).toFloat()).alpha(0f).setDuration(180).withEndAction { root.removeView(dim); onClose() }.start()
        }
    }

    private fun ValueAnimator.doOnEnd(f: () -> Unit) = addListener(object : android.animation.AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: android.animation.Animator) = f()
    })
}
