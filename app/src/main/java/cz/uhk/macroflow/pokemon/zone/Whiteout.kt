package cz.uhk.macroflow.pokemon.zone

/**
 * Prohra celého týmu (docs/adr/0052). Souboj po posledním „FAINTED!“ nastaví příznak a zavře se;
 * mapa pak přiblíží postavu, přehraje animaci smrti a hráč se probudí na prahu domova ve městě.
 */
object Whiteout {
    /** Příznak v GamePrefs: souboj skončil prohrou celého týmu, mapa ještě nepřehrála smrt. */
    const val PENDING_KEY = "pending_whiteout"
    /** Uzel ve městě, kde se hráč probudí. */
    const val RESPAWN_NODE = "prah_domova"
    const val TEXT = "💀 Všichni tví Makromoni padli a ty s nimi… Probudil ses doma. Tým si mezitím odpočinul."
}
