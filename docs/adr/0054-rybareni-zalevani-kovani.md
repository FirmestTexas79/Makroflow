# 0054 – Rybaření u vody, zalévání záhonů a kování u stolu

**Stav:** návrh (větev `POSTAVA`) · **Datum:** 2026-10-02

## Kontext
Postava ze Sunnyside (docs/adr/0051) umí animace `reeling`, `caught`, `watering` a `hamering`, které hra nepoužívala. Setkání u vody, zahrada i výroba proběhly okamžitě, bez jediného pohybu postavy.

## Rozhodnutí
* **Nové pásy** (`gen_hero.py`):
  * `reeling` a `caught` mají vyšší snímek, 64 px místo 40, protože vlasec a šplouchnutí jsou pod nohama. Výška pásu je v `hero.json` jako `"h"` a `HeroAnims.Spec.height(key)` ji čte;
  * `MovementEngine` při vyšším snímku prodlouží view postavy dolů, hlava zůstane na místě;
  * `hammer` je jen úder ze snímků 15–21 tagu `hamering` (náraz na 4. snímku);
  * `watering`, rybaření i kladivo jsou v souboru nakreslené čelem doprava a doleva se zrcadlí.
* **Rybaření u vody** – setkání na uzlech `voda` a `jezirko_*`:
  * postava nahodí ke straně, kde je voda. Strana se pozná z modrých pixelů obrázku mapy vlevo a vpravo od nohou;
  * 2–3× zatáhne za prut, nad hlavou vyskočí bublina „!“ a telefon cvakne;
  * postava zabere (`caught`) a teprve pak naskočí vodní souboj s úvodní scénou u vody;
  * v 10 % nic nezabere: tři nahození a hláška „Nic nezabralo“;
  * po celou dobu mapa nebere dotyky.
* **Zalévání záhonů** (`Garden`, pokryto testy):
  * zalít jde jednou za čtvrtinu doby růstu, počítá se od zasazení nebo posledního zalití;
  * zalití posune růst o 15 % celé doby. Kdo zalévá pokaždé, sklidí zhruba o třetinu dřív;
  * čas posledního zalití je v `garden_water_<i>`. Posun růstu se uloží jako dřívější zasazení, takže formát záhonu se nemění;
  * žíznivý záhon má nad cedulkou poskakující modrou kapku;
  * klepnutí na něj spustí animaci zalévání;
  * na nežíznivém záhonu ukáže hláška i čas do dalšího zalití.
* **Kování u pracovního stolu** (Makroballs i vybavení):
  * suroviny se odečtou hned a animace přijde až potom;
  * postava třikrát udeří kladivem. Při každém nárazu vyletí jiskry, telefon cvakne a kamera se přiblíží (1,35×, 1,75×, 2,2×) na místo mezi postavou a stolem;
  * pak ze stolu vyskočí ikona vyrobeného předmětu se září a točícími se paprsky za sebou;
  * předmět odletí k postavě a kamera se vrátí. Teprve potom přijde hláška o výrobě.
