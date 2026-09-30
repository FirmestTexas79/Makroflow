package cz.uhk.macroflow.pokemon.battlefx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoveAnimsTest {
    /** Všechny útoky, které ve hře existují (vygenerováno z BattleEngine). */
    private val allMoves = listOf("ACID SPRAY","AQUA RING","AQUA TAIL","AURORA BEAM","BABY-DOLL","BELCH","BITE","BLIZZARD","BODY SLAM","BUBBLE BEAM","CHARM","CONFUSE RAY","CRUNCH","CRYSTAL SHARD","DARK PULSE","DIG","DRAGON CLAW","DRAGON DANCE","DRAGON PULSE","DRAGONBREATH","EARTHQUAKE","EMBER","FIRE BLAST","FIRE FANG","FLAMETHROWER","FREEZE-DRY","FURY ATTACK","GROWL","GUST","HARDEN","HEAT WAVE","HEAVY SLAM","HEX","HYDRO CANNON","HYDRO PUMP","HYPER FANG","HYPNOSIS","ICE FANG","ICE SHARD","LEAF BLADE","LEER","LICK","LULLABY","MEMENTO","MOONBLAST","MUD-SLAP","NIGHT SHADE","ORIGIN PULSE","OUTRAGE","PAIN SPLIT","PETAL DANCE","PHANTOM FORCE","PLAY ROUGH","POISON GAS","POISON STING","PSYCHIC","QUICK ATTACK","RAZOR LEAF","REGENERATE","SAND ATTACK","SCRATCH","SEED BOMB","SHADOW BALL","SHADOW PUNCH","SLAM","SLASH","SLEEP POWDER","SLUDGE BOMB","SMOKESCREEN","SNORE","SOLAR BEAM","SOUL DRAIN","SPECTRAL TIDE","SPIRAL SPIN","SPITE","STRING SHOT","TACKLE","TAIL WHIP","THUNDER SHOCK","THUNDERBOLT","TOXIC AURA","TOXIC","VINE WHIP","WATER GUN","WATER PULSE","WILL-O-WISP","WING ATTACK","WISE WORDS","WOOD HAMMER")

    // hráč vlevo dole, soupeř vpravo nahoře (herní pixely 160 × 144)
    private val ax = 48f; private val ay = 77f; private val tx = 112f; private val ty = 40f

    @Test fun everyMoveHasItsOwnAnimation() {
        val missing = allMoves.filter { it !in MoveAnims.KNOWN }
        assertTrue("chybí: $missing", missing.isEmpty())
    }

    @Test fun framesStayOnScreenAndInRange() {
        for (name in MoveAnims.KNOWN) {
            val s = MoveAnims.spec(name, "NORMAL", 40)
            assertTrue(name, s.hitAt in 0.2f..0.95f && s.durationMs in 400..1500)
            for (k in 0..20) {
                val t = k / 20f
                for (dir in listOf(true, false)) {
                    val f = if (dir) MoveAnims.frame(s, t, ax, ay, tx, ty, 3) else MoveAnims.frame(s, t, tx, ty, ax, ay, 3)
                    f.particles.forEach { p ->
                        assertTrue("$name t=$t $p", MoveAnims.inBounds(p) && p.alpha >= -0.001f && p.r >= 0f)
                    }
                    assertTrue(name, f.attackerAlpha in 0f..1f && f.tintAlpha in 0f..1f)
                }
            }
        }
    }

    @Test fun somethingHappensAroundTheHit() {
        for (name in MoveAnims.KNOWN) {
            val s = MoveAnims.spec(name, "NORMAL", 40)
            val mid = (0..10).map { MoveAnims.frame(s, s.hitAt * it / 10f + 0.001f, ax, ay, tx, ty, 1) }
            assertTrue("$name nic nekreslí", mid.any { it.particles.isNotEmpty() })
        }
    }

    @Test fun contactMovesLungeAndComeBack() {
        val s = MoveAnims.spec("TACKLE", "NORMAL", 40)
        val atHit = MoveAnims.frame(s, s.hitAt, ax, ay, tx, ty)
        assertTrue(atHit.attackerDx > 20f)
        assertEquals(0f, MoveAnims.frame(s, 1f, ax, ay, tx, ty).attackerDx, 0.01f)
        val dig = MoveAnims.spec("DIG", "GROUND", 80)
        assertEquals(0f, MoveAnims.frame(dig, 0.5f, ax, ay, tx, ty).attackerAlpha, 0.001f)   // pod zemí
    }

    @Test fun gustTravelsTowardsTheTarget() {
        val s = MoveAnims.spec("GUST", "FLYING", 40)
        fun cx(t: Float) = MoveAnims.frame(s, t, ax, ay, tx, ty).particles.map { it.x }.average()
        assertTrue(cx(0.1f) < cx(0.4f) && cx(0.4f) < cx(0.75f))
    }

    @Test fun unknownMovesFallBackByType() {
        assertEquals(MoveAnims.Style.CONTACT, MoveAnims.spec("NOVÝ ÚTOK", "NORMAL", 50).style)
        assertEquals(MoveAnims.Style.PROJECTILE, MoveAnims.spec("NOVÝ ÚTOK", "FIRE", 50).style)
        assertEquals(MoveAnims.Style.RINGS, MoveAnims.spec("NOVÝ ÚTOK", "FIRE", 0).style)
        // stejný seed = stejný snímek
        val s = MoveAnims.spec("BLIZZARD", "WATER", 110)
        assertEquals(MoveAnims.frame(s, 0.5f, ax, ay, tx, ty, 9), MoveAnims.frame(s, 0.5f, ax, ay, tx, ty, 9))
    }
}
