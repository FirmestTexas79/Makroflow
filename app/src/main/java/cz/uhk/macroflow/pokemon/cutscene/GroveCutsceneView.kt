package cz.uhk.macroflow.pokemon.cutscene

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import cz.uhk.macroflow.R
import cz.uhk.macroflow.pokemon.story.SecretGrove
import kotlin.math.sin

/**
 * Cutscéna „Modré světlo v trní“ (docs/adr/0098) – po Mydrusově zmínce o modrých houbách. Hvozd poprvé po dlouhé době
 * usne, kamera sjede od mýtiny k trní na západě lesa a tam se jedna po druhé rozsvítí modré houby. Světla se zvednou
 * a ukážou cestu do trní; na okamžik mezi stromy stojí postava s parožím (Elderan) a zašeptá „…konečně…“.
 */
class GroveCutsceneView(context: Context) : CutsceneView(context) {

    override val world: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.forest, BitmapFactory.Options().apply { inScaled = false })
    override val totalMs = 25_000f

    private val tx = SecretGrove.FOREST_X.toFloat()
    private val ty = SecretGrove.FOREST_Y.toFloat()

    override val shots = listOf(
        Shot(0f, 150f, 70f, 130f), Shot(4_600f, 128f, 190f, 120f), Shot(8_800f, tx, ty - 8f, 92f),
        Shot(13_000f, tx - 6f, ty - 4f, 80f), Shot(17_200f, tx - 16f, ty - 6f, 66f), Shot(22_000f, tx - 10f, ty - 4f, 70f)
    )

    override val captions = listOf(
        Caption(600f, 4_000f, "Hniloba odešla. Hvozd poprvé po dlouhé době usnul klidně."),
        Caption(4_300f, 8_400f, "Na západě lesa, kousek od tiché cesty, stojí trní, kterým by neprolezla ani myš."),
        Caption(8_700f, 12_800f, "Té noci se v něm rozsvítilo první modré světlo. Pak druhé. Třetí."),
        Caption(13_100f, 16_800f, "Světla se zvedla a čekala. Jako by někdo ukazoval cestu."),
        Caption(17_100f, 19_800f, "Na okamžik tam někdo stál. Paroží. Světlo. Žádná tvář."),
        Caption(20_100f, 22_000f, "„…konečně…“"),
        Caption(22_200f, 24_300f, "Modré houby rostou jen tam, kde odpočívá duše.")
    )

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private fun light(c: Canvas, x: Float, y: Float, r: Float, color: Int, a: Float) {
        if (a <= 0f || r <= 0f) return
        glow.shader = RadialGradient(x, y, r, intArrayOf(color, color and 0x00FFFFFF), null, Shader.TileMode.CLAMP)
        glow.alpha = (a * 255).toInt().coerceIn(0, 255)
        c.drawCircle(x, y, r, glow)
    }

    override fun drawActors(c: Canvas, t: Float, toX: (Float) -> Float, toY: (Float) -> Float, scale: Float) {
        // noc padá na les
        val night = (t / 5_000f).coerceIn(0f, 1f) * 0.55f
        fill.shader = null; fill.color = 0xFF050A1A.toInt(); fill.alpha = (night * 255).toInt()
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill)

        // modré houby v trní, jedna po druhé
        SecretGrove.FOREST_MUSHROOMS.forEachIndexed { i, (mx, my) ->
            val on = ((t - 9_000f - i * 1_000f) / 500f).coerceIn(0f, 1f)
            if (on <= 0f) return@forEachIndexed
            val p = 0.8f + 0.2f * sin(t / 260f + i)
            light(c, toX(mx.toFloat()), toY(my.toFloat()), scale * 7f * p, 0xCC5AB8FF.toInt(), on)
            fill.color = 0xFFCDEBFF.toInt(); fill.alpha = (on * 255).toInt()
            c.drawCircle(toX(mx.toFloat()), toY(my.toFloat()), scale * 0.9f, fill)
        }

        // světla se zvednou a seřadí do cesty na západ, do mezery v trní
        for (i in 0 until 7) {
            val start = 13_000f + i * 280f
            val k = ((t - start) / 2_600f).coerceIn(0f, 1f)
            if (k <= 0f) continue
            val (mx, my) = SecretGrove.FOREST_MUSHROOMS[i % SecretGrove.FOREST_MUSHROOMS.size]
            val e = k * k * (3 - 2 * k)
            val gx = tx - 6f - i * 7f; val gy = ty - 6f + sin(i * 1.3f) * 4f
            val x = mx + (gx - mx) * e + sin(t / 500f + i) * 1.2f
            val y = my - 8f * sin(e * 3.14f) + (gy - my) * e + sin(t / 430f + i * 2f) * 1.0f
            light(c, toX(x), toY(y), scale * 4.2f, 0xBB7FD0FF.toInt(), 0.9f)
            fill.color = Color.WHITE; fill.alpha = 230
            c.drawCircle(toX(x), toY(y), scale * 0.6f, fill)
        }

        // Elderan – bledá postava s parožím za trním, jen na okamžik
        val ghost = when {
            t < 17_100f -> 0f
            t < 18_000f -> (t - 17_100f) / 900f
            t < 19_300f -> 1f - 0.25f * (0.5f + 0.5f * sin(t / 60f))
            else -> (1f - (t - 19_300f) / 900f).coerceIn(0f, 1f)
        }
        if (ghost > 0f) elderan(c, toX(tx - 42f), toY(ty - 2f), scale, ghost)

        // chladný dech: drobné lístky vyletí z trní
        if (t > 20_000f) for (i in 0 until 18) {
            val age = ((t - 20_000f - i * 120f) / 2_200f)
            if (age !in 0f..1f) continue
            val x = tx - 20f + age * (30f + i * 2f); val y = ty - 10f + sin(i * 2.1f + age * 6f) * 6f - age * 8f
            fill.color = 0xFF6FA86A.toInt(); fill.alpha = ((1f - age) * 220).toInt()
            c.drawRect(toX(x), toY(y), toX(x + 1.2f), toY(y + 0.8f), fill)
        }
    }

    private fun elderan(c: Canvas, x: Float, foot: Float, s: Float, a: Float) = ElderanFigure.draw(c, x, foot, s, a)
}

