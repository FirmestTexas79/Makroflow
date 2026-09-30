package cz.uhk.macroflow.pokemon.walk

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt

/**
 * Animace postavy hráče ze Sunnyside World (docs/adr/0051). Čistý Kotlin, pokryto testy.
 *
 * Pásy snímků leží v assets/hero/<animace>_<směr>.png (tools/sprites/gen_hero.py). Pohyb
 * (idle, walk, run) má 8 směrů, práce (axe, mining, casting…) jen e / w.
 */
object HeroAnims {
    /** Směry po směru hodinových ručiček od východu (osa y míří dolů jako na obrazovce). */
    val DIRS8 = listOf("e", "se", "s", "sw", "w", "nw", "n", "ne")

    /** Směr z posunu (dx, dy). Skoro nulový posun = zůstává předchozí. */
    fun dir8(dx: Float, dy: Float, previous: String = "s"): String {
        if (abs(dx) < 0.5f && abs(dy) < 0.5f) return previous
        val a = atan2(dy.toDouble(), dx.toDouble())                       // −π..π, 0 = východ
        val k = ((a / (PI / 4)).roundToInt() + 8) % 8
        return DIRS8[k]
    }

    /** Starý čtyřsměrový kód MovementEngine (0 dolů, 1 nahoru, 2 vlevo, 3 vpravo) → směr. */
    fun fromFacing(dir4: Int): String = when (dir4) { 1 -> "n"; 2 -> "w"; 3 -> "e"; else -> "s" }

    /** Práce má jen dva směry – šikmé a svislé se přiklopí na bližší stranu. */
    fun side(dir: String): String = if (dir.contains("w")) "w" else "e"

    /** Animace práce u sběrného místa podle dovednosti (Skill.id). */
    fun actionFor(skillId: String): String = when (skillId) {
        "logging" -> "axe"
        "mining" -> "mining"
        "bugcatching" -> "casting"
        else -> "doing"
    }

    /** Index snímku po [elapsedMs] ms; [durations] = délky snímků. Smyčka se opakuje. */
    fun frameAt(durations: List<Int>, elapsedMs: Long): Int {
        if (durations.isEmpty()) return 0
        val total = durations.sum().coerceAtLeast(1)
        var t = (elapsedMs.coerceAtLeast(0) % total).toInt()
        durations.forEachIndexed { i, d -> if (t < d) return i; t -= d }
        return durations.lastIndex
    }

    /** Klíč pásu snímků; práce se přiklopí na stranu. */
    fun key(anim: String, dir: String): String = if (anim in MOVE) "${anim}_$dir" else "${anim}_${side(dir)}"

    val MOVE = setOf("idle", "walk", "run")

    /** Popis pásů z hero.json. */
    data class Spec(val frameW: Int, val frameH: Int, val footY: Int, val anims: Map<String, List<Int>>)

    /** Minimalistický parser hero.json (bez závislosti na Androidu). */
    fun parseSpec(json: String): Spec {
        fun int(name: String) = Regex("\"$name\"\\s*:\\s*(\\d+)").find(json)!!.groupValues[1].toInt()
        val anims = LinkedHashMap<String, List<Int>>()
        Regex("\"([a-z]+_[a-z]+)\"\\s*:\\s*\\{[^}]*?\"durations\"\\s*:\\s*\\[([^\\]]*)\\]").findAll(json).forEach { m ->
            anims[m.groupValues[1]] = m.groupValues[2].split(",").mapNotNull { it.trim().toIntOrNull() }
        }
        return Spec(int("frameW"), int("frameH"), int("footY"), anims)
    }
}
