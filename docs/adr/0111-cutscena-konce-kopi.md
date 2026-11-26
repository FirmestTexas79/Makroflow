# 0111 – Cutscény konců: KONEC I · Kopí

## Kontext
Konce (0110) se dohrávaly jen textem po stránkách. Samuel chce ke každému konci plnohodnotnou cutscénu („vyhraj si
s tím víc než s čímkoliv“). Začíná se prvním koncem – Kopí.

## Rozhodnutí
- **Předrenderované video z kódu** (`tools/cutscene/`): `cine.py` je malý střihový stůl – záběr = funkce času → pixelový
  obraz 360 × 780 (1 px = 1 px mapy), ×2 bez vyhlazení na 720 × 1560, titulky psacím strojem (DejaVu Serif, česká
  diakritika), ffmpeg H.264 (`-preset veryfast -crf 27 -tune animation`). Jediný zdroj pravdy pro hru i náhled.
  Proč video, ne živý pohled: rozpad Trpitele na desítky tisíc částic a 3D pózy z raymarchingu by v Kotlinu stály
  měsíce práce a výkon telefonu; video ~9 MB na konec je přijatelné.
- **Pózy Trpitele** (`trpitel_poses.py`, `gen_trpitel.POSE`): stejný SDF model jako ve hře – `release` 0–3 (pouští
  okraj rány, pak obě ruce z ratiště), `smile` 0–1 (spodní víčka zvednou šest očí do úsměvu), detail hlavy K = 4,6.
- **Záběry** (`ending_spear.py`, 87 s): výstup Středem kořenů ke světlu → Kmen, hráč jde po lávce → detail: oči se
  usmějí → ruce pouští jedna po druhé (obláčky popela) → tělo praskne, rozžhaví se a rozpadne na popel, který stoupá,
  zůstane kopí → prázdná rána, malý hráč u ratiště → sonda Řádu zblízka, Sept: „Nástupnictví potvrzeno…“ – „Jak že se
  jmenujete?“ → nahoře: Brann podá chleba cizímu, Gudwin vítá nového poutníka, tabule v čekárně překlopí 213 → 214 →
  Spirra se stočí hráči u nohy a usne, kamera odjíždí.
- **Zvuk** (ElevenLabs, `mix_kopi.sh`): hudba na míru (87 s, violoncello, sbor, tichá pasáž pro rádio, hrací strojek),
  Septův hlas (eleven_v4, filtr rádia), efekty – puštění ruky ×3, popel stoupá, šum rádia, překlopení tabule.
- **Hra**: `EndingScene.show(…, videoRes)` přehraje `res/raw/ending_kopi.mp4` přes celou obrazovku (klepnutí ukáže
  „přeskočit“, druhé přeskočí), hudba hry je po dobu videa pozastavená, pak titulek KONEC I. Debug: Cutscény a videa →
  Konec I–IV (bez příznaků a bez návratu).

## Důsledky
Další konce stejným postupem (nový skript záběrů + mix). Změna mapy nebo modelu Trpitele = znovu vyrenderovat video.
