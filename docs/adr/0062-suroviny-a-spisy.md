# 0062 – Suroviny a Spisy v grafice deníku

**Stav:** návrh (větev `POSTAVA`) · **Datum:** 2026-10-03

## Kontext
Postava, Denní úkoly, Příběh, Kapsa, Batoh a Makrodex mají dřevěný pixelový vzhled (docs/adr/0058, 0060). Suroviny byly nadpis a holá políčka. Spisy byly odstavce strojopisu na papíře.

## Rozhodnutí
* **Suroviny** (`JournalPages.resources`):
  * dřevěná cedule s počtem kusů a řádek „Máš X z Y druhů“;
  * každý zdroj (Z Makromonů, Ze záhonů, Semínka, Z dolů, Ze stromů, Hmyz z Dolů, Vyrobené) je pergamenový rám s barevnou stužkou: ikona, název a „3 / 7“ (zlatě, když máš všechny druhy);
  * políčka jsou po čtyřech s názvem pod sebou, prázdná jsou ztlumená;
  * klepnutí ukáže kde surovinu získat, stejně jako dřív.
* **Spisy** (`ArchivePages`), archiv Kustodiátu:
  * tmavá ocelová cedule „Spisy Kustodiátu · S-7“;
  * měřák Vhledu se 6 fialovými dílky;
  * listy a sny jsou složky spisu: štítek nahoře (LIST 3, SEN D-2), papír (listy béžové, sny modrošedé), kde a jak byl list nalezen (u snu kdo ho měl), strojopisný text, razítko DŮVĚRNÉ a dole „Začerněno: 2 místa“ nebo „✓ Plně čitelné“;
  * začerněná místa jsou opravdové černé pruhy přes text;
  * nenalezené listy a sny jsou zavřené složky „??? · Nenalezen“, takže je vidět, kolik chybí (3 / 7). Pod Vhledem 3 je připomínka, že sny se zdají až od něj.

## Důsledky
* Obsah a pravidla Vhledu se nemění, jen vzhled. Nový list nebo sen se v archivu ukáže sám (`Insight.PAGES`, `Dreams.ALL`).
