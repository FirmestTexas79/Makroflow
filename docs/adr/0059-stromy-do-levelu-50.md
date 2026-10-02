# 0059 – Stromy dovedností do levelu 50: bod za level, uzly s úrovněmi

**Stav:** návrh (větev `POSTAVA`) · **Datum:** 2026-10-02

## Kontext
* Strom měl 22 jednorázových uzlů a body chodily jen na levelech 3, 6, 10, 15, 20… Na levelu 34 to bylo 7 bodů a strom byl brzy celý.
* Předloha je IdleOn. Talenty se tam vylepšují po úrovních, bod je za každý level a bodů je méně, než strom unese, takže se hráč rozhoduje. Talenty jedné třídy podporují ostatní (synergie).

## Rozhodnutí
* **Body:** za každý level dovednosti od 2 do **50** přibude jeden bod, celkem 49 na dovednost (`SkillMath.POINT_CAP`).
  * Nad levelem 50 už body nechodí. Dál roste pasivní bonus, který zároveň končí na 50 % (level 51).
  * Na levely 51+ je místo pro „Strom II“ později.
* **Uzly s úrovněmi:** `Node.maxRank` (1–5), `cost` = body za jednu úroveň, efekt se násobí úrovní.
  * Další stupeň se otevře, když má předchozí uzel potřebnou úroveň (`needs`, výchozí = maximum) a dovednost level `minLevel`.
  * Úroveň se ukládá jako množství `skill_node_<id>`, takže stará uložená hra má své uzly na úrovni 1 a nic se nemigruje. Volné body nikdy nejsou záporné.
* **Kostra stejná pro všech šest dovedností:**
  * **Zkušenosti I / II / III:** 5 úrovní po +5 %, po +10 % a po +25 % (I za 1 bod, Lv 1; II za 1 bod, Lv 15; III za 2 body, Lv 30). Celkem +200 % XP za 20 bodů.
  * **Vrcholný uzel** (korunka): 5 bodů, Lv 50, dva efekty najednou.
* **Chytání (70 bodů):**
  * tým 2–6 (1/2/3/4/5 bodů, Lv 2/5/12/20/30);
  * Pevný hod (−2 % útěk z ballu ×5) a Mistrovský hod (−3 % ×5, Lv 25);
  * Lovec kořisti (+5 % kořist z Makromonů ×5) a Sběratel trofejí (+10 % ×5, Lv 28);
  * vrchol Legendární lovec: kořist +25 % a útěk −10 %.
* **Těžba / Kácení / Hmyz (68 bodů):**
  * Efektivita I (+10 % ×5) a II (+20 % ×5, Lv 20);
  * dvojitý kus (+2 % ×5 od Lv 8, pak +3 % ×5 od Lv 35);
  * AFK (+3 h ×4, pak +4 h ×3, Lv 25), celkem až 36 h;
  * vrchol Mistr: efektivita +50 % a XP +25 %.
  * Každá má synergii: Kovářova ruda (XP Výroby), Kompost z pilin (XP Pěstování), Návnada (XP Chytání), vždy +5 % ×3.
* **Výroba (51 bodů):**
  * Základní vybavení;
  * Dvojitá výroba (+3 % ×5, pak +5 % ×5);
  * Nástrojář: +4 % efektivita všech nástrojů ×5;
  * Učitel: +2 % XP všech ostatních dovedností ×5;
  * vrchol Velmistr dílny: dvojitá výroba +10 % a XP ostatních +10 %.
* **Pěstování (61 bodů):**
  * Nové záhony;
  * Hnojivo (+5 % ×5, pak +7 % ×5), růst je nejvýš o 75 % rychlejší;
  * Bohatá úroda (+3 % ×5, pak +5 % ×5);
  * Bylinkář: XP Výroby +4 % ×5;
  * vrchol Strážce zahrady.
* **Nové efekty:** `XpFor`, `XpOthers`, `EfficiencyAll`, `DropRate`, `CatchBonus`, `Many`.
  * Dvojitou výrobu a sklizeň teď počítá `multiChance` (pasivní bonus + vybavení + strom), stejně jako sběr.
* **Přeučení:** tlačítko Přeučit v okně stromu, za **200 mincí** a s potvrzením druhým klepnutím. Vrátí body ze všech uzlů dovednosti kromě týmu, záhonů a vybavení, aby se nerozbil tým nebo zasazené záhony.
* **Okno stromu:**
  * pod uzlem jsou dílky úrovní (zlaté = koupené) a název, dlouhý se zalomí na dva řádky;
  * široké stromy (5 sloupců) mají menší uzly;
  * uzel s úrovněmi je zelený;
  * zámek znamená chybějící předchozí uzel nebo nízký level.
  
  Karta uzlu ukazuje úroveň r/max, co uzel dává teď, co přidá další úroveň, cenu a proč nejde vylepšit.

## Důsledky
* Kdo už má uzly, má je na úrovni 1 a dostane mnohem víc bodů (Kácení Lv 34 = 33 bodů místo 7). Staré jednorázové bonusy (+15 % XP, +20 % efektivita, +12 h AFK) jsou teď první úroveň menšího kroku a zbytek si hráč dokoupí.
* `SkillsTest` hlídá:
  * body;
  * úrovně řady XP;
  * součty všech efektů;
  * staré uložené hry;
  * že každý strom unese 50–80 bodů a s dostatkem bodů jde koupit celý (žádná nesplnitelná podmínka).
