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
import cz.uhk.macroflow.pokemon.audio.GameAudio
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

/**
 * Cutscéna „Sjezd do Hlubiny“ (docs/adr/0104) při první jízdě výtahem na −2. Zlatá tečka na plánku Věže sjede
 * pod přízemí a dál, než plánek sahá. Pak šachta zepředu: kabina s lampou jede dolů kolem zavřených dveří přízemí,
 * kolem −1 (za mřížkou Hunfreyho lampa), betonem a čedičem, kterým prorůstají kořeny; rudé nitě Řádu jedou s ní.
 * Šachta skončí ve stropě jeskyně, kabina se s trhnutím zastaví – a kamera pomalu sjede po kořenech ke kmeni
 * a obrácené koruně Spící. Modrá světla na konečcích větví jednou bliknou jako tep; v kmeni se zablýskne kopí.
 * Střih na Sektor Hlubiny: dveře výtahu nad lávkou. Pozadí šachty: tools/mapgen/gen_descent.py.
 */
class DescentCutsceneView(context: Context) : CutsceneView(context) {

    private fun bmp(res: Int) = BitmapFactory.decodeResource(resources, res, BitmapFactory.Options().apply { inScaled = false })
    private val lift = bmp(R.drawable.tower_lift_map)
    private val shaft = bmp(R.drawable.tower_descent)
    private val abyss = bmp(R.drawable.tower_abyss)
    override val world: Bitmap = lift
    override val totalMs = 41_500f

    override fun worldAt(t: Float): Bitmap = when {
        t < CUT1 -> lift
        t < CUT2 -> shaft
        else -> abyss
    }

    override val shots = listOf(
        Shot(0f, 20f, 196f, 40f), Shot(CUT1 - 1f, 20f, 252f, 40f),
        // šachta: kamera jede s kabinou (cameraAt), po zastavení pomalu dolů po kořenech ke koruně
        Shot(STOP, 88f, cabinTop(STOP) + 40f, 112f), Shot(STOP + 1_800f, 88f, 500f, 124f),
        Shot(29_000f, 88f, 640f, 176f), Shot(31_600f, 88f, 650f, 176f), Shot(CUT2 - 1f, 82f, 600f, 96f),
        // Sektor Hlubiny: od dveří výtahu po lávce k balkonu
        Shot(CUT2, 88f, 96f, 120f), Shot(41_500f, 88f, 170f, 150f)
    )

    override val captions = listOf(
        Caption(600f, 4_600f, "„Subjekt 213: vyvážen. Sektor Hlubiny: schváleno.“"),
        Caption(5_400f, 9_600f, "Výtah jede dolů déle, než by měl."),
        Caption(10_400f, 14_600f, "−1. Za mřížkou ještě svítí Hunfreyho lampa. Pak už žádná tlačítka."),
        Caption(15_000f, 19_200f, "Cihly skončily. Beton skončil. Zbyl čedič – a kořeny, které prorůstají zdí."),
        Caption(19_600f, 23_200f, "Rudé nitě Řádu jedou s tebou. Celou cestu dolů."),
        Caption(24_000f, 27_800f, "Pak stěny zmizely. Pod kabinou nebylo nic. Jen tma – a v ní Ona."),
        Caption(28_000f, 30_400f, "Spící. Strom hlavou dolů, bledý jako kost. Kořeny drží všechno, co znáš."),
        Caption(30_600f, 32_800f, "Modrá světla na větvích jednou bliknou. Jako tep."),
        Caption(33_000f, 35_200f, "Hluboko v kmeni se zablýskne kov."),
        Caption(36_000f, 41_000f, "„Sektor Hlubiny,“ řekne reproduktor. „Sledovat. Neprobouzet. Dobrá práce, Subjekte.“")
    )

    /** Horní hrana kabiny (art px šachty): rozjezd, plná rychlost, dobrzdění pod ústím šachty. */
    private fun cabinTop(t: Float): Float {
        val k = ((t - CUT1) / (STOP - CUT1)).coerceIn(0f, 1f)
        return -60f + (STOP_Y + 60f) * (k - sin(2 * PI * k).toFloat() / (2 * PI).toFloat())
    }

    override fun cameraAt(t: Float): Shot =
        if (t in CUT1..STOP) Shot(t, 88f, cabinTop(t) + 40f, 112f) else super.cameraAt(t)

    private val pixel = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val rect = RectF()

