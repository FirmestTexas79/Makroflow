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

    /** Aktivní tým (docs/adr/0034): první = parťák na liště. */
    private var team: List<Int> = emptyList()
    private var teamSlots = 1

    private fun saveTeam(t: List<Int>) {
        team = t
        cz.uhk.macroflow.pokemon.skills.SkillStore.saveTeam(requireContext(), t)
    }

    /** Připne Makromona na lištu (aktivní parťák = první v týmu). */
    private fun pinToBar(item: CapturedMakromonEntity) {
        requireContext().getSharedPreferences("GamePrefs", Context.MODE_PRIVATE).edit()
            .putBoolean("pokemonAcquired", true)
            .putLong("currentOnBarCaughtDate", item.caughtDate)
            .putString("currentOnBarName", item.name.uppercase())
            .putInt("currentOnBarCapturedId", item.id)
            .apply()
        saveTeam(cz.uhk.macroflow.pokemon.skills.Team.makeActive(team, item.id, teamSlots))
        (requireActivity() as? MainActivity)?.updateMakromonVisibility()
        (requireActivity() as? MainActivity)?.refreshStickyNotification()
    }

    private fun loadData() {
        lifecycleScope.launch {
            if (currentTab == 0) {
                val ctx = requireContext().applicationContext
                val (list, team, slots) = withContext(Dispatchers.IO) {
                    Triple(db.capturedMakromonDao().getAllCaught(),
                        cz.uhk.macroflow.pokemon.skills.SkillStore.team(ctx),
                        cz.uhk.macroflow.pokemon.skills.SkillStore.state(ctx).teamSlots)
                }
                this@InventoryFragment.team = team
                teamSlots = slots
                rvInventory.adapter = MakromonAdapter(list)
            } else {
                val list = withContext(Dispatchers.IO) { db.userItemDao().getAllItems() }
                // Stav dovedností a záhonů je v user_items taky, ale do inventáře nepatří
                val ownedItems = list.filter { it.quantity > 0 && !cz.uhk.macroflow.pokemon.skills.SkillStore.isInternal(it.itemId) }
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
            val btnTeam: ImageButton   = v.findViewById(R.id.btnTeam)
            val tvTeam: TextView       = v.findViewById(R.id.tvTeamBadge)
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
            val spriteRes = makromonDrawableRes(item.makromonId, item.name)
            cz.uhk.macroflow.pokemon.shiny.ShinySprites.into(holder.ivSprite, spriteRes, item.makromonId, item.isShiny && spriteRes != R.drawable.ic_home)

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
            // Tým: odznak s pořadím a tlačítko přidat / odebrat
            val teamIdx = team.indexOf(item.id)
            holder.tvTeam.visibility = if (teamIdx >= 0) View.VISIBLE else View.GONE
            holder.tvTeam.text = if (teamIdx == 0 && isActiveOnBar) "Aktivní" else "Tým ${teamIdx + 1}"
            holder.btnTeam.setImageResource(if (teamIdx >= 0) android.R.drawable.ic_menu_close_clear_cancel else android.R.drawable.ic_input_add)
            holder.btnTeam.contentDescription = if (teamIdx >= 0) "Odebrat z týmu" else "Přidat do týmu"
            holder.btnTeam.setOnClickListener {
                if (teamIdx >= 0) {
                    val rest = cz.uhk.macroflow.pokemon.skills.Team.remove(team, item.id)
                    saveTeam(rest)
                    if (isActiveOnBar) {
                        // Aktivní odešel z týmu → nastoupí další, jinak se parťák schová
                        val next = list.firstOrNull { it.id == rest.firstOrNull() }
                        if (next != null) pinToBar(next) else {
                            prefs.edit().putBoolean("pokemonAcquired", false).putLong("currentOnBarCaughtDate", -1L).apply()
                            (requireActivity() as? MainActivity)?.updateMakromonVisibility()
                        }
                    }
                    Toast.makeText(context, "${item.name} odešel z týmu.", Toast.LENGTH_SHORT).show()
                } else {
                    when (val r = cz.uhk.macroflow.pokemon.skills.Team.add(team, item.id, teamSlots)) {
                        is cz.uhk.macroflow.pokemon.skills.Team.Result.Ok -> {
                            saveTeam(r.team)
                            if (!isAcquired) pinToBar(item)
                            Toast.makeText(context, "${item.name} je v týmu (${r.team.size}/$teamSlots).", Toast.LENGTH_SHORT).show()
                        }
                        cz.uhk.macroflow.pokemon.skills.Team.Result.Full -> Toast.makeText(context,
                            "Tým je plný ($teamSlots/$teamSlots). Další místo odemkneš ve stromu Chytání (deník → Postava).",
                            Toast.LENGTH_LONG).show()
                    }
                }
                loadData()
            }

            holder.btnPin.setOnClickListener {
                pinToBar(item)
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

            val ball = cz.uhk.macroflow.pokemon.balls.Makroball.from(item.itemId)
            val med = cz.uhk.macroflow.pokemon.status.MedItem.from(item.itemId)
            val crystal = cz.uhk.macroflow.pokemon.cave.CrystalColor.fromItem(item.itemId)
            val resource = cz.uhk.macroflow.pokemon.skills.Resource.from(item.itemId)
            holder.tvName.text = ball?.label ?: med?.label ?: crystal?.label ?: resource?.label ?: when (item.itemId) {
                "lure_lamp"  -> "Spooky Plate"
                else         -> item.itemId
            }

            holder.tvQuantity.visibility = View.VISIBLE
            holder.tvQuantity.text = if (crystal != null) "Klíčový předmět · klepni pro popis" else "Vlastníš: ${item.quantity} ks"

            // Itemy zatím stále načítají z URL – nemáme lokální drawable pro itemy
            val imageUrl = when (item.itemId) {
                "lure_lamp"  -> "https://img.pokemondb.net/sprites/items/spooky-plate.png"
                else         -> ""
            }

            if (resource != null) {
                holder.ivSprite.setImageBitmap(cz.uhk.macroflow.pokemon.balls.BallSprites.pixelIcon(resource,
                    cz.uhk.macroflow.pokemon.skills.SkillArt.resourceIcon(resource), cz.uhk.macroflow.pokemon.skills.SkillArt.ITEM,
                    (64 * holder.itemView.resources.displayMetrics.density).toInt()))
            } else if (crystal != null) {
                holder.ivSprite.setImageBitmap(cz.uhk.macroflow.pokemon.balls.BallSprites.pixelIcon(crystal,
                    cz.uhk.macroflow.pokemon.cave.Crystals.iconPixels(crystal), cz.uhk.macroflow.pokemon.cave.Crystals.H,
                    (64 * holder.itemView.resources.displayMetrics.density).toInt()))
            } else if (med != null) {
                holder.ivSprite.setImageBitmap(cz.uhk.macroflow.pokemon.balls.BallSprites.pixelIcon(med, med.pixels, cz.uhk.macroflow.pokemon.status.MedItem.SIZE, (64 * holder.itemView.resources.displayMetrics.density).toInt()))
            } else if (ball != null) {
                holder.ivSprite.setImageBitmap(cz.uhk.macroflow.pokemon.balls.BallSprites.icon(ball, (64 * holder.itemView.resources.displayMetrics.density).toInt()))
            } else if (imageUrl.isNotEmpty()) {
                holder.ivSprite.load(imageUrl) {
                    placeholder(R.drawable.ic_home)
                    error(R.drawable.ic_home)
                }
            } else {
                holder.ivSprite.setImageResource(android.R.drawable.ic_menu_compass)
            }

            holder.itemView.setOnClickListener {
                if (resource != null) {
                    Toast.makeText(requireContext(), "${resource.label}: ${resource.description}", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                if (crystal != null) {
                    android.app.AlertDialog.Builder(requireContext())
                        .setTitle("💎 ${crystal.label}")
                        .setMessage(crystal.description)
                        .setPositiveButton("OK", null)
                        .show()
                    return@setOnClickListener
                }
                if (med != null) {
                    Toast.makeText(requireContext(), "${med.label}: ${med.description} Použij v souboji přes ITEM → LÉKÁRNIČKA.", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
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

            listOf(R.id.btnLock, R.id.btnPinToBar, R.id.btnUnpinFromBar, R.id.btnDeletePokemon, R.id.btnTeam, R.id.tvTeamBadge, R.id.separator)
                .forEach { id -> holder.itemView.findViewById<View>(id)?.visibility = View.GONE }
            holder.itemView.findViewById<View>(R.id.pbPokemonXp)?.visibility = View.GONE
        }

        override fun getItemCount() = list.size
    }
}