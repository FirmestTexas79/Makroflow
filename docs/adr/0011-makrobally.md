# ADR 0011 – Makrobally: tři úrovně, vlastní sprity a animace chycení

- **Stav:** přijato (2026-09)
- **Kód:** `pokemon/balls/Makroball.kt` (čistý Kotlin, testy `MakroballTest`), `BallSprites.kt`,
  `PokemonBattleView` (hod, otevření, vtažení, kolébání), `PokemonShopFragment`, `InventoryFragment`.

## Kontext
Souboj kreslil pro každý ball stejný 7px sprite. Kolébání bylo jen text „...“. Obchod a inventář
stahovaly obrázky originálních Pokéballů z pokemondb.net, tedy cizí grafiku, která se offline nezobrazí.

## Rozhodnutí
- **Tři Makrobally:**

  | Makroball | ID | Násobitel | Cena a balení | Vzhled |
  |---|---|---|---|---|
  | Makroball | `poke_ball` | 1× | 20 mincí / 5 ks | barvy aplikace |
  | Proteinball | `great_ball` | 1,5× | 50 mincí / 3 ks | modrý „šejkr“ s bílým pruhem |
  | Kreatinball | `ultra_ball` | 2× | 120 mincí / 2 ks | černý s tmavě stříbrným spodkem a zlatým pásem a nýty, jako kotouč činky |

  ID zůstala kvůli datům v DB, Firebase a promo kódům.
- **Šance na chycení** se počítá stejným vzorcem jako dřív: `((1 − HP/maxHP) · 220 + 20) · druh · ball`, strop 255 z 256.
- **Vzhled:** střed je u všech tří stejný, černý pás a kulaté bílé tlačítko vpředu; liší se barvou víčka a spodku.
  Sprite je 12 × 12 px definovaný v kódu jako mřížka znaků, takže jde otestovat.
  Víčko (řádky 0–5) a spodek (6–11) se kreslí zvlášť, aby šlo víčko odklopit na pantu.
- **Animace:**

  | Fáze | Co se děje |
  |---|---|
  | Let | ball letí obloukem a otáčí se (2 otáčky) |
  | Dopad | víčko se odklopí, rozsvítí se záře a Makromon se změní ve světlo, zmenší se a vletí do ballu |
  | Pád | víčko se zavře, ball spadne na plošinu s odrazem |
  | Kolébání | ball se kolébá kolem spodku; počet kolébání vychází z šance jako dřív |
  | Chyceno | z ballu vyletí tři zlaté hvězdičky |
  | Utekl | ball se otevře a Makromon z něj vyroste zpátky |
- **Obchod a inventář** kreslí ikonky ze stejných pixelů (žádná síť). Menu souboje bere počty z mezipaměti.
  Dřív se DB četla při každém překreslení.

## Otevřené
- Návnady v obchodě (Spooky Plate, Black Belt) mají pořád jména a obrázky z Pokémonů.
- Stavy soupeře násobí šanci na chycení (ADR 0012).
