package cz.uhk.macroflow

import android.os.Bundle
import android.view.*
import android.widget.*
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import android.graphics.drawable.GradientDrawable

/**
 * Makroflow stylový time picker — bottom sheet s number pickery.
 * Nahrazuje systémový TimePickerDialog.
 *
 * Použití:
 *   MakroflowTimePicker.show(parentFragmentManager, "07", "30", "Čas tréninku") { h, m ->
 *       val timeStr = "%02d:%02d".format(h, m)
 *   }
 */
class MakroflowTimePicker : BottomSheetDialogFragment() {

    var onTimeSelected: ((hour: Int, minute: Int) -> Unit)? = null
    private var initHour   = 7
    private var initMinute = 0
    private var title      = "Nastav čas"

    companion object {
        fun show(
            fm: androidx.fragment.app.FragmentManager,
            initH: Int = 7,
            initM: Int = 0,
            title: String = "Nastav čas",
            onSelected: (Int, Int) -> Unit
        ): MakroflowTimePicker {
            return MakroflowTimePicker().apply {
                initHour        = initH
                initMinute      = initM
                this.title      = title
                onTimeSelected  = onSelected
            }.also { it.show(fm, "MakroflowTimePicker") }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val ctx = requireContext()
        val dp  = ctx.resources.displayMetrics.density

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFFEFAE0.toInt())
            setPadding((24*dp).toInt(), (20*dp).toInt(), (24*dp).toInt(), (32*dp).toInt())
        }

        // ── Handle bar ────────────────────────────────────────────────
        root.addView(android.view.View(ctx).apply {
            background = GradientDrawable().apply {
                setColor(0x30283618)
                cornerRadius = 4 * dp
            }
            layoutParams = LinearLayout.LayoutParams((40*dp).toInt(), (4*dp).toInt()).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = (16*dp).toInt()
            }
        })

        // ── Titulek ───────────────────────────────────────────────────
        root.addView(TextView(ctx).apply {
            text      = title
            textSize  = 13f
            setTextColor(0xFF606C38.toInt())
            typeface  = android.graphics.Typeface.DEFAULT_BOLD
            letterSpacing = 0.08f
            gravity   = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (8*dp).toInt() }
        })

        // ── Čas zobrazení (velký) ─────────────────────────────────────
        var selH = initHour; var selM = initMinute

        val tvBigTime = TextView(ctx).apply {
            text     = "%02d:%02d".format(selH, selM)
            textSize = 56f
            typeface = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.NORMAL)
            setTextColor(0xFF283618.toInt())
            gravity  = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (4*dp).toInt() }
        }
        root.addView(tvBigTime)

        // Doplňkový text — délka tréninku
        root.addView(TextView(ctx).apply {
            text      = "Trénink trvá ~1h 15min → konec v %02d:%02d".format(
                (selH + (selM + 75) / 60) % 24, (selM + 75) % 60)
            textSize  = 11f
            setTextColor(0xFF606C38.toInt())
            gravity   = Gravity.CENTER
            alpha     = 0.7f
            id        = android.R.id.text2
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (20*dp).toInt() }
        })

        // Helper pro aktualizaci end time textu
        fun updateDisplay() {
            tvBigTime.text = "%02d:%02d".format(selH, selM)
            val endH = (selH + (selM + 75) / 60) % 24
            val endM = (selM + 75) % 60
            root.findViewById<TextView>(android.R.id.text2)?.text =
                "Trénink trvá ~1h 15min → konec v %02d:%02d".format(endH, endM)
        }

        // ── Pickery hodiny + minuty ────────────────────────────────────
        val pickerRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (24*dp).toInt() }
        }

        fun makePickerSection(
            label: String,
            maxVal: Int,
            initVal: Int,
            step: Int = 1,
            onChange: (Int) -> Unit
        ): LinearLayout {
            val sec = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity     = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            // Label
            sec.addView(TextView(ctx).apply {
                text     = label
                textSize = 10f
                setTextColor(0xFF606C38.toInt())
                gravity  = Gravity.CENTER
                alpha    = 0.7f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = (8*dp).toInt() }
            })

            // ▲ tlačítko
            fun makeBtn(symbol: String, onClick: () -> Unit) = TextView(ctx).apply {
                text      = symbol
                textSize  = 22f
                setTextColor(0xFF283618.toInt())
                gravity   = Gravity.CENTER
                background = GradientDrawable().apply {
                    setColor(0x12283618)
                    cornerRadius = 12 * dp
                }
                layoutParams = LinearLayout.LayoutParams(
                    (52*dp).toInt(), (52*dp).toInt()
                ).apply { gravity = Gravity.CENTER_HORIZONTAL }
                setOnClickListener { onClick() }
            }

            // Hodnota
            var current = initVal
            val tvVal = TextView(ctx).apply {
                text     = "%02d".format(current)
                textSize = 36f
                typeface = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.NORMAL)
                setTextColor(0xFF283618.toInt())
                gravity  = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin    = (6*dp).toInt()
                    bottomMargin = (6*dp).toInt()
                }
            }

            val btnUp   = makeBtn("▲") {
                current = (current + step) % (maxVal + 1)
                tvVal.text = "%02d".format(current)
                onChange(current)
                updateDisplay()
            }
            val btnDown = makeBtn("▼") {
                current = if (current - step < 0) maxVal - (step - 1) else current - step
                tvVal.text = "%02d".format(current)
                onChange(current)
                updateDisplay()
            }

            sec.addView(btnUp)
            sec.addView(tvVal)
            sec.addView(btnDown)
            return sec
        }

        pickerRow.addView(makePickerSection("HOD", 23, selH, 1) { selH = it })

        // Dvojtečka uprostřed
        pickerRow.addView(TextView(ctx).apply {
            text     = ":"
            textSize = 36f
            typeface = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.NORMAL)
            setTextColor(0x60283618)
            gravity  = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin  = (8*dp).toInt()
                rightMargin = (8*dp).toInt()
                gravity     = Gravity.CENTER_VERTICAL
            }
        })

        pickerRow.addView(makePickerSection("MIN", 59, selM, 5) { selM = it })

        root.addView(pickerRow)

        // ── Potvrzovací tlačítko ──────────────────────────────────────
        root.addView(TextView(ctx).apply {
            text      = "Uložit čas"
            textSize  = 16f
            setTextColor(0xFFFEFAE0.toInt())
            gravity   = Gravity.CENTER
            typeface  = android.graphics.Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(0xFF283618.toInt())
                cornerRadius = 16 * dp
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (56*dp).toInt()
            )
            setPadding(0, 0, 0, 0)
            setOnClickListener {
                onTimeSelected?.invoke(selH, selM)
                dismiss()
            }
        })

        return root
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
        }
    }
}