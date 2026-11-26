package cz.uhk.macroflow.pokemon.story

/**
 * Železný výtah nad propastí na náměstí Ozvěny (docs/adr/0108). Klec visí za zábradlím nad korunou Lumiry; na cedulce stojí
 * „SUBJEKT PŘIPRAVEN“ s prázdnou kolonkou. Jakmile hráč projde Věží až na lávku Sektoru Hlubiny (−2) a uvidí Spící,
 * kolonka je vyplněná (213) a výtah jezdí: z náměstí dolů k lávce −2 a zpátky – zkratka mimo Věž, ne náhrada příběhu.
 */
object AbyssLift {
    /** Hráč už jel výtahem (první jízda má vlastní scénu); GamePrefs. */
    const val RIDDEN_KEY = "abyss_lift_ridden"

    fun open(isSet: (String) -> Boolean) = isSet(TowerStory.ABYSS_KEY)

    const val OPEN_TEXT = "🛗 Železný výtah nad propastí. Kolonka na cedulce je vyplněná, razítko ještě voní inkoustem:\n" +
        "SUBJEKT PŘIPRAVEN: 213.\nKlec je odemčená. Jede dolů – k lávce Sektoru Hlubiny nad korunou Lumiry."

    const val FIRST_RIDE = "Zatáhneš za páku. Klec sebou trhne a řetězy začnou cvakat přes kladku.\n\n" +
        "Náměstí odjede nahoru. Pak zábradlí, pak skála – a pak už jen tma a mlha, ze které pomalu vystupují kořeny. " +
        "Nejdřív tenké, pak tlusté jako věže. Visíš mezi nimi jako pavouk na vlákně.\n\n" +
        "Hluboko pod tebou se v mlze rýsuje koruna. Hlavou dolů. Lumira.\n\n" +
        "Klec dosedne vedle lávky Sektoru Hlubiny. Signálka nad ní blikne žlutě: PŘIPRAVEN."

    const val DOCK_TEXT = "🛗 Železný výtah z náměstí Ozvěny. Dva řetězy mizí nahoru do tmy. Nahoru pojede, kdykoli zatáhneš za páku."
}
