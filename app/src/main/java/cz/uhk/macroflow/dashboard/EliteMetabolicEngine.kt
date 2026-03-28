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
            "HIGH_PROTEIN" -> 0.20
            "KETO" -> 0.10
            "VEGAN" -> 0.12
            "LOW_CARB" -> 0.15
            else -> 0.10 // BALANCED
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
        return when {
            ratio > 10.4 -> 1.10 // Ektomorf: vyšší tolerance sacharidů
            ratio < 9.6 -> 0.90  // Endomorf: nižší tolerance
            else -> 1.0
        }
    }
}