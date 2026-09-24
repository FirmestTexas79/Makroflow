package cz.uhk.macroflow.energy

import java.text.Normalizer
import kotlin.math.max
import kotlin.math.min

// ═══════════════════════════════════════════════════════════════════════════
//  Faktoriální model denního energetického výdeje (bez závislosti na Androidu)
//
//  TDEE = BMR + NEAT(životní styl) + chůze(kroky) + trénink + TEF
//  Každá složka se počítá PRÁVĚ JEDNOU:
//   - BMR:        Mifflin-St Jeor (1990), nebo Katch-McArdle při změřeném % tuku
//   - NEAT:       BMR × (faktor životního stylu − 1), BEZ chůze a cvičení
//   - chůze:      čistý výdej dle ACSM rovnice pro chůzi z kroků a délky kroku
//   - trénink:    čistý výdej (MET − 1) × kg × h, Compendium of Physical Activities
//   - TEF:        10 % celkového výdeje (Westerterp 2004)
//  Zdroje a odvození: docs/adr/0002-energeticky-model.md
// ═══════════════════════════════════════════════════════════════════════════

enum class Sex {
    MALE, FEMALE;

    companion object {
        fun from(value: String?): Sex = if (value?.lowercase()?.startsWith("f") == true ||
            value?.lowercase()?.startsWith("ž") == true) FEMALE else MALE
    }
}

enum class Goal {
    CUT, MAINTAIN, BULK;

    companion object {
        fun from(value: String?): Goal = entries.firstOrNull { it.name == value?.uppercase() } ?: MAINTAIN
    }
}

enum class Diet {
    BALANCED, HIGH_PROTEIN, LOW_CARB, KETO, VEGAN;

    companion object {
        /** Přijímá české popisky z UI ("Vyvážená", "Low Carb") i enumy ("HIGH_PROTEIN"). */
        fun from(value: String?): Diet {
            val n = Normalizer.normalize(value ?: "", Normalizer.Form.NFD)
                .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
                .uppercase().trim().replace(" ", "_")
            return when {
                n.contains("KETO") -> KETO
                n.contains("LOW_CARB") -> LOW_CARB
                n.contains("HIGH") || n.contains("PROTEIN") -> HIGH_PROTEIN
                n.contains("VEGAN") -> VEGAN
                else -> BALANCED
            }
        }
    }
}

/**
 * Pohyb MIMO chůzi a cvičení (typ práce, domácnost). Kroky a tréninky se přičítají zvlášť.
 * Faktory jsou kalibrované tak, aby sedavý člověk s 6000 kroky vyšel na PAL ≈ 1,4
 * (FAO/WHO/UNU 2004, „sedentary“ 1,40–1,69). Individuální odchylku doladí adaptivní výdej.
 */
enum class Lifestyle(val factor: Double, val storedMultiplier: Float, val label: String) {
    SEDENTARY(1.15, 1.2f, "Sedavá práce"),
    ACTIVE_JOB(1.30, 1.4f, "Práce v pohybu"),
    PHYSICAL_JOB(1.50, 1.6f, "Fyzicky náročná práce");

    companion object {
        /** Zpětná kompatibilita s UserProfileEntity.activityMultiplier (1.2 / 1.4 / 1.6). */
        fun fromStored(multiplier: Float): Lifestyle =
            entries.minByOrNull { kotlin.math.abs(it.storedMultiplier - multiplier) } ?: SEDENTARY
    }
}

data class Person(
    val weightKg: Double,
    val heightCm: Double,
    val ageYears: Int,
    val sex: Sex,
    /** Změřené % tuku; null = neznámé (odhadne se z BMI). */
    val measuredBodyFatPct: Double? = null
)

enum class StrengthKind(val met: Double) {
    // Compendium of Physical Activities (Ainsworth 2011), kódy 02054 a 02052
    PUSH(3.5), PULL(3.5),
    LEGS(5.0), FULL_BODY(5.0);

    companion object {
        fun from(type: String?): StrengthKind? {
            val t = type?.lowercase() ?: return null
            return when {
                t.contains("full") -> FULL_BODY
                t.contains("legs") -> LEGS
                t.contains("pull") -> PULL
                t.contains("push") -> PUSH
                else -> null
            }
        }
    }
}

