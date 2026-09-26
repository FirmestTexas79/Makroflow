// Náhled pixel artu dovedností: kotlinc Preview.kt + zdroje skills/*.kt + balls/Makroball.kt, spustit → out.png
import cz.uhk.macroflow.pokemon.skills.*
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

fun main(args: Array<String>) {
    val items = mutableListOf<Triple<IntArray, Int, Int>>()
    Skill.entries.forEach { items += Triple(SkillArt.skillIcon(it), SkillArt.ICON, SkillArt.ICON) }
    Resource.entries.forEach { items += Triple(SkillArt.resourceIcon(it), SkillArt.ITEM, SkillArt.ITEM) }
    items += Triple(SkillArt.plot(false), SkillArt.PLOT_W, SkillArt.PLOT_H)
    items += Triple(SkillArt.plot(true), SkillArt.PLOT_W, SkillArt.PLOT_H)
    Berry.entries.forEach { b -> (0..2).forEach { s -> items += Triple(SkillArt.plant(b, s), SkillArt.PLANT, SkillArt.PLANT) } }
    items += Triple(SkillArt.craftingTable(), SkillArt.TABLE_W, SkillArt.TABLE_H)
    listOf(0f, 0.5f, 0.9f).forEach { items += Triple(SkillArt.hourglass(it), SkillArt.GLASS_W, SkillArt.GLASS_H) }
    val s = 8; val cell = 26 * s; val cols = 6
    val rows = (items.size + cols - 1) / cols
    val img = BufferedImage(cols * cell, rows * cell, BufferedImage.TYPE_INT_ARGB)
    val g = img.createGraphics(); g.color = java.awt.Color(0x8FB84A); g.fillRect(0, 0, img.width, img.height)
    items.forEachIndexed { i, (px, w, h) ->
        val ox = (i % cols) * cell + 8; val oy = (i / cols) * cell + 8
        for (y in 0 until h) for (x in 0 until w) { val c = px[y * w + x]; if (c != 0) for (dy in 0 until s) for (dx in 0 until s) img.setRGB(ox + x * s + dx, oy + y * s + dy, c) }
    }
    ImageIO.write(img, "png", File(args.getOrElse(0) { "out.png" }))
}
