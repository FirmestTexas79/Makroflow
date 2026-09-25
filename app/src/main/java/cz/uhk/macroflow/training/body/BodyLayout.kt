package cz.uhk.macroflow.training.body

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Pohled na postavu: zepředu nebo zezadu. */
enum class BodySide(val label: String) { FRONT("zepředu"), BACK("zezadu") }

/**
 * Rozložení postav v ploše view (čistý Kotlin, pokryto testy). Stejný výpočet používá kreslení
 * i převod dotyku na souřadnice těla (viewBox 100 × 200), takže se nikdy „nerozjedou“.
 */
data class BodyLayout(
    val sides: List<BodySide>,
    val scale: Float,
    val offX: Float,
    val offY: Float,
    val gap: Float
) {
    /** Levý okraj postavy na pozici [index] v pixelech view. */
    fun originX(index: Int): Float = offX + index * (BodyShapes.WIDTH + gap) * scale

    /** Dotyk v pixelech → (strana, x, y) v souřadnicích těla; mimo postavy null. */
    fun toBody(x: Float, y: Float): Triple<BodySide, Float, Float>? {
        if (scale <= 0f) return null
        val by = (y - offY) / scale
        if (by < 0f || by > BodyShapes.HEIGHT) return null
        sides.forEachIndexed { i, side ->
            val bx = (x - originX(i)) / scale
            if (bx >= 0f && bx <= BodyShapes.WIDTH) return Triple(side, bx, by)
        }
        return null
    }

    companion object {
        /** Poměr mezery mezi postavami k šířce postavy. */
        const val GAP_RATIO = 0.12f

        fun compute(
            sides: List<BodySide>, width: Int, height: Int,
            padLeft: Int, padTop: Int, padRight: Int, padBottom: Int, captionH: Float
        ): BodyLayout? {
            if (sides.isEmpty()) return null
            val gap = BodyShapes.WIDTH * GAP_RATIO
            val totalW = BodyShapes.WIDTH * sides.size + gap * (sides.size - 1)
            val availW = (width - padLeft - padRight).toFloat()
            val availH = (height - padTop - padBottom) - captionH
            if (availW <= 0f || availH <= 0f) return null
            val scale = minOf(availW / totalW, availH / BodyShapes.HEIGHT)
            val offX = padLeft + (availW - totalW * scale) / 2
            val offY = padTop + (availH - BodyShapes.HEIGHT * scale) / 2
            return BodyLayout(sides, scale, offX, offY, gap)
        }
    }
}

/**
 * Výběr partie klepnutím (čistý Kotlin, pokryto testy). Test „leží bod ve svalu“ dodává view
 * (Region z cest), tady je jen rozhodovací logika:
 *  1. přesný zásah – vyhrává partie kreslená navrch (poslední v [order]),
 *  2. jinak tolerance pro prst: vzorky na kružnicích do poloměru [radius], vyhrává partie
 *     zasažená nejblíž středu (při shodě ta s více zásahy).
 */
object MuscleHit {
    private const val DIRECTIONS = 12
    private const val RINGS = 3

    fun pick(
        x: Float, y: Float, radius: Float, order: List<Muscle>,
        contains: (Muscle, Float, Float) -> Boolean
    ): Muscle? {
        order.asReversed().firstOrNull { contains(it, x, y) }?.let { return it }
        if (radius <= 0f) return null
        for (ring in 1..RINGS) {
            val r = radius * ring / RINGS
            val hits = HashMap<Muscle, Int>()
            for (d in 0 until DIRECTIONS) {
                val a = 2 * PI * d / DIRECTIONS
                val px = x + (r * cos(a)).toFloat()
                val py = y + (r * sin(a)).toFloat()
                order.asReversed().firstOrNull { contains(it, px, py) }?.let { hits[it] = (hits[it] ?: 0) + 1 }
            }
            if (hits.isNotEmpty()) return hits.maxWith(compareBy({ it.value }, { order.indexOf(it.key) })).key
        }
        return null
    }
}
