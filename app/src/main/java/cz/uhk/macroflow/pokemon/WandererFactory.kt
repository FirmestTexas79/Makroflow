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

        WandererConfig("001", 1.8f, { ctx, view, sc -> StandardWanderer(ctx, view, "001", sc) }) { SmokeTransitionEffect(false) },
        WandererConfig("002", 1.9f, { ctx, view, sc -> StandardWanderer(ctx, view, "002", sc) }) { SmokeTransitionEffect(false) },
        WandererConfig("003", 2.1f, { ctx, view, sc -> StandardWanderer(ctx, view, "003", sc) }) { SmokeTransitionEffect(false) },
        WandererConfig("004", 1.8f, { ctx, view, sc -> StandardWanderer(ctx, view, "004", sc) }) { SmokeTransitionEffect(false) },
        WandererConfig("005", 2.0f, { ctx, view, sc -> StandardWanderer(ctx, view, "005", sc) }) { SmokeTransitionEffect(false) },
        WandererConfig("006", 2.0f, { ctx, view, sc -> StandardWanderer(ctx, view, "006", sc) }) { SmokeTransitionEffect(false) },
        WandererConfig("007", 1.8f, { ctx, view, sc -> StandardWanderer(ctx, view, "007", sc) }) { SmokeTransitionEffect(false) },
        WandererConfig("008", 2.0f, { ctx, view, sc -> StandardWanderer(ctx, view, "008", sc) }) { SmokeTransitionEffect(false) },
        WandererConfig("009", 2.2f, { ctx, view, sc -> StandardWanderer(ctx, view, "009", sc) }) { SmokeTransitionEffect(false) },
        WandererConfig("010", 2.0f, { ctx, view, sc -> StandardWanderer(ctx, view, "010", sc) }) { SmokeTransitionEffect(false) },
        WandererConfig("011", 1.8f, { ctx, view, sc -> StandardWanderer(ctx, view, "011", sc) }) { SmokeTransitionEffect(false) },
        WandererConfig("012", 2.2f, { ctx, view, sc -> StandardWanderer(ctx, view, "012", sc) }) { SmokeTransitionEffect(false) },
        WandererConfig("013", 1.8f, { ctx, view, sc -> StandardWanderer(ctx, view, "013", sc) }) { SmokeTransitionEffect(false) },
        WandererConfig("014", 1.7f, { ctx, view, sc -> StandardWanderer(ctx, view, "014", sc) }) { SmokeTransitionEffect(false) },
        WandererConfig("015", 2.2f, { ctx, view, sc -> StandardWanderer(ctx, view, "015", sc) }) { SmokeTransitionEffect(false) },
        WandererConfig("016", 2.0f, { ctx, view, sc -> StandardWanderer(ctx, view, "016", sc) }) { SmokeTransitionEffect(false) },
        WandererConfig("017", 2.1f, { ctx, view, sc -> StandardWanderer(ctx, view, "017", sc) }) { SmokeTransitionEffect(false) },
        WandererConfig("018", 2.3f, { ctx, view, sc -> StandardWanderer(ctx, view, "018", sc) }) { SmokeTransitionEffect(false) },
        WandererConfig("019", 1.9f, { ctx, view, sc -> StandardWanderer(ctx, view, "019", sc) }) { SmokeTransitionEffect(false) },
        WandererConfig("020", 2.1f, { ctx, view, sc -> StandardWanderer(ctx, view, "020", sc) }) { SmokeTransitionEffect(false) },
        WandererConfig("021", 2.0f, { ctx, view, sc -> StandardWanderer(ctx, view, "021", sc) }) { SmokeTransitionEffect(false) },
        WandererConfig("022", 2.2f, { ctx, view, sc -> StandardWanderer(ctx, view, "022", sc) }) { SmokeTransitionEffect(false) },
        WandererConfig("023", 1.9f, { ctx, view, sc -> StandardWanderer(ctx, view, "023", sc) }) { SmokeTransitionEffect(false) },
        WandererConfig("024", 2.1f, { ctx, view, sc -> StandardWanderer(ctx, view, "024", sc) }) { SmokeTransitionEffect(false) },


        // --- 🐛 CATERPIE EVOLUTION LINE (NEW) ──────────────────────────

        // --- COMMON ───────────────────────────────────────────────────
        WandererConfig("050", 3.0f, { ctx, view, sc -> StandardWanderer(ctx, view, "050", sc, DigTransitionEffect()) }) { DigTransitionEffect() },
        WandererConfig("025", 2.0f, { ctx, view, sc -> StandardWanderer(ctx, view, "025", sc) }) { SmokeTransitionEffect(false) },
        WandererConfig("133", 2.0f, { ctx, view, sc -> StandardWanderer(ctx, view, "133", sc) }) { SmokeTransitionEffect(false) },

        // --- RARE ─────────────────────────────────────────────────────

        WandererConfig("132", 2.0f, { ctx, view, sc -> StandardWanderer(ctx, view, "132", sc) }) { SmokeTransitionEffect(false) },


        WandererConfig("092", 2.2f, { ctx, view, sc -> StandardWanderer(ctx, view, "092", sc, SmokeTransitionEffect(true)) }) { SmokeTransitionEffect(true) },
        WandererConfig("051", 3.2f, { ctx, view, sc -> StandardWanderer(ctx, view, "051", sc, DigTransitionEffect()) }) { DigTransitionEffect() },

        // --- EPIC ─────────────────────────────────────────────────────
        WandererConfig("137", 2.0f, { ctx, view, sc -> StandardWanderer(ctx, view, "137", sc, StandardWanderer.PixelTransitionEffect()) }) { StandardWanderer.PixelTransitionEffect() },
        WandererConfig("093", 1.6f, { ctx, view, sc -> StandardWanderer(ctx, view, "093", sc, SmokeTransitionEffect(true)) }) { SmokeTransitionEffect(true) },
        WandererConfig("094", 1.5f, { ctx, view, sc -> StandardWanderer(ctx, view, "094", sc, SmokeTransitionEffect(true)) }) { SmokeTransitionEffect(true) },
        WandererConfig("143", 2.2f, { ctx, view, sc -> StandardWanderer(ctx, view, "143", sc) }) { SmokeTransitionEffect(false) },
        WandererConfig("115", 2.2f, { ctx, view, sc -> StandardWanderer(ctx, view, "115", sc, HeavyTransitionEffect()) }) { HeavyTransitionEffect() }, // 🦖 Tady je tvůj Kangaskhan!
        WandererConfig("026", 2.0f, { ctx, view, sc -> StandardWanderer(ctx, view, "026", sc) }) { SmokeTransitionEffect(false) },
        WandererConfig("131", 2.3f, { ctx, view, sc -> StandardWanderer(ctx, view, "131", sc) }) { SmokeTransitionEffect(false) },

        // --- LEGENDARY ────────────────────────────────────────────────
        WandererConfig("150", 1.8f, { ctx, view, sc -> StandardWanderer(ctx, view, "150", sc, SmokeTransitionEffect(true)) }) { SmokeTransitionEffect(true) },

        // --- MYTHIC ───────────────────────────────────────────────────
        WandererConfig("151", 1.8f, { ctx, view, sc -> StandardWanderer(ctx, view, "151", sc, SmokeTransitionEffect(true)) }) { SmokeTransitionEffect(true) }
    )

    private val configMap by lazy { CONFIGS.associateBy { it.pokemonId } }

    fun create(context: Context, pokemonView: ImageView, pokemonId: String): PokemonBehavior {
        // ✅ PŘEDÁVÁME ID DO DEFAULTU, POKUD NENÍ V MAPĚ
        val cfg = configMap[pokemonId] ?: defaultConfig(pokemonId)
        return cfg.behaviorFactory(context, pokemonView, cfg.baseScale)
    }

    // ✅ OPRAVA: Default musí brát ID, aby StandardWanderer věděl, o koho jde
    private fun defaultConfig(id: String) = WandererConfig(
        pokemonId     = id,
        baseScale     = 2.0f,
        behaviorFactory = { ctx, view, sc -> StandardWanderer(ctx, view, id, sc) },
        effectFactory = { SmokeTransitionEffect(false) }
    )
}