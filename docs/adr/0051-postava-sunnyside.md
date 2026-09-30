# 0051 – Nová postava hráče ze Sunnyside World a kostlivý Vendelín

**Stav:** návrh (větev `POSTAVA`) · **Datum:** 2026-09-30

## Kontext
* Hráč měl sprite Reda z Pokémonů. Dodal balík animací Sunnyside World (`.aseprite`, 310 snímků 96 × 64, 34 animací: idle, walk, run, axe, mining, casting, watering, dig…).
* Havíř Vendelín byl nejdřív malá „omalovánka“. Pak chibi kostlivec (styl Tinybones), ten ale vyšel příliš velký.

## Rozhodnutí
* **Rozbalení `.aseprite`** – vlastní čtečka formátu `tools/sprites/ase_extract.py`:
  * umí vrstvy, cely raw / linked / zlib, tagy a délky snímků;
  * vynechá pozadí a vodítka;
  * `--hair` vybere jeden ze 6 účesů.
* **Postava hráče** – `tools/sprites/gen_hero.py` z `tools/sprites/sunnyside_human.aseprite`:
  * vyrobí pásy `assets/hero/<animace>_<směr>.png` (snímek 64 × 40 px, pata na řádku 38) a `hero.json`;
  * pohyb (idle / walk / run) má 8 směrů, sw / w / ne jsou zrcadlené;
  * práce (axe, mining, casting, doing, watering, dig) má směr e / w;
  * pozor, v souboru kouká boční chůze doprava, práce doleva a tag „ne“ ve skutečnosti nahoru doleva.
* **MovementEngine**:
  * snímky přepíná vlastní časovač (33 ms) podle délek z Aseprite;
  * směr chůze se bere z úhlu pohybu (8 směrů, `HeroAnims.dir8`);
  * dvojklik = běh;
  * u sběrného místa postava opravdu pracuje: kácení = axe, těžba = mining, chytání hmyzu = casting, čelem k místu. Po odchodu znovu stojí.
* **Velikost**: 1 px spritu = 2 dp, zaokrouhleno na celé pixely zařízení, bez vyhlazení.
* Portrét v deníku (stránka Postava) je první snímek stání. Drawables `ash_*` se už nepoužívají.
* **Vendelín**:
  * kostlivý chibi havíř ve stylu Sunnyside: přilba s lampičkou, žhnoucí důlky, modrozelený kabát, nášivka S-7, kahan s jiskřivkami;
  * na mapě má 4 snímky idle;
  * je vykreslený o čtvrtinu menší než art px mapy.

## Důsledky
* Účes jde změnit jen přegenerováním pásů (`--hair`). Výběr účesu ve hře by znamenal pásy pro všech 6 účesů.
