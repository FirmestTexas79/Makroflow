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
import cz.uhk.macroflow.data.FirebaseRepository
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
                val list = withContext(Dispatchers.IO) { db.capturedMakromonDao().getAllCaught() }
                rvInventory.adapter = MakromonAdapter(list)
            } else {
                val list = withContext(Dispatchers.IO) { db.userItemDao().getAllItems() }
                val ownedItems = list.filter { it.quantity > 0 }
                rvInventory.adapter = ItemAdapter(ownedItems)
            }
        }
    }

    private inner class MakromonAdapter(
        private val list: List<CapturedMakromonEntity>
    ) : RecyclerView.Adapter<MakromonAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val ivSprite: ImageView    = v.findViewById(R.id.ivPokemonSprite)
            val tvName: TextView       = v.findViewById(R.id.tvPokemonName)
            val tvLevel: TextView      = v.findViewById(R.id.tvPokemonLevel)
            val pbXp: ProgressBar      = v.findViewById(R.id.pbPokemonXp)
            val btnLock: ImageButton   = v.findViewById(R.id.btnLock)
            val btnPin: ImageButton    = v.findViewById(R.id.btnPinToBar)
            val btnUnpin: ImageButton  = v.findViewById(R.id.btnUnpinFromBar)
            val btnDelete: ImageButton = v.findViewById(R.id.btnDeletePokemon)
        }

        /**
         * Vrátí resource ID drawable pro daného Makromona.
         * Konvence: makromon_spirra, makromon_ignar, atd.
         *
         * Shiny verze jsou zatím zakomentovány – odkomentuj až budou hotové sprity:
         * Konvence shiny: makromon_spirra_shiny, makromon_ignar_shiny, atd.
         */
        private fun makromonDrawableRes(makromonId: String, name: String): Int {
            // 1. Získáme zkrácené ID (např. "012" -> "12")
            val shortId = if (makromonId.length >= 3) makromonId.takeLast(2) else makromonId

            // 2. Vyčistíme jméno
            val namePart = name.lowercase().trim().replace(" ", "_")

            // TODO: Odkomentuj až budeme mít shiny sprity Makromonů
            // val drawableName = if (isShiny) "makromon_${baseName}_shiny" else "makromon_$baseName"

            // 3. Sestavíme dynamický název: makromon_12_spirra
            val drawableName = "makromon_${shortId}_$namePart"

            val resId = requireContext().resources.getIdentifier(
                drawableName, "drawable", requireContext().packageName
            )
            return if (resId != 0) resId else R.drawable.ic_home
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_captured_pokemon, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = list[position]
            val context = holder.itemView.context
            val prefs = context.getSharedPreferences("GamePrefs", Context.MODE_PRIVATE)

            val activeOnBarCaughtDate = prefs.getLong("currentOnBarCaughtDate", -1L)
            val isAcquired = prefs.getBoolean("pokemonAcquired", false)
            val isActiveOnBar = isAcquired && (item.caughtDate == activeOnBarCaughtDate)

            // 1. Nastavení viditelnosti tlačítek pro připnutí (Pin/Unpin)
            holder.btnPin.visibility   = if (isActiveOnBar) View.GONE else View.VISIBLE
            holder.btnUnpin.visibility = if (isActiveOnBar) View.VISIBLE else View.GONE

            // 2. Výpočet a zobrazení Levelu a XP progresu
            val prog = PokemonLevelCalc.progressToNextLevel(item.xp)
            holder.tvLevel.text = "Lv.${item.level}"
            holder.pbXp.progress = (prog * 100).toInt()
            holder.tvLevel.visibility = View.VISIBLE
            holder.pbXp.visibility    = View.VISIBLE

            // 3. Jméno Makromona
            holder.tvName.text = item.name

            // 4. KLÍČOVÁ OPRAVA: Načtení správného obrázku podle tvé nové konvence
            // Posíláme ID (např. "018") i Jméno (např. "Drakirra")
            holder.ivSprite.setImageResource(makromonDrawableRes(item.makromonId, item.name))

            // 5. Logika zámku (proti nechtěnému smazání)
            val lockIcon = if (item.isLocked)
                android.R.drawable.ic_lock_lock
            else
                android.R.drawable.ic_lock_idle_lock
            holder.btnLock.setImageResource(lockIcon)

            holder.btnLock.setOnClickListener {
                lifecycleScope.launch(Dispatchers.IO) {
                    item.isLocked = !item.isLocked
                    db.capturedMakromonDao().updateMakromon(item)
                    if (FirebaseRepository.isLoggedIn) FirebaseRepository.uploadCapturedMakromon(item)
                    withContext(Dispatchers.Main) { loadData() }
                }
            }

            // 6. Tlačítko Připnout (Pin) na hlavní lištu
            holder.btnPin.setOnClickListener {
                prefs.edit()
                    .putBoolean("pokemonAcquired", true)
                    .putLong("currentOnBarCaughtDate", item.caughtDate)
                    .putString("currentOnBarName", item.name.uppercase())
                    .putInt("currentOnBarCapturedId", item.id)
                    .apply()

                (requireActivity() as? MainActivity)?.updateMakromonVisibility()
                (requireActivity() as? MainActivity)?.refreshStickyNotification()
                loadData()
                android.widget.Toast.makeText(context, "📌 ${item.name} vybaven!", android.widget.Toast.LENGTH_SHORT).show()
            }

            // 7. Tlačítko Odepnout (Unpin) z lišty
            holder.btnUnpin.setOnClickListener {
                prefs.edit()
                    .putBoolean("pokemonAcquired", false)
                    .putLong("currentOnBarCaughtDate", -1L)
                    .apply()

                (requireActivity() as? MainActivity)?.updateMakromonVisibility()
                (requireActivity() as? MainActivity)?.refreshStickyNotification()
                loadData()
                android.widget.Toast.makeText(context, "📥 Makromon schován do kapsy.", android.widget.Toast.LENGTH_SHORT).show()
            }

            // 8. Tlačítko Smazat (Delete)
            holder.btnDelete.setOnClickListener {
                if (item.isLocked) {
                    android.widget.Toast.makeText(context, "Odemkni Makromona před smazáním! 🔒", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    lifecycleScope.launch(Dispatchers.IO) {
                        db.capturedMakromonDao().deleteMakromon(item)
                        if (FirebaseRepository.isLoggedIn) {
                            try {
                                FirebaseRepository.deleteCapturedMakromon(item.caughtDate)
                            } catch (e: Exception) { e.printStackTrace() }
                        }
                        if (isActiveOnBar) {
                            prefs.edit()
                                .putBoolean("pokemonAcquired", false)
                                .putLong("currentOnBarCaughtDate", -1L)
                                .apply()
                        }
                        withContext(Dispatchers.Main) {
                            (requireActivity() as? MainActivity)?.updateMakromonVisibility()
                            loadData()
                            android.widget.Toast.makeText(context, "🗑️ Makromon smazán.", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        override fun getItemCount() = list.size
    }

    private inner class ItemAdapter(private val list: List<UserItemEntity>) :
        RecyclerView.Adapter<ItemAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val ivSprite: ImageView  = v.findViewById(R.id.ivPokemonSprite)
            val tvName: TextView     = v.findViewById(R.id.tvPokemonName)
            val tvQuantity: TextView = v.findViewById(R.id.tvPokemonLevel)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_captured_pokemon, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = list[position]

            holder.tvName.text = when (item.itemId) {
                "poke_ball"  -> "Poké Ball"
                "great_ball" -> "Great Ball"
                "lure_lamp"  -> "Spooky Plate"
                else         -> item.itemId
            }

            holder.tvQuantity.visibility = View.VISIBLE
            holder.tvQuantity.text = "Vlastníš: ${item.quantity} ks"

            // Itemy zatím stále načítají z URL – nemáme lokální drawable pro itemy
            val imageUrl = when (item.itemId) {
                "poke_ball"  -> "https://img.pokemondb.net/sprites/items/poke-ball.png"
                "great_ball" -> "https://img.pokemondb.net/sprites/items/great-ball.png"
                "lure_lamp"  -> "https://img.pokemondb.net/sprites/items/spooky-plate.png"
                else         -> ""
            }

            if (imageUrl.isNotEmpty()) {
                holder.ivSprite.load(imageUrl) {
                    placeholder(R.drawable.ic_home)
                    error(R.drawable.ic_home)
                }
            } else {
                holder.ivSprite.setImageResource(android.R.drawable.ic_menu_compass)
            }

            holder.itemView.setOnClickListener {
                if (item.itemId == "lure_lamp" && item.quantity > 0) {
                    val prefs = requireContext().getSharedPreferences("GamePrefs", Context.MODE_PRIVATE)
                    if (prefs.getBoolean("ghostPlateActive", false)) {
                        Toast.makeText(requireContext(), "Spooky Plate už je aktivní!", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    lifecycleScope.launch(Dispatchers.IO) {
                        if (db.userItemDao().consumeItem("lure_lamp", 1)) {
                            prefs.edit().putBoolean("ghostPlateActive", true).apply()
                            if (FirebaseRepository.isLoggedIn) {
                                val updatedItem = db.userItemDao().getItem("lure_lamp")
                                if (updatedItem != null) FirebaseRepository.uploadUserItem(updatedItem)
                            }
                            withContext(Dispatchers.Main) {
                                Toast.makeText(requireContext(), "👻 Spooky Plate aktivován!", Toast.LENGTH_SHORT).show()
                                (requireActivity() as? MainActivity)?.runItemSpawner()
                                loadData()
                            }
                        }
                    }
                }
            }

            listOf(R.id.btnLock, R.id.btnPinToBar, R.id.btnUnpinFromBar, R.id.btnDeletePokemon, R.id.separator)
                .forEach { id -> holder.itemView.findViewById<View>(id)?.visibility = View.GONE }
            holder.itemView.findViewById<View>(R.id.pbPokemonXp)?.visibility = View.GONE
        }

        override fun getItemCount() = list.size
    }
}