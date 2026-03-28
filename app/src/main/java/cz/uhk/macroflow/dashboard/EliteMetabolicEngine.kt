package cz.uhk.macroflow.dashboard

import kotlin.math.*

object EliteMetabolicEngine {

    /**
     * KATCH-MCARDLE FORMULA
     * Na rozdíl od Mifflin-St Jeor neřeší věk ani pohlaví přímo, ale počítá s LBM.
     * LBM (Lean Body Mass) je metabolicky nejaktivnější část těla.
     */
    fun calculateEliteBMR(weight: Double, bodyFat: Double): Double {
        val lbm = weight * (1 - (bodyFat / 100.0))
        return 370 + (21.6 * lbm)
    }

    /**
     * DYNAMICKÝ TERMICKÝ EFEKT (DTEF)
     * Vrací koeficient energetické náročnosti trávení podle zvoleného protokolu.
     */
    fun getDietaryTEFModifier(dietType: String): Double {
        return when (dietType.uppercase()) {
            "HIGH_PROTEIN" -> 0.25 // Maximální termogeneze (proteiny pálí 25-30% sebe sama)
            "KETO" -> 0.15         // Vyšší nároky na oxidaci tuků a glukoneogenezi
            "VEGAN" -> 0.10        // Vysoká vláknina zvyšuje pasivní výdej
            "LOW_FAT" -> 0.12      // Standardní zpracování sacharidů
            "CARBO_LOADING" -> 0.08// Efektivní ukládání glykogenu s nízkým TEF
            else -> 0.12           // BALANCED
        }
    }

    /**
     * SOMATOTYP DLE ZÁPĚSTÍ (Bio-Typing)
     * Využívá poměr výšky a obvodu zápěstí k určení kosterní robustnosti.
     * Ovlivňuje citlivost na sacharidy (Inzulínová odezva).
     */
    fun getCarbSensitivity(wristCm: Double, heightCm: Double): Double {
        if (wristCm <= 0) return 1.0
        val ratio = heightCm / wristCm

        // Hodnoty pro muže (lékařské tabulky): > 10.4 Ektomorf, < 9.6 Endomorf
        return when {
            ratio > 10.4 -> 1.15 // Ektomorf: Rychlé spalování, vysoká tolerance sacharidů
            ratio < 9.6 -> 0.85  // Endomorf: Tendence k ukládání, nižší tolerance
            else -> 1.0          // Mezomorf: Zlatý střed
        }
    }
}