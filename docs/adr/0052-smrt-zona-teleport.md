# 0052 – Smrt postavy, mapa „Zóna 1“ v deníku a teleport

**Stav:** návrh (větev `POSTAVA`) · **Datum:** 2026-09-30

## Kontext
* Když padl celý tým, souboj jen napsal „FAINTED!“, zavřel se a hráč stál na mapě, jako by se nic nestalo.
* Deník neměl mapu. Hráč neviděl, jak na sebe lokace navazují, a všude musel pěšky.
* Nová postava ze Sunnyside World (docs/adr/0051) má i animace death, jump a hurt.

## Rozhodnutí
* **Jednorázové animace** – `gen_hero.py` vyrobí i `death_s`, `jump_s` a `hurt_s`:
  * jsou nakreslené zepředu, takže se nezrcadlí (`HeroAnims.ONCE`);
  * po doběhnutí zůstane poslední snímek (`frameOnce`);
  * `MovementEngine.playOnce(anim, slow)` je přehraje (volitelně zpomaleně), `resetIdle()` vrátí stání.
* **Smrt postavy** – padne-li celý tým (mimo souboj s legendou), `PokemonBattleView` nastaví `Whiteout.PENDING_KEY`. Po zavření souboje mapa:
  * přiblíží kameru na postavu (svět se zvětší 2,4× kolem hráče);
  * přehraje zpomalenou animaci smrti;
  * zatmí obrazovku;
  * hráč se probudí na prahu domova ve městě (`prah_domova`).
  
  Příznak přežije i zavření aplikace a scéna se přehraje při dalším otevření mapy.
* **Mapa Zóna 1** – nová záložka deníku **Mapa**. Na její stránce je vlevo svislý sloupec záložek zón jako „WORLD 1…7“ v Idleonu (zatím jen **Zóna 1**, vybraná je zelená) a vpravo mapa zóny. Rozvržení vyrábí `tools/mapgen/gen_zone.py`:
  * tvar lokace = maska chůze rozšířená o okolí, vyříznutá z obrázku lokace (lehká sépie, inkoustový obrys). Venkovní mapy berou jen část viditelnou na telefonu;
  * vchody a východy leží na mapě přesně tam, kde jsou uzly ve hře. Spoje vedou jako čárkované křivky: z každého konce vyjedou směrem, kterým se z lokace odchází;
  * `zone1.json` nese obdélníky, převod pozice ve hře na plátno, spoje, pozice NPC a jmen. `ZoneOneTest` hlídá, že souřadnice sedí s `BiomeRegistry` a `CaveMap`;
  * **hlavy NPC** jsou kulaté odznaky na místě NPC: Gudwin, keřík, král Mlsák, Mydrus, Vendelín, Elderan;
  * hráč je vidět tam, kde právě stojí (hlava v pulzujícím kroužku);
  * neobjevené lokace jsou jen obrys v mlze s „???“;
  * **tajné lokace (Zapomenutý háj) se kreslí jen tehdy, když v nich hráč stojí, a to i se svým spojem. Platí pro všechny budoucí tajné lokace.**
* **Objevené lokace** – ukládají se jako příběhový příznak `zone_seen_<BIOM>`, který se synchronizuje. U starších uložených her se objevené lokace odvodí z příznaků a rozběhnutých questů (`ZoneOne.inferSeen`).
* **Teleport** – klepnutím na objevenou lokaci se otevře dřevěná nabídka. Teleport hlídá stejné zámky jako chůze:
  * lokace za horami potřebují dnešní kroky;
  * Hvozd potřebuje splněné úkoly;
  * tajné lokace a místo, kde hráč stojí, nejdou.
  
  Animace: deník se zavře, kamera se přiblíží, postava se přikrčí a vyskočí nahoru z obrazovky s pixelovým prachem od nohou. Pak přijde tma a v cíli postava spadne shora, při dopadu se zvedne prach a postava se krátce přikrčí. Cílem je uzel `ZoneOne.ARRIVAL` (rozcestí nebo vchod lokace).

## Důsledky
* Nová lokace v zóně = zápis do `ZoneOne` (jméno, příjezd) a do `gen_zone.py` (umístění, uzly spojů, NPC). Pak znovu spustit generátor.
* Když se posune uzel ve hře, generátor je potřeba spustit znovu, jinak `ZoneOneTest` spadne.
