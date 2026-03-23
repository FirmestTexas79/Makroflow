package cz.uhk.macroflow.pokemon

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import cz.uhk.macroflow.AppDatabase
import cz.uhk.macroflow.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PokemonShopFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        // Použijeme zbrusu nové XML
        return inflater.inflate(R.layout.fragment_pokemon_shop, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvBalance = view.findViewById<TextView>(R.id.tvShopCoinBalance)
        val btnBuy    = view.findViewById<MaterialButton>(R.id.btnBuyGengar)
        val ivGengar  = view.findViewById<ImageView>(R.id.ivGengar)

        val prefs = requireContext().getSharedPreferences("GamePrefs", Context.MODE_PRIVATE)
        val isOwned = prefs.getBoolean("gengarPurchased", false)

        // 1. Nastav UI podle vlastnictví
        if (isOwned) {
            btnBuy.text = "Vlastníš"
            btnBuy.isEnabled = false
            ivGengar.alpha = 0.5f // Grafický náznak že je "vlastněn"
        } else {
            btnBuy.setOnClickListener { handlePurchase(view) }
        }

        // 2. Načti zůstatek peněženky
        lifecycleScope.launch {
            val balance = withContext(Dispatchers.IO) {
                AppDatabase.getDatabase(requireContext()).coinDao().getBalance()?.balance ?: 0
            }
            tvBalance.text = "$balance ×🪙"
        }
    }

    private fun handlePurchase(view: View) {
        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                AppDatabase.getDatabase(requireContext()).coinDao()
                    .spendCoins(BattleFactory.GENGAR_SHOP_PRICE)
            }

            if (success) {
                requireContext().getSharedPreferences("GamePrefs", Context.MODE_PRIVATE)
                    .edit().putBoolean("gengarPurchased", true).apply()

                // Osvěžíme fragment
                parentFragmentManager.beginTransaction()
                    .replace(R.id.nav_host_fragment, PokemonShopFragment())
                    .commit()

                android.app.AlertDialog.Builder(requireContext())
                    .setTitle("🎉 Úspěch!")
                    .setMessage("Odemkl jsi Gengara! V souboji (podržením spodního tlačítka) bude nyní namísto Digletta.")
                    .setPositiveButton("Hustý!") { d, _ -> d.dismiss() }
                    .show()
            } else {
                android.app.AlertDialog.Builder(requireContext())
                    .setTitle("Nedostatek coinů")
                    .setMessage("Pro nákup Gengara potřebuješ ${BattleFactory.GENGAR_SHOP_PRICE} 🪙.")
                    .setPositiveButton("Rozumím", null)
                    .show()
            }
        }
    }
}