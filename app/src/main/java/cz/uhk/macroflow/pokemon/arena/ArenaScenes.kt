package cz.uhk.macroflow.pokemon.arena

import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/** Vzhled arény podle lokace (docs/adr/0038). */
enum class ArenaTheme {
    TOWN, MEADOW, FOREST, MOUNTAINS, CAVE_OPEN, CAVE_MAZE, WATER;

    companion object {
        fun fromBiome(name: String?): ArenaTheme = when (name) {
            "TOWN" -> TOWN
            "MEADOW" -> MEADOW
            "FOREST" -> FOREST
            "MOUNTAINS" -> MOUNTAINS
            "CAVE_OPEN" -> CAVE_OPEN
            "CAVE_MAZE" -> CAVE_MAZE
            "WATER", "LAKE" -> WATER
            else -> MEADOW
        }
    }
}

private fun c(hex: Long) = hex.toInt()

/** Paleta bloků. */
object M {
    val GRASS = Mat(Pattern.GRASS, c(0xFF3E7A2A), c(0xFF58A03A), c(0xFF6DB947), c(0xFF8CCB5A))
    val FLOWERS = Mat(Pattern.FLOWERS, c(0xFF3E7A2A), c(0xFF58A03A), c(0xFF6DB947), c(0xFF8CCB5A))
    val MOSS = Mat(Pattern.MOSS, c(0xFF2A5220), c(0xFF3B6E2C), c(0xFF4A8236), c(0xFF6A9C46))
    val PATH = Mat(Pattern.DIRT, c(0xFF8A6A42), c(0xFFA5845A), c(0xFFB89A6C), c(0xFFC7AB7E))
    val DIRT = Mat(Pattern.DIRT, c(0xFF5E3B1E), c(0xFF7A4E28), c(0xFF8E5E33), c(0xFF6B4423))
    val SAND = Mat(Pattern.SAND, c(0xFFC4AC74), c(0xFFDCC690), c(0xFFE8D5A2), c(0xFFF2E2B8))
    val STONE = Mat(Pattern.STONE, c(0xFF6A6E74), c(0xFF80858C), c(0xFF8E949A), c(0xFF9EA4AA))
    val BOULDER = Mat(Pattern.STONE, c(0xFF625C55), c(0xFF766F67), c(0xFF857D73), c(0xFF968E83))
    val DARK_STONE = Mat(Pattern.STONE, c(0xFF2A2A33), c(0xFF383844), c(0xFF464654), c(0xFF555566))
    val CAVE_WALL = Mat(Pattern.STONE, c(0xFF26222A), c(0xFF342E38), c(0xFF403A46), c(0xFF4C4654))
    val GRAVEL = Mat(Pattern.GRAVEL, c(0xFF6E6A62), c(0xFF858075), c(0xFF99948A), c(0xFFAFA99C))
    val CAVE_GRAVEL = Mat(Pattern.GRAVEL, c(0xFF2E2B2C), c(0xFF3C3839), c(0xFF4A4546), c(0xFF5A5455))
    val SNOW = Mat(Pattern.SNOW, c(0xFFC8D4E0), c(0xFFDDE6EF), c(0xFFEEF4FA), c(0xFFFFFFFF))
    val TILES = Mat(Pattern.TILES, c(0xFF8F9398), c(0xFFC9CDD2), c(0xFFE6E9EC), c(0xFFF7F8F9))
    val COBBLE = Mat(Pattern.COBBLE, c(0xFF4E4A48), c(0xFF7C7672), c(0xFF948E88), c(0xFF66615D))
    val BRICK = Mat(Pattern.BRICK, c(0xFFD8CFC4), c(0xFFA34B32), c(0xFFBF6242), c(0xFF8C3E2A))
    val PLASTER = Mat(Pattern.PLASTER, c(0xFFC8BFA8), c(0xFFEDE4CC), c(0xFFF6EFDC), c(0xFFDCD2B8))
    val PLANK = Mat(Pattern.PLANK, c(0xFF5A3A1E), c(0xFF8A5A2E), c(0xFFA06C38))
    val LEAF = Mat(Pattern.LEAF, c(0xFF25521F), c(0xFF2F6B27), c(0xFF3E8531), c(0xFF5AA040))
    val LEAF_LIGHT = Mat(Pattern.LEAF, c(0xFF2F6B27), c(0xFF3E8531), c(0xFF55A03C), c(0xFF79BB52))
    val LEAF_DARK = Mat(Pattern.LEAF, c(0xFF173816), c(0xFF1F4A1C), c(0xFF2A5E25), c(0xFF3C7A34))
    val LEAF_AUTUMN = Mat(Pattern.LEAF, c(0xFF7A3A12), c(0xFFB0601E), c(0xFFD08A2E), c(0xFFE8B048))
    val BARK = Mat(Pattern.BARK, c(0xFF3E2A18), c(0xFF5A3D22), c(0xFF6E4C2C), c(0xFF4A3220))
    val BIRCH = Mat(Pattern.BARK, c(0xFF3A3A3A), c(0xFFE8E4DA), c(0xFFD2CEC4), c(0xFFB8B2A6))
    val ROOF_RED = Mat(Pattern.ROOF, c(0xFF6E2418), c(0xFFA8382A), c(0xFFC24A36))
    val ROOF_BLUE = Mat(Pattern.ROOF, c(0xFF1E3050), c(0xFF2E4C7A), c(0xFF3E62A0))
    val ROOF_GREEN = Mat(Pattern.ROOF, c(0xFF1E4630), c(0xFF2E6A46), c(0xFF3E8458))
    val WATER = Mat(Pattern.WATER, c(0xFF1E5A8C), c(0xFF2A74AA), c(0xFF3A8CC4), c(0xFF9ED4F0))
    val CAVE_WATER = Mat(Pattern.WATER, c(0xFF0E2A3A), c(0xFF143A50), c(0xFF1C4C66), c(0xFF4FA8C8))
    val CRYSTAL_CYAN = Mat(Pattern.CRYSTAL, c(0xFF1A5A70), c(0xFF2FB5D8), c(0xFF7CE6FF), c(0xFFE8FDFF), emissive = true)
    val CRYSTAL_VIOLET = Mat(Pattern.CRYSTAL, c(0xFF4A2A70), c(0xFF8A55D8), c(0xFFB990FF), c(0xFFF2E6FF), emissive = true)
    val SILVER = Mat(Pattern.CRYSTAL, c(0xFF5E6A78), c(0xFF9AA8B8), c(0xFFCFD9E4), c(0xFFFFFFFF), emissive = true)
    val GOLD = Mat(Pattern.CRYSTAL, c(0xFF7A5210), c(0xFFD49A22), c(0xFFF2C94A), c(0xFFFFF2B0), emissive = true)
    val LAMP = Mat(Pattern.LAMP, c(0xFFB08A20), c(0xFFFFD66B), c(0xFFFFF0B0), emissive = true)
    val WINDOW = Mat(Pattern.WINDOW, c(0xFF3A2A1E), c(0xFF5B7FA8), c(0xFF5B7FA8), c(0xFFA8D0F0))
    val WINDOW_LIT = Mat(Pattern.WINDOW, c(0xFF3A2A1E), c(0xFFF2C66A), c(0xFFF2C66A), c(0xFFFFEBB0), emissive = true)
    val METAL = Mat(Pattern.METAL, c(0xFF1E2228), c(0xFF3A4048), c(0xFF5A626C))
    val RAIL = Mat(Pattern.METAL, c(0xFF3A3C40), c(0xFF6E737A), c(0xFF9AA0A8))
    val MUSHROOM = Mat(Pattern.MUSHROOM, c(0xFF8A2218), c(0xFFC0392B), c(0xFFD9534A), c(0xFFF4EDE0))
    val LILY = Mat(Pattern.LILY, c(0xFF2E6B2A), c(0xFF3E8A36), c(0xFF5AA64A))
    val REED = Mat(Pattern.LEAF, c(0xFF4A6A26), c(0xFF6A8C34), c(0xFF86A844), c(0xFFA8B45A))
}

