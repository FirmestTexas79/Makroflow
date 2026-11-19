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
import kotlin.math.abs
import kotlin.math.sin

/**
 * Cutscéna „Dveře Věže“ (docs/adr/0103) při prvním příchodu s Pozvánkou Řádu Váhy. Kamera dojede k Věži, sjede po
 * jejích patrech k portálu; pečeť vah na dveřích se rozsvítí, po křídlech se rozběhnou zlaté linky, sedm závor povolí
 * (otřesy) a pak osmá, hlubší. Obří bronzová křídla se pomalu otevřou dovnitř do studeného bílého světla.
 * Střih do vestibulu: od vchodu k pultu archiváře Septa.
 */
class TowerDoorCutsceneView(context: Context) : CutsceneView(context) {

    private fun bmp(res: Int) = BitmapFactory.decodeResource(resources, res, BitmapFactory.Options().apply { inScaled = false })
    private val city = bmp(R.drawable.ozvena)
    private val gate = bmp(R.drawable.tower_gate)
    private val lobby = bmp(R.drawable.tower_lobby)
    private val leaf = bmp(R.drawable.tower_door_leaf)
    override val world: Bitmap = city
    override val totalMs = 26_600f

    override fun worldAt(t: Float): Bitmap = when {
        t < CUT1 -> city
        t < CUT2 -> gate
        else -> lobby
    }

    override val shots = listOf(
        Shot(0f, 192f, 470f, 130f), Shot(CUT1 - 1f, 192f, 556f, 96f),
        // průčelí: shora po patrech dolů k portálu, pak blízko k pečeti a do světla
        Shot(CUT1, PX, 70f, 170f), Shot(9_200f, PX, 150f, 150f), Shot(12_000f, PX, PY + 70f, 104f),
        Shot(19_000f, PX, PY + 72f, 96f), Shot(CUT2 - 1f, PX, PY + 70f, 54f),
        // vestibul: od vchodu k pultu
        Shot(CUT2, 88f, 300f, 120f), Shot(26_600f, 110f, 196f, 132f)
    )

    override val captions = listOf(
        Caption(600f, 5_600f, "Pozvánka v ruce byla teplá. Jako by ji někdo právě dopsal."),
        Caption(6_400f, 9_800f, "Věž Řádu Váhy. Sedm pater nahoru. Kolik dolů, to se neříká."),
        Caption(10_000f, 13_600f, "Pečeť vah se rozsvítila. Uvnitř se něco pohnulo – těžké a pomalé."),
        Caption(13_800f, 17_400f, "Sedm závor. Pak osmá, o které nikdo nemluví."),
        Caption(17_600f, 21_200f, "Uvnitř nebyla tma. Bylo tam světlo, jaké mají jen kanceláře."),
        Caption(21_800f, 26_000f, "„Subjekt Okruhu 213. Očekávali jsme vás,“ ozvalo se od pultu. „Čekání je součástí měření.“")
    )

    private val pixel = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val dst = RectF()

    private fun light(c: Canvas, x: Float, y: Float, r: Float, color: Int, a: Float) {
        if (a <= 0f || r <= 0f) return
        glow.shader = RadialGradient(x, y, r, intArrayOf(color, color and 0x00FFFFFF), null, Shader.TileMode.CLAMP)
        glow.alpha = (a * 255).toInt().coerceIn(0, 255)
        c.drawCircle(x, y, r, glow)
    }

    /** Sedm závor (13,8–16,6 s) a osmá hlubší (17,2 s). */
    private val thuds = (0 until 7).map { 13_900f + it * 420f } + 17_200f

    override fun shake(t: Float): Pair<Float, Float> {
        if (t !in CUT1..CUT2) return 0f to 0f
        val hit = thuds.lastOrNull { t >= it } ?: return 0f to 0f
        val age = (t - hit) / 260f
        if (age > 1f) return 0f to 0f
        val amp = (if (hit == thuds.last()) 16f else 6f) * (1f - age)
        return sin(t / 17f) * amp to sin(t / 23f) * amp * 0.6f
    }

    private var doorSfx = false

