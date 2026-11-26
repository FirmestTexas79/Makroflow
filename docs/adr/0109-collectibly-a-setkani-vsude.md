# 0109 – Sběratelské předměty, vedlejší úkoly a viditelná setkání ve všech zónách

## Kontext
Po 0108 měla sběratelské předměty a propojovací úkol jen Hlubina, setkání byla vidět jen v Hlubině a v jeskyních
a čtení podržením chybělo na grafových mapách (Město, Louka, Hory).

## Rozhodnutí
- **Obecné sběratelské předměty** (`story/Collectibles`): sada = šest předmětů v šesti lokacích, sprite 8 × 8 se zářičkou,
  klepnutí = dojít a sebrat (PickupFx → panel). První kus spustí úkol a ukáže jeho úvod; fáze 1 počítá příznaky.
  - **Kuchařka krále Mlsáka** (zóna 1: Město, Louka, Hory, jeskyně, Starý důl, Hvozd) → 2 snídaně. Recepty s poznámkami
    na okraji; poslední stránka „AŤ UŽ NIKDY NIKDO NEMÁ HLAD. — H.“ (Hladomír, bez odhalení). Nic neodměňuje hladovění.
  - **Nedoručené dopisy** (zóna 2: pláň, sad, kaňon, 2× Ozvěna, Aréna) → vrátit pošťáku Liovi (když zmizel, připíchnout na
    vývěsku). Dopisy jsou pro domy, kde nikdo nebydlí; poslední, jen s číslem 213, je od Hunfreyho (212).
  - **Spánkové deníky** (Věž, patra 2–7: okruhy 206–211, které Hunfrey vynesl z Mrazírny) → jedna noc aspoň 7 h.
- **Viditelná setkání všude** (`placeEncounterMarks`): ve tmě pár očí, venku a v zarostlých patrech „šust“ (dvě čárky
  a poskočení). Jeskyně a doly mají dál svou záři, vodu je vidět sama.
- **Čtení i na grafových mapách**: inspect pracuje s `ArtFrame` (jeskyně i mapa na celou obrazovku); rozcestník v Horách.

## Důsledky
Nová sada = záznam v `Collectibles.SETS` + úkol v `QuestRegistry`; test hlídá uzly, kolize se stopami, listy a lucernami.
