package cz.uhk.macroflow.training

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.*
import android.widget.*
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import cz.uhk.macroflow.data.FirebaseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Makroflow stylový editor kardio detailů.
 * UI vytvořeno čistě v kódu pro maximální kontrolu a rychlost.
 * Dynamicky mění vstupy podle typu kardia (Běh/Kolo vs Švihadlo).
 */
class MakroflowKardioPicker : BottomSheetDialogFragment() {

    private var dayEnglish: String = ""
    private var dayCzech: String = ""
    private var onSaved: (() -> Unit)? = null

    companion object {
        fun show(
            fm: FragmentManager,
            dayEng: String,
            dayCz: String,
            onSaved: () -> Unit
        ): MakroflowKardioPicker {
            return MakroflowKardioPicker().apply {
                this.dayEnglish = dayEng
                this.dayCzech = dayCz
                this.onSaved = onSaved
            }.also { it.show(fm, "MakroflowKardioPicker") }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val ctx = requireContext()
        val dp = ctx.resources.displayMetrics.density
        val trainingPrefs = ctx.getSharedPreferences("TrainingPrefs", android.content.Context.MODE_PRIVATE)

        // Detekce typu kardia pro dynamické UI
        val currentType = trainingPrefs.getString("kardio_type_$dayEnglish", "rest") ?: "rest"
        val isRope = currentType == "rope"

        // --- HLAVNÍ KONTEJNER ---
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFFEFAE0.toInt()) // FEFAE0 - manuál
            setPadding((24 * dp).toInt(), (20 * dp).toInt(), (24 * dp).toInt(), (32 * dp).toInt())
        }

        // --- HANDLE BAR ---
        root.addView(View(ctx).apply {
            background = GradientDrawable().apply {
                setColor(0x30283618) // 283618 s alphou
                cornerRadius = 4 * dp
            }
            layoutParams = LinearLayout.LayoutParams((40 * dp).toInt(), (4 * dp).toInt()).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = (24 * dp).toInt()
            }
        })

        // --- TITULEK ---
        root.addView(TextView(ctx).apply {
            text = when(currentType) {
                "run"  -> "Detail běhu"
                "rope" -> "Detail švihadla"
                "bike" -> "Detail kola"
                else   -> "Nastavení kardia"
            }
            textSize = 22f
            setTextColor(0xFF283618.toInt())
            typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
            layoutParams = LinearLayout.LayoutParams(-1, -2)
        })

        root.addView(TextView(ctx).apply {
            text = dayCzech
            textSize = 14f
            setTextColor(0xFF606C38.toInt())
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = (24 * dp).toInt() }
        })

        // --- POMOCNÉ FUNKCE PRO UI ---
        fun createLabel(txt: String) = TextView(ctx).apply {
            text = txt
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.1f
            setTextColor(0xFF283618.toInt())
            alpha = 0.8f
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = (8 * dp).toInt() }
        }

        fun createInput(hint: String, currentVal: String?, isDecimal: Boolean = false): EditText {
            return EditText(ctx).apply {
                inputType = if (isDecimal) {
                    InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                } else {
                    InputType.TYPE_CLASS_NUMBER
                }
                setText(currentVal ?: "")
                setHint(hint)
                setHintTextColor(0x40283618)
                setTextColor(0xFF283618.toInt())
                typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
                textSize = 18f
                setPadding((20 * dp).toInt(), 0, (20 * dp).toInt(), 0)
                background = GradientDrawable().apply {
                    setColor(0x10283618)
                    cornerRadius = 16 * dp
                }
                layoutParams = LinearLayout.LayoutParams(-1, (60 * dp).toInt()).apply { bottomMargin = (20 * dp).toInt() }
            }
        }

        // --- DYNAMICKÉ INPUTY ---

        // 1. Délka je společná
        root.addView(createLabel("DÉLKA TRÉNINKU (MIN)"))
        val etDuration = createInput("Např. 45", trainingPrefs.getString("kardio_duration_$dayEnglish", ""))
        root.addView(etDuration)

        // 2. Druhý řádek se mění podle typu (Tempo vs Přeskoky)
        val labelSecond = if (isRope) "CELKOVÝ POČET PŘESKOKŮ" else "PRŮMĚRNÉ TEMPO (KM/H)"
        val keySecond   = if (isRope) "kardio_jumps_$dayEnglish" else "kardio_speed_$dayEnglish"
        val hintSecond  = if (isRope) "Např. 1500" else "Např. 8.5"

        root.addView(createLabel(labelSecond))
        val etSecondValue = createInput(hintSecond, trainingPrefs.getString(keySecond, ""), !isRope)
        root.addView(etSecondValue)

        // --- TLAČÍTKA ---
        val btnRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(-1, (56 * dp).toInt()).apply { topMargin = (12 * dp).toInt() }
        }

        val btnCancel = TextView(ctx).apply {
            text = "Zrušit"
            gravity = Gravity.CENTER
            setTextColor(0xFF606C38.toInt())
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, -1, 1f)
            setOnClickListener { dismiss() }
        }

        val btnSave = TextView(ctx).apply {
            text = "Uložit trénink"
            gravity = Gravity.CENTER
            setTextColor(0xFFFEFAE0.toInt())
            typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
            background = GradientDrawable().apply {
                setColor(0xFF283618.toInt())
                cornerRadius = 16 * dp
            }
            layoutParams = LinearLayout.LayoutParams(0, -1, 1.5f)
            setOnClickListener {
                val dur = etDuration.text.toString()
                val secVal = etSecondValue.text.toString().replace(",", ".")

                // 1. Uložit lokálně
                trainingPrefs.edit().apply {
                    putString("kardio_duration_$dayEnglish", dur)
                    putString(keySecond, secVal)
                    apply()
                }

                // 2. Sync do cloudu přes lifecycleScope (bezpečnější než CoroutineScope v fragmentu)
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    val planMap = mutableMapOf<String, String>()
                    val days = listOf("Monday","Tuesday","Wednesday","Thursday","Friday","Saturday","Sunday")
                    val keysToSync = listOf(
                        "type_", "time_", "kardio_type_", "time_kardio_",
                        "kardio_duration_", "kardio_speed_", "kardio_jumps_"
                    )

                    days.forEach { day ->
                        keysToSync.forEach { key ->
                            val fullKey = "$key$day"
                            trainingPrefs.getString(fullKey, null)?.let { planMap[fullKey] = it }
                        }
                    }
                    FirebaseRepository.uploadTrainingPlan(planMap)
                }

                onSaved?.invoke()
                dismiss()
            }
        }

        btnRow.addView(btnCancel)
        btnRow.addView(btnSave)
        root.addView(btnRow)

        return root
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }
}