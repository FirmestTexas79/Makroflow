package cz.uhk.macroflow.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import cz.uhk.macroflow.achievements.AchievementDao
import cz.uhk.macroflow.achievements.AchievementEntity
import cz.uhk.macroflow.pokemon.*
import kotlin.concurrent.thread

@Database(
    entities = [
        SnackEntity::class,
        CheckInEntity::class,
        ConsumedSnackEntity::class,
        UserProfileEntity::class,
        BodyMetricsEntity::class,
        WaterEntity::class,
        AchievementEntity::class,
        CoinEntity::class,
        CapturedMakromonEntity::class,
        UserItemEntity::class,
        MakrodexEntryEntity::class,
        MakrodexStatusEntity::class,
        SeenPokemonEntity::class,
        MakromonXpEntity::class,
        StepsEntity::class,
        AnalyticsCacheEntity::class,
        SnackUsageEntity::class
    ],
    version = 32,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun snackDao(): SnackDao
    abstract fun checkInDao(): CheckInDao
    abstract fun consumedSnackDao(): ConsumedSnackDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun bodyMetricsDao(): BodyMetricsDao
    abstract fun waterDao(): WaterDao
    abstract fun achievementDao(): AchievementDao
    abstract fun coinDao(): CoinDao
    abstract fun capturedMakromonDao(): CapturedMakromonDao
    abstract fun userItemDao(): UserItemDao
    abstract fun makrodexEntryDao(): MakrodexEntryDao
    abstract fun makrodexStatusDao(): MakrodexStatusDao
    abstract fun makromonXpDao(): MakromonXpDao
    abstract fun stepsDao(): StepsDao
    abstract fun analyticsDao(): AnalyticsDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "macroflow_database"
                )
                    .fallbackToDestructiveMigration()
                    .allowMainThreadQueries()
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            db.execSQL("INSERT INTO coins (id, balance) VALUES (1, 100)")
                            db.execSQL("INSERT INTO user_items (itemId, quantity) VALUES ('poke_ball', 5)")
                            db.execSQL("INSERT INTO user_items (itemId, quantity) VALUES ('great_ball', 3)")
                        }

                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            thread { fillMakrodexEntries(db) }
                        }
                    })
                    .build()
                    .also { INSTANCE = it }
            }
        }

        private fun fillMakrodexEntries(db: SupportSQLiteDatabase) {
            getMakromonList().forEach { entry ->
                val safeDesc = entry.desc.replace("'", "''")
                val safeHint = entry.hint.replace("'", "''")
                val safeName = entry.displayName.replace("'", "''")

                db.execSQL(
                    "INSERT OR REPLACE INTO pokedex_entries " +
                            "(makrodexId, drawableName, displayName, type, macroDesc, unlockedHint, evolveLevel, evolveToId) " +
                            "VALUES ('${entry.id}', '${entry.drawableName}', '$safeName', '${entry.type}', '$safeDesc', '$safeHint', ${entry.evolveLevel}, '${entry.evolveToId}')"
                )
            }
        }

        private data class MakromonEntry(
            val id: String,
            val drawableName: String,
            val displayName: String,
            val type: String,
            val desc: String,
            val hint: String,
            val evolveLevel: Int = 0,
            val evolveToId: String = ""
        )

        private fun getMakromonList(): List<MakromonEntry> = listOf(

            // ── IGNAR RODINA (001-003) ────────────────────────────────
            MakromonEntry(
                id = "001", drawableName = "makromon_ignar", displayName = "Ignar",
                type = "OHEŇ / STARTER",
                desc = "Ohnivá ještěrka plná energie. Nastartuj svůj metabolismus jako Ignar rozdmýchává svůj ocas!",
                hint = "Ignar tě čeká od samého začátku. Vyber si ho jako svého startéra!",
                evolveLevel = 4, evolveToId = "002"
            ),
            MakromonEntry(
                id = "002", drawableName = "ic_home", displayName = "Ignaroc",
                type = "OHEŇ / PROGRES",
                desc = "Střední evoluce Ignara. Oheň uvnitř roste spolu s tvojí silou.",
                hint = "Ignar se vyvine na levelu 4. Cvič poctivě!",
                evolveLevel = 10, evolveToId = "003"
            ),
            MakromonEntry(
                id = "003", drawableName = "ic_home", displayName = "Ignaroth",
                type = "OHEŇ / DRAK",
                desc = "Finální forma. Dračí oheň a síla. Ultimátní spalovač kalorií.",
                hint = "Ignaroc se vyvine na levelu 10. Dlouhodobá disciplína přináší ovoce.",
                evolveLevel = 0, evolveToId = ""
            ),

            // ── AQULIN RODINA (004-006) ───────────────────────────────
            MakromonEntry(
                id = "004", drawableName = "makromon_aqulin", displayName = "Aqulin",
                type = "VODA / STARTER",
                desc = "Malý vydří s ploutví. Hydratace je základ každého výkonu!",
                hint = "Aqulin tě čeká od samého začátku. Vyber si ho jako svého startéra!",
                evolveLevel = 4, evolveToId = "005"
            ),
            MakromonEntry(
                id = "005", drawableName = "ic_home", displayName = "Aqulind",
                type = "VODA / REGENERACE",
                desc = "Střední evoluce Aqulina. Voda léčí a regeneruje.",
                hint = "Aqulin se vyvine na levelu 4. Plň svůj vodní cíl každý den!",
                evolveLevel = 10, evolveToId = "006"
            ),
            MakromonEntry(
                id = "006", drawableName = "ic_home", displayName = "Aqulinox",
                type = "VODA / SÍLA",
                desc = "Finální forma. Hydro pumpa na maximum. Svaly nabyté vodou a silou.",
                hint = "Aqulind se vyvine na levelu 10. Vytrvalost a hydratace jsou klíčem.",
                evolveLevel = 0, evolveToId = ""
            ),

            // ── FLORI RODINA (007-009) ────────────────────────────────
            MakromonEntry(
                id = "007", drawableName = "makromon_flori", displayName = "Flori",
                type = "PŘÍRODA / STARTER",
                desc = "Jelínek s listovou korunou. Zelenina a vláknina jsou tvoji přátelé!",
                hint = "Flori tě čeká od samého začátku. Vyber si ho jako svého startéra!",
                evolveLevel = 4, evolveToId = "008"
            ),
            MakromonEntry(
                id = "008", drawableName = "ic_home", displayName = "Florind",
                type = "PŘÍRODA / RŮST",
                desc = "Střední evoluce Floriho. Rostlinná energie pro každodenní výkon.",
                hint = "Flori se vyvine na levelu 4. Jez více zeleniny a ovoce!",
                evolveLevel = 10, evolveToId = "009"
            ),
            MakromonEntry(
                id = "009", drawableName = "ic_home", displayName = "Florindra",
                type = "PŘÍRODA / MOUDROST",
                desc = "Finální forma. Stromový duch harmonie. Dlouhodobé zdraví těla i mysli.",
                hint = "Florind se vyvine na levelu 10. Konzistentnost je tvoje největší zbraň.",
                evolveLevel = 0, evolveToId = ""
            ),

            // ── SPECIÁLNÍ (010-011) ───────────────────────────────────
            MakromonEntry(
                id = "010", drawableName = "makromon_umbex", displayName = "Umbex",
                type = "DUCH / TEMNÝ",
                desc = "Shluk špatných vzpomínek a smutku. Vznikne jen z lásky a ztráty. Uvnitř dobrý.",
                hint = "Umbex se toulá pouze v noci. Zkus večerní trénink po 19:00.",
                evolveLevel = 0, evolveToId = ""
            ),
            MakromonEntry(
                id = "011", drawableName = "ic_home", displayName = "Lumex",
                type = "DUCH / SVĚTLO",
                desc = "Protějšek Umbexe. Zářivý zvenku, ale uvnitř skrývá temné záměry.",
                hint = "Lumex je velmi vzácný a toulá se pouze v noci.",
                evolveLevel = 0, evolveToId = ""
            ),

            // ── SPIRRA RODINA (012-019) ───────────────────────────────
            MakromonEntry(
                id = "012", drawableName = "makromon_spirra", displayName = "Spirra",
                type = "NORMÁLNÍ / ZÁKLAD",
                desc = "Béžová veverka se spirálovým ocasem. Základní forma plná potenciálu.",
                hint = "Spirra je nejčastější Makromon. Hledej ji všude kolem sebe!",
                evolveLevel = 0, evolveToId = ""
            ),
            MakromonEntry(
                id = "013", drawableName = "makromon_flamirra", displayName = "Flamirra",
                type = "OHEŇ / EVOLUCE",
                desc = "Ohnivá evoluce Spirry. Zlatooranžová spirála spaluje tuky jako šílená.",
                hint = "Flamirra se toulá v každém počasí. Pravidelný trénink ji přiláká.",
                evolveLevel = 0, evolveToId = ""
            ),
            MakromonEntry(
                id = "014", drawableName = "makromon_aquirra", displayName = "Aquirra",
                type = "VODA / EVOLUCE",
                desc = "Vodní evoluce Spirry. Teal modrá spirála pro dokonalou hydrataci.",
                hint = "Aquirra se objeví, když splníš svůj denní vodní cíl.",
                evolveLevel = 0, evolveToId = ""
            ),
            MakromonEntry(
                id = "015", drawableName = "makromon_verdirra", displayName = "Verdirra",
                type = "PŘÍRODA / EVOLUCE",
                desc = "Travní evoluce Spirry. Zelená spirála pro sílu ze země.",
                hint = "Verdirra miluje čerstvý vzduch. Cvič venku a přilákáš ji!",
                evolveLevel = 0, evolveToId = ""
            ),
            MakromonEntry(
                id = "016", drawableName = "makromon_shadirra", displayName = "Shadirra",
                type = "DUCH / EVOLUCE",
                desc = "Temná evoluce Spirry. Fialová spirála skrývající tajemství noci.",
                hint = "Shadirra se toulá pouze v noci po 19:00.",
                evolveLevel = 0, evolveToId = ""
            ),
            MakromonEntry(
                id = "017", drawableName = "makromon_charmirra", displayName = "Charmirra",
                type = "VÍLA / EVOLUCE",
                desc = "Fairy evoluce Spirry. Růžová pastelová spirála plná šarmu.",
                hint = "Charmirra je přátelská. Splň svá makra a ona tě navštíví!",
                evolveLevel = 0, evolveToId = ""
            ),
            MakromonEntry(
                id = "018", drawableName = "ic_home", displayName = "Glacirra",
                type = "LED / EVOLUCE",
                desc = "Ledová evoluce Spirry. Chladná a precizní jako tvůj tréninkový plán.",
                hint = "Glacirra přichází nečekaně. Buď konzistentní a najdeš ji!",
                evolveLevel = 0, evolveToId = ""
            ),
            MakromonEntry(
                id = "019", drawableName = "makromon_drakirra", displayName = "Drakirra",
                type = "DRAK / SKRYTÁ EVOLUCE",
                desc = "Tajemná dračí evoluce Spirry. Zlatá a teal spirála plná prastaré síly.",
                hint = "Drakirra je skrytá evoluce. Jen ti nejdisciplinovanější ji spatří – 30 check-inů!",
                evolveLevel = 0, evolveToId = ""
            ),

            // ── OSTATNÍ (020-031) ─────────────────────────────────────
            MakromonEntry(
                id = "020", drawableName = "makromon_finlet", displayName = "Finlet",
                type = "VODA / SLABÝ",
                desc = "Malá průhledná rybka. Každý začátek je malý, ale i Finlet může být mocný.",
                hint = "Finlet je velmi běžný. Hledej ho u vodních zdrojů.",
                evolveLevel = 8, evolveToId = "021"
            ),
            MakromonEntry(
                id = "021", drawableName = "ic_home", displayName = "Serpfin",
                type = "VODA / SÍLA",
                desc = "Obří rybohadí monstrum. Důkaz že konzistence přináší brutální transformaci.",
                hint = "Finlet se vyvine na levelu 8. Věř procesu!",
                evolveLevel = 0, evolveToId = ""
            ),
            MakromonEntry(
                id = "022", drawableName = "makromon_mycit", displayName = "Mycit",
                type = "NORMÁLNÍ / KOLONIE",
                desc = "Malá myška s krystalky. Žijí v koloniích a staví primitivní domečky.",
                hint = "Mycit je velmi běžný. Hledej ho na okrajích lesů a luk.",
                evolveLevel = 7, evolveToId = "023"
            ),
            MakromonEntry(
                id = "023", drawableName = "ic_home", displayName = "Mydrus",
                type = "JED / DRUID",
                desc = "Druidský vůdce kolonie. Pozřen jedem, ale imunní. Chrání své bratry.",
                hint = "Mydrus se vyvine z Mycita na levelu 7. Po 5 check-inech ho najdeš.",
                evolveLevel = 0, evolveToId = ""
            ),
            MakromonEntry(
                id = "024", drawableName = "ic_home", displayName = "Soulu",
                type = "DUCH / ZÁRODEK",
                desc = "Malá roztomilá duše. Ještě se učí ovládat svůj průhledný obleček.",
                hint = "Soulu se toulá v noci. Cvič po 19:00 a třeba ho potkáš!",
                evolveLevel = 5, evolveToId = "025"
            ),
            MakromonEntry(
                id = "025", drawableName = "ic_home", displayName = "Soulex",
                type = "DUCH / STŘEDNÍ",
                desc = "Obleček začíná sedět, ale stále uniká kontrole. Roste v síle.",
                hint = "Soulu se vyvine na levelu 5. Noční trénink urychlí jeho růst.",
                evolveLevel = 10, evolveToId = "026"
            ),
            MakromonEntry(
                id = "026", drawableName = "ic_home", displayName = "Soulord",
                type = "DUCH / MISTR",
                desc = "Finální forma. Duše v dokonalém obleku. Ovládá prostor i čas.",
                hint = "Soulex se vyvine na levelu 10. Po 20 nočních check-inech!",
                evolveLevel = 0, evolveToId = ""
            ),
            MakromonEntry(
                id = "027", drawableName = "ic_home", displayName = "Phantil",
                type = "VODA / DUCH",
                desc = "Malá průsvitná duch-ryba. Lehká jako pára nad hladinou.",
                hint = "Phantil se toulá v noci u vodních ploch.",
                evolveLevel = 6, evolveToId = "028"
            ),
            MakromonEntry(
                id = "028", drawableName = "ic_home", displayName = "Phantius",
                type = "VODA / DUCH",
                desc = "Větší a temnější. Duchový opar kolem jeho těla houstne.",
                hint = "Phantil se vyvine na levelu 6. Noční trénink je klíčem.",
                evolveLevel = 12, evolveToId = "029"
            ),
            MakromonEntry(
                id = "029", drawableName = "ic_home", displayName = "Phantiax",
                type = "VODA / DUCH",
                desc = "Obří duch-mořský drak. Vznáší se nad hladinou a ovládá přílivy.",
                hint = "Phantius se vyvine na levelu 12. Po 20 check-inech!",
                evolveLevel = 0, evolveToId = ""
            ),
            MakromonEntry(
                id = "030", drawableName = "makromon_gudwin", displayName = "Gudwin",
                type = "NORMÁLNÍ / MOUDROST",
                desc = "Tlustý medvěd s váčkem přes rameno. Moudrý rádce a zkušený bojovník.",
                hint = "Gudwin vychází ven až po 7 poctivých check-inech. Ranní rituál je klíčem.",
                evolveLevel = 0, evolveToId = ""
            ),
            MakromonEntry(
                id = "031", drawableName = "makromon_axlu", displayName = "Axlu",
                type = "VODA / VÍLA",
                desc = "Růžový axolotl, tvář Makroflow. Vzácný, roztomilý a neuvěřitelně odolný.",
                hint = "Axlu je extrémně vzácný. Říká se, že se zjeví jen těm nejdisciplinovanějším – 50 check-inů!",
                evolveLevel = 0, evolveToId = ""
            )
        )
    }
}