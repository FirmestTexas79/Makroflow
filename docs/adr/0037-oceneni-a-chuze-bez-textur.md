# 0037 – Ocenění Makrosvěta a chůze mimo textury

**Stav:** návrh (větev `feature/workout-log`) · **Datum:** 2026-09-26

## Rozhodnutí
* **Chůze v jeskyních a v Hvozdu** se řídí maskou podlahy, kterou kreslí přímo generátor mapy:
  * jeskyně: `gen_caves.py` ukládá `<jeskyně>_walk.png` = podlaha bez vody, balvanů, krápníků a oltáře;
  * Hvozd: `gen_forest.py` odečte všechno, co překrývají koruny stromů (`covered`), vodu, objekty setkání a kmeny ke kácení. Břehy jezírek se kreslí jen u schůdné plochy.

  Dřív se schůdnost odhadovala z barev obrázku. Proto se chodilo po stěnách jeskyní a pod korunami stromů.
* **Klepnutí na místo setkání** (`CaveMap.tapAreas`): jezírko, kapradí, kruh muchomůrek, starý dub, žíly a stromy ke kácení mají kruhovou plochu. Klepnutí do ní spustí akci uzlu, i když samotné místo není schůdné. Díky tomu klepnutí na jezírko spustí vodní setkání.
* **Ocenění** (`skills/Awards.kt`, `AwardStore.kt`): nová, pátá záložka deníku.
  * 31 medailí v šesti kategoriích: Všeobecné, Chytání, Výroba, Pěstování, Těžba a Kácení.
  * Barva medaile odpovídá obtížnosti (bronz, stříbro, zlato, platina), uvnitř je pixelový symbol. Nezískaná medaile je šedá.
  * Políčka jsou zhruba poloviční oproti Surovinám.
  * Klepnutí otevře dřevěnou ceduli: medaile v dřevěném rámečku (jako čtvereček s fajfkou), popis, pruh postupu a den získání.
  * **Úložiště**: počítadla jsou `stat_*` v `user_items`, takže se zálohují do Firebase. Splněná ocenění jsou `award_<id>` = den splnění.
  * **Starší úlovky**: chytání bere větší hodnotu z počítadla a z tabulky chycených, takže se započítají i úlovky z doby před zavedením ocenění.
  * **Kdy se nové ocenění kontroluje**: po souboji a zavření deníku, po sklizni, výrobě, výběru těžby, návratu do hry a vstupu do Hvozdu. Nové ocenění se oznámí dole na mapě.
* **Zatím nesplnitelné**: „Celý set oblečení“ a „Plné doplňky“ čekají na první předměty do těch slotů.
