package cz.uhk.macroflow.pokemon.skills

/**
 * Rozložení stromu dovedností do mřížky (docs/adr/0058): kořen nahoře, každý další stupeň
 * o řádek níž. Listy dostanou sloupce zleva doprava, rodič stojí uprostřed nad svými dětmi.
 * Čistá funkce – kreslí ji SkillTreeView, hlídá SkillTreeLayoutTest.
 */
object SkillTreeLayout {

    /** [col] může být i polovina (rodič mezi dvěma dětmi), [row] = hloubka od kořene. */
    data class Pos(val col: Float, val row: Int)

    data class Layout(val pos: Map<String, Pos>, val columns: Int, val rows: Int) {
        /** Spoje rodič → dítě (id rodiče, id dítěte). */
        fun links(nodes: List<SkillTree.Node>): List<Pair<String, String>> =
            nodes.mapNotNull { n -> n.requires?.takeIf { it in pos }?.let { it to n.id } }
    }

    fun of(nodes: List<SkillTree.Node>): Layout {
        val ids = nodes.map { it.id }.toSet()
        val children = nodes.groupBy { n -> n.requires?.takeIf { it in ids } }
        val pos = LinkedHashMap<String, Pos>()
        var nextLeaf = 0f
        fun place(id: String, depth: Int): Float {
            val kids = children[id].orEmpty()
            val col = if (kids.isEmpty()) nextLeaf.also { nextLeaf += 1f }
                      else kids.map { place(it.id, depth + 1) }.let { (it.first() + it.last()) / 2f }
            pos[id] = Pos(col, depth)
            return col
        }
        children[null].orEmpty().forEach { place(it.id, 0) }
        return Layout(pos, nextLeaf.toInt().coerceAtLeast(1), (pos.values.maxOfOrNull { it.row } ?: 0) + 1)
    }
}
