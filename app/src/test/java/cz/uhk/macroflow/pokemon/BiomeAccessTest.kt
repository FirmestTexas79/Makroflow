package cz.uhk.macroflow.pokemon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BiomeAccessTest {

    @Test
    fun `hory jsou zamčené pod denním cílem`() {
        assertFalse(BiomeAccess.canEnter(BiomeType.MOUNTAINS, 0))
        assertFalse(BiomeAccess.canEnter(BiomeType.MOUNTAINS, BiomeAccess.MOUNTAINS_DAILY_STEPS - 1))
    }

    @Test
    fun `hory se otevřou přesně na denním cíli`() {
        assertTrue(BiomeAccess.canEnter(BiomeType.MOUNTAINS, BiomeAccess.MOUNTAINS_DAILY_STEPS))
    }

    @Test
    fun `chybějící kroky nejsou nikdy záporné`() {
        assertEquals(1500, BiomeAccess.missingSteps(BiomeType.MOUNTAINS, 3500))
        assertEquals(0, BiomeAccess.missingSteps(BiomeType.MOUNTAINS, 12_000))
    }

    @Test
    fun `biomy bez zámku jsou volně přístupné`() {
        assertTrue(BiomeAccess.canEnter(BiomeType.TOWN, 0))
        assertTrue(BiomeAccess.canEnter(BiomeType.MEADOW, 0))
    }
}
