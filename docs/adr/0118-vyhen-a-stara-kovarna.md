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
- Obrazovka na šířku (`ForgeActivity`): vlevo 3 × 3 karty (klepnutí = 1, podržení = co jde), vpravo pec,
  bublina s uhlíky, přetavení, artefakty pod pecí, menu pasivních bonusů.
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