object Arenas {
    const val W = 480
    const val H = 288

    /** Kamera je pro všechny lokace stejná – Makromoni stojí vždy na stejném místě obrazovky. */
    val camera = Camera(V3(0f, 3f, -6f), 8f, 10f, 60f)

    /** Nohy soupeře a hráčova Makromona v pixelech arény (W × H). Hráč stojí pod dolním okrajem. */
    const val ENEMY_X = 336f
    const val ENEMY_Y = 162f
    const val PLAYER_X = 144f
    const val PLAYER_Y = 300f

    /** Výška podstavce soupeře. */
    const val PEDESTAL = 0.5f

    val enemyFoot: V3 = camera.unproject(ENEMY_X, ENEMY_Y, W, H, PEDESTAL)!!
    val playerFoot: V3 = camera.unproject(PLAYER_X, PLAYER_Y, W, H, 0f)!!

    /** Aréna W × (H + [extraTop]); obraz se prodlouží nahoru (víc oblohy a korun), scéna se neposune. */
    fun render(theme: ArenaTheme, seed: Int = 0, extraTop: Int = 0): IntArray =
        VoxelRenderer.render(scene(theme, seed), camera, W, H + extraTop, extraTop + H / 2f)

    fun scene(theme: ArenaTheme, seed: Int = 0): Scene = when (theme) {
        ArenaTheme.MEADOW -> Builder(seed).meadow()
        ArenaTheme.TOWN -> Builder(seed).town()
        ArenaTheme.FOREST -> Builder(seed).forest()
        ArenaTheme.MOUNTAINS -> Builder(seed).mountains()
        ArenaTheme.CAVE_OPEN -> Builder(seed).caveOpen()
        ArenaTheme.CAVE_MAZE -> Builder(seed).caveMaze()
        ArenaTheme.WATER -> Builder(seed).water()
    }

