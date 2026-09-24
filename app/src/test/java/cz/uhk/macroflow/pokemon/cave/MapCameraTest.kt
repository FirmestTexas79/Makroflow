package cz.uhk.macroflow.pokemon.cave

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapCameraTest {

    @Test
    fun scaleIsIntegerCloseToWantedAndCoversScreen() {
        val s = MapCamera.pixelScale(240, 400, 1080, 2340, 120)
        assertEquals(9, s)
        assertTrue(240 * s >= 1080 && 400 * s >= 2340)
        // Malá mapa se zvětší, aby pokryla obrazovku
        val small = MapCamera.pixelScale(100, 100, 1080, 2340, 120)
        assertTrue(100 * small >= 2340)
    }

    @Test
    fun cameraCentersPlayerInTheMiddleOfTheMap() {
        // svět 2000, obrazovka 1000, postava na 1200 → svět posunut o −700
        assertEquals(-700f, MapCamera.offset(1200f, 1000, 2000), 0f)
    }

    @Test
    fun cameraStopsAtMapEdges() {
        assertEquals(0f, MapCamera.offset(100f, 1000, 2000), 0f)          // u levého okraje
        assertEquals(-1000f, MapCamera.offset(1990f, 1000, 2000), 0f)     // u pravého okraje
    }

    @Test
    fun smallerWorldIsCentered() {
        assertEquals(100f, MapCamera.offset(50f, 1000, 800), 0f)
        assertEquals(0f, MapCamera.offset(500f, 1000, 1000), 0f)          // běžné mapy: žádný posun
    }

    @Test
    fun tapRadiusIsMeasuredInScreenUnits() {
        // Na mapě velikosti obrazovky se chová jako dřív
        assertEquals(0.05f, MapCamera.tapDistance(0.5f, 0.5f, 0.55f, 0.5f, 1000, 2000, 1000, 2000), 1e-6f)
        // Ve dvakrát širším světě je stejný relativní rozdíl dvakrát dál na obrazovce
        assertEquals(0.1f, MapCamera.tapDistance(0.5f, 0.5f, 0.55f, 0.5f, 2000, 2000, 1000, 2000), 1e-6f)
    }
}
