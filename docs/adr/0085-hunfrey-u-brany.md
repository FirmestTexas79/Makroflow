# 0085 – Hunfrey, poutník u Brány světů

Stav: přijato (2026-10-14), větev `MULTI`

- **NPC**: lišák Hunfrey (stejný tvor jako Makromon #042) stojí na plošině vpravo od Brány světů
  v Nebeském průsmyku (uzel `plosina`, dřív jen výhled – výhled zůstává na `vyhlidka`).
  Na mapě i v dialogu je animovaný GIF `npc_hunfrey.gif` (86 × 86 px pixel artu zvětšených 4×,
  13 snímků po 150 ms) přes Coil; `imageLoader` je teď pole aktivity.
- **Quest `sky_hunfrey`** (vedlejší, kapitola „🧭 BRÁNA SVĚTŮ“):
  1. *Poutník u brány* – vysvětlí, že Drakirra po probuzení přelétla bránu a stočila se nad Hvozd; pošle hráče k rýhám po drápech (VISIT_NODE `drapy`).
  2. *Runy na bráně* – hráč si přečte tabuli u brány (STORY_FLAG `world_gate_seen`), Hunfrey pak runy vyloží.
  3. *Srdce pro bránu* – splní se vložením Srdce Hvozdu (`forest_heart_placed`). Při každém dalším oslovení
     **radí podle postupu** (`Hunfrey.advice`, nový `QuestManager.hintProvider`): málo úkolů pro vstup do Hvozdu
     (x z 5) → deník; Hvozd nezačatý → druid Mydrus na mýtině; rozpracovaný quest Hvozdu → název a nápověda
     aktuální fáze; Srdce v batohu → vlož ho do lůžka.
- Lore: kartu „našel ve Městě“ a na rubu má razítko S-7 (stopa Kustodiátu, ADR k bibli příběhu).
  Drakirra „neutíkala před tebou, ale před něčím, co viděla vedle tebe“ – ladí s bible (viděla Spirru / nádobu).
- Kdo bránu otevře dřív, než Hunfreye osloví, má quest hned splněný a slyší rovnou rozloučení.
