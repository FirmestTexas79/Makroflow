// Náhled intra u vody (docs/adr/0030): vyrenderuje snímky do PPM (převod na PNG např. Pillow).
// Spuštění (kotlinc): zkompilovat s pokemon/encounter/{MountainIntro,MountainScene,WaterIntro,WaterScene}.kt,
// main = WaterPreviewKt, argument = výstupní složka.
import cz.uhk.macroflow.pokemon.encounter.WaterScene
import java.io.File

fun main(args: Array<String>) {
    val dir = File(args.getOrElse(0) { "." }).apply { mkdirs() }
    val scene = WaterScene(128, 286)
    val px = IntArray(scene.w * scene.h)
    for (t in listOf(150L, 600L, 1000L, 1180L, 1500L, 1700L, 1850L, 1990L, 2300L)) {
        scene.render(t, px)
        File(dir, "water_%04d.ppm".format(t)).outputStream().buffered().use { o ->
            o.write("P6\n${scene.w} ${scene.h}\n255\n".toByteArray())
            for (c in px) { o.write((c shr 16) and 0xFF); o.write((c shr 8) and 0xFF); o.write(c and 0xFF) }
        }
    }
}
