package cz.uhk.macroflow.pokemon.arena

import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/** Náhled gladiátorské arény do build/arena-preview (docs/adr/0078) – pro kontrolu očima. */
class ColosseumPreviewTest {
    @Test
    fun writePreview() {
        val extra = 220
        val px = Arenas.render(ArenaTheme.COLOSSEUM, 1, extra)
        val img = BufferedImage(Arenas.W, Arenas.H + extra, BufferedImage.TYPE_INT_ARGB)
        img.setRGB(0, 0, Arenas.W, Arenas.H + extra, px, 0, Arenas.W)
        val dir = File("build/arena-preview").apply { mkdirs() }
        ImageIO.write(img, "png", File(dir, "colosseum.png"))
    }
}
