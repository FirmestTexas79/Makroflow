# 0035 – Těžba a kácení (AFK) a vybavení postavy

**Stav:** návrh (větev `feature/workout-log`) · **Datum:** 2026-09-26

## Kontext
Suroviny zatím šly získat jen z Makromonů a ze záhonů. Svět 1 proto dostal dvě dovednosti ve stylu IdleOn: **Těžbu** (krumpáč) a **Kácení** (sekera).
* Běží i když hráč Makrosvět zavře (AFK).
* Potřebují vybavení, takže okno postavy dostalo inventář vybavení.

## Rozhodnutí
* **Vybavení** (`skills/Gathering.kt`):
  * na stránce Postava je mezi hlavičkou a dovednostmi panel se záložkami EQUIPS / ACCESS / TOOLS;
  * každá záložka má 4 sloty:
    * EQUIPS: přilba, hrudní plát, kalhoty, boty;
    * ACCESS: talisman, přívěsek, 2 prsteny;
    * TOOLS: sekera, krumpáč, síťka a „?“ (přijde později);
  * prázdný slot ukazuje světlou siluetu;
  * klepnutí na slot otevře dřevěné menu s předměty, které se do slotu hodí, a tlačítky Nasadit / Sundat;
  * nasazení se ukládá v `user_items` jako `equip_<slot>` = kód předmětu, takže se zálohuje do Firebase.
* **Startovní nástroje**: Stará sekera a Starý krumpáč (síla 10) přijdou jednou při otevření mapy a hned se nasadí. Později se z nich stane odměna za úkol.
* **Místa**: klepnutí na místo ukáže dřevěnou tabuli s potřebnou a vlastní efektivitou, časem na kus, XP, šancí na dvojitý kus a stropem AFK.

  | Místo | Potřebná efektivita | Čas na kus při potřebné efektivitě | XP |
  |---|---|---|---|
  | Měď | 10 | 3 min | 10 |
  | Stříbro | 30 | 6 min | 25 |
  | Zlato | 70 | 12 min | 60 |
  | Dub | 10 | 3 min | 10 |
  | Bříza | 30 | 6 min | 25 |
  | Javor | 70 | 12 min | 60 |

  * Rudné žíly jsou v údolí hor, stromy na louce (`GatherLayout`).
  * Balvany a kmeny jsou v mapě chůze neprůchozí; hráč stojí pod nimi a otočí se k nim.
* **Efektivita** = (síla nástroje + 2 × (level − 1)) × (1 + bonus ze stromu). Pod potřebnou efektivitou to nejde. Čas na kus = základ × potřebná / tvoje, nejvýš 10× rychleji.
* **AFK**:
  * probíhá vždy jen jedna činnost (`gather_spot` a `gather_since` v `user_items`);
  * hotové kusy se počítají od posledního vybrání a zbytek rozpracovaného kusu se nezahazuje;
  * počítá se nejvýš 12 h, strom přidá dalších 12 h;
  * každý kus má šanci (level − 1) % na dvojnásobek (multiore / multilog);
  * odchod od místa po mapě těžbu ukončí a vybere, jako v IdleOn musí postava stát u místa;
  * zavření Makrosvěta těžbu neukončí.
* **Poslední místo**: mapa si při odchodu uloží lokaci, pozici a čas. Při dalším otevření hráče vrátí tam, kde byl. Pokud byl pryč aspoň minutu a něco se vytěžilo, ukáže dřevěnou ceduli „Vítej zpět!“ s dobou nepřítomnosti, výnosem a XP.
* Nad místem, kde se právě těží, je cedulka s přesýpacími hodinami, počtem hotových kusů a časem do dalšího.
* **Stromy dovedností**:

  | Dovednost | Uzly |
  |---|---|
  | Těžba | +20 % efektivita krumpáče; +15 % XP; Dlouhá směna +12 h AFK |
  | Kácení | +20 % efektivita sekery; +15 % XP; Celodenní šichta +12 h AFK |
* **Suroviny** přibyly v sekcích „Z dolů“ (měděná, stříbrná a zlatá ruda) a „Ze stromů“ (dubové, březové a javorové poleno).
* **Oprava**: ikony v deníku a menu se zvětšují celočíselným násobkem předem. Při necelém měřítku bez vyhlazení se ztrácela horní řada pixelů.

## Důsledky
* Lepší nástroje (měděná sekera…) a vybavení do EQUIPS a ACCESS stačí přidat do `Gear` s novým kódem, efektivita je vezme automaticky.
