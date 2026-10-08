package cz.uhk.macroflow.nutrition

import cz.uhk.macroflow.nutrition.FoodCategory.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodCategoryTest {
    @Test fun commonFoods() {
        mapOf(
            "Kuřecí prsa (raw)" to MEAT, "Lovecký salám" to MEAT, "Tuňák v oleji" to FISH, "Krevety" to FISH,
            "Řecký jogurt (Skyr)" to DAIRY, "Vejce celé (60g)" to DAIRY, "Eidam 30%" to DAIRY,
            "Rýže bílá (syrová)" to GRAINS, "Houskové knedlíky" to GRAINS, "Makarony" to GRAINS, "Batáty" to GRAINS,
            "Žitný chléb" to BAKERY, "Tortilla celozrnná" to BAKERY,
            "Brokolice" to VEGETABLES, "Fazolky zelené" to VEGETABLES, "Žampiony" to VEGETABLES,
            "Banán" to FRUIT, "Rybíz červený" to FRUIT, "Avokádo" to FRUIT,
            "Červená čočka" to LEGUMES, "Hummus" to LEGUMES, "Tofu na pánvi" to LEGUMES,
            "Mandle" to NUTS, "Arašídové máslo" to NUTS, "Mák" to NUTS,
            "Olej (olivový/řepkový)" to FATS, "Kokosový olej" to FATS, "Máslo" to FATS,
            "Syrovátkový Izolát" to SUPPLEMENTS, "Proteinová tyčinka" to SUPPLEMENTS,
            "Pizza margherita" to MEALS, "Čokoláda hořká" to SWEETS, "Bramburky solené" to SWEETS,
            "Coca-Cola Zero" to DRINKS, "Sójový nápoj" to DRINKS
        ).forEach { (name, cat) -> assertEquals(name, cat, FoodCategory.of(name)) }
    }

    @Test fun unknownFallsBackByUnit() {
        assertEquals(DRINKS, FoodCategory.of("Xyz Fresh", "330 ml"))
        assertEquals(OTHER, FoodCategory.of("Xyz Fresh", "100 g"))
    }

    @Test fun seedPantryIsAlmostFullyCategorized() {
        val other = SnackSeed.DEFAULTS.filter { FoodCategory.of(it.name, it.weight) == OTHER }.map { it.name }
        assertTrue("Bez kategorie: $other", other.size <= 2)
    }
}