    /** Pás mezi kamerou a Makromony – nic vysokého tam nesmí stát. */
    fun inClearZone(x: Float, z: Float, margin: Float = 0f): Boolean {
        val e = enemyFoot; val p = playerFoot
        // lichoběžník od hráče (a kamery) k soupeři
        if (z < p.z - 6f - margin || z > e.z + 2.2f + margin) return false
        val t = ((z - p.z) / (e.z - p.z)).coerceIn(0f, 1f)
        val cx = p.x + (e.x - p.x) * t
        val half = 2.6f + 0.6f * t + margin
        return abs(x - cx) < half
    }

    private class Builder(val seed: Int) {
        val boxes = ArrayList<Box>()
        val lights = ArrayList<PointLight>()
        val rnd = Random(seed * 7919 + 17)
        val E = enemyFoot
        val P = playerFoot

        fun box(x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float, m: Mat, shadow: Boolean = true) {
            boxes.add(Box(minOf(x0, x1), minOf(y0, y1), minOf(z0, z1), maxOf(x0, x1), maxOf(y0, y1), maxOf(z0, z1), m, shadow))
        }

        fun ground(m: Mat, y: Float = 0f) = box(-40f, y - 3f, -12f, 50f, y, 80f, m)

        fun r(a: Float, b: Float) = a + rnd.nextFloat() * (b - a)

        fun free(x: Float, z: Float, margin: Float = 1f) = !inClearZone(x, z, margin)

        /** Podstavec soupeře (výška [PEDESTAL]) se schodem. */
        fun pedestal(top: Mat, w: Float = 3.2f, d: Float = 3f) {
            box(E.x - w / 2, 0f, E.z - d / 2 + 0.3f, E.x + w / 2, PEDESTAL, E.z + d / 2 + 0.3f, top)
        }

        fun tree(x: Float, z: Float, h: Float, leaf: Mat, trunk: Mat = M.BARK, size: Float = 1.5f) {
            box(x - 0.4f, 0f, z - 0.4f, x + 0.4f, h, z + 0.4f, trunk)
            box(x - size, h - 0.6f, z - size, x + size, h + 1.4f, z + size, leaf)
            box(x - size + 0.6f, h + 1.4f, z - size + 0.6f, x + size - 0.6f, h + 2.2f, z + size - 0.6f, leaf)
            repeat(3) {
                val ox = r(-size, size); val oz = r(-size, size)
                box(x + ox - 0.5f, h - 1f + r(0f, 1.6f), z + oz - 0.5f, x + ox + 0.5f, h + r(0.2f, 1.8f), z + oz + 0.5f, leaf)
            }
        }

        fun pine(x: Float, z: Float, h: Float, leaf: Mat) {
            box(x - 0.35f, 0f, z - 0.35f, x + 0.35f, h * 0.35f, z + 0.35f, M.BARK)
            var y = h * 0.25f; var s = 1.7f
            while (s > 0.3f) { box(x - s, y, z - s, x + s, y + 0.9f, z + s, leaf); y += 0.8f; s -= 0.45f }
        }

        fun bush(x: Float, z: Float, s: Float, m: Mat) {
            box(x - s, 0f, z - s * 0.8f, x + s, s * 1.1f, z + s * 0.8f, m)
            box(x - s * 0.5f, s * 1.1f, z - s * 0.4f, x + s * 0.6f, s * 1.5f, z + s * 0.4f, m)
        }

        fun tufts(n: Int, m: Mat, area: (Float, Float) -> Boolean = { _, _ -> true }) {
            repeat(n) {
                val x = r(-10f, 18f); val z = r(-2f, 20f)
                if (!area(x, z)) return@repeat
                if (abs(x - E.x) < 1.8f && abs(z - E.z - 0.3f) < 1.8f) return@repeat
                if (abs(x - P.x) < 1.2f && abs(z - P.z) < 1.2f) return@repeat
                val hh = r(0.15f, 0.35f)
                box(x - 0.09f, 0f, z - 0.09f, x + 0.09f, hh, z + 0.09f, m, shadow = false)
                box(x + 0.12f, 0f, z - 0.06f, x + 0.26f, hh * 0.7f, z + 0.08f, m, shadow = false)
            }
        }

        fun scene(atm: Atmosphere) = Scene(boxes, atm, seed)

