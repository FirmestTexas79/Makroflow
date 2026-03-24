package cz.uhk.macroflow.dashboard

import android.R
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.*
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.achievements.AchievementEngine
import cz.uhk.macroflow.data.WaterEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class WaterDialog : DialogFragment() {

    var onWaterLogged: ((Int) -> Unit)? = null

    private lateinit var glassView:   WaterGlassView
    private lateinit var etAmount:    EditText
    private lateinit var tvGoalLabel: TextView
    private lateinit var btnConfirm:  MaterialButton

    private val db    by lazy { AppDatabase.Companion.getDatabase(requireContext()) }
    private val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setDimAmount(0.6f)
            attributes.windowAnimations = R.style.Animation_Dialog
        }
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? = inflater.inflate(cz.uhk.macroflow.R.layout.dialog_water, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        glassView   = view.findViewById(cz.uhk.macroflow.R.id.waterGlassView)
        etAmount    = view.findViewById(cz.uhk.macroflow.R.id.etWaterAmount)
        tvGoalLabel = view.findViewById(cz.uhk.macroflow.R.id.tvWaterGoalLabel)
        btnConfirm  = view.findViewById(cz.uhk.macroflow.R.id.btnConfirmWater)

        lifecycleScope.launch {
            val currentMl = withContext(Dispatchers.IO) {
                db.waterDao().getTotalMlForDateSync(today)
            }
            val target = MacroCalculator.calculate(requireContext())
            val goalMl = (target.water * 1000).toInt()

            glassView.goalMl    = goalMl
            glassView.currentMl = currentMl
            glassView.amountMl  = 300

            tvGoalLabel.text = "Cíl: ${goalMl} ml · Vypito: ${currentMl} ml"
            etAmount.setText("300")
        }

        etAmount.addTextChangedListener { s ->
            val ml = s.toString().toIntOrNull() ?: return@addTextChangedListener
            val clamped = ml.coerceIn(1, glassView.maxSingleDrinkMl)
            if (clamped != glassView.amountMl) glassView.amountMl = clamped
        }

        glassView.onAmountChanged = { ml ->
            if (etAmount.text.toString().toIntOrNull() != ml) {
                etAmount.setText(ml.toString())
                etAmount.setSelection(etAmount.text.length)
            }
        }

        view.findViewById<MaterialButton>(cz.uhk.macroflow.R.id.btn150ml)?.setOnClickListener {
            glassView.amountMl = 150; etAmount.setText("150")
        }
        view.findViewById<MaterialButton>(cz.uhk.macroflow.R.id.btn250ml)?.setOnClickListener {
            glassView.amountMl = 250; etAmount.setText("250")
        }
        view.findViewById<MaterialButton>(cz.uhk.macroflow.R.id.btn500ml)?.setOnClickListener {
            glassView.amountMl = 500; etAmount.setText("500")
        }

        btnConfirm.setOnClickListener {
            val ml = glassView.amountMl
            if (ml <= 0) { dismiss(); return@setOnClickListener }

            btnConfirm.isEnabled = false
            glassView.triggerVortex {
                lifecycleScope.launch(Dispatchers.IO) {
                    // 1. Ulož vodu
                    db.waterDao().insertWater(
                        WaterEntity(date = today, amountMl = ml)
                    )

                    // 2. Zkontroluj achievementy — po uložení vody
                    val newAchievements = AchievementEngine.checkAll(requireContext())

                    withContext(Dispatchers.Main) {
                        // 3. Informuj Dashboard o nové vodě
                        onWaterLogged?.invoke(ml)

                        // 4. Zobraz notifikace nově odemčených achievementů
                        newAchievements.forEach { ach ->
                            Toast.makeText(
                                requireContext(),
                                "🏆 Achievement odemčen: ${ach.titleCs}",
                                Toast.LENGTH_LONG
                            ).show()
                        }

                        dismiss()
                    }
                }
            }
        }

        view.findViewById<View>(cz.uhk.macroflow.R.id.btnCancelWater)?.setOnClickListener { dismiss() }
    }
}