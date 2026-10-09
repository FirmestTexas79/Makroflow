package cz.uhk.macroflow.pokemon.trainer

import cz.uhk.macroflow.pokemon.BattleEngine
import cz.uhk.macroflow.pokemon.Makromon
import cz.uhk.macroflow.pokemon.MakromonType
import cz.uhk.macroflow.pokemon.Move
import cz.uhk.macroflow.pokemon.status.Condition
import kotlin.random.Random

/**
 * Rozhodování trenéra v souboji (duch i AI trenér, docs/adr/0076). Divoký Makromon útočí náhodně,
 * trenér většinou zvolí útok s největším očekávaným zraněním (síla × účinnost typu × přesnost),
 * občas ale zahraje stavový útok nebo něco jiného, aby nebyl úplně předvídatelný.
 */
object TrainerAi {

    /** Jak často trenér nezvolí nejsilnější útok. */
    const val VARIETY = 0.25f

    fun expectedDamage(mv: Move, target: MakromonType): Float =
        mv.power * BattleEngine.getTypeEffectiveness(mv.type, target) * mv.accuracy / 100f

    fun chooseMove(
        self: Makromon,
        target: Makromon,
        targetCond: Condition? = null,
        selfCond: Condition? = null,
        rnd: Random = Random.Default
    ): Move {
        val best = self.moves.filter { it.pp > 0 && it.power > 0 }
            .map { it to expectedDamage(it, target.speciesType) }
            .filter { it.second > 0f }
            .maxByOrNull { it.second }?.first
        if (best == null || rnd.nextFloat() < VARIETY) return BattleEngine.enemyChooseMove(self, targetCond, selfCond)
        return best
    }
}
