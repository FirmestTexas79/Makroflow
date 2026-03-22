package cz.uhk.macroflow

/**
 * Definice všech achievementů v Makroflow.
 *
 * Každý achievement má:
 *  - id          : unikátní string klíč
 *  - category    : pro seskupení na obrazovce
 *  - tier        : BRONZE / SILVER / GOLD / DIAMOND
 *  - titleCs     : český název
 *  - descCs      : popis co splnit
 *  - emoji       : placeholder ikona (dokud nebudou obrázky)
 */

enum class AchievementTier(val labelCs: String, val color: String) {
    BRONZE  ("Bronzová",  "#BC6C25"),
    SILVER  ("Stříbrná",  "#9E9E9E"),
    GOLD    ("Zlatá",     "#DDA15E"),
    DIAMOND ("Diamantová","#4A8FA8")
}

enum class AchievementCategory(val labelCs: String, val emoji: String) {
    STREAK      ("Zápisový řetězec", "🔥"),
    PROTEIN     ("Bílkoviny",        "🥩"),
    CARBS       ("Sacharidy",        "⚡"),
    FAT         ("Tuky",             "🥑"),
    WATER       ("Hydratace",        "💧"),
    CHECKIN     ("Ranní rituál",     "🌅"),
    SYMMETRY    ("Symetrie těla",    "📐"),
    WEIGHT      ("Váhový pokrok",    "⚖️"),
    VARIETY     ("Rozmanitost",      "🍽️"),
    MILESTONE   ("Milníky",          "🏆")
}

data class AchievementDef(
    val id: String,
    val category: AchievementCategory,
    val tier: AchievementTier,
    val titleCs: String,
    val descCs: String,
    val emoji: String
)

object AchievementRegistry {