        // ── Louka ──
        fun meadow(): Scene {
            ground(M.FLOWERS)
            // vyšlapaná cestička loukou
            var z = -4f
            while (z < 26f) {
                val x = 7.5f + sin(z * 0.28f) * 2.2f - z * 0.1f
                box(x - 0.9f, 0f, z, x + 0.9f, 0.03f, z + 0.5f, M.PATH, shadow = false)
                z += 0.5f
            }
            pedestal(M.GRASS)
            box(E.x - 1.1f, 0f, E.z + 1.8f, E.x + 1.7f, 0.9f, E.z + 2.6f, M.GRASS)
            // živý plot a stromy vzadu
            var x = -16f
            while (x < 30f) {
                if (abs(x - (7.5f + sin(19f * 0.28f) * 2.2f - 1.9f)) > 1.5f) box(x, 0f, 19f, x + 2f, r(1.6f, 2.4f), 20.4f, M.LEAF)
                x += 2f
            }
            for ((tx, tz) in listOf(-7.5f to 7f, -10f to 13f, -6f to 16.5f, 13.5f to 15f, 18f to 11f, 22f to 17.5f, -13f to 22f, 3f to 23f, 10f to 24f)) {
                if (free(tx, tz, 1.5f)) tree(tx, tz, r(2.6f, 3.6f), if (rnd.nextFloat() > 0.8f) M.LEAF_LIGHT else M.LEAF)
            }
            for ((bx, bz) in listOf(-4.5f to 4f, 10f to 4.5f, 11.5f to 8.5f, -5f to 10.5f, 15f to 6f)) if (free(bx, bz, 0.5f)) bush(bx, bz, r(0.6f, 0.9f), M.LEAF_LIGHT)
            // plot z kůlů vpravo
            var fz = 5f
            while (fz < 17f) { box(14.8f, 0f, fz, 15.1f, 1.1f, fz + 0.3f, M.PLANK); fz += 2f }
            box(14.85f, 0.7f, 5f, 15.05f, 0.85f, 17.3f, M.PLANK)
            // druhá řada stromů v dálce
            var bx = -30f
            while (bx < 45f) { tree(bx + r(-1f, 1f), r(30f, 36f), r(3f, 5f), if (rnd.nextFloat() > 0.6f) M.LEAF_LIGHT else M.LEAF); bx += r(4f, 6f) }
            tufts(90, M.LEAF_LIGHT)
            return scene(Atmosphere(c(0xFF5BA8E8), c(0xFFCDE8F5), c(0xFFCDE8F5), 16f, 70f, 0.8f,
                light = c(0xFFFFF6E4), ambient = 0.64f, sun = V3(-1f, 1.1f, 0.1f)))
        }

        // ── Město ──
        fun town(): Scene {
            ground(M.TILES)
            // dlážděný okraj náměstí
            box(-40f, 0f, 19f, 50f, 0.04f, 21f, M.COBBLE, shadow = false)
            // pódium soupeře
            box(E.x - 1.8f, 0f, E.z - 1.3f, E.x + 1.8f, PEDESTAL, E.z + 1.9f, M.STONE)
            // domy vzadu
            house(-15f, 23f, 6f, M.PLASTER, M.ROOF_RED, 4f)
            house(-8.5f, 23.5f, 5f, M.BRICK, M.ROOF_BLUE, 3.6f)
            house(-2f, 23f, 6.5f, M.PLASTER, M.ROOF_GREEN, 4.6f)
            house(5.5f, 23.5f, 5.5f, M.BRICK, M.ROOF_RED, 4f)
            house(12f, 23f, 6f, M.PLASTER, M.ROOF_BLUE, 4.4f)
            house(19f, 23.5f, 5f, M.BRICK, M.ROOF_GREEN, 3.8f)
            // kašna vlevo
            val fx = -6.5f; val fz = 8.5f
            box(fx - 2f, 0f, fz - 2f, fx + 2f, 0.7f, fz + 2f, M.COBBLE)
            box(fx - 1.6f, 0.55f, fz - 1.6f, fx + 1.6f, 0.62f, fz + 1.6f, M.WATER, shadow = false)
            box(fx - 0.3f, 0.6f, fz - 0.3f, fx + 0.3f, 1.8f, fz + 0.3f, M.COBBLE)
            box(fx - 0.7f, 1.8f, fz - 0.7f, fx + 0.7f, 2.1f, fz + 0.7f, M.COBBLE)
            // lampy
            for ((lx, lz) in listOf(-3.5f to 13.5f, 12f to 15f, 16f to 5f, -1f to 20f)) {
                box(lx - 0.12f, 0f, lz - 0.12f, lx + 0.12f, 2.6f, lz + 0.12f, M.METAL)
                box(lx - 0.3f, 2.6f, lz - 0.3f, lx + 0.3f, 3.1f, lz + 0.3f, M.LAMP, shadow = false)
                box(lx - 0.36f, 3.1f, lz - 0.36f, lx + 0.36f, 3.2f, lz + 0.36f, M.METAL)
                lights.add(PointLight(V3(lx, 2.8f, lz), c(0xFFFFD68A), 3.5f, 0.35f))
            }
            // truhlíky s keři
            for ((px, pz) in listOf(9.5f to 7f, 13f to 9.5f, -3.5f to 4f)) {
                box(px - 0.9f, 0f, pz - 0.6f, px + 0.9f, 0.5f, pz + 0.6f, M.PLANK)
                box(px - 0.8f, 0.5f, pz - 0.5f, px + 0.8f, 1.2f, pz + 0.5f, M.LEAF_LIGHT)
            }
            for ((tx, tz) in listOf(-11f to 12f, 18f to 12f, -13f to 19f, 24f to 18f)) tree(tx, tz, 3f, M.LEAF)
            return scene(Atmosphere(c(0xFF5FA6E6), c(0xFFD6ECF6), c(0xFFD6ECF6), 18f, 75f, 0.8f,
                light = c(0xFFFFF4E2), ambient = 0.66f, sun = V3(-1f, 1.1f, 0.15f), lights = lights))
        }

