package cz.uhk.macroflow

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment

class DashboardFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.activity_dashboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        displayMacros(view)
    }

    private fun displayMacros(view: View) {
        val data = MacroCalculator.calculate(requireContext())

        // 1. Reference na UI
        val tvCalories = view.findViewById<TextView>(R.id.tvCalories)
        val tvValueProtein = view.findViewById<TextView>(R.id.tvValueProtein)
        val tvValueCarbs = view.findViewById<TextView>(R.id.tvValueCarbs)
        val tvValueFat = view.findViewById<TextView>(R.id.tvValueFat)
        val tvValueWater = view.findViewById<TextView>(R.id.tvValueWater)
        val tvTrainingStatus = view.findViewById<TextView>(R.id.tvTrainingStatus)

        val pbF = view.findViewById<ProgressBar>(R.id.progressFat)
        val pbC = view.findViewById<ProgressBar>(R.id.progressCarbs)
        val pbP = view.findViewById<ProgressBar>(R.id.progressProtein)

        // 2. Texty a dynamická hydratace
        tvCalories.text = "${data.calories.toInt()}"
        tvValueProtein.text = "${data.protein.toInt()}g"
        tvValueCarbs.text = "${data.carbs.toInt()}g"
        tvValueFat.text = "${data.fat.toInt()}g"

        // Dynamický výpočet vody: Základ + Bonus podle tréninku
        val trainingType = data.trainingType.lowercase()
        val waterBonus = when (trainingType) {
            "legs" -> 1.0
            "push", "pull" -> 0.5
            else -> 0.0
        }
        val finalWater = data.water + waterBonus
        tvValueWater.text = String.format("%.1f L", finalWater)

        tvTrainingStatus.text = "DNES: ${data.trainingType.uppercase()}"

        // 3. Výpočet proporcí (pro sekvenční graf)
        val pCal = data.protein * 4
        val cCal = data.carbs * 4
        val fCal = data.fat * 9
        val total = pCal + cCal + fCal

        val fProp = (fCal / total).toFloat()
        val cProp = (cCal / total).toFloat()
        val pProp = (pCal / total).toFloat()

        pbF.max = 1000
        pbC.max = 1000
        pbP.max = 1000

        // 4. Rotace - plynulé navazování (Tuky -> Sacharidy -> Bílkoviny)
        // Vše začíná na 12:00 (-90 stupňů)
        val startAngle = -90f
        pbF.rotation = startAngle
        pbC.rotation = startAngle - (fProp * 360f)
        pbP.rotation = startAngle - (fProp * 360f) - (cProp * 360f)

        // 5. SEKVENČNÍ ANIMACE
        val animF = ObjectAnimator.ofInt(pbF, "progress", 0, (fProp * 1000).toInt())
        val animC = ObjectAnimator.ofInt(pbC, "progress", 0, (cProp * 1000).toInt())
        val animP = ObjectAnimator.ofInt(pbP, "progress", 0, (pProp * 1000).toInt())

        animF.duration = 600
        animC.duration = 800
        animP.duration = 600

        val animatorSet = AnimatorSet()
        animatorSet.playSequentially(animF, animC, animP)
        animatorSet.interpolator = DecelerateInterpolator()
        animatorSet.start()
    }
}