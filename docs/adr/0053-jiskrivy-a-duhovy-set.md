# 0053 – Jiskřivý a Duhový set

**Stav:** návrh (větev `POSTAVA`) · **Datum:** 2026-09-30

## Kontext
* Jediná sada brnění byl Dobrodruhův set (docs/adr/0039) z bobulí, dřeva a rudy.
* Doly (docs/adr/0049) přinesly tři druhy hmyzu – jiskřivky, krystalové a magmové mušky – a zlato se dá těžit v Mechové jeskyni. Chybělo, na co je dlouhodobě utrácet.

## Rozhodnutí
* **Jiskřivý set** (stříbro + jiskřivky + kořist z Makromonů):

  | Kus | Recept | Bonus |
  |---|---|---|
  | Jiskřivá přilba | stříbro 40, jiskřivky 24, chudé plamínky 8 | +30 % XP těžba, +80 efektivita těžby |
  | Jiskřivý kyrys | stříbro 60, jiskřivky 32, dušičky 10 | +30 % XP hmyz, +10 % dvojitý úlovek |
  | Jiskřivé nohavice | stříbro 50, jiskřivky 24, vodní perly 10, březová polena 20 | +30 % XP kácení, +80 efektivita kácení |
  | Jiskřivé boty | stříbro 60, jiskřivky 30, **krystalové mušky 24**, suché listy 12 | +240 efektivita a +30 % XP těžba / kácení / hmyz |

  **Celá sada:** těžba, kácení a chytání hmyzu běží o 1 h déle bez hráče.
* **Duhový set** (zlato + krystalové mušky):

  | Kus | Recept | Bonus |
  |---|---|---|
  | Duhová přilba | zlato 50, krystalové mušky 28, pixie prach 8 | +50 % XP těžba, +12 % dvojitá ruda, +4 h AFK |
  | Duhový kyrys | zlato 70, krystalové mušky 36, dušičky 12 | +50 % XP hmyz, +16 % dvojitý úlovek, +4 h AFK |
  | Duhové nohavice | zlato 60, krystalové mušky 28, vodní perly 12, javorová polena 30 | +50 % XP kácení, +12 % dvojitá polena, +4 h AFK |
  | Magmové boty | zlato 60, **magmové mušky 36**, koule magmatu 4, dračí šupiny 4 | +450 efektivita a +45 % XP těžba / kácení / hmyz, kořist +30 % |

  **Celá sada:** +10 % XP a +5 % dvojitý kus za těžbu, kácení i chytání hmyzu.
* **Vyvážení** – po prvním návrhu dostaly kusy dvojnásobné bonusy i ceny, boty trojnásobné. Kořist z Makromonů (vzácné dropy) má i u bot jen dvojnásobek. Důvod: síla nejlepšího vyrobitelného nástroje sama na vzácná místa nestačí, efektivita z brnění to má dorovnat.
* **Bonus sady** (`GearSet`) platí jen s nasazenými všemi čtyřmi kusy a přičítá se v `SkillState` k XP, dvojitému kusu a AFK hodinám. Dobrodruhův set bonus sady nemá.
* **Výroba** – u pracovního stolu na louce, pod stejným uzlem stromu Výroby („Základní vybavení“). Každá sada má v dílně vlastní oddíl a pod názvem popis bonusu celé sady.
* **Ikony** 16 × 16 px (`GearArt.ARMOR`), inspirované sadou pixelových brnění od uživatele:
  * stříbrné kusy mají jiskřivky v hledí, na prsou a na kolenou;
  * zlaté kusy mají duhové krystaly;
  * magmové boty jsou z černého krunýře s ohnivými prasklinami a zlatým lemem.
* Na mapě Zóna 1 jsou názvy lokací větší (46 místo 30 jednotek plátna).
