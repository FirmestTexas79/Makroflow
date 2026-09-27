# 0044 – Nebeský průsmyk, Brána světů a synchronizace postupu příběhu

**Stav:** návrh (větev `feature/workout-log`) · **Datum:** 2026-09-27

## Kontext
Po souboji s legendou zůstala brána za svatyní otevřená, ale vedla jen k hlášce „cesta se teprve chystá“. Dohodnutý příběh:
1. Drakirra po souboji odletí nad Hvozd.
2. Brána za svatyní vede do **Nebeského průsmyku** s výhledem na nový kraj a **Branou světů**.
3. Brána je zapečetěná, dokud do ní hráč nevloží **Srdce Hvozdu** z lesního příběhu (druid Mydrus, Starý dub). Ten přijde později.

Postup legendy (strážci, krystaly, legenda) přitom žil jen v `GamePrefs`. Po přeinstalaci nebo na jiném telefonu se ztratil, i když questy, Makromoni a předměty se přes Firebase synchronizují.

## Rozhodnutí
* **Nová lokace `BiomeType.SKY_PASS`** (`cave/SkyPass.kt`, mapa 160×440 z `tools/mapgen/gen_skypass.py`, maska chůze `assets/walk/sky_pass.txt`).
  * Serpentina z tmavého oblouku u svatyně nahoru na vyhlídkovou plošinu.
  * Nahoře je výhled:
    * svítání a hvězdy;
    * létající ostrovy s vodopády, sopka s kouřem;
    * moře mraků a v údolí terasová pole, řeka a město s arénou.
  * Divocí Makromoni tu nejsou. Hudba, aréna a pravidla jsou horské.
* **Vstup:** klepnutí na svatyni ve stavu *GateOpen* vede do průsmyku (přechod mlhou). Uzel `vstup_ze_svatyne` vrací na vrchol (`peak`).
* **Místa v průsmyku:**

  | Uzel | Co dělá |
  |---|---|
  | `brana_svetu` | Dřevěná tabule s detailem brány (výřez z mapy), lore a „Chybí: Srdce Hvozdu“. Nastaví `world_gate_seen`. |
  | `drapy` | Rýhy po drápech a šupina: Drakirra odletěla dolů nad Hvozd. |
  | `muzik` | Mužík poutníků s vyrytým listem (odbočka ze serpentiny). |
  | `vyhlidka`, `plosina` | Kamera vyjede k údolí, ukáže kraj a vrátí se k hráči. |
* **Grafika na živo** (`cave/SkyPassArt.kt`, čistá logika):
  * v prstenci se točí třpytivý závoj (12 snímků ve smyčce);
  * runy dýchají tyrkysovou září;
  * lůžko pro Srdce slabě pulzuje zeleně;
  * přes moře mraků plují tři obláčky.
* **Výhled:**
  * Při *první* návštěvě začne kamera nahoře nad údolím, ještě pod otevírajícím se přechodem, a pomalu sjede k hráči. Hráč tak nový kraj uvidí hned.
  * Během jízdy kamery se klepnutí ignorují.
* **Hláška legendy:** „FLEW AWAY OVER THE FOREST!“ místo „over the peaks“, aby navazovala na lesní příběh.
* **Synchronizace příběhu** (`story/StoryProgress.kt` je čistá logika, `story/StoryFlags.kt` je Android):
  * Každý příznak se zapisuje do `GamePrefs` a zároveň jako předmět `story_<klíč>` = 1 do `user_items`, které už Firebase synchronizuje.
  * Zapisuje se přes `StoryFlags.set`. Předmět se ukládá na pozadí.
  * Synchronizované klíče:
    * `boss_defeated_*`, `crystal_*`;
    * `crystals_placed`, `legend_faced`;
    * `sky_pass_visited`, `world_gate_seen`, `forest_heart_placed`.
  * `StoryFlags.sync` sloučí obě strany jako sjednocení (postup se jen přidává). Volá se:
    * při startu mapy;
    * po stažení dat z cloudu (`syncCloudDataToLocal`).
  * Díky tomu se doplní i postup hráčů, kteří ho získali před touto změnou.
  * `story_*` jsou interní předměty a v batohu nejsou vidět.
  * Debug reset legendy maže příznaky všude, jinak by je sync hned vrátil.

## Důsledky
* Region 2 se připojí přes `forest_heart_placed`. Tabule u brány už má text pro otevřený stav.
* Nové příznaky příběhu stačí přidat do `StoryProgress.STORY_KEYS` a zapisovat přes `StoryFlags.set`.
* Testy: `SkyPassTest` (graf, klepací oblasti, závoj, obláčky), `StoryProgressTest` (sloučení, přeinstalace, zpětné doplnění) a maska průsmyku ve `WalkGridTest` (všechny uzly dosažitelné).
