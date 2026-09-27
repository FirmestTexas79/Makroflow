# 0046 – Zapomenutý háj: skrytá lokace a tajná linka příběhu

**Stav:** návrh (větev `feature/workout-log`) · **Datum:** 2026-09-27

## Kontext
Hvozd je velký a po ADR 0045 má hlavní příběh (Mydrus, hniloba, Srdce Hvozdu). Chceme v něm skrytou lokaci s tajnou linkou, která příběhu přidá další úroveň: kdo byl Soulord, proč byla Brána světů zapečetěná a co čeká za ní.

## Nová vrstva příběhu
* **Soulord byl Elderan.** Elderan byl poslední Strážce brány a Mydrusův učitel. Svou duši zaklel do kořenů Starého dubu, aby hlídal Srdce Hvozdu. Popel ho zkazil v Soulorda. Hráč tedy ve Hvozdu bojoval s duchem strážce, aniž to věděl.
* **Rudá hniloba nebyl Drakiřin popel.**
  * Drakirra je strážkyně brány, ne viník. Spala na vrcholu hor, aby držela pečeť.
  * Za Branou světů leží **Popelavý kraj** (sopka z výhledu na průsmyku) a vládne tam **Pán popela**, „Ten, který spaluje“.
  * Když hráč probudil draka, pečeť povolila a popel prošel po Drakiřině stopě.
* **Hráč bránu právě otevřel.** Runy brány říkají „KDO OTEVŘE, AŤ HLÍDÁ. POPEL ČEKÁ ZA MRAKY.“ To navádí přímo do Regionu 2.
* Deník naznačuje i budoucí volbu: Srdce jde z lůžka zase vyjmout a bránu zavřít, ale pečeť už nikdy nebude celá.

## Rozhodnutí
* **Vstup:**
  * Na západě Hvozdu (uzel `skryta_stezka`, 96×312) je v úzké uličce u trní mezera.
  * Dokud stojí Soulord, je tu jen „neprostupné trní“. Po jeho porážce v trní svítí modré houby (jediná viditelná stopa) a klepnutí hráče provede mezerou do háje.
  * Slovní stopu dává Mydrus v rozloučení: modré houby rostou jen tam, kde odpočívá duše.
* **Lokace `BiomeType.HIDDEN_GROVE`** (`cave/GroveMap.kt`, mapa 160×300 z `tools/mapgen/gen_grove.py`):
  * měsícem zalitá mýtina v tmavém lese;
  * kamenný kruh s menhiry kolem **Studánky hvězd** (odráží cizí nebe s rudým bodem);
  * tři kameny s vytesanými svítícími obrazy;
  * prastarý tis s modrými houbami, oltář s lucernami a hrob strážce;
  * vstup je tunelem z trní.
  * Divocí Makromoni tu nejsou. Hraje jeskynní hudba, přechod je listový.
* **Nové obrázky** (`tools/mapgen/gen_grove_art.py`):
  * **Portrét Elderana:** duch starce z rodu Mycitů se svítícím parožím, vousem z mechu a světla a holí s krystalem.
  * **Tři obrazy do kamenů** (`mural_1..3`):
    1. draci stavějí bránu;
    2. druidi pečetí bránu Srdcem a drak usíná na hoře s krystaly;
    3. Pán popela za branou a strážce, který se vpíjí do kořenů.
* **Tajný quest `secret_grove`** (v deníku „✦ ZAPOMENUTÝ HÁJ“, objeví se až po nalezení háje):

  | # | Fáze | Úkol | Typ |
  |---|---|---|---|
  | 0 | Kamenný kruh | prohlédnout tři kameny s obrazy | VISIT_NODE |
  | 1 | Noční bdění | přijít k oltáři **v noci** (21:00–5:00 místního času) | STORY_FLAG `grove_vigil` |
  | 2 | Dary mrtvým | 3 malé dušičky, 3 vodní perly, 1 živý list | DELIVER_ITEMS |
  | 3 | Hranice lesa | ujít dnes 10 000 kroků | WALK_STEPS |
  | 4 | Poslední slovo strážce | pustit duši u hrobu | STORY_FLAG `grove_released` |

  * Mluvčí je v prvních dvou fázích „Šepot“; jméno Elderan se dozvíš, až si vzpomene.
  * Dušičky padají z duchů, kteří v noci bloudí krajem, takže noční motiv prochází celou linkou.
* **Na mapě háje:**
  * obrazové kameny dýchají tyrkysovou září a nad studánkou a u oltáře poletují světlušky;
  * v noci se za oltářem slabě rýsuje duch, po bdění je jasný a pohupuje se;
  * vysvobození:
    1. duch dopluje nad hrob;
    2. vztyčí se sloup světla;
    3. duch vystoupá a rozplyne se;
    4. háj zazáří;
  * potom nad hrobem zůstane jen klidná záře.
* **Odměna:** **Deník strážce Elderana**, klíčový předmět s vlastní ikonou. V batohu se čte: pět stránek o Bráně, pečeti, Pánu popela, runách a poslední zápis pro Mydruse.
* **Další vazby:**
  * Tabule u Brány světů po vysvobození ukazuje přeložené runy.
  * Mydrus na mýtině jednou promluví o svém učiteli (`grove_mydrus_told`).
* **Postup se synchronizuje:** `grove_found`, `grove_vigil`, `grove_released` a `grove_mydrus_told` jsou mezi příznaky příběhu (`StoryProgress`).
* **Debug** (podržení deníku):
  * Soulord poražen (otevře háj);
  * bdění splněno;
  * reset háje.

## Důsledky
* Region 2 dostal antagonistu (Pán popela) a důvod, proč za branou hlídat. Drakirra se může vrátit jako spojenec.
* Testy:
  * `SecretGroveTest`: vstup jen po uzdravení, noc, skrytá větev lesa, háj a jeho uzly, obrazy, deník a příznaky.
  * `SecretGroveQuestTest`: struktura questu, odměna a stopa v rozloučení Mydruse.
  * `WalkGridTest`: maska háje a dosažitelnost uzlu v trní.
