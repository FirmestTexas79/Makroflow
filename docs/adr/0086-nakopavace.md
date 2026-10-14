# 0086 – Nakopávače (XP a levely Makromonům)

Stav: přijato (2026-10-12, do `MULTI` 2026-10-14; původně na větvi `feature/rare-nakopavace` jako 0074)

- Nové vzácné předměty jako Rare Candy, jen v duchu aplikace – energie a doplňky (`pokemon/boost/RareBoost`):
  **Proteinová tyčinka** (+250 XP), **Preworkout** (+1 level), **Bílý Monster** (+3 levely).
  Síla každého je na jednom místě (`RareBoost.effect`), takže ladění je změna jednoho čísla.
- Pravidla: level nikdy neklesne; levelový nakopávač nastaví XP na začátek nového levelu (postup od nuly),
  XP nakopávač jde přes `PokemonLevelCalc.gain` (stará chycení s levelem z divočiny a XP 0 nespadnou).
  Na maximálním levelu (30) se nic nespotřebuje a Makromon je v seznamu zašedlý.
- Batoh: vlastní přihrádka **Nakopávače** (mezi Lékárničkou a Vybavením), akce „Dát Makromonovi“ →
  výběr z Kapsy (tým první, pak podle levelu) → `PocketActions.feedBoost` (spotřeba předmětu, zápis do DB
  a na Firebase). Evoluce: stejné pravidlo jako u XP za cíle (level dosáhne nebo přeskočí hranici druhu,
  `RareBoost.evolutionDue`), Spirra jde přes `SpirraEvolutionFlow`, aby zůstala volba větve na hráči.
- Starý inventář (`InventoryFragment`) nakopávače jen zobrazí (název, ikona) a odkáže do deníku.
- Ladění (debug build): podržení deníku → tři nové položky dají +5 ks daného nakopávače do batohu.
- Zdroj: **obchod** (záložka s Makrobally a léky) – tyčinka 120, preworkout 300, Bílý Monster 800 penízků
  (`RareBoost.price`). Za souboj padá 1–5 penízků, takže levelové nakopávače zůstávají vzácné.
