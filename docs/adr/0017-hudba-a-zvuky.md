# 0017 – Hudba lokací a zvuky souboje

**Stav:** přijato · **Datum:** 2026-09-25

## Kontext
Makrosvět byl potichu. Přání: klidná hudba ve městě (probrnkávání loutny jako vítr), suchá
bubnová v horách, kapání a lehké píšťaly v jeskyních; zvuk začátku souboje, chycení a poražení.

## Rozhodnutí
* **Vlastní hudba syntetizovaná v kódu** (`tools/audiogen/gen_audio.py`): skladby jsou zapsané
  jako noty a akordy a vyrenderované malým syntezátorem – loutna (Karplus-Strong jako IIR filtr),
  flétna/píšťala (harmonické + dech + vibrato), nosová píšťala do hor, pad, rámový buben, tom,
  chrastítko, kapky vody, vítr, ptáci, dozvuk konvolucí. Žádné cizí samply → licenčně čisté,
  reprodukovatelné, verzované jako kód.
  * Město: D dur, 3/4, 72 bpm – arpeggia loutny, jemný vítr; píšťala až ve druhém průchodu.
  * Louka + Hvozd: G dur, loutna a flétna, ptáci (jedna skladba → přechod ji nepřeruší).
  * Hory: A frygická, 96 bpm, rámový buben / tom / chrastítko, suchý oud, bordun, nosová píšťala,
    skoro bez dozvuku.
  * Jeskyně: E moll, bordun, kapky laděné do pentatoniky, tiché fráze píšťaly, dlouhá ozvěna.
* **Bezešvé smyčky:** dozvuk a ozvěna přesahující konec se přičtou na začátek, závěrečný
  filtr běží kruhově; skok na švu je menší než běžný krok signálu (ověřeno měřením).
  Harmonie ověřená proti akordům (opraveny 2 disonance v melodii města).
* **OGG Vorbis v `res/raw`** (~2 MB celkem). `GameAudio`: MediaPlayer se smyčkou, prolnutí
  při změně lokace (600 ms ven / 1200 ms dovnitř), ztišení na 30 % během souboje, pauza při
  odchodu z aplikace, SoundPool pro zvuky. Přepínač zvuku v HUD mapy (GamePrefs `soundEnabled`).
* Mapování lokace → skladba a průběh prolnutí jsou čistý Kotlin (`MusicMap`) s testy.

## Důsledky
* − Syntetický zvuk nezní jako živé nástroje; skladby jde ale kdykoli upravit v kódu
  a přegenerovat, případně vyměnit za nahrávky se stejnými názvy souborů.
