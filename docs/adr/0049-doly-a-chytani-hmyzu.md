# 0049 – Doly a chytání hmyzu síťkou

**Stav:** návrh (větev `STORY`) · **Datum:** 2026-09-30

## Kontext
* Levá jeskyně v horách je Starý důl. Hráč z ní chtěl další, hlubší lokaci: staré koleje, vozíky, praskliny ve zdech navazující na přirozené jeskyně a něco živého, třeba lávový vodopád, který opravdu teče.
* Ve slotech nástrojů chyběla síťka. Chytání (IdleOn „Catching“) mělo jen chytání Makromonů, ne AFK chytání hmyzu jako těžba a kácení.

## Rozhodnutí
* **Vchod do Dolů**:
  * Je v zatáčce Starého dolu u `chodba_sever`, v čele stěny. Je to zabedněná štola s výdřevou, zdola z ní prosvítá rudý žár a v aplikaci pulzuje oranžová záře.
  * Nový uzel `vstup_doly` (26, 269), oblast klepnutí je na štole. Obrázek dolu kreslí `gen_caves.py` (`mine_door`), mapa chůze se přegenerovala.
* **Doly** (`BiomeType.MINES`, `cave/MinesMap.kt`, mapa `tools/mapgen/gen_mines.py`, 150 × 540 art px) mají tři patra spojená rampami s kolejemi:

  | Patro | Co tam je |
  |---|---|
  | Nakládací rampa | koleje a vedlejší kolej, vozík s rudou, převrácený vozík, rumpál, bedny; lucerna na sloupu, kolem které létají **jiskřivky**; **stará síťka** na rezavém háku |
  | Puklina | výdřeva končí, stěny praskají do přirozené jeskyně; fialové a rudé krystaly a žhnoucí praskliny (náznak, že Doly sahají k Rudému krystalu); **krystalové mušky** |
  | Lávový sál | lávový vodopád z pukliny ve skále, jezírko, stoka a lávová řeka přes celý sál; přes řeku vede kolejový most; **magmové mušky** nad jezírkem |

  * Nahoře jsou zamčené železné dveře. Jejich text („NEKOPEJTE HLOUBĚJ.“) je stopa Kustodiátu. S Vhledem ≥ 3 je vidět i razítko S-7.
  * Místa setkání jsou trsy žhnoucího mechu s oranžovou září.
  * Makromoni jsou horští, levely 8–14. Aréna i intro jsou jeskynní a hraje jeskynní hudba.
* **Živá láva** (`MinesArt`, `MinesFxView`):
  * Tvary lávy (vodopád, jezírko, stoka, řeka bez mostu) jsou stejné v Kotlinu i v generátoru.
  * Animace leží přesně na statické lávě z obrázku.
  * Vodopád teče dolů, jezírko vře a praská bublinami, po řece plave kůra doprava. Z lávy stoupají jiskry a nad ní dýchá žár.
  * Po lávě se chodit nedá, přes most ano (test).
* **Chytání hmyzu** (AFK jako těžba a kácení, `GatherSpot`):

  | Místo | Potřebná efektivita | Kus za | XP | Surovina |
  |---|---|---|---|---|
  | Jiskřivky u lucerny | 10 | 3 min | 10 | Jiskřivka |
  | Krystalové mušky | 30 | 6 min | 25 | Krystalová muška |
  | Magmové mušky | 70 | 12 min | 60 | Magmová muška |

  * U každého místa poletují tři mušky, každá po vlastní osmičce. Mávají křídly a zadeček bliká.
  * Klepnutím se otevře stejná tabule jako u žíly nebo stromu. XP jdou do **Chytání**, takže jedna dovednost = Makromoni i hmyz.
  * Šance na dvojitý úlovek = pasivní bonus + síťka + strom (`SkillState.multiChance`, platí teď pro všechna místa).
* **Síťky** (slot NET):

  | Síťka | Síla | Bonus | Recept |
  |---|---|---|---|
  | Stará | 10 | – | visí na háku v Dolech, sebere se jednou a sama se nasadí |
  | Měděná | 25 | +5 % XP | 12 měď, 15 dub, 8 jiskřivek, 5 olivových |
  | Stříbrná | 60 | +10 % XP, +5 % dvojitý úlovek | 18 stříbro, 18 bříza, 10 krystalových mušek, 5 modrých |
  | Zlatá | 150 | +20 % XP, +8 % dvojitý úlovek, AFK +4 h | 22 zlato, 22 javor, 10 magmových mušek, 3 černozlaté |

  * Kovové síťky se vyrábějí u pracovního stolu (Nástroje) za 80 / 180 / 360 XP Výroby.
  * Návnadou je hmyz, který se chytá síťkou o stupeň horší.
* **Strom Chytání** – nová větev síťky, nezávislá na uzlech týmu:

  | Uzel | Cena | Efekt |
  |---|---|---|
  | Lehká ruka | 1 bod | +20 % efektivita síťky |
  | Entomolog | 1 bod | +10 % XP za chytání |
  | Plná síťka | 2 body | +10 % dvojitý úlovek (nový efekt `MultiChance`) |
  | Noční lov | 2 body | +12 h AFK |

* **Deník a ocenění**:
  * Suroviny mají novou sekci „Hmyz z Dolů“ (ikony mušek ve sklenici).
  * Chytání ukazuje dvojitý úlovek, efektivitu síťky a AFK.
  * Nová ocenění: Světluška (100 jiskřivek), Duhová křídla (50 krystalových), Ohnivý tanec (25 magmových).
* **Přechod „Sestup do hlubin“** (`MineDescent`):
  * Let šikmou štolou: ubíhají dřevěné rámy výdřevy, po zemi koleje s pražci, ve stěnách se blýskají fialové a rudé krystaly a žhnou praskliny.
  * Z hloubky roste rudý žár a proti nám letí jiskry. Mapa se vymění pod zakrytou obrazovkou v 0,9 s.
  * Zpět do Starého dolu vede obyčejný jeskynní tunel.
* Příznaky `mines_visited` a `mines_net_taken` se synchronizují jako ostatní příznaky příběhu.

## Důsledky
* Uzly a tvary lávy se musí měnit v `MinesMap.kt` i `gen_mines.py` zároveň; hlídají to testy `MinesTest` a `WalkGridTest`.
* Body Chytání se teď dělí mezi tým a síťku – je to volba hráče jako v IdleOn.
