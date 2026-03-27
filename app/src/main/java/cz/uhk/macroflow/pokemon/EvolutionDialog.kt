package cz.uhk.macroflow.pokemon

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import coil.load
import cz.uhk.macroflow.R
import cz.uhk.macroflow.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
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

    private val dialogScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.Job())
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

        // ✅ VYNUCENÍ PEVNÉ ŠÍŘKY DIALOGU, ABY SE NEROZTAHOVAL DO NEKONEČNA
        window?.setLayout(
            (context.resources.displayMetrics.density * 340).toInt(), // šířka v dp
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

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
        Log.d("EVO_DEBUG", "0. loadData() spuštěno!")

        dialogScope.launch {
            try {
                Log.d("EVO_DEBUG", "0.1 Sahám do DB pro capturedId = $capturedPokemonId")

                val pokemonInDb = withContext(Dispatchers.IO) {
                    db.capturedPokemonDao().getAllCaught().find { it.id == capturedPokemonId }
                }
                val oldEntry = withContext(Dispatchers.IO) {
                    db.pokedexEntryDao().getEntry(oldId)
                }
                val newEntry = withContext(Dispatchers.IO) {
                    db.pokedexEntryDao().getEntry(newId)
                }

                Log.d("EVO_DEBUG", "0.2 DB vrátila: pokemonInDb found = ${pokemonInDb != null}")

                if (pokemonInDb != null) {
                    activePokemon = pokemonInDb
                    startEvolutionAnimation(oldEntry, newEntry)
                } else {
                    Log.d("EVO_DEBUG", "0.3 Chyba: pokemonInDb je NULL!")
                    Toast.makeText(context, "❌ Chyba: Pokémon v DB nenalezen.", Toast.LENGTH_SHORT).show()
                    dismiss()
                    onComplete()
                }
            } catch (e: Exception) {
                Log.e("EVO_DEBUG", "💥 Chyba v loadData DB operaci: ${e.message}", e)
            }
        }
    }

    private fun startEvolutionAnimation(oldEntry: PokedexEntryEntity?, newEntry: PokedexEntryEntity?) {
        val oldName = oldEntry?.displayName ?: "Pokémon"
        val newName = newEntry?.displayName ?: "Nová Forma"

        tvEvoText.text = "Co se to děje? Tvoje $oldName začíná zářit!"

        ivEvoSprite.alpha = 1f
        ivEvoSprite.visibility = View.VISIBLE

        ivEvoSilhouette.colorFilter = PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
        ivEvoSilhouette.alpha = 0f
        ivEvoSilhouette.visibility = View.VISIBLE

        val oldWebName = oldEntry?.webName ?: "caterpie"
        val newWebName = newEntry?.webName ?: "metapod"

        val oldUrl = "https://img.pokemondb.net/sprites/firered-leafgreen/normal/$oldWebName.png"
        val newUrl = "https://img.pokemondb.net/sprites/firered-leafgreen/normal/$newWebName.png"

        ivEvoSprite.load(oldUrl) {
            listener(
                onSuccess = { _, _ ->
                    ivEvoSilhouette.load(oldUrl)
                    runEvoAnimator(oldName, newName, newUrl)
                },
                onError = { _, _ ->
                    runEvoAnimator(oldName, newName, newUrl)
                }
            )
        }
    }

    private fun runEvoAnimator(oldName: String, newName: String, newSpriteUrl: String) {
        Log.d("EVO_DEBUG", "4. runEvoAnimator započal")

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
                Log.d("EVO_DEBUG", "5. Animace úspěšně SKONČILA!")
                ivEvoSprite.alpha = 1f
                ivEvoSilhouette.visibility = View.GONE

                ivEvoSprite.load(newSpriteUrl)

                tvEvoText.text = "Gratulace! Tvoje $oldName se vyvinula v $newName!"

                Log.d("EVO_DEBUG", "6. Zapisuji do databáze pomocí dialogScope...")

                dialogScope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            activePokemon.pokemonId = newId
                            activePokemon.name = newName.uppercase()
                            db.capturedPokemonDao().updatePokemon(activePokemon)

                            val prefs = context.getSharedPreferences("GamePrefs", Context.MODE_PRIVATE)
                            prefs.edit()
                                .putString("currentOnBarId", newId)
                                .putString("currentOnBarName", newName.uppercase())
                                .apply()
                        }

                        Log.d("EVO_DEBUG", "7. Databáze úspěšně uložena.")

                        if (newMoveToLearn != null) {
                            Log.d("EVO_DEBUG", "8a. Nabízím nový útok")
                            showMoveLearning(newMoveToLearn)
                        } else {
                            Log.d("EVO_DEBUG", "8b. Žádný útok k naučení, zavírám za 2s")
                            ivEvoSprite.postDelayed({
                                dismiss()
                                onComplete()
                            }, 2000)
                        }
                    } catch (e: Exception) {
                        Log.e("EVO_DEBUG", "💥 Chyba při ukládání evoluce do DB: ${e.message}", e)
                    }
                }
            }
        })

        Log.d("EVO_DEBUG", "🔥 STARTOVÁNÍ ANIMÁTORU")
        animator.start()
    }

    private fun showMoveLearning(newMove: Move) {
        val currentMoves = activePokemon.moveListStr.split(",")
            .filter { it.isNotEmpty() }
            .toMutableList()

        llMoveSelection.visibility = View.VISIBLE

        if (currentMoves.size < 4) {
            currentMoves.add(newMove.name)
            activePokemon.moveListStr = currentMoves.joinToString(",")

            dialogScope.launch {
                withContext(Dispatchers.IO) {
                    db.capturedPokemonDao().updatePokemon(activePokemon)
                }

                tvNewMovePrompt.text = "✅ ${activePokemon.name} se automaticky naučil ${newMove.name}!"
                llCurrentMoves.removeAllViews()

                btnCancelLearning.text = "Pokračovat"
                btnCancelLearning.visibility = View.VISIBLE
                btnCancelLearning.setOnClickListener {
                    dismiss()
                    onComplete()
                }
            }
        } else {
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

            btnCancelLearning.visibility = View.VISIBLE
            btnCancelLearning.text = "Zrušit učení"
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

                saveMoveQuietly(activePokemon) {
                    tvNewMovePrompt.text = "Útok úspěšně zapomenut! ${activePokemon.name} se naučil ${moveNew.name}!"
                    llCurrentMoves.removeAllViews()

                    ivEvoSprite.postDelayed({
                        dismiss()
                        onComplete()
                    }, 2000)
                }
            }
        }

        val btnNo = Button(context).apply {
            text = "NE, zrušit"
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#BC6C25"))
            setOnClickListener {
                showMoveLearning(moveNew)
            }
        }

        llCurrentMoves.addView(btnYes)
        llCurrentMoves.addView(btnNo)
    }

    private fun saveMoveQuietly(pokemon: CapturedPokemonEntity, onSaved: () -> Unit) {
        // ✅ OPRAVA: Používáme bezpečný dialogScope pro zápis do DB
        dialogScope.launch {
            withContext(Dispatchers.IO) {
                db.capturedPokemonDao().updatePokemon(pokemon)
            }
            onSaved()
        }
    }

    override fun dismiss() {
        super.dismiss()
        dialogScope.cancel() // Uklidíme běžící procesy, aby to nežralo paměť po zavření
    }
}