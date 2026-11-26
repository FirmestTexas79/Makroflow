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
 * Třpyt nad místy, která jde prozkoumat: čtyřcípé pixelové hvězdičky, které se střídavě rozsvěcují a pohasínají
 * (bílé a azurové – jinak než zlaté šipky východů). Přečtená místa třpytí slaběji. Jeden pohled přes celý svět mapy.
 */
class InspectGlitterView(context: Context, private val scale: Int, private val spots: List<Spot>) : View(context) {
    data class Spot(val x: Float, val y: Float, val read: Boolean)

    private val paint = Paint()
    private val start = System.nanoTime()
    private val seeds = spots.map { s -> List(4) { k -> Triple(((s.x * 7 + k * 13) % 11) - 5f, ((s.y * 3 + k * 7) % 9) - 6f, k * 0.27f + (s.x % 1f)) } }

    init { setWillNotDraw(false) }

    override fun onDraw(canvas: Canvas) {
        val t = (System.nanoTime() - start) / 1e9f
        val s = scale.toFloat()
        spots.forEachIndexed { i, spot ->
            for ((dx, dy, ph) in seeds[i]) {
                val w = ((t * 0.9f + ph + i * 0.37f) % 1f)
                val life = if (w < 0.5f) w * 2 else (1 - w) * 2                 // 0 → 1 → 0
                if (life < 0.15f) continue
                val arm = if (life > 0.75f) 2 else 1
                val a = ((if (spot.read) 120 else 235) * life).toInt()
                paint.color = if ((i + dx.toInt()) % 2 == 0) Color.argb(a, 255, 255, 255) else Color.argb(a, 150, 240, 228)
                val cx = ((spot.x + dx) * s).toInt().toFloat(); val cy = ((spot.y + dy) * s).toInt().toFloat()
                canvas.drawRect(cx, cy, cx + s, cy + s, paint)
                for (k in 1..arm) {                                       // cípy hvězdičky po celých pixelech
                    val aa = (a * (if (k == 1) 0.8f else 0.45f)).toInt()
                    paint.alpha = aa
                    canvas.drawRect(cx - k * s, cy, cx - (k - 1) * s, cy + s, paint)
                    canvas.drawRect(cx + k * s, cy, cx + (k + 1) * s, cy + s, paint)
                    canvas.drawRect(cx, cy - k * s, cx + s, cy - (k - 1) * s, paint)
                    canvas.drawRect(cx, cy + k * s, cx + s, cy + (k + 1) * s, paint)
                }
            }
            if (!spot.read) {                                             // pomalu kroužící světélko
                val ang = t * 1.6f + i
                paint.color = Color.argb(160, 255, 244, 200)
                val ox = ((spot.x + cos(ang) * 7f) * s).toInt().toFloat(); val oy = ((spot.y - 2 + sin(ang) * 3f) * s).toInt().toFloat()
                canvas.drawRect(ox, oy, ox + s, oy + s, paint)
            }
        }
        postInvalidateDelayed(70)
    }
}

/** Panel „Prozkoumáno“: tmavá kamenná deska s azurovým pixelovým lemem, ikona v rámečku, místo, text po písmenech. */
object InspectPanel {
    private const val TAG = "inspect_panel"

    fun isOpen(root: FrameLayout) = root.findViewWithTag<View>(TAG) != null

    fun show(root: FrameLayout, place: String, text: String, onClose: () -> Unit = {}) {
        root.findViewWithTag<View>(TAG)?.let { root.removeView(it) }
        val ctx = root.context
        val d = ctx.resources.displayMetrics.density
        fun dp(v: Float) = (v * d).toInt()
        val (icon, body) = Inspect.split(text)

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
