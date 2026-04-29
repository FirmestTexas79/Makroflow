package cz.uhk.macroflow.pokemon

import android.content.Context
import android.widget.ImageView
import cz.uhk.macroflow.R

private data class WandererConfig(
    val makromonId: String,
    val baseScale: Float,
    val behaviorFactory: (Context, ImageView, Float) -> PokemonBehavior,
    val effectFactory: () -> TransitionEffect
)

object WandererFactory {

    private val CONFIGS = listOf(

        // ── IGNAR RODINA (001-003) ────────────────────────────────────
        WandererConfig("001", 1.8f, { ctx, view, sc -> StandardWanderer(ctx, view, "001", sc) }) { SmokeTransitionEffect(false) },
        WandererConfig("002", 2.0f, { ctx, view, sc -> StandardWanderer(ctx, view, "002", sc) }) { SmokeTransitionEffect(false) },
        WandererConfig("003", 2.2f, { ctx, view, sc -> StandardWanderer(ctx, view, "003", sc) }) { SmokeTransitionEffect(false) },

        // ── AQULIN RODINA (004-006) ───────────────────────────────────
        WandererConfig("004", 1.8f, { ctx, view, sc -> StandardWanderer(ctx, view, "004", sc) }) { SmokeTransitionEffect(false) },
        WandererConfig("005", 2.0f, { ctx, view, sc -> StandardWanderer(ctx, view, "005", sc) }) { SmokeTransitionEffect(false) },
        WandererConfig("006", 2.2f, { ctx, view, sc -> StandardWanderer(ctx, view, "006", sc) }) { SmokeTransitionEffect(false) },

        // ── FLORI RODINA (007-009) ────────────────────────────────────
        WandererConfig("007", 1.8f, { ctx, view, sc -> StandardWanderer(ctx, view, "007", sc) }) { SmokeTransitionEffect(false) },
        WandererConfig("008", 2.0f, { ctx, view, sc -> StandardWanderer(ctx, view, "008", sc) }) { SmokeTransitionEffect(false) },
        WandererConfig("009", 2.2f, { ctx, view, sc -> StandardWanderer(ctx, view, "009", sc) }) { SmokeTransitionEffect(false) },

        // ── SPECIÁLNÍ (010-011) ───────────────────────────────────────
        // Umbex a Lumex – duchové, fialový smoke efekt
        WandererConfig("010", 2.0f, { ctx, view, sc -> StandardWanderer(ctx, view, "010", sc, SmokeTransitionEffect(true)) }) { SmokeTransitionEffect(true) },
        WandererConfig("011", 2.0f, { ctx, view, sc -> StandardWanderer(ctx, view, "011", sc, SmokeTransitionEffect(true)) }) { SmokeTransitionEffect(true) },

        // ── SPIRRA RODINA (012-019) ───────────────────────────────────
        WandererConfig("012", 2.0f, { ctx, view, sc -> StandardWanderer(ctx, view, "012", sc) }) { SmokeTransitionEffect(false) },
        WandererConfig("013", 2.0f, { ctx, view, sc -> StandardWanderer(ctx, view, "013", sc) }) { SmokeTransitionEffect(false) },
        WandererConfig("014", 2.0f, { ctx, view, sc -> StandardWanderer(ctx, view, "014", sc) }) { SmokeTransitionEffect(false) },
        WandererConfig("015", 2.0f, { ctx, view, sc -> StandardWanderer(ctx, view, "015", sc) }) { SmokeTransitionEffect(false) },
        // Shadirra – temná, fialový smoke
        WandererConfig("016", 2.0f, { ctx, view, sc -> StandardWanderer(ctx, view, "016", sc, SmokeTransitionEffect(true)) }) { SmokeTransitionEffect(true) },
        WandererConfig("017", 2.0f, { ctx, view, sc -> StandardWanderer(ctx, view, "017", sc) }) { SmokeTransitionEffect(false) },
        WandererConfig("018", 2.0f, { ctx, view, sc -> StandardWanderer(ctx, view, "018", sc) }) { SmokeTransitionEffect(false) },
        // Drakirra – legendární, fialový smoke
        WandererConfig("019", 2.2f, { ctx, view, sc -> StandardWanderer(ctx, view, "019", sc, SmokeTransitionEffect(true)) }) { SmokeTransitionEffect(true) },

        // ── FINLET / SERPFIN (020-021) ────────────────────────────────
        WandererConfig("020", 1.8f, { ctx, view, sc -> StandardWanderer(ctx, view, "020", sc) }) { SmokeTransitionEffect(false) },
        WandererConfig("021", 2.4f, { ctx, view, sc -> StandardWanderer(ctx, view, "021", sc) }) { SmokeTransitionEffect(false) },

        // ── MYCIT / MYDRUS (022-023) ──────────────────────────────────
        WandererConfig("022", 1.8f, { ctx, view, sc -> StandardWanderer(ctx, view, "022", sc) }) { SmokeTransitionEffect(false) },
        WandererConfig("023", 2.1f, { ctx, view, sc -> StandardWanderer(ctx, view, "023", sc) }) { SmokeTransitionEffect(false) },

        // ── SOULU RODINA (024-026) ────────────────────────────────────
        WandererConfig("024", 1.8f, { ctx, view, sc -> StandardWanderer(ctx, view, "024", sc, SmokeTransitionEffect(true)) }) { SmokeTransitionEffect(true) },
        WandererConfig("025", 2.0f, { ctx, view, sc -> StandardWanderer(ctx, view, "025", sc, SmokeTransitionEffect(true)) }) { SmokeTransitionEffect(true) },
        WandererConfig("026", 2.2f, { ctx, view, sc -> StandardWanderer(ctx, view, "026", sc, SmokeTransitionEffect(true)) }) { SmokeTransitionEffect(true) },

        // ── PHANTIL RODINA (027-029) ──────────────────────────────────
        WandererConfig("027", 1.8f, { ctx, view, sc -> StandardWanderer(ctx, view, "027", sc, SmokeTransitionEffect(true)) }) { SmokeTransitionEffect(true) },
        WandererConfig("028", 2.0f, { ctx, view, sc -> StandardWanderer(ctx, view, "028", sc, SmokeTransitionEffect(true)) }) { SmokeTransitionEffect(true) },
        WandererConfig("029", 2.3f, { ctx, view, sc -> StandardWanderer(ctx, view, "029", sc, SmokeTransitionEffect(true)) }) { SmokeTransitionEffect(true) },

        // ── GUDWIN (030) ──────────────────────────────────────────────
        // Těžký moudrý medvěd – HeavyTransitionEffect jako Kangaskhan
        WandererConfig("030", 2.2f, { ctx, view, sc -> StandardWanderer(ctx, view, "030", sc, HeavyTransitionEffect()) }) { HeavyTransitionEffect() },

        // ── AXLU (031) ────────────────────────────────────────────────
        // Tvář aplikace – mythic, fialový smoke
        WandererConfig("031", 2.0f, { ctx, view, sc -> StandardWanderer(ctx, view, "031", sc, SmokeTransitionEffect(true)) }) { SmokeTransitionEffect(true) }
    )

