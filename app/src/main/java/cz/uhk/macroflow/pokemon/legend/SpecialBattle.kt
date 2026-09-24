package cz.uhk.macroflow.pokemon.legend

import cz.uhk.macroflow.pokemon.cave.CrystalColor

/**
 * Zvláštní souboje příběhu krystalů (čistý Kotlin, pokryto testy). Podklady v docs/adr/0014.
 *
 * * Strážci jeskyní (lvl 12) hlídají krystal – dokud nepadnou, krystal nejde vzít.
 * * Legenda na vrcholu Hor (lvl 80) se probudí po vložení obou krystalů. Nedá se porazit
 *   (HP neklesne pod 1), hráče porazí a uletí – tím se otevře brána dál.
 *
 * Ani jeden nejde chytit a z žádného se nedá utéct.
 */
enum class SpecialBattle(
    val id: String,
    /** Číslo v Makrodexu (sprite makromon_NN_jmeno). */
    val makromonId: String,
    val level: Int,
    val kind: Kind,
    /** Krystal, který strážce hlídá (u legendy null). */
    val crystal: CrystalColor?,
    /** Drawable spritu (strážce stojí na mapě před oltářem). */
    val spriteName: String
) {
    BOSS_BLUE("boss_blue", "021", 12, Kind.BOSS, CrystalColor.BLUE, "makromon_21_serpfin"),     // had z podzemního jezírka
    BOSS_RED("boss_red", "003", 12, Kind.BOSS, CrystalColor.RED, "makromon_03_ignaroth"),       // oheň v hlubinách dolu
    LEGEND_PEAK("legend_peak", "019", 80, Kind.LEGEND, null, "makromon_19_drakirra");           // drak z vrcholu

    enum class Kind { BOSS, LEGEND }

    val canCatch: Boolean get() = false
    val canRun: Boolean get() = false

    /** Úvodní hláška místo „WILD X APPEARED!“ (max. 23 znaků na řádek). */
    fun appearLines(name: String): Pair<String, String> = when (kind) {
        Kind.BOSS -> "GUARDIAN $name" to "BLOCKS THE WAY!"
        Kind.LEGEND -> "LEGENDARY $name" to "HAS AWAKENED!"
    }

    /** Legendu nelze porazit: HP po zásahu neklesne pod 1. */
    fun clampEnemyHp(hp: Int): Int = if (kind == Kind.LEGEND) maxOf(1, hp) else hp

    /** Hlášky, když hráč zkusí hodit ball nebo utéct. */
    val noCatchLines: Pair<String, String>
        get() = if (kind == Kind.LEGEND) "IT DEFLECTED" to "THE BALL!" else "THE GUARDIAN" to "SWATTED THE BALL!"
    val noRunLines: Pair<String, String>
        get() = "THERE IS" to "NO ESCAPE!"

    /** Konec souboje s legendou (hráčův Makromon padl): legenda odletí. */
    fun fleeLines(name: String): List<Pair<String, String>> =
        listOf("$name LET OUT" to "A MIGHTY ROAR!", "$name FLEW AWAY" to "OVER THE PEAKS!")

    companion object {
        /** Klíč v GamePrefs: souboj, který se má spustit místo divokého setkání. */
        const val PREF = "SPECIAL_BATTLE"

        fun from(id: String?): SpecialBattle? = entries.firstOrNull { it.id == id }

        fun guardianOf(crystal: CrystalColor): SpecialBattle = entries.first { it.crystal == crystal }
    }
}
