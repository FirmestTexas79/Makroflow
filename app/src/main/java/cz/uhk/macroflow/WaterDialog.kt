package cz.uhk.macroflow

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.*
import android.widget.EditText
import android.widget.TextView
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
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

    private val db    by lazy { AppDatabase.getDatabase(requireContext()) }
    private val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setDimAmount(0.6f)
            attributes.windowAnimations = android.R.style.Animation_Dialog
        }
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.dialog_water, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        glassView   = view.findViewById(R.id.waterGlassView)
        etAmount    = view.findViewById(R.id.etWaterAmount)
        tvGoalLabel = view.findViewById(R.id.tvWaterGoalLabel)
        btnConfirm  = view.findViewById(R.id.btnConfirmWater)

        // Načti aktuální stav a nastav sklenici
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

        // Sync editText → sklenice: libovolný ml (1..2000), vizuál se snapuje automaticky
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

        // Preset tlačítka
        view.findViewById<MaterialButton>(R.id.btn150ml)?.setOnClickListener {
            glassView.amountMl = 150; etAmount.setText("150")
        }
        view.findViewById<MaterialButton>(R.id.btn250ml)?.setOnClickListener {
            glassView.amountMl = 250; etAmount.setText("250")
        }
        view.findViewById<MaterialButton>(R.id.btn500ml)?.setOnClickListener {
            glassView.amountMl = 500; etAmount.setText("500")
        }

        // Potvrdit — Vortex → uložení
        // triggerVortex bere Runnable, ne suspend lambda — proto použijeme post
        btnConfirm.setOnClickListener {
            val ml = glassView.amountMl
            if (ml <= 0) { dismiss(); return@setOnClickListener }

            btnConfirm.isEnabled = false
            glassView.triggerVortex {
                lifecycleScope.launch(Dispatchers.IO) {
                    db.waterDao().insertWater(
                        WaterEntity(date = today, amountMl = ml)
                    )
                    withContext(Dispatchers.Main) {
                        onWaterLogged?.invoke(ml)
                        dismiss()
                    }
                }
            }
        }

        view.findViewById<View>(R.id.btnCancelWater)?.setOnClickListener { dismiss() }
    }
}