/** Elderan jako postava z modrého světla: tělo, hlava a rozvětvené paroží, bez obličeje (háj, pád Soulorda). */
object ElderanFigure {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val antlers = Path()

    fun draw(c: Canvas, x: Float, foot: Float, s: Float, a: Float) {
        val h = 24f * s
        glow.shader = RadialGradient(x, foot - h * 0.55f, h * 0.9f, intArrayOf(0x667FD0FF, 0x007FD0FF), null, Shader.TileMode.CLAMP)
        glow.alpha = (a * 255).toInt().coerceIn(0, 255)
        c.drawCircle(x, foot - h * 0.55f, h * 0.9f, glow)
        fill.color = 0xFFBFE6FF.toInt(); fill.alpha = (a * 150).toInt().coerceIn(0, 255)
        c.drawOval(x - 4f * s, foot - h * 0.78f, x + 4f * s, foot, fill)
        c.drawCircle(x, foot - h * 0.84f, 2.6f * s, fill)
        line.color = 0xFFDFF3FF.toInt(); line.alpha = (a * 200).toInt().coerceIn(0, 255); line.strokeWidth = s * 0.7f
        antlers.rewind()
        for (side in listOf(-1f, 1f)) {
            val bx = x + side * 1.6f * s; val by = foot - h * 0.92f
            antlers.moveTo(bx, by); antlers.lineTo(bx + side * 3.5f * s, by - 5f * s); antlers.lineTo(bx + side * 5.5f * s, by - 9f * s)
            antlers.moveTo(bx + side * 2.2f * s, by - 3f * s); antlers.lineTo(bx + side * 0.6f * s, by - 7f * s)
            antlers.moveTo(bx + side * 4.4f * s, by - 6.5f * s); antlers.lineTo(bx + side * 7.5f * s, by - 7.5f * s)
        }
        c.drawPath(antlers, line)
    }
}
