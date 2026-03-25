package cz.uhk.macroflow.pokemon

import android.content.Context
import android.widget.ImageView

private data class WandererConfig(
    val pokemonId: String,
    val baseScale: Float,
    val effectFactory: () -> TransitionEffect
)

object WandererFactory {

    // ── Centrální konfigurace všech pokémonů na liště ─────────────────
    // baseScale: větší hodnota = větší sprite na liště
    // effectFactory: animace přechodu mezi zónami
    private val CONFIGS = listOf(

        // ── COMMON ───────────────────────────────────────────────────
        WandererConfig("050", 3.0f) { DigTransitionEffect() },          // Diglett — malý, potřebuje zvětšení
        WandererConfig("025", 2.0f) { SmokeTransitionEffect(false) },   // Pikachu
        WandererConfig("133", 1.8f) { SmokeTransitionEffect(false) },   // Eevee

        // ── RARE ─────────────────────────────────────────────────────
        WandererConfig("001", 1.8f) { SmokeTransitionEffect(false) },   // Bulbasaur
        WandererConfig("004", 1.8f) { SmokeTransitionEffect(false) },   // Charmander
        WandererConfig("007", 1.8f) { SmokeTransitionEffect(false) },   // Squirtle
        WandererConfig("092", 1.6f) { SmokeTransitionEffect(true)  },   // Gastly — fialový kouř

        // ── EPIC ─────────────────────────────────────────────────────
        WandererConfig("093", 1.6f) { SmokeTransitionEffect(true)  },   // Haunter — fialový kouř
        WandererConfig("094", 1.5f) { SmokeTransitionEffect(true)  },   // Gengar — fialový kouř
        WandererConfig("143", 2.2f) { SmokeTransitionEffect(false) },   // Snorlax — velký

        // ── LEGENDARY ────────────────────────────────────────────────
        WandererConfig("006", 2.0f) { SmokeTransitionEffect(false) },   // Charizard
        WandererConfig("150", 1.8f) { SmokeTransitionEffect(true)  },   // Mewtwo — fialový kouř

        // ── MYTHIC ───────────────────────────────────────────────────
        WandererConfig("151", 1.8f) { SmokeTransitionEffect(true)  }    // Mew — fialový kouř
    )

    private val configMap by lazy { CONFIGS.associateBy { it.pokemonId } }

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
        baseScale     = 1.8f,
        effectFactory = { SmokeTransitionEffect(false) }
    )
}