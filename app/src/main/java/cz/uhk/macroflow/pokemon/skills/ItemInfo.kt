package cz.uhk.macroflow.pokemon.skills

import cz.uhk.macroflow.pokemon.balls.Makroball
import java.util.Locale

/**
 * Odkud se berou suroviny a Makrobally – pro dřevěnou ceduli v deníku (docs/adr/0035).
 * Počítá se z těch samých konstant jako hra (Drops, Berry, GatherSpot), takže čísla sedí.
 */
object ItemInfo {

    /** Jeden zdroj: kde / z čeho a s jakou šancí nebo za kolik. */
    data class Source(val where: String, val rate: String)

    private fun pct(p: Double): String {
        val v = p * 100
        return if (v == Math.floor(v)) "${v.toInt()} %" else String.format(Locale("cs"), "%.2f", v).trimEnd('0').trimEnd(',') + " %"
    }

    private const val WILD = "Louka, Hory, jeskyně a Hvozd"

    fun sources(itemId: String): List<Source> {
        Resource.from(itemId)?.let { r ->
            return when {
                r == Resource.ENERGY -> listOf(
                    Source("Poražený divoký Makromon ($WILD)",
                        "${pct(Drops.fragmentChance(0, false))} + 1 % za level (max ${pct(Drops.fragmentChance(99, false))}); od Lv 8 občas 2 ks"),
                    Source("Chycený Makromon", pct(Drops.fragmentChance(0, true))),
                    Source("Normální Makromoni (Spirra, Mycit, Axlu, Gudwin…)", "o 20 % vyšší šance (max ${pct(Drops.fragmentChance(99, false, DropFamily.NORMAL))})")
                )
                r.isMonsterMaterial -> {
                    val names = Drops.speciesFor(r).map { it.lowercase().replaceFirstChar { c -> c.uppercase() } }
                    if (Drops.UPGRADE.containsValue(r)) listOf(
                        Source("Evoluce: ${Drops.UPGRADE.filterValues { it == r }.keys.joinToString { it.lowercase().replaceFirstChar { c -> c.uppercase() } }}",
                            "po výhře ${pct(Drops.upgradeChance(false))}, po chycení ${pct(Drops.upgradeChance(true))}")
                    ) else listOf(
                        Source(names.joinToString(), "po výhře ${pct(Drops.materialChance(0, false))} + 1 % za level (max ${pct(Drops.materialChance(99, false))}), po chycení ${pct(Drops.materialChance(0, true))}; od Lv 10 občas 2 ks")
                    )
                }
                r.isSeed -> {
                    val b = r.berry!!
                    val tier = when (b) { Berry.BLACK -> 0.05; Berry.BLUE -> 0.20; Berry.GREEN -> 0.75 }
                    listOf(
                        Source("Listoví Makromoni (Flori, Verdirra…) – po výhře i chycení", pct(Drops.SEED_CHANCE * tier)),
                        Source("Obchod ve městě (záložka Semínka)", "${b.seedPrice} makro penízků")
                    )
                }
                r.berry != null -> {
                    val b = r.berry!!
                    listOf(
                        Source("Záhony na louce vpravo pod mostem – zasaď ${b.seedLabel.lowercase(Locale("cs"))}",
                            "roste ${growText(b.growSeconds)}, 1 ks (šance na 2 podle Pěstování)"),
                        Source("Použití", "pracovní stůl → ${b.ball.label}")
                    )
                }
                else -> GatherSpot.entries.filter { it.resource == r }.map { s ->
                    Source("${s.placeLabel} – ${s.label} (${if (s.skill == Skill.MINING) "krumpáč" else "sekera"})",
                        "efektivita ${s.required}+, 1 ks za ${growText(s.baseSeconds)} (rychleji s vyšší efektivitou)")
                }
            }
        }
        Makroball.from(itemId)?.let { ball ->
            val berry = Berry.forBall(ball)
            return listOf(
                Source("Pracovní stůl na louce", "1 fragment energie + 1× ${berry.label}"),
                Source("Obchod ve městě", "${ball.price} makro penízků za ${ball.packSize} ks")
            )
        }
        Gear.from(itemId)?.let { g ->
            if (g.legendary) return listOf(Source("Poražený Gudwin – legendární artefakt, padá jen jednou", pct(Drops.ARTIFACT_CHANCE)))
            val recipe = GearCrafting.recipe(g) ?: return listOf(Source("Startovní vybavení", "dostaneš na začátku"))
            val parts = recipe.entries.joinToString(" + ") { (id, n) -> "$n× ${Resource.from(id)?.label ?: id}" }
            return listOf(Source("Pracovní stůl na louce – Dobrodruhův set (uzel „Základní vybavení“ ve stromu Výroby)", parts))
        }
        return emptyList()
    }

    private fun growText(sec: Long): String = when {
        sec >= 3600 && sec % 3600 == 0L -> "${sec / 3600} h"
        sec >= 3600 -> "${sec / 3600} h ${(sec % 3600) / 60} min"
        else -> "${sec / 60} min"
    }
}
