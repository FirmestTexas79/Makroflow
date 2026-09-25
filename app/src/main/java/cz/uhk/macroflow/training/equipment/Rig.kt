package cz.uhk.macroflow.training.equipment

import cz.uhk.macroflow.training.exercises.Equipment
import cz.uhk.macroflow.training.exercises.Exercise
import java.util.Locale
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * Náčiní u zápisu série (docs/adr/0028), bez Androidu – co nakreslit a jak číst zapsanou váhu.
 *
 * | Náčiní | Zapsaná váha | Obrázek |
 * |---|---|---|
 * | jednoručky (1 / 2) | jedna jednoručka | hlavy rostou s vahou |
 * | olympijská osa | celkem včetně osy 20 kg | kotouče na každé straně |
 * | rovná / EZ činka | celkem (pevná činka) | konce rostou s vahou |
 * | stroj na kotouče (obě / jedna strana) | na jednu stranu | kotouče na rameni stroje |
 * | blok závaží (kladka, stroj s kolíkem) | celkem | bloky závaží na konci lanka |
 */
enum class Rig(val label: String, val weightHint: String) {
    DUMBBELL_PAIR("2 jednoručky", "Váha jedné jednoručky"),
    DUMBBELL_SINGLE("1 jednoručka", "Váha jednoručky"),
    OLYMPIC_BAR("Osa s kotouči", "Celkem včetně osy 20 kg"),
    STRAIGHT_BAR("Rovná činka", "Váha pevné činky"),
    EZ_BAR("EZ činka", "Váha pevné EZ činky"),
    MACHINE_BOTH("Kotouče obě strany", "Váha na jednu stranu"),
    MACHINE_ONE("Kotouče jedna strana", "Váha na zatíženou stranu"),
    STACK("Blok závaží", "Váha na bloku závaží");

    val usesPlates: Boolean get() = this == OLYMPIC_BAR || this == MACHINE_BOTH || this == MACHINE_ONE

    companion object {
        const val OLYMPIC_BAR_KG = 20.0
        const val STACK_BLOCK_KG = 5.0
        /** Kotouče v posilovně (kg), od největšího. Bez 25 a 15 – 25 kg na stranu = 20 + 5. */
        val PLATES = listOf(20.0, 10.0, 5.0, 2.5, 1.25)

        /** Cviky s olympijskou osou (kotouče). Ostatní cviky s osou = pevná činka. */
        private val OLYMPIC = setOf(
            "bench_press", "back_squat", "deadlift", "rdl", "overhead_press",
            "barbell_row", "close_grip_bench", "hip_thrust"
        )
        private val EZ = setOf("ez_bar_curl", "skull_crusher")
        private val SINGLE_DUMBBELL = setOf("db_row", "single_arm_supported_curl")
        /** Stroje na kotouče; ostatní stroje mají blok závaží s kolíkem. */
        private val PLATE_LOADED = setOf(
            "machine_chest_press", "incline_machine_press", "machine_shoulder_press", "machine_row",
            "hack_squat", "leg_press", "single_leg_press", "standing_calf_raise", "seated_calf_raise"
        )

        /** Na výběr u cviku (prázdné = bez obrázku, např. vlastní váha). */
        fun options(e: Exercise): List<Rig> = when (e.equipment) {
            Equipment.DUMBBELL -> listOf(DUMBBELL_PAIR, DUMBBELL_SINGLE)
            Equipment.BARBELL -> listOf(OLYMPIC_BAR, STRAIGHT_BAR, EZ_BAR)
            Equipment.MACHINE -> listOf(MACHINE_BOTH, MACHINE_ONE, STACK)
            Equipment.CABLE -> listOf(STACK)
            Equipment.BAR, Equipment.BODYWEIGHT, Equipment.OTHER -> emptyList()
        }

        fun default(e: Exercise): Rig? = when (e.equipment) {
            Equipment.DUMBBELL -> if (e.id in SINGLE_DUMBBELL) DUMBBELL_SINGLE else DUMBBELL_PAIR
            Equipment.BARBELL -> when (e.id) { in OLYMPIC -> OLYMPIC_BAR; in EZ -> EZ_BAR; else -> STRAIGHT_BAR }
            Equipment.MACHINE -> if (e.id in PLATE_LOADED) MACHINE_BOTH else STACK
            Equipment.CABLE -> STACK
            else -> null
        }

        /** Uložená volba, pokud k cviku patří, jinak výchozí. */
        fun resolve(e: Exercise, saved: String?): Rig? =
            options(e).firstOrNull { it.name == saved } ?: default(e)
    }
}

