package cz.uhk.macroflow.pokemon

import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.R
import cz.uhk.macroflow.common.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PokedexFragment : Fragment() {

    private lateinit var rvPokedex: RecyclerView
    private lateinit var db: AppDatabase

    private lateinit var ivDetailSprite: ImageView
    private lateinit var tvDetailNumber: TextView
    private lateinit var tvDetailName: TextView
    private lateinit var tvDetailType: TextView
    private lateinit var tvDetailMacro: TextView
    private lateinit var btnTestEvo: Button

    private var isFirstLoad = true
    private lateinit var pokedexAdapter: PokedexAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_pokedex, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = AppDatabase.getDatabase(requireContext())

        ivDetailSprite = view.findViewById(R.id.ivDetailSprite)
        tvDetailNumber = view.findViewById(R.id.tvDetailNumber)
        tvDetailName   = view.findViewById(R.id.tvDetailName)
        tvDetailType   = view.findViewById(R.id.tvDetailType)
        tvDetailMacro  = view.findViewById(R.id.tvDetailMacro)
        btnTestEvo     = view.findViewById(R.id.btnTestEvo)

        rvPokedex = view.findViewById(R.id.rvPokedex)
        rvPokedex.layoutManager = GridLayoutManager(requireContext(), 3)

        pokedexAdapter = PokedexAdapter(emptyList(), emptyList(), emptyList(), emptyMap())
        rvPokedex.adapter = pokedexAdapter

        loadPokedex()
    }

    private fun loadPokedex() {
        if (!isFirstLoad) return
        isFirstLoad = false

        lifecycleScope.launch(Dispatchers.Main) {
            val invIds = withContext(Dispatchers.IO) {
                db.capturedPokemonDao().getAllCaught().map { it.pokemonId }
            }

            val unlockedIds = withContext(Dispatchers.IO) {
                val statusIds = db.pokedexStatusDao().getUnlockedIds()
                val caughtIds = db.capturedPokemonDao().getAllCaught().map { it.pokemonId }
                (statusIds + caughtIds).distinct()
            }

            val catchStats = withContext(Dispatchers.IO) {
                db.capturedPokemonDao().getAllCaught()
                    .groupBy { it.pokemonId }
                    .mapValues { it.value.size }
            }

            val kantoList = withContext(Dispatchers.IO) {
                val dbEntries = db.pokedexEntryDao().getAllEntries()
                if (dbEntries.isEmpty()) {
                    SpawnManager.allEntries.map { spawn ->
                        PokedexEntryEntity(
                            pokedexId = spawn.id,
                            webName = spawn.name.lowercase(),
                            displayName = spawn.name,
                            type = spawn.rarity.label,
                            macroDesc = "Tento Pokémon čeká na tvůj trénink.",
                            unlockedHint = getFallbackHint(spawn.id)
                        )
                    }
                } else {
                    dbEntries
                }
            }

            pokedexAdapter.updateData(kantoList, unlockedIds, invIds, catchStats)

            if (kantoList.isNotEmpty()) {
                val isUnlocked = unlockedIds.contains(kantoList[0].pokedexId)
                val isInInv = invIds.contains(kantoList[0].pokedexId)
                val count = catchStats[kantoList[0].pokedexId] ?: 0
                showDetail(kantoList[0], isUnlocked, isInInv, count)
            }
        }
    }

    private fun getFallbackHint(id: String): String = when (id) {
        "010" -> "Hledej v trávě u Dashboardu. Caterpie je nenápadná, ale s poctivým zapisováním Maker ji jistě objevíš!"
        "007" -> "Cítíš sucho v krku? Vodní Pokémoni se k tvému Dashboardu nepřiblíží, dokud pořádně nehydratuješ a nesplníš dnešní cíl vody!"
        "092", "093", "094" -> "Někteří Pokémoni nesnáší slunce. Zkusil jsi někdy večerní trénink? Říká se, že po 19:00 můžeš narazit na staré duchy."
        "143" -> "Tento Pokémon tvrdě spí. Probudit ho dokáže jen poctivá ranní rutina. Zkus zapsat svůj spánek a váhu 7 dní v kuse!"
        "006" -> "Žhnoucí plameny Charizarda spatří jen opravdoví dříči. Pokračuj v konzistentním zapisování tréninků a budování návyků aspoň 14 dní."
        "150", "151" -> "Tajemná psychická energie pulzuje kdesi v nedohlednu. Získá ji jen ten, kdo se stane mistrem dlouhodobé disciplíny v MakroFlow."
        else -> "Zapiš trénink, udržuj disciplínu a vyraz ho hledat!"
    }

    private fun showDetail(pokemon: PokedexEntryEntity, isUnlocked: Boolean, isInInventory: Boolean, catchCount: Int) {
        tvDetailNumber.text = "#${pokemon.pokedexId}"
        tvDetailName.text = if (isUnlocked) pokemon.displayName else "???"
        tvDetailType.text = if (isUnlocked) pokemon.type.uppercase() else "???"

        if (isInInventory) {
            tvDetailType.backgroundTintList =
                android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#BC6C25"))
            tvDetailType.text = "${pokemon.type.uppercase()} • V INVENTÁŘI ($catchCount ×)"
        } else {
            tvDetailType.backgroundTintList =
                android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#DDA15E"))
            if (isUnlocked) {
                tvDetailType.text = "${pokemon.type.uppercase()} • ($catchCount ×)"
            }
        }

        tvDetailMacro.text = if (isUnlocked) {
            pokemon.macroDesc
        } else {
            if (pokemon.unlockedHint.isNotEmpty()) pokemon.unlockedHint else "Tento Pokémon ještě nebyl chycen. Zapiš trénink a vyraz ho hledat!"
        }

        val imageUrl =
            "https://img.pokemondb.net/sprites/firered-leafgreen/normal/${pokemon.webName}.png"

        ivDetailSprite.load(imageUrl) {
            placeholder(R.drawable.ic_home)
            error(R.drawable.ic_home)
        }

        if (!isUnlocked) {
            val matrix = android.graphics.ColorMatrix().apply { setSaturation(0f) }
            ivDetailSprite.colorFilter = android.graphics.ColorMatrixColorFilter(matrix)
            ivDetailSprite.alpha = 0.3f
        } else {
            ivDetailSprite.clearColorFilter()
            ivDetailSprite.alpha = 1.0f
        }

        if (isInInventory && (pokemon.pokedexId == "010" || pokemon.pokedexId == "025")) {
            btnTestEvo.visibility = View.VISIBLE
            btnTestEvo.setOnClickListener {
                lifecycleScope.launch(Dispatchers.IO) {
                    val lastCaught = db.capturedPokemonDao().getAllCaught()
                        .find { it.pokemonId == pokemon.pokedexId }

                    withContext(Dispatchers.Main) {
                        if (lastCaught != null) {
                            val growthProfile = PokemonGrowthManager.getProfile(pokemon.pokedexId)
                            val targetEvolveId = growthProfile?.evolutionToId ?: ""
                            val evolveLvl = growthProfile?.evolutionLevel ?: 1

                            if (targetEvolveId.isNotEmpty()) {
                                // 1. Zkusíme útok pro tabulkový level evoluce
                                var moveForEvo = PokemonGrowthManager.getNewMoveForLevel(
                                    targetEvolveId,
                                    evolveLvl
                                )

                                // ✅ 2. Fallback: Pokud tam nic není (jako u Raichu na levelu 6), vytáhneme automaticky startovní útok z Levelu 1 nové evoluce!
                                if (moveForEvo == null) {
                                    moveForEvo = PokemonGrowthManager.getNewMoveForLevel(targetEvolveId, 1)
                                }

                                val testEvoDialog = EvolutionDialog(
                                    context = requireContext(),
                                    capturedPokemonId = lastCaught.id,
                                    oldId = pokemon.pokedexId,
                                    newId = targetEvolveId,
                                    newMoveToLearn = moveForEvo,
                                    onComplete = {
                                        btnTestEvo.visibility = View.GONE
                                        isFirstLoad = true
                                        loadPokedex()

                                        (activity as? MainActivity)?.updatePokemonVisibility()
                                    }
                                )
                                testEvoDialog.show()
                            }
                        }
                    }
                }
            }
        } else {
            btnTestEvo.visibility = View.GONE
        }
    }

    private inner class PokedexAdapter(
        private var list: List<PokedexEntryEntity>,
        private var unlockedIds: List<String>,
        private var invIds: List<String>,
        private var catchStats: Map<String, Int>
    ) : RecyclerView.Adapter<PokedexAdapter.VH>() {

        fun updateData(newList: List<PokedexEntryEntity>, newUnlocked: List<String>, newInv: List<String>, newStats: Map<String, Int>) {
            this.list = newList
            this.unlockedIds = newUnlocked
            this.invIds = newInv
            this.catchStats = newStats
            notifyDataSetChanged()
        }

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val ivSprite: ImageView = v.findViewById(R.id.ivPokedexSprite)
            val tvName: TextView = v.findViewById(R.id.tvPokedexName)
            val tvNumber: TextView = v.findViewById(R.id.tvPokedexNumber)
            val cardView: com.google.android.material.card.MaterialCardView = v as com.google.android.material.card.MaterialCardView
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_pokedex_entry, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val pokemon = list[position]
            val isUnlocked = unlockedIds.contains(pokemon.pokedexId)
            val isInInventory = invIds.contains(pokemon.pokedexId)

            holder.tvName.text   = if (isUnlocked) pokemon.displayName else "???"
            holder.tvNumber.text = "#${pokemon.pokedexId}"

            if (isInInventory) {
                holder.cardView.strokeWidth = (2 * holder.itemView.context.resources.displayMetrics.density).toInt()
                holder.cardView.strokeColor = Color.parseColor("#BC6C25")
            } else {
                holder.cardView.strokeWidth = 0
            }

            val imageUrl = "https://img.pokemondb.net/sprites/firered-leafgreen/normal/${pokemon.webName}.png"
            holder.ivSprite.load(imageUrl) {
                crossfade(true)
                crossfade(150)
                placeholder(R.drawable.ic_home)
                error(R.drawable.ic_home)
                memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                diskCachePolicy(coil.request.CachePolicy.ENABLED)
                allowRgb565(true)
            }

            if (!isUnlocked) {
                val matrix = ColorMatrix().apply { setSaturation(0f) }
                holder.ivSprite.colorFilter = ColorMatrixColorFilter(matrix)
                holder.ivSprite.alpha = 0.4f
            } else {
                holder.ivSprite.clearColorFilter()
                holder.ivSprite.alpha = 1.0f
            }

            holder.itemView.setOnClickListener {
                val count = catchStats[pokemon.pokedexId] ?: 0
                showDetail(pokemon, isUnlocked, isInInventory, count)
            }
        }

        override fun getItemCount() = list.size
    }
}