package cz.uhk.macroflow.training.analysis

/**
 * Automatické rozpoznání série pro „aktivní režim“ (telefon opřený na zemi, bez parťáka).
 *
 *  - Začátek: kotouč se svisle pohne o víc než [startMoveCm] během 1,5 s.
 *    Do série se přibalí i poslední 3 s před pohybem, aby se neuřízl začátek 1. repu.
 *  - Konec: [restMs] bez svislého pohybu větším než 3 cm, nebo kotouč [lostMs] nevidět
 *    (odložení činky do stojanu, odchod ze záběru).
 */
class SetDetector(
    private val plateDiameterCm: Double = RepAnalyzer.DEFAULT_PLATE_DIAMETER_CM,
    private val startMoveCm: Double = 8.0,
    private val restMs: Long = 4000,
    private val lostMs: Long = 6000
) {
    enum class State { IDLE, ACTIVE }

    var state: State = State.IDLE
        private set
    private val buffer = ArrayDeque<Sample>()
    private var activeSince = 0L
    private var lastSeen = 0L

    /** Vzorky aktuální série (pro živé počítání repů). */
    fun currentSet(): List<Sample> = if (state == State.ACTIVE) buffer.toList() else emptyList()

    /** @return dokončená série, pokud tímto snímkem skončila; jinak null. */
    fun onSample(s: Sample): List<Sample>? {
        lastSeen = s.tMs
        buffer.addLast(s)
        when (state) {
            State.IDLE -> {
                while (buffer.isNotEmpty() && buffer.first().tMs < s.tMs - 3000) buffer.removeFirst()
                if (verticalRangeCm(s.tMs - 1500) >= startMoveCm) {
                    state = State.ACTIVE
                    activeSince = s.tMs
                }
            }
            State.ACTIVE -> {
                if (s.tMs - activeSince > restMs && verticalRangeCm(s.tMs - restMs) < 3.0) return finish()
            }
        }
        return null
    }

    /** Snímek bez kotouče. */
    fun onLost(tMs: Long): List<Sample>? =
        if (state == State.ACTIVE && tMs - lastSeen > lostMs) finish() else null

    /** Ruční ukončení (tlačítko) nebo vypnutí režimu. */
    fun finish(): List<Sample>? {
        val out = if (state == State.ACTIVE) buffer.toList() else null
        buffer.clear()
        state = State.IDLE
        return out
    }

    private fun verticalRangeCm(fromMs: Long): Double {
        val window = buffer.filter { it.tMs >= fromMs }
        if (window.size < 3) return 0.0
        val r = window.map { it.radiusPx }.sorted()[window.size / 2]
        if (r <= 0f) return 0.0
        val cmPerPx = plateDiameterCm / (2.0 * r)
        return (window.maxOf { it.yPx } - window.minOf { it.yPx }) * cmPerPx
    }
}
