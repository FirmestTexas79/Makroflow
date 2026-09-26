# 0039 – Dobrodruhův set (první oblečení)

**Stav:** návrh (větev `feature/workout-log`) · **Datum:** 2026-09-26

## Rozhodnutí
* **První vybavení do slotů EQUIPS**: čtyři kusy, které se vyrábějí u pracovního stolu na louce.

  | Kus | Slot | Recept | Bonus |
  |---|---|---|---|
  | Dobrodruhova čepice | Přilba | 5 fragmentů energie + 3 modré bobule | +10 % XP za chytání |
  | Dobrodruhova tunika | Hrudní plát | 15 dubových polen + 10 olivových bobulí | +10 % XP za výrobu |
  | Dobrodruhovy tepláky | Kalhoty | 15 měděné rudy + 1 černozlatá bobule | +10 % XP za pěstování |
  | Dobrodruhovy pantofle | Boty | 5 stříbrné rudy + 5 březových polen | +50 k efektivitě těžby a kácení, +15 % XP za obojí |
* **Recepty odemyká** uzel „Základní vybavení“ ve stromu Výroby, který na ně ve hře už čekal. Každý kus se dá vyrobit jen jednou. Když je jeho slot prázdný, rovnou se nasadí. Výroba dává XP Výroby (60 / 90 / 110 / 150).
* **Bonusy platí, jen dokud je kus nasazený**:
  * **XP**: bonusy z vybavení se sčítají s bonusy ze stromu dovedností (IdleOn vzorec, aditivní část).
  * **Efektivita**: bonus +50 se přičte až k hotové efektivitě (síla nástroje, level, strom). Platí jen s nástrojem v ruce: bez sekery nebo krumpáče zůstává efektivita 0.
* **Oprava vzorce XP**: zisk se sčítal v plovoucí čárce, takže třeba 100 × 1,15 vyšlo 114,999… a zaokrouhlilo se na 114. Před zaokrouhlením dolů se teď přičítá drobná rezerva.
* Ocenění „Oblečený do posledního švu“ je teď splnitelné.
* Debug: `--ez seed_gear true` přidá suroviny na celý set a odemkne recepty.