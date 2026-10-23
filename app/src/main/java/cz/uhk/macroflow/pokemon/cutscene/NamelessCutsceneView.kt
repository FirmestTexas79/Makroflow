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
import cz.uhk.macroflow.pokemon.story.Fenn
import kotlin.math.cos
import kotlin.math.sin

/**
 * Cutscéna „Bezejmenní“ (finále questu Fenna, docs/adr/0097): noc bez měsíce, světlušky zhasínají jedna po druhé,
 * sad zahalí mlha a z ní ke chalupě přijdou postavy bez tváří. Obklíčí Fenna, jehož plamen se zmenšuje a třese.
 * Končí otázkou „Čí jméno si vezmeme?“ – pak hráč volí konec.
 */
class NamelessCutsceneView(context: Context) : CutsceneView(context) {

    override val world: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.quiet_orchard_night,
        BitmapFactory.Options().apply { inScaled = false })
    override val totalMs = 21_000f

    override val shots = listOf(
        Shot(0f, 90f, 240f, 120f), Shot(6_000f, 96f, 262f, 104f), Shot(14_000f, Fenn.X.toFloat(), 288f, 72f),
        Shot(19_000f, Fenn.X.toFloat(), 292f, 56f)
    )

    override val captions = Fenn.FINALE_LINES.let { l ->
        listOf(Caption(800f, 4_000f, l[0]), Caption(4_200f, 7_600f, l[1]), Caption(7_900f, 11_600f, l[2]),
            Caption(11_900f, 14_800f, l[3]), Caption(15_000f, 17_600f, l[4]), Caption(17_900f, 20_400f, l[5]))
    }

    private val fenn = FennActor(context)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val oval = RectF()

    /** Světlušky: x, y, fáze, tempo – každá zhasne o kus později. */
    private val flies = List(36) { i -> floatArrayOf(10f + (i * 37 % 140), 150f + (i * 71 % 160), (i * 0.73f) % 6.28f, 0.6f + (i % 5) * 0.15f) }

    /** Bezejmenní: odkud přijdou (art px) a úhel, pod kterým obklíčí Fenna. */
    private val nameless = List(6) { i ->
        val start = listOf(0f to 210f, 160f to 230f, 20f to 320f, 150f to 320f, 60f to 180f, 130f to 170f)[i]
        Triple(start, (200f + i * 32f) * Math.PI.toFloat() / 180f, 7_000f + i * 900f)
    }

    /** Mlha: x, y, poloměr, rychlost. */
    private val fog = List(16) { i -> floatArrayOf(10f + (i * 53 % 150), 190f + (i * 29 % 140), 16f + (i % 4) * 6f, 0.5f + (i % 3) * 0.3f) }

    override fun drawActors(c: Canvas, t: Float, toX: (Float) -> Float, toY: (Float) -> Float, scale: Float) {
        // tma houstne
        val dark = (t / 6_000f).coerceIn(0f, 1f) * 0.5f
        fill.shader = null; fill.color = Color.BLACK; fill.alpha = (dark * 255).toInt()
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill)

        // světlušky zhasínají jedna po druhé
        flies.forEachIndexed { i, f ->
            val off = 3_000f + i * 110f
            val life = (1f - (t - off) / 500f).coerceIn(0f, 1f)
            if (life <= 0f) return@forEachIndexed
            val x = f[0] + sin(t / 1900f * f[3] + f[2]) * 6f
            val y = f[1] + sin(t / 2600f * f[3] + f[2] * 1.7f) * 4f
            val b = (0.5f + 0.5f * sin(t / 420f * f[3] + f[2] * 3f)) * life
            fill.color = 0xFFF2FF9A.toInt(); fill.alpha = (b * 220).toInt()
            c.drawCircle(toX(x), toY(y), scale * 0.55f, fill)
        }

        // mlha se valí od krajů
        val fogA = ((t - 5_000f) / 4_000f).coerceIn(0f, 1f)
        if (fogA > 0f) for (f in fog) {
            val x = f[0] + sin(t / 3_000f * f[3] + f[1]) * 10f
            val y = f[1] + cos(t / 3_700f * f[3] + f[0]) * 5f
            val r = f[2] * scale
            glow.shader = RadialGradient(toX(x), toY(y), r, intArrayOf(0x55B8BCC8, 0x00B8BCC8), null, Shader.TileMode.CLAMP)
            glow.alpha = (fogA * 255).toInt()
            c.drawCircle(toX(x), toY(y), r, glow)
        }

        // Fenn s plamenem, který se zmenšuje a třese
        val shake = if (t > 12_000f) sin(t / 37f) * 0.4f * ((t - 12_000f) / 6_000f).coerceIn(0f, 1f) else 0f
        fenn.draw(c, toX, toY, 1f, shake)
        val shrink = 1f - 0.65f * ((t - 9_000f) / 9_000f).coerceIn(0f, 1f)
        val flick = 1f + 0.18f * sin(t / 53f) + 0.1f * sin(t / 23f)
        val r = scale * 8f * shrink * flick
        glow.shader = RadialGradient(toX(fenn.flameX + shake), toY(fenn.flameY), r,
            intArrayOf(0x999CC8FF.toInt(), 0x339CC8FF, 0x009CC8FF), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        glow.alpha = 255
        c.drawCircle(toX(fenn.flameX + shake), toY(fenn.flameY), r, glow)

        // Bezejmenní: tmavé postavy bez tváří, přijdou z mlhy a obklíčí chalupu
        for ((start, angle, from) in nameless) {
            val k = ((t - from) / 6_000f).coerceIn(0f, 1f)
            if (k <= 0f) continue
            val e = k * k * (3 - 2 * k)
            val tx = Fenn.X + cos(angle) * 24f; val ty = Fenn.Y + sin(angle) * 9f + 4f
            val x = start.first + (tx - start.first) * e + sin(t / 900f + angle) * 0.8f
            val y = start.second + (ty - start.second) * e
            figure(c, toX(x), toY(y), scale, (k * 1.6f).coerceAtMost(1f), t, angle)
        }
    }

    /** Postava bez tváře: protáhlá silueta, bledý obrys a místo obličeje prázdný šum. */
    private fun figure(c: Canvas, fx: Float, fy: Float, s: Float, a: Float, t: Float, seed: Float) {
        val w = 8f * s; val h = 24f * s
        glow.shader = RadialGradient(fx, fy - h * 0.55f, h * 0.7f, intArrayOf(0x33E6ECF5, 0x00E6ECF5), null, Shader.TileMode.CLAMP)
        glow.alpha = (a * 255).toInt()
        c.drawCircle(fx, fy - h * 0.55f, h * 0.7f, glow)
        fill.shader = null
        fill.color = 0xFF06070B.toInt(); fill.alpha = (a * 240).toInt()
        oval.set(fx - w / 2, fy - h, fx + w / 2, fy)
        c.drawRoundRect(oval, w / 2, w / 2, fill)
        // „obličej“ = místo, kde by měl být – přesýpající se šedý šum
        val faceY = fy - h * 0.8f
        val n = ((t / 80f).toInt() + (seed * 100).toInt())
        for (i in 0 until 9) {
            val gx = i % 3 - 1; val gy = i / 3 - 1
            val v = ((n * 31 + i * 17) % 7) / 7f
            fill.color = Color.rgb((150 + v * 90).toInt(), (152 + v * 90).toInt(), (160 + v * 90).toInt()); fill.alpha = (a * 200).toInt()
            c.drawRect(fx + gx * s * 1.2f - s * 0.6f, faceY + gy * s * 1.2f - s * 0.6f, fx + gx * s * 1.2f + s * 0.6f, faceY + gy * s * 1.2f + s * 0.6f, fill)
        }
    }
}
