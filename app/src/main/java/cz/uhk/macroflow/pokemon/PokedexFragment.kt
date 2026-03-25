package cz.uhk.macroflow.pokemon

import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.R
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
        tvDetailName = view.findViewById(R.id.tvDetailName)
        tvDetailType = view.findViewById(R.id.tvDetailType)
        tvDetailMacro = view.findViewById(R.id.tvDetailMacro)

        rvPokedex = view.findViewById(R.id.rvPokedex)
        rvPokedex.layoutManager = GridLayoutManager(requireContext(), 3)

        pokedexAdapter = PokedexAdapter(emptyList(), emptyList(), emptyList())
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
                db.pokedexStatusDao().getUnlockedIds()
            }

            val kantoList = withContext(Dispatchers.IO) {
                db.pokedexEntryDao().getAllEntries()
            }

            pokedexAdapter.updateData(kantoList, unlockedIds, invIds)

            if (kantoList.isNotEmpty()) {
                val isUnlocked = unlockedIds.contains(kantoList[0].pokedexId)
                val isInInv = invIds.contains(kantoList[0].pokedexId)
                showDetail(kantoList[0], isUnlocked, isInInv)
            }
        }
    }

    private fun showDetail(
        pokemon: PokedexEntryEntity,
        isUnlocked: Boolean,
        isInInventory: Boolean
    ) {
        tvDetailNumber.text = "#${pokemon.pokedexId}"
        tvDetailName.text = if (isUnlocked) pokemon.displayName else "???"
        tvDetailType.text = if (isUnlocked) pokemon.type.uppercase() else "???"

        if (isInInventory) {
            tvDetailType.backgroundTintList =
                android.content.res.ColorStateList.valueOf(Color.parseColor("#BC6C25"))
            tvDetailType.text = "${pokemon.type.uppercase()} • V INVENTÁŘI"
        } else {
            tvDetailType.backgroundTintList =
                android.content.res.ColorStateList.valueOf(Color.parseColor("#DDA15E"))
        }

        tvDetailMacro.text =
            if (isUnlocked) pokemon.macroDesc else "Tento Pokémon ještě nebyl chycen. Zapiš trénink a vyraz ho hledat!"

        // ✅ PIXELOVÉ SPRITY DO POKEDEXU Z FIRERED-LEAFGREEN
        val imageUrl =
            "https://img.pokemondb.net/sprites/firered-leafgreen/normal/${pokemon.webName}.png"

        ivDetailSprite.load(imageUrl) {
            placeholder(R.drawable.ic_home)
            error(R.drawable.ic_home)
        }

        if (!isUnlocked) {
            val matrix = ColorMatrix().apply { setSaturation(0f) }
            ivDetailSprite.colorFilter = ColorMatrixColorFilter(matrix)
            ivDetailSprite.alpha = 0.3f
        } else {
            ivDetailSprite.clearColorFilter()
            ivDetailSprite.alpha = 1.0f
        }
    }

    private inner class PokedexAdapter(
        private var list: List<PokedexEntryEntity>,
        private var unlockedIds: List<String>,
        private var invIds: List<String>
    ) : RecyclerView.Adapter<PokedexAdapter.VH>() {

        fun updateData(
            newList: List<PokedexEntryEntity>,
            newUnlocked: List<String>,
            newInv: List<String>
        ) {
            this.list = newList
            this.unlockedIds = newUnlocked
            this.invIds = newInv
            notifyDataSetChanged()
        }

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val ivSprite: ImageView = v.findViewById(R.id.ivPokedexSprite)
            val tvName: TextView = v.findViewById(R.id.tvPokedexName)
            val tvNumber: TextView = v.findViewById(R.id.tvPokedexNumber)

            // 🛡️ NAJDEME KOŘENOVÝ CARDVIEW BEZ NUTNOSTI ZNÁT JEHO PŘESNÉ XML ID!
            val cardView: com.google.android.material.card.MaterialCardView =
                v as com.google.android.material.card.MaterialCardView
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_pokedex_entry, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val pokemon = list[position]
            val isUnlocked = unlockedIds.contains(pokemon.pokedexId)
            val isInInventory = invIds.contains(pokemon.pokedexId)

            holder.tvName.text = if (isUnlocked) pokemon.displayName else "???"
            holder.tvNumber.text = "#${pokemon.pokedexId}"

            // 🌟 ZLATÝ RÁMEČEK (Když ho máš v kapse)
            if (isInInventory) {
                holder.cardView.strokeWidth =
                    (2 * holder.itemView.context.resources.displayMetrics.density).toInt()
                holder.cardView.strokeColor = Color.parseColor("#BC6C25") // Tvoje hnědá barva
            } else {
                holder.cardView.strokeWidth = 0
            }

            val imageUrl =
                "https://img.pokemondb.net/sprites/firered-leafgreen/normal/${pokemon.webName}.png"
            holder.ivSprite.load(imageUrl)

            if (!isUnlocked) {
                val matrix = ColorMatrix().apply { setSaturation(0f) }
                holder.ivSprite.colorFilter = ColorMatrixColorFilter(matrix)
                holder.ivSprite.alpha = 0.4f
            } else {
                holder.ivSprite.clearColorFilter()
                holder.ivSprite.alpha = 1.0f
            }

            holder.itemView.setOnClickListener {
                showDetail(pokemon, isUnlocked, isInInventory)
            }
        }

        override fun getItemCount() = list.size
    }
}