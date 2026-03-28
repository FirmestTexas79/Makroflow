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
import cz.uhk.macroflow.data.FirebaseRepository
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

    private val dialogScope = kotlinx.coroutines.CoroutineScope(Dispatchers.Main + kotlinx.coroutines.Job())
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

        window?.setLayout(
            (context.resources.displayMetrics.density * 340).toInt(),
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
        dialogScope.launch {
            try {
                // 1. Najdeme konkrétního pokémona v inventáři podle ID
                val pokemonInDb = withContext(Dispatchers.IO) {
                    db.capturedPokemonDao().getPokemonById(capturedPokemonId)
                }

                if (pokemonInDb != null) {
                    activePokemon = pokemonInDb

                    // 2. Načteme data z pokedex_entries podle pokemonId (staré) a newId (nové)
                    // ✅ OPRAVA: Používáme přímo ID z objektu, aby to sedělo
                    val oldEntry = withContext(Dispatchers.IO) {
                        db.pokedexEntryDao().getEntry(pokemonInDb.pokemonId)
                    }
                    val newEntry = withContext(Dispatchers.IO) {
                        db.pokedexEntryDao().getEntry(newId)
                    }

                    startEvolutionAnimation(oldEntry, newEntry)
                } else {
                    Log.e("EVO_DEBUG", "Pokémon s ID $capturedPokemonId nenalezen v DB")
                    dismiss()
                    onComplete()
                }
            } catch (e: Exception) {
                Log.e("EVO_DEBUG", "💥 Chyba v loadData: ${e.message}")
                dismiss()
                onComplete()
            }
        }
    }

    private fun startEvolutionAnimation(oldEntry: PokedexEntryEntity?, newEntry: PokedexEntryEntity?) {
        // ✅ OPRAVA FALLBACKŮ: Pokud entry chybí, použijeme aspoň ID, ne natvrdo "caterpie"
        val oldName = oldEntry?.displayName ?: "Pokémon"
        val newName = newEntry?.displayName ?: "Nová Forma"
        val oldWebName = oldEntry?.webName ?: oldId
        val newWebName = newEntry?.webName ?: newId

        tvEvoText.text = "Co se to děje? Tvoje $oldName začíná záryt!"

        ivEvoSprite.alpha = 1f
        ivEvoSprite.visibility = View.VISIBLE
        ivEvoSilhouette.colorFilter = PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
        ivEvoSilhouette.alpha = 0f
        ivEvoSilhouette.visibility = View.VISIBLE

        val oldUrl = "https://img.pokemondb.net/sprites/firered-leafgreen/normal/$oldWebName.png"
        val newUrl = "https://img.pokemondb.net/sprites/firered-leafgreen/normal/$newWebName.png"

        ivEvoSprite.load(oldUrl) {
            listener(onSuccess = { _, _ ->
                ivEvoSilhouette.load(oldUrl)
                runEvoAnimator(oldName, newName, newUrl, newEntry)
            }, onError = { _, _ ->
                // I při chybě obrázku animaci spustíme
                runEvoAnimator(oldName, newName, newUrl, newEntry)
            })
        }
    }

    private fun runEvoAnimator(oldName: String, newName: String, newSpriteUrl: String, newEntry: PokedexEntryEntity?) {
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
                ivEvoSprite.load(newSpriteUrl)
                tvEvoText.text = "Gratulace! Tvoje $oldName se vyvinula v $newName!"

                dialogScope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            // UPDATE OBJEKTU
                            activePokemon.pokemonId = newId
                            activePokemon.name = newEntry?.displayName?.uppercase() ?: newName.uppercase()

                            // 1. Zápis do lokální DB
                            db.capturedPokemonDao().updatePokemon(activePokemon)

                            // 2. 🔥 KLÍČOVÝ ZÁPIS DO FIREBASE (bez toho se evoluce ztratí)
                            if (FirebaseRepository.isLoggedIn) {
                                FirebaseRepository.uploadCapturedPokemon(activePokemon)
                            }

                            // Update SharedPreferences pro widget/lištu
                            val prefs = context.getSharedPreferences("GamePrefs", Context.MODE_PRIVATE)
                            if (prefs.getString("currentOnBarId", "") == oldId) {
                                prefs.edit()
                                    .putString("currentOnBarId", newId)
                                    .putString("currentOnBarName", activePokemon.name)
                                    .apply()
                            }
                        }

                        if (newMoveToLearn != null) {
                            showMoveLearning(newMoveToLearn)
                        } else {
                            ivEvoSprite.postDelayed({ dismiss(); onComplete() }, 2000)
                        }
                    } catch (e: Exception) {
                        Log.e("EVO_DEBUG", "💥 Chyba při ukládání: ${e.message}")
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

        if (currentMoves.contains(newMove.name)) {
            ivEvoSprite.postDelayed({
                dismiss()
                onComplete()
            }, 1500)
            return
        }

        if (currentMoves.size < 4) {
            // ✅ AUTOMATICKÉ UČENÍ (Méně než 4 útoky) - obrázek ponecháme viditelný!
            llMoveSelection.visibility = View.VISIBLE

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
            // 🔥 PLNO (4 útoky) - Skryjeme evoluční obrázek, aby byl prostor na tlačítka "co zapomenout"
            ivEvoSprite.visibility = View.GONE
            ivEvoSilhouette.visibility = View.GONE

            llMoveSelection.visibility = View.VISIBLE
            tvNewMovePrompt.text = "${activePokemon.name} se chce naučit ${newMove.name}! Vyber útok k zapomnění:"
            llCurrentMoves.removeAllViews()

            currentMoves.forEachIndexed { index, moveName ->
                val btn = Button(context).apply {
                    text = moveName
                    backgroundTintList = ColorStateList.valueOf(Color.parseColor("#424242"))
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
            backgroundTintList = ColorStateList.valueOf(Color.parseColor("#DDA15E"))
            setOnClickListener {
                val updatedMoves = currentMoves.toMutableList()
                if (indexToForget < updatedMoves.size) {
                    updatedMoves[indexToForget] = moveNew.name
                }
                activePokemon.moveListStr = updatedMoves.joinToString(",")

                saveMoveQuietly(activePokemon) {
                    // 🔥 VRÁCENÍ OBRÁZKU - Po vyřešení zápisu obrázek zase ukážeme nahoře!
                    ivEvoSprite.visibility = View.VISIBLE

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
            backgroundTintList = ColorStateList.valueOf(Color.parseColor("#BC6C25"))
            setOnClickListener {
                showMoveLearning(moveNew)
            }
        }

        llCurrentMoves.addView(btnYes)
        llCurrentMoves.addView(btnNo)
    }

    private fun saveMoveQuietly(pokemon: CapturedPokemonEntity, onSaved: () -> Unit) {
        dialogScope.launch {
            withContext(Dispatchers.IO) {
                db.capturedPokemonDao().updatePokemon(pokemon)
            }
            onSaved()
        }
    }

    override fun dismiss() {
        super.dismiss()
        dialogScope.cancel()
    }
}