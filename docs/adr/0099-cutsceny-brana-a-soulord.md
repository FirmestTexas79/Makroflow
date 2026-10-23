# 0099 – Cutscény: Brána se otevírá a Pád Soulorda

Stav: přijato (2026-10-23), větev `MULTI`, navazuje na ADR 0098

- **„Brána se otevírá“** (`GateCutsceneView`, ~27 s) po vložení Srdce Hvozdu (po zeleném záblesku, před tabulí):
  prstenec se roztočí, závoj se rozestoupí a na naši stranu pronikne jemný popel; Hunfrey ustoupí o krok
  („Poprvé nevěděl, co nakreslit“). Střih za bránu – Popelavá pláň, padá popel, v dálce bílé hradby Ozvěny.
  Střih do Hvozdu – daleko od brány tiše uschne jeden strom: „Přání vždycky něco stojí.“
- **„Pád Soulorda“** (`SoulordCutsceneView`, ~25 s) po výhře nad pánem Rudé hniloby: hniloba se stahuje z kořenů,
  maska praskne (otřes), na okamžik pod ní prosvitne světlo s parožím (Elderan – předzvěst Zapomenutého háje),
  zbyde popel a ticho, Starý dub se nadechne a v kořenech se rozsvítí Srdce Hvozdu. Souboj nastaví
  `cutscene_soulord_pending`; po návratu na mapu se přehraje **před** přepočtem questu, takže odměna od dubu navazuje.
- Elderan se kreslí jednou funkcí (`ElderanFigure`) v háji i při pádu Soulorda.
- Debug menu má všechny cutscény: úvod Makrosvěta, Světlo mezi stromy, Bezejmenní, Oči, Modré světlo v trní,
  Pád Soulorda, Brána se otevírá.