    private val configMap by lazy { CONFIGS.associateBy { it.makromonId } }

    fun create(context: Context, makromonView: ImageView, makromonId: String): PokemonBehavior {
        val cfg = configMap[makromonId] ?: defaultConfig(makromonId)

        // --- KLÍČOVÁ OPRAVA: Nastavení obrázku před spuštěním behavioru ---
        setupMakromonSprite(context, makromonView, makromonId)

        return cfg.behaviorFactory(context, makromonView, cfg.baseScale)
    }

    private fun setupMakromonSprite(context: Context, view: ImageView, id: String) {
        // 1. Najdeme jméno v SpawnManageru (nebo v tvé definici), abychom sestavili název
        val entry = SpawnManager.allEntries.find { it.id == id }
        val namePart = entry?.name?.lowercase()?.trim()?.replace(" ", "_") ?: ""

        // 2. Zkrácené ID (012 -> 12)
        val shortId = if (id.length >= 3) id.takeLast(2) else id

        // 3. Sestavení názvu: makromon_12_spirra
        val drawableName = "makromon_${shortId}_$namePart"

        val resId = context.resources.getIdentifier(drawableName, "drawable", context.packageName)

        if (resId != 0) {
            view.setImageResource(resId)
        } else {
            view.setImageResource(R.drawable.ic_home)
        }
    }

    private fun defaultConfig(id: String) = WandererConfig(
        makromonId      = id,
        baseScale       = 2.0f,
        behaviorFactory = { ctx, view, sc -> StandardWanderer(ctx, view, id, sc) },
        effectFactory   = { SmokeTransitionEffect(false) }
    )
}