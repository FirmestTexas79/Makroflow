package cz.uhk.macroflow.pokemon.story

/**
 * Hunfrey u Brány světů (docs/adr/0085). Čistá data, pokryto testy.
 *
 * Zcestovalý lišák s batohem plným plánků. Sedí na plošině vedle brány a kreslí ji. Viděl, jak
 * Drakirra po probuzení přelétla nad bránou a stočila se dolů nad Hvozd. Hráči vysvětlí, co se
 * stalo, ukáže stopy a runy a dokud hráč nemá Srdce Hvozdu, radí podle jeho postupu, co dál.
 * Kartu, kterou pořád točí v prstech, „našel ve Městě“ – na rubu má vybledlé razítko S-7.
 */
object Hunfrey {
    const val QUEST_ID = "sky_hunfrey"
    const val NAME = "Hunfrey"

    /** Stojí na plošině vpravo od brány (uzel Nebeského průsmyku). */
    const val NODE = cz.uhk.macroflow.pokemon.cave.SkyPass.LEDGE_NODE
    /** Kde stojí na mapě (art px průsmyku, pata postavy) a jak je vysoký. */
    const val X = 130
    const val Y = 226
    const val HEIGHT = 26

    /** Fáze, ve které radí, jak získat Srdce Hvozdu (index). */
    const val ADVICE_STAGE = 2

    /** Postup questu Hvozdu: (index aktuální fáze, název, vlastní připomínka), null = nezačal. */
    data class ForestStage(val index: Int, val title: String, val hint: String?)

    /**
     * Rada ve fázi [ADVICE_STAGE] podle toho, kde hráč je.
     * [tasksDone]/[tasksNeeded] = splněné úkoly pro vstup do Hvozdu (ForestMap).
     */
    fun advice(hasHeart: Boolean, heartPlaced: Boolean, tasksDone: Int, tasksNeeded: Int, forest: ForestStage?, forestDone: Boolean): String = when {
        heartPlaced -> "Brána je otevřená. Teď už jen dokreslím poslední runu a pak… kdo ví, třeba se potkáme na druhé straně."
        hasHeart -> "To zelené světlo v tvém batohu… to je ono! Srdce Hvozdu. Polož ho do lůžka pod bránou – já se radši podívám z bezpečné vzdálenosti."
        tasksDone < tasksNeeded ->
            "Srdce hlídá Hvozd pod námi. Jenže les je hustý a druidi tam pustí jen toho, kdo už něco dokázal: " +
                "aspoň $tasksNeeded splněných úkolů, ty jich máš $tasksDone. Mrkni do deníku, co tě čeká v Městě, na Louce a v Horách. " +
                "Vchod do Hvozdu je na severním okraji Louky."
        forest == null || forestDone ->
            "Vchod do Hvozdu je na severním okraji Louky. Na mýtině uprostřed lesa najdeš druida Mydruse – " +
                "ten ví, kde Srdce leží a co ho trápí."
        else ->
            "Podle toho, co mi vyprávíš, ti druid Mydrus dal úkol: „${forest.title}“. " +
                (forest.hint ?: "Co přesně ještě chybí, najdeš v deníku.") +
                " Až les uzdravíš, Starý dub ti Srdce sám vydá."
    }

    // ── Texty fází (použité v QuestRegistry) ──

    const val INTRO =
        "Ty jsi ten, kdo vzbudil dračici? Klid, já nic. Jsem Hunfrey – cestovatel, kartograf a… sběratel. " +
            "Sedím tu už třetí den a kreslím tuhle bránu. Takovou architekturu jsem neviděl ani za mořem.\n\n" +
            "Viděl jsem ji. Drakirru. Vylétla ze svatyně, přeletěla těsně nad bránou – a pak se stočila dolů. " +
            "Neutíkala před tebou. Utíkala před něčím, co viděla vedle tebe.\n\n" +
            "Nevěříš? Dole na stezce jsou rýhy po jejích drápech. Jdi se podívat, kam míří."

    const val CLAWS_HINT = "Rýhy po drápech jsou dole na stezce, kousek nad vstupem ze svatyně."

    const val RUNES =
        "Míří dolů, nad Hvozd, že? Tam, kde se teď šíří rudá hniloba. Kde dopadl její popel, vyrůstají rudé houby.\n\n" +
            "A teď ta brána. Myslel jsem, že je to jen ozdoba. Jenže uvnitř prstence se mihotá závoj a pod ním je prázdné lůžko " +
            "ve tvaru listu. Přečti si runy a pak mi řekni, co tam stojí – já jim nerozumím, ale ty… ty jsi tady z nějakého důvodu."

    const val RUNES_HINT = "Brána stojí uprostřed plošiny, hned vedle mě. Klepni na ni, přečti si runy a pak se vrať za mnou."

    const val HEART =
        "„Jen ten, komu les svěří své srdce, projde.“ Tak to je ono. Brána nechce klíč, chce Srdce Hvozdu.\n\n" +
            "A víš, co je zvláštní? Tahle karta. Našel jsem ji ve Městě, je na ní stejný list jako na lůžku. " +
            "A na rubu vybledlé razítko… S-7. Nosí štěstí, tak si ji nechávám.\n\n" +
            "Přines Srdce. Když nebudeš vědět kudy, stav se – poradím ti."

    const val FAREWELL =
        "Viděl jsi to světlo? Celý život kreslím mapy, a teď poprvé nevím, co nakreslit za tou bránou.\n\n" +
            "Zůstanu tu ještě chvíli. Někdo musí zapsat, že se tady něco změnilo – i kdyby si to nikdo jiný nepamatoval."
}
