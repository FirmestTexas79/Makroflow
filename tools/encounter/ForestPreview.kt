// Náhled intra v Hvozdu (docs/adr/0036): vyrenderuje snímky do PPM (převod na PNG např. Pillow).
// Spuštění (kotlinc): zkompilovat s pokemon/encounter/{MountainIntro,MountainScene,ForestIntro,ForestScene}.kt,
// main = WaterPreviewKt, argument = výstupní složka.
import cz.uhk.macroflow.pokemon.encounter.ForestScene
import java.io.File

fun main(args: Array<String>) {
    val dir = File(args.getOrElse(0) { "." }).apply { mkdirs() }
    val scene = ForestScene(128, 286)
    val px = IntArray(scene.w * scene.h)
    for (t in listOf(200L, 700L, 1020L, 1300L, 1550L, 1700L, 1820L, 1960L, 2300L)) {
        scene.render(t, px)
        File(dir, "forest_%04d.ppm".format(t)).outputStream().buffered().use { o ->
            o.write("P6\n${scene.w} ${scene.h}\n255\n".toByteArray())
            for (c in px) { o.write((c shr 16) and 0xFF); o.write((c shr 8) and 0xFF); o.write(c and 0xFF) }
        }
    }
}
