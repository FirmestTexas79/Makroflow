package cz.uhk.macroflow.pokemon

data class PokedexEntry(
    val id: String,
    val webName: String,
    val displayName: String,
    val type: String,
    val macroDesc: String,
    val rarity: Rarity
)

object PokedexRegistry {
    val list = listOf(

        // ── COMMON ───────────────────────────────────────────────────
        PokedexEntry("025", "pikachu",    "Pikachu",    "ELECTRIC",
            "Rychlý zdroj energie. Ideální pro pre-workout — lehký, výbušný.", Rarity.COMMON),

        PokedexEntry("050", "diglett",    "Diglett",    "GROUND",
            "Malý pokémon, co rád pomáhá s ranním kardiem.",                  Rarity.COMMON),

        PokedexEntry("133", "eevee",      "Eevee",      "NORMAL",
            "Vyvážená makra — přizpůsobí se jakémukoliv jídelníčku.",         Rarity.COMMON),

        // ── RARE ─────────────────────────────────────────────────────
        PokedexEntry("001", "bulbasaur",  "Bulbasaur",  "GRASS / POISON",
            "Vyvážená volba plná vlákniny a sacharidů pro svaly.",            Rarity.RARE),

        PokedexEntry("004", "charmander", "Charmander", "FIRE",
            "Vysoký metabolismus. Spaluje kalorie jako šílený.",              Rarity.RARE),

        PokedexEntry("007", "squirtle",   "Squirtle",   "WATER",
            "Hydratační šampión. Pomáhá plnit denní cíl vody.",              Rarity.RARE),

        PokedexEntry("092", "gastly",     "Gastly",     "GHOST / POISON",
            "Noční jedlík. Spawnuje jen ve tmě — vyhýbá se sacharidům.",     Rarity.RARE),

        // ── EPIC ─────────────────────────────────────────────────────
        PokedexEntry("093", "haunter",    "Haunter",    "GHOST / POISON",
            "Noční makro sabotér. Ukradne ti kalorie než si to uvědomíš.",   Rarity.EPIC),

        PokedexEntry("094", "gengar",     "Gengar",     "GHOST / POISON",
            "Těžká váha v noci. Miluje tmu a cheat meals.",                   Rarity.EPIC),

        PokedexEntry("143", "snorlax",    "Snorlax",    "NORMAL",
            "Král bulku. Potřebuje streak 7 dní — pak se probudí z kelu.",   Rarity.EPIC),

        // ── LEGENDARY ────────────────────────────────────────────────
        PokedexEntry("006", "charizard",  "Charizard",  "FIRE / FLYING",
            "Ohnivé makra pro hardcore cutting. Streak 30 dní ho přivolá.",  Rarity.LEGENDARY),

        PokedexEntry("150", "mewtwo",     "Mewtwo",     "PSYCHIC",
            "Geneticky dokonalá makra. Vzácný. Streak 60 dní. Opatrně.",    Rarity.LEGENDARY),

        // ── MYTHIC ───────────────────────────────────────────────────
        PokedexEntry("151", "mew",        "Mew",        "PSYCHIC",
            "Obsahuje DNA všech pokémonů i maker. Streak 100 dní. Legenda.", Rarity.MYTHIC)
    )

    fun findById(id: String) = list.find { it.id == id }
    fun findByName(name: String) = list.find { it.displayName.uppercase() == name.uppercase() }
}