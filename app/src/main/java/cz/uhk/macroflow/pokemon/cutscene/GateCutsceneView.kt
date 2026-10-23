package cz.uhk.macroflow.pokemon.cutscene

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import cz.uhk.macroflow.R
import cz.uhk.macroflow.pokemon.cave.SkyPassArt
import cz.uhk.macroflow.pokemon.story.Hunfrey
import kotlin.math.cos
import kotlin.math.sin

/**
 * Cutscéna „Brána se otevírá“ (docs/adr/0099) po vložení Srdce Hvozdu. Prstenec se roztočí, závoj se rozestoupí a
 * na naši stranu pronikne jemný popel; Hunfrey ustoupí. Střih za bránu: Popelavá pláň a v dálce bílé hradby Ozvěny.
 * Střih do Hvozdu: daleko od brány tiše uschne jeden strom – přání vždycky něco stojí.
 */
class GateCutsceneView(context: Context) : CutsceneView(context) {

    private fun bmp(res: Int) = BitmapFactory.decodeResource(resources, res, BitmapFactory.Options().apply { inScaled = false })
    private val pass = bmp(R.drawable.sky_pass)
    private val plain = bmp(R.drawable.ash_plain)
    private val forest = bmp(R.drawable.forest)
    override val world: Bitmap = pass
    override val totalMs = 27_500f

    override fun worldAt(t: Float): Bitmap = when {
        t < CUT1 -> pass
        t < CUT2 -> plain
        else -> forest
    }

    private val gx = SkyPassArt.GATE_X.toFloat()
    private val gy = SkyPassArt.GATE_Y.toFloat()

    override val shots = listOf(
        Shot(0f, gx, gy + 6f, 96f), Shot(4_000f, gx, gy, 70f), Shot(7_400f, gx + 14f, gy + 10f, 96f),
        Shot(CUT1 - 1f, gx + 10f, gy + 6f, 100f),
        // za branou: od Brány světů dolů přes pláň k hradbám Ozvěny
        Shot(CUT1, 80f, 60f, 120f), Shot(15_000f, 80f, 250f, 140f), Shot(CUT2 - 1f, 80f, 390f, 140f),
        // Hvozd
        Shot(CUT2, 90f, 120f, 120f), Shot(23_000f, TREE_X, TREE_Y + 6f, 70f), Shot(27_500f, TREE_X, TREE_Y + 4f, 62f)
    )

    override val captions = listOf(
        Caption(600f, 3_800f, "Srdce Hvozdu zapadlo do lůžka."),
        Caption(4_000f, 7_400f, "Prstenec se roztočil. Závoj, který tu visel staletí, se rozestoupil."),
        Caption(7_600f, 10_700f, "Hunfrey ustoupil o krok. Poprvé nevěděl, co nakreslit."),
        Caption(11_600f, 15_000f, "Na druhé straně ležel kraj, kde i vítr šedne."),
        Caption(15_200f, 18_700f, "A dole, v dálce, bílé hradby. Město, které čekalo."),
        Caption(19_600f, 23_000f, "Hluboko ve Hvozdu, daleko od brány…"),
        Caption(23_200f, 26_800f, "…tiše uschl jeden strom. Přání vždycky něco stojí.")
    )

    private val hunfrey: Bitmap = Bitmap.createScaledBitmap(bmp(R.drawable.makromon_42_hunfrey), 64, 64, false)
    private val pixel = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val dst = RectF()

    private fun light(c: Canvas, x: Float, y: Float, r: Float, color: Int, a: Float) {
        if (a <= 0f || r <= 0f) return
        glow.shader = RadialGradient(x, y, r, intArrayOf(color, color and 0x00FFFFFF), null, Shader.TileMode.CLAMP)
        glow.alpha = (a * 255).toInt().coerceIn(0, 255)
        c.drawCircle(x, y, r, glow)
    }

