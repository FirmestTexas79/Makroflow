package cz.uhk.macroflow.pokemon

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import cz.uhk.macroflow.common.MainActivity

class PokemonBattleFragment : Fragment() {

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
                // ✅ Voláme novou aktualizaci na MainActivity
                (requireActivity() as? MainActivity)?.updatePokemonVisibility()

                view?.postDelayed({ parentFragmentManager.popBackStack() }, 3000)
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
            setOnClickListener { parentFragmentManager.popBackStack() }
        }

        root.addView(titleTv)
        root.addView(battleView)
        root.addView(closeBtn)
        return root
    }
}