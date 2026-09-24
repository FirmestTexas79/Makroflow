package cz.uhk.macroflow.energy

/**
 * Energetická hodnota potravin podle nařízení (EU) č. 1169/2011, příloha XIV.
 * Jediné místo v aplikaci, kde se z maker počítají kcal / kJ.
 *
 * Sacharidy jsou v EU uváděny BEZ vlákniny (vláknina má vlastní faktor),
 * což odpovídá datům z OpenFoodFacts i českým etiketám.
 */
object FoodEnergy {
    const val KJ_PER_G_PROTEIN = 17.0
    const val KJ_PER_G_CARBS = 17.0
    const val KJ_PER_G_FAT = 37.0
    const val KJ_PER_G_FIBER = 8.0

    const val KCAL_PER_G_PROTEIN = 4.0
    const val KCAL_PER_G_CARBS = 4.0
    const val KCAL_PER_G_FAT = 9.0
    const val KCAL_PER_G_FIBER = 2.0

    const val KJ_PER_KCAL = 4.184

    fun kcal(protein: Double, carbs: Double, fat: Double, fiber: Double = 0.0): Double =
        protein * KCAL_PER_G_PROTEIN + carbs * KCAL_PER_G_CARBS + fat * KCAL_PER_G_FAT + fiber * KCAL_PER_G_FIBER

    fun kj(protein: Double, carbs: Double, fat: Double, fiber: Double = 0.0): Double =
        protein * KJ_PER_G_PROTEIN + carbs * KJ_PER_G_CARBS + fat * KJ_PER_G_FAT + fiber * KJ_PER_G_FIBER

    // Float přetížení – entity v aplikaci ukládají makra jako Float
    fun kcal(protein: Float, carbs: Float, fat: Float, fiber: Float = 0f): Double =
        kcal(protein.toDouble(), carbs.toDouble(), fat.toDouble(), fiber.toDouble())

    fun kj(protein: Float, carbs: Float, fat: Float, fiber: Float = 0f): Float =
        kj(protein.toDouble(), carbs.toDouble(), fat.toDouble(), fiber.toDouble()).toFloat()

    fun kjToKcal(kj: Double): Double = kj / KJ_PER_KCAL

    /**
     * Energie potraviny: přednost má hodnota z etikety (kJ), pokud je známá,
     * jinak výpočet z maker. Etiketa zahrnuje i alkohol, polyoly apod.
     */
    fun kcalPreferLabel(labelKj: Float, protein: Float, carbs: Float, fat: Float, fiber: Float = 0f): Double =
        if (labelKj > 0.1f) kjToKcal(labelKj.toDouble()) else kcal(protein, carbs, fat, fiber)
}
