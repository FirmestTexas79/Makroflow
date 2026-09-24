package cz.uhk.macroflow.pokemon.cave

import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Kamera pro mapy větší než obrazovka (čistý Kotlin, pokryto testy). Podklady v docs/adr/0013.
 *
 * Svět (pozadí + postava) se posouvá tak, aby postava byla uprostřed obrazovky,
 * ale kamera nikdy nevyjede za okraj obrázku pozadí.
 */
object MapCamera {

    /**
     * Celočíselné zvětšení jednoho art pixelu na obrazovce: na šířku je vidět zhruba
     * [artPixelsAcross] art pixelů, ale mapa vždy pokryje celou obrazovku (žádné černé okraje).
     * Celé číslo = všechny pixely stejně velké (ostrý pixel art).
     */
    fun pixelScale(artW: Int, artH: Int, viewW: Int, viewH: Int, artPixelsAcross: Int): Int {
        require(artW > 0 && artH > 0 && artPixelsAcross > 0)
        if (viewW <= 0 || viewH <= 0) return 1
        val wanted = (viewW.toFloat() / artPixelsAcross).roundToInt()
        val cover = maxOf(ceilDiv(viewW, artW), ceilDiv(viewH, artH))
        return maxOf(1, wanted, cover)
    }

    /**
     * Posun světa (translationX/Y) v jedné ose: [target] (pozice postavy ve světě) má být
     * uprostřed [viewport], posun je ale omezený hranou světa. Menší svět se vycentruje.
     */
    fun offset(target: Float, viewport: Int, world: Int): Float =
        if (world <= viewport) (viewport - world) / 2f
        else -(target - viewport / 2f).coerceIn(0f, (world - viewport).toFloat())

    /**
     * Vzdálenost klepnutí od uzlu v jednotkách obrazovky (stejně jako na mapách bez kamery,
     * kde svět = obrazovka). Díky tomu je „dotyková zóna“ uzlu stejně velká na každé mapě.
     */
    fun tapDistance(
        nodeRelX: Float, nodeRelY: Float, tapRelX: Float, tapRelY: Float,
        worldW: Int, worldH: Int, viewW: Int, viewH: Int
    ): Float = hypot(
        (nodeRelX - tapRelX) * worldW / viewW.coerceAtLeast(1),
        (nodeRelY - tapRelY) * worldH / viewH.coerceAtLeast(1)
    )

    private fun ceilDiv(a: Int, b: Int) = (a + b - 1) / b
}
