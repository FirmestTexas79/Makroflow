package cz.uhk.macroflow.pokemon

data class PokedexEntry(
    val id: String,         // např. "050"
    val webName: String,    // "diglett" (pro URL)
    val displayName: String, // "Diglett"
    val type: String,
    val macroDesc: String
)

object PokedexRegistry {
    val list = listOf(
        PokedexEntry("050", "diglett", "Diglett", "Zemní", "Malý pokémon, co rád pomáhá s ranním kardiem."),
        PokedexEntry("094", "gengar", "Gengar", "Duch", "Těžká váha v noci. Miluje tmu a cheat meals.")
    )
}