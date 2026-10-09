# 0082 – Obranný tým, tréninkový bonus, dřevěné okno výměny

Stav: přijato (2026-10-09), větev `MULTI`

- **Obranný tým**: v kartě Arény řádek „🛡 OBRANA“ + UPRAVIT. Výběr až 6 Makromonů v pořadí
  klepnutí (`arena_defense`, id z Room). Do cloudu jako duch jde obranný tým; prázdný výběr
  = aktuální tým. Soupeři arény a rank se dál řídí aktuálním (bojovým) týmem.
- **Tréninkový bonus** (`TrainingBonus`, platí ve všech soubojích – divocí, strážci, trenéři, duchové, rank):
  - trénink nebo kardio za posledních 24 h (`game_events` WORKOUT_DONE / CARDIO_DONE) →
    celý tým +10 % útoku, divocí Makromoni se chytají o 15 % snáz;
  - dnes splněná makra → **makro štít**: první stav (spánek, paralýza, jed, popálení, zmatení)
    na tvůj tým v souboji se zruší („MACRO SHIELD BLOCKED IT!“). Omráčení štít neblokuje.
  - Na začátku souboje oznámení `GameToast`, co je aktivní. Bonus se počítá jen u tvého týmu –
    duch jiného hráče ho nemá, takže odměňuje skutečný trénink toho, kdo hraje.
  - Při level-upu uprostřed souboje se statistiky přepočítají bez bonusu (drobnost, neřešeno).
- **Okno výměny**: stejný průvodce (BottomSheet), ale dřevěný panel, písmo Jersey, karty
  v dřevěných slotech, štítky jako pilulky, hlavní akce zelená dlaždice, shiny přebarvení.