sealed interface Exercise {
    val minutes: Double

    data class Strength(val kind: StrengthKind, override val minutes: Double) : Exercise
    data class Run(val speedKmh: Double, override val minutes: Double) : Exercise
    /** [jumps] = celkový počet přeskoků; když je známá i délka, určí se z nich kadence. */
    data class JumpRope(val jumps: Int, override val minutes: Double) : Exercise
    data class Stairs(override val minutes: Double) : Exercise
}

/** Rozpad denního výdeje – každá položka v kcal. */
data class EnergyBreakdown(
    val bmr: Double,
    val lifestyleNeat: Double,
    val walking: Double,
    val exercise: Double,
    val tef: Double,
    val steps: Int,
    /** Korekce z adaptivního výdeje (fáze B): kladná = tělo pálí víc, než říkají rovnice. */
    val adaptive: Double = 0.0
) {
    val modelTotal: Double get() = bmr + lifestyleNeat + walking + exercise + tef
    val total: Double get() = modelTotal + adaptive
}

object EnergyModel {

    const val TEF_FRACTION = 0.10
    /** Předpoklad pro den, který ještě neskončil nebo nemá záznam kroků. */
    const val ASSUMED_DAILY_STEPS = 6000
    const val DEFAULT_STRENGTH_MINUTES = 60.0

    private const val KCAL_PER_LITER_O2 = 5.0
    private const val WALK_NET_ML_O2_PER_KG_PER_M = 0.1   // ACSM: VO2 = 0.1·v + 3.5
    private const val RUN_NET_ML_O2_PER_KG_PER_M = 0.2    // ACSM: VO2 = 0.2·v + 3.5

    // ── Tělesné složení ─────────────────────────────────────────────────────

    fun bmi(p: Person): Double = p.weightKg / ((p.heightCm / 100.0) * (p.heightCm / 100.0))

    /** Deurenberg et al. (1991): %BF = 1,20·BMI + 0,23·věk − 10,8·pohlaví − 5,4 (muž = 1). */
    fun estimatedBodyFatPct(p: Person): Double {
        val sexTerm = if (p.sex == Sex.MALE) 1.0 else 0.0
        return 1.20 * bmi(p) + 0.23 * p.ageYears - 10.8 * sexTerm - 5.4
    }

    fun bodyFatPct(p: Person): Double =
        (p.measuredBodyFatPct?.takeIf { it > 0.0 } ?: estimatedBodyFatPct(p)).coerceIn(3.0, 60.0)

    fun fatFreeMassKg(p: Person): Double = p.weightKg * (1.0 - bodyFatPct(p) / 100.0)

    // ── Bazální metabolismus ────────────────────────────────────────────────

    fun bmrMifflin(p: Person): Double =
        10.0 * p.weightKg + 6.25 * p.heightCm - 5.0 * p.ageYears + if (p.sex == Sex.MALE) 5.0 else -161.0

    fun bmrKatchMcArdle(fatFreeMassKg: Double): Double = 370.0 + 21.6 * fatFreeMassKg

    /** Při změřeném % tuku Katch-McArdle (zohlední svalovou hmotu), jinak Mifflin-St Jeor. */
    fun bmr(p: Person): Double =
        if (p.measuredBodyFatPct != null && p.measuredBodyFatPct > 0.0) bmrKatchMcArdle(fatFreeMassKg(p))
        else bmrMifflin(p)

    // ── Chůze ───────────────────────────────────────────────────────────────

    /** Odhad délky kroku z výšky (0,415 × výška u mužů, 0,413 × výška u žen). */
    fun stepLengthM(p: Person): Double = p.heightCm / 100.0 * if (p.sex == Sex.MALE) 0.415 else 0.413

    /** Čistý výdej chůze (nad klidový) – ACSM: 0,1 ml O2/kg/m, 5 kcal/l O2 → 0,5 kcal/kg/km. */
    fun walkingNetKcal(steps: Int, p: Person): Double {
        val meters = steps.coerceAtLeast(0) * stepLengthM(p)
        return meters * WALK_NET_ML_O2_PER_KG_PER_M * p.weightKg / 1000.0 * KCAL_PER_LITER_O2
    }

