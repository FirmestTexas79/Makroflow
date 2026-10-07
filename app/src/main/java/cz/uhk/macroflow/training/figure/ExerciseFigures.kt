package cz.uhk.macroflow.training.figure

import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Ilustrace cviku v detailu (docs/adr/0066): beztvářná postava z boku a stroj, výchozí a koncová poloha.
 * Bez Androidu – tady jsou jen data a kinematika, kreslí [ExerciseFigureView].
 *
 * Souřadnice jsou v „jednotkách“ (postava ~100 vysoká), y roste nahoru. Pózu zadávají cíle:
 * pánev, sklon trupu a kam míří zápěstí a kotník. Loket a koleno dopočítá dvoukloubová IK,
 * takže autor nemusí ladit úhly.
 */
object ExerciseFigures {

    data class P(val x: Float, val y: Float) {
        operator fun plus(o: P) = P(x + o.x, y + o.y)
        operator fun minus(o: P) = P(x - o.x, y - o.y)
        operator fun times(k: Float) = P(x * k, y * k)
        fun len() = hypot(x, y)
    }

    fun dir(deg: Float) = Math.toRadians(deg.toDouble()).let { P(cos(it).toFloat(), sin(it).toFloat()) }

    // Délky článků (jednotky)
    const val TORSO = 30f
    const val NECK = 3f
    const val HEAD_R = 5.5f
    const val UPPER_ARM = 16f
    const val FOREARM = 14f
    const val THIGH = 24f
    const val SHIN = 23f
    const val FOOT = 7f

    /**
     * Póza. [torso] = úhel pánev → ramena (90 = vzpřímeně, 180 = vleže hlavou vlevo).
     * [elbowBend] / [kneeBend] = +1 ohnout proti směru hodin, −1 po směru (vůči spojnici rameno → zápěstí).
     */
    data class Pose(
        val pelvis: P,
        val torso: Float,
        val wrist: P,
        val ankle: P,
        val elbowBend: Int = -1,
        val kneeBend: Int = 1,
        val footAngle: Float = 0f
    )

    /** Spočítané klouby pózy. [front] = jednotkový směr „před tělo“ (kam kouká obličej). */
    data class Joints(
        val pelvis: P, val shoulder: P, val head: P, val elbow: P, val wrist: P,
        val knee: P, val ankle: P, val toe: P, val front: P
    )

    /** Dvoukloubová IK: kde je prostřední kloub, když konec míří na [target] (nedosažitelné → natažené). */
    fun ik(root: P, target: P, a: Float, b: Float, bend: Int): P {
        val d = (target - root)
        val dist = d.len().coerceIn(kotlin.math.abs(a - b) + 0.01f, a + b - 0.01f)
        val base = atan2(d.y, d.x)
        val cosA = ((a * a + dist * dist - b * b) / (2 * a * dist)).coerceIn(-1f, 1f)
        val ang = base + bend * acos(cosA)
        return root + P(cos(ang), sin(ang)) * a
    }

    fun solve(p: Pose): Joints {
        val t = dir(p.torso)
        val shoulder = p.pelvis + t * TORSO
        val head = shoulder + t * (NECK + HEAD_R)
        val elbow = ik(shoulder, p.wrist, UPPER_ARM, FOREARM, p.elbowBend)
        val wrist = elbow + (p.wrist - elbow).let { it * (FOREARM / it.len().coerceAtLeast(0.001f)) }
        val knee = ik(p.pelvis, p.ankle, THIGH, SHIN, p.kneeBend)
        val ankle = knee + (p.ankle - knee).let { it * (SHIN / it.len().coerceAtLeast(0.001f)) }
        return Joints(p.pelvis, shoulder, head, elbow, wrist, knee, ankle, ankle + dir(p.footAngle) * FOOT, P(t.y, -t.x))
    }

    // ── Náčiní ───────────────────────────────────────────────────────────

    enum class Ink { FRAME, STEEL, CUSHION, PLATE }
    /** Kdy se kreslí: za postavou, mezi trupem a bližší paží, nebo úplně vepředu. */
    enum class Layer { BACK, MID, FRONT }

    sealed class Prop(val layer: Layer) {
        /** Zaoblená tyč (rám, sedák, opěrka). */
        class Bar(val a: P, val b: P, val w: Float, val ink: Ink, layer: Layer = Layer.BACK) : Prop(layer)
        class Disc(val c: P, val r: Float, val ink: Ink, layer: Layer = Layer.BACK) : Prop(layer)
        /** Kotouč / úchop v ruce (pohybuje se s pózou). */
        class HandDisc(val r: Float, val ink: Ink, layer: Layer = Layer.FRONT) : Prop(layer)
        /** Lanko nebo páka z pevného bodu do ruky. */
        class ToHand(val from: P, val w: Float, val ink: Ink, layer: Layer = Layer.MID) : Prop(layer)
    }

