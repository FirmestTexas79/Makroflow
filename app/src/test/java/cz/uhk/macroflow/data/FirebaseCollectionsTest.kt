package cz.uhk.macroflow.data

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Smazání účtu musí smazat každou kolekci, do které aplikace ukládá (docs/adr/0023).
 * FirebaseRepository nejde v unit testu vytvořit (inicializuje Firebase), proto se kontroluje zdroják:
 * každé userDoc().collection("…") musí být v USER_COLLECTIONS.
 */
class FirebaseCollectionsTest {

    private fun source(): String {
        val rel = "src/main/java/cz/uhk/macroflow/data/FirebaseRepository.kt"
        val f = listOf(File(rel), File("app/$rel")).first { it.exists() }
        return f.readText()
    }

    @Test
    fun deletionCoversEveryWrittenCollection() {
        val src = source()
        val used = Regex("""userDoc\(\)\s*\.collection\("([a-z_]+)"\)""").findAll(src).map { it.groupValues[1] }.toSet()
        val listBlock = Regex("""USER_COLLECTIONS\s*=\s*listOf\(([^)]*)\)""").find(src)!!.groupValues[1]
        val deleted = Regex(""""([a-z_]+)"""").findAll(listBlock).map { it.groupValues[1] }.toSet()
        assertTrue("chybí v USER_COLLECTIONS: ${used - deleted}", deleted.containsAll(used))
        assertTrue(used.isNotEmpty())
    }
}
