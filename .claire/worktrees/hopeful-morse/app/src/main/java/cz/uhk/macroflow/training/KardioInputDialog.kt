package cz.uhk.macroflow.training

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import cz.uhk.macroflow.R

/**
 * Makroflow-styled BottomSheet pro zadání délky nebo tempa kardia.
 *
 * @param title    Nadpis dialogu (např. "Délka kardia")
 * @param subtitle Podnázev – den (např. "Pondělí")
 * @param unit     Jednotka (např. "min" nebo "km/h")
 * @param current  Aktuální hodnota jako String (nebo prázdný řetězec)
 * @param onSave   Callback s hodnotou jako String
 */
class KardioInputDialog(
    private val title: String,
    private val subtitle: String,
    private val unit: String,
    private val current: String,
    private val onSave: (String) -> Unit
) : BottomSheetDialogFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.dialog_kardio_input, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<TextView>(R.id.tvDialogTitle).text    = title
        view.findViewById<TextView>(R.id.tvDialogSubtitle).text = subtitle
        view.findViewById<TextView>(R.id.tvUnit).text           = unit

        val etInput = view.findViewById<EditText>(R.id.etInput)
        etInput.setText(current)
        etInput.setSelection(current.length)

        // Automaticky otevřít klávesnici
        etInput.requestFocus()
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)

        view.findViewById<View>(R.id.btnDialogCancel).setOnClickListener { dismiss() }
        view.findViewById<View>(R.id.btnDialogSave).setOnClickListener {
            val value = etInput.text.toString().replace(",", ".").trim()
            if (value.isNotEmpty()) onSave(value)
            dismiss()
        }
    }

    override fun onStart() {
        super.onStart()
        // Průhledné pozadí za dialogem
        dialog?.window?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            ?.setBackgroundResource(android.R.color.transparent)
    }

    companion object {
        fun showDuration(
            fragmentManager: androidx.fragment.app.FragmentManager,
            dayLabel: String,
            current: Int,
            onSave: (Int) -> Unit
        ) {
            KardioInputDialog(
                title    = "Délka kardia",
                subtitle = dayLabel,
                unit     = "min",
                current  = if (current > 0) current.toString() else "",
                onSave   = { onSave(it.toIntOrNull() ?: 0) }
            ).show(fragmentManager, "kardio_duration")
        }

        fun showSpeed(
            fragmentManager: androidx.fragment.app.FragmentManager,
            dayLabel: String,
            current: Float,
            onSave: (Float) -> Unit
        ) {
            KardioInputDialog(
                title    = "Průměrné tempo",
                subtitle = dayLabel,
                unit     = "km/h",
                current  = if (current > 0f) String.format("%.1f", current) else "",
                onSave   = { onSave(it.toFloatOrNull() ?: 0f) }
            ).show(fragmentManager, "kardio_speed")
        }
    }
}
