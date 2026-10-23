package cz.uhk.macroflow.pokemon.cutscene

/** Kdy se přehrají příběhové cutscény prvního regionu (docs/adr/0098) – klíče v GamePrefs (nesynchronizují se). */
object StoryCutscenes {
    /** Legenda právě odletěla – po návratu ze souboje na mapu se přehraje „Oči“. */
    const val DRAKIRRA_PENDING = "cutscene_drakirra_pending"
    /** Mydrus právě zmínil modré houby – po zavření dialogu se přehraje „Modré světlo v trní“ (jen jednou). */
    const val GROVE_ARMED = "cutscene_grove_armed"
    const val GROVE_SEEN = "cutscene_grove_seen"
    /** Soulord právě padl – „Pád Soulorda“ se přehraje před odměnou od Starého dubu. */
    const val SOULORD_PENDING = "cutscene_soulord_pending"
}