/** Výpočty pro obrázek a popisek. */
object RigMath {

    data class Plates(val perSide: List<Double>, val remainderKg: Double) {
        val perSideKg: Double get() = perSide.sum()
    }

    /** Váha na jednu stranu k naložení (osa: bez osy, stroj: zapsaná váha). */
    fun perSideKg(rig: Rig, weightKg: Double): Double = when (rig) {
        Rig.OLYMPIC_BAR -> ((weightKg - Rig.OLYMPIC_BAR_KG) / 2).coerceAtLeast(0.0)
        Rig.MACHINE_BOTH, Rig.MACHINE_ONE -> weightKg.coerceAtLeast(0.0)
        else -> 0.0
    }

    /**
     * Kotouče na jednu stranu – od největšího (jako se nakládají). [remainderKg] = co nejde
     * poskládat (např. 21 kg na osu: 0,5 kg na stranu chybí).
     */
    fun plates(perSideKg: Double, inventory: List<Double> = Rig.PLATES): Plates {
        var left = (perSideKg * 100).roundToInt()          // v setinách kg, bez chyb plovoucí čárky
        val out = mutableListOf<Double>()
        for (p in inventory.sortedDescending()) {
            val c = (p * 100).roundToInt()
            while (left >= c) { out += p; left -= c }
        }
        return Plates(out, left / 100.0)
    }

    fun plates(rig: Rig, weightKg: Double): Plates =
        if (rig.usesPlates) plates(perSideKg(rig, weightKg)) else Plates(emptyList(), 0.0)

    /**
     * Velikost jednoručky / konců pevné činky 0–1 podle váhy (logaritmicky – rozdíl 2 a 6 kg je
     * vidět víc než 40 a 44 kg). Jednoručky 1–60 kg, pevné činky 5–60 kg.
     */
    fun size(rig: Rig, weightKg: Double): Double {
        val (lo, hi) = when (rig) {
            Rig.DUMBBELL_PAIR, Rig.DUMBBELL_SINGLE -> 1.0 to 60.0
            Rig.STRAIGHT_BAR, Rig.EZ_BAR -> 5.0 to 60.0
            else -> return 0.0
        }
        val w = weightKg.coerceIn(lo, hi)
        return ln(w / lo) / ln(hi / lo)
    }

    /** Bloky závaží: celé 5kg bloky a zbytek (malé přídavné závaží). */
    data class Stack(val blocks: Int, val extraKg: Double)

    fun stack(weightKg: Double): Stack {
        val w = weightKg.coerceAtLeast(0.0)
        val blocks = floor(w / Rig.STACK_BLOCK_KG + 1e-9).toInt()
        return Stack(blocks, ((w - blocks * Rig.STACK_BLOCK_KG) * 100).roundToInt() / 100.0)
    }

    fun kg(v: Double): String = String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.').replace('.', ',')

    /** Popisek pod obrázkem („Na stranu 20 + 5 kg · osa 20 kg“, „2 × 22,5 kg = 45 kg“). */
    fun summary(rig: Rig, weightKg: Double): String {
        val w = weightKg.coerceAtLeast(0.0)
        return when (rig) {
            Rig.DUMBBELL_PAIR -> "2 × ${kg(w)} kg = ${kg(w * 2)} kg"
            Rig.DUMBBELL_SINGLE -> "1 × ${kg(w)} kg"
            Rig.STRAIGHT_BAR, Rig.EZ_BAR -> "${rig.label} ${kg(w)} kg"
            Rig.OLYMPIC_BAR -> {
                if (w < Rig.OLYMPIC_BAR_KG) "Jen osa 20 kg (zapsáno ${kg(w)} kg)"
                else platesText(plates(rig, w), "osa 20 kg")
            }
            Rig.MACHINE_BOTH -> platesText(plates(rig, w), "celkem ${kg(w * 2)} kg")
            Rig.MACHINE_ONE -> platesText(plates(rig, w), "jen jedna strana")
            Rig.STACK -> {
                val s = stack(w)
                if (w <= 0.0) "Bez závaží"
                else "${s.blocks} × 5 kg" + (if (s.extraKg > 0) " + ${kg(s.extraKg)} kg" else "") + " = ${kg(w)} kg"
            }
        }
    }

    private fun platesText(p: Plates, tail: String): String {
        val body = if (p.perSide.isEmpty()) "Bez kotoučů" else "Na stranu " + p.perSide.joinToString(" + ") { kg(it) } + " kg"
        val rest = if (p.remainderKg > 0.0) " (chybí ${kg(p.remainderKg)} kg na stranu)" else ""
        return "$body$rest · $tail"
    }
}
