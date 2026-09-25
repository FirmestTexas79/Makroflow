package cz.uhk.macroflow.nutrition

import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.View
import android.widget.TextView
import cz.uhk.macroflow.R
import cz.uhk.macroflow.data.SnackEntity
import cz.uhk.macroflow.energy.FoodEnergy
import java.util.Locale
import kotlin.math.roundToInt

/** Naplnění řádku `item_snack_row` – sdílí Snacky i Složit jídlo (docs/adr/0021). */
object SnackRowBinder {

    val PROTEIN = Color.parseColor("#606C38")
    val CARBS = Color.parseColor("#D08A3C")      // o stupeň tmavší než #E9B072 – čitelné na krémové
    val FAT = Color.parseColor("#BC6C25")

    fun bind(
        row: View,
        snack: SnackEntity,
        onClick: (SnackEntity) -> Unit,
        onQuickAdd: ((SnackEntity) -> Unit)?,
        onLongClick: ((SnackEntity) -> Unit)? = null
    ) {
        row.findViewById<TextView>(R.id.tvSnackName).text = snack.name
        row.findViewById<TextView>(R.id.tvSnackMeta).text = meta(snack)
        row.findViewById<TextView>(R.id.tvSnackKcal).text =
            FoodEnergy.kcalPreferLabel(snack.energyKj, snack.p, snack.s, snack.t, snack.fiber).roundToInt().toString()
        row.findViewById<MacroDonutView>(R.id.donutSnack).setMacros(snack.p, snack.s, snack.t)
        row.setOnClickListener { onClick(snack) }
        if (onLongClick != null) row.setOnLongClickListener { onLongClick(snack); true } else row.setOnLongClickListener(null)
        row.findViewById<View>(R.id.btnQuickAdd).apply {
            visibility = if (onQuickAdd != null) View.VISIBLE else View.GONE
            setOnClickListener { onQuickAdd?.invoke(snack) }
            contentDescription = "Přidat porci ${snack.weight}: ${snack.name}"
        }
        row.contentDescription = "${snack.name}, ${snack.weight}"
    }

    /** „180 g  ·  B 10  S 40  T 8“ – písmena v barvách maker. */
    fun meta(snack: SnackEntity): CharSequence {
        val sb = SpannableStringBuilder()
        val start = sb.length
        sb.append(portionLabel(snack.weight))
        sb.setSpan(StyleSpan(Typeface.BOLD), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.append("   ")
        listOf("B" to (snack.p to PROTEIN), "S" to (snack.s to CARBS), "T" to (snack.t to FAT)).forEach { (label, pair) ->
            val (v, c) = pair
            val s = sb.length
            sb.append("$label ${grams(v)}")
            sb.setSpan(ForegroundColorSpan(c), s, s + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(StyleSpan(Typeface.BOLD), s, s + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.append("  ")
        }
        return sb
    }

    fun portionLabel(weight: String): String {
        val g = SnackCatalog.portionGrams(weight)
        return "${grams(g)} ${SnackCatalog.unit(weight)}"
    }

    /** Celá čísla bez „,0“, jinak jedno desetinné místo s čárkou. */
    fun grams(v: Float): String =
        if (v >= 10f || v % 1f == 0f) v.roundToInt().toString()
        else String.format(Locale.US, "%.1f", v).replace('.', ',')
}
