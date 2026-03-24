package cz.uhk.macroflow.pokemon

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.android.material.tabs.TabLayout
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.common.MainActivity
import cz.uhk.macroflow.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class InventoryFragment : Fragment() {

    private lateinit var rvInventory: RecyclerView
    private lateinit var tabLayout: TabLayout
    private lateinit var db: AppDatabase

    private var currentTab = 0 // 0 = Pokémoni, 1 = Itemy

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_inventory, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = AppDatabase.getDatabase(requireContext())
        rvInventory = view.findViewById(R.id.rvInventory)
        tabLayout = view.findViewById(R.id.tabLayoutInventory)

        rvInventory.layoutManager = GridLayoutManager(requireContext(), 2)

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentTab = tab?.position ?: 0
                loadData()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        loadData()
    }

    private fun loadData() {
        lifecycleScope.launch {
            if (currentTab == 0) {
                val list = withContext(Dispatchers.IO) {
                    db.capturedPokemonDao().getAllCaught()
                }
                rvInventory.adapter = PokemonAdapter(list)
            } else {
                val list = withContext(Dispatchers.IO) {
                    db.userItemDao().getAllItems()
                }
                rvInventory.adapter = ItemAdapter(list)
            }
        }
    }

    // --- 🦁 1. ADAPTÉR PRO POKÉ-KAPSU (Pokémoni) ---
    private inner class PokemonAdapter(private val list: List<CapturedPokemonEntity>) :
        RecyclerView.Adapter<PokemonAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val ivSprite: ImageView = v.findViewById(R.id.ivPokemonSprite)
            val tvName: TextView = v.findViewById(R.id.tvPokemonName)
            val btnLock: ImageButton = v.findViewById(R.id.btnLock)
            val btnPinToBar: ImageButton = v.findViewById(R.id.btnPinToBar)
            val btnUnpinFromBar: ImageButton = v.findViewById(R.id.btnUnpinFromBar)
            val btnDeletePokemon: ImageButton = v.findViewById(R.id.btnDeletePokemon)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_captured_pokemon, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = list[position]
            holder.tvName.text = item.name

            val drawableName = when (item.name) {
                "GENGAR" -> "pokemon_gengar"
                "DIGLETT" -> "pokemon_diglett"
                else -> "pokemon_diglett"
            }
            val resId = resources.getIdentifier(drawableName, "drawable", requireContext().packageName)
            if (resId != 0) holder.ivSprite.setImageResource(resId)

            // 🔒 Přepínání ikony zámku
            val lockIcon = if (item.isLocked) android.R.drawable.ic_lock_lock else android.R.drawable.ic_lock_idle_lock
            holder.btnLock.setImageResource(lockIcon)

            holder.btnLock.setOnClickListener {
                lifecycleScope.launch(Dispatchers.IO) {
                    val updated = item.copy(isLocked = !item.isLocked)
                    db.capturedPokemonDao().updatePokemon(updated)
                    withContext(Dispatchers.Main) { loadData() }
                }
            }

            // 📍 Vypustit na lištu (Jakéhokoliv Pokémona!)
            holder.btnPinToBar.setOnClickListener {
                requireContext().getSharedPreferences("GamePrefs", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("pokemonAcquired", true)
                    .putString("currentOnBarId", item.pokemonId)
                    .putString("currentOnBarName", item.name.uppercase())
                    .apply()

                (requireActivity() as? MainActivity)?.updatePokemonVisibility()
                android.widget.Toast.makeText(requireContext(), "📌 ${item.name} vypuštěn na lištu!", android.widget.Toast.LENGTH_SHORT).show()
            }

            // 📥 Schovat do inventáře (Skrýt z lišty)
            holder.btnUnpinFromBar.setOnClickListener {
                requireContext().getSharedPreferences("GamePrefs", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("pokemonAcquired", false)
                    .apply()

                (requireActivity() as? MainActivity)?.updatePokemonVisibility()
                android.widget.Toast.makeText(requireContext(), "📥 Pokémon schován do kapsy.", android.widget.Toast.LENGTH_SHORT).show()
            }

            // 🗑️ Smazat (pokud není zamčený!)
            holder.btnDeletePokemon.setOnClickListener {
                if (item.isLocked) {
                    android.widget.Toast.makeText(requireContext(), "Odemkni pokémona před smazáním! 🔒", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    lifecycleScope.launch(Dispatchers.IO) {
                        db.capturedPokemonDao().deletePokemon(item)
                        withContext(Dispatchers.Main) { loadData() }
                    }
                    android.widget.Toast.makeText(requireContext(), "🗑️ Pokémon smazán.", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }

        override fun getItemCount() = list.size
    }

    // --- 🎒 2. ADAPTÉR PRO BATOH (Předměty) ---
    private inner class ItemAdapter(private val list: List<UserItemEntity>) :
        RecyclerView.Adapter<ItemAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val ivSprite: ImageView = v.findViewById(R.id.ivPokemonSprite)
            val tvName: TextView = v.findViewById(R.id.tvPokemonName)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_captured_pokemon, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = list[position]

            holder.tvName.text = when(item.itemId) {
                "poke_ball" -> "Poké Ball (${item.quantity}x)"
                "great_ball" -> "Great Ball (${item.quantity}x)"
                "lure_lamp" -> "Ghost Plate / Lampa (${item.quantity}x)"
                else -> "${item.itemId} (${item.quantity}x)"
            }

            val imageUrl = when(item.itemId) {
                "poke_ball" -> "https://img.pokemondb.net/sprites/items/poke-ball.png"
                "great_ball" -> "https://img.pokemondb.net/sprites/items/great-ball.png"
                "lure_lamp" -> "https://img.pokemondb.net/sprites/items/spell-tag.png"
                else -> ""
            }

            if (imageUrl.isNotEmpty()) {
                holder.ivSprite.load(imageUrl)
            } else {
                holder.ivSprite.setImageResource(R.drawable.ic_home)
            }

            holder.itemView.setOnClickListener {
                if (item.itemId == "lure_lamp" && item.quantity > 0) {
                    val prefs = requireContext().getSharedPreferences("GamePrefs", Context.MODE_PRIVATE)
                    val isAlreadyActive = prefs.getBoolean("ghostPlateActive", false)

                    if (isAlreadyActive) {
                        android.widget.Toast.makeText(requireContext(), "Ghost Plate už máš aktivní!", android.widget.Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    lifecycleScope.launch(Dispatchers.IO) {
                        val success = db.userItemDao().consumeItem("lure_lamp", 1)

                        if (success) {
                            prefs.edit().putBoolean("ghostPlateActive", true).apply()

                            withContext(Dispatchers.Main) {
                                android.widget.Toast.makeText(requireContext(), "👻 Ghost Plate aktivován! Příští Pokémon bude Gengar.", android.widget.Toast.LENGTH_SHORT).show()
                                loadData()
                            }
                        }
                    }
                }
            }

            holder.itemView.findViewById<View>(R.id.btnLock)?.visibility = View.GONE
            holder.itemView.findViewById<View>(R.id.separator)?.visibility = View.GONE
            holder.itemView.findViewById<View>(R.id.btnPinToBar)?.visibility = View.GONE
            holder.itemView.findViewById<View>(R.id.btnUnpinFromBar)?.visibility = View.GONE
            holder.itemView.findViewById<View>(R.id.btnDeletePokemon)?.visibility = View.GONE
        }

        override fun getItemCount() = list.size
    }
}