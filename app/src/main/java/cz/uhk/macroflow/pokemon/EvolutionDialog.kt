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
import cz.uhk.macroflow.R
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.data.FirebaseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EvolutionDialog(
    context: Context,
    private val capturedMakromonId: Int, // Primární klíč z tabulky captured_makromon
    private val oldId: String,          // Např. "001"
    private val newId: String,          // Např. "002"
    private val newMoveToLearn: Move?,
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
    private lateinit var activeMakromon: CapturedMakromonEntity

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        setContentView(R.layout.dialog_evolution)
        setCancelable(false)

        window?.setLayout(
            (context.resources.displayMetrics.density * 340).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        ivEvoSprite     = findViewById(R.id.ivEvoSprite)
        ivEvoSilhouette = findViewById(R.id.ivEvoSilhouette)
        tvEvoText       = findViewById(R.id.tvEvoText)
        llMoveSelection = findViewById(R.id.llMoveSelection)
        tvNewMovePrompt = findViewById(R.id.tvNewMovePrompt)
        llCurrentMoves  = findViewById(R.id.llCurrentMoves)
        btnCancelLearning = findViewById(R.id.btnCancelLearning)

        loadData()
    }

    private fun loadData() {
        dialogScope.launch {
            try {
                val makromonInDb = withContext(Dispatchers.IO) {
                    db.capturedMakromonDao().getMakromonById(capturedMakromonId)
                }

                if (makromonInDb != null) {
                    activeMakromon = makromonInDb

                    val oldEntry = withContext(Dispatchers.IO) {
                        db.makrodexEntryDao().getEntry(makromonInDb.makromonId)
                    }
                    val newEntry = withContext(Dispatchers.IO) {
                        db.makrodexEntryDao().getEntry(newId)
                    }

                    startEvolutionAnimation(oldEntry, newEntry)
                } else {
                    Log.e("EVO_DEBUG", "Makromon s ID $capturedMakromonId nenalezen v DB")
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

    private fun startEvolutionAnimation(oldEntry: MakrodexEntryEntity?, newEntry: MakrodexEntryEntity?) {
        val oldName = oldEntry?.displayName ?: "Makromon"
        val newName = newEntry?.displayName ?: "Nová Forma"

        tvEvoText.text = "Co se to děje? Tvůj $oldName začíná měnit formu!"

        ivEvoSprite.alpha = 1f
        ivEvoSprite.visibility = View.VISIBLE
        ivEvoSilhouette.colorFilter = PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
        ivEvoSilhouette.alpha = 0f
        ivEvoSilhouette.visibility = View.VISIBLE

        // Načtení starého spritu z lokálního drawable
        val oldDrawable = oldEntry?.drawableName ?: "ic_home"
        val oldResId = context.resources.getIdentifier(oldDrawable, "drawable", context.packageName)
        val oldFinalResId = if (oldResId != 0) oldResId else R.drawable.ic_home

        ivEvoSprite.setImageResource(oldFinalResId)
        ivEvoSilhouette.setImageResource(oldFinalResId)

        runEvoAnimator(oldName, newName, newEntry)
    }

    private fun runEvoAnimator(oldName: String, newName: String, newEntry: MakrodexEntryEntity?) {
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 3000
            addUpdateListener { anim ->
                val p   = anim.animatedFraction
                val sin = Math.sin(p * Math.PI * 30 * p)
                if (sin > 0) {
                    ivEvoSprite.alpha     = 1f
                    ivEvoSilhouette.alpha = 0f
                } else {
                    ivEvoSprite.alpha     = 0f
                    ivEvoSilhouette.alpha = 1f
                }
            }
        }

        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                ivEvoSprite.alpha = 1f
                ivEvoSilhouette.visibility = View.GONE

                // Načtení nového spritu z lokálního drawable
                val newDrawable = newEntry?.drawableName ?: "ic_home"
                val newResId = context.resources.getIdentifier(newDrawable, "drawable", context.packageName)
                ivEvoSprite.setImageResource(if (newResId != 0) newResId else R.drawable.ic_home)

                tvEvoText.text = "Gratulace! Tvůj $oldName se vyvinul v $newName!"

                dialogScope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            activeMakromon.makromonId = newId
                            activeMakromon.name = newEntry?.displayName?.uppercase() ?: newName.uppercase()

                            db.capturedMakromonDao().updateMakromon(activeMakromon)

                            if (FirebaseRepository.isLoggedIn) {
                                FirebaseRepository.uploadCapturedMakromon(activeMakromon)
                            }

                            val prefs = context.getSharedPreferences("GamePrefs", Context.MODE_PRIVATE)
                            if (prefs.getString("currentOnBarId", "") == oldId) {
                                prefs.edit()
                                    .putString("currentOnBarId", newId)
                                    .putString("currentOnBarName", activeMakromon.name)
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
        val currentMoves = activeMakromon.moveListStr.split(",")
            .filter { it.isNotEmpty() }
            .toMutableList()

        if (currentMoves.contains(newMove.name)) {
            ivEvoSprite.postDelayed({ dismiss(); onComplete() }, 1500)
            return
        }

        if (currentMoves.size < 4) {
            llMoveSelection.visibility = View.VISIBLE
            currentMoves.add(newMove.name)
            activeMakromon.moveListStr = currentMoves.joinToString(",")

            dialogScope.launch {
                withContext(Dispatchers.IO) {
                    db.capturedMakromonDao().updateMakromon(activeMakromon)
                }
                tvNewMovePrompt.text = "✅ ${activeMakromon.name} se automaticky naučil ${newMove.name}!"
                llCurrentMoves.removeAllViews()
                btnCancelLearning.text = "Pokračovat"
                btnCancelLearning.visibility = View.VISIBLE
                btnCancelLearning.setOnClickListener { dismiss(); onComplete() }
            }
        } else {
            ivEvoSprite.visibility    = View.GONE
            ivEvoSilhouette.visibility = View.GONE
            llMoveSelection.visibility = View.VISIBLE
            tvNewMovePrompt.text = "${activeMakromon.name} se chce naučit ${newMove.name}! Vyber útok k zapomnění:"
            llCurrentMoves.removeAllViews()

            currentMoves.forEachIndexed { index, moveName ->
                val btn = Button(context).apply {
                    text = moveName
                    backgroundTintList = ColorStateList.valueOf(Color.parseColor("#424242"))
                    setTextColor(Color.WHITE)
                    setOnClickListener { confirmForgetMove(index, moveName, newMove, currentMoves) }
                }
                llCurrentMoves.addView(btn)
            }

            btnCancelLearning.visibility = View.VISIBLE
            btnCancelLearning.text = "Zrušit učení"
            btnCancelLearning.setOnClickListener { dismiss(); onComplete() }
        }
    }

    private fun confirmForgetMove(
        indexToForget: Int,
        moveNameOld: String,
        moveNew: Move,
        currentMoves: List<String>
    ) {
        tvNewMovePrompt.text = "Opravdu chceš zapomenout útok $moveNameOld a naučit se ${moveNew.name}?"
        llCurrentMoves.removeAllViews()

        val btnYes = Button(context).apply {
            text = "ANO, zapomenout $moveNameOld"
            backgroundTintList = ColorStateList.valueOf(Color.parseColor("#DDA15E"))
            setOnClickListener {
                val updatedMoves = currentMoves.toMutableList()
                if (indexToForget < updatedMoves.size) updatedMoves[indexToForget] = moveNew.name
                activeMakromon.moveListStr = updatedMoves.joinToString(",")

                saveMoveQuietly(activeMakromon) {
                    ivEvoSprite.visibility = View.VISIBLE
                    tvNewMovePrompt.text = "Útok úspěšně zapomenut! ${activeMakromon.name} se naučil ${moveNew.name}!"
                    llCurrentMoves.removeAllViews()
                    ivEvoSprite.postDelayed({ dismiss(); onComplete() }, 2000)
                }
            }
        }

        val btnNo = Button(context).apply {
            text = "NE, zrušit"
            backgroundTintList = ColorStateList.valueOf(Color.parseColor("#BC6C25"))
            setOnClickListener { showMoveLearning(moveNew) }
        }

        llCurrentMoves.addView(btnYes)
        llCurrentMoves.addView(btnNo)
    }

    private fun saveMoveQuietly(makromon: CapturedMakromonEntity, onSaved: () -> Unit) {
        dialogScope.launch {
            withContext(Dispatchers.IO) {
                db.capturedMakromonDao().updateMakromon(makromon)
            }
            onSaved()
        }
    }

    override fun dismiss() {
        super.dismiss()
        dialogScope.cancel()
    }
}