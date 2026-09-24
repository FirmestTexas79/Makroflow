package cz.uhk.macroflow.training.analysis

import kotlin.math.hypot
import kotlin.math.max

/**
 * Výběr a sledování kotouče mezi detekcemi (čistý Kotlin).
 *
 * Proč původní verze „blbla“:
 *  - detektor vracel jen jeden nejvýraznější objekt (často člověka), kotouč pak chyběl,
 *  - filtr poměru stran 0,90–1,15 zahazoval kotouč natočený byť o pár stupňů (elipsa),
 *  - bral se vždy NEJVĚTŠÍ kulatý objekt ve snímku → skoky na jiné předměty,
 *  - neexistoval „zámek“ na konkrétní objekt ani omezení, jak daleko může kotouč mezi snímky uskočit.
 *
 * Teď: zámek na trackingId (nebo ťuknutím), hradlování vzdáleností od predikované polohy,
 * α-β filtr polohy a krátké „doběhnutí“ po predikci, když detektor na chvíli vypadne.
 */
class PlateTracker(
    private val alpha: Double = 0.6,
    private val beta: Double = 0.15,
    private val minSizePx: Float = 30f,
    private val maxCoastMs: Long = 400
) {
    data class Detection(val left: Float, val top: Float, val right: Float, val bottom: Float, val trackingId: Int?) {
        val cx get() = (left + right) / 2f
        val cy get() = (top + bottom) / 2f
        val w get() = right - left
        val h get() = bottom - top
        val radius get() = (w + h) / 4f
        fun contains(x: Float, y: Float) = x in left..right && y in top..bottom
    }

    data class Track(val x: Float, val y: Float, val radius: Float, val measured: Boolean)

    var lockedId: Int? = null
        private set
    private var x = 0.0; private var y = 0.0; private var vx = 0.0; private var vy = 0.0
    private var r = 0.0
    private var lastT = -1L
    private var lastMeasuredT = -1L
    val hasTarget: Boolean get() = lastT >= 0

    fun reset() {
        lockedId = null; lastT = -1; lastMeasuredT = -1; vx = 0.0; vy = 0.0
    }

    /** Kotouč z boku je kruh, při mírném natočení elipsa – tolerance 0,7–1,4. */
    private fun isPlateLike(d: Detection) = d.w >= minSizePx && d.h >= minSizePx && (d.w / d.h) in 0.7f..1.4f

    /** Ruční zámek ťuknutím: detekce pod prstem, jinak nejbližší kulatá do 2 poloměrů. */
    fun lockAt(px: Float, py: Float, detections: List<Detection>, tMs: Long): Boolean {
        val hit = detections.firstOrNull { it.contains(px, py) && isPlateLike(it) }
            ?: detections.filter(::isPlateLike)
                .minByOrNull { hypot(it.cx - px, it.cy - py) }
                ?.takeIf { hypot(it.cx - px, it.cy - py) <= 2 * it.radius }
            ?: return false
        start(hit, tMs)
        return true
    }

    /** Zpracuje detekce jednoho snímku. null = kotouč ztracen (a predikce už nestačí). */
    fun update(detections: List<Detection>, tMs: Long): Track? {
        val candidates = detections.filter(::isPlateLike)

        if (!hasTarget) {
            // Automatické zaměření: největší kulatý objekt (uživatel může přezamknout ťuknutím)
            val best = candidates.maxByOrNull { it.w * it.h } ?: return null
            start(best, tMs)
            return Track(x.toFloat(), y.toFloat(), r.toFloat(), measured = true)
        }

        val dt = ((tMs - lastT).coerceAtLeast(1)) / 1000.0
        val lostFor = tMs - lastMeasuredT
        // Po delší ztrátě už rychlosti nevěříme – hledá se kolem poslední známé polohy
        if (lostFor > maxCoastMs) { vx = 0.0; vy = 0.0 }
        val px = x + vx * dt
        val py = y + vy * dt

        val byId = lockedId?.let { id -> candidates.firstOrNull { it.trackingId == id } }
        val gate = r * (2.5 + lostFor / 250.0)   // čím déle ztracen, tím širší okno hledání
        val match = byId ?: candidates
            .filter { max(it.radius / r, r / it.radius) <= 1.6 }
            .minByOrNull { hypot(it.cx - px, it.cy - py) }
            ?.takeIf { hypot(it.cx - px, it.cy - py) <= gate }

        if (match == null) {
            if (lostFor > maxCoastMs) return null
            // krátké doběhnutí po predikci
            x = px; y = py; lastT = tMs
            return Track(x.toFloat(), y.toFloat(), r.toFloat(), measured = false)
        }

        // α-β filtr
        val rx = match.cx - px
        val ry = match.cy - py
        x = px + alpha * rx; y = py + alpha * ry
        vx += beta * rx / dt; vy += beta * ry / dt
        r += (match.radius - r) * 0.2
        if (match.trackingId != null) lockedId = match.trackingId
        lastT = tMs; lastMeasuredT = tMs
        return Track(x.toFloat(), y.toFloat(), r.toFloat(), measured = true)
    }

    private fun start(d: Detection, tMs: Long) {
        x = d.cx.toDouble(); y = d.cy.toDouble(); vx = 0.0; vy = 0.0
        r = d.radius.toDouble()
        lockedId = d.trackingId
        lastT = tMs; lastMeasuredT = tMs
    }
}
