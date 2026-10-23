package cz.uhk.macroflow.pokemon.cutscene

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import cz.uhk.macroflow.R
import kotlin.math.cos
import kotlin.math.sin

/**
 * Cutscéna „Oči“ (docs/adr/0098) po souboji s legendou na vrcholu Hor. Krystaly v lůžkách se rozhoří a jejich světlo
 * stoupá do prázdných očnic Drakirry. Po staletích vidí – nejdřív hráče, pak malou béžovou veverku vedle něj.
 * Není to vztek, je to strach: zařve, smete hráče a vzlétne. Střih na Nebeský průsmyk: přeletí Bránu světů
 * a stočí se dolů nad Hvozd, kam za ní padá žhavý popel.
 */
class DrakirraCutsceneView(context: Context) : CutsceneView(context) {

    private fun scaled(res: Int, div: Int): Bitmap = BitmapFactory.decodeResource(resources, res,
        BitmapFactory.Options().apply { inScaled = false }).let { Bitmap.createScaledBitmap(it, it.width / div, it.height / div, false) }

    /** Vrchol Hor (mountains.png je 4× zvětšená, 172 × 384 art px) a Nebeský průsmyk. */
    private val peak = scaled(R.drawable.mountains, 4)
    private val sky = BitmapFactory.decodeResource(resources, R.drawable.sky_pass, BitmapFactory.Options().apply { inScaled = false })
    override val world: Bitmap = peak
    override val totalMs = 33_000f

    override fun worldAt(t: Float): Bitmap = if (t < CUT) peak else sky

    override val shots = listOf(
        Shot(0f, 86f, 78f, 112f), Shot(7_200f, 86f, 60f, 84f), Shot(10_400f, 90f, 46f, 62f),
        Shot(12_600f, 92f, 92f, 64f), Shot(16_200f, 90f, 90f, 66f), Shot(17_400f, 86f, 74f, 110f),
        Shot(CUT - 1f, 86f, 70f, 120f),
        // Nebeský průsmyk
        Shot(CUT, 80f, 168f, 150f), Shot(27_000f, 78f, 140f, 140f), Shot(33_000f, 70f, 150f, 150f)
    )

    override val captions = listOf(
        Caption(600f, 3_600f, "Vrchol Hor. Krystaly v lůžkách se rozhořely."),
        Caption(3_900f, 7_200f, "Modrý. Rudý. Světlo stoupalo vzhůru – do prázdných očnic."),
        Caption(7_500f, 10_500f, "Drakirra otevřela oči. Po staletích viděla."),
        Caption(10_800f, 13_600f, "Nejdřív tebe."),
        Caption(13_900f, 16_600f, "Pak malou béžovou veverku po tvém boku."),
        Caption(16_900f, 19_300f, "Nebyl to vztek. Byl to strach."),
        Caption(21_200f, 24_500f, "Přeletěla Bránu světů, jako by ji něco pronásledovalo…"),
        Caption(24_800f, 28_200f, "…a stočila se dolů, nad Hvozd."),
        Caption(28_500f, 31_800f, "Kam dopadl popel z jejích křídel, začal les rudnout.")
    )

    private val dragon = scaled(R.drawable.makromon_19_drakirra, 10)     // 64 px, oči na (37, 30) a (44, 29.5)
    private val spirra = scaled(R.drawable.makromon_12_spirra, 10)
    private val hero: Bitmap? = runCatching {
        context.assets.open("hero/idle_s.png").use { BitmapFactory.decodeStream(it) }?.let { Bitmap.createBitmap(it, 0, 0, 64, 40) }
    }.getOrNull()

    private val pixel = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dst = RectF()
    private val silhouette = PorterDuffColorFilter(0xFF1A1030.toInt(), PorterDuff.Mode.SRC_ATOP)

    override fun shake(t: Float): Pair<Float, Float> {
        val k = when {
            t in 16_900f..18_600f -> 1f - (t - 16_900f) / 1_700f
            else -> 0f
        }
        return (sin(t / 23f) * 14f * k) to (cos(t / 31f) * 10f * k)
    }

