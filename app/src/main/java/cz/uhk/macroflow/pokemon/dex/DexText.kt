package cz.uhk.macroflow.pokemon.dex

import cz.uhk.macroflow.pokemon.CapturedMakromonEntity
import cz.uhk.macroflow.pokemon.evolution.SpirraEvolution

/**
 * Texty Makrodexu sdílené obrazovkou Makrodexu a záložkou v deníku (docs/adr/0060):
 * nápověda, jak druh najít, a přehled cest vývoje Spirry.
 */
object DexText {

    /** Druhy v Makrodexu, které se nedají potkat v divočině (strážci) – číslo Makrodexu. */
    const val IGNILEO = "034"
    const val AQUAVULP = "035"
    val EXTRA_IDS: Set<String> = setOf(IGNILEO, AQUAVULP)

    /** Všechna čísla Makrodexu: divocí z SpawnManageru + strážci. */
    fun dexIds(spawnIds: Collection<String>): Set<String> = spawnIds.toSet() + EXTRA_IDS

    /** Strážci, které hráč porazil – v Makrodexu jsou „viděni“ (chytit je nejde). */
    fun defeatedGuardians(flags: Set<String>): Set<String> =
        buildSet {
            if ("boss_defeated_RED" in flags || "crystal_RED" in flags) add(IGNILEO)
            if ("boss_defeated_BLUE" in flags || "crystal_BLUE" in flags) add(AQUAVULP)
        }

    /** Nápověda k neobjevenému druhu; obecnou větu nahradí nápověda z databáze, pokud je. */
    fun hint(id: String, unlockedHint: String): String {
        val h = fallback(id)
        return if (h.startsWith("Zapiš") && unlockedHint.isNotEmpty()) unlockedHint else h
    }

    private fun fallback(id: String): String = when (id) {
        "001", "002", "003" -> "Ignar se probouzí teplem tvého tréninku. Zapiš dnešní cvičení!"
        "004", "005", "006" -> "Aqulin připluje jen tehdy, když splníš svůj denní vodní cíl."
        "007", "008", "009" -> "Flori roste tam, kde je zdravá strava. Zapiš dnešní jídla!"
        "010"               -> "Umbex se toulá v noci. Zkus večerní trénink po 19:00."
        "011"               -> "Lumex je velmi vzácný a toulá se pouze v noci."
        "012"               -> "Spirra je nejčastější Makromon. Hledej ji všude kolem sebe!"
        "013"               -> "Vyvine se ze Spirry, která s tebou pořádně zapotí. Má ráda, když spálíš hodně kalorií pohybem."
        "014"               -> "Vyvine se ze Spirry, se kterou se pořádně napiješ. Má ráda, když ti nikdy nedojde voda."
        "015"               -> "Vyvine se ze Spirry, se kterou jíš zeleninu den co den. Má ráda, když má vláknina svou míru."
        "016"               -> "Vyvine se ze Spirry, se kterou si večer zdravě zamlsáš. Má ráda noční svačinky."
        "017"               -> "Vyvine se ze Spirry, se kterou nachodíš spoustu kilometrů. Má ráda dlouhé procházky."
        "018"               -> "Vyvine se ze Spirry, se kterou poctivě dřeš v posilovně. Má ráda každou zapsanou sérii."
        "041"               -> "Vyvine se ze Spirry, se kterou každé ráno uděláš check-in. Má ráda klidná rána a dobrý spánek."
        "019"               -> "Tajná evoluce Spirry. Ani Spirra neví, jak se jí stát – zatím ji jde jen ulovit."
        "020"               -> "Finlet je velmi běžný. Hledej ho všude kolem sebe."
        "021"               -> "Serpfin se vyvine z Finleta na levelu 8. Věř procesu!"
        "022"               -> "Mycit žije na okrajích lesů a luk."
        "023"               -> "Mydrus se vyvine z Mycita. Po 5 check-inech ho najdeš."
        "024", "025", "026" -> "Soulu rodina se toulá pouze v noci."
        "027", "028", "029" -> "Phantil rodina se toulá v noci u vodních ploch."
        "030"               -> "Gudwin vychází ven až po 7 poctivých check-inech."
        "031"               -> "Axlu se ukáže jen těm nejdisciplinovanějším – 50 check-inů!"
        "032"               -> "Mysnic pobíhá po kamenitých stezkách v Horách."
        "033"               -> "Mysnor se vyvine z Mysnica na levelu 10. Vzácně ho potkáš i v Horách."
        "034"               -> "Strážce rudého krystalu v hlubinách Starého dolu. Zapíše se, až ho porazíš."
        "035"               -> "Strážkyně modrého krystalu u podzemního jezírka. Zapíše se, až ji porazíš."
        else                -> "Zapiš trénink a jídlo, Makromon se brzy objeví!"
    }

    /** Cesty vývoje Spirry a postup aktivní Spirry (docs/adr/0031, 0055). */
    fun spirraPaths(active: CapturedMakromonEntity?, days: List<SpirraEvolution.Day>?): String {
        val SE = SpirraEvolution
        val progress = days?.let { SE.progress(it) }
        val reached = days?.let { SE.reachedOn(it) }.orEmpty()
        val first = days?.let { SE.evolveInto(it, SE.LEVEL) }          // stejné pravidlo jako vývoj
        val fmt = java.time.format.DateTimeFormatter.ofPattern("d. M.")
        return buildString {
            if (active == null) append("Počítá se, jen když je Spirra tvým aktivním parťákem na liště. Nastav ji v Kapse.\n\n")
            else {
                append("Všechny cesty se počítají najednou. Od levelu ${SE.LEVEL} se Spirra vyvine do té, kterou splní PRVNÍ.\n")
                append("Teď: Lv ${active.level}" + (if (active.level < SE.LEVEL) " – do vývoje chybí ${SE.LEVEL - active.level} lv." else " – vývoj je odemčený.") + "\n")
                first?.let { append("První splněná cesta: ${it.displayName} (${reached[it]?.format(fmt)})\n") }
                append("\n")
            }
            SpirraEvolution.Branch.entries.forEach { b ->
                val v = progress?.get(b) ?: 0
                val pct = (SE.fraction(b, v) * 100).toInt()
                val mark = when { b == first -> "★ "; b in reached -> "✓ "; else -> "" }
                append("$mark${b.displayName.uppercase()}\n${b.task}\n")
                append(if (progress != null) "${SE.progressText(b, v)}  ($pct %)\n\n" else "\n")
            }
            append("DRAKIRRA\n??? – tajná, zatím jen k ulovení")
        }
    }
}
