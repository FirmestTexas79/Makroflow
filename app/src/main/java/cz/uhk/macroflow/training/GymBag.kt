package cz.uhk.macroflow.training

/**
 * Taška do gymu: zaškrtávací seznam věcí (čistý Kotlin, pokryto testy).
 * Zaškrtnutí platí jen pro daný den – další den se seznam sám „vybalí“.
 * Serializace do jednoduchých řetězců, aby šla uložit do SharedPreferences a testovat bez Androidu.
 */
data class BagItem(val id: String, val label: String)

data class GymBagState(
    val items: List<BagItem>,
    val checked: Set<String>,
    /** Den (yyyy-MM-dd), ke kterému patří zaškrtnutí. */
    val date: String
) {
    val packedCount: Int get() = items.count { it.id in checked }
    val allPacked: Boolean get() = items.isNotEmpty() && packedCount == items.size
    fun isPacked(item: BagItem) = item.id in checked
}

object GymBag {

    const val MAX_LABEL = 40

    val DEFAULT_LABELS = listOf(
        "Kompresní triko", "Kompresní kraťasy", "Kraťasy", "Oversized triko", "Boty",
        "Trhačky", "Žuváky", "Pití", "Ručník", "Sluchátka", "Permanentka"
    )

    fun defaults(date: String) = GymBagState(DEFAULT_LABELS.mapIndexed { i, l -> BagItem("d$i", l) }, emptySet(), date)

    /** Nový den → nic zaškrtnutého; seznam věcí zůstává. */
    fun forDay(state: GymBagState, today: String): GymBagState =
        if (state.date == today) state else state.copy(checked = emptySet(), date = today)

    fun toggle(state: GymBagState, id: String): GymBagState =
        state.copy(checked = if (id in state.checked) state.checked - id else state.checked + id)

    fun unpackAll(state: GymBagState): GymBagState = state.copy(checked = emptySet())

    /** Přidá věc; prázdný text nebo duplicita (bez ohledu na velikost písmen) stav nemění. */
    fun add(state: GymBagState, label: String, newId: String): GymBagState {
        val clean = label.replace('\t', ' ').replace('\n', ' ').trim().take(MAX_LABEL)
        if (clean.isEmpty() || state.items.any { it.label.equals(clean, ignoreCase = true) }) return state
        return state.copy(items = state.items + BagItem(newId, clean))
    }

    fun remove(state: GymBagState, id: String): GymBagState =
        state.copy(items = state.items.filterNot { it.id == id }, checked = state.checked - id)

    // ── Uložení ──

    fun encodeItems(items: List<BagItem>): String = items.joinToString("\n") { "${it.id}\t${it.label}" }

    fun decodeItems(s: String?): List<BagItem>? = s?.lines()?.mapNotNull { line ->
        val tab = line.indexOf('\t')
        if (tab <= 0) null else BagItem(line.substring(0, tab), line.substring(tab + 1))
    }

    fun encodeChecked(ids: Set<String>): String = ids.joinToString(",")

    fun decodeChecked(s: String?): Set<String> = s?.split(',')?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
}
