# ADR 0008 – Mapa svalů v plánu a taška do gymu

- **Stav:** přijato (2026-09)
- **Kód:** `training/body/` (Muscles = čistý Kotlin + testy, BodyShapes, BodyMapView), `training/GymBag.kt` (+ testy),
  `PlanFragment`, `fragment_plan.xml`, `item_day.xml`. Náhled postavy: `tools/bodymap/`.

## Mapa svalů
- **Postava:** vektorová, zepředu i zezadu. Uložená je jen levá polovina jako SVG cesty (viewBox 100 × 200),
  pravá vznikne zrcadlením. Partie jsou samostatné cesty oddělené mezerou, takže jdou podbarvit jednotlivě.
  Náhled se generuje do PNG přes prohlížeč (`render.py` + `shot.js`) a Kotlin konstanty vznikají ze stejného zdroje.
- **Karta dne:** po výběru typu se partie plynule rozsvítí barvou typu. Hlavní zapojení má plnou sytost, vedlejší poloviční.
  Vedle postavy je výpis („Prsa, přední ramena, triceps“).
- **Mapování (zjednodušené):**

  | Typ | Hlavní partie | Vedlejší partie |
  |---|---|---|
  | PUSH | prsa, přední ramena, triceps | – |
  | PULL | široký zádový, trapézy, zadní ramena, biceps | předloktí, spodní záda |
  | LEGS | kvadricepsy, hamstringy, hýždě, lýtka | spodní záda, břicho |
  | FULL | hlavní partie velkých cviků | ostatní |

  U kardia (běh, kolo, schody, švihadlo) jsou hlavně nohy.
- **Týden na těle:** efektivní frekvence partie za týden, kde hlavní zapojení = 1 a vedlejší = 0,5.
  Plná barva odpovídá ≥ 2× týdně. Hranice vychází z metaanalýzy Schoenfeld, Ogborn & Krieger (2016, Sports Med):
  frekvence 2× týdně vedla k většímu přírůstku svalů než 1×. V režimu Power karta vypíše partie pod 2× týdně.
  V režimu Kardio se frekvence pro růst nehodnotí.

## Taška do gymu
- Výchozí věci podle uživatele: kompresní triko a kraťasy, kraťasy, oversized triko, boty, trhačky, žuváky, pití,
  ručník, sluchátka, permanentka. Věci jdou přidat (max. 40 znaků, bez duplicit) a podržením odebrat.
- Zaškrtnutí patří ke dni. Další den se seznam sám „vybalí“, věci zůstanou.
- Karta je rozbalená jen v den, kdy je v plánu trénink, a jen dokud není vše sbaleno.
- Stav se ukládá do SharedPreferences (`GymBagPrefs`) jako jednoduché řetězce. Není potřeba migrace DB
  a serializace jde testovat bez Androidu.
