package cz.uhk.macroflow.pokemon

import android.content.Context
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.data.GameEventType
import cz.uhk.macroflow.pokemon.stamina.StaminaStore

/**
 * Tréninkový bonus ve všech soubojích (docs/adr/0082):
 * - trénink nebo kardio za posledních 24 h → tým má +10 % útoku a divoké se chytají o 15 % snáz,
 * - splněná makra dnes → „makro štít“ zablokuje první stavový efekt na tvůj tým v souboji.
 */
class TrainingBonus(val trained: Boolean, var shield: Boolean) {

    val active get() = trained || shield
    val attackMultiplier get() = if (trained) ATTACK else 1f
    val catchMultiplier get() = if (trained) CATCH else 1f

    companion object {
        const val ATTACK = 1.10f
        const val CATCH = 1.15f
        private const val DAY_MS = 24 * 60 * 60 * 1000L

        val NONE get() = TrainingBonus(trained = false, shield = false)

        /** Čte databázi – volat mimo hlavní vlákno. */
        fun current(ctx: Context, now: Long = System.currentTimeMillis()): TrainingBonus {
            val ev = AppDatabase.getDatabase(ctx).gameEventDao()
            val trained = listOf(GameEventType.WORKOUT_DONE, GameEventType.CARDIO_DONE).any { ev.countSince(it.name, now - DAY_MS) > 0 }
            val macros = runCatching { StaminaStore.dayFacts(ctx).macrosHit }.getOrDefault(false)
            return TrainingBonus(trained, macros)
        }
    }
}
