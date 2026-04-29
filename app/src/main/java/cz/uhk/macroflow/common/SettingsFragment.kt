package cz.uhk.macroflow.common

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.R
import cz.uhk.macroflow.pokemon.CapturedMakromonEntity
import cz.uhk.macroflow.pokemon.SpawnManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsFragment : Fragment() {

    private lateinit var db: AppDatabase

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = AppDatabase.getDatabase(requireContext())

        val btnCheatPokeballs = view.findViewById<MaterialButton>(R.id.btnCheatPokeballs)
        val btnCheatGreatballs = view.findViewById<MaterialButton>(R.id.btnCheatGreatballs)
        val btnCheatCoins = view.findViewById<MaterialButton>(R.id.btnCheatCoins)
        val btnEditPokedex = view.findViewById<MaterialButton>(R.id.btnEditPokedex)

        // Prvky pro generování Pokémonů (Cheat kapsa)
        val etCheatId = view.findViewById<EditText>(R.id.etCheatPokemonId)
        val btnCheatAddPokemon = view.findViewById<MaterialButton>(R.id.btnCheatAddPokemon)

        val etPromoCode = view.findViewById<EditText>(R.id.etPromoCode)
        val btnApplyPromo = view.findViewById<MaterialButton>(R.id.btnApplyPromo)


        // 🧪 1. Přidání libovolného Pokémona do kapsy zadáním ID (např. 025)
        btnCheatAddPokemon?.setOnClickListener {
            val typedId = etCheatId?.text?.toString()?.trim() ?: ""
            if (typedId.length != 3) {
                Toast.makeText(requireContext(), "Zadej validní 3-místné ID (např. 001)!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch(Dispatchers.IO) {
                // 1. Zkusíme najít v Pokédexu (v databázi)
                var makrodexEntry = db.makrodexEntryDao().getEntry(typedId)
                var makromonName = makrodexEntry?.displayName

                // 🔍 ZÁCHRANNÁ BRZDA: Pokud v DB není, vytáhneme jméno ze SpawnManageru!
                if (makromonName == null) {
                    val spawnEntry = SpawnManager.findById(typedId)
                    if (spawnEntry != null) {
                        makromonName = spawnEntry.name
                    }
                }

                if (makromonName != null) {
                    val newCapture = CapturedMakromonEntity(
                        makromonId = typedId,
                        name = makromonName.uppercase(),
                        isShiny = false,
                        isLocked = false,
                        caughtDate = System.currentTimeMillis()
                    )
                    db.capturedMakromonDao().insertMakromon(newCapture)

                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "✅ $makromonName přidán do Poké-kapsy!", Toast.LENGTH_SHORT).show()
                        etCheatId?.text?.clear()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "❌ Pokémon s ID $typedId nebyl v DB ani v Poolu nalezen!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }


        // 📝 2. Otevření vlastního dialogu pro editaci textů v Pokédexu
        btnEditPokedex?.setOnClickListener {
            showPokedexEditorDialog()
        }


        // 🛠️ 3. Cheat na 5 Poké Ballů
        btnCheatPokeballs?.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                db.userItemDao().addItem("poke_ball", 5)
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "🎁 Přidáno 5x Poké Ball!", Toast.LENGTH_SHORT).show()
                }
            }
        }


        // 🛠️ 4. Cheat na 5 Great Ballů
        btnCheatGreatballs?.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                db.userItemDao().addItem("great_ball", 5)
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "🎁 Přidáno 5x Great Ball!", Toast.LENGTH_SHORT).show()
                }
            }
        }


        // 🛠️ 5. Cheat na 100 Coinů
        btnCheatCoins?.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                db.coinDao().addCoins(100)
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "💰 Přidáno 100 coinů!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnApplyPromo?.setOnClickListener {
            val code = etPromoCode?.text?.toString() ?: ""
            if (code.isNotEmpty()) {
                PromoManager.redeemCode(requireContext(), code, lifecycleScope) {
                    // Co se má stát po úspěchu (např. vymazat pole)
                    etPromoCode?.text?.clear()
                    // Tady můžeš zavolat i refresh UI pokud zobrazuješ počet coinů/ballů
                }
            } else {
                Toast.makeText(requireContext(), "Zadej kód!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showPokedexEditorDialog() {
        val ctx = requireContext()

        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 32)
            setBackgroundColor(Color.parseColor("#FEFAE0"))
        }

        val tvTitle = TextView(ctx).apply {
            text = "📝 Editovat Makrodex v DB"
            textSize = 18f
            setTextColor(Color.parseColor("#283618"))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 16)
        }

        val etId = EditText(ctx).apply { hint = "ID Makromona (např. 050)" }
        val etType = EditText(ctx).apply { hint = "Nový typ (např. ZEMĚ / VLÁKNINA)" }
        val etDesc = EditText(ctx).apply {
            hint = "Nový nutriční popisek..."
            minLines = 3
            gravity = Gravity.TOP
        }

        layout.addView(tvTitle)
        layout.addView(etId)
        layout.addView(etType)
        layout.addView(etDesc)

        AlertDialog.Builder(ctx)
            .setView(layout)
            .setPositiveButton("💾 Uložit do SQL") { _, _ ->
                val id = etId.text.toString().trim()
                val type = etType.text.toString().trim()
                val desc = etDesc.text.toString().trim()

                if (id.isNotEmpty() && type.isNotEmpty() && desc.isNotEmpty()) {
                    saveNewMakrodexData(id, type, desc)
                } else {
                    Toast.makeText(ctx, "Musíš vyplnit všechna pole!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Zrušit", null)
            .show()
    }

    private fun saveNewMakrodexData(pokedexId: String, newType: String, newDesc: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val dao = db.makrodexEntryDao()
            val existingEntry = dao.getEntry(pokedexId)

            if (existingEntry != null) {
                val updated = existingEntry.copy(type = newType, macroDesc = newDesc)
                dao.insertAll(listOf(updated))

                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "✅ Popisek pro ${existingEntry.displayName} byl upraven!", Toast.LENGTH_SHORT).show()
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "❌ Pokémon s ID $pokedexId nebyl v DB nalezen!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}