package cz.uhk.macroflow.pokemon

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
                Triple(inv.toList(), unlocked, stats)
            }

            val filteredList = withContext(Dispatchers.IO) {
                val definedIds = SpawnManager.allEntries.map { it.id }
                val allFromDb  = db.makrodexEntryDao().getAllEntries()
                allFromDb.filter { entry -> definedIds.contains(entry.makrodexId) }
                    .sortedBy { it.makrodexId }
            }

            makrodexAdapter.updateData(filteredList, unlockedIds, invIds, catchStats)

            if (filteredList.isNotEmpty()) {
                val first = filteredList[0]
                showDetail(
                    first,
                    unlockedIds.contains(first.makrodexId),
                    invIds.contains(first.makrodexId),
                    catchStats[first.makrodexId] ?: 0
                )
            }
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
        tvDetailType.text = when {
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
        ivDetailSprite.setImageResource(if (resId != 0) resId else R.drawable.ic_home)

        if (!isUnlocked) {
            val matrix = ColorMatrix().apply { setSaturation(0f) }
            ivDetailSprite.colorFilter = ColorMatrixColorFilter(matrix)
            ivDetailSprite.alpha = 0.3f
        } else {
            ivDetailSprite.clearColorFilter()
            ivDetailSprite.alpha = 1.0f
        }

        // Evoluce tlačítko
        if (isInInventory) {
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
                                    onComplete     = {
                                        isFirstLoad = true
                                        loadMakrodex()
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

    private inner class MakrodexAdapter(
        private var list: List<MakrodexEntryEntity>,
        private var unlockedIds: List<String>,
        private var invIds: List<String>,
        private var catchStats: Map<String, Int>
    ) : RecyclerView.Adapter<MakrodexAdapter.VH>() {

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
            val isUnlocked    = unlockedIds.contains(m.makrodexId)
            val isInInventory = invIds.contains(m.makrodexId)

            holder.tvName.text   = if (isUnlocked) m.displayName else "???"
            holder.tvNumber.text = "#${m.makrodexId}"
            holder.cardView.strokeWidth = if (isInInventory) 6 else 0
            holder.cardView.strokeColor = C_BROWN

            // --- DYNAMICKÉ SESTAVENÍ NÁZVU OBRÁZKU V ADAPTÉRU ---
            val shortId = if (m.makrodexId.length >= 3) m.makrodexId.takeLast(2) else m.makrodexId
            val namePart = m.displayName.lowercase().trim()
            val dynamicName = "makromon_${shortId}_$namePart"

            val resId = holder.itemView.context.resources.getIdentifier(
                dynamicName, "drawable", holder.itemView.context.packageName
            )
            holder.ivSprite.setImageResource(if (resId != 0) resId else R.drawable.ic_home)

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