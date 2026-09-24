package cz.uhk.macroflow.pokemon.status

import cz.uhk.macroflow.pokemon.MakromonType
import kotlin.random.Random

/**
 * Stavové efekty v souboji (čistý Kotlin, pokryto testy). Podklady v docs/adr/0012.
 * Pravidla vycházejí z Pokémonů (Gen 5+), zjednodušená pro souboj 1 na 1.
 */
enum class StatusKind(val tag: String, val label: String, val catchBonus: Float) {
    SLEEP("SLP", "SPI", 2.5f),
    PARALYSIS("PAR", "JE PARALYZOVAN", 1.5f),
    POISON("PSN", "JE OTRAVEN", 1.5f),
    BURN("BRN", "MA POPALENINY", 1.5f);
}

/** Co může útok způsobit a s jakou šancí (%). */
enum class EffectKind { SLEEP, PARALYZE, POISON, BURN, CONFUSE, FLINCH }

data class MoveEffect(val kind: EffectKind, val chance: Int = 100)

/** Stav jednoho Makromona během souboje. Hlavní stav je jen jeden; zmatení a omráčení jdou navíc. */
class Condition {
    var major: StatusKind? = null
    var sleepTurns = 0
    var confusedTurns = 0
    /** Omráčení platí jen pro nejbližší tah (útočník musel jednat dřív). */
    var flinched = false

    val confused: Boolean get() = confusedTurns > 0

    fun clearAll() { major = null; sleepTurns = 0; confusedTurns = 0; flinched = false }

    /** Štítek do rámečku HP: hlavní stav, jinak zmatení. */
    val tag: String? get() = major?.tag ?: if (confused) "CNF" else null
}

/** Výsledek kontroly před tahem. */
sealed class BeforeMove {
    object CanAct : BeforeMove()
    object Flinched : BeforeMove()
    object StillAsleep : BeforeMove()
    object WokeUp : BeforeMove()                 // probudil se a jedná
    object FullyParalyzed : BeforeMove()
    object SnappedOut : BeforeMove()             // zmatení skončilo, jedná
    object ConfusedButActs : BeforeMove()
    data class HurtItself(val damage: Int) : BeforeMove()

    val acts: Boolean get() = this is CanAct || this is WokeUp || this is SnappedOut || this is ConfusedButActs
}

enum class InflictResult { APPLIED, ALREADY, IMMUNE, FAILED_CHANCE }

object StatusRules {

    const val PARALYSIS_SKIP_PCT = 25
    const val CONFUSION_SELF_HIT_PCT = 33
    const val CONFUSION_SELF_POWER = 40

    /**
     * Pokus způsobit efekt [effect] cíli. Imunity podle typu:
     * ohnivý se nepopálí, jedovatý se neotráví, elektrický se neparalyzuje.
     */
    fun tryInflict(target: Condition, targetType: MakromonType, effect: MoveEffect, rng: Random): InflictResult {
        if (rng.nextInt(100) >= effect.chance) return InflictResult.FAILED_CHANCE
        return when (effect.kind) {
            EffectKind.FLINCH -> { target.flinched = true; InflictResult.APPLIED }
            EffectKind.CONFUSE -> {
                if (target.confused) InflictResult.ALREADY
                else { target.confusedTurns = 2 + rng.nextInt(4); InflictResult.APPLIED }   // 2–5 tahů
            }
            else -> {
                val kind = when (effect.kind) {
                    EffectKind.SLEEP -> StatusKind.SLEEP
                    EffectKind.PARALYZE -> StatusKind.PARALYSIS
                    EffectKind.POISON -> StatusKind.POISON
                    else -> StatusKind.BURN
                }
                when {
                    target.major != null -> InflictResult.ALREADY
                    isImmune(targetType, kind) -> InflictResult.IMMUNE
                    else -> {
                        target.major = kind
                        if (kind == StatusKind.SLEEP) target.sleepTurns = 1 + rng.nextInt(3)   // 1–3 tahy
                        InflictResult.APPLIED
                    }
                }
            }
        }
    }

    fun isImmune(type: MakromonType, kind: StatusKind): Boolean = when (kind) {
        StatusKind.BURN -> type == MakromonType.FIRE
        StatusKind.POISON -> type == MakromonType.POISON
        StatusKind.PARALYSIS -> type == MakromonType.ELECTRIC
        StatusKind.SLEEP -> false
    }

    /**
     * Kontrola před tahem v pořadí: omráčení → spánek → paralýza → zmatení.
     * [selfHitDamage] spočítá zranění, když se zmatený zasáhne sám (útok bez typu, síla 40).
     */
    fun beforeMove(c: Condition, rng: Random, selfHitDamage: () -> Int): BeforeMove {
        if (c.flinched) { c.flinched = false; return BeforeMove.Flinched }
        if (c.major == StatusKind.SLEEP) {
            c.sleepTurns--
            if (c.sleepTurns > 0) return BeforeMove.StillAsleep
            c.major = null; c.sleepTurns = 0
            return BeforeMove.WokeUp
        }
        if (c.major == StatusKind.PARALYSIS && rng.nextInt(100) < PARALYSIS_SKIP_PCT) return BeforeMove.FullyParalyzed
        if (c.confused) {
            c.confusedTurns--
            if (c.confusedTurns == 0) return BeforeMove.SnappedOut
            return if (rng.nextInt(100) < CONFUSION_SELF_HIT_PCT) BeforeMove.HurtItself(selfHitDamage())
                   else BeforeMove.ConfusedButActs
        }
        return BeforeMove.CanAct
    }

    /** Zranění na konci kola: otrava 1/8, popálení 1/16 max. HP (aspoň 1). */
    fun endOfTurnDamage(c: Condition, maxHp: Int): Int = when (c.major) {
        StatusKind.POISON -> (maxHp / 8).coerceAtLeast(1)
        StatusKind.BURN -> (maxHp / 16).coerceAtLeast(1)
        else -> 0
    }

    /** Popálení půlí útok, paralýza půlí rychlost. */
    fun attackMultiplier(c: Condition): Float = if (c.major == StatusKind.BURN) 0.5f else 1f
    fun speedMultiplier(c: Condition): Float = if (c.major == StatusKind.PARALYSIS) 0.5f else 1f

    /** Násobitel šance na chycení podle hlavního stavu soupeře. */
    fun catchBonus(c: Condition): Float = c.major?.catchBonus ?: 1f
}
