package cz.uhk.macroflow.training.figure

import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Ilustrace cviku v detailu (docs/adr/0066): beztvářná postava a stroj, výchozí a koncová poloha.
 * Bez Androidu – tady je jen kinematika a typy, data jsou v [ExerciseFigureData], kreslí [ExerciseFigureView].
 *
 * Souřadnice jsou v „jednotkách“ (postava ~100 vysoká), y roste nahoru. Pózu zadávají cíle:
 * pánev, sklon trupu a kam míří zápěstí a kotník. Loket a koleno dopočítá dvoukloubová IK.
 * Bokorys ([View.SIDE]) kouká doprava; čelní pohled ([View.FRONT]) zrcadlí levou stranu podle pánve.
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
    /** Půlšířka ramen a boků v čelním pohledu. */
    const val SHOULDER_HALF = 8.5f
    const val HIP_HALF = 5f

    enum class View { SIDE, FRONT }

    /**
     * Póza. [torso] = úhel pánev → ramena (90 = vzpřímeně, 180 = vleže hlavou vlevo obličejem nahoru,
     * 0 = hlavou vpravo obličejem dolů). [elbowBend] / [kneeBend] = +1 ohnout proti směru hodin, −1 po směru.
     * Druhá (vzdálenější / levá) končetina má vlastní cíl, jinak kopíruje bližší (v čelním pohledu zrcadlí).
     * [armScale] zkracuje paži v průmětu (pohyb do hloubky), [thighScale] stehno (vsedě zepředu),
     * [shrug] zvedá ramena.
     */
    data class Pose(
        val pelvis: P,
        val torso: Float,
        val wrist: P,
        val ankle: P,
        val elbowBend: Int = -1,
        val kneeBend: Int = 1,
        val footAngle: Float = 0f,
        val view: View = View.SIDE,
        val wrist2: P? = null,
        val ankle2: P? = null,
        val elbowBend2: Int? = null,
        val kneeBend2: Int? = null,
        val footAngle2: Float? = null,
        val armScale: Float = 1f,
        val armScale2: Float? = null,
        val thighScale: Float = 1f,
        val shrug: Float = 0f
    )

    /** Spočítané klouby. U bokorysu jsou „2“ vzdálenější končetiny, u čelního pohledu levé. */
    data class Joints(
        val view: View,
        val pelvis: P, val shoulder: P, val head: P, val front: P,
        val shoulderR: P, val shoulderL: P, val hipR: P, val hipL: P,
        val elbow: P, val wrist: P, val knee: P, val ankle: P, val toe: P,
        val elbow2: P, val wrist2: P, val knee2: P, val ankle2: P, val toe2: P
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

    /** Prostřední kloub a konec řetězu (konec leží na spojnici s cílem ve správné délce). */
    private fun chain(root: P, target: P, a: Float, b: Float, bend: Int): Pair<P, P> {
        val m = ik(root, target, a, b, bend)
        val v = target - m
        return m to (m + v * (b / v.len().coerceAtLeast(0.001f)))
    }

    private fun mirror(p: P, cx: Float) = P(2 * cx - p.x, p.y)

    fun solve(p: Pose): Joints {
        val t = dir(p.torso)
        val shoulder = p.pelvis + t * (TORSO + p.shrug)
        val head = p.pelvis + t * (TORSO + NECK + HEAD_R)
        val front = P(t.y, -t.x)
        val a1 = p.armScale; val a2 = p.armScale2 ?: a1; val ts = p.thighScale
        return if (p.view == View.SIDE) {
            val (e, w) = chain(shoulder, p.wrist, UPPER_ARM * a1, FOREARM * a1, p.elbowBend)
            val (k, a) = chain(p.pelvis, p.ankle, THIGH * ts, SHIN, p.kneeBend)
            val (e2, w2) = chain(shoulder, p.wrist2 ?: p.wrist, UPPER_ARM * a2, FOREARM * a2, p.elbowBend2 ?: p.elbowBend)
            val (k2, a2j) = chain(p.pelvis, p.ankle2 ?: p.ankle, THIGH * ts, SHIN, p.kneeBend2 ?: p.kneeBend)
            Joints(View.SIDE, p.pelvis, shoulder, head, front, shoulder, shoulder, p.pelvis, p.pelvis,
                e, w, k, a, a + dir(p.footAngle) * FOOT,
                e2, w2, k2, a2j, a2j + dir(p.footAngle2 ?: p.footAngle) * FOOT)
        } else {
            val n = front   // vpravo od diváka
            val sR = shoulder + n * SHOULDER_HALF; val sL = shoulder - n * SHOULDER_HALF
            val hR = p.pelvis + n * HIP_HALF; val hL = p.pelvis - n * HIP_HALF
            val cx = p.pelvis.x
            val (e, w) = chain(sR, p.wrist, UPPER_ARM * a1, FOREARM * a1, p.elbowBend)
            val (e2, w2) = chain(sL, p.wrist2 ?: mirror(p.wrist, cx), UPPER_ARM * a2, FOREARM * a2, p.elbowBend2 ?: -p.elbowBend)
            val (k, a) = chain(hR, p.ankle, THIGH * ts, SHIN, p.kneeBend)
            val (k2, a2j) = chain(hL, p.ankle2 ?: mirror(p.ankle, cx), THIGH * ts, SHIN, p.kneeBend2 ?: -p.kneeBend)
            Joints(View.FRONT, p.pelvis, shoulder, head, front, sR, sL, hR, hL,
                e, w, k, a, a + P(2.5f, -1f), e2, w2, k2, a2j, a2j + P(-2.5f, -1f))
        }
    }

    // ── Náčiní ───────────────────────────────────────────────────────────

    enum class Ink { FRAME, STEEL, CUSHION, PLATE, SKIN }
    /** Kdy se kreslí: za postavou, mezi trupem a bližší paží, nebo úplně vepředu. */
    enum class Layer { BACK, MID, FRONT }
    /** Kloub, ke kterému je náčiní připnuté (pohybuje se s pózou). */
    enum class J { WRIST, WRIST2, ELBOW, SHOULDER, PELVIS, KNEE, KNEE2, ANKLE, ANKLE2, HEAD }

    fun joint(j: Joints, which: J): P = when (which) {
        J.WRIST -> j.wrist; J.WRIST2 -> j.wrist2; J.ELBOW -> j.elbow; J.SHOULDER -> j.shoulder
        J.PELVIS -> j.pelvis; J.KNEE -> j.knee; J.KNEE2 -> j.knee2; J.ANKLE -> j.ankle; J.ANKLE2 -> j.ankle2; J.HEAD -> j.head
    }

    sealed class Prop(val layer: Layer) {
        /** Zaoblená tyč (rám, sedák, opěrka). */
        class Bar(val a: P, val b: P, val w: Float, val ink: Ink, layer: Layer = Layer.BACK) : Prop(layer)
        class Disc(val c: P, val r: Float, val ink: Ink, layer: Layer = Layer.BACK) : Prop(layer)
        /** Kotouč / úchop / jednoručka na kloubu. */
        class At(val joint: J, val r: Float, val ink: Ink, layer: Layer = Layer.FRONT) : Prop(layer)
        /** Lanko nebo páka z pevného bodu ke kloubu. */
        class ToJoint(val from: P, val joint: J, val w: Float, val ink: Ink, layer: Layer = Layer.MID) : Prop(layer)
        /** Opěrka posunutá vůči kloubu (např. pod koleno, na ramena). */
        class Pad(val joint: J, val d1: P, val d2: P, val w: Float, val ink: Ink, layer: Layer = Layer.BACK) : Prop(layer)
        /** Jen v jednom snímku (0 = začátek, 1 = konec). */
        class Only(val frame: Int, val prop: Prop) : Prop(prop.layer)
    }

    /** [end] = null → jediný snímek (výdrž, např. plank). */
    data class Illustration(val start: Pose, val end: Pose?, val props: List<Prop>)

    val BY_ID: Map<String, Illustration> get() = ExerciseFigureData.BY_ID
}
