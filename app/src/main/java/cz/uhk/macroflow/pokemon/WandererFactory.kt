package cz.uhk.macroflow.pokemon

import android.content.Context
import android.widget.ImageView

private data class WandererConfig(
    val pokemonId: String,
    val baseScale: Float,
    val behaviorFactory: (Context, ImageView, Float) -> PokemonBehavior,
    val effectFactory: () -> TransitionEffect
)

object WandererFactory {

    private val CONFIGS = listOf(

        // --- 🐛 CATERPIE EVOLUTION LINE (NEW) ──────────────────────────
        WandererConfig("010", 2.0f, { ctx, view, sc -> CaterpieWanderer(ctx, view, sc) }) { SmokeTransitionEffect(false) },
        WandererConfig("011", 1.8f, { ctx, view, sc -> MetapodWanderer(ctx, view, sc) })  { SmokeTransitionEffect(false) },
        WandererConfig("012", 2.2f, { ctx, view, sc -> ButterfreeWanderer(ctx, view, sc) }) { SmokeTransitionEffect(false) },

        // --- COMMON ───────────────────────────────────────────────────
        WandererConfig("050", 3.0f, { ctx, view, sc -> StandardWanderer(ctx, view, "050", sc, DigTransitionEffect()) }) { DigTransitionEffect() },
        WandererConfig("025", 2.0f, { ctx, view, sc -> StandardWanderer(ctx, view, "025", sc) }) { SmokeTransitionEffect(false) },
        WandererConfig("133", 2.0f, { ctx, view, sc -> StandardWanderer(ctx, view, "133", sc) }) { SmokeTransitionEffect(false) },

        // --- RARE ─────────────────────────────────────────────────────
        WandererConfig("001", 1.8f, { ctx, view, sc -> StandardWanderer(ctx, view, "001", sc) }) { SmokeTransitionEffect(false) },
        WandererConfig("004", 1.8f, { ctx, view, sc -> StandardWanderer(ctx, view, "004", sc) }) { SmokeTransitionEffect(false) },
        WandererConfig("007", 1.8f, { ctx, view, sc -> StandardWanderer(ctx, view, "007", sc) }) { SmokeTransitionEffect(false) },
        WandererConfig("092", 2.2f, { ctx, view, sc -> StandardWanderer(ctx, view, "092", sc, SmokeTransitionEffect(true)) }) { SmokeTransitionEffect(true) },

        // --- EPIC ─────────────────────────────────────────────────────
        WandererConfig("093", 1.6f, { ctx, view, sc -> StandardWanderer(ctx, view, "093", sc, SmokeTransitionEffect(true)) }) { SmokeTransitionEffect(true) },
        WandererConfig("094", 1.5f, { ctx, view, sc -> StandardWanderer(ctx, view, "094", sc, SmokeTransitionEffect(true)) }) { SmokeTransitionEffect(true) },
        WandererConfig("143", 2.2f, { ctx, view, sc -> StandardWanderer(ctx, view, "143", sc) }) { SmokeTransitionEffect(false) },

        // --- LEGENDARY ────────────────────────────────────────────────
        WandererConfig("006", 2.0f, { ctx, view, sc -> StandardWanderer(ctx, view, "006", sc) }) { SmokeTransitionEffect(false) },
        WandererConfig("150", 1.8f, { ctx, view, sc -> StandardWanderer(ctx, view, "150", sc, SmokeTransitionEffect(true)) }) { SmokeTransitionEffect(true) },

        // --- MYTHIC ───────────────────────────────────────────────────
        WandererConfig("151", 1.8f, { ctx, view, sc -> StandardWanderer(ctx, view, "151", sc, SmokeTransitionEffect(true)) }) { SmokeTransitionEffect(true) }
    )

    private val configMap by lazy { CONFIGS.associateBy { it.pokemonId } }

    fun create(context: Context, pokemonView: ImageView, pokemonId: String): PokemonBehavior {
        val cfg = configMap[pokemonId] ?: defaultConfig()
        return cfg.behaviorFactory(context, pokemonView, cfg.baseScale)
    }

    private fun defaultConfig() = WandererConfig(
        pokemonId     = "",
        baseScale     = 1.8f,
        behaviorFactory = { ctx, view, sc -> StandardWanderer(ctx, view, "", sc) },
        effectFactory = { SmokeTransitionEffect(false) }
    )
}