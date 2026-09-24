package cz.uhk.macroflow.nutrition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BarcodeProductLookupTest {

    @Test
    fun `nalezený produkt s českým názvem`() {
        val json = """
            {"status":1,"product":{"product_name_cs":"Tvaroh","product_name":"Quark",
             "nutriments":{"proteins_100g":12.5,"carbohydrates_100g":3.5,"fat_100g":0.5,
                           "fiber_100g":0,"energy-kj_100g":285}}}
        """.trimIndent()
        val p = BarcodeProductLookup.parse(json, "859")!!
        assertEquals("Tvaroh", p.name)
        assertEquals(12.5f, p.proteins100g, 0.001f)
        assertEquals(285f, p.energyKj100g, 0.001f)
    }

    @Test
    fun `chybějící kJ se dopočítá z kcal`() {
        val json = """{"status":1,"product":{"product_name":"X","nutriments":{"energy-kcal_100g":100}}}"""
        assertEquals(418.4f, BarcodeProductLookup.parse(json, "1")!!.energyKj100g, 0.01f)
    }

    @Test
    fun `neznámý kód nevrací produkt – a tedy ani quest událost`() {
        assertNull(BarcodeProductLookup.parse("""{"status":0,"status_verbose":"product not found"}""", "0"))
    }

    @Test
    fun `produkt bez nutričních údajů se nepočítá`() {
        assertNull(BarcodeProductLookup.parse("""{"status":1,"product":{"product_name":"X"}}""", "0"))
    }
}
