package cz.uhk.macroflow.pokemon.story

import cz.uhk.macroflow.pokemon.PokemonLevelCalc
import cz.uhk.macroflow.pokemon.trainer.Trainer
import cz.uhk.macroflow.pokemon.trainer.TrainerMon
import cz.uhk.macroflow.pokemon.trainer.Trainers
import cz.uhk.macroflow.pokemon.wild.MovePool
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Kapitola V · Pět Mistrů Arény (bible příběhu, docs/adr/0102). Čistá pravidla a texty, pokryto testy.
 *
 * Mistři jdou po sobě. Mistr N chce N tréninků za posledních 7 dní (nejvýš 4) – počítá se deník, dokončené
 * „Jdu na trénink“ i aktivita z hodinek. Prohra nic nestojí. Síla se řídí hráčovým týmem (jako AI v Aréně).
 */
object Masters {
    data class Master(
        val key: String, val name: String, val title: String,
        /** Číslo v Makrodexu pro portrét. */
        val portrait: Int,
        val team: List<String>, val levelDelta: Int, val need: Int,
        val intro: String, val win: String
    )

    val ALL = listOf(
        Master("rhenna", "Rhenna", "Mistryně rozcvičky", 8, listOf("007", "008", "020"), -2, 1,
            "„Kdo se nerozcvičí, ten se neučí. Kdo se neučí, ten se opakuje. A tady se opakuje dost lidí.“",
            "„Dobře. Zahřátý a pozorný. Odran už o tobě ví.“"),
        Master("odran", "Odran", "Mistr objemu", 33, listOf("032", "033", "044", "045"), 0, 2,
            "„Síla je jen trpělivost, která zvedá. Ukaž, kolik jí máš.“",
            "„Uneseš víc, než vypadáš. To se tu hodí. Tíhy tu přibývá.“"),
        Master("misk", "Misk", "Mistr tempa", 39, listOf("039", "024", "040", "036"), 1, 3,
            "„Pauza mezi sériemi je taky trénink. Já ji jen zkracuju. Připraven? Ne? Jdeme.“",
            "„Rychlý. Skoro tak rychlý, jako když tu lidi mizí. Haha. To byl vtip. Asi.“"),
        Master("taddeo", "Taddeo", "Mistr techniky", 29, listOf("029", "009", "006", "021"), 2, 4,
            "„Bojoval jsem tu stokrát a pokaždé vyhrál stejně. Nevím, proč si to pamatuju. Snad to dnes bude jiné.“",
            "„Jiné. Poprvé jiné. Děkuju ti.“ Na chvíli vypadá, jako by se mu ulevilo."),
        Master("kalibrator", "Kalibrátor", "Maskovaný Mistr", 10, listOf("010", "003", "037", "045", "029"), 4, 4,
            "„Subjekt se dostavil. Zahájit měření.“",
            "„Okruh dvě stě třináct. Výkon v toleranci.“")
    )

    const val MAX_NEED = 4
    fun flag(key: String) = "master_beaten_$key"
    val KEYS: Set<String> = ALL.map { flag(it.key) }.toSet()
    /** Poražený Kalibrátor = Pozvánka Řádu Váhy. */
    val INVITE_KEY = flag("kalibrator")

    fun trainerId(key: String) = "ai:master_$key"
    fun keyOfTrainer(id: String): String? = id.takeIf { it.startsWith("ai:master_") }?.removePrefix("ai:master_")?.takeIf { k -> ALL.any { it.key == k } }

    enum class Status { BEATEN, OPEN, NEED_TRAINING, NEED_PREVIOUS }

    fun status(index: Int, trainings: Int, beaten: Set<String>): Status {
        val m = ALL[index]
        return when {
            m.key in beaten -> Status.BEATEN
            index > 0 && ALL[index - 1].key !in beaten -> Status.NEED_PREVIOUS
            trainings < m.need.coerceAtMost(MAX_NEED) -> Status.NEED_TRAINING
            else -> Status.OPEN
        }
    }

    fun needText(need: Int, have: Int): String =
        "Mistr chce vidět ${trainingsWord(need)} za posledních 7 dní (máš $have). Počítá se deník, „Jdu na trénink“ i hodinky."

    private fun trainingsWord(n: Int) = "$n " + when (n) { 1 -> "trénink"; in 2..4 -> "tréninky"; else -> "tréninků" }

    /** Tým Mistra: jeho druhy, level podle průměru hráčova týmu, počet nejvýš o jednoho víc než hráč (aspoň 2). */
    fun trainer(m: Master, myLevels: List<Int>, day: Long): Trainer {
        val avg = if (myLevels.isEmpty()) 5 else myLevels.average().roundToInt()
        val level = (avg + m.levelDelta).coerceIn(1, PokemonLevelCalc.MAX_LEVEL)
        val size = (myLevels.size + 1).coerceIn(2, m.team.size)
        val rnd = Random(m.key.hashCode() * 31L + day)
        val team = m.team.take(size).map { id -> TrainerMon(id, level, MovePool.wildMoveset(id, level, rnd).map { it.name }) }
        return Trainer(trainerId(m.key), m.name, Trainer.Kind.AI, team.take(Trainers.MAX_TEAM))
    }

    const val HALL_INTRO = "Velitel Kassar: „Do Síně šampionů tě zapíšu, až porazíš pět Mistrů. Jenže Mistr nebojuje s někým, kdo netrénuje.“"

    const val VESSA_ARENA = "\n\nA ještě něco. Jména vítězů z Arény ze zdi nikdy nezmizela. Nevím proč. " +
        "Velitel Kassar hledá vyzyvatele pro pět Mistrů – v hale Arény je tréninkový kout, tam se přihlas."
    const val STAGE_HINT = "Tréninkový kout v hale Arény. Mistr chce vidět, že trénuješ: počítají se tréninky za posledních 7 dní."
    const val FAREWELL = "Maska se ani nepohne. „Okruh dvě stě třináct. Výkon v toleranci.“\n\n" +
        "Podá ti obálku s pečetí v podobě vah – Pozvánku Řádu Váhy. „Věž tě očekává. Žádost je… předběžně schválena.“\n\n" +
        "Kassar za tebou píše tvé jméno do Síně šampionů. Křída se mu v půlce slova zlomí."
    const val TOWER_INVITED = "⚖️ Věž Řádu Váhy. Cedulka na dveřích se změnila: „Žádost předběžně schválena. Čeká se na kalibraci.“"
    const val INVITE_ID = "order_invite"
    const val INVITE_LABEL = "Pozvánka Řádu Váhy"
    const val INVITE_TEXT = "Obálka s pečetí v podobě vah. Uvnitř jediná věta: „Subjekt Okruhu 213 se dostaví ke kalibraci.“"
}
