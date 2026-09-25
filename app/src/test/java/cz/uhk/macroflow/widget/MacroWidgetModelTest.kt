package cz.uhk.macroflow.widget

import cz.uhk.macroflow.energy.Adherence
import cz.uhk.macroflow.widget.MacroWidgetModel.Macro
import cz.uhk.macroflow.widget.MacroWidgetModel.Mode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MacroWidgetModelTest {

    private val targets = Adherence.Targets(kcal = 2200.0, protein = 180.0, carbs = 250.0, fat = 60.0)
    private val eaten = Adherence.Eaten(kcal = 1240.0, protein = 90.0, carbs = 125.0, fat = 72.0)
    private val nb = "\u202F"

    @Test
    fun arcsLeaveBottomGapAndSeparators() {
        val s = MacroWidgetModel.build(eaten, targets, Mode.EATEN).segments
        assertEquals(listOf(Macro.CARBS, Macro.PROTEIN, Macro.FAT), s.map { it.macro })
        // levý začíná na kraji spodní mezery, pravý na ní končí
        assertEquals(90f + 42f, s[0].startDeg, 0.01f)
        assertEquals(360f + 90f - 42f, s[2].startDeg + s[2].sweepDeg, 0.01f)
        // mezi oblouky přesně oddělovač
        assertEquals(MacroWidgetModel.SEPARATOR_DEG, s[1].startDeg - (s[0].startDeg + s[0].sweepDeg), 0.01f)
        // bílkoviny nahoře: střed oblouku na 270°
        assertEquals(270f, s[1].midDeg, 0.01f)
    }

    @Test
    fun fillFractionsAndDirection() {
        val s = MacroWidgetModel.build(eaten, targets, Mode.EATEN).segments.associateBy { it.macro }
        assertEquals(0.5f, s.getValue(Macro.PROTEIN).fraction, 1e-4f)
        assertEquals(0.5f, s.getValue(Macro.CARBS).fraction, 1e-4f)
        assertEquals(1f, s.getValue(Macro.FAT).fraction, 1e-4f)          // přes cíl → plný
        assertTrue(s.getValue(Macro.FAT).over)
        assertFalse(s.getValue(Macro.PROTEIN).over)
        // levý oblouk se plní po směru ručiček od spodku, pravý proti směru od spodku
        assertTrue(s.getValue(Macro.CARBS).fillSweepDeg > 0)
        val fat = s.getValue(Macro.FAT)
        assertEquals(fat.startDeg + fat.sweepDeg, fat.fillStartDeg, 0.01f)
        assertEquals(-fat.sweepDeg, fat.fillSweepDeg, 0.01f)
    }

    @Test
    fun eatenModeTexts() {
        val st = MacroWidgetModel.build(eaten, targets, Mode.EATEN)
        assertEquals("1${nb}240", st.kcalText)
        assertEquals("z 2${nb}200 kcal", st.kcalCaption)
        assertEquals(listOf(125, 90, 72), st.segments.map { it.value })
    }

    @Test
    fun remainingModeTexts() {
        val st = MacroWidgetModel.build(eaten, targets, Mode.REMAINING)
        assertEquals("960", st.kcalText)
        assertEquals("kcal zbývá", st.kcalCaption)
        assertEquals(listOf(125, 90, 12), st.segments.map { it.value })   // tuky: 12 g navíc
        assertEquals("+12", MacroWidgetModel.segmentLabel(st.segments[2], Mode.REMAINING, true))
        assertEquals("90", MacroWidgetModel.segmentLabel(st.segments[1], Mode.REMAINING, true))

        val over = MacroWidgetModel.build(eaten.copy(kcal = 2350.0), targets, Mode.REMAINING)
        assertEquals("+150", over.kcalText)
        assertEquals("kcal navíc", over.kcalCaption)
    }

    @Test
    fun withoutTargetsShowsEatenOnly() {
        val st = MacroWidgetModel.build(eaten, null, Mode.REMAINING)
        assertFalse(st.hasTargets)
        assertEquals("1${nb}240", st.kcalText)
        assertEquals("kcal snědeno", st.kcalCaption)
        assertTrue(st.segments.all { it.fraction == 0f })
        assertEquals(listOf(125, 90, 72), st.segments.map { it.value })
    }

    @Test
    fun emptyDay() {
        val st = MacroWidgetModel.build(Adherence.Eaten(0.0, 0.0, 0.0, 0.0), targets, Mode.EATEN)
        assertEquals("0", st.kcalText)
        assertTrue(st.segments.all { it.fraction == 0f && it.fillSweepDeg == 0f || it.fillSweepDeg == -0f })
    }

    @Test
    fun thousandsFormatting() {
        assertEquals("0", MacroWidgetModel.thousands(0))
        assertEquals("999", MacroWidgetModel.thousands(999))
        assertEquals("12${nb}345", MacroWidgetModel.thousands(12345))
        assertEquals("-1${nb}000", MacroWidgetModel.thousands(-1000))
    }

    @Test
    fun descriptionForScreenReaders() {
        val d = MacroWidgetModel.build(eaten, targets, Mode.EATEN).description
        assertTrue(d, d.contains("1240 kcal snědeno z 2200"))
        assertTrue(d, d.contains("bílkoviny 90 z 180 g"))
    }
}
