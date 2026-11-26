package cz.uhk.macroflow.pokemon.cutscene

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
import cz.uhk.macroflow.pokemon.story.Finale
import kotlin.math.sin

/**
 * Dohrání konce (docs/adr/0110): celá obrazovka, za textem živé pixelové pozadí podle konce (popel stoupá – Kopí,
 * zlatá voda a okvětní lístky – Den kvetení, modré jiskry Lumenců – Poslední přání, noc se mění v ráno – Probuzení).
 * Stránky se vypisují po písmenech, klepnutí dopíše / pokračuje. Na konci titulek „KONEC …“, dovětek a návrat do světa.
 */
object EndingScene {
    private const val TAG = "ending_scene"

    fun isOpen(root: FrameLayout) = root.findViewWithTag<View>(TAG) != null

    /**
     * [videoRes] ≠ 0: konec má vlastní cutscénu (res/raw, tools/cutscene) – přehraje se celá obrazovka, klepnutí ukáže
     * „přeskočit“, druhé ji přeskočí; pak titulek. Jinak stránky textu po písmenech.
     */
    fun show(root: FrameLayout, ending: Finale.Ending, videoRes: Int = 0, onDone: () -> Unit) {
        val ctx = root.context
        val d = ctx.resources.displayMetrics.density
        fun dp(v: Float) = (v * d).toInt()
        val shell = FrameLayout(ctx).apply {
            tag = TAG
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.BLACK)
            elevation = 60f; isClickable = true; alpha = 0f
        }
        val fx = EndingFxView(ctx, ending.end).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        val pageView = TextView(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER).apply {
                setMargins(dp(28f), 0, dp(28f), 0)
            }
            textSize = 19f
            setLineSpacing(dp(5f).toFloat(), 1f)
            setTextColor(0xFFF4ECD8.toInt()); typeface = Typeface.SERIF
            setShadowLayer(8f, 0f, 2f, Color.BLACK)
            gravity = Gravity.CENTER
        }
        val hint = TextView(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                bottomMargin = dp(46f)
            }
            text = "klepni ▸"; textSize = 12f; alpha = 0f
            setTextColor(0x99F4ECD8.toInt()); typeface = Typeface.MONOSPACE
        }
        shell.addView(fx); shell.addView(pageView); shell.addView(hint)
        root.addView(shell)
        shell.animate().alpha(1f).setDuration(1400).start()

        var page = 0
        var shown = 0
        var typer: ValueAnimator? = null
        fun body() = ending.pages[page]
        fun type() {
            val b = body()
            pageView.alpha = 0f; pageView.text = ""; hint.alpha = 0f
            pageView.animate().alpha(1f).setDuration(500).start()
            typer = ValueAnimator.ofInt(0, b.length).apply {
                duration = (b.length * 34L).coerceIn(900L, 9000L)
                startDelay = 300
                addUpdateListener { shown = it.animatedValue as Int; pageView.text = b.substring(0, shown) }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) { hint.animate().alpha(1f).setDuration(600).start() }
                })
                start()
            }
        }
        fun finish() {
            shell.setOnClickListener(null)
            pageView.animate().alpha(0f).setDuration(600).start(); hint.animate().alpha(0f).setDuration(300).start()
            val card = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER).apply {
                    setMargins(dp(24f), 0, dp(24f), 0)
                }
                alpha = 0f
            }
            card.addView(TextView(ctx).apply {
                text = "KONEC ${ending.end.roman}"; textSize = 13f; letterSpacing = 0.4f; gravity = Gravity.CENTER
                setTextColor(0xCCF2D58A.toInt()); typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            })
            card.addView(TextView(ctx).apply {
                text = ending.end.title; textSize = 38f; gravity = Gravity.CENTER
                setTextColor(0xFFFFF6E0.toInt()); typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
                setShadowLayer(16f, 0f, 0f, 0x88F2D58A.toInt())
                setPadding(0, dp(6f), 0, dp(18f))
            })
            card.addView(TextView(ctx).apply {
                text = ending.epilogue; textSize = 16f; gravity = Gravity.CENTER
                setTextColor(0xFFE6DCC4.toInt()); typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
                setLineSpacing(dp(4f).toFloat(), 1f)
            })
            card.addView(TextView(ctx).apply {
                text = Finale.BACK; textSize = 15f; gravity = Gravity.CENTER
                setTextColor(0xFF1A1410.toInt()); typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
                setBackgroundColor(0xFFF2D58A.toInt())
                setPadding(dp(22f), dp(12f), dp(22f), dp(12f))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(40f) }
                setOnClickListener {
                    isEnabled = false
                    shell.animate().alpha(0f).setDuration(1200).withEndAction { fx.stop(); root.removeView(shell); onDone() }.start()
                }
            })
            shell.addView(card)
            card.animate().alpha(1f).setStartDelay(900).setDuration(1600).start()
        }
        if (videoRes != 0) {
            fx.visibility = View.INVISIBLE
            val video = android.widget.VideoView(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
            }
            shell.addView(video, 1)
            var ended = false
            val end = {
                if (!ended) {
                    ended = true
                    runCatching { video.stopPlayback() }
                    video.animate().alpha(0f).setDuration(500).withEndAction { shell.removeView(video); fx.visibility = View.VISIBLE; finish() }.start()
                }
            }
            hint.text = "klepni znovu – přeskočit ▸▸"
            var armed = 0L
            shell.setOnClickListener {
                val now = System.currentTimeMillis()
                if (now - armed < 2500) end() else { armed = now; hint.animate().alpha(1f).setDuration(200).withEndAction {
                    hint.animate().alpha(0f).setStartDelay(1800).setDuration(400).start() }.start() }
            }
            video.setOnCompletionListener { end() }
            video.setOnErrorListener { _, _, _ -> end(); true }
            video.setVideoURI(android.net.Uri.parse("android.resource://${ctx.packageName}/$videoRes"))
            video.setOnPreparedListener { video.start() }
            return
        }
        shell.setOnClickListener {
            if (shown < body().length) { typer?.cancel(); shown = body().length; pageView.text = body(); hint.animate().alpha(1f).setDuration(300).start(); return@setOnClickListener }
            page++
            if (page >= ending.pages.size) finish() else type()
        }
        shell.postDelayed({ type() }, 1200)
    }
}

