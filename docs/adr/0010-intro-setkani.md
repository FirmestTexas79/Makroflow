# ADR 0010 – Intro setkání: oprava stávajícího a vlastní intro pro Hory

- **Stav:** přijato (2026-09)
- **Kód:** `pokemon/encounter/MountainIntro.kt` a `MountainScene.kt` (čistý Kotlin, testy),
  `MountainEncounterView.kt` (Android), `PokemonBattleFragment` (výběr intra, záblesk, přeskočení).

## Zhodnocení původního intra
1. **Všude stejné.** Křoví se zavrtělo i ve vodě a v Horách.
2. **Rozmazaný a zdeformovaný pixel art.** Keř 64 × 64 px se s vyhlazováním roztahoval na 80 % × 50 % obrazovky,
   listí 32 px podobně.
3. **Neviditelný záblesk.** Animovalo se pozadí overlaye, jenže ho celé zakrýval přechod (potomek overlaye).
   Záblesk tak nebyl vidět a intro na konci jen naráz zmizelo.
4. **Nejde přeskočit.** Intro trvá asi 2,3 s a klepnutí ho nezrychlí.
5. **Po zavření dobíhá.** Zpožděná volání (Handler) a animace se nerušila, takže běžela i po zavření souboje.

## Rozhodnutí
- **Výběr intra podle lokace** (`LAST_BIOME`): Hory mají vlastní scénu, louka a město křoví, voda křoví s modrým přechodem.
- **Hory:**
  - Pixel-art scéna v nízkém rozlišení (~120 px na šířku), zvětšená celočíselným násobkem bez vyhlazení.
  - Paleta je převzatá z `mountains.png`.
  - Průběh (časová osa v `MountainIntro`):

    | Čas | Co se děje |
    |---|---|
    | 0–0,35 s | roztmívání do soumraku se sluncem |
    | 0,2–0,95 s | zdola vyjede zadní hřeben (mid-point displacement) a přední stolové hory srovnané do teras s balvanem |
    | 1,0 s | dunění: balvan se třese, padají kamínky, u paty víří prach |
    | 1,45 s | shora dolů roste klikatá prasklina |
    | 1,75 s | balvan se rozpůlí, půlky odletí po parabole s rotací, silný otřes, 22 úlomků s gravitací a mrak prachu |
    | 2,0 s | písečný (u shiny zlatý) záblesk a souboj |

  - Snímek je čistá funkce času (`render(t)`), bez stavu mezi snímky. Díky tomu:
    - přeskočení je jen skok v čase;
    - scéna jde testovat;
    - náhled (GIF) jde vyrenderovat mimo telefon.
- **Křoví:**
  - keře jsou čtvercové bez deformace, pixel art bez vyhlazování;
  - záblesk je samostatná vrstva navrchu a na jeho vrcholu se scéna vymění za souboj;
  - klepnutím jde intro přeskočit;
  - všechna volání a animace se při zavření zruší.
- **Shiny** efekty (ADR 0009) fungují v obou intrech přes společnou funkci `revealBattle`.