        fun house(x0: Float, z0: Float, w: Float, wall: Mat, roof: Mat, h: Float) {
            val x1 = x0 + w; val z1 = z0 + 5f
            box(x0, 0f, z0, x1, h, z1, wall)
            box(x0 - 0.1f, 0f, z0 - 0.1f, x1 + 0.1f, 0.5f, z1 + 0.1f, M.COBBLE)
            // okna a dveře na přední stěně
            val door = x0 + w / 2f - 0.5f
            box(door, 0f, z0 - 0.08f, door + 1f, 1.8f, z0, M.PLANK)
            var wx = x0 + 0.7f
            while (wx + 1f < x1 - 0.5f) {
                if (wx + 1f < door - 0.2f || wx > door + 1.2f)
                    box(wx, 1.4f, z0 - 0.06f, wx + 1f, 2.4f, z0, if (rnd.nextFloat() > 0.75f) M.WINDOW_LIT else M.WINDOW)
                wx += 1.6f
            }
            if (h > 3.9f) { var ux = x0 + 0.7f; while (ux + 1f < x1 - 0.4f) { box(ux, 2.8f, z0 - 0.06f, ux + 1f, 3.6f, z0, M.WINDOW); ux += 1.6f } }
            // stupňovitá střecha
            for (i in 0 until 4) {
                val inset = i * 0.65f
                box(x0 - 0.35f, h + i * 0.5f, z0 - 0.35f + inset, x1 + 0.35f, h + (i + 1) * 0.5f, z1 + 0.35f - inset, roof)
            }
            box(x1 - 1.4f, h + 1f, z0 + 3f, x1 - 0.6f, h + 3f, z0 + 3.8f, M.BRICK)
        }

        // ── Hvozd ──
        fun forest(): Scene {
            ground(M.MOSS)
            var z = -4f
            while (z < 30f) {
                val x = 8f + sin(z * 0.22f + 1f) * 2.5f
                box(x - 0.8f, 0f, z, x + 0.8f, 0.03f, z + 0.5f, M.PATH, shadow = false)
                z += 0.5f
            }
            pedestal(M.MOSS)
            // kořeny kolem podstavce
            box(E.x - 2.2f, 0f, E.z + 0.2f, E.x - 1.4f, 0.3f, E.z + 0.6f, M.BARK)
            box(E.x + 1.5f, 0f, E.z - 0.4f, E.x + 2.4f, 0.25f, E.z, M.BARK)
            // husté stromy
            val spots = listOf(-6f to 2f, -8.5f to 7f, -5.5f to 11f, -10f to 14f, -4f to 17f, 1f to 19f, 6f to 21f, 12f to 18f,
                15f to 13f, 17f to 7f, 20f to 16f, -12f to 20f, 9f to 26f, -1f to 25f, 14.5f to 23f, 22f to 22f, -14f to 9f, 12.5f to 2.5f, 15.5f to 3f, 11f to 14.5f, 19f to 3f)
            for ((tx, tz) in spots) if (free(tx, tz, 1.4f)) {
                if (rnd.nextFloat() > 0.35f) tree(tx, tz, r(5f, 7f), if (rnd.nextFloat() > 0.85f) M.LEAF_AUTUMN else M.LEAF_DARK, M.BARK, r(1.6f, 2.2f))
                else pine(tx, tz, r(6f, 8f), M.LEAF_DARK)
            }
            // padlý kmen, houby, kapradí
            box(10f, 0f, 9f, 14.5f, 0.8f, 9.8f, M.BARK)
            for ((mx, mz) in listOf(-3f to 6f, 11f to 5f, 12.2f to 10.4f, -4.2f to 9f, 5.8f to 12f)) if (free(mx, mz, 0f)) {
                box(mx - 0.08f, 0f, mz - 0.08f, mx + 0.08f, 0.28f, mz + 0.08f, M.MUSHROOM)
                box(mx - 0.28f, 0.28f, mz - 0.28f, mx + 0.28f, 0.46f, mz + 0.28f, M.MUSHROOM)
            }
            for ((bx, bz) in listOf(-3.5f to 2f, 9.5f to 3f, -4f to 13f, 13f to 4.5f, 16f to 10f, 3f to 16f)) if (free(bx, bz, 0.4f)) bush(bx, bz, r(0.5f, 0.8f), M.LEAF)
            tufts(200, M.LEAF)
            // světlušky
            repeat(10) {
                val fx = r(-6f, 16f); val fz = r(2f, 18f); val fy = r(0.6f, 2.4f)
                if (free(fx, fz, 0f)) {
                    box(fx - 0.05f, fy, fz - 0.05f, fx + 0.05f, fy + 0.1f, fz + 0.05f, M.LAMP, shadow = false)
                    lights.add(PointLight(V3(fx, fy, fz), c(0xFFE8FF9A), 1.6f, 0.3f))
                }
            }
            return scene(Atmosphere(c(0xFF7FB0C8), c(0xFFB8D8C0), c(0xFF5E7E5A), 9f, 36f, 0.9f,
                light = c(0xFFFFEFC4), ambient = 0.5f, sun = V3(-0.35f, 1f, -0.3f), lights = lights))
        }

