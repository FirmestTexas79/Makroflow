# 0056 – Ignileo, nový strážce rudého krystalu

**Stav:** návrh (větev `POSTAVA`) · **Datum:** 2026-10-02 · navazuje na [0014](0014-pribeh-krystalu-legenda.md)

## Kontext
Rudý krystal ve Starém dole hlídal Ignaroth, obyčejný Makromon z Makrodexu. Samuel nakreslil vlastního bosse: ohnivého lva s korunou a zlatou zbrojí (64 × 64 art px).

## Rozhodnutí
* **Strážce `BOSS_RED`** má vlastní jméno a sprite (`displayName = "IGNILEO"`, `makromon_03_ignileo.png`). Na mapě stojí před oltářem a ukazuje se v souboji.
* **Statistiky a útoky** se berou z Ignarotha (`makromonId` 003). `BattleFactory.makrodexId("IGNILEO")` vrací 003, takže typ, level a útoky fungují beze změny. V Makrodexu ani v chytání se Ignileo neobjeví, protože strážce nejde chytit.
* **Kořist** jako ohniví Makromoni (chudé plamínky).
* **Ladění:** `--es debug_special boss_red` spustí souboj se strážcem rovnou z mapy (jen debug build).
* **Přiblížení kamery** (kování, smrt, teleport) se nově omezí tak, aby u okrajů mapy nezůstal černý pruh (`clampZoom`).
