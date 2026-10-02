package cz.uhk.macroflow.pokemon.bag

import cz.uhk.macroflow.pokemon.balls.Makroball
import cz.uhk.macroflow.pokemon.cave.CrystalColor
import cz.uhk.macroflow.pokemon.cave.Crystals
import cz.uhk.macroflow.pokemon.skills.Gear
import cz.uhk.macroflow.pokemon.skills.GearArt
import cz.uhk.macroflow.pokemon.skills.Resource
import cz.uhk.macroflow.pokemon.skills.SkillStore
import cz.uhk.macroflow.pokemon.skills.TreeArt
import cz.uhk.macroflow.pokemon.status.MedItem
import cz.uhk.macroflow.pokemon.story.ForestHeart
import cz.uhk.macroflow.pokemon.story.Insight
import cz.uhk.macroflow.pokemon.story.SecretGrove

/**
 * Obsah Batohu v deníku (docs/adr/0060): co předmět je, do které přihrádky patří, jeho pixelová
 * ikona a co s ním jde dělat. Suroviny (rudy, dřevo, bobule…) mají v deníku vlastní záložku,
 * do Batohu nepatří; interní stav (XP, uzly stromu, záhony…) taky ne. Bez Androidu – testuje BagItemsTest.
 */
object BagItems {

    enum class Pocket(val label: String) {
        BALLS("Makrobally"), MEDS("Lékárnička"), GEAR("Vybavení"), KEY("Klíčové předměty"), OTHER("Ostatní")
    }

    enum class Action {
        /** Jen popis. */
        NONE,
        /** Čte se (listy, deník strážce). */
        READ,
        /** Dá se použít hned (Spooky Plate). */
        USE
    }

    data class Item(
        val id: String,
        val label: String,
        val description: String,
        val pocket: Pocket,
        val pixels: IntArray,
        /** Strana čtvercové ikony v pixelech. */
        val size: Int,
        val action: Action = Action.NONE
    )

    const val LURE_LAMP = "lure_lamp"

    /** Patří předmět do Batohu? (ne suroviny, ne interní stav) */
    fun belongs(itemId: String): Boolean =
        !SkillStore.isInternal(itemId) && Resource.from(itemId) == null

    fun of(itemId: String): Item? {
        if (!belongs(itemId)) return null
        Makroball.from(itemId)?.let { return Item(it.id, it.label, it.description, Pocket.BALLS, it.pixels, Makroball.SIZE) }
        MedItem.from(itemId)?.let {
            return Item(it.id, it.label, it.description + " Použiješ v souboji přes ITEM → LÉKÁRNIČKA.", Pocket.MEDS, it.pixels, MedItem.SIZE)
        }
        Gear.from(itemId)?.let {
            return Item(it.id, it.label, it.description + " Nasadíš v deníku: Postava → vybavení.", Pocket.GEAR, GearArt.gearIcon(it), GearArt.ICON)
        }
        CrystalColor.fromItem(itemId)?.let { return Item(itemId, it.label, it.description, Pocket.KEY, Crystals.iconPixels(it), Crystals.H) }
        return when (itemId) {
            ForestHeart.ITEM_ID -> Item(itemId, ForestHeart.LABEL, ForestHeart.DESCRIPTION, Pocket.KEY, ForestHeart.iconPixels(), ForestHeart.ICON)
            SecretGrove.DIARY_ID -> Item(itemId, SecretGrove.DIARY_LABEL, "Zápisky strážce Elderana z Hvozdu.", Pocket.KEY,
                SecretGrove.diaryIcon(), SecretGrove.ICON, Action.READ)
            Insight.PAGES_ITEM -> Item(itemId, Insight.PAGES_LABEL, "Útržky cizích papírů. Čím víc toho víš, tím víc z nich přečteš.", Pocket.KEY,
                Insight.pageIcon(), Insight.ICON, Action.READ)
            LURE_LAMP -> Item(itemId, "Spooky Plate", "Přiláká duchy – na chvíli se častěji objevují duchové Makromoni.", Pocket.OTHER,
                TreeArt.MOON, TreeArt.SIZE, Action.USE)
            else -> Item(itemId, itemId, "Neznámý předmět.", Pocket.OTHER, TreeArt.LOCK, TreeArt.SIZE)
        }
    }

    /** Vlastněné předměty rozdělené do přihrádek (v pořadí [Pocket]), uvnitř podle jména. */
    fun pockets(counts: Map<String, Int>): Map<Pocket, List<Pair<Item, Int>>> =
        counts.filter { it.value > 0 }.mapNotNull { (id, n) -> of(id)?.let { it to n } }
            .groupBy { it.first.pocket }
            .mapValues { (_, l) -> l.sortedBy { it.first.label } }
            .toSortedMap(compareBy { it.ordinal })
}