    /**
     * Kolik kroků použít pro den:
     *  - bez záznamu (0) → předpoklad, senzor pravděpodobně neběžel,
     *  - dnešek → alespoň předpoklad (den ještě neskončil, cíl nesmí ráno padat),
     *  - uzavřený den → skutečnost.
     */
    fun stepsForDay(recordedSteps: Int?, isToday: Boolean): Int {
        val recorded = recordedSteps ?: 0
        return when {
            recorded <= 0 -> ASSUMED_DAILY_STEPS
            isToday -> max(recorded, ASSUMED_DAILY_STEPS)
            else -> recorded
        }
    }

    // ── Trénink ─────────────────────────────────────────────────────────────

    fun jumpRopeMet(jumpsPerMinute: Double): Double = when {
        // Compendium 15551–15553: pomalu < 100/min, středně 100–120/min, rychle 120–160/min
        jumpsPerMinute < 100 -> 8.8
        jumpsPerMinute <= 120 -> 11.8
        else -> 12.3
    }

    /** Čistý výdej tréninku nad klidový metabolismus (klid už je v BMR). */
    fun exerciseNetKcal(e: Exercise, weightKg: Double): Double {
        fun netMet(met: Double, minutes: Double) = (met - 1.0).coerceAtLeast(0.0) * weightKg * (minutes / 60.0)
        return when (e) {
            is Exercise.Strength -> netMet(e.kind.met, e.minutes)
            is Exercise.Run -> {
                val meters = e.speedKmh * 1000.0 * (e.minutes / 60.0)
                meters * RUN_NET_ML_O2_PER_KG_PER_M * weightKg / 1000.0 * KCAL_PER_LITER_O2
            }
            is Exercise.JumpRope -> {
                val cadence = if (e.minutes > 0 && e.jumps > 0) e.jumps / e.minutes else 110.0
                val minutes = if (e.minutes > 0) e.minutes else e.jumps / 110.0
                netMet(jumpRopeMet(cadence), minutes)
            }
            is Exercise.Stairs -> netMet(9.0, e.minutes) // Compendium 02065, stair-treadmill ergometer
        }
    }

    // ── Celkový výdej ───────────────────────────────────────────────────────

    /**
     * @param adaptiveFactor korekční faktor k z [AdaptiveExpenditure] (1.0 = čistý model)
     */
    fun expenditure(
        p: Person,
        lifestyle: Lifestyle,
        steps: Int,
        exercises: List<Exercise>,
        adaptiveFactor: Double = 1.0
    ): EnergyBreakdown {
        val bmr = bmr(p)
        val neat = bmr * (lifestyle.factor - 1.0)
        val walking = walkingNetKcal(steps, p)
        val exercise = exercises.sumOf { exerciseNetKcal(it, p.weightKg) }
        // TEF je podíl z CELKOVÉHO výdeje: total = (ostatní) / (1 − TEF)
        val withoutTef = bmr + neat + walking + exercise
        val tef = withoutTef / (1.0 - TEF_FRACTION) * TEF_FRACTION
        val modelTotal = withoutTef + tef
        return EnergyBreakdown(bmr, neat, walking, exercise, tef, steps, adaptive = modelTotal * (adaptiveFactor - 1.0))
    }
}

// ═══════════════════════════════════════════════════════════════════════════
//  Cílový příjem a makroživiny
// ═══════════════════════════════════════════════════════════════════════════

data class MacroTargets(
    val kcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val fiberG: Double,
    val waterL: Double,
    val expenditure: EnergyBreakdown
)

object MacroPlanner {

    /** Energie tukové tkáně – zjednodušení (Wishnofsky 1958); přesnější dynamiku řeší adaptivní výdej. */
    const val KCAL_PER_KG_TISSUE = 7700.0

    /** Tempo změny váhy v % tělesné hmotnosti za týden (Helms et al. 2014; Iraki et al. 2019). */
    const val CUT_RATE_PCT_PER_WEEK = -0.5
    const val BULK_RATE_PCT_PER_WEEK = 0.25
    /** Deficit nikdy nepřesáhne tuto část výdeje. */
    const val MAX_DEFICIT_FRACTION = 0.25

    const val FAT_MIN_ENERGY_FRACTION = 0.20       // spodní hranice AMDR (IOM 2005)
    const val FAT_DEFAULT_ENERGY_FRACTION = 0.25
    const val LOW_CARB_MAX_ENERGY_FRACTION = 0.20
    const val KETO_CARBS_G = 30.0

