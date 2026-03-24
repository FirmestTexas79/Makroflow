package cz.uhk.macroflow.pokemon

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
    private lateinit var topSection: android.widget.LinearLayout

    private lateinit var ivDetailSprite: ImageView
    private lateinit var tvDetailNumber: TextView
    private lateinit var tvDetailName: TextView
    private lateinit var tvDetailType: TextView
    private lateinit var tvDetailMacro: TextView

    private var isFirstLoad = true

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

        rvPokedex = view.findViewById(R.id.rvPokedex)
        rvPokedex.layoutManager = GridLayoutManager(requireContext(), 3)

        // Nastav paddingTop na RecyclerView podle výšky pevné horní části
        topSection = view.findViewById(R.id.pokedexTopSection)
        topSection.post {
            val topHeight = topSection.height
            rvPokedex.setPadding(
                rvPokedex.paddingLeft,
                topHeight,
                rvPokedex.paddingRight,
                rvPokedex.paddingBottom
            )
        }

        loadPokedex()
    }

    private fun loadPokedex() {
        if (!isFirstLoad) return
        isFirstLoad = false

        lifecycleScope.launch(Dispatchers.Main) {

            // ── Oprava Diglettu při prvním načtení ─────────────────
            withContext(Dispatchers.IO) {
                val diglett = db.pokedexEntryDao().getEntry("050")
                if (diglett != null &&
                    (diglett.macroDesc.contains("POOP Emoji") ||
                            diglett.macroDesc.contains("Zdravá strava") ||
                            diglett.macroDesc.startsWith("Zdravá"))) {
                    val fixed = diglett.copy(
                        type = "ZEMĚ / VLÁKNINA",
                        macroDesc = "Král ranního vyprazdňování! Tento podzemní tvor symbolizuje zdravou peristaltiku střev. Pokud tvůj trůnní rituál vázne, přidej rozpustnou vlákninu a dostatek vody."
                    )
                    db.pokedexEntryDao().insertAll(listOf(fixed))
                }
            }

            val caughtNames = withContext(Dispatchers.IO) {
                db.capturedPokemonDao().getAllCaught().map { it.name.uppercase() }
            }

            val kantoList = withContext(Dispatchers.IO) {
                db.pokedexEntryDao().getAllEntries()
            }

            rvPokedex.adapter = PokedexAdapter(kantoList, caughtNames)

            if (kantoList.isNotEmpty()) {
                val isCaught = caughtNames.contains(kantoList[0].displayName.uppercase())
                showDetail(kantoList[0], isCaught)
            }
        }
    }

    private fun showDetail(pokemon: PokedexEntryEntity, isCaught: Boolean) {
        tvDetailNumber.text = "#${pokemon.pokedexId}"
        tvDetailName.text   = if (isCaught) pokemon.displayName else "???"
        tvDetailType.text   = if (isCaught) pokemon.type.uppercase() else "???"

        tvDetailMacro.text = if (isCaught) {
            pokemon.macroDesc
        } else {
            "Tento Pokémon ještě nebyl chycen. Zapiš trénink a vyraz ho hledat!"
        }

        val imageUrl = "https://img.pokemondb.net/sprites/home/normal/${pokemon.webName}.png"
        ivDetailSprite.load(imageUrl) {
            placeholder(R.drawable.ic_home)
            error(R.drawable.ic_home)
        }

        if (!isCaught) {
            val matrix = ColorMatrix().apply { setSaturation(0f) }
            ivDetailSprite.colorFilter = ColorMatrixColorFilter(matrix)
            ivDetailSprite.alpha = 0.3f
        } else {
            ivDetailSprite.clearColorFilter()
            ivDetailSprite.alpha = 1.0f
        }
    }

    private inner class PokedexAdapter(
        private val list: List<PokedexEntryEntity>,
        private val caughtNames: List<String>
    ) : RecyclerView.Adapter<PokedexAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val ivSprite:  ImageView = v.findViewById(R.id.ivPokedexSprite)
            val tvName:    TextView  = v.findViewById(R.id.tvPokedexName)
            val tvNumber:  TextView  = v.findViewById(R.id.tvPokedexNumber)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_pokedex_entry, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val pokemon  = list[position]
            val isCaught = caughtNames.contains(pokemon.displayName.uppercase())

            holder.tvName.text   = if (isCaught) pokemon.displayName else "???"
            holder.tvNumber.text = "#${pokemon.pokedexId}"

            val imageUrl = "https://img.pokemondb.net/sprites/home/normal/${pokemon.webName}.png"
            holder.ivSprite.load(imageUrl) {
                placeholder(R.drawable.ic_home)
                error(R.drawable.ic_home)
            }

            if (!isCaught) {
                val matrix = ColorMatrix().apply { setSaturation(0f) }
                holder.ivSprite.colorFilter = ColorMatrixColorFilter(matrix)
                holder.ivSprite.alpha = 0.4f
            } else {
                holder.ivSprite.clearColorFilter()
                holder.ivSprite.alpha = 1.0f
            }

            holder.itemView.setOnClickListener {
                showDetail(pokemon, isCaught)
            }
        }

        override fun getItemCount() = list.size
    }
}