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
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import coil.load
import cz.uhk.macroflow.R
import cz.uhk.macroflow.data.AppDatabase

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
        Thread {
            val list = db.capturedPokemonDao().getAllCaught()
            activePokemon = list.find { it.id == capturedPokemonId } ?: return@Thread

            val oldEntry = db.pokedexEntryDao().getEntry(oldId)
            val newEntry = db.pokedexEntryDao().getEntry(newId)

            window?.decorView?.post {
                startEvolutionAnimation(oldEntry, newEntry)
            }
        }.start()
    }

    private fun startEvolutionAnimation(oldEntry: PokedexEntryEntity?, newEntry: PokedexEntryEntity?) {
        val oldName = oldEntry?.displayName ?: "Pokémon"
        val newName = newEntry?.displayName ?: "Nová Forma"

        tvEvoText.text = "Co se to děje? Tvoje $oldName začíná zářit!"

        val oldSpriteUrl = "https://img.pokemondb.net/sprites/firered-leafgreen/normal/${oldEntry?.webName}.png"

        // ✅ Oprava: Načteme normální obrázek
        ivEvoSprite.load(oldSpriteUrl)

        // ✅ Oprava: Pro siluetu načteme stejný obrázek, ale obarvíme ho nativním filtrem Androidu na bílo
        ivEvoSilhouette.load(oldSpriteUrl) {
            listener(onSuccess = { _, _ ->
                // Jakmile se načte, obarvíme ho nativně na bílo přes PorterDuff
                ivEvoSilhouette.colorFilter =
                    PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
            })
        }

        ivEvoSilhouette.visibility = View.VISIBLE

        // Problikávání (Game Boy styl!)
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 3000
            addUpdateListener { anim ->
                val p = anim.animatedFraction
                // Sinusoida, která se s rostoucím časem zrychluje
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
                // Končíme s Metapodem
                ivEvoSprite.alpha = 1f
                ivEvoSilhouette.visibility = View.GONE
                ivEvoSprite.load("https://img.pokemondb.net/sprites/firered-leafgreen/normal/${newEntry?.webName}.png")

                tvEvoText.text = "Gratulace! Tvoje $oldName se vyvinula v $newName!"

                // Zápis evoluce do DB
                Thread {
                    activePokemon.pokemonId = newId
                    activePokemon.name = newName.uppercase()
                    db.capturedPokemonDao().updatePokemon(activePokemon)

                    // Obnovíme si preferences parťáka pro MainActivity lištu
                    val prefs = context.getSharedPreferences("GamePrefs", Context.MODE_PRIVATE)
                    prefs.edit()
                        .putString("currentOnBarId", newId)
                        .putString("currentOnBarName", newName.uppercase())
                        .apply()

                    window?.decorView?.post {
                        if (newMoveToLearn != null) {
                            showMoveLearning(newMoveToLearn)
                        } else {
                            dismiss()
                            onComplete()
                        }
                    }
                }.start()
            }
        })

        animator.start()
    }

    private fun showMoveLearning(newMove: Move) {
        llMoveSelection.visibility = View.VISIBLE
        tvNewMovePrompt.text = "Metapod se chce naučit ${newMove.name}! Vyber útok, který má zapomenout:"

        // Útoky bereme z BattleFactory pro Caterpie
        val baseCaterpie = BattleFactory.createCaterpie()
        val currentMoves = baseCaterpie.moves

        llCurrentMoves.removeAllViews()

        currentMoves.forEachIndexed { index, move ->
            val btn = Button(context).apply {
                text = "${move.name} (${move.type})"
                backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#424242"))
                setTextColor(Color.WHITE)
                setOnClickListener {
                    confirmForgetMove(index, move, newMove)
                }
            }
            llCurrentMoves.addView(btn)
        }

        btnCancelLearning.setOnClickListener {
            dismiss()
            onComplete()
        }
    }

    private fun confirmForgetMove(indexToForget: Int, moveOld: Move, moveNew: Move) {
        tvEvoText.text = "Opravdu chceš zapomenout útok ${moveOld.name} a naučit se ${moveNew.name}?"

        llCurrentMoves.removeAllViews()

        val btnYes = Button(context).apply {
            text = "ANO, zapomenout ${moveOld.name}"
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#DDA15E"))
            setOnClickListener {
                saveNewMove(indexToForget, moveNew)
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

    private fun saveNewMove(indexToReplace: Int, moveNew: Move) {
        Thread {
            // Přepíšeme seznam útoků. Abychom to teď udělali bezpečně pro tvůj engine,
            // zapíšeme si to jako String oddělený čárkou u pokémona v DB.
            val baseCaterpie = BattleFactory.createCaterpie()
            val moves = baseCaterpie.moves.toMutableList()

            if (indexToReplace < moves.size) {
                moves[indexToReplace] = moveNew
            }

            val movesString = moves.joinToString(",") { it.name }
            activePokemon.moveListStr = movesString

            db.capturedPokemonDao().updatePokemon(activePokemon)

            window?.decorView?.post {
                tvEvoText.text = "Útok úspěšně zapomenut! Metapod se naučil ${moveNew.name}!"
                llMoveSelection.visibility = View.GONE

                window?.decorView?.postDelayed({
                    dismiss()
                    onComplete()
                }, 2000)
            }
        }.start()
    }
}