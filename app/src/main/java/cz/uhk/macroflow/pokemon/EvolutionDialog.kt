package cz.uhk.macroflow.pokemon

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import coil.load
import cz.uhk.macroflow.R
import cz.uhk.macroflow.common.MainActivity
import cz.uhk.macroflow.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EvolutionDialog(
    context: Context,
    private val capturedPokemonId: Int, // Primární klíč z tabulky captured_pokemon
    private val oldId: String,          // "010"
    private val newId: String,          // "011"
    private val newMoveToLearn: Move?,  // Útok, který se má naučit (např. Harden)
    private val onComplete: () -> Unit
) : Dialog(context) {

    private lateinit var ivEvoSprite: ImageView
    private lateinit var ivEvoSilhouette: ImageView
    private lateinit var tvEvoText: TextView
    private lateinit var llMoveSelection: LinearLayout
    private lateinit var tvNewMovePrompt: TextView
    private lateinit var llCurrentMoves: LinearLayout
    private lateinit var btnCancelLearning: Button

    private val db = AppDatabase.getDatabase(context)
    private lateinit var activePokemon: CapturedPokemonEntity

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        setContentView(R.layout.dialog_evolution)
        setCancelable(false)

        ivEvoSprite = findViewById(R.id.ivEvoSprite)
        ivEvoSilhouette = findViewById(R.id.ivEvoSilhouette)
        tvEvoText = findViewById(R.id.tvEvoText)
        llMoveSelection = findViewById(R.id.llMoveSelection)
        tvNewMovePrompt = findViewById(R.id.tvNewMovePrompt)
        llCurrentMoves = findViewById(R.id.llCurrentMoves)
        btnCancelLearning = findViewById(R.id.btnCancelLearning)

        loadData()
    }

    private fun loadData() {
        // ✅ Bezpečné načtení dat pomocí lifecycleScope a Dispatchers.IO
        (context as? MainActivity)?.lifecycleScope?.launch(Dispatchers.IO) {
            val list = db.capturedPokemonDao().getAllCaught()
            val pokemonInDb = list.find { it.id == capturedPokemonId }
            val oldEntry = db.pokedexEntryDao().getEntry(oldId)
            val newEntry = db.pokedexEntryDao().getEntry(newId)

            if (pokemonInDb != null) {
                activePokemon = pokemonInDb
                withContext(Dispatchers.Main) {
                    startEvolutionAnimation(oldEntry, newEntry)
                }
            } else {
                withContext(Dispatchers.Main) {
                    tvEvoText.text = "❌ Chyba: Pokémon v databázi nenalezen."
                    dismiss()
                    onComplete()
                }
            }
        }
    }

    private fun startEvolutionAnimation(oldEntry: PokedexEntryEntity?, newEntry: PokedexEntryEntity?) {
        val oldName = oldEntry?.displayName ?: "Pokémon"
        val newName = newEntry?.displayName ?: "Nová Forma"

        tvEvoText.text = "Co se to děje? Tvoje $oldName začíná zářit!"

        val oldSpriteUrl = "https://img.pokemondb.net/sprites/firered-leafgreen/normal/${oldEntry?.webName}.png"

        ivEvoSprite.load(oldSpriteUrl)

        ivEvoSilhouette.load(oldSpriteUrl) {
            listener(onSuccess = { _, _ ->
                ivEvoSilhouette.colorFilter = PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
            })
        }

        ivEvoSilhouette.visibility = View.VISIBLE

        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 3000
            addUpdateListener { anim ->
                val p = anim.animatedFraction
                val sin = Math.sin(p * Math.PI * 30 * p)
                if (sin > 0) {
                    ivEvoSprite.alpha = 1f
                    ivEvoSilhouette.alpha = 0f
                } else {
                    ivEvoSprite.alpha = 0f
                    ivEvoSilhouette.alpha = 1f
                }
            }
        }

        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                ivEvoSprite.alpha = 1f
                ivEvoSilhouette.visibility = View.GONE
                ivEvoSprite.load("https://img.pokemondb.net/sprites/firered-leafgreen/normal/${newEntry?.webName}.png")

                tvEvoText.text = "Gratulace! Tvoje $oldName se vyvinula v $newName!"

                // Zápis evoluce do DB
                (context as? MainActivity)?.lifecycleScope?.launch(Dispatchers.IO) {
                    activePokemon.pokemonId = newId
                    activePokemon.name = newName.uppercase()
                    db.capturedPokemonDao().updatePokemon(activePokemon)

                    val prefs = context.getSharedPreferences("GamePrefs", Context.MODE_PRIVATE)
                    prefs.edit()
                        .putString("currentOnBarId", newId)
                        .putString("currentOnBarName", newName.uppercase())
                        .apply()

                    withContext(Dispatchers.Main) {
                        if (newMoveToLearn != null) {
                            showMoveLearning(newMoveToLearn)
                        } else {
                            window?.decorView?.postDelayed({
                                dismiss()
                                onComplete()
                            }, 2000)
                        }
                    }
                }
            }
        })

        animator.start()
    }

    private fun showMoveLearning(newMove: Move) {
        val currentMoves = activePokemon.moveListStr.split(",")
            .filter { it.isNotEmpty() }
            .toMutableList()

        llMoveSelection.visibility = View.VISIBLE

        // ✅ OPRAVA: Pokud má pokémon < 4 útoky, nic se nezapomíná! Učí se to automaticky.
        if (currentMoves.size < 4) {
            currentMoves.add(newMove.name)
            activePokemon.moveListStr = currentMoves.joinToString(",")

            saveMoveQuietly(activePokemon)

            tvNewMovePrompt.text = "✅ ${activePokemon.name} se automaticky naučil ${newMove.name}!"
            llCurrentMoves.removeAllViews()

            btnCancelLearning.text = "Pokračovat"
            btnCancelLearning.setOnClickListener {
                dismiss()
                onComplete()
            }
        } else {
            // ❌ Má už 4 útoky -> standardní výběr k zapomnění
            tvNewMovePrompt.text = "${activePokemon.name} se chce naučit ${newMove.name}! Vyber útok k zapomnění:"
            llCurrentMoves.removeAllViews()

            currentMoves.forEachIndexed { index, moveName ->
                val btn = Button(context).apply {
                    text = moveName
                    backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#424242"))
                    setTextColor(Color.WHITE)
                    setOnClickListener {
                        confirmForgetMove(index, moveName, newMove, currentMoves)
                    }
                }
                llCurrentMoves.addView(btn)
            }

            btnCancelLearning.setOnClickListener {
                dismiss()
                onComplete()
            }
        }
    }

    private fun confirmForgetMove(indexToForget: Int, moveNameOld: String, moveNew: Move, currentMoves: List<String>) {
        tvNewMovePrompt.text = "Opravdu chceš zapomenout útok $moveNameOld a naučit se ${moveNew.name}?"

        llCurrentMoves.removeAllViews()

        val btnYes = Button(context).apply {
            text = "ANO, zapomenout $moveNameOld"
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#DDA15E"))
            setOnClickListener {
                val updatedMoves = currentMoves.toMutableList()
                if (indexToForget < updatedMoves.size) {
                    updatedMoves[indexToForget] = moveNew.name
                }
                activePokemon.moveListStr = updatedMoves.joinToString(",")

                saveMoveQuietly(activePokemon)

                tvNewMovePrompt.text = "Útok úspěšně zapomenut! ${activePokemon.name} se naučil ${moveNew.name}!"
                llCurrentMoves.removeAllViews()

                window?.decorView?.postDelayed({
                    dismiss()
                    onComplete()
                }, 2000)
            }
        }

        val btnNo = Button(context).apply {
            text = "NE, zrušit"
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#BC6C25"))
            setOnClickListener {
                showMoveLearning(moveNew) // vrátí zpět na výběr
            }
        }

        llCurrentMoves.addView(btnYes)
        llCurrentMoves.addView(btnNo)
    }

    private fun saveMoveQuietly(pokemon: CapturedPokemonEntity) {
        (context as? MainActivity)?.lifecycleScope?.launch(Dispatchers.IO) {
            db.capturedPokemonDao().updatePokemon(pokemon)
        }
    }
}