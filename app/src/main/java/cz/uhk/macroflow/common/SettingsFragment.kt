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

        db = AppDatabase.Companion.getDatabase(requireContext())

        val btnCheatPokeballs = view.findViewById<MaterialButton>(R.id.btnCheatPokeballs)
        val btnCheatGreatballs = view.findViewById<MaterialButton>(R.id.btnCheatGreatballs)
        val btnCheatCoins = view.findViewById<MaterialButton>(R.id.btnCheatCoins)




        // Najdi v layoutu (nebo si přidej tlačítko v XML) a svaž ho s dialogem:
        view.findViewById<MaterialButton>(R.id.btnEditPokedex)?.setOnClickListener {
            showPokedexEditorDialog()
        }


        // 🛠️ Cheat na 5 Poké Ballů
        btnCheatPokeballs.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                db.userItemDao().addItem("poke_ball", 5)
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "🎁 Přidáno 5x Poké Ball!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // 🛠️ Cheat na 5 Great Ballů
        btnCheatGreatballs.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                db.userItemDao().addItem("great_ball", 5)
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "🎁 Přidáno 5x Great Ball!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // 🛠️ Cheat na 100 Coinů
        btnCheatCoins.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                db.coinDao().addCoins(100)
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "💰 Přidáno 100 coinů!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Vlož toto na konec onViewCreated (pod tvoje stávající cheaty):
        val btnEditPokedex = view.findViewById<MaterialButton>(R.id.btnEditPokedex)

        btnEditPokedex?.setOnClickListener {
            showPokedexEditorDialog()
        }
    }
    private fun showPokedexEditorDialog() {
        val ctx = requireContext()

        // Hlavní kontejner dialogu
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#FEFAE0")) // Makroflow barva
        }

        val tvTitle = TextView(ctx).apply {
            text = "📝 Editovat Pokédex v DB"
            textSize = 18f
            setTextColor(Color.parseColor("#283618"))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 16)
        }

        // Vstupy
        val etId = EditText(ctx).apply { hint = "ID Pokémona (např. 050 nebo 094)" }
        val etType = EditText(ctx).apply { hint = "Nový typ (např. ZEMĚ / FEKÁL)" }
        val etDesc = EditText(ctx).apply {
            hint = "Nový nutriční popisek..."
            minLines = 3
            gravity = Gravity.TOP
        }

        layout.addView(tvTitle)
        layout.addView(etId)
        layout.addView(etType)
        layout.addView(etDesc)

        // Sestavení Dialogu
        AlertDialog.Builder(ctx)
            .setView(layout)
            .setPositiveButton("💾 Uložit do SQL") { _, _ ->
                val id = etId.text.toString().trim()
                val type = etType.text.toString().trim()
                val desc = etDesc.text.toString().trim()

                if (id.isNotEmpty() && type.isNotEmpty() && desc.isNotEmpty()) {
                    saveNewPokedexData(id, type, desc)
                } else {
                    Toast.makeText(ctx, "Musíš vyplnit všechna pole!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Zrušit", null)
            .show()
    }

    // Pomocná funkce pro reálný zápis do Roomu na pozadí
    private fun saveNewPokedexData(pokedexId: String, newType: String, newDesc: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val dao = db.pokedexEntryDao()
            val existingEntry = dao.getEntry(pokedexId)

            if (existingEntry != null) {
                // Pokémon existuje, přepíšeme ho s novými daty
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