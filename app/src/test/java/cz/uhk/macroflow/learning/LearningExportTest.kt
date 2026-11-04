package cz.uhk.macroflow.learning

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LearningExportTest {
    private val day = LocalDate.of(2026, 11, 4)

    @Test fun exportContainsFindingsAndLog() {
        val r = Report(35, true, listOf(
            Learning(Area.ENERGY, "Tvůj výdej je o 6 % nižší než podle rovnice", "Z 12 vážení.", "2400 kcal", "2260 kcal", 3, true)))
        val t = LearningModel.exportText(r, listOf(Triple("2026-11-03", "Kalorie o 5 % níž", true)), day)
        assertTrue(t.contains("Výdej energie"))
        assertTrue(t.contains("2400 kcal → tvoje 2260 kcal"))
        assertTrue(t.contains("Jistota: 3/3"))
        assertTrue(t.contains("použito: Kalorie o 5 % níž"))
        assertTrue(t.contains("4. 11. 2026"))
    }

    @Test fun emptyReportSaysSo() {
        val t = LearningModel.exportText(Report(2, false, emptyList()), emptyList(), day)
        assertTrue(t.contains("málo dat"))
        assertFalse(t.contains("Úpravy z uzavřeného kruhu"))
    }
}