    override fun drawActors(c: Canvas, t: Float, toX: (Float) -> Float, toY: (Float) -> Float, scale: Float) {
        if (!doorSfx && t >= 13_600f) {                                     // závory a skřípění křídel (ElevenLabs)
            doorSfx = true
            cz.uhk.macroflow.pokemon.audio.GameAudio.sfx(context, cz.uhk.macroflow.pokemon.audio.GameAudio.Sfx.TOWER_DOOR)
        }
        when {
            t < CUT1 -> cityAct(c, t, toX, toY, scale)
            t < CUT2 -> gateAct(c, t, toX, toY, scale)
            else -> lobbyAct(c, t, toX, toY, scale)
        }
        for (cut in listOf(CUT1, CUT2)) {
            val dip = 1f - abs(t - cut) / 700f
            if (dip > 0f) {
                fill.shader = null; fill.color = if (cut == CUT2) Color.WHITE else Color.BLACK
                fill.alpha = (dip * 255).toInt(); c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill)
            }
        }
    }

    private fun ash(c: Canvas, t: Float, toX: (Float) -> Float, toY: (Float) -> Float, s: Float, w: Float, h: Float, x0: Float, y0: Float) {
        fill.shader = null
        for (i in 0 until 70) {
            val x = x0 + (i * 37 % w.toInt()) + sin(t / 900f + i) * 5f
            val y = y0 + ((i * 71 % h.toInt()) + t / 45f * (0.5f + i % 4 * 0.2f)) % h
            fill.color = 0xFFD2CDC5.toInt(); fill.alpha = 160
            c.drawCircle(toX(x), toY(y), s * 0.45f, fill)
        }
    }

    private fun cityAct(c: Canvas, t: Float, toX: (Float) -> Float, toY: (Float) -> Float, s: Float) {
        ash(c, t, toX, toY, s, 200f, 260f, 92f, 400f)
        light(c, toX(192f), toY(560f), s * 18f, 0x66FFE9A0, 0.6f + 0.3f * sin(t / 300f))     // pozvánka u dveří
    }

    private fun gateAct(c: Canvas, t: Float, toX: (Float) -> Float, toY: (Float) -> Float, s: Float) {
        val open = ((t - 17_500f) / 3_600f).coerceIn(0f, 1f).let { it * it * (3 - 2 * it) }
        val left = PX - PW / 2f; val right = PX + PW / 2f
        // světlo chodby proniká škvírou a pak celým portálem
        if (open > 0f) light(c, toX(PX), toY(PY + PH * 0.6f), s * (14f + 80f * open), 0xFFF4F8FF.toInt(), 0.35f + 0.55f * open)
        // křídla: otáčí se dovnitř kolem vnějšího okraje → zužují se a tmavnou
        val k = 1f - open * 0.86f
        val lw = PW / 2f * k
        dst.set(toX(left), toY(PY), toX(left + lw), toY(PY + PH))
        c.drawBitmap(leaf, null, dst, pixel)
        shadeLeaf(c, dst, open)
        c.save(); c.scale(-1f, 1f, toX(right - lw / 2f), 0f)
        dst.set(toX(right - lw), toY(PY), toX(right), toY(PY + PH))
        c.drawBitmap(leaf, null, dst, pixel)
        shadeLeaf(c, dst, open)
        c.restore()

        // pečeť vah na spáře se rozsvítí, zlaté linky se rozběhnou po panelech
        val seal = ((t - 9_800f) / 1_400f).coerceIn(0f, 1f) * (1f - open)
        light(c, toX(PX), toY(PY + SEAL_Y), s * (16f + 4f * sin(t / 140f)), 0xFFFFE08A.toInt(), seal)
        val trace = ((t - 10_600f) / 2_800f).coerceIn(0f, 1f) * (1f - open)
        if (trace > 0f) {
            line.strokeWidth = s * 0.8f; line.color = 0xFFFFE9A0.toInt(); line.alpha = (trace * 230).toInt()
            for ((x0, y0, x1, y1) in TRACES) {
                val ex = x0 + (x1 - x0) * trace; val ey = y0 + (y1 - y0) * trace
                c.drawLine(toX(PX + x0), toY(PY + y0), toX(PX + ex), toY(PY + ey), line)
                c.drawLine(toX(PX - x0), toY(PY + y0), toX(PX - ex), toY(PY + ey), line)
            }
        }
        // prach z rámu při každé závoře
        fill.shader = null
        for (hit in thuds) {
            val age = (t - hit) / 1_300f
            if (age !in 0f..1f) continue
            for (i in 0 until 14) {
                val x = left + (i * 53 % PW.toInt()) + sin(i * 1.7f) * 3f
                val y = PY + 2f + age * (18f + i % 5 * 6f)
                fill.color = 0xFFE6E0D4.toInt(); fill.alpha = ((1f - age) * 210).toInt()
                c.drawCircle(toX(x), toY(y), s * (0.5f + (i % 3) * 0.2f), fill)
            }
        }
        ash(c, t, toX, toY, s, 192f, 288f, 0f, 0f)
    }

    private fun shadeLeaf(c: Canvas, r: RectF, open: Float) {
        if (open <= 0f) return
        fill.shader = null; fill.color = 0xFF0A0C14.toInt(); fill.alpha = (open * 170).toInt()
        c.drawRect(r, fill)
    }

    private fun lobbyAct(c: Canvas, t: Float, toX: (Float) -> Float, toY: (Float) -> Float, s: Float) {
        light(c, toX(155f), toY(171f), s * 10f, 0x88B4F0C8.toInt(), 0.8f)     // bankéřská lampa
        light(c, toX(88f), toY(320f), s * 40f, 0x66FFF4E0, 1f - ((t - CUT2) / 2_500f).coerceIn(0f, 1f) * 0.6f)
    }

    companion object {
        private const val CUT1 = 6_000f
        private const val CUT2 = 21_500f
        /** Portál v tower_gate.png (art px): střed x, horní okraj, šířka a výška vnitřku (gen_tower.py). */
        private const val PX = 96f
        private const val PY = 112f
        private const val PW = 60f
        private const val PH = 142f
        private const val SEAL_Y = 85f
        /** Zlaté linky od pečeti po panelech (relativně k [PX], [PY]; zrcadlí se na druhé křídlo). */
        private val TRACES = listOf(
            floatArrayOf(-2f, 72f, -26f, 72f), floatArrayOf(-26f, 72f, -26f, 30f), floatArrayOf(-26f, 30f, -4f, 30f),
            floatArrayOf(-2f, 98f, -26f, 98f), floatArrayOf(-26f, 98f, -26f, 136f)
        )
    }
}

private operator fun FloatArray.component1() = this[0]
private operator fun FloatArray.component2() = this[1]
private operator fun FloatArray.component3() = this[2]
private operator fun FloatArray.component4() = this[3]