        // ── Hory ──
        fun mountains(): Scene {
            ground(M.GRAVEL)
            // kamenné plotny v zemi
            repeat(40) {
                val x = r(-12f, 20f); val z = r(-2f, 18f)
                box(x, 0f, z, x + r(0.8f, 2f), 0.04f, z + r(0.8f, 1.6f), M.STONE, shadow = false)
            }
            // skalní římsa soupeře
            box(E.x - 1.8f, 0f, E.z - 1.2f, E.x + 1.8f, PEDESTAL, E.z + 1.8f, M.STONE)
            box(E.x - 1.2f, 0f, E.z + 1.8f, E.x + 2.6f, 1.6f, E.z + 3.2f, M.BOULDER)
            // rozeklané skály vzadu – bloky různé hloubky a výšky, mezi nimi prosvítají štíty
            var cx = -34f
            while (cx < 44f) {
                val w = r(2.5f, 5f); val z0 = r(17f, 23f); val hh = r(1.5f, 6.5f)
                box(cx, 0f, z0, cx + w, hh, 40f, if (rnd.nextFloat() > 0.3f) M.STONE else M.BOULDER)
                if (hh > 3.5f) box(cx + r(0f, 0.8f), hh, z0 + r(0.4f, 1.5f), cx + w - r(0f, 0.8f), hh + r(1f, 3f), 40f, M.STONE)
                if (hh > 4.5f) box(cx, hh, z0, cx + w, hh + 0.25f, z0 + r(0.8f, 1.8f), M.SNOW)
                cx += w
            }
            // skalní stěna vpravo a vlevo (schody)
            box(13f, 0f, 6f, 30f, 2.2f, 18f, M.STONE)
            box(15f, 2.2f, 8f, 30f, 4.2f, 18f, M.BOULDER)
            box(15f, 4.2f, 8f, 30f, 4.45f, 11f, M.SNOW)
            box(-30f, 0f, 5f, -8.5f, 1.8f, 18f, M.STONE)
            box(-30f, 1.8f, 8f, -10f, 3.6f, 18f, M.BOULDER)
            // balvany
            for ((bx, bz) in listOf(-4.5f to 3f, 9.5f to 3.5f, -6f to 9f, 10f to 11f, -3f to 13f)) if (free(bx, bz, 0.6f)) {
                val s = r(0.6f, 1.1f)
                box(bx - s, 0f, bz - s, bx + s, s * 1.3f, bz + s, M.BOULDER)
            }
            // vzdálené štíty
            for ((px, pz, ph) in listOf(Triple(-20f, 60f, 24f), Triple(5f, 72f, 34f), Triple(30f, 62f, 26f), Triple(-45f, 70f, 28f), Triple(55f, 75f, 30f))) {
                box(px - 14f, 0f, pz - 6f, px + 14f, ph * 0.6f, pz + 6f, M.STONE, shadow = false)
                box(px - 9f, ph * 0.6f, pz - 4f, px + 9f, ph * 0.85f, pz + 4f, M.STONE, shadow = false)
                box(px - 4.5f, ph * 0.85f, pz - 2f, px + 4.5f, ph, pz + 2f, M.SNOW, shadow = false)
            }
            tufts(50, M.REED)
            return scene(Atmosphere(c(0xFF6A9ED8), c(0xFFDCE8F2), c(0xFFC4D6E6), 16f, 70f, 0.85f,
                light = c(0xFFFFFFFF), ambient = 0.6f, sun = V3(-0.5f, 1f, -0.5f)))
        }

        private fun caveShell(floor: Mat, wall: Mat) {
            ground(floor)
            box(-40f, 0f, -12f, -7f, 14f, 80f, wall)
            box(15f, 0f, -12f, 50f, 14f, 80f, wall)
            box(-40f, 0f, 20f, 50f, 14f, 80f, wall)
            box(-40f, 9f, -12f, 50f, 12f, 80f, wall)
            // nerovné stěny
            repeat(26) {
                val side = rnd.nextInt(3)
                val y = r(0f, 6f); val hh = r(1f, 3.5f)
                when (side) {
                    0 -> { val z = r(0f, 20f); box(-7f, y, z, -7f + r(0.6f, 2f), y + hh, z + r(1f, 3f), wall) }
                    1 -> { val z = r(0f, 20f); box(15f - r(0.6f, 2f), y, z, 15f, y + hh, z + r(1f, 3f), wall) }
                    else -> { val x = r(-7f, 15f); box(x, y, 20f - r(0.6f, 2f), x + r(1f, 3f), y + hh, 20f, wall) }
                }
            }
            // krápníky ze stropu
            repeat(18) {
                val x = r(-7f, 15f); val z = r(4f, 20f)
                if (free(x, z, 0f)) box(x, r(5.5f, 7.5f), z, x + 0.5f, 9f, z + 0.5f, wall, shadow = false)
            }
        }

