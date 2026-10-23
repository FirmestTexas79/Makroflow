# 0098 – Cutscény prvního regionu: Oči a Modré světlo v trní

Stav: přijato (2026-10-23), větev `MULTI`, navazuje na ADR 0096 (CutsceneView)

- **CutsceneView** umí střih na jinou mapu (`worldAt`), otřes kamery (`shake`) a kamera smí svisle přejet okraj mapy
  o výšku černého pruhu – postava u okraje mapy (Fenn u chalupy) už nezmizí pod titulky.
- **„Oči“** (`DrakirraCutsceneView`) po souboji s legendou: krystaly v lůžkách pošlou světlo do prázdných očnic,
  Drakirra po staletích vidí – nejdřív hráče, pak malou béžovou veverku vedle něj. „Nebyl to vztek. Byl to strach.“
  Řev (otřes, prach), hráče to odhodí, Spirra se schová a Drakirra vzlétne. Střih na Nebeský průsmyk: tmavá silueta
  s modrým a rudým okem přeletí Bránu světů, stočí se dolů nad Hvozd a za ní padá žhavý popel (začátek Rudé hniloby).
  Spouští se jednou: souboj nastaví `cutscene_drakirra_pending`, mapa ji přehraje po návratu ze souboje.
- **„Modré světlo v trní“** (`GroveCutsceneView`) po Mydrusově rozloučení s nápovědou k modrým houbám: noc nad
  Hvozdem, kamera od mýtiny k trní na západě, houby se rozsvítí jedna po druhé, světla se zvednou a seřadí do cesty,
  na okamžik se mezi stromy zjeví postava s parožím (Elderan) – „…konečně…“. Jen jednou (`cutscene_grove_*`),
  spustí se po zavření dialogu (`QuestDialogManager.onHidden`).
- Debug menu: „🎬 Cutscéna: Oči“ a „🎬 Cutscéna: Modré světlo v trní“.
