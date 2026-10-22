package cz.uhk.macroflow.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migrace 40 → 41 na datech ze staré verze (issue #4). Pouští se na telefonu přes `am instrument`
 * (ne `connectedAndroidTest`, ten po testu aplikaci odinstaluje i s daty). Pracuje s vlastní
 * testovací databází, hráčova data nechává být.
 *
 * Databázi v40 vyrobí z dnešní: tabulce captured_pokemon vezme sloupce uid a otName (jediné, co
 * migrace přidává). Room po migraci ověří celé schéma, takže chyba v migraci = pád testu.
 */
@RunWith(AndroidJUnit4::class)
class Migration40To41Test {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "migration-test.db"

    @After fun cleanup() { ctx.deleteDatabase(name) }

    @Test
    fun migraceZachovaUlovkyADoplniUid() {
        ctx.deleteDatabase(name)
        Room.databaseBuilder(ctx, AppDatabase::class.java, name).build().apply { openHelper.writableDatabase; close() }

        SQLiteDatabase.openDatabase(ctx.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            val create = db.rawQuery("SELECT sql FROM sqlite_master WHERE name = 'captured_pokemon'", null)
                .use { it.moveToFirst(); it.getString(0) }
            val v40 = create.replace(Regex(",\\s*`uid`[^,)]*"), "").replace(Regex(",\\s*`otName`[^,)]*"), "")
            assertTrue("sloupce se nepodařilo odebrat: $v40", "uid" !in v40 && "otName" !in v40)
            db.execSQL("DROP TABLE captured_pokemon")
            db.execSQL(v40)
            repeat(3) { i ->
                db.execSQL(
                    "INSERT INTO captured_pokemon (makromonId, name, isShiny, isLocked, caughtDate, moveListStr, level, xp) " +
                        "VALUES ('01$i', 'TEST$i', 0, ${i % 2}, ${1000 + i}, 'TACKLE', ${5 + i}, ${10 * i})"
                )
            }
            db.version = 40
        }

        val room = Room.databaseBuilder(ctx, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_40_41).build()
        val all = room.capturedMakromonDao().getAllCaught().sortedBy { it.caughtDate }
        room.close()

        assertEquals(3, all.size)
        all.forEachIndexed { i, m ->
            assertEquals("01$i", m.makromonId)
            assertEquals(5 + i, m.level)
            assertEquals(10 * i, m.xp)
            assertEquals(i % 2 == 1, m.isLocked)
            assertEquals("", m.otName)
            assertTrue("uid ve tvaru UUID: ${m.uid}", Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[0-9a-f]{4}-[0-9a-f]{12}").matches(m.uid))
        }
        assertEquals(3, all.map { it.uid }.toSet().size)
    }
}