    override fun drawActors(c: Canvas, t: Float, toX: (Float) -> Float, toY: (Float) -> Float, scale: Float) {
        when {
            t < CUT1 -> gateAct(c, t, toX, toY, scale)
            t < CUT2 -> plainAct(c, t, toX, toY, scale)
            else -> forestAct(c, t, toX, toY, scale)
        }
        for (cut in listOf(CUT1, CUT2)) {
            val dip = 1f - kotlin.math.abs(t - cut) / 700f
            if (dip > 0f) { fill.shader = null; fill.color = Color.BLACK; fill.alpha = (dip * 255).toInt(); c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill) }
        }
    }

    private fun gateAct(c: Canvas, t: Float, toX: (Float) -> Float, toY: (Float) -> Float, s: Float) {
        // Srdce v lůžku pod prstencem
        light(c, toX(SkyPassArt.SOCKET_X.toFloat()), toY(SkyPassArt.SOCKET_Y.toFloat()), s * 6f * (0.8f + 0.2f * sin(t / 200f)), 0xEEFFD86A.toInt(), 1f)
        // runy a prstenec: zelená záře, roztáčející se oblouky závoje
        val open = ((t - 2_500f) / 4_000f).coerceIn(0f, 1f)
        light(c, toX(gx), toY(gy), s * (20f + 14f * open), 0x99B8FFC8.toInt(), 0.4f + 0.6f * open)
        val spin = t / 1000f * (1.5f + open * 5f)
        arc.strokeWidth = s * 1.4f
        for (i in 0 until 6) {
            val r = s * (SkyPassArt.VEIL_R - 2f - i * 2.4f) * (1f - open * 0.35f)
            arc.color = if (i % 2 == 0) 0xFF9CFFD0.toInt() else 0xFF5AD1A8.toInt()
            arc.alpha = ((1f - open * 0.7f) * 220).toInt()
            dst.set(toX(gx) - r, toY(gy) - r, toX(gx) + r, toY(gy) + r)
            c.drawArc(dst, (spin * 57f * (if (i % 2 == 0) 1 else -1) + i * 40f) % 360f, 120f, false, arc)
        }
        // závoj se rozestoupí: střed brány se rozsvítí
        light(c, toX(gx), toY(gy), s * SkyPassArt.VEIL_R * open, 0xEEFFFFFF.toInt(), open * 0.9f)

        // jemný popel proniká branou na naši stranu
        if (t > 5_500f) for (i in 0 until 50) {
            val born = 5_500f + i * 95f
            val age = (t - born) / 3_200f
            if (age !in 0f..1f) continue
            val a = i * 0.9f
            val x = gx + cos(a) * age * (30f + i % 7 * 6f)
            val y = gy + sin(a) * age * 14f + age * 26f
            fill.shader = null; fill.color = 0xFFB9B4AE.toInt(); fill.alpha = ((1f - age) * 200).toInt()
            c.drawCircle(toX(x), toY(y), s * 0.55f, fill)
        }

        // Hunfrey na plošině ustoupí o krok
        val back = ((t - 7_600f) / 900f).coerceIn(0f, 1f)
        val hx = Hunfrey.X + back * 6f; val hy = Hunfrey.Y.toFloat()
        val w = 26f; val k = w / hunfrey.width
        dst.set(toX(hx - w / 2f), toY(hy - 60f * k), toX(hx + w / 2f), toY(hy - 60f * k + hunfrey.height * k))
        c.drawBitmap(hunfrey, null, dst, pixel)
    }

    private fun plainAct(c: Canvas, t: Float, toX: (Float) -> Float, toY: (Float) -> Float, s: Float) {
        // brána z druhé strany se rozsvítí, kraj je šedý; padá popel
        light(c, toX(80f), toY(36f), s * 26f, 0x88B8FFC8.toInt(), 0.8f)
        for (i in 0 until 60) {
            val x = (i * 37 % 160).toFloat() + sin(t / 900f + i) * 6f
            val y = ((i * 71 % 448) + (t - CUT1) / 40f * (0.6f + i % 4 * 0.2f)) % 448f
            fill.shader = null; fill.color = 0xFFCFCAC2.toInt(); fill.alpha = 150
            c.drawCircle(toX(x), toY(y), s * 0.5f, fill)
        }
        // bílé hradby v dálce
        light(c, toX(80f), toY(420f), s * 30f, 0x55FFFFFF, ((t - 15_000f) / 2_000f).coerceIn(0f, 1f))
    }

    private fun forestAct(c: Canvas, t: Float, toX: (Float) -> Float, toY: (Float) -> Float, s: Float) {
        // koruna jednoho stromu zešedne a zhnědne, listí opadá
        val wither = ((t - 22_400f) / 3_000f).coerceIn(0f, 1f)
        if (wither > 0f) {
            glow.shader = RadialGradient(toX(TREE_X), toY(TREE_Y), s * 13f, intArrayOf(0xCC7A6A4E.toInt(), 0x997A6A4E.toInt(), 0x007A6A4E),
                floatArrayOf(0f, 0.7f, 1f), Shader.TileMode.CLAMP)
            glow.alpha = (wither * 255).toInt()
            c.drawCircle(toX(TREE_X), toY(TREE_Y), s * 13f, glow)
            for (i in 0 until 26) {
                val born = 22_600f + i * 140f
                val age = (t - born) / 2_600f
                if (age !in 0f..1f) continue
                val x = TREE_X - 9f + (i * 7 % 18) + sin(age * 9f + i) * 2.5f
                val y = TREE_Y - 4f + age * 26f
                fill.shader = null; fill.color = if (i % 2 == 0) 0xFFA0703A.toInt() else 0xFF8B7B5A.toInt()
                fill.alpha = ((1f - age) * 230).toInt()
                c.drawRect(toX(x), toY(y), toX(x + 1.4f), toY(y + 0.9f), fill)
            }
        }
    }

    companion object {
        private const val CUT1 = 11_200f
        private const val CUT2 = 19_200f
        /** Strom ve Hvozdu, který uschne (art px mapy Hvozdu). */
        private const val TREE_X = 60f
        private const val TREE_Y = 100f
    }
}