/** Živé pozadí konce: pixelové částice a barevný přechod, jeden pohled přes celou obrazovku. */
class EndingFxView(context: Context, private val end: Finale.End) : View(context) {
    private val paint = Paint()
    private val start = System.nanoTime()
    private val px = context.resources.displayMetrics.density * 3f
    private var running = true
    private val seeds = List(90) { k -> Triple(((k * 7919) % 1000) / 1000f, ((k * 104729) % 1000) / 1000f, ((k * 31) % 17) / 17f) }

    fun stop() { running = false }

    private fun bg(top: Int, bottom: Int, canvas: Canvas) {
        val h = height.toFloat()
        val steps = 16                                                       // pásy jako v pixel artu, ne hladký gradient
        for (i in 0 until steps) {
            val f = i / (steps - 1f)
            paint.color = blend(top, bottom, f)
            canvas.drawRect(0f, h * i / steps, width.toFloat(), h * (i + 1) / steps + 1, paint)
        }
    }

    private fun blend(a: Int, b: Int, f: Float): Int = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * f).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * f).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * f).toInt())

    private fun dot(canvas: Canvas, x: Float, y: Float, color: Int, size: Int = 1) {
        paint.color = color
        val sx = (x / px).toInt() * px; val sy = (y / px).toInt() * px
        canvas.drawRect(sx, sy, sx + px * size, sy + px * size, paint)
    }

    override fun onDraw(canvas: Canvas) {
        val t = (System.nanoTime() - start) / 1e9f
        val w = width.toFloat(); val h = height.toFloat()
        when (end) {
            Finale.End.SPEAR -> {                                            // tma, svislé kopí, popel stoupá
                bg(0xFF0A0908.toInt(), 0xFF2A1E14.toInt(), canvas)
                paint.color = 0x332A2622
                canvas.drawRect(w / 2 - px, 0f, w / 2 + px, h, paint)
                for ((a, b, c) in seeds) {
                    val y = h - ((b * h + t * (18 + c * 30) * px) % (h + 20))
                    val x = a * w + sin(t * 0.6f + c * 9) * 6 * px
                    dot(canvas, x, y, Color.argb((90 + c * 120).toInt(), 200, 192, 180))
                }
            }
            Finale.End.BLOOM -> {                                            // zlatá voda stoupá, okvětní lístky
                bg(0xFF2A3A30.toInt(), 0xFFE8C878.toInt(), canvas)
                val lvl = h * (1f - (t / 40f).coerceAtMost(0.55f))
                paint.color = 0x55FFE6A0
                canvas.drawRect(0f, lvl, w, h, paint)
                for ((a, b, c) in seeds) {
                    val y = h - ((b * h + t * (10 + c * 22) * px) % (h + 20))
                    val x = (a * w + sin(t * 0.8f + c * 7) * 14 * px + w) % w
                    dot(canvas, x, y, if (c > 0.6f) 0xFFFFF4F8.toInt() else if (c > 0.3f) 0xFFFFC0D0.toInt() else 0xFFFFE8A0.toInt(), if (c > 0.85f) 2 else 1)
                }
            }
            Finale.End.WISH -> {                                             // modrá tma, jiskry Lumenců se rozlétají vzhůru
                bg(0xFF04081A.toInt(), 0xFF10264A.toInt(), canvas)
                for ((a, b, c) in seeds) {
                    val life = ((t * (0.08f + c * 0.08f) + b) % 1f)
                    val x = w / 2 + (a - 0.5f) * w * life * 1.6f
                    val y = h * 0.62f - life * h * 0.7f
                    val al = (255 * (1 - life)).toInt()
                    dot(canvas, x, y, if (c > 0.5f) Color.argb(al, 160, 220, 255) else Color.argb(al, 240, 250, 255), if (c > 0.9f) 2 else 1)
                }
                paint.color = Color.argb((120 + 60 * sin(t * 2f)).toInt(), 200, 236, 255)
                val r = px * 3
                canvas.drawRect(w / 2 - r, h * 0.62f - r, w / 2 + r, h * 0.62f + r, paint)
            }
            Finale.End.SECRET -> {                                           // noc se pomalu mění v ráno, hvězdy blednou
                val f = (t / 45f).coerceAtMost(1f)
                bg(blend(0xFF050816.toInt(), 0xFF8AB4D8.toInt(), f), blend(0xFF141A30.toInt(), 0xFFFFD8A0.toInt(), f), canvas)
                for ((a, b, c) in seeds) {
                    val tw = (0.5f + 0.5f * sin(t * (1 + c * 2) + a * 20))
                    dot(canvas, a * w, b * h * 0.7f, Color.argb((200 * tw * (1 - f)).toInt(), 255, 250, 230))
                }
                paint.color = Color.argb((180 * f).toInt(), 255, 236, 180)
                val sunR = px * 10
                canvas.drawRect(w / 2 - sunR, h - sunR * (0.2f + f), w / 2 + sunR, h, paint)
            }
        }
        if (running) postInvalidateDelayed(50)
    }
}