        // ── Starý důl (stříbro) ──
        fun caveMaze(): Scene {
            caveShell(M.CAVE_GRAVEL, M.CAVE_WALL)
            box(E.x - 1.8f, 0f, E.z - 1.2f, E.x + 1.8f, PEDESTAL, E.z + 1.8f, M.DARK_STONE)
            // koleje s pražci
            var z = -4f
            while (z < 20f) { box(9.4f, 0f, z, 11.6f, 0.08f, z + 0.35f, M.PLANK, shadow = false); z += 0.9f }
            box(9.8f, 0.08f, -4f, 9.95f, 0.2f, 20f, M.RAIL); box(11.05f, 0.08f, -4f, 11.2f, 0.2f, 20f, M.RAIL)
            // důlní výdřeva
            for (fz in listOf(9f, 16f)) {
                box(-6.4f, 0f, fz, -5.8f, 6f, fz + 0.6f, M.PLANK)
                box(13.8f, 0f, fz, 14.4f, 6f, fz + 0.6f, M.PLANK)
                box(-6.6f, 6f, fz - 0.1f, 14.6f, 6.6f, fz + 0.7f, M.PLANK)
                box(-5.5f, 3.2f, fz - 0.25f, -5.1f, 3.7f, fz + 0.1f, M.LAMP, shadow = false)
                lights.add(PointLight(V3(-5f, 3.3f, fz - 0.6f), c(0xFFFFB060), 7f, 0.9f))
                box(13.1f, 3.2f, fz - 0.25f, 13.5f, 3.7f, fz + 0.1f, M.LAMP, shadow = false)
                lights.add(PointLight(V3(13f, 3.3f, fz - 0.6f), c(0xFFFFB060), 7f, 0.9f))
            }
            // stříbrné žíly ve stěnách
            repeat(9) {
                val side = rnd.nextInt(3)
                val y = r(0.5f, 4f)
                val (x, zz) = when (side) { 0 -> -7f to r(3f, 19f); 1 -> 14.8f to r(3f, 19f); else -> r(-5f, 13f) to 19.8f }
                box(x - 0.25f, y, zz - 0.25f, x + 0.25f, y + 0.4f, zz + 0.25f, M.SILVER, shadow = false)
                lights.add(PointLight(V3(x, y + 0.2f, zz), c(0xFFB8C8E0), 1.8f, 0.35f))
            }
            // vozík
            box(10f, 0.2f, 12f, 11.8f, 1.2f, 13.6f, M.RAIL)
            box(10.1f, 1.1f, 12.1f, 11.7f, 1.35f, 13.5f, M.SILVER)
            lights.add(PointLight(V3(E.x, 3f, E.z - 1f), c(0xFFFFD0A0), 9f, 0.55f))
            lights.add(PointLight(V3(P.x + 1f, 2.5f, P.z + 1f), c(0xFFFFD0A0), 6f, 0.4f))
            repeat(60) {
                val x = r(-6f, 14f); val zz = r(-2f, 19f)
                if (free(x, zz, -0.5f)) box(x, 0f, zz, x + r(0.2f, 0.5f), r(0.1f, 0.3f), zz + r(0.2f, 0.4f), M.DARK_STONE, shadow = false)
            }
            return scene(Atmosphere(c(0xFF000000), c(0xFF0A080C), c(0xFF0A080C), 12f, 34f, 0.95f,
                light = c(0xFFB4AEC8), ambient = 0.75f, sun = V3(-0.3f, 1f, -0.3f), lights = lights, clouds = false))
        }

        // ── Mechová jeskyně (zlato, krystaly) ──
        fun caveOpen(): Scene {
            caveShell(M.MOSS, M.CAVE_WALL)
            // tmavé kamenné plochy v mechu
            repeat(12) { val x = r(-7f, 15f); val z = r(-2f, 19f); box(x, 0f, z, x + r(0.6f, 1.4f), 0.04f, z + r(0.6f, 1.2f), M.STONE, shadow = false) }
            box(E.x - 1.8f, 0f, E.z - 1.2f, E.x + 1.8f, PEDESTAL, E.z + 1.8f, M.STONE)
            box(E.x - 1.6f, PEDESTAL, E.z - 1f, E.x + 1.6f, PEDESTAL + 0.05f, E.z + 1.6f, M.MOSS)
            // podzemní jezírko vlevo
            box(-7f, -0.3f, 6f, -2f, 0.02f, 14f, M.CAVE_WATER, shadow = false)
            // krystaly
            for ((kx, kz, mat) in listOf(Triple(-5.5f, 3f, M.CRYSTAL_CYAN), Triple(-6f, 16f, M.CRYSTAL_VIOLET), Triple(12.5f, 5f, M.CRYSTAL_VIOLET),
                    Triple(13f, 15f, M.CRYSTAL_CYAN), Triple(3f, 18.5f, M.CRYSTAL_CYAN), Triple(8.5f, 18f, M.CRYSTAL_VIOLET), Triple(-1f, 12f, M.CRYSTAL_CYAN))) {
                if (!free(kx, kz, 0f)) continue
                box(kx - 0.3f, 0f, kz - 0.3f, kx + 0.3f, r(1.2f, 2f), kz + 0.3f, mat, shadow = false)
                box(kx + 0.3f, 0f, kz - 0.2f, kx + 0.7f, r(0.6f, 1.1f), kz + 0.2f, mat, shadow = false)
                box(kx - 0.7f, 0f, kz, kx - 0.35f, r(0.5f, 0.9f), kz + 0.35f, mat, shadow = false)
                lights.add(PointLight(V3(kx, 1f, kz), if (mat === M.CRYSTAL_CYAN) c(0xFF6FE0FF) else c(0xFFB480FF), 5.5f, 0.85f))
            }
            // zlatý balvan vzadu
            box(10f, 0f, 16.5f, 12.5f, 1.8f, 19f, M.BOULDER)
            box(10.6f, 1.2f, 16.4f, 11.2f, 1.6f, 16.6f, M.GOLD, shadow = false)
            box(11.5f, 0.5f, 16.4f, 12f, 0.9f, 16.6f, M.GOLD, shadow = false)
            lights.add(PointLight(V3(11f, 1f, 16f), c(0xFFFFD060), 3f, 0.6f))
            // světlo ze stropní pukliny nad arénou
            lights.add(PointLight(V3(E.x - 1f, 6f, E.z - 1f), c(0xFFCFF4E0), 10f, 0.75f))
            lights.add(PointLight(V3(P.x, 3f, P.z + 1f), c(0xFFA8E8D0), 6f, 0.45f))
            tufts(120, M.LEAF_LIGHT)
            return scene(Atmosphere(c(0xFF000000), c(0xFF06100E), c(0xFF06100E), 12f, 34f, 0.95f,
                light = c(0xFFA8C8B4), ambient = 0.75f, sun = V3(-0.3f, 1f, -0.3f), lights = lights, clouds = false))
        }

