package cz.uhk.macroflow.pokemon

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.R
import coil.load // ✅ TENTO IMPORT JE KLÍČOVÝ PRO STAŽENÍ Z WEBU!
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Upravená datová třída pro webové URL adresy
data class ShopProduct(
    val id: String,
    val name: String,
    val desc: String,
    val price: Int,
    val quantityToGive: Int,
    val imageUrl: String // ✅ Místo lokálního resource ID dáváme URL adresu
)

class PokemonShopFragment : Fragment() {

    private lateinit var rvShop: RecyclerView
    private lateinit var tabLayout: TabLayout
    private lateinit var tvBalance: TextView
    private lateinit var db: AppDatabase

    private var currentTab = 0 // 0 = Bally, 1 = Návnady

    // 🌐 DEFINICE ZBOŽÍ S REÁLNÝMI REKLAMNÍMI OBRÁZKY Z POKÉDB
    private val ballProducts = listOf(
        ShopProduct(
            "poke_ball",
            "Poké Ball (5x)",
            "Základní míček na ranní kardio.",
            20, 5,
            "https://img.pokemondb.net/sprites/items/poke-ball.png"
        ),
        ShopProduct(
            "great_ball",
            "Great Ball (3x)",
            "Lepší šance na těžké váhy.",
            50, 3,
            "https://img.pokemondb.net/sprites/items/great-ball.png"
        )
    )

    private val lureProducts = listOf(
        ShopProduct(
            "lure_lamp",
            "Spooky Plate",
            "Zvedne spawn Gengara v noci.",
            150, 1,
            "https://img.pokemondb.net/sprites/items/spooky-plate.png" // 👻 Gengar lure
        ),
        ShopProduct(
            "lure_protein",
            "Black Belt",
            "Zaručí spawn Machampa po tréninku.",
            100, 1,
            "https://img.pokemondb.net/sprites/items/black-belt.png" // 🥋 Machamp lure
        )
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_pokemon_shop, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = AppDatabase.getDatabase(requireContext())
        rvShop = view.findViewById(R.id.rvShopItems)
        tabLayout = view.findViewById(R.id.tabLayoutShop)
        tvBalance = view.findViewById(R.id.tvShopCoinBalance)

        rvShop.layoutManager = GridLayoutManager(requireContext(), 2)

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentTab = tab?.position ?: 0
                updateList()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        updateUI()
    }

    private fun updateUI() {
        lifecycleScope.launch {
            val balance = withContext(Dispatchers.IO) {
                db.coinDao().getBalance()?.balance ?: 0
            }
            tvBalance.text = balance.toString()
            updateList()
        }
    }

    private fun updateList() {
        val products = if (currentTab == 0) ballProducts else lureProducts
        rvShop.adapter = ShopAdapter(products)
    }

    // --- 🛒 ADAPTÉR PRO OBCHOD ---
    private inner class ShopAdapter(private val products: List<ShopProduct>) :
        RecyclerView.Adapter<ShopAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val ivIcon: ImageView = v.findViewById(R.id.ivProductIcon)
            val tvName: TextView = v.findViewById(R.id.tvProductName)
            val tvDesc: TextView = v.findViewById(R.id.tvProductDesc)
            val btnBuy: MaterialButton = v.findViewById(R.id.btnBuyProduct)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_shop_product, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val product = products[position]
            holder.tvName.text = product.name
            holder.tvDesc.text = product.desc
            holder.btnBuy.text = "KOUPIT ZA ${product.price} 🪙"

            // ✅ COIL NAČTENÍ OBRÁZKU Z INTERNETU
            holder.ivIcon.load(product.imageUrl) {
                placeholder(R.drawable.ic_home) // Než se stáhne, ukaž domeček
                error(R.drawable.ic_home)       // Kdyby to spadlo, ukaž domeček
            }

            holder.btnBuy.setOnClickListener {
                handlePurchase(product)
            }
        }

        override fun getItemCount() = products.size
    }

    private fun handlePurchase(product: ShopProduct) {
        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                db.coinDao().spendCoins(product.price)
            }

            if (success) {
                withContext(Dispatchers.IO) {
                    db.userItemDao().addItem(product.id, product.quantityToGive)
                }
                Toast.makeText(requireContext(), "🎉 Koupeno: ${product.name}", Toast.LENGTH_SHORT).show()
                updateUI()
            } else {
                android.app.AlertDialog.Builder(requireContext())
                    .setTitle("Nedostatek 🪙")
                    .setMessage("Pro nákup ${product.name} potřebuješ ${product.price} coinů.")
                    .setPositiveButton("Rozumím", null)
                    .show()
            }
        }
    }
}