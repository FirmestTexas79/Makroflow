package cz.uhk.macroflow.pokemon.trainer

import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.text.InputFilter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import cz.uhk.macroflow.R
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.data.FirebaseRepository
import cz.uhk.macroflow.pokemon.CapturedMakromonEntity
import cz.uhk.macroflow.pokemon.PokemonBattleFragment
import cz.uhk.macroflow.pokemon.skills.SkillStore
import cz.uhk.macroflow.pokemon.species.SpeciesRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Aréna (docs/adr/0076): přehled týmu, AI trenéři dne a duchové ostatních hráčů.
 * Souboj leží přes celou aplikaci; po jeho zavření se obrazovka obnoví (výsledky, nový level).
 */
class ArenaFragment : Fragment() {

    private var myTeam: List<TrainerMon> = emptyList()
    private val backStackListener = FragmentManager.OnBackStackChangedListener { if (isAdded) load(publish = false) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_arena, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<TextView>(R.id.tvArenaName).setOnClickListener { editName() }
        requireActivity().supportFragmentManager.addOnBackStackChangedListener(backStackListener)
        load(publish = true)
    }

    override fun onDestroyView() {
        requireActivity().supportFragmentManager.removeOnBackStackChangedListener(backStackListener)
        super.onDestroyView()
    }

    /** Tým v pořadí jako v souboji: aktivní parťák, pak ostatní členové. */
    private fun loadTeam(): List<CapturedMakromonEntity> {
        val ctx = requireContext().applicationContext
        val dao = AppDatabase.getDatabase(ctx).capturedMakromonDao()
        val active = SkillStore.activeId(ctx)
        val ids = (listOfNotNull(active) + SkillStore.team(ctx)).distinct()
        return ids.mapNotNull { dao.getMakromonById(it) }.take(Trainers.MAX_TEAM)
    }

