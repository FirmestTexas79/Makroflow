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
import kotlin.math.cos
import kotlin.math.sin

/**
 * Cutscéna „Pád Soulorda“ (docs/adr/0099) po souboji v kořenech Starého dubu. Rudá hniloba se stahuje z kořenů,
 * Soulordova maska praskne a na okamžik pod ní prosvitne světlo s parožím (Elderan – předzvěst háje). Pak zbude
 * popel a ticho, Starý dub se po letech nadechne a v kořenech se rozsvítí Srdce Hvozdu.
 */
class SoulordCutsceneView(context: Context) : CutsceneView(context) {

    override val world: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.forest, BitmapFactory.Options().apply { inScaled = false })
    override val totalMs = 25_500f

    override val shots = listOf(
        Shot(0f, OAK_X, 70f, 84f), Shot(7_600f, OAK_X, 78f, 64f), Shot(11_400f, OAK_X, 80f, 52f),
        Shot(14_400f, OAK_X, 74f, 70f), Shot(17_600f, OAK_X, 56f, 84f), Shot(25_500f, OAK_X, 66f, 74f)
    )

    override val captions = listOf(
        Caption(600f, 3_600f, "Soulord padl ke kořenům Starého dubu."),
        Caption(3_800f, 7_600f, "Rudá hniloba se stahovala z kořenů jako voda z písku."),
        Caption(7_900f, 11_400f, "Jeho maska praskla."),
        Caption(11_600f, 14_200f, "Na okamžik pod ní prosvitlo světlo. Paroží."),
        Caption(14_400f, 17_400f, "Pak zbyl jen popel. A ticho."),
        Caption(17_600f, 21_400f, "Starý dub se po letech nadechl."),
        Caption(21_600f, 25_000f, "V jeho kořenech se rozsvítilo Srdce Hvozdu.")
    )

    private val soulord: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.makromon_26_soulord,
        BitmapFactory.Options().apply { inScaled = false }).let { Bitmap.createScaledBitmap(it, 64, 64, false) }
    private val pixel = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val crack = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val dst = RectF()

    /** Skvrny hniloby kolem kořenů: x, y, poloměr. */
    private val rot = List(22) { i -> floatArrayOf(OAK_X - 26f + (i * 23 % 52), 62f + (i * 17 % 34), 2.5f + (i % 4)) }

    private fun light(c: Canvas, x: Float, y: Float, r: Float, color: Int, a: Float) {
        if (a <= 0f || r <= 0f) return
        glow.shader = RadialGradient(x, y, r, intArrayOf(color, color and 0x00FFFFFF), null, Shader.TileMode.CLAMP)
        glow.alpha = (a * 255).toInt().coerceIn(0, 255)
        c.drawCircle(x, y, r, glow)
    }

    override fun shake(t: Float): Pair<Float, Float> {
        val k = if (t in 7_900f..9_000f) 1f - (t - 7_900f) / 1_100f else 0f
        return (sin(t / 19f) * 10f * k) to (cos(t / 27f) * 7f * k)
    }

    override fun drawActors(c: Canvas, t: Float, toX: (Float) -> Float, toY: (Float) -> Float, s: Float) {
        // šero kořenů, které se po pádu rozjasní
        val gloom = 0.45f * (1f - ((t - 15_000f) / 4_000f).coerceIn(0f, 1f))
        fill.shader = null; fill.color = 0xFF14061A.toInt(); fill.alpha = (gloom * 255).toInt()
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill)

        // rudá hniloba se stahuje k Soulordovi a mizí
        val retreat = ((t - 3_800f) / 4_400f).coerceIn(0f, 1f)
        rot.forEachIndexed { i, r ->
            val x = r[0] + (OAK_X - r[0]) * retreat; val y = r[1] + (FOOT - 6f - r[1]) * retreat
            val a = (1f - retreat * 0.7f) * (1f - ((t - 13_000f) / 1_500f).coerceIn(0f, 1f))
            light(c, toX(x), toY(y), s * r[2] * (1f - retreat * 0.6f) * (0.9f + 0.1f * sin(t / 180f + i)), 0xCCB0283A.toInt(), a)
        }

        // Soulord: třese se, praskne, zmizí v popelu
        val fade = 1f - ((t - 13_600f) / 1_400f).coerceIn(0f, 1f)
        if (fade > 0f) {
            val w = 30f; val k = w / soulord.width
            val jitter = if (t in 3_800f..13_600f) sin(t / 41f) * 0.5f else 0f
            dst.set(toX(OAK_X - w / 2 + jitter), toY(FOOT - 60f * k), toX(OAK_X + w / 2 + jitter), toY(FOOT - 60f * k + soulord.height * k))
            pixel.alpha = (fade * 255).toInt()
            c.drawBitmap(soulord, null, dst, pixel)
            // praskliny přes masku
            val split = ((t - 7_900f) / 1_800f).coerceIn(0f, 1f)
            if (split > 0f) {
                crack.color = 0xFFE6F4FF.toInt(); crack.alpha = (fade * 230).toInt(); crack.strokeWidth = s * 0.5f
                val cx = OAK_X; val cy = FOOT - 21f
                for (j in 0 until 5) {
                    val a = -1.4f + j * 0.7f
                    c.drawLine(toX(cx), toY(cy), toX(cx + cos(a) * 6f * split), toY(cy + sin(a) * 6f * split), crack)
                }
                light(c, toX(cx), toY(cy), s * 6f * split, 0xAA7FD0FF.toInt(), fade)
            }
        }
        // Elderan prosvitne na okamžik
        val ghost = when {
            t < 11_600f -> 0f
            t < 12_300f -> (t - 11_600f) / 700f
            t < 13_400f -> 1f - 0.3f * (0.5f + 0.5f * sin(t / 55f))
            else -> (1f - (t - 13_400f) / 800f).coerceIn(0f, 1f)
        }
        if (ghost > 0f) ElderanFigure.draw(c, toX(OAK_X), toY(FOOT), s, ghost)

        // popel stoupá
        if (t > 13_600f) for (i in 0 until 34) {
            val age = (t - 13_600f - i * 70f) / 3_000f
            if (age !in 0f..1f) continue
            val x = OAK_X - 8f + (i * 5 % 16) + sin(age * 7f + i) * 3f
            val y = FOOT - 10f - age * 34f
            fill.shader = null; fill.color = 0xFF9C9090.toInt(); fill.alpha = ((1f - age) * 200).toInt()
            c.drawCircle(toX(x), toY(y), s * 0.5f, fill)
        }

        // dub se nadechne: zelená záře v koruně; v kořenech jantarové Srdce
        light(c, toX(OAK_X), toY(44f), s * 30f, 0x8899FF88.toInt(), ((t - 17_600f) / 2_500f).coerceIn(0f, 1f) * (0.8f + 0.2f * sin(t / 400f)))
        val heart = ((t - 21_400f) / 1_800f).coerceIn(0f, 1f)
        light(c, toX(OAK_X), toY(72f), s * 9f * (0.85f + 0.15f * sin(t / 220f)), 0xEEFFD86A.toInt(), heart)
    }

    companion object {
        /** Starý dub v art px Hvozdu: střed kmene a pata postavy před kořeny. */
        private const val OAK_X = 92f
        private const val FOOT = 90f
    }
}
