package cz.uhk.macroflow.energy

import kotlin.math.sqrt

/**
 * Trend tělesné hmotnosti – Kalmanův filtr s modelem lokálního lineárního trendu.
 *
 * Stav: x = [L, S]ᵀ, L = „skutečná“ hmotnost bez výkyvů vody (kg), S = změna za den (kg/den).
 *   predikce o 1 den:  L' = L + S,  S' = S          (F = [[1,1],[0,1]])
 *   měření:            z = L + šum(σ_obs)          (H = [1, 0])
 *
 * Proč ne klouzavý průměr nebo regrese přes záznamy:
 *  - dny bez vážení se zpracují správně (jen predikce bez korekce),
 *  - výsledek má rozptyl → interval spolehlivosti predikce místo „ručního“ stínu,
 *  - dřívější regrese počítala s pořadím záznamu, ne s dny, takže mezery zkreslovaly sklon.
 *
 * Parametry (kg):
 *  - σ_obs = 0,7 – denní výkyvy vody, glykogenu a obsahu střev bývají ±0,5–1 kg,
 *  - q_level = 0,05 – náhodné odchylky skutečné hmotnosti mimo trend za den,
 *  - q_slope = 0,004 – jak rychle se může měnit tempo (kg/den za den).
 */
class WeightTrend(
    private val obsSd: Double = 0.7,
    private val levelSd: Double = 0.05,
    private val slopeSd: Double = 0.004
) {
    /** Odhad v jednom dni. Rozptyly v kg² a (kg/den)². */
    data class Point(
        val day: Int,
        val level: Double,
        val slope: Double,
        val varLevel: Double,
        val varSlope: Double,
        val covLevelSlope: Double
    ) {
        val sdLevel: Double get() = sqrt(varLevel)
        val sdSlope: Double get() = sqrt(varSlope)
    }

    /**
     * @param weights mapa den → vážení (den = pořadové číslo dne, např. epochDay); dny bez vážení chybí.
     * @return odhad pro každý den od prvního do posledního vážení (včetně dnů bez vážení).
     */
    fun filter(weights: Map<Int, Double>): List<Point> {
        if (weights.isEmpty()) return emptyList()
        val first = weights.keys.min()
        val last = weights.keys.max()

        var l = weights.getValue(first)
        var s = 0.0
        // Počáteční nejistota: úroveň = jedno měření, tempo neznámé (±0,1 kg/den)
        var pLL = obsSd * obsSd
        var pLS = 0.0
        var pSS = 0.1 * 0.1

        val r = obsSd * obsSd
        val qL = levelSd * levelSd
        val qS = slopeSd * slopeSd

        val out = ArrayList<Point>(last - first + 1)
        out += Point(first, l, s, pLL, pSS, pLS)

        for (day in first + 1..last) {
            // Predikce
            l += s
            val nLL = pLL + 2 * pLS + pSS + qL
            val nLS = pLS + pSS
            val nSS = pSS + qS
            pLL = nLL; pLS = nLS; pSS = nSS

            // Korekce, pokud se ten den vážil
            weights[day]?.let { z ->
                val innovation = z - l
                val sInn = pLL + r
                val kL = pLL / sInn
                val kS = pLS / sInn
                l += kL * innovation
                s += kS * innovation
                val uLL = (1 - kL) * pLL
                val uLS = (1 - kL) * pLS
                val uSS = pSS - kS * pLS
                pLL = uLL; pLS = uLS; pSS = uSS
            }
            out += Point(day, l, s, pLL, pSS, pLS)
        }
        return out
    }

    /**
     * Rauch–Tung–Striebel vyhlazení: odhad v každém dni s využitím VŠECH vážení
     * (i pozdějších). Pro zpětné vyhodnocení okna (adaptivní výdej) je přesnější
     * než samotný filtr, jehož začátek stojí jen na prvním vážení.
     */
    fun smooth(weights: Map<Int, Double>): List<Point> {
        val filtered = filter(weights)
        if (filtered.size < 2) return filtered
        val out = filtered.toMutableList()
        val qL = levelSd * levelSd
        val qS = slopeSd * slopeSd
        for (i in filtered.size - 2 downTo 0) {
            val f = filtered[i]
            val next = out[i + 1]
            // Predikovaná kovariance P⁻(t+1) = F·P(t)·Fᵀ + Q
            val pLL = f.varLevel + 2 * f.covLevelSlope + f.varSlope + qL
            val pLS = f.covLevelSlope + f.varSlope
            val pSS = f.varSlope + qS
            val det = pLL * pSS - pLS * pLS
            if (det <= 0.0) continue
            // C = P(t)·Fᵀ·P⁻(t+1)⁻¹
            val aLL = f.varLevel + f.covLevelSlope; val aLS = f.covLevelSlope
            val aSL = f.covLevelSlope + f.varSlope; val aSS = f.varSlope
            val iLL = pSS / det; val iLS = -pLS / det; val iSS = pLL / det
            val cLL = aLL * iLL + aLS * iLS; val cLS = aLL * iLS + aLS * iSS
            val cSL = aSL * iLL + aSS * iLS; val cSS = aSL * iLS + aSS * iSS
            // x_s(t) = x(t) + C·(x_s(t+1) − F·x(t))
            val dL = next.level - (f.level + f.slope)
            val dS = next.slope - f.slope
            val level = f.level + cLL * dL + cLS * dS
            val slope = f.slope + cSL * dL + cSS * dS
            // P_s(t) = P(t) + C·(P_s(t+1) − P⁻(t+1))·Cᵀ
            val eLL = next.varLevel - pLL; val eLS = next.covLevelSlope - pLS; val eSS = next.varSlope - pSS
            val mLL = cLL * eLL + cLS * eLS; val mLS = cLL * eLS + cLS * eSS
            val mSL = cSL * eLL + cSS * eLS; val mSS = cSL * eLS + cSS * eSS
            out[i] = Point(
                day = f.day, level = level, slope = slope,
                varLevel = f.varLevel + mLL * cLL + mLS * cLS,
                varSlope = f.varSlope + mSL * cSL + mSS * cSS,
                covLevelSlope = f.covLevelSlope + mLL * cSL + mLS * cSS
            )
        }
        return out
    }

    /**
     * Posune odhad o [days] dní bez vážení (jen predikční krok filtru) – např. když vybraný den
     * leží za posledním vážením. Vrací celý stav včetně kovariancí.
     */
    fun predict(from: Point, days: Int): Point {
        var p = from
        repeat(days.coerceAtLeast(0)) {
            p = Point(
                day = p.day + 1,
                level = p.level + p.slope,
                slope = p.slope,
                varLevel = p.varLevel + 2 * p.covLevelSlope + p.varSlope + levelSd * levelSd,
                varSlope = p.varSlope + slopeSd * slopeSd,
                covLevelSlope = p.covLevelSlope + p.varSlope
            )
        }
        return p
    }

    /**
     * Zpřesní odhad nezávislou informací o tempu (kg/den ± [sd]), např. z energetické bilance.
     * Kalmanova korekce s H = [0, 1]: tempo se posune úměrně nejistotám a přes kovarianci
     * se lehce opraví i úroveň. Vrací nový stav a váhu, s jakou se informace uplatnila (0..1).
     */
    fun observeSlope(from: Point, slope: Double, sd: Double): Pair<Point, Double> {
        val r = sd * sd
        val sInn = from.varSlope + r
        if (sInn <= 0.0) return from to 0.0
        val innovation = slope - from.slope
        val kL = from.covLevelSlope / sInn
        val kS = from.varSlope / sInn
        val updated = Point(
            day = from.day,
            level = from.level + kL * innovation,
            slope = from.slope + kS * innovation,
            varLevel = from.varLevel - kL * from.covLevelSlope,
            varSlope = from.varSlope - kS * from.varSlope,
            covLevelSlope = from.covLevelSlope - kL * from.varSlope
        )
        return updated to kS
    }

    /**
     * Predikce skutečné hmotnosti za [daysAhead] dní z posledního odhadu.
     * @return (střed, směrodatná odchylka) – pro interval ±1,96·sd.
     */
    fun forecast(from: Point, daysAhead: Int): Pair<Double, Double> {
        val t = daysAhead.toDouble()
        val mean = from.level + from.slope * t
        // Var(L + tS) + nahromaděný procesní šum úrovně i tempa
        var variance = from.varLevel + 2 * t * from.covLevelSlope + t * t * from.varSlope
        variance += t * levelSd * levelSd + slopeSd * slopeSd * t * (t + 1) * (2 * t + 1) / 6.0
        return mean to sqrt(variance.coerceAtLeast(0.0))
    }
}
