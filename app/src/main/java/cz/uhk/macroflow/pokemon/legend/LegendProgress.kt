package cz.uhk.macroflow.pokemon.legend

import cz.uhk.macroflow.pokemon.cave.CrystalColor

/**
 * Postup příběhu krystalů (čistý Kotlin, pokryto testy). Podklady v docs/adr/0014.
 *
 * strážce poražen → krystal sebrán (předmět v inventáři) → oba vloženy do svatyně na vrcholu
 * → souboj s legendou → brána otevřená.
 */
data class LegendProgress(
    val bossesDefeated: Set<CrystalColor> = emptySet(),
    /** Krystal byl sebrán z oltáře (jednou provždy). */
    val crystalsTaken: Set<CrystalColor> = emptySet(),
    /** Krystaly, které hráč právě nese (předměty v inventáři). */
    val crystalsInBag: Set<CrystalColor> = emptySet(),
    val crystalsPlaced: Boolean = false,
    val legendFaced: Boolean = false
) {
    enum class Altar { GUARDED, CRYSTAL_READY, EMPTY }

    sealed class Shrine {
        data class NeedCrystals(val missing: Set<CrystalColor>) : Shrine()
        object ReadyToPlace : Shrine()
        /** Krystaly jsou vložené, legenda čeká (souboj se přerušil / aplikace spadla). */
        object LegendAwaits : Shrine()
        object GateOpen : Shrine()
    }

    fun altar(color: CrystalColor): Altar = when {
        color in crystalsTaken -> Altar.EMPTY
        color !in bossesDefeated -> Altar.GUARDED
        else -> Altar.CRYSTAL_READY
    }

    val shrine: Shrine
        get() = when {
            legendFaced -> Shrine.GateOpen
            crystalsPlaced -> Shrine.LegendAwaits
            crystalsInBag.containsAll(CrystalColor.entries) -> Shrine.ReadyToPlace
            else -> Shrine.NeedCrystals(CrystalColor.entries.toSet() - crystalsInBag)
        }

    /** Která lůžka ve svatyni svítí: po vložení obě. */
    val socketsFilled: Set<CrystalColor> get() = if (crystalsPlaced) CrystalColor.entries.toSet() else emptySet()

    companion object {
        // Klíče v GamePrefs (předměty jsou v tabulce user_items → synchronizují se přes Firebase)
        fun bossKey(c: CrystalColor) = "boss_defeated_${c.name}"
        fun takenKey(c: CrystalColor) = c.prefKey
        const val PLACED_KEY = "crystals_placed"
        const val LEGEND_KEY = "legend_faced"

        /** Sestaví stav z úložiště klíč → boolean a počtů předmětů. */
        fun load(flag: (String) -> Boolean, itemCount: (String) -> Int): LegendProgress = LegendProgress(
            bossesDefeated = CrystalColor.entries.filter { flag(bossKey(it)) }.toSet(),
            // Starší verze (před strážci) uměla krystal „sebrat“ bez předmětu do inventáře.
            // Sebrání bez poraženého strážce proto neplatí → krystal je zase na oltáři.
            crystalsTaken = CrystalColor.entries.filter { flag(takenKey(it)) && flag(bossKey(it)) }.toSet(),
            crystalsInBag = CrystalColor.entries.filter { itemCount(it.itemId) > 0 }.toSet(),
            crystalsPlaced = flag(PLACED_KEY),
            legendFaced = flag(LEGEND_KEY)
        )
    }
}

/**
 * Svatyně na vrcholu Hor v art pixelech mapy mountains.png (172 × 384) – musí sedět
 * s tools/mapgen/gen_mountains.py (sekce „vrchol“).
 */
object PeakShrine {
    const val ART_W = 172
    const val ART_H = 384
    /** Středy lůžek (x) a jejich spodní okraj (y). */
    val SOCKETS = mapOf(CrystalColor.BLUE to 82, CrystalColor.RED to 90)
    const val SOCKET_BOTTOM = 49
    /** Brána ve skále: x od–do, y od–do (včetně). */
    const val GATE_LEFT = 80
    const val GATE_RIGHT = 92
    const val GATE_TOP = 28
    const val GATE_BOTTOM = 42

    /**
     * Otevřená brána (šířka × výška podle GATE_*): oblouk stejného tvaru jako zavřená brána
     * v mapě, uvnitř temnota s fialovým svitem a schody vedoucími vzhůru. 0 = průhledné.
     */
    fun openGatePixels(): IntArray {
        val w = GATE_RIGHT - GATE_LEFT + 1
        val h = GATE_BOTTOM - GATE_TOP + 1
        val half = w / 2
        val out = IntArray(w * h)
        fun archTop(dx: Int) = half - kotlin.math.sqrt((half * half - dx * dx).toDouble()).toInt()
        for (y in 0 until h) for (x in 0 until w) {
            val dx = x - half
            val top = archTop(dx)
            if (y < top) continue
            val f = y.toFloat() / (h - 1)
            var c = mix(0xFF07040F.toInt(), 0xFF2A1848.toInt(), f * f)
            if (y == top || x == 0 || x == w - 1) c = 0xFF5A3C8C.toInt()                  // svítící lem
            if (y >= h - 5 && (h - 1 - y) % 2 == 0 && kotlin.math.abs(dx) <= half - 1 - (h - 1 - y) / 2) c = 0xFF3C2A5C.toInt()  // schody
            out[y * w + x] = c
        }
        for ((sx, sy) in listOf(half - 2 to 5, half + 3 to 7, half to 9)) out[sy * w + sx] = 0xFFB488FF.toInt()  // jiskry
        return out
    }

    private fun mix(a: Int, b: Int, t: Float): Int {
        fun ch(s: Int) = (((a shr s) and 0xFF) * (1 - t) + ((b shr s) and 0xFF) * t).toInt()
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}
