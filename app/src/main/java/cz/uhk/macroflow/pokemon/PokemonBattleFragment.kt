package cz.uhk.macroflow.pokemon

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import cz.uhk.macroflow.common.MainActivity

class PokemonBattleFragment : Fragment() {

    private var isClosing = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val ctx = requireContext()
        val dp  = ctx.resources.displayMetrics.density

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.BLACK)
            gravity     = android.view.Gravity.CENTER
        }

        val titleTv = TextView(ctx).apply {
            text      = "★  SECRET  ★"
            textSize  = 10f
            typeface  = android.graphics.Typeface.MONOSPACE
            setTextColor(android.graphics.Color.parseColor("#A8C8F8"))
            gravity   = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = (6 * dp).toInt() }
        }

        val battleView = PokemonBattleView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            onCaught = {
                // ✅ Voláme aktualizaci na MainActivity
                (requireActivity() as? MainActivity)?.updatePokemonVisibility()

                // 🛡️ Bezpečné odložení zavření fragmentu
                view?.postDelayed({
                    safeClose()
                }, 2000) // 2 sekundy bohatě stačí na přečtení hlášky o chycení
            }
        }

        val closeBtn = TextView(ctx).apply {
            text      = "[ CLOSE ]"
            textSize  = 9f
            typeface  = android.graphics.Typeface.MONOSPACE
            setTextColor(android.graphics.Color.parseColor("#686868"))
            gravity   = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = (8 * dp).toInt() }
            setOnClickListener { safeClose() }
        }

        root.addView(titleTv)
        root.addView(battleView)
        root.addView(closeBtn)
        return root
    }

    // 🔒 Bezpečná unifikovaná metoda pro opuštění souboje
    private fun safeClose() {
        if (isClosing) return // Pokud se už zavírá, ignoruj další požadavky
        isClosing = true

        if (isAdded && !isRemoving) {
            parentFragmentManager.popBackStack()
        }
    }
}