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
            val (invIds, unlockedIds, catchStats) = withContext(Dispatchers.IO) {
                val caught = db.capturedPokemonDao().getAllCaught()
                val inv = caught.map { it.pokemonId }.toSet()
                val status = db.pokedexStatusDao().getUnlockedIds()
                val unlocked = (status + inv).distinct()
                val stats = caught.groupBy { it.pokemonId }.mapValues { it.value.size }
                Triple(inv.toList(), unlocked, stats)
            }

            val filteredList = withContext(Dispatchers.IO) {
                // 1. Získáme seznam všech IDs, která MAJÍ v AppDatabase definovaný popisek
                // (Tyhle IDs odpovídají tomu, co máš v tom velkém poli Triple v AppDatabase)
                val definedIds = listOf(
                    "001", // Bulbasaur
                    "002", // Ivysaur
                    "003", // Venusaur
                    "004", // Charmander
                    "005", // Charmeleon
                    "006", // Charizard
                    "007", // Squirtle
                    "008", // Wartortle
                    "009", // Blastoise
                    "010", // Caterpie
                    "011", // Metapod (Evoluce)
                    "012", // Butterfree (Evoluce)
                    "013", // Weedle
                    "014", // Kakuna (Evoluce)
                    "015", // Beedrill (Evoluce)
                    "016", // Pidgey
                    "017", // Pidgeotto (Evoluce)
                    "018", // Pidgeot (Evoluce)
                    "019", // Rattata
                    "020", // Raticate (Evoluce)
                    "021", // Spearow
                    "022", // Fearow
                    "023", // Ekans
                    "024", // Arbok
                    "025", // Pikachu
                    "026", // Raichu (Evoluce)
                    "050", // Diglett
                    "051", // Dugtrio (Evoluce)
                    "092", // Gastly
                    "093", // Haunter
                    "094", // Gengar
                    "115", // Kangaskhan
                    "131", // Lapras
                    "132", // Ditto
                    "133", // Eevee
                    "137", // Porygon
                    "143", // Snorlax
                    "150", // Mewtwo
                    "151"  // Mew
                )
                // 2. Načteme vše z DB
                val allFromDb = db.pokedexEntryDao().getAllEntries()

                // 3. Pustíme dál jen ty, jejichž ID je v našem seznamu "povolených"
                allFromDb.filter { entry ->
                    definedIds.contains(entry.pokedexId)
                }.sortedBy { it.pokedexId }
            }

            pokedexAdapter.updateData(filteredList, unlockedIds, invIds, catchStats)

            if (filteredList.isNotEmpty()) {
                val first = filteredList[0]
                showDetail(first, unlockedIds.contains(first.pokedexId), invIds.contains(first.pokedexId), catchStats[first.pokedexId] ?: 0)
            }
        }
    }

    private fun getFallbackHint(id: String): String = when (id) {
        // --- STARTÉŘI (001 - 009) ---
        "001", "002", "003" -> "Tito travní Pokémoni milují čistou stravu. Zkus dnes zapsat aspoň jedno jídlo bohaté na vlákninu a zeleninu, aby se ukázali!"
        "004" -> "Žár Charmandera pocítíš, jen když se pořádně zapotíš. Zapiš svůj dnešní trénink a sleduj, jestli se někde neobjeví plamínek."
        "005" -> "Já tuhle špeluňu podpálím. (Ale jen pokud dneska pořádně mákneš!)"
        "006" -> "Žhnoucí plameny Charizarda spatří jen opravdoví dříči. Pokračuj v konzistentním zapisování tréninků a budování návyků aspoň 14 dní."
        "007" -> "Cítíš sucho v krku? Vodní Pokémoni se k tvému Dashboardu nepřiblíží, dokud pořádně nehydratuješ a nesplníš dnešní cíl vody!"
        "008", "009" -> "Hluboké vody vyžadují disciplínu. Udržuj svůj pitný režim na 100 % po dobu 3 dnů a Blastoise tě možná poctí návštěvou."

        // --- HMYZ (010 - 015) ---
        "010", "011", "012" -> "Hledej v trávě u Dashboardu. Caterpie je nenápadná, ale s poctivým zapisováním Maker ji jistě objevíš!"
        "013", "014", "015" -> "Hmyzí Pokémoni milují ranní rosu. Zkus zapsat svou snídani před 8:00 ráno a uvidíš, jestli se v trávě něco nepohne!"

        // --- PTÁCI (016 - 018) ---
        "016", "017", "018" -> "Pidgey monitoruje tvou rychlost. Tohoto létače přilákáš jen tak, že tvůj průměrný denní počet kroků neklesne pod 6 000 po dobu 3 dnů."
        "019", "020" -> "Rattata je rychlá a neustále něco kouše. Zkus dnes zapsat 3 zdravé snacky (ovoce nebo ořechy) místo sladkostí, abys ji zahnal do pasti!"
        "021", "022" -> "Spearow je velmi teritoriální. Ukáže se ti jen tehdy, pokud dnes v rámci svého tréninku navštívíš aspoň jedno nové místo (novou trasu na procházku)!"
        "023", "024" -> "Ekans se plazí tiše v trávě. Vyžaduje trpělivost – zkus dnes vydržet bez cheat-mealu a zapiš poctivě všechna jídla dne."

        // --- ELEKTRICKÁ RODINA (025 - 026) ---
        "025", "026" -> "Elektřina vyžaduje energii. Zkus dneska dodržet svůj kalorický cíl (nebuď v přílišném deficitu), aby měl Pikachu kde dobít baterky!"

        // --- ZEMNÍ A OSTATNÍ (050 - 137) ---
        "050" -> "Diglett se skrývá pod povrchem tvé rutiny. Zkus zapsat svou váhu hned po probuzení, abys ho vyhrabal ze země."
        "051" -> "Tři shity jdou, v řadě za sebou (Tomáš, Sam a Vítek). Chytit je můžeš jen poctivým skupinovým tréninkem!"
        "092", "093", "094" -> "Někteří Pokémoni nesnáší slunce. Zkusil jsi někdy večerní trénink? Říká se, že po 19:00 můžeš narazit na staré duchy."
        "115" -> "Kangaskhan chrání své mladé, stejně jako ty musíš chránit své svaly. Zapiš dnes dostatečný příjem bílkovin!"
        "131" -> "Pluje si životem a nenechá se ničím rozhodit. Zkus udržet cíl vody na 100 % po dobu 7 dní v kuse."
        "132" -> "Ditto se dokáže přizpůsobit čemukoliv. Zkus dnes zapsat jídlo, které jsi v aplikaci ještě nikdy neměl – miluje pestrost!"
        "133" -> "Eevee je symbolem potenciálu. Ukaž svou všestrannost a zapiš dnes trénink i meditaci nebo spánek."
        "137" -> "Data, data a zase data. Býval jsem studentem Statistiky a vím, že bez zápisu Maker se Porygon v kódu neobjeví."

        // --- OBŘI A LEGENDY (143 - 151) ---
        "143" -> "Tento Pokémon tvrdě spí. Probudit ho dokáže jen poctivá ranní rutina. Zkus zapsat svůj spánek a váhu 7 dní v kuse!"
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
            // Pokud NENÍ chycený, ukaž fitness radu (hint)
            // Nejdřív zkusíme, jestli máme specifickou radu v getFallbackHint
            val hint = getFallbackHint(pokemon.pokedexId)

            // Pokud getFallbackHint vrátí obecný "else" (Zapiš trénink...),
            // zkontrolujeme, jestli není něco přímo v DB entitě (unlockedHint)
            if (hint.startsWith("Zapiš trénink") && pokemon.unlockedHint.isNotEmpty()) {
                pokemon.unlockedHint
            } else {
                hint
            }
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