        // ── Voda ──
        fun water(): Scene {
            // písečný břeh vpředu, dál voda
            box(-40f, -3f, -12f, 50f, 0f, 0.8f, M.SAND)
            repeat(12) { val x = -12f + it * 2.4f; box(x, -3f, 0.8f, x + 2.4f, 0f, 0.8f + r(0f, 1.6f), M.SAND) }
            box(-40f, -3f, 0.8f, 50f, -0.35f, 80f, M.WATER, shadow = false)
            // kamenný ostrůvek soupeře
            box(E.x - 1.7f, -0.6f, E.z - 1.2f, E.x + 1.7f, PEDESTAL, E.z + 1.8f, M.BOULDER)
            box(E.x - 1.4f, PEDESTAL, E.z - 0.9f, E.x + 1.4f, PEDESTAL + 0.05f, E.z + 1.5f, M.MOSS)
            box(E.x + 1.4f, -0.6f, E.z + 0.6f, E.x + 2.5f, 0.1f, E.z + 1.6f, M.BOULDER)
            // lekníny
            repeat(22) {
                val x = r(-8f, 16f); val z = r(3f, 18f)
                if (abs(x - E.x) < 2.3f && abs(z - E.z) < 2.3f) return@repeat
                val s = r(0.35f, 0.7f)
                box(x - s, -0.35f, z - s * 0.8f, x + s, -0.3f, z + s * 0.8f, M.LILY, shadow = false)
                if (rnd.nextFloat() > 0.7f) box(x - 0.12f, -0.3f, z - 0.12f, x + 0.12f, -0.15f, z + 0.12f, M.MUSHROOM, shadow = false)
            }
            // rákosí na březích
            fun reeds(x0: Float, z0: Float, n: Int) = repeat(n) {
                val x = x0 + r(-1.2f, 1.2f); val z = z0 + r(-0.8f, 0.8f)
                box(x - 0.06f, -0.4f, z - 0.06f, x + 0.06f, r(0.7f, 1.6f), z + 0.06f, M.REED)
                if (rnd.nextFloat() > 0.6f) box(x - 0.09f, 0.9f, z - 0.09f, x + 0.09f, 1.25f, z + 0.09f, M.BARK)
            }
            reeds(-6f, 1.2f, 14); reeds(10f, 1.4f, 12); reeds(-9f, 8f, 10); reeds(15.5f, 9f, 10)
            // protější břeh se stromy
            box(-40f, -3f, 21f, 50f, 0.3f, 60f, M.GRASS)
            box(-40f, -3f, 20f, 50f, 0.05f, 21f, M.SAND)
            for (tx in listOf(-12f, -6.5f, -1f, 5f, 10.5f, 16f, 21f)) tree(tx + r(-1f, 1f), r(23f, 26f), r(3f, 4.5f), if (rnd.nextFloat() > 0.7f) M.LEAF_LIGHT else M.LEAF)
            // levý břeh
            box(-40f, -3f, 0f, -9f, 0.1f, 21f, M.GRASS)
            tree(-11f, 6f, 3.5f, M.LEAF); tree(-12f, 13f, 4f, M.LEAF_LIGHT)
            box(-40f, 0f, 45f, 60f, 8f, 60f, M.LEAF_LIGHT, shadow = false)
            return scene(Atmosphere(c(0xFF5AA6E6), c(0xFFD2ECF8), c(0xFFD2ECF8), 16f, 70f, 0.8f,
                light = c(0xFFFFF8E8), ambient = 0.66f, sun = V3(-0.5f, 1f, -0.45f)))
        }
    }
}