    private fun light(c: Canvas, x: Float, y: Float, r: Float, color: Int, a: Float) {
        if (a <= 0f || r <= 0f) return
        glow.shader = RadialGradient(x, y, r, intArrayOf(color, color and 0x00FFFFFF), null, Shader.TileMode.CLAMP)
        glow.alpha = (a * 255).toInt().coerceIn(0, 255)
        c.drawCircle(x, y, r, glow)
    }

    /** Tep Spící: 0..1 kolem [BEAT]. */
    private fun beat(t: Float) = exp(-((t - BEAT) / 380f).let { it * it })

    override fun shake(t: Float): Pair<Float, Float> {
        val stop = (t - STOP) / 450f                                       // kabina se s trhnutím zastaví
        val a1 = if (stop in 0f..1f) 9f * (1f - stop) else 0f
        val a2 = 14f * beat(t)
        val amp = a1 + a2
        return if (amp < 0.3f) 0f to 0f else sin(t / 19f) * amp to sin(t / 27f) * amp * 0.7f
    }

    private val played = BooleanArray(4)
    private fun once(i: Int, at: Float, t: Float, sfx: GameAudio.Sfx) {
        if (!played[i] && t >= at) { played[i] = true; GameAudio.sfx(context, sfx) }
    }

    override fun drawActors(c: Canvas, t: Float, toX: (Float) -> Float, toY: (Float) -> Float, scale: Float) {
        once(0, CUT1, t, GameAudio.Sfx.ELEVATOR)
        once(1, 16_500f, t, GameAudio.Sfx.DRIP)
        once(2, STOP, t, GameAudio.Sfx.PNEUMATIC)
        once(3, CUT2 + 300f, t, GameAudio.Sfx.ELEVATOR)
        when {
            t < CUT1 -> liftAct(c, t, toX, toY, scale)
            t < CUT2 -> shaftAct(c, t, toX, toY, scale)
            else -> abyssAct(c, t, toX, toY, scale)
        }
        for (cut in listOf(CUT1, CUT2)) {
            val dip = 1f - abs(t - cut) / 600f
            if (dip > 0f) {
                fill.shader = null; fill.color = Color.BLACK
                fill.alpha = (dip * 255).toInt(); c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill)
            }
        }
    }

    /** Plánek Věže: zlatá tečka sjede z přízemí přes −1 a −2 a ještě dál. */
    private fun liftAct(c: Canvas, t: Float, toX: (Float) -> Float, toY: (Float) -> Float, s: Float) {
        val k = (t / (CUT1 - 400f)).coerceIn(0f, 1f)
        val y = 223f + (290f - 223f) * k * k
        light(c, toX(20f), toY(y), s * 9f, 0xFFFFE08A.toInt(), 0.85f)
        fill.shader = null; fill.color = 0xFFFFF4C8.toInt(); fill.alpha = 255
        c.drawCircle(toX(20f), toY(y), s * 1.4f, fill)
    }

    private fun shaftAct(c: Canvas, t: Float, toX: (Float) -> Float, toY: (Float) -> Float, s: Float) {
        // Hunfreyho lampa za mřížkou −1 (bliká)
        light(c, toX(137f), toY(163f), s * 16f, 0xFFFFC070.toInt(), 0.35f + 0.15f * sin(t / 90f) * sin(t / 37f))

        // Spící: světla na konečcích větví dýchají, při tepu se rozzáří
        val b = beat(t)
        for (i in TIPS.indices step 2) {
            val x = TIPS[i]; val y = TIPS[i + 1]
            val tw = 0.25f + 0.15f * sin(t / 600f + i)
            light(c, toX(x), toY(y), s * (6f + 10f * b), 0xFF8AC8FF.toInt(), tw + 0.7f * b)
        }
        light(c, toX(88f), toY(700f), s * (40f + 50f * b), 0xFF4A7AD0.toInt(), 0.12f + 0.45f * b)
        // tep jede po kmeni nahoru ke kořenům
        if (b > 0.05f) {
            val up = ((t - BEAT + 400f) / 1_400f).coerceIn(0f, 1f)
            light(c, toX(88f), toY(620f - 160f * up), s * 22f, 0xFF9AD0FF.toInt(), 0.5f * (1f - up))
        }
        // kopí: krátký záblesk s křížkem
        val g = ((t - 33_200f) / 900f).let { if (it in 0f..1f) sin(it * PI.toFloat()) else 0f }
        if (g > 0f) {
            light(c, toX(SPEAR_X), toY(SPEAR_Y), s * 8f, 0xFFFFFFFF.toInt(), g)
            line.strokeWidth = s * 0.5f; line.color = Color.WHITE; line.alpha = (g * 255).toInt()
            val r = s * 6f * g
            c.drawLine(toX(SPEAR_X) - r, toY(SPEAR_Y), toX(SPEAR_X) + r, toY(SPEAR_Y), line)
            c.drawLine(toX(SPEAR_X), toY(SPEAR_Y) - r, toX(SPEAR_X), toY(SPEAR_Y) + r, line)
        }

        // kabina na laně
        val top = cabinTop(t)
        val swing = if (t > STOP) sin((t - STOP) / 260f) * 1.6f * exp(-(t - STOP) / 1_800f) else 0f
        val cx = 88f + swing
        line.strokeWidth = s * 0.6f; line.color = 0xFF98A0B2.toInt(); line.alpha = 255
        c.drawLine(toX(88f), toY(top - 400f), toX(cx), toY(top - 4f), line)
        light(c, toX(cx), toY(top + 26f), s * 70f, 0xFFFFD89A.toInt(), 0.42f)          // lampa osvětlí šachtu
        light(c, toX(cx), toY(top + 60f), s * 26f, 0xFFFFE8B0.toInt(), 0.25f)
        fill.shader = null
        fill.color = 0xFF3E4452.toInt(); rect.set(toX(cx - 20f), toY(top - 4f), toX(cx + 20f), toY(top)); c.drawRect(rect, fill)
        fill.color = 0xFF2A1E10.toInt(); rect.set(toX(cx - 22f), toY(top), toX(cx + 22f), toY(top + CAB_H)); c.drawRect(rect, fill)
        light(c, toX(cx), toY(top + 10f), s * 22f, 0xFFFFE0A0.toInt(), 0.75f)            // světlo uvnitř
        // zlatá mříž dveří kabiny (kosočtverce) a rám
        line.strokeWidth = s * 0.7f; line.color = 0xFFD0A440.toInt(); line.alpha = 230
        rect.set(toX(cx - 22f), toY(top), toX(cx + 22f), toY(top + CAB_H))
        c.save(); c.clipRect(rect)
        var d = -CAB_H
        while (d < 44f) {
            c.drawLine(toX(cx - 22f + d), toY(top), toX(cx - 22f + d + CAB_H), toY(top + CAB_H), line)
            c.drawLine(toX(cx + 22f - d), toY(top), toX(cx + 22f - d - CAB_H), toY(top + CAB_H), line)
            d += 8f
        }
        c.restore()
        line.strokeWidth = s * 1.6f; line.color = 0xFFF0D070.toInt(); line.alpha = 255
        rect.set(toX(cx - 22f), toY(top), toX(cx + 22f), toY(top + CAB_H)); c.drawRect(rect, line)
        c.drawLine(toX(cx), toY(top), toX(cx), toY(top + CAB_H), line)
    }

    /** Sektor Hlubiny: dveře výtahu se otevřou do studeného světla nad lávkou. */
    private fun abyssAct(c: Canvas, t: Float, toX: (Float) -> Float, toY: (Float) -> Float, s: Float) {
        val open = ((t - CUT2 - 400f) / 1_600f).coerceIn(0f, 1f)
        light(c, toX(88f), toY(70f), s * (10f + 26f * open), 0xFFFFE6B0.toInt(), 0.7f * open)
        light(c, toX(88f), toY(240f), s * 30f, 0xFF8AB8FF.toInt(), 0.25f + 0.1f * sin(t / 500f))
    }

    companion object {
        private const val CUT1 = 5_000f
        private const val STOP = 23_500f
        private const val BEAT = 31_000f
        private const val CUT2 = 35_500f
        /** Kabina: výška a horní hrana po zastavení (art px tower_descent.png; ústí šachty je na y = 400). */
        private const val CAB_H = 52f
        private const val STOP_Y = 420f
        /** Světla na konečcích větví a kopí v kmeni – vypisuje tools/mapgen/gen_descent.py. */
        private val TIPS = floatArrayOf(159f, 645f, 20f, 654f, 73f, 691f, 159f, 703f, 44f, 727f, 85f, 735f, 71f, 749f, 41f, 753f, 153f, 759f)
        private const val SPEAR_X = 80f
        private const val SPEAR_Y = 598f
    }
}
