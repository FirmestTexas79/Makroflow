package cz.uhk.macroflow.pokemon

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
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
    private var currentTab = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_inventory, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        db = AppDatabase.getDatabase(requireContext())
        rvInventory = view.findViewById(R.id.rvInventory)
        tabLayout   = view.findViewById(R.id.tabLayoutInventory)
        rvInventory.layoutManager = GridLayoutManager(requireContext(), 2)

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) { currentTab = tab?.position ?: 0; loadData() }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
        loadData()
    }

    private fun loadData() {
        lifecycleScope.launch {
            if (currentTab == 0) {
                val list = withContext(Dispatchers.IO) { db.capturedPokemonDao().getAllCaught() }
                // Načti XP pro každého pokémona
                val xpMap = withContext(Dispatchers.IO) {
                    list.associate { it.pokemonId to (db.pokemonXpDao().getXp(it.pokemonId)?.totalXp ?: 0) }
                }
                rvInventory.adapter = PokemonAdapter(list, xpMap)
            } else {
                val list = withContext(Dispatchers.IO) { db.userItemDao().getAllItems() }
                rvInventory.adapter = ItemAdapter(list)
            }
        }
    }

    // ── 🐉 Pokémon adapter s XP/Level (Jen pro aktivního na liště) ──

    private inner class PokemonAdapter(
        private val list: List<CapturedPokemonEntity>,
        private val xpMap: Map<String, Int>
    ) : RecyclerView.Adapter<PokemonAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val ivSprite: ImageView     = v.findViewById(R.id.ivPokemonSprite)
            val tvName: TextView        = v.findViewById(R.id.tvPokemonName)
            val tvLevel: TextView       = v.findViewById(R.id.tvPokemonLevel)
            val pbXp: ProgressBar       = v.findViewById(R.id.pbPokemonXp)
            val btnLock: ImageButton    = v.findViewById(R.id.btnLock)
            val btnPin: ImageButton     = v.findViewById(R.id.btnPinToBar)
            val btnUnpin: ImageButton   = v.findViewById(R.id.btnUnpinFromBar)
            val btnDelete: ImageButton  = v.findViewById(R.id.btnDeletePokemon)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_captured_pokemon, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = list[position]
            val prefs = holder.itemView.context.getSharedPreferences("GamePrefs", Context.MODE_PRIVATE)

            // 🔍 Zjistíme, jestli je tento pokémon aktuálně na liště
            val activeOnBarId = prefs.getString("currentOnBarId", "") ?: ""
            val isAcquired = prefs.getBoolean("pokemonAcquired", false)
            val isActiveOnBar = isAcquired && (item.pokemonId == activeOnBarId)

            val xp = xpMap[item.pokemonId] ?: 0
            val level = PokemonLevelCalc.levelFromXp(xp)
            val prog = PokemonLevelCalc.progressToNextLevel(xp)

            holder.tvName.text = item.name

            // 📈 Zobrazíme level a XP bar POUZE pokud je aktivní na liště
            if (isActiveOnBar) {
                holder.tvLevel.visibility = View.VISIBLE
                holder.pbXp.visibility = View.VISIBLE
                holder.tvLevel.text = "Lv.$level"
                holder.pbXp.progress = (prog * 100).toInt()
            } else {
                holder.tvLevel.visibility = View.GONE
                holder.pbXp.visibility = View.GONE
            }

            val webName = item.name.lowercase()
                .replace(" ", "-").replace(".", "")
                .replace("♀", "-f").replace("♂", "-m")
            holder.ivSprite.load("https://img.pokemondb.net/sprites/lets-go-pikachu-eevee/normal/$webName.png") {
                placeholder(R.drawable.ic_home); error(R.drawable.ic_home)
            }

            val lockIcon = if (item.isLocked) android.R.drawable.ic_lock_lock else android.R.drawable.ic_lock_idle_lock
            holder.btnLock.setImageResource(lockIcon)

            holder.btnLock.setOnClickListener {
                lifecycleScope.launch(Dispatchers.IO) {
                    db.capturedPokemonDao().updatePokemon(item.copy(isLocked = !item.isLocked))
                    withContext(Dispatchers.Main) { loadData() }
                }
            }

            holder.btnPin.setOnClickListener {
                requireContext().getSharedPreferences("GamePrefs", Context.MODE_PRIVATE).edit()
                    .putBoolean("pokemonAcquired", true)
                    .putString("currentOnBarId", item.pokemonId)
                    .putString("currentOnBarName", item.name.uppercase())
                    .apply()
                (requireActivity() as? MainActivity)?.updatePokemonVisibility()
                loadData() // 🔄 Přenačteme data pro aktualizaci zobrazení XP baru
                Toast.makeText(requireContext(), "📌 ${item.name} vypuštěn na lištu!", Toast.LENGTH_SHORT).show()
            }

            holder.btnUnpin.setOnClickListener {
                requireContext().getSharedPreferences("GamePrefs", Context.MODE_PRIVATE).edit()
                    .putBoolean("pokemonAcquired", false).apply()
                (requireActivity() as? MainActivity)?.updatePokemonVisibility()
                loadData() // 🔄 Přenačteme data pro schování XP baru
                Toast.makeText(requireContext(), "📥 Pokémon schován do kapsy.", Toast.LENGTH_SHORT).show()
            }

            holder.btnDelete.setOnClickListener {
                if (item.isLocked) {
                    Toast.makeText(requireContext(), "Odemkni pokémona před smazáním! 🔒", Toast.LENGTH_SHORT).show()
                } else {
                    lifecycleScope.launch(Dispatchers.IO) {
                        db.capturedPokemonDao().deletePokemon(item)
                        withContext(Dispatchers.Main) { loadData() }
                    }
                    Toast.makeText(requireContext(), "🗑️ Pokémon smazán.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        override fun getItemCount() = list.size
    }

    // ── 🎒 Item adapter (Pro herní předměty bez XP a Levelu) ──

    private inner class ItemAdapter(private val list: List<UserItemEntity>) :
        RecyclerView.Adapter<ItemAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val ivSprite: ImageView = v.findViewById(R.id.ivPokemonSprite)
            val tvName: TextView    = v.findViewById(R.id.tvPokemonName)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_captured_pokemon, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = list[position]
            holder.tvName.text = when (item.itemId) {
                "poke_ball"  -> "Poké Ball (${item.quantity}x)"
                "great_ball" -> "Great Ball (${item.quantity}x)"
                "lure_lamp"  -> "Spooky Plate (${item.quantity}x)"
                else         -> "${item.itemId} (${item.quantity}x)"
            }
            val imageUrl = when (item.itemId) {
                "poke_ball"  -> "https://img.pokemondb.net/sprites/items/poke-ball.png"
                "great_ball" -> "https://img.pokemondb.net/sprites/items/great-ball.png"
                "lure_lamp"  -> "https://img.pokemondb.net/sprites/items/spooky-plate.png"
                else         -> ""
            }
            if (imageUrl.isNotEmpty()) holder.ivSprite.load(imageUrl)

            holder.itemView.setOnClickListener {
                if (item.itemId == "lure_lamp" && item.quantity > 0) {
                    val prefs = requireContext().getSharedPreferences("GamePrefs", Context.MODE_PRIVATE)
                    if (prefs.getBoolean("ghostPlateActive", false)) {
                        Toast.makeText(requireContext(), "Duchovní deska už je aktivní!", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    lifecycleScope.launch(Dispatchers.IO) {
                        if (db.userItemDao().consumeItem("lure_lamp", 1)) {
                            prefs.edit().putBoolean("ghostPlateActive", true).apply()
                            withContext(Dispatchers.Main) {
                                Toast.makeText(requireContext(), "👻 Ghost Plate aktivován! Příští setkání bude Gengar.", Toast.LENGTH_SHORT).show()
                                loadData()
                            }
                        }
                    }
                }
            }

            // 🚫 Skryjeme akční tlačítka pro Mazání, Piny a Locky
            listOf(R.id.btnLock, R.id.btnPinToBar, R.id.btnUnpinFromBar, R.id.btnDeletePokemon, R.id.separator)
                .forEach { id -> holder.itemView.findViewById<View>(id)?.visibility = View.GONE }

            // 🚫 Skryjeme Level a progress bar pro předměty
            holder.itemView.findViewById<View>(R.id.tvPokemonLevel)?.visibility = View.GONE
            holder.itemView.findViewById<View>(R.id.pbPokemonXp)?.visibility = View.GONE
        }

        override fun getItemCount() = list.size
    }
}