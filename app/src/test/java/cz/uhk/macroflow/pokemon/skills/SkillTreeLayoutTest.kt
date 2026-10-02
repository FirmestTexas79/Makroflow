package cz.uhk.macroflow.pokemon.skills

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rozložení grafického stromu dovedností a pixelové ikony (docs/adr/0058). */
class SkillTreeLayoutTest {

    @Test fun catchingTreeHasBranchAndLongChain() {
        val l = SkillTreeLayout.of(SkillTree.of(Skill.CATCHING))
        assertEquals(SkillTreeLayout.Pos(0.5f, 0), l.pos["team_2"])          // kořen mezi dvěma větvemi
        assertEquals(SkillTreeLayout.Pos(0f, 1), l.pos["catch_xp"])
        assertEquals(SkillTreeLayout.Pos(1f, 1), l.pos["team_3"])
        assertEquals(SkillTreeLayout.Pos(1f, 4), l.pos["team_6"])
        assertEquals(2, l.columns)
        assertEquals(5, l.rows)
    }

    @Test fun everyNodeIsPlacedOnceAndChildrenSitBelowParents() {
        Skill.entries.forEach { s ->
            val nodes = SkillTree.of(s)
            val l = SkillTreeLayout.of(nodes)
            assertEquals(s.name, nodes.map { it.id }.toSet(), l.pos.keys)
            assertEquals(s.name, nodes.count { it.requires != null }, l.links(nodes).size)
            l.links(nodes).forEach { (a, b) -> assertTrue("$a→$b", l.pos.getValue(b).row == l.pos.getValue(a).row + 1) }
            // dva uzly nikdy na stejném místě
            assertEquals(s.name, l.pos.size, l.pos.values.toSet().size)
        }
    }

    @Test fun parentIsCenteredAboveChildren() {
        val l = SkillTreeLayout.of(SkillTree.of(Skill.BUG_CATCHING))   // kořen se třemi dětmi
        assertEquals(SkillTreeLayout.Pos(1f, 0), l.pos["net_eff"])
        assertEquals(3, l.columns)
    }

    @Test fun iconsAreFullTwelveByTwelve() {
        listOf(TreeArt.STAR, TreeArt.DOUBLE, TreeArt.MOON, TreeArt.LOCK, TreeArt.COIN, TreeArt.SWORD, TreeArt.TREE,
            TreeArt.DROP, TreeArt.MEAT, TreeArt.LEAF, TreeArt.BOWL, TreeArt.BOOT, TreeArt.DUMBBELL, TreeArt.SUN).forEach {
            assertEquals(TreeArt.SIZE * TreeArt.SIZE, it.size)
            assertTrue(it.count { c -> c != 0 } > 20)
            assertTrue(it.any { c -> c == 0xFF1E140C.toInt() })                 // každá má tmavý obrys
        }
    }
}
