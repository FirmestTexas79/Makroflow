# 0043 – Staré nástroje za questy a kovové nástroje

**Stav:** návrh (větev `feature/workout-log`) · **Datum:** 2026-09-26

## Rozhodnutí
* **Stará sekera a Starý krumpáč už se nerozdávají automaticky**, jsou odměnou za první fázi questu (`QuestRewards`). Hráči, kteří je dostali dřív, o nic nepřijdou. Předmět se připíše jen jednou a nasadí se do prázdného slotu. Hláška NPC zazní na začátku úvodu další fáze.

  | NPC | Fáze | Odměna | Hláška (zkráceně) |
  |---|---|---|---|
  | Křoví (louka) | Příprava na cestu | Stará sekera | „Tohle do mě zarazil nějakej lovec před tebou. Když jsem ho pozdravil, tak s křikem utekl směrem do lesa.“ |
  | Král Mlsák (hory) | Audience u krále | Starý krumpáč | „Eeee… koukni se mi prosím zezadu na krk, něco mě tam tak už čtyři roky svědí. … Krumpáč?!“ |
* **Kovové nástroje** u pracovního stolu v sekci „Nástroje“. Každý stupeň má vyšší sílu a lepší pasivní bonus:

  | Nástroj | Síla | Bonus | Recept (sekera / krumpáč) | XP Výroby |
  |---|---|---|---|---|
  | Měděné | 25 | +5 % XP | 12 / 20 měď, 20 / 12 dub, 5 olivových bobulí | 80 |
  | Stříbrné | 60 | +10 % XP, +5 % dvojitý kus | 15 / 25 stříbro, 25 / 15 bříza, +10 mědi / +10 dubu, 5 modrých bobulí | 180 |
  | Zlaté | 150 | +20 % XP, +8 % dvojitý kus, AFK +4 h | 20 / 30 zlato, 30 / 20 javor, +10 stříbra / +10 břízy, 3 černozlaté bobule | 360 |
* **Ekonomika receptů**:
  * Sekera chce víc dřeva, krumpáč víc rudy: hlava z kovu, topůrko ze dřeva.
  * Každý stupeň potřebuje i suroviny předchozího stupně, takže staré žíly a stromy nepřestanou být užitečné.
  * Bobule (olej na topůrko / svačina pro kováře) drží vazbu na pěstování. Vzácnější kov chce vzácnější bobuli.
* **Postup po žebříčku**:
  * měděný nástroj těží na Lv 1 jen měď a dub; stříbro (efektivita 30) jde od Lv 4;
  * stříbrný nástroj zvládne zlato (70) od Lv 6;
  * zlatý (150) má rezervu na rychlejší těžbu;
  * Makromonovy artefakty (500) zůstávají na vrcholu.
* **Nasazení**: vyrobený nástroj se nasadí sám, pokud je silnější než ten v ruce. Zlaté nástroje prodlužují strop AFK o 4 h (`afkHours`).
