# ADR 0009 – Shiny Makromoni

- **Stav:** přijato (2026-09)
- **Kód:** `pokemon/shiny/ShinyPalette.kt` (čistý Kotlin, testy `ShinyPaletteTest`), `ShinySprites.kt` (Android, cache),
  `PokemonBattleFragment` (los + intro), `PokemonBattleView` (sprite, hvězdy, uložení),
  zobrazení chyceného: Inventory, CompanionManager, MakromonBarController + WandererFactory, notifikace, EvolutionDialog.
  DB beze změny: `captured_pokemon.isShiny` existoval od začátku.

## Rozhodnutí
- **Šance:** 1 : 100 na každé divoké setkání. Los probíhá ve fragmentu ještě před vytvořením souboje, aby na něj
  mohlo reagovat intro. Tutoriálové křoví (`FORCE_ENCOUNTER_ID`) shiny nikdy není.
- **Filtr = rotace odstínu v HSV.** Každý pixel se převede do HSV, odstín se otočí o úhel druhu, sytost se zvedne ×1,12
  (max 1) a jas i průhlednost zůstanou. Pixely bez barvy (černý obrys, bílá, šedá) se nemění, takže sprite si drží
  kresbu a mění se jen „barva srsti“.
- **Úhel podle druhu:** hash ID (FNV-1a + promíchání fmix32) do rozsahu 90–270°. Pod 90° by rozdíl nebyl vidět,
  nad 270° by se odstín vracel k původnímu. Shiny daného druhu je tak vždy stejný. Evoluce shiny dá shiny nového druhu
  s jeho vlastním odstínem.
- **Výsledný obrázek je soupeř:** souboj kreslí přebarvenou bitmapu, a když se Makromon chytí,
  uloží se s `isShiny = true`. Stejným filtrem se pak zobrazuje všude, kde je vidět chycený Makromon (bitmapy v LRU cache).
- **Efekt:**

  | Kdy | Co se děje |
  |---|---|
  | Intro | zlatý záblesk místo bílého a déšť hvězd ✦ |
  | Souboj (odhalení) | výbuch dvou vln čtyřcípých hvězd, zlatá záře za soupeřem a vibrace |
  | Souboj (dál) | záře jemně pulzuje a po soupeři občas přeběhnou třpytky |
  | HUD a texty | štítek „*SHINY“, text „*SHINY* JMÉNO APPEARED!“ |

  Po chycení dostane aktivní Makromon dvojnásobné XP.
- **Ladění:** v debug buildu nastaví podržení tlačítka deníku na mapě, že příští setkání bude shiny.

## Související úpravy (Hory)
- Portrét krále Mlsáka v dialogu má 110 × 220 dp místo 110 × 110. Obrázek je 1 : 2, takže ve čtverci byl poloviční.
- Body v Horách:
  - **Tábor** nově ukáže přehled dne (kroky a co zbývá z kalorií a bílkovin).
  - **Rozcestník** je klikací a ukáže směry.
  - **Neúspěšné hledání** (10 %) nově napíše hlášku. Dřív se nestalo nic a bod působil rozbitě.
