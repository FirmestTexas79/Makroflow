# 0097 – Zahradník, který sázel jména: Nelle, Zalévání a Noc bez měsíce

Stav: přijato (2026-10-23), větev `MULTI`, navazuje na ADR 0096

- **Co ví Nelle** (fáze 3): po třech jménech se hráč v Ozvěně zeptá Nelle na Maren. Volba: **pravda o sadu**
  (`fenn_nelle_truth` – Nelle pak nosí jablka k jezírku a má novou repliku) nebo **fasáda** (dál prodává jako včera).
  Fáze se splní, až to hráč v noci vyřídí Fennovi (`fenn_reported`, Fennova reakce podle volby).
- **Zalévání** (fáze 4): nový požadavek `WATER_DAYS` – splněný denní cíl vody ve 3 různých dnech od začátku fáze
  (`WaterDao.getWaterSinceSync`, `QuestProgression.daysAtLeast`). Tvoje pití = zalévání sadu.
- **Noc bez měsíce** (fáze 5): první oslovení Fenna v noci přehraje úvod, druhé spustí **cutscénu Bezejmenní**
  (`NamelessCutsceneView`: světlušky zhasínají, mlha, postavy bez tváří obklíčí chalupu, Fennův plamen se zmenšuje)
  a pak volbu konce:
  - **A** – zapsat Fenna do Pamětní knihy: Fenn zůstane (v noci u chalupy, lucerna v chalupě svítí i ve dne,
    každou noc dá Zlaté jablko), ale **Tobben z Ozvěny trvale zmizí** (prázdná lavička, Vessa to okomentuje).
  - **B** – Fenn odejde do Staré jabloně: jabloň v noci svítí a každou noc dá Zlaté jablko; Fenn už není.
  - **C** (skrytá: 3 pravdy Fennovi + pravda Nelle + Vhled ≥ 3) – seznam má razítko S-0, „Kontrolní výsadba –
    měření zármutku“; svět jako u B, navíc +2 Vhled a odkrytý spis Kendla (043) v Makrodexu.
- **Zlaté jablko** = nový nakopávač (`RareBoost.GOLDEN_APPLE`, 600 XP), v obchodě se neprodává (`sold`).
- Pamětník se v sadu nedává – patří hlavní lince.
- Debug: „🕯 Cutscéna: Bezejmenní“, „🕯 Fenn: skok na Noc bez měsíce“ (příznaky + fáze 5 v DB).
