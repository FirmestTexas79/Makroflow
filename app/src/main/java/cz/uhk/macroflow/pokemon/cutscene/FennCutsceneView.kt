package cz.uhk.macroflow.pokemon.cutscene

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import cz.uhk.macroflow.R
import cz.uhk.macroflow.pokemon.story.Fenn
import kotlin.math.sin

/**
 * Cutscéna „Světlo mezi stromy“ (Ztichlý sad, docs/adr/0096): noční sad, světlušky, modrý plamínek putuje
 * od stromu ke stromu a každému dá dobrou noc jménem, které se nedá dočíst. U chalupy se zastaví –
 * je to plamen na hlavě zahradníka Fenna (Kendl) – a otočí se k hráči.
 */
class FennCutsceneView(context: Context) : CutsceneView(context) {

    override val world: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.quiet_orchard_night,
        BitmapFactory.Options().apply { inScaled = false })
    override val totalMs = 22_500f

    override val shots = listOf(
        Shot(0f, 80f, 70f, 120f), Shot(4_500f, 80f, 150f, 110f), Shot(8_000f, 60f, 200f, 100f),
        Shot(11_000f, 100f, 200f, 100f), Shot(14_000f, 85f, 250f, 95f), Shot(17_000f, 100f, 282f, 80f),
        Shot(20_500f, Fenn.X.toFloat(), 292f, 62f)
    )

    override val captions = Fenn.CUTSCENE_LINES.let { l ->
        listOf(Caption(700f, 4_300f, l[0]), Caption(4_700f, 7_800f, l[1]), Caption(8_100f, 10_800f, l[2]),
            Caption(11_100f, 13_800f, l[3]), Caption(14_100f, 16_800f, l[4]), Caption(17_600f, 21_200f, l[5]))
    }

    private val fennActor = FennActor(context)
    private val flameHomeX get() = fennActor.flameX
    private val flameHomeY get() = fennActor.flameY

    /** Pouť plamínku: (čas, x, y); mezi body se plynule přesouvá, u stromu chvíli postojí. */
    private val path = listOf(
        Triple(4_800f, 30f, 150f), Triple(8_000f, 42f, 196f), Triple(8_800f, 42f, 194f),
        Triple(11_000f, 120f, 196f), Triple(11_800f, 120f, 194f), Triple(14_000f, 80f, 258f),
        Triple(14_800f, 80f, 256f), Triple(17_000f, flameHomeX, flameHomeY)
    )

    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val flies = List(36) { i ->
        floatArrayOf(10f + (i * 37 % 140), 40f + (i * 71 % 270), (i * 0.73f) % 6.28f, 0.6f + (i % 5) * 0.15f)
    }

    private fun flameAt(t: Float): Pair<Float, Float> {
        if (t <= path.first().first) return path.first().second to path.first().third
        val i = path.indexOfLast { it.first <= t }
        val a = path[i]; val b = path.getOrNull(i + 1) ?: return a.second to a.third
        val k = ((t - a.first) / (b.first - a.first)).coerceIn(0f, 1f).let { it * it * (3 - 2 * it) }
        return (a.second + (b.second - a.second) * k) to (a.third + (b.third - a.third) * k + sin(t / 260f) * 1.2f * (1 - k * k))
    }

    override fun drawActors(c: Canvas, t: Float, toX: (Float) -> Float, toY: (Float) -> Float, scale: Float) {
        // světlušky
        for (f in flies) {
            val x = f[0] + sin(t / 1900f * f[3] + f[2]) * 6f
            val y = f[1] + sin(t / 2600f * f[3] + f[2] * 1.7f) * 4f
            val b = (0.5f + 0.5f * sin(t / 420f * f[3] + f[2] * 3f)).coerceIn(0f, 1f)
            dot.color = 0xFFF2FF9A.toInt(); dot.alpha = (b * 220).toInt()
            c.drawCircle(toX(x), toY(y), scale * 0.55f, dot)
            dot.alpha = (b * 50).toInt()
            c.drawCircle(toX(x), toY(y), scale * 1.8f, dot)
        }
        // Fenn se zjeví pod plamenem
        val fennA = ((t - 16_600f) / 1_600f).coerceIn(0f, 1f)
        if (fennA > 0f) fennActor.draw(c, toX, toY, fennA)
        // plamínek (po splynutí s Fennem z něj zbude jen záře nad svíčkou)
        val appear = ((t - 4_800f) / 900f).coerceIn(0f, 1f)
        if (appear > 0f) {
            val (fx, fy) = flameAt(t)
            val flick = 1f + 0.12f * sin(t / 70f) + 0.08f * sin(t / 31f)
            val r = scale * 7f * flick
            glow.shader = RadialGradient(toX(fx), toY(fy), r, intArrayOf(0x889CC8FF.toInt(), 0x339CC8FF, 0x009CC8FF),
                floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
            glow.alpha = (appear * 255).toInt()
            c.drawCircle(toX(fx), toY(fy), r, glow)
            if (fennA < 1f) {
                dot.color = 0xFFE8F4FF.toInt(); dot.alpha = ((1f - fennA) * appear * 255).toInt()
                c.drawCircle(toX(fx), toY(fy), scale * 1.3f * flick, dot)
            }
        }
    }

}

/** Zahradník Fenn (Kendl z Makrodexu, 64 px pixel artu) na mapě sadu – vysoký jako ve hře, pata na Fenn.X/Y. */
class FennActor(context: Context) {
    private val bmp: Bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.makromon_43_kendl,
        BitmapFactory.Options().apply { inScaled = false }).let { Bitmap.createScaledBitmap(it, it.width / 10, it.height / 10, false) }
    private val k = ART_H / bmp.height
    private val foot: Int
    /** Špička plamene na hlavě (art px sadu). */
    val flameX: Float
    val flameY: Float
    private val paint = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
    private val dst = RectF()

    init {
        fun opaqueRow(y: Int) = (0 until bmp.width).any { bmp.getPixel(it, y) ushr 24 != 0 }
        var f = bmp.height - 1
        while (f > 0 && !opaqueRow(f)) f--
        foot = f
        var top = 0
        while (top < bmp.height - 1 && !opaqueRow(top)) top++
        val xs = (0 until bmp.width).filter { bmp.getPixel(it, top) ushr 24 != 0 }
        flameX = Fenn.X + (xs.average().toFloat() - bmp.width / 2f) * k
        flameY = Fenn.Y - (foot + 1 - (top + 4f)) * k
    }

    fun draw(c: Canvas, toX: (Float) -> Float, toY: (Float) -> Float, alpha: Float, dx: Float = 0f) {
        val w = bmp.width * k; val h = bmp.height * k
        val left = Fenn.X - w / 2f + dx; val top = Fenn.Y - (foot + 1) * k
        dst.set(toX(left), toY(top), toX(left + w), toY(top + h))
        paint.alpha = (alpha * 255).toInt()
        c.drawBitmap(bmp, null, dst, paint)
    }

    companion object { const val ART_H = 25f }
}
