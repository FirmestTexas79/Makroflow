package cz.uhk.macroflow.pokemon.evolution

import cz.uhk.macroflow.pokemon.MakromonType
import cz.uhk.macroflow.pokemon.Move
import cz.uhk.macroflow.pokemon.status.EffectKind

/**
 * Učení útoku po evoluci (docs/adr/0064). Hráč se vždy rozhoduje sám: naučit, nebo ne,
 * a když už má čtyři útoky, který z nich zapomene. Nic se neděje samo. Čistý Kotlin, testy v MoveLearningTest.
 */
object MoveLearning {

    const val MAX_MOVES = 4

    enum class Situation {
        /** Útok už umí – není co řešit. */
        ALREADY_KNOWN,
        /** Má volné místo – po potvrzení se útok přidá. */
        FREE_SLOT,
        /** Má plno – musí vybrat, který útok zapomene. */
        MUST_REPLACE
    }

    fun situation(current: List<String>, new: String): Situation = when {
        current.any { it.equals(new, ignoreCase = true) } -> Situation.ALREADY_KNOWN
        current.size < MAX_MOVES -> Situation.FREE_SLOT
        else -> Situation.MUST_REPLACE
    }

    /** Přidá útok na volné místo (při plné sadě nic nezmění). */
    fun learn(current: List<String>, new: String): List<String> =
        if (situation(current, new) == Situation.FREE_SLOT) current + new else current

    /** Nahradí útok na [index] novým; pořadí ostatních zůstane. */
    fun replace(current: List<String>, index: Int, new: String): List<String> {
        require(index in current.indices) { "index $index mimo ${current.size} útoků" }
        if (situation(current, new) == Situation.ALREADY_KNOWN) return current
        return current.toMutableList().also { it[index] = new }
    }

    /** Typ útoku česky (štítek na kartě útoku). */
    fun typeLabel(t: MakromonType): String = when (t) {
        MakromonType.NORMAL -> "NORMÁLNÍ"; MakromonType.FIRE -> "OHEŇ"; MakromonType.WATER -> "VODA"
        MakromonType.GRASS -> "TRÁVA"; MakromonType.ELECTRIC -> "ELEKTRO"; MakromonType.BUG -> "HMYZ"
        MakromonType.FLYING -> "LÉTAVÝ"; MakromonType.GHOST -> "DUCH"; MakromonType.GROUND -> "ZEMĚ"
        MakromonType.PSYCHIC -> "PSYCHO"; MakromonType.DRAGON -> "DRAK"; MakromonType.POISON -> "JED"
        MakromonType.FAIRY -> "VÍLA"
    }

    /** Co útok udělá navíc (prázdné = jen poškození). */
    fun effectText(m: Move): String {
        val e = m.fullEffect ?: return if (m.power == 0) "Bez poškození." else ""
        val chance = if (e.chance in 1..99) " (${e.chance} %)" else ""
        val stages = if (e.stages > 1) " o ${e.stages} stupně" else ""
        return when (e.kind) {
            EffectKind.SLEEP -> "Uspí soupeře$chance."
            EffectKind.PARALYZE -> "Ochromí soupeře$chance."
            EffectKind.POISON -> "Otráví soupeře$chance."
            EffectKind.BURN -> "Popálí soupeře$chance."
            EffectKind.CONFUSE -> "Zmate soupeře$chance."
            EffectKind.FLINCH -> "Soupeř se může zaleknout$chance."
            EffectKind.LOWER_ATK -> "Sníží soupeři útok$stages$chance."
            EffectKind.LOWER_DEF -> "Sníží soupeři obranu$stages$chance."
            EffectKind.RAISE_ATK -> "Zvýší si útok$stages."
            EffectKind.RAISE_DEF -> "Zvýší si obranu$stages."
        }
    }
}
