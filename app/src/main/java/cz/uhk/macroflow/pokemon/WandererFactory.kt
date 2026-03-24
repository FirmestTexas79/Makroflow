package cz.uhk.macroflow.pokemon

import android.content.Context
import android.widget.ImageView

/**
 * Konfigurace konkrétního Pokémona na liště.
 * Přidání nového = jen nový řádek v CONFIGS.
 */
private data class WandererConfig(
    val pokemonId: String,
    val baseScale: Float,
    val effectFactory: () -> TransitionEffect
)

object WandererFactory {

    // ── Centrální konfigurace všech Pokémonů na liště ──────────────────────
    private val CONFIGS = listOf(

        WandererConfig(
            pokemonId   = "050",   // Diglett
            baseScale   = 3.0f,    // je malý sprite, potřebuje zvětšení
            effectFactory = { DigTransitionEffect() }
        ),

        WandererConfig(
            pokemonId   = "094",   // Gengar
            baseScale   = 1.5f,
            effectFactory = { SmokeTransitionEffect(purple = true) }
        )

        // ── Budoucí rozšíření ──────────────────────────────────────────
        // WandererConfig("066", 1.5f) { SmokeTransitionEffect() },  // Machop
        // WandererConfig("133", 1.8f) { SmokeTransitionEffect() },  // Eevee
        // WandererConfig("007", 1.6f) { SmokeTransitionEffect() },  // Squirtle
    )

    private val configMap by lazy { CONFIGS.associateBy { it.pokemonId } }

    // ── Factory metoda ────────────────────────────────────────────────────
    fun create(context: Context, pokemonView: ImageView, pokemonId: String): PokemonBehavior {
        val cfg = configMap[pokemonId] ?: defaultConfig()
        return StandardWanderer(
            context     = context,
            pokemonView = pokemonView,
            pokemonId   = pokemonId,
            baseScale   = cfg.baseScale,
            effect      = cfg.effectFactory()
        )
    }

    private fun defaultConfig() = WandererConfig(
        pokemonId     = "",
        baseScale     = 1.5f,
        effectFactory = { SmokeTransitionEffect(purple = false) }
    )
}