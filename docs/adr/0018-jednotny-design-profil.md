# 0018 – Jednotný design: profil, barvy Material 3, drawer a názvosloví

**Stav:** přijato · **Datum:** 2026-09-25

## Kontext
Revize všech obrazovek přímo na telefonu (screenshoty přes ADB) ukázala, že profil – první
stránka projektu – nesedí k ostatním (volná pole bez karet, nejasný kruh životního stylu,
zdvojený nadpis „MOJE OBVODY“, ostré oranžové/červené tlačítko přes celou šířku) a že
v aplikaci prosvítá výchozí fialová Material 3 (výběr v draweru, přepínač pohlaví).
Na několika místech zůstalo Pokémon názvosloví (Pokédex, Kanto, Poké-kapsa, „Chyť je všechny“).

## Rozhodnutí
* **Téma:** doplněny všechny role barev M3 (container, surface container, outline, error)
  z palety aplikace → žádné fialové výchozí barvy v komponentách a dialozích.
* **Profil** ve stejném jazyce jako ostatní stránky: hlavička s odznakem, tmavá souhrnná karta
  (jméno, údaje, cíl, dnešní kcal a makra ze stejného výpočtu jako Dashboard), sekce s oddělovači,
  světlé karty s obrysem; pohlaví jako značkový přepínač; váha/výška/věk vedle sebe; cíl
  CUT/MAINTAIN/BULK s českým podtitulkem; životní styl jako tři volby s ikonou a popisem;
  účet s obrysovým „Odhlásit se“ a nenápadným textovým „Smazat účet“. Styly v `styles.xml`
  (`Profile*`) jsou připravené k použití i jinde.
* **Drawer:** vlastní linkové ikony, české názvy (Úspěchy, Makrodex, Kapsa a batoh),
  zvýraznění jen vybrané položky; vývojářské nástroje a mazání úspěchů jen v debug buildu.
* **Názvosloví:** Makrodex, MAKROSVĚT, „Makromoni“, Makroball – pryč s Pokémon značkou
  (i z ohledem na ochranné známky u diplomové práce).
