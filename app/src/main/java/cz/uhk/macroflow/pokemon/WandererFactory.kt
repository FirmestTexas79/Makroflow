package cz.uhk.macroflow.pokemon

import android.content.Context
import android.widget.ImageView

object WandererFactory {

    /**
     * ✅ Tady budeme mít definovaných všech 151 pokémonů a jejich pohybů!
     */
    fun create(context: Context, pokemonView: ImageView, pokemonId: String): PokemonBehavior {
        return when (pokemonId) {

            "050" -> {
                // Diglett – chodí po zemi a hrabe se pod zemí (DigTransitionEffect)
                StandardWanderer(
                    context, pokemonView, pokemonId,
                    baseScale = 1.0f,
                    effect = DigTransitionEffect()
                )
            }

            "094" -> {
                // Gengar – je menší (0.7f) a teleportuje se kouřem (SmokeTransitionEffect)
                StandardWanderer(
                    context, pokemonView, pokemonId,
                    baseScale = 0.7f,
                    effect = SmokeTransitionEffect()
                )
            }

            // Až budeš přidávat další:
            // "001" -> StandardWanderer(context, pokemonView, pokemonId, baseScale = 1.0f, effect = VineTransitionEffect())
            // "025" -> StandardWanderer(context, pokemonView, pokemonId, baseScale = 1.0f, effect = ElectricTransitionEffect())

            else -> {
                // Výchozí chodec pro kohokoliv jiného
                StandardWanderer(
                    context, pokemonView, pokemonId,
                    baseScale = 1.0f,
                    effect = SmokeTransitionEffect()
                )
            }
        }
    }
}