    /** Bitmapa s patou na (x, footY) a šířkou w art px. */
    private fun draw(c: Canvas, b: Bitmap, x: Float, footY: Float, w: Float, foot: Float, toX: (Float) -> Float, toY: (Float) -> Float,
                     alpha: Float = 1f, flip: Boolean = false) {
        val k = w / b.width; val h = b.height * k
        val top = footY - foot * k
        dst.set(toX(x - w / 2), toY(top), toX(x + w / 2), toY(top + h))
        pixel.alpha = (alpha * 255).toInt()
        if (flip) { c.save(); c.scale(-1f, 1f, dst.centerX(), dst.centerY()) }
        c.drawBitmap(b, null, dst, pixel)
        if (flip) c.restore()
    }

    private fun light(c: Canvas, x: Float, y: Float, r: Float, color: Int, a: Float) {
        if (a <= 0f || r <= 0f) return
        glow.shader = RadialGradient(x, y, r, intArrayOf(color, color and 0x00FFFFFF), null, Shader.TileMode.CLAMP)
        glow.alpha = (a * 255).toInt().coerceIn(0, 255)
        c.drawCircle(x, y, r, glow)
    }

    override fun drawActors(c: Canvas, t: Float, toX: (Float) -> Float, toY: (Float) -> Float, scale: Float) {
        if (t < CUT) peakAct(c, t, toX, toY, scale) else skyAct(c, t, toX, toY, scale)
        // střih: krátce do černé
        val dip = 1f - kotlin.math.abs(t - CUT) / 700f
        if (dip > 0f) { fill.shader = null; fill.color = Color.BLACK; fill.alpha = (dip * 255).toInt(); c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill) }
    }

    private fun peakAct(c: Canvas, t: Float, toX: (Float) -> Float, toY: (Float) -> Float, s: Float) {
        val pulse = 0.75f + 0.25f * sin(t / 300f)
        // krystaly v lůžkách
        light(c, toX(82f), toY(46f), s * 9f * pulse, 0xCC4FC3F7.toInt(), 1f)
        light(c, toX(90f), toY(46f), s * 9f * pulse, 0xCCFF5252.toInt(), 1f)

        // Drakirra: vznáší se před branou, po řevu vzlétne
        val launch = ((t - 18_300f) / 1_500f).coerceIn(0f, 1f)
        val dx = 86f; val dy = 70f + sin(t / 650f) * 1.5f - launch * launch * 120f
        val w = 70f + launch * 20f
        val k = w / dragon.width
        val awake = ((t - 7_500f) / 1_200f).coerceIn(0f, 1f)
        pixel.colorFilter = if (awake < 1f) PorterDuffColorFilter(Color.argb(((1f - awake) * 140).toInt(), 10, 8, 24), PorterDuff.Mode.SRC_ATOP) else null
        draw(c, dragon, dx, dy, w, 56f, toX, toY)
        pixel.colorFilter = null
        val eyeL = (dx + (37f - 32f) * k) to (dy - (56f - 30f) * k)
        val eyeR = (dx + (44f - 32f) * k) to (dy - (56f - 29.5f) * k)

        // světlo stoupá z lůžek do očnic
        val beam = ((t - 3_900f) / 3_300f).coerceIn(0f, 1f)
        if (beam > 0f && awake < 1f) {
            fill.shader = null; fill.strokeWidth = s * 1.1f; fill.style = Paint.Style.STROKE
            fill.color = 0xFF4FC3F7.toInt(); fill.alpha = ((1f - awake) * 220).toInt()
            c.drawLine(toX(82f), toY(46f), toX(82f + (eyeL.first - 82f) * beam), toY(46f + (eyeL.second - 46f) * beam), fill)
            fill.color = 0xFFFF5252.toInt(); fill.alpha = ((1f - awake) * 220).toInt()
            c.drawLine(toX(90f), toY(46f), toX(90f + (eyeR.first - 90f) * beam), toY(46f + (eyeR.second - 46f) * beam), fill)
            fill.style = Paint.Style.FILL
        }
        // oči: modré a rudé
        if (awake > 0f) {
            val flare = 1f + if (t in 16_900f..18_300f) 1.2f * sin((t - 16_900f) / 1_400f * 3.14f) else 0f
            light(c, toX(eyeL.first), toY(eyeL.second), s * 3.2f * flare, 0xEE4FC3F7.toInt(), awake)
            light(c, toX(eyeR.first), toY(eyeR.second), s * 3.2f * flare, 0xEEFF5252.toInt(), awake)
        }

        // hráč a Spirra; řev hráče smete dozadu, Spirra se schová
        val blown = ((t - 17_000f) / 900f).coerceIn(0f, 1f)
        val hy = 104f + blown * 9f
        hero?.let { draw(c, it, 86f, hy + 2f, 64f * 0.8f, 38f, toX, toY, 1f - blown * 0.25f) }
        val hide = ((t - 15_400f) / 900f).coerceIn(0f, 1f)
        draw(c, spirra, 99f - hide * 6f, hy + 1f, 15f, 60f, toX, toY)

        // prach a vítr od křídel
        if (t > 16_900f) for (i in 0 until 28) {
            val a = i * 0.224f
            val d = ((t - 16_900f) / 1_800f + (i % 5) * 0.07f) * 70f
            val life = (1f - (t - 16_900f) / 2_400f).coerceIn(0f, 1f)
            fill.shader = null; fill.color = 0xFFE8D2A0.toInt(); fill.alpha = (life * 180).toInt()
            c.drawCircle(toX(dx + cos(a) * d), toY(66f + sin(a) * d * 0.5f), s * (0.7f + (i % 3) * 0.3f), fill)
        }
    }

    /** Nebeský průsmyk: tmavá silueta s modrým a rudým okem přeletí bránu a stočí se dolů; padá žhavý popel. */
    private fun skyAct(c: Canvas, t: Float, toX: (Float) -> Float, toY: (Float) -> Float, s: Float) {
        val path = listOf(
            floatArrayOf(CUT, 84f, 300f, 46f), floatArrayOf(23_600f, 80f, 175f, 30f), floatArrayOf(25_200f, 104f, 82f, 22f),
            floatArrayOf(26_400f, 48f, 66f, 22f), floatArrayOf(28_200f, 24f, 210f, 50f), floatArrayOf(29_400f, 0f, 380f, 90f)
        )
        val i = path.indexOfLast { it[0] <= t }.coerceAtLeast(0)
        val a = path[i]; val b = path.getOrNull(i + 1) ?: a
        val k = if (b === a) 1f else ((t - a[0]) / (b[0] - a[0])).coerceIn(0f, 1f)
        val e = k * k * (3 - 2 * k)
        val x = a[1] + (b[1] - a[1]) * e; val y = a[2] + (b[2] - a[2]) * e; val w = a[3] + (b[3] - a[3]) * e
        val flip = b[1] < a[1]                         // letí doleva → otočit
        if (t < 29_400f) {
            pixel.colorFilter = silhouette
            draw(c, dragon, x, y + w * 0.4f, w, 56f, toX, toY, 0.95f, flip)
            pixel.colorFilter = null
            val kk = w / dragon.width
            val ex = if (flip) -1f else 1f
            light(c, toX(x + ex * 5f * kk), toY(y + w * 0.4f - 26f * kk), s * 2.2f, 0xEE4FC3F7.toInt(), 1f)
            light(c, toX(x + ex * 12f * kk), toY(y + w * 0.4f - 26.5f * kk), s * 2.2f, 0xEEFF5252.toInt(), 1f)
        }
        // žhavý popel padá ze stopy letu dolů nad Hvozd
        if (t > 25_500f) for (j in 0 until 40) {
            val born = 25_500f + j * 110f
            if (t < born) continue
            val age = (t - born) / 3_000f
            if (age > 1f) continue
            val px = 104f - j * 2.2f + sin(j * 1.7f + t / 500f) * 3f
            val py = 80f + j * 4f + age * 90f
            fill.shader = null; fill.color = if (j % 3 == 0) 0xFFFFB04A.toInt() else 0xFFE5533D.toInt()
            fill.alpha = ((1f - age) * 230).toInt()
            c.drawCircle(toX(px), toY(py), s * 0.8f, fill)
        }
    }

    companion object {
        /** Střih z vrcholu na Nebeský průsmyk. */
        private const val CUT = 20_100f
    }
}