    /** Bílkoviny na kg netukové hmoty (Helms 2014: 2,3–3,1 g/kg FFM při dietě). */
    fun proteinPerKgFfm(goal: Goal, diet: Diet): Double {
        val base = if (goal == Goal.CUT) 2.7 else 2.3
        return if (diet == Diet.HIGH_PROTEIN) base + 0.4 else base
    }

    fun targetKcal(p: Person, goal: Goal, tdee: Double, bmr: Double): Double {
        val ratePct = when (goal) {
            Goal.CUT -> CUT_RATE_PCT_PER_WEEK
            Goal.BULK -> BULK_RATE_PCT_PER_WEEK
            Goal.MAINTAIN -> 0.0
        }
        var delta = p.weightKg * ratePct / 100.0 * KCAL_PER_KG_TISSUE / 7.0
        if (delta < 0) delta = max(delta, -tdee * MAX_DEFICIT_FRACTION)
        val floor = max(bmr, if (p.sex == Sex.MALE) 1500.0 else 1200.0)
        return max(tdee + delta, min(floor, tdee))
    }

    fun fiberG(kcal: Double): Double = max(kcal / 1000.0 * 14.0, 25.0) // IOM 2005 / DGA; EFSA AI 25 g

    /** EFSA 2010: celkový příjem vody 2,5 l (muži) / 2,0 l (ženy), z nápojů ~80 %; + pot při tréninku. */
    fun waterL(sex: Sex, exerciseMinutes: Double): Double =
        (if (sex == Sex.MALE) 2.0 else 1.6) + 0.5 * (exerciseMinutes / 60.0)

    fun plan(
        p: Person,
        goal: Goal,
        diet: Diet,
        lifestyle: Lifestyle,
        steps: Int,
        exercises: List<Exercise>,
        adaptiveFactor: Double = 1.0
    ): MacroTargets {
        val exp = EnergyModel.expenditure(p, lifestyle, steps, exercises, adaptiveFactor)
        val kcal = targetKcal(p, goal, exp.total, exp.bmr)
        val fiber = fiberG(kcal)

        val protein = EnergyModel.fatFreeMassKg(p) * proteinPerKgFfm(goal, diet)
        // Energie pro sacharidy + tuky (vláknina má vlastní 2 kcal/g a do cíle patří)
        val budget = kcal - protein * FoodEnergy.KCAL_PER_G_PROTEIN - fiber * FoodEnergy.KCAL_PER_G_FIBER
        val minFat = kcal * FAT_MIN_ENERGY_FRACTION / FoodEnergy.KCAL_PER_G_FAT

        var carbs: Double
        var fat: Double
        when (diet) {
            Diet.KETO -> {
                carbs = KETO_CARBS_G
                fat = (budget - carbs * FoodEnergy.KCAL_PER_G_CARBS) / FoodEnergy.KCAL_PER_G_FAT
            }
            Diet.LOW_CARB -> {
                carbs = kcal * LOW_CARB_MAX_ENERGY_FRACTION / FoodEnergy.KCAL_PER_G_CARBS
                fat = (budget - carbs * FoodEnergy.KCAL_PER_G_CARBS) / FoodEnergy.KCAL_PER_G_FAT
            }
            else -> {
                fat = kcal * FAT_DEFAULT_ENERGY_FRACTION / FoodEnergy.KCAL_PER_G_FAT
                carbs = (budget - fat * FoodEnergy.KCAL_PER_G_FAT) / FoodEnergy.KCAL_PER_G_CARBS
            }
        }

        // Pojistky: tuk nikdy pod 20 % energie; sacharidy nikdy záporné
        if (fat < minFat) {
            fat = minFat
            carbs = (budget - fat * FoodEnergy.KCAL_PER_G_FAT) / FoodEnergy.KCAL_PER_G_CARBS
        }
        if (carbs < 0) {
            carbs = 0.0
            fat = max(minFat, budget / FoodEnergy.KCAL_PER_G_FAT)
        }

        val exerciseMinutes = exercises.sumOf { it.minutes }
        return MacroTargets(kcal, protein, carbs, fat, fiber, waterL(p.sex, exerciseMinutes), exp)
    }
}
