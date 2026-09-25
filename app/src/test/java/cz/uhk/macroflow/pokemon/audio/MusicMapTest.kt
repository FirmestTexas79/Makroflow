package cz.uhk.macroflow.pokemon.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MusicMapTest {

    @Test
    fun everyLocationHasItsMood() {
        assertEquals(MusicMap.TOWN, MusicMap.trackFor("TOWN"))
        assertEquals(MusicMap.MEADOW, MusicMap.trackFor("MEADOW"))
        assertEquals(MusicMap.MOUNTAINS, MusicMap.trackFor("MOUNTAINS"))
        assertEquals(MusicMap.CAVE, MusicMap.trackFor("CAVE_OPEN"))
        assertEquals(MusicMap.CAVE, MusicMap.trackFor("CAVE_MAZE"))
        // Hvozd sdílí pastorálu s loukou → přechod mezi nimi hudbu nepřeruší
        assertEquals(MusicMap.trackFor("MEADOW"), MusicMap.trackFor("FOREST"))
        assertNull(MusicMap.trackFor("NEZNAMY"))
    }

    @Test
    fun fadeIsLinearAndClamped() {
        assertEquals(0f, MusicMap.fade(0f, 1f, 0, 1000), 0f)
        assertEquals(0.5f, MusicMap.fade(0f, 1f, 500, 1000), 1e-6f)
        assertEquals(1f, MusicMap.fade(0f, 1f, 5000, 1000), 0f)
        assertEquals(MusicMap.BATTLE_DUCK, MusicMap.fade(1f, MusicMap.BATTLE_DUCK, 400, 400), 1e-6f)
        assertEquals(0.3f, MusicMap.fade(1f, 0.3f, 10, 0), 0f)
    }
}