    data class Illustration(val start: Pose, val end: Pose, val props: List<Prop>)

    private fun p(x: Number, y: Number) = P(x.toFloat(), y.toFloat())

    /** Prototyp: tři cviky (docs/adr/0066). Ostatní cviky obrázek zatím nemají. */
    val BY_ID: Map<String, Illustration> = mapOf(
        // Francouzský tlak vleže s EZ osou: vleže na rovné lavici, hlavou vlevo, osa za hlavou → nad ramena
        "skull_crusher" to Illustration(
            start = Pose(pelvis = p(60, 36), torso = 180f, wrist = p(14, 48), ankle = p(82, 34), elbowBend = -1, kneeBend = 1),
            end = Pose(pelvis = p(60, 36), torso = 180f, wrist = p(29, 65), ankle = p(82, 34), elbowBend = -1, kneeBend = 1),
            props = listOf(
                Prop.Bar(p(8, 27), p(90, 27), 5f, Ink.CUSHION),
                Prop.Bar(p(16, 25), p(16, 3), 3f, Ink.FRAME), Prop.Bar(p(82, 25), p(82, 3), 3f, Ink.FRAME),
                Prop.Bar(p(10, 2), p(22, 2), 3f, Ink.FRAME), Prop.Bar(p(76, 2), p(88, 2), 3f, Ink.FRAME),
                Prop.HandDisc(8.5f, Ink.PLATE, Layer.MID),
                Prop.HandDisc(2f, Ink.STEEL, Layer.FRONT)
            )
        ),
        // Šikmý tlak na stroji: zády na šikmé opěrce, páky se otáčejí v čepu nad hlavou
        "incline_machine_press" to Illustration(
            start = Pose(pelvis = p(40, 30), torso = 115f, wrist = p(38, 57), ankle = p(62, 7), elbowBend = -1, kneeBend = 1),
            end = Pose(pelvis = p(40, 30), torso = 115f, wrist = p(52, 70), ankle = p(62, 7), elbowBend = -1, kneeBend = 1),
            props = listOf(
                Prop.Bar(p(6, 3), p(72, 3), 4f, Ink.FRAME),
                Prop.Bar(p(18, 3), p(24, 96), 4f, Ink.FRAME),
                Prop.Bar(p(38, 3), p(38, 24), 4f, Ink.FRAME),
                Prop.Bar(p(28, 25), p(52, 25), 6f, Ink.CUSHION),
                Prop.Bar(p(35, 28), p(21, 60), 6f, Ink.CUSHION),
                Prop.Disc(p(26, 98), 3f, Ink.STEEL),
                Prop.ToHand(p(26, 98), 3f, Ink.STEEL, Layer.MID),
                Prop.HandDisc(2.4f, Ink.FRAME, Layer.FRONT)
            )
        ),
        // Stahování horní kladky: vsedě, stehna pod opěrkou, tyč od natažených paží k horní části hrudníku
        "lat_pulldown" to Illustration(
            start = Pose(pelvis = p(40, 30), torso = 95f, wrist = p(41, 93), ankle = p(64, 6), elbowBend = 1, kneeBend = 1),
            end = Pose(pelvis = p(40, 30), torso = 100f, wrist = p(40, 57), ankle = p(64, 6), elbowBend = -1, kneeBend = 1),
            props = listOf(
                Prop.Bar(p(28, 3), p(86, 3), 4f, Ink.FRAME),
                Prop.Bar(p(82, 3), p(82, 114), 5f, Ink.FRAME),
                Prop.Bar(p(82, 114), p(38, 114), 4f, Ink.FRAME),
                Prop.Disc(p(40, 111), 3.2f, Ink.STEEL),
                Prop.Bar(p(40, 3), p(40, 24), 4f, Ink.FRAME),
                Prop.Bar(p(30, 25), p(50, 25), 6f, Ink.CUSHION),
                Prop.Bar(p(70, 38), p(82, 38), 3f, Ink.FRAME),
                Prop.Bar(p(58, 38), p(70, 38), 6f, Ink.CUSHION),
                Prop.ToHand(p(40, 108), 1.2f, Ink.STEEL, Layer.BACK),
                Prop.HandDisc(2.2f, Ink.FRAME, Layer.FRONT)
            )
        )
    )
}
