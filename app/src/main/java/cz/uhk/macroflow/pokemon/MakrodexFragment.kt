package cz.uhk.macroflow.pokemon

import android.content.Context
import android.content.res.ColorStateList
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
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.R
import cz.uhk.macroflow.common.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MakrodexFragment : Fragment() {

    private lateinit var rvMakrodex: RecyclerView
    private lateinit var db: AppDatabase

    private lateinit var ivDetailSprite: ImageView
    private lateinit var tvDetailNumber: TextView
    private lateinit var tvDetailName: TextView
    private lateinit var tvDetailType: TextView
    private lateinit var tvDetailMacro: TextView
    private lateinit var btnTestEvo: Button

    private var isFirstLoad = true
    private lateinit var makrodexAdapter: MakrodexAdapter

    private val C_BROWN   = 0xFFBC6C25.toInt()
    private val C_ACCENT  = 0xFFDDA15E.toInt()
    private val C_GOLD    = 0xFFE9B072.toInt()

    // ── Shiny Makrodex (docs/adr/0016) ──
    private enum class Mode { DEX, SHINY_SEEN, SHINY_CAUGHT }
    private var mode = Mode.DEX
    private var allEntries: List<MakrodexEntryEntity> = emptyList()
    private var unlockedCache: List<String> = emptyList()
    private var invCache: List<String> = emptyList()
    private var statsCache: Map<String, Int> = emptyMap()
    private var shinySeen: Set<String> = emptySet()
    private var shinyCaught: Set<String> = emptySet()

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

        rvMakrodex = view.findViewById(R.id.rvPokedex)
        rvMakrodex.layoutManager = GridLayoutManager(requireContext(), 3)

        makrodexAdapter = MakrodexAdapter(emptyList(), emptyList(), emptyList(), emptyMap())
        rvMakrodex.adapter = makrodexAdapter

        view.findViewById<View>(R.id.chipDex).setOnClickListener { setMode(Mode.DEX) }
        view.findViewById<View>(R.id.chipShinySeen).setOnClickListener { setMode(Mode.SHINY_SEEN) }
        view.findViewById<View>(R.id.chipShinyCaught).setOnClickListener { setMode(Mode.SHINY_CAUGHT) }
        styleChips()

        loadMakrodex()
    }

    private fun loadMakrodex() {
        if (!isFirstLoad) return
        isFirstLoad = false

        lifecycleScope.launch(Dispatchers.Main) {
            val (invIds, unlockedIds, catchStats) = withContext(Dispatchers.IO) {
                val caught   = db.capturedMakromonDao().getAllCaught()
                val inv      = caught.map { it.makromonId }.toSet()
                val status   = db.makrodexStatusDao().getUnlockedIds()
                val unlocked = (status + inv).distinct()
                val stats    = caught.groupBy { it.makromonId }.mapValues { it.value.size }
                shinyCaught  = caught.filter { it.isShiny }.map { it.makromonId }.toSet()
                Triple(inv.toList(), unlocked, stats)
            }
            // Viděné shiny: zápis ze soubojů + všichni chycení shiny (i z doby před evidencí)
            shinySeen = requireContext().getSharedPreferences("GamePrefs", Context.MODE_PRIVATE)
                .getStringSet(cz.uhk.macroflow.pokemon.shiny.ShinyDex.SEEN_KEY, emptySet()).orEmpty() + shinyCaught

            val filteredList = withContext(Dispatchers.IO) {
                val definedIds = SpawnManager.allEntries.map { it.id }
                val allFromDb  = db.makrodexEntryDao().getAllEntries()
                allFromDb.filter { entry -> definedIds.contains(entry.makrodexId) }
                    .sortedBy { it.makrodexId }
            }

            allEntries = filteredList
            unlockedCache = unlockedIds; invCache = invIds; statsCache = catchStats
            applyMode()
        }
    }

    private fun setMode(m: Mode) {
        if (m == mode) return
        mode = m
        styleChips()
        applyMode()
    }

    private fun styleChips() {
        val v = view ?: return
        listOf(R.id.chipDex to Mode.DEX, R.id.chipShinySeen to Mode.SHINY_SEEN, R.id.chipShinyCaught to Mode.SHINY_CAUGHT)
            .forEach { (id, m) ->
                val chip = v.findViewById<TextView>(id)
                val active = m == mode
                val activeColor = if (m == Mode.DEX) 0xFF606C38.toInt() else C_BROWN
                chip.backgroundTintList = ColorStateList.valueOf(if (active) activeColor else 0x1A606C38)
                chip.setTextColor(if (active) 0xFFFEFAE0.toInt() else if (m == Mode.DEX) 0xFF606C38.toInt() else C_BROWN)
            }
    }

    /** Naplní mřížku podle záložky; ve shiny záložkách jen viděné / chycené v jejich barvách. */
    private fun applyMode() {
        val v = view ?: return
        val ids = allEntries.map { it.makrodexId }
        val list = when (mode) {
            Mode.DEX -> allEntries
            Mode.SHINY_SEEN, Mode.SHINY_CAUGHT -> {
                val filter = if (mode == Mode.SHINY_SEEN) cz.uhk.macroflow.pokemon.shiny.ShinyDex.Filter.SEEN
                    else cz.uhk.macroflow.pokemon.shiny.ShinyDex.Filter.CAUGHT
                val keep = cz.uhk.macroflow.pokemon.shiny.ShinyDex.visible(ids, filter, shinySeen, shinyCaught).toSet()
                allEntries.filter { it.makrodexId in keep }
            }
        }
        makrodexAdapter.shinyMode = mode != Mode.DEX
        makrodexAdapter.updateData(list, unlockedCache, invCache, statsCache)

        val separator = v.findViewById<TextView>(R.id.tvListSeparator)
        val empty = v.findViewById<TextView>(R.id.tvShinyEmpty)
        if (mode == Mode.DEX) {
            separator.setText(R.string.pokedex_list_separator)
            empty.visibility = View.GONE
        } else {
            val (seenN, caughtN) = cz.uhk.macroflow.pokemon.shiny.ShinyDex.counts(ids, shinySeen, shinyCaught)
            separator.text = "✦ VIDĚNO $seenN · CHYCENO $caughtN / ${ids.size}"
            empty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            empty.text = if (mode == Mode.SHINY_SEEN)
                "Zatím jsi žádného shiny Makromona nepotkal.\nŠance je 1 : 100 – každé setkání se počítá!"
            else "Zatím nemáš chyceného shiny.\nAž nějakého chytíš, objeví se tu v jeho barvách."
        }

        list.firstOrNull()?.let { first ->
            showDetail(first, unlockedCache.contains(first.makrodexId) || mode != Mode.DEX,
                invCache.contains(first.makrodexId), statsCache[first.makrodexId] ?: 0)
        }
    }

    private fun getFallbackHint(id: String): String = when (id) {
        "001", "002", "003" -> "Ignar se probouzí teplem tvého tréninku. Zapiš dnešní cvičení!"
        "004", "005", "006" -> "Aqulin připluje jen tehdy, když splníš svůj denní vodní cíl."
        "007", "008", "009" -> "Flori roste tam, kde je zdravá strava. Zapiš dnešní jídla!"
        "010"               -> "Umbex se toulá v noci. Zkus večerní trénink po 19:00."
        "011"               -> "Lumex je velmi vzácný a toulá se pouze v noci."
        "012"               -> "Spirra je nejčastější Makromon. Hledej ji všude kolem sebe!"
        "013"               -> "Flamirra přijde, když pravidelně trénuješ."
        "014"               -> "Aquirra se objeví po splnění vodního cíle."
        "015"               -> "Verdirra miluje čerstvý vzduch. Cvič venku!"
        "016"               -> "Shadirra se toulá pouze v noci po 19:00."
        "017"               -> "Charmirra přijde, když splníš svá makra."
        "018"               -> "Glacirra přichází nečekaně. Buď konzistentní!"
        "019"               -> "Drakirra je skrytá evoluce. Jen 30 check-inů ji přivolá."
        "020"               -> "Finlet je velmi běžný. Hledej ho všude kolem sebe."
        "021"               -> "Serpfin se vyvine z Finleta na levelu 8. Věř procesu!"
        "022"               -> "Mycit žije na okrajích lesů a luk."
        "023"               -> "Mydrus se vyvine z Mycita. Po 5 check-inech ho najdeš."
        "024", "025", "026" -> "Soulu rodina se toulá pouze v noci."
        "027", "028", "029" -> "Phantil rodina se toulá v noci u vodních ploch."
        "030"               -> "Gudwin vychází ven až po 7 poctivých check-inech."
        "031"               -> "Axlu se ukáže jen těm nejdisciplinovanějším – 50 check-inů!"
        else                -> "Zapiš trénink a jídlo, Makromon se brzy objeví!"
    }

    private fun showDetail(
        entry: MakrodexEntryEntity,
        isUnlocked: Boolean,
        isInInventory: Boolean,
        catchCount: Int
    ) {
        tvDetailNumber.text = "#${entry.makrodexId}"
        tvDetailName.text   = if (isUnlocked) entry.displayName else "???"

        val typeColor = if (isInInventory) C_BROWN else C_ACCENT
        tvDetailType.backgroundTintList = ColorStateList.valueOf(typeColor)
        val shinyStatus = if (mode == Mode.DEX) null
            else cz.uhk.macroflow.pokemon.shiny.ShinyDex.status(entry.makrodexId, shinySeen, shinyCaught)
        if (shinyStatus != null) tvDetailType.backgroundTintList = ColorStateList.valueOf(
            if (shinyStatus == cz.uhk.macroflow.pokemon.shiny.ShinyDex.Status.CAUGHT) C_GOLD else C_ACCENT)
        tvDetailType.text = when {
            shinyStatus == cz.uhk.macroflow.pokemon.shiny.ShinyDex.Status.CAUGHT -> "✦ SHINY • CHYCENO"
            shinyStatus == cz.uhk.macroflow.pokemon.shiny.ShinyDex.Status.SEEN -> "✦ SHINY • VIDĚNO"
            isInInventory -> "${entry.type.uppercase()} • V INVENTÁŘI ($catchCount ×)"
            isUnlocked    -> "${entry.type.uppercase()} • ($catchCount ×)"
            else          -> "???"
        }

        tvDetailMacro.text = if (isUnlocked) {
            entry.macroDesc
        } else {
            val hint = getFallbackHint(entry.makrodexId)
            if (hint.startsWith("Zapiš") && entry.unlockedHint.isNotEmpty()) entry.unlockedHint else hint
        }

        // --- DYNAMICKÉ SESTAVENÍ NÁZVU OBRÁZKU ---
        val shortId = if (entry.makrodexId.length >= 3) entry.makrodexId.takeLast(2) else entry.makrodexId
        val namePart = entry.displayName.lowercase().trim()
        val dynamicName = "makromon_${shortId}_$namePart"

        val resId = requireContext().resources.getIdentifier(
            dynamicName, "drawable", requireContext().packageName
        )
        if (resId != 0) cz.uhk.macroflow.pokemon.shiny.ShinySprites.into(ivDetailSprite, resId, entry.makrodexId, shiny = shinyStatus != null)
        else ivDetailSprite.setImageResource(R.drawable.ic_home)

        if (!isUnlocked) {
            val matrix = ColorMatrix().apply { setSaturation(0f) }
            ivDetailSprite.colorFilter = ColorMatrixColorFilter(matrix)
            ivDetailSprite.alpha = 0.3f
        } else {
            ivDetailSprite.clearColorFilter()
            ivDetailSprite.alpha = 1.0f
        }

        // Evoluce tlačítko
        if (isInInventory && entry.makrodexId == cz.uhk.macroflow.pokemon.evolution.SpirraEvolution.SPIRRA_ID) {
            // Spirra se vyvíjí podle činností, ne podle levelu (docs/adr/0031)
            btnTestEvo.visibility = View.VISIBLE
            btnTestEvo.text = "Cesty vývoje"
            btnTestEvo.setOnClickListener { showSpirraPaths() }
        } else if (isInInventory) {
            btnTestEvo.text = getString(R.string.pokedex_btn_evo_test)
            val profile = MakromonGrowthManager.getProfile(entry.makrodexId)
            if (profile != null && profile.evolutionToId.isNotEmpty()) {
                btnTestEvo.visibility = View.VISIBLE
                btnTestEvo.setOnClickListener {
                    lifecycleScope.launch(Dispatchers.IO) {
                        val lastCaught = db.capturedMakromonDao().getAllCaught()
                            .find { it.makromonId == entry.makrodexId }

                        withContext(Dispatchers.Main) {
                            if (lastCaught != null) {
                                val moveForEvo = MakromonGrowthManager.getNewMoveForLevel(
                                    profile.evolutionToId, profile.evolutionLevel
                                ) ?: MakromonGrowthManager.getNewMoveForLevel(profile.evolutionToId, 1)

                                EvolutionDialog(
                                    context        = requireContext(),
                                    capturedMakromonId = lastCaught.id,
                                    oldId          = entry.makrodexId,
                                    newId          = profile.evolutionToId,
                                    newMoveToLearn = moveForEvo,
                                    onComplete = {
                                        isFirstLoad = true
                                        loadMakrodex()
                                        // Vynutíme přenačtení spritu na liště
                                        requireContext().getSharedPreferences("GamePrefs", Context.MODE_PRIVATE)
                                            .edit().putString("currentOnBarId", "").apply()
                                        (activity as? MainActivity)?.updateMakromonVisibility()
                                    }
                                ).show()
                            }
                        }
                    }
                }
            } else {
                btnTestEvo.visibility = View.GONE
            }
        } else {
            btnTestEvo.visibility = View.GONE
        }
    }

    /** Přehled větví vývoje Spirry a postupu aktivní Spirry. */
    private fun showSpirraPaths() {
        val ctx = requireContext().applicationContext
        lifecycleScope.launch {
            val (active, progress) = withContext(Dispatchers.IO) {
                val sp = cz.uhk.macroflow.pokemon.evolution.SpirraBond.activeSpirra(ctx)
                sp to sp?.let { cz.uhk.macroflow.pokemon.evolution.SpirraBond.progress(ctx, it.id) }
            }
            if (!isAdded) return@launch
            val SE = cz.uhk.macroflow.pokemon.evolution.SpirraEvolution
            val text = buildString {
                append(if (active == null) "Počítá se, jen když je Spirra tvým aktivním parťákem na liště. Nastav ji v inventáři.\n\n"
                       else "Spirra se vyvine podle toho, který cíl splníš první:\n\n")
                SE.Branch.entries.forEach { b ->
                    val v = progress?.get(b) ?: 0
                    val pct = (SE.fraction(b, v) * 100).toInt()
                    append("${b.displayName.uppercase()}\n${b.task}\n")
                    append(if (progress != null) "${SE.progressText(b, v)}  ($pct %)\n\n" else "\n")
                }
                append("DRAKIRRA\n??? – tajná, zatím jen k ulovení")
            }
            com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("Cesty vývoje Spirry")
                .setMessage(text)
                .setPositiveButton("Zavřít", null)
                .show()
        }
    }

    private inner class MakrodexAdapter(
        private var list: List<MakrodexEntryEntity>,
        private var unlockedIds: List<String>,
        private var invIds: List<String>,
        private var catchStats: Map<String, Int>
    ) : RecyclerView.Adapter<MakrodexAdapter.VH>() {

        /** Shiny záložka: sprity v shiny barvách, zlatý rámeček u chycených. */
        var shinyMode = false

        fun updateData(
            newList: List<MakrodexEntryEntity>,
            newUnlocked: List<String>,
            newInv: List<String>,
            newStats: Map<String, Int>
        ) {
            list        = newList
            unlockedIds = newUnlocked
            invIds      = newInv
            catchStats  = newStats
            notifyDataSetChanged()
        }

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val ivSprite:  ImageView = v.findViewById(R.id.ivPokedexSprite)
            val tvName:    TextView  = v.findViewById(R.id.tvPokedexName)
            val tvNumber:  TextView  = v.findViewById(R.id.tvPokedexNumber)
            val cardView:  com.google.android.material.card.MaterialCardView =
                v as com.google.android.material.card.MaterialCardView
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_pokedex_entry, parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) {
            val m             = list[position]
            val isUnlocked    = unlockedIds.contains(m.makrodexId) || shinyMode
            val isInInventory = invIds.contains(m.makrodexId)

            holder.tvName.text   = if (isUnlocked) m.displayName else "???"
            holder.tvNumber.text = "#${m.makrodexId}"
            if (shinyMode) {
                val caught = m.makrodexId in shinyCaught
                holder.cardView.strokeWidth = if (caught) 6 else 0
                holder.cardView.strokeColor = C_GOLD
            } else {
                holder.cardView.strokeWidth = if (isInInventory) 6 else 0
                holder.cardView.strokeColor = C_BROWN
            }

            // --- DYNAMICKÉ SESTAVENÍ NÁZVU OBRÁZKU V ADAPTÉRU ---
            val shortId = if (m.makrodexId.length >= 3) m.makrodexId.takeLast(2) else m.makrodexId
            val namePart = m.displayName.lowercase().trim()
            val dynamicName = "makromon_${shortId}_$namePart"

            val resId = holder.itemView.context.resources.getIdentifier(
                dynamicName, "drawable", holder.itemView.context.packageName
            )
            if (resId != 0) cz.uhk.macroflow.pokemon.shiny.ShinySprites.into(holder.ivSprite, resId, m.makrodexId, shiny = shinyMode)
            else holder.ivSprite.setImageResource(R.drawable.ic_home)

            if (!isUnlocked) {
                val matrix = ColorMatrix().apply { setSaturation(0f) }
                holder.ivSprite.colorFilter = ColorMatrixColorFilter(matrix)
                holder.ivSprite.alpha = 0.4f
            } else {
                holder.ivSprite.clearColorFilter()
                holder.ivSprite.alpha = 1.0f
            }

            holder.itemView.setOnClickListener {
                showDetail(m, isUnlocked, isInInventory, catchStats[m.makrodexId] ?: 0)
            }
        }

        override fun getItemCount() = list.size
    }
}