    val all: List<AchievementDef> = listOf(

        // ══ ZÁPISOVÝ ŘETĚZEC ══════════════════════════════════════════
        AchievementDef("streak_3",  AchievementCategory.STREAK, AchievementTier.BRONZE,
            "Zápisový začátečník",  "3 dny záznamu za sebou",    "📓"),
        AchievementDef("streak_10", AchievementCategory.STREAK, AchievementTier.SILVER,
            "Pravidelný zapisovatel","10 dní záznamu za sebou",  "⚡"),
        AchievementDef("streak_40", AchievementCategory.STREAK, AchievementTier.GOLD,
            "Zápisová elita",       "40 dní záznamu za sebou",   "🌙"),
        AchievementDef("streak_100", AchievementCategory.STREAK, AchievementTier.DIAMOND,
            "Mistr zápisů: 100 dní!", "100 dní záznamu za sebou",   "💎"),

        // ══ BÍLKOVINY ════════════════════════════════════════════════
        AchievementDef("protein_bronze",  AchievementCategory.PROTEIN, AchievementTier.BRONZE,
            "Protein Starter",    "Splň proteinový cíl 3× za sebou",      "🥚"),
        AchievementDef("protein_silver",  AchievementCategory.PROTEIN, AchievementTier.SILVER,
            "Protein Warrior",    "Splň proteinový cíl 10× za sebou",     "🍗"),
        AchievementDef("protein_gold",    AchievementCategory.PROTEIN, AchievementTier.GOLD,
            "Protein Master",     "Splň proteinový cíl 40× celkem",      "💪"),
        AchievementDef("protein_diamond", AchievementCategory.PROTEIN, AchievementTier.DIAMOND,
            "Protein Legend",     "Splň proteinový cíl 100× celkem",      "🥩"),

        // ══ SACHARIDY ════════════════════════════════════════════════
        AchievementDef("carbs_bronze",  AchievementCategory.CARBS, AchievementTier.BRONZE,
            "Sacharidový začátečník", "Splň sacharidový cíl 3× za sebou",  "🍯"),
        AchievementDef("carbs_silver",  AchievementCategory.CARBS, AchievementTier.SILVER,
            "Sacharidový nadšenec",   "Splň sacharidový cíl 10× za sebou", "🍞"),
        AchievementDef("carbs_gold",    AchievementCategory.CARBS, AchievementTier.GOLD,
            "Sacharidový expert",     "Splň sacharidový cíl 40× celkem",  "🌾"),
        AchievementDef("carbs_diamond", AchievementCategory.CARBS, AchievementTier.DIAMOND,
            "Sacharidová legenda",    "Splň sacharidový cíl 100× celkem",  "⚗️"),

        // ══ TUKY ═════════════════════════════════════════════════════
        AchievementDef("fat_bronze",  AchievementCategory.FAT, AchievementTier.BRONZE,
            "Tukový průzkumník",  "Splň tukový cíl 3× za sebou",         "🐟"),
        AchievementDef("fat_silver",  AchievementCategory.FAT, AchievementTier.SILVER,
            "Tukový praktik",     "Splň tukový cíl 10× za sebou",        "🥑"),
        AchievementDef("fat_gold",    AchievementCategory.FAT, AchievementTier.GOLD,
            "Tukový mistr",       "Splň tukový cíl 40× celkem",         "🌰"),
        AchievementDef("fat_diamond", AchievementCategory.FAT, AchievementTier.DIAMOND,
            "Tukový génius",      "Splň tukový cíl 100× celkem",         "⚗️"),

        // ══ HYDRATACE ════════════════════════════════════════════════
        AchievementDef("water_bronze",  AchievementCategory.WATER, AchievementTier.BRONZE,
            "První doušek",       "Splň hydratační cíl 3× za sebou",     "💧"),
        AchievementDef("water_silver",  AchievementCategory.WATER, AchievementTier.SILVER,
            "Vodní nadšenec",     "Splň hydratační cíl 10× za sebou",    "🌊"),
        AchievementDef("water_gold",    AchievementCategory.WATER, AchievementTier.GOLD,
            "Hydratační expert",  "Splň hydratační cíl 40× celkem",     "🏊"),
        AchievementDef("water_diamond", AchievementCategory.WATER, AchievementTier.DIAMOND,
            "Vodní legenda",      "Splň hydratační cíl 100× celkem",     "💎"),

        // ══ RANNÍ RITUÁL ═════════════════════════════════════════════
        AchievementDef("checkin_bronze",  AchievementCategory.CHECKIN, AchievementTier.BRONZE,
            "Ranní odhodlanec",   "Dokonči ranní rituál 3× za sebou",    "🌅"),
        AchievementDef("checkin_silver",  AchievementCategory.CHECKIN, AchievementTier.SILVER,
            "Ranní bojovník",     "Dokonči ranní rituál 10× za sebou",   "☀️"),
        AchievementDef("checkin_gold",    AchievementCategory.CHECKIN, AchievementTier.GOLD,
            "Ranní mistr",        "Dokonči ranní rituál 40× celkem",    "🏅"),
        AchievementDef("checkin_diamond", AchievementCategory.CHECKIN, AchievementTier.DIAMOND,
            "Ranní legenda",      "Dokonči ranní rituál 100× celkem",    "👑"),

        // ══ SYMETRIE TĚLA ════════════════════════════════════════════
        AchievementDef("symmetry_bronze",  AchievementCategory.SYMMETRY, AchievementTier.BRONZE,
            "Změřen",             "Zadej tělesné míry poprvé",           "📏"),
        AchievementDef("symmetry_silver",  AchievementCategory.SYMMETRY, AchievementTier.SILVER,
            "Progres viditelný",  "Zlepši symetrii o 5% oproti začátku", "📈"),
        AchievementDef("symmetry_gold",    AchievementCategory.SYMMETRY, AchievementTier.GOLD,
            "Symetrie 80%+",      "Dosáhni celkového skóre symetrie 80%","⚖️"),
        AchievementDef("symmetry_diamond", AchievementCategory.SYMMETRY, AchievementTier.DIAMOND,
            "Symetrie 90%+",      "Dosáhni celkového skóre symetrie 90%","🔷"),

        // ══ VÁHOVÝ POKROK ════════════════════════════════════════════
        AchievementDef("weight_bronze",  AchievementCategory.WEIGHT, AchievementTier.BRONZE,
            "První vážení",       "Zaznamenej váhu poprvé v rituálu",    "⚖️"),
        AchievementDef("weight_silver",  AchievementCategory.WEIGHT, AchievementTier.SILVER,
            "Konzistentní váha",  "Važ se 10 dní v řadě",               "📊"),
        AchievementDef("weight_gold",    AchievementCategory.WEIGHT, AchievementTier.GOLD,
            "Pokrok na váze",     "Změna váhy o 2kg oproti začátku",     "📉"),
        AchievementDef("weight_diamond", AchievementCategory.WEIGHT, AchievementTier.DIAMOND,
            "Transformace",       "Změna váhy o 5kg oproti začátku",    "🦋"),

        // ══ ROZMANITOST JÍDELNÍČKU ════════════════════════════════════
        AchievementDef("variety_bronze",  AchievementCategory.VARIETY, AchievementTier.BRONZE,
            "Průzkumník chutí",   "Zaloguj 5 různých jídel",            "🍽️"),
        AchievementDef("variety_silver",  AchievementCategory.VARIETY, AchievementTier.SILVER,
            "Gurmán",             "Zaloguj 15 různých jídel",            "👨‍🍳"),
        AchievementDef("variety_gold",    AchievementCategory.VARIETY, AchievementTier.GOLD,
            "Gastronomický mistr","Zaloguj 40 různých jídel",           "🌟"),
        AchievementDef("variety_diamond", AchievementCategory.VARIETY, AchievementTier.DIAMOND,
            "Jídelní encyklopedie","Zaloguj 100 různých jídel",          "📚"),

        // ══ MILNÍKY ══════════════════════════════════════════════════
        AchievementDef("milestone_first", AchievementCategory.MILESTONE, AchievementTier.BRONZE,
            "Vítej v Makroflow!", "Otevři aplikaci poprvé",              "🎉"),
        AchievementDef("milestone_week",  AchievementCategory.MILESTONE, AchievementTier.SILVER,
            "Týden s Makroflow",  "Používej aplikaci 3 dny",             "📅"),
        AchievementDef("milestone_month", AchievementCategory.MILESTONE, AchievementTier.GOLD,
            "Měsíc s Makroflow", "Používej aplikaci 10 dní",            "🗓️"),
        AchievementDef("milestone_perfect",AchievementCategory.MILESTONE, AchievementTier.DIAMOND,
            "Perfektní týden",    "7 dní — rituál + makra + voda vše splněno", "💎")
    )

    fun findById(id: String) = all.find { it.id == id }
    fun byCategory(cat: AchievementCategory) = all.filter { it.category == cat }
}