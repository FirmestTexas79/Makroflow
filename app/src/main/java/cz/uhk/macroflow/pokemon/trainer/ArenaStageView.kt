package cz.uhk.macroflow.pokemon.trainer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.View
import cz.uhk.macroflow.pokemon.arena.ArenaTheme
import cz.uhk.macroflow.pokemon.arena.Arenas
import kotlin.math.sin

/**
 * Pozadí obrazovky Arény (docs/adr/0078): 3D gladiátorská aréna přes celou šířku nahoře a na písku
 * dva Makromoni čelem k sobě – vlevo tvůj parťák, vpravo vedoucí Makromon vybraného soupeře.
 * Aréna se vykreslí ve vlákně (nejdřív hrubý náhled, pak plná), Makromoni lehce pohupují.
 */
class ArenaStageView(context: Context) : View(context) {

    companion object { private const val SWAP_MS = 2600L }

    private var bg: Bitmap? = null
    private var extra = -1
    /** Makromoni na písku se po chvíli střídají (tvůj tým vlevo, soupeř/soupeři vpravo). */
    private var left: List<Bitmap> = emptyList()
    private var right: List<Bitmap> = emptyList()
    private var rightSince = 0L
    private val crisp = Paint().apply { isFilterBitmap = false }
    private val smooth = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55000000 }
    private val handler = Handler(Looper.getMainLooper())
    private val start = System.currentTimeMillis()
    private val tick = object : Runnable { override fun run() { invalidate(); handler.postDelayed(this, 50) } }

    /** Výška, kterou aréna zabere (horní část obrazovky). */
    var stageHeight = 0
        private set

    fun setFighters(l: List<Bitmap>, r: List<Bitmap>) { left = l; setRight(r) }
    fun setRight(r: List<Bitmap>) { right = r; rightSince = System.currentTimeMillis(); invalidate() }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); handler.post(tick) }
    override fun onDetachedFromWindow() { handler.removeCallbacksAndMessages(null); super.onDetachedFromWindow() }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        // aréna zabere ~48 % výšky; nad scénu se přidá obloha, aby poměr seděl na šířku displeje
        stageHeight = (h * 0.48f).toInt()
        val want = (Arenas.W * stageHeight.toFloat() / w).toInt()
        val ex = (want - Arenas.H).coerceIn(0, 900)
        if (ex == extra) return
        extra = ex
        Thread {
            runCatching {
                val (pw, ph) = Arenas.previewSize(ex)
                val pre = Arenas.renderPreview(ArenaTheme.COLOSSEUM, 7, ex)
                val pb = Bitmap.createBitmap(pre, pw, ph, Bitmap.Config.ARGB_8888)
                handler.post { if (ex == extra && bg == null) { bg = pb; invalidate() } }
                val full = Arenas.render(ArenaTheme.COLOSSEUM, 7, ex)
                val fb = Bitmap.createBitmap(full, Arenas.W, Arenas.H + ex, Bitmap.Config.ARGB_8888)
                handler.post { if (ex == extra) { bg = fb; invalidate() } }
            }
        }.start()
    }

    override fun onDraw(c: Canvas) {
        c.drawColor(0xFFF2D6A8.toInt())
        val b = bg ?: return
        // aréna přes celou šířku, zarovnaná nahoru; měřítko pixelů arény → obrazovka
        val k = width.toFloat() / Arenas.W
        val imgH = (Arenas.H + extra) * k
        c.drawBitmap(b, null, RectF(0f, 0f, width.toFloat(), imgH), crisp)
        val t = (System.currentTimeMillis() - start) / 1000f
        // oba na stejné úrovni: soupeř na svém valu, ty naproti zrcadlově
        val footY = (extra + Arenas.ENEMY_Y) * k
        val size = 108f * k
        val now = System.currentTimeMillis()
        drawFighter(c, pick(left, now - start), Arenas.PLAYER_X * k, footY + 6f * k, size, mirror = true, bob = sin(t * 3.1f) * 2.2f * k)
        drawFighter(c, pick(right, now - rightSince + 1300), Arenas.ENEMY_X * k, footY, size, mirror = false, bob = sin(t * 3.1f + 1.7f) * 2.2f * k)
    }

    /** Kdo právě stojí na písku a jak moc je vidět (krátké prolnutí při výměně). */
    private fun pick(list: List<Bitmap>, ms: Long): Pair<Bitmap, Float>? {
        if (list.isEmpty()) return null
        val slot = ms / SWAP_MS
        val inSlot = (ms % SWAP_MS).toFloat()
        val a = if (list.size == 1) 1f else minOf(1f, inSlot / 250f, (SWAP_MS - inSlot) / 250f)
        return list[(slot % list.size).toInt()] to a.coerceIn(0f, 1f)
    }

    private fun drawFighter(c: Canvas, who: Pair<Bitmap, Float>?, cx: Float, footY: Float, h: Float, mirror: Boolean, bob: Float) {
        val (bmp, alpha) = who ?: return
        smooth.alpha = (alpha * 255).toInt(); shadow.alpha = (alpha * 0x55).toInt()
        val w = h * bmp.width / bmp.height
        c.drawOval(cx - w * 0.38f, footY - h * 0.05f, cx + w * 0.38f, footY + h * 0.05f, shadow)
        val r = RectF(cx - w / 2f, footY - h + bob, cx + w / 2f, footY + bob)
        if (mirror) { c.save(); c.scale(-1f, 1f, cx, 0f) }
        c.drawBitmap(bmp, null, r, smooth)
        if (mirror) c.restore()
    }
}
