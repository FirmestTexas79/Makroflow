package cz.uhk.macroflow

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.*

class ProfileFragment : Fragment() {

    private var selectedMultiplier: Float = 1.2f
    private var isExpanded = false

    private lateinit var circleContainer: FrameLayout
    private lateinit var tvDesc: TextView
    private lateinit var etWeight: EditText
    private lateinit var etHeight: EditText
    private lateinit var etAge: EditText
    private lateinit var toggleGender: MaterialButtonToggleGroup
    private lateinit var iconsCenter: List<ImageView>
    private lateinit var partsCircle: List<ImageView>

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.activity_setup, container, false)
        view.setPadding(0, 44, 0, 0)

        etWeight = view.findViewById(R.id.etWeight)
        etHeight = view.findViewById(R.id.etHeight)
        etAge = view.findViewById(R.id.etAge)
        toggleGender = view.findViewById(R.id.toggleGender)
        tvDesc = view.findViewById(R.id.tvLifestyleDesc)
        circleContainer = view.findViewById(R.id.circleLifestyle)

        iconsCenter = listOf(
            view.findViewById(R.id.iconCenterLow),
            view.findViewById(R.id.iconCenterMed),
            view.findViewById(R.id.iconCenterHigh)
        )
        partsCircle = listOf(
            view.findViewById(R.id.partLezerni),
            view.findViewById(R.id.partAktivni),
            view.findViewById(R.id.partSportovec)
        )

        val userPrefs = requireContext().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        loadUserData(userPrefs)

        // DŮLEŽITÉ: Aby se layout nehýbal, nastavíme popisek na INVISIBLE (zabírá místo, ale není vidět)
        tvDesc.visibility = View.INVISIBLE

        view.findViewById<View>(R.id.clickLezerni).setOnClickListener { selectMode(1.2f, "Ležérní - Minimum pohybu") }
        view.findViewById<View>(R.id.clickAktivni).setOnClickListener { selectMode(1.4f, "Aktivní - Práce v pohybu") }
        view.findViewById<View>(R.id.clickSportovec).setOnClickListener { selectMode(1.6f, "Sportovec - Těžké tréninky") }

        view.findViewById<View>(R.id.setupMainLayout).setOnClickListener { if (isExpanded) shrinkCircle() }
        view.findViewById<Button>(R.id.btnSave).setOnClickListener { saveAllData() }
        view.findViewById<MaterialCardView>(R.id.cardOptionalMetrics).setOnClickListener { showMetricsBottomSheet() }

        return view
    }

    private fun selectMode(multiplier: Float, description: String) {
        selectedMultiplier = multiplier
        updateCircleVisuals(multiplier)

        if (!isExpanded) {
            isExpanded = true
            circleContainer.animate().scaleX(1.4f).scaleY(1.4f).translationY(-60f).setDuration(450).start()
            tvDesc.text = description
            tvDesc.visibility = View.VISIBLE
            tvDesc.alpha = 0f
            tvDesc.animate().alpha(1f).translationY(-10f).setDuration(450).start()
        } else {
            tvDesc.text = description
        }
    }

    private fun updateCircleVisuals(m: Float) {
        val targets = listOf(1.2f, 1.4f, 1.6f)
        targets.forEachIndexed { index, value ->
            val isSelected = value == m
            partsCircle[index].animate().alpha(if (isSelected) 1.0f else 0.2f).setDuration(300).start()
            iconsCenter[index].animate().alpha(if (isSelected) 1.0f else 0.0f)
                .scaleX(if (isSelected) 1.1f else 0.8f).scaleY(if (isSelected) 1.1f else 0.8f)
                .setDuration(300).start()
        }
    }

    private fun shrinkCircle() {
        isExpanded = false
        circleContainer.animate().scaleX(1.0f).scaleY(1.0f).translationY(0f).setDuration(300).start()
        tvDesc.animate().alpha(0f).translationY(0f).setDuration(300).withEndAction { tvDesc.visibility = View.INVISIBLE }.start()
    }

    private fun showMetricsBottomSheet() {
        val dialog = BottomSheetDialog(requireContext())
        val sheetView = layoutInflater.inflate(R.layout.layout_metrics_sheet, null)
        dialog.setContentView(sheetView)

        val prefs = requireContext().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val calendar = Calendar.getInstance()
        val today = Calendar.getInstance() // Fixní bod pro dnešek

        val tvDate = sheetView.findViewById<TextView>(R.id.tvSelectedDate)
        val btnPrev = sheetView.findViewById<ImageButton>(R.id.btnPrevDay)
        val btnNext = sheetView.findViewById<ImageButton>(R.id.btnNextDay)

        val metricMap = mapOf(
            R.id.etNeck to "neck", R.id.etChest to "chest", R.id.etBicep to "bicep",
            R.id.etForearm to "forearm", R.id.etWaist to "waist", R.id.etAbdomen to "abdomen",
            R.id.etThigh to "thigh", R.id.etCalf to "calf"
        )

        fun refreshSheet() {
            val dateKey = sdf.format(calendar.time)
            val isToday = dateKey == sdf.format(today.time)

            tvDate.text = if (isToday) "Dnes" else dateKey

            // LOGIKA BLOKOVÁNÍ: Pokud jsme na dnešku, zakážeme tlačítko "Vpřed"
            if (calendar.after(today) || isToday) {
                btnNext.isEnabled = false
                btnNext.alpha = 0.3f // Vizuálně zešedne
            } else {
                btnNext.isEnabled = true
                btnNext.alpha = 1.0f
            }

            metricMap.forEach { (viewId, key) ->
                val et = sheetView.findViewById<EditText>(viewId)
                et.setText(prefs.getString("${dateKey}_$key", ""))
            }
        }

        refreshSheet()

        btnPrev.setOnClickListener {
            calendar.add(Calendar.DAY_OF_YEAR, -1)
            refreshSheet()
        }

        btnNext.setOnClickListener {
            // Kontrola, abychom nešli za dnešek
            if (calendar.before(today)) {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
                refreshSheet()
            }
        }

        sheetView.findViewById<Button>(R.id.btnConfirmMetrics).setOnClickListener {
            val editor = prefs.edit()
            val dateKey = sdf.format(calendar.time)

            metricMap.forEach { (viewId, key) ->
                val value = sheetView.findViewById<EditText>(viewId).text.toString()
                editor.putString("${dateKey}_$key", value)
            }
            editor.apply()
            Toast.makeText(context, "Uloženo pro $dateKey", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun saveAllData() {
        val prefs = requireContext().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        val weight = etWeight.text.toString()
        val height = etHeight.text.toString()
        val age = etAge.text.toString()
        val gender = if (toggleGender.checkedButtonId == R.id.btnMale) "male" else "female"

        if (weight.isNotEmpty() && height.isNotEmpty() && age.isNotEmpty()) {
            editor.putString("weightAkt", weight).putString("height", height).putString("age", age)
            editor.putString("gender", gender).putFloat("multiplier", selectedMultiplier)
            if (editor.commit()) {
                Toast.makeText(context, "Profil sportovce aktualizován! 💪", Toast.LENGTH_SHORT).show()
                if (isExpanded) shrinkCircle()
            }
        } else {
            Toast.makeText(context, "Doplň prosím všechny údaje", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadUserData(prefs: android.content.SharedPreferences) {
        val colorDark = Color.parseColor("#283618")
        etWeight.setText(prefs.getString("weightAkt", "83"))
        etHeight.setText(prefs.getString("height", "175"))
        etAge.setText(prefs.getString("age", "22"))
        listOf(etWeight, etHeight, etAge).forEach { it.setTextColor(colorDark) }
        val gender = prefs.getString("gender", "male")
        toggleGender.check(if (gender == "male") R.id.btnMale else R.id.btnFemale)
        selectedMultiplier = prefs.getFloat("multiplier", 1.2f)
        updateCircleVisuals(selectedMultiplier)
    }
}