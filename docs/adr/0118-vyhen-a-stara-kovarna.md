# 0118 – Výheň (minihra regionu 1) a Stará kovárna

**Princip.** Každý region má jednu minihru s vlastní měnou a vlastním menu vylepšení; platí se jen měnou té minihry
a surovinami tří povolání, která region představí (region 1: těžba, kácení, chytání hmyzu). Minihra dává hlavně
pasivní bonusy pro běžné hraní (obrana, HP, únik z ballu, odolnosti proti elementům); vybavení a strom dovedností
zůstávají pro povolání.

**Výheň (`pokemon/forge`).** Měna uhlíky; šlápnutí na měch (klepnutí na pec) + výheň hoří sama.
- 9 nekonečných vylepšení jen za uhlíky: Silnější měch, Jiskra (šance ×3, 1 − 1/(1 + 0,02 L)), Sleva
  (cena × 1/(1 + 0,1 L)), Výheň hoří sama (+1/s), Druhé ohniště (+8/s), Násobič žáru (+5 %), Popelník
  (výdělek mimo hru 10 % → ~100 %, nejvýš 24 h), Bonus za kroky (až +100 % za 10 000 kroků, strop 20 000),
  Bonus za spánek (až +100 % za 8 h, strop 9 h). Podíl kroků/spánku/popelníku roste jako 1 − 1/(1 + 0,1 L).
- Přetavení: 10 mil. → 1 000 stříbrných → 10 000 stříbrných uhlíků; vynuluje uhlíky a vylepšení za uhlíky,
  pasivní bonusy nechá a ukove artefakt: Srdce výhně (×10 uhlíků), Kovadlina předků (pasivní bonusy ×2),
  Kladivo mistra (každá úroveň Silnějšího měchu zlevní vše o 1 %, násobně). Simulace při ~10 min hraní denně:
  ~1,5 dne / ~5,5 dne / ~40 dní.
- Měny: od 100 mil. se ukazují stříbrné uhlíky (1 = 1 000 000), od 100 mil. stříbrných zlaté. Interně Double.
- Pasivní bonusy (uhlíky + rudy/dřevo/brouci, trvalé, Firebase přes user_items `forge_p_*`): Kovaný pás,
  Dubový štít (tráva), Jiskrová síť, Kalení ×5, Stříbrný amulet (země), Krystalový štít (elektřina), Žárové srdce,
  Magmový plášť (oheň), Mistrovská výheň ×5. Stropy: odolnost 40 %, obrana 50 %, HP 30 %, únik z ballu (celkem) 90 %.
  Regiony 2 a 3 pokryjí další elementy. V souboji: obrana a HP týmu při startu, odolnost násobí poškození,
  které dostává hráčův Makromon, únik z ballu se sčítá s pasivem Chytání.
- Obrazovka na výšku (`ForgeActivity`): horní polovina je pec s bublinou uhlíků, přetavením a artefakty pod ní, dolní
  polovina dřevěný panel s 3 × 3 kartami vylepšení (klepnutí = 1, podržení = co jde). Tlačítko „Pasivní bonusy“ nepoužívá
  popup: přepne spodní panel na scrollovací seznam bonusů (Ukovat u každého), tlačítko „Vylepšení výhně“ nebo Zpět vrátí karty.
- Grafika: `tools/mapgen/gen_forge.py` (pec 128 × 144 – úlová kopule z cihel zhuštěných k okrajům, saze, obruč,
  mech, komín; oheň 4 snímky; pozadí dílny) a `forge_icons.py` (ikony 20 × 20 ve stylu předloh).

**Stará kovárna (`cave/Smithy`, `gen_smithy.py`).** Domek v Horách vpravo od rozcestí (uzel `kovarna`, kreslí
`gen_mountains.py`), interiér 176 × 240: výheň s komínovým pláštěm, měch na kozlíku, kovadlina na pařezu, kalicí káď,
brus, stojan se zbraněmi, regál s lahvičkami, ponk, uhlí, ruda, ingoty, sudy, bedny. Kovář Magmor (Makromon 047)
hráče poprvé seznámí s výhní (příznak `kovarna_magmor`), pak minihru otevírá on nebo klepnutí na výheň.

**Oprava vstupu do kovárny.** Domek v Horách původně ležel přes rozcestník u vstupu (levá stěna na jeho sloupku) a uzel
`kovarna` nebyl mezi klikatelnými uzly mapy (`MakromonMapActivity.clickableNodes`), takže klepnutí na dveře nic nespustilo.
- Rozcestník se v `gen_mountains.py` přesunul vlevo od cesty (`cx − 13, cy + 11`), domek má volné okolí.
- `Smithy.DOOR_NODE` je v `clickableNodes`; šipka u dveří i klepnutí na dveře vedou do `walkInto(SMITHY, kv_vstup)`.
- Maska chůze Hor se přegenerovala (`tools/walkmask/gen_walkmask.py`): domek je pevný, před dveřmi zůstává průchod
  a z rozcestí se k nim dojde (ověřeno prohledáváním masky; ostatní uzly Hor zůstaly dosažitelné).

**Odemykání vylepšení a ceny (úprava pacingu).** Na začátku kola je k dispozici jen Silnější měch. Mezi pecí a spodním
panelem je bar „vydělané uhlíky / práh dalšího vylepšení“ (číslo uvnitř baru, nad ním název toho, co se odemkne).
Prahy v `Forge.UNLOCK` se počítají z uhlíků vydělaných celkem v tomto kole (`State.earned`, útrata je nesnižuje):
100 → Jiskra, 1 000 → Sleva, 5 000 → Výheň hoří sama, 25 000 → Druhé ohniště, 50 000 → Násobič žáru,
100 000 → Popelník, 500 000 → Bonus za kroky, 2 000 000 → Bonus za spánek (poslední práh je můj odhad). Zamčená karta je
ztmavená a píše podmínku; přetavení vylepšení zamkne a `earned` vynuluje. Starší uložení bez `earned` bere zůstatek
a vylepšení, která už mají úroveň, zůstávají odemčená.
- Nové ceny (základ × růst): měch 10 × 1,15; Jiskra 60 × 1,16; Sleva 200 × 1,17; Hoří sama 500 × 1,18; Druhé ohniště
  2 500 × 1,19; Násobič žáru 8 000 × 1,20; Popelník 30 000 × 1,22; Kroky 120 000 × 1,24; Spánek 400 000 × 1,26.
  Pozdější vylepšení jsou dražší i zdražují rychleji. Hrubá simulace (greedy nákup, ~10 min hraní denně, 4 klepnutí/s,
  7 000 kroků, 7,5 h spánku): první přetavení za ~12 dní místo ~1,5 (samotné odemykání ho posune na ~2). Ceny přetavení
  se nezměnily; je to potřeba odzkoušet na telefonu.
- Pasivní bonusy: u každé suroviny je její ikona (stejná jako ve skladu, `SkillArt.resourceIcon`) a jen potřebné
  množství; stav skladu se neukazuje, jen červená barva, když surovina chybí. Písmo v celé minihře je větší.

**Vzhled dílny a relikvie.** Pozadí (`forge_bg.png`, 400 × 560 – zeď sahá až k horní hraně) je teď ve stejném pixelovém měřítku jako pec: aplikace kreslí
pec, pozadí, relikvie a oheň ve stejném celočíselném násobku, takže se nemíchají velikosti pixelů. Pozadí má kamenné kvádry
nestejné velikosti s hranami, prasklinami a sazemi, dubový trám, cihlový komínový tah, který navazuje na komín pece,
police s lahvičkami, nářadí, dlažbu v perspektivě, světlo z ohně a kontaktní stín pod soklem (sokl stojí přesně na
řádku podlahy, `ForgeArt.BG_FLOOR_ROW`). Relikvie z přetavení jsou sprity 40 × 40 (`tools/mapgen/forge_relics.py` → `forge_relics.png`): obsidiánové Srdce výhně
rozpraskané magmatem se zlatou korunkou a plamínky, Kovadlina předků z temné oceli se zlatou intarzií a tyrkysovými runami,
Kladivo mistra se zlatými objímkami, rubínem a koženým toporem; každá má vlastní barvu záře. Pec a bublina s uhlíky jsou
uprostřed, relikvie ve velkých slotech v pravé polovině vedle pece (měřítko se volí tak, aby se vešly); klepnutí ukáže panel s názvem a bonusem (u neukované řekne, při kterém přetavení se ukove), druhé klepnutí
ho schová. Generátor: `tools/mapgen/gen_forge.py`.
