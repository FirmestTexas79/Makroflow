package cz.uhk.macroflow.dashboard

import kotlin.math.*

object EliteMetabolicEngine {

    /**
     * KATCH-MCARDLE FORMULA
     * Pro Elite mód je toto mnohem přesnější než Mifflin, protože 76kg
     * s 12% body fat má úplně jiný metabolismus než 76kg s 25% fat.
     */
    fun calculateEliteBMR(weight: Double, bodyFat: Double): Double {
        // LBM = váha bez tuku. To je motor, který pálí energii.
        val lbm = weight * (1 - (bodyFat / 100.0))
        return 370 + (21.6 * lbm)
    }

    /**
     * DYNAMICKÝ TERMICKÝ EFEKT (DTEF) - PŘEDIKCE
     * Sníženo, aby nedocházelo k přešvihnutí kalorií (Snowball),
     * protože reálný TEF se počítá v MacroFlowEngine.
     */
    fun getDietaryTEFModifier(dietType: String): Double {
        val type = dietType.uppercase()
        return when {
            // High protein vyžaduje víc energie na zpracování,
            // ale dáváme sem jen "příplatek" k bazálu.
            type.contains("HIGH") -> 0.12 // Sníženo z 0.20
            type.contains("LOW") -> 0.08
            type.contains("KETO") -> 0.05
            type.contains("VEGAN") -> 0.07
            else -> 0.05 // BALANCED
        }
    }

    /**
     * SOMATOTYP DLE ZÁPĚSTÍ (Bio-Typing)
     * Tento výpočet je super a vědecky podložený (poměr výška/zápěstí).
     * Necháme ho, jak je, funguje jako pojistka pro ektomorfy/endomorfy.
     */
    fun getCarbSensitivity(wristCm: Double, heightCm: Double): Double {
        if (wristCm <= 0) return 1.0
        val ratio = heightCm / wristCm

        // Pokud máš 175 cm a 15 cm zápěstí, tvůj ratio je 11.6 -> jsi ektomorf.
        // To znamená, že ti engine právem přidává 10% sacharidů, protože je lépe pálíš.
        return when {
            ratio > 10.4 -> 1.10 // Ektomorf (drobnější kostra, rychlý spalovač)
            ratio < 9.6 -> 0.90  // Endomorf (mohutná kostra, sklon k ukládání)
            else -> 1.0          // Mezomorf (atletický střed)
        }
    }
}