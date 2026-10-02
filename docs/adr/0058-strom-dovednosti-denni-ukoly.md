# 0058 – Karta dovednosti, grafický strom dovedností a nové Denní úkoly

**Stav:** návrh (větev `POSTAVA`) · **Datum:** 2026-10-02

## Kontext
* Na stránce Postava byl rozpis dovednosti jen text na pozadí knihy.
* Strom dovedností byl seznam karet pod rozpisem: dlouhé posouvání, žádná představa o větvích.
* Denní úkoly byly průsvitné kartičky se zaoblenými rohy. Ke zbytku deníku (dřevo, pixely) neladily.

## Rozhodnutí
* **Karta dovednosti** (`JournalPages.skillDetail`):
  * pergamen v dřevěném rámu (`WoodPanelDrawable`);
  * hlavička s ikonou v modré dlaždici a oranžovým štítkem levelu;
  * animovaný XP pruh;
  * bonusy, každý v pergamenovém políčku s hodnotou v tmavém štítku.
  
  Strom už na stránce není. Je tu jen dřevěné tlačítko **Strom dovedností** se zlatým pulzujícím štítkem `+N` volných bodů.
* **Okno stromu** (`SkillTreeOverlay`): vyjede zespodu přes deník a zavře ho klepnutí mimo, ✕ nebo tlačítko zpět. Obsahuje:
  * dřevěný rám;
  * záložky všech dovedností s ikonami (u každé volné body);
  * řádek „Dovednost · Lv · volné body“;
  * tmavou desku se stromem;
  * pergamenovou kartu vybraného uzlu s tlačítkem Odemknout.
* **Kreslení stromu** (`SkillTreeView`):
  * rozložení počítá `SkillTreeLayout`: kořen nahoře, listy zleva doprava, rodič uprostřed nad dětmi;
  * uzly jsou pixelové osmiúhelníky a barva odpovídá stavu:
    * odemčeno = zelená se zlatým obrysem;
    * jde odemknout = dřevo s pulzující zlatou září;
    * chybí body = šedomodrá;
    * zamčeno = tmavá se zámkem;
  * ikona uzlu odpovídá efektu:
    * místo v týmu = Makroball;
    * XP = hvězda;
    * efektivita = nástroj dovednosti;
    * AFK = měsíc;
    * dvojitý kus = dva krystaly;
    * záhony = záhon;
    * vybavení = pracovní stůl;
    * růst = list;
  * pod uzlem je cena v kosočtverečcích a název;
  * spoje jsou lomené pixelové čáry. Zlatá cesta mezi odemčenými uzly má běžící jiskru, cesta k uzlu, který jde odemknout, pulzuje;
  * při otevření uzly naskakují po řádcích, po odemčení z uzlu vyletí jiskry a kruh. V pozadí blikají výtrusy.
* **Denní úkoly** (`DailyPage`):
  * dřevěná cedule se sluncem a třemi kolečky postupu (zlaté = vyzvednuto, zelené = splněno);
  * odpočet do půlnoci s přesýpacími hodinami;
  * každý úkol na pergamenu v rámu obsahuje:
    * barevnou dlaždici podle skupiny (Makrosvět červená, Jídlo zelená, Pohyb modrá) s pixelovou ikonou druhu;
    * mincovní štítek odměny;
    * pixelový pruh postupu s textem;
    * dýchající dřevěné tlačítko Vyzvednout;
    * po vyzvednutí zelené razítko SPLNĚNO.
* **Ikony 12 × 12** (`TreeArt`) se kreslí z mřížek znaků v `tools/skillart/icons.py`. Ten skript `TreeArt.kt` i vygeneruje.
* Zpět zavře i menu, které leží v deníku: `WorkshopMenus.close` ho odebere z jeho vlastního rodiče.

## Důsledky
* Nový uzel stromu se rozloží sám. Nový efekt potřebuje ikonu v `SkillTreeView.icon`, nový druh denního úkolu ikonu v `DailyPage.icon`. Kompilátor si o obojí řekne (`when` přes všechny možnosti).
* `SkillTreeLayoutTest` hlídá, že každý uzel má své místo, dítě leží o řádek pod rodičem a ikony mají správnou velikost.