    private fun load(publish: Boolean) {
        val ctx = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val user = FirebaseRepository.currentUser
            val name = Arena.arenaName(ctx, user?.displayName)
            val mons = withContext(Dispatchers.IO) { loadTeam() }
            val me = Arena.snapshot(user?.uid ?: "local", name, mons, System.currentTimeMillis())
            myTeam = me.team
            renderMe(me)
            renderTrainers(R.id.llAiTrainers, Arena.aiTrainers(me.team, LocalDate.now().toEpochDay()))

            val empty = view?.findViewById<TextView>(R.id.tvGhostsEmpty) ?: return@launch
            val status = view?.findViewById<TextView>(R.id.tvGhostStatus) ?: return@launch
            if (user == null) {
                status.text = "Přihlas se a tvůj tým začne hájit arénu proti ostatním hráčům."
                empty.text = "Duchové ostatních hráčů jsou vidět po přihlášení."
                empty.visibility = View.VISIBLE
                return@launch
            }
            status.text = if (me.team.isEmpty()) "Nemáš tým – chyť Makromona v Makrosvětě." else "👻 Tvůj duch hájí arénu."
            val ghosts = withContext(Dispatchers.IO) {
                runCatching {
                    if (publish && me.team.isNotEmpty()) FirebaseRepository.publishArenaGhost(me)
                    FirebaseRepository.fetchArenaGhosts()
                }
            }
            if (!isAdded) return@launch
            ghosts.onSuccess { list ->
                val picked = Arena.pickGhosts(list, user.uid, me.power)
                renderTrainers(R.id.llGhosts, picked)
                empty.visibility = if (picked.isEmpty()) View.VISIBLE else View.GONE
                empty.text = "Zatím tu nejsou žádní další duchové. Pozvi kamaráda!"
            }.onFailure {
                empty.visibility = View.VISIBLE
                empty.text = "Duchy se nepodařilo načíst. Zkus to znovu později."
                if (publish) status.text = "Tvého ducha se nepodařilo nahrát."
            }
        }
    }

    private fun renderMe(me: Trainer) {
        val v = view ?: return
        v.findViewById<TextView>(R.id.tvArenaName).text = "${me.name} ✎"
        v.findViewById<TextView>(R.id.tvArenaPower).text = "⚔ ${me.power}"
        val r = Arena.record(requireContext())
        v.findViewById<TextView>(R.id.tvArenaRecord).text = "${r.wins} výher · ${r.losses} proher"
        fillTeam(v.findViewById(R.id.llMyTeam), me.team, onDark = true)
    }

    private fun renderTrainers(containerId: Int, trainers: List<Trainer>) {
        val box = view?.findViewById<LinearLayout>(containerId) ?: return
        box.removeAllViews()
        trainers.forEach { t ->
            val row = layoutInflater.inflate(R.layout.item_arena_trainer, box, false)
            row.findViewById<TextView>(R.id.tvTrainerKind).text = if (t.kind == Trainer.Kind.GHOST) "👻 DUCH HRÁČE" else "TRENÉR ARÉNY"
            row.findViewById<TextView>(R.id.tvTrainerName).text = t.name
            row.findViewById<TextView>(R.id.tvTrainerSub).text =
                "⚔ ${t.power} · ${t.team.size} ${if (t.team.size == 1) "Makromon" else if (t.team.size < 5) "Makromoni" else "Makromonů"}" +
                    " · odměna ${Arena.coinsForWin(t)} 🪙"
            fillTeam(row.findViewById(R.id.llTrainerTeam), t.team, onDark = false)
            row.findViewById<View>(R.id.btnChallenge).setOnClickListener { challenge(t) }
            box.addView(row)
        }
    }

    /** Sprity týmu v kroužcích, pod každým level. */
    private fun fillTeam(box: LinearLayout, team: List<TrainerMon>, onDark: Boolean) {
        box.removeAllViews()
        val dp = resources.displayMetrics.density
        team.forEach { m ->
            val sp = SpeciesRegistry.byId(m.speciesId)
            val cell = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val res = sp?.let { resources.getIdentifier(it.sprite, "drawable", requireContext().packageName) } ?: 0
            cell.addView(ImageView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams((44 * dp).toInt(), (44 * dp).toInt())
                setBackgroundResource(R.drawable.bg_arena_slot)
                if (onDark) background.mutate().alpha = 60
                setPadding((5 * dp).toInt(), (5 * dp).toInt(), (5 * dp).toInt(), (5 * dp).toInt())
                if (res != 0) setImageResource(res)
                (drawable as? BitmapDrawable)?.isFilterBitmap = false
            })
            cell.addView(TextView(requireContext()).apply {
                text = "Lv ${m.level}"
                textSize = 10f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(if (onDark) 0xCCFEFAE0.toInt() else 0xFF606C38.toInt())
            })
            box.addView(cell)
        }
        // prázdná místa do šesti, aby řada měla stejný rytmus
        repeat(Trainers.MAX_TEAM - team.size) {
            box.addView(View(requireContext()).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) })
        }
    }

    private fun challenge(t: Trainer) {
        if (myTeam.isEmpty()) {
            Toast.makeText(requireContext(), "Nejdřív si v Makrosvětě chyť Makromona.", Toast.LENGTH_SHORT).show()
            return
        }
        requireActivity().supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out, android.R.anim.fade_in, android.R.anim.fade_out)
            .add(android.R.id.content, PokemonBattleFragment.forTrainer(t))
            .addToBackStack("arena_battle")
            .commit()
    }

    private fun editName() {
        val ctx = requireContext()
        val input = EditText(ctx).apply {
            setText(Arena.arenaName(ctx, FirebaseRepository.currentUser?.displayName))
            filters = arrayOf(InputFilter.LengthFilter(Trainers.NAME_MAX))
            setSingleLine()
        }
        val pad = (20 * resources.displayMetrics.density).toInt()
        AlertDialog.Builder(ctx)
            .setTitle("Jméno v aréně")
            .setMessage("Tohle jméno uvidí ostatní hráči u tvého ducha.")
            .setView(android.widget.FrameLayout(ctx).apply { setPadding(pad, 0, pad, 0); addView(input) })
            .setPositiveButton("Uložit") { _, _ ->
                val n = input.text.toString().trim()
                if (n.isNotEmpty()) { Arena.setArenaName(ctx, n); load(publish = true) }
            }
            .setNegativeButton("Zrušit", null)
            .show()
    }
}
