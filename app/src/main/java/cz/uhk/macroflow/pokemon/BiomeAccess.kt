package cz.uhk.macroflow.pokemon

/**
 * Pravidla přístupu do biomů. Záměrně bez závislosti na Androidu (unit testy).
 *
 * Hory: uživatel musí **ten den** ujít daný počet kroků. Kroky se nulují o půlnoci
 * (StepsEntity je po dnech), takže zámek se každý den zavírá znovu. Kdo už v horách je,
 * toho nevyhazujeme – kontroluje se jen při vstupu.
 */
object BiomeAccess {

    const val MOUNTAINS_DAILY_STEPS = 5000

    private val requiredDailySteps: Map<BiomeType, Int> = mapOf(
        BiomeType.MOUNTAINS to MOUNTAINS_DAILY_STEPS
    )

    fun requiredSteps(biome: BiomeType): Int = requiredDailySteps[biome] ?: 0

    fun canEnter(biome: BiomeType, stepsToday: Int): Boolean = stepsToday >= requiredSteps(biome)

    fun missingSteps(biome: BiomeType, stepsToday: Int): Int =
        (requiredSteps(biome) - stepsToday).coerceAtLeast(0)
}
