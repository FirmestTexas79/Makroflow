package cz.uhk.macroflow.common

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import cz.uhk.macroflow.R

class DisclaimerFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_placeholder, container, false).also {
        it.findViewById<TextView>(R.id.tvPlaceholderTitle).text = "📋 PROHLÁŠENÍ"
        it.findViewById<TextView>(R.id.tvPlaceholderDesc).text =
            "Makroflow není špion. Tvoje váha, míry a to, kolik jsi dneska snědl banánů, jsou tvá soukromá věc. Data ukládáme do tvého cloudu jen proto, aby se ti nesmazaly medaile, až si koupíš novej mobil. Neprodáváme tvoje info korporacím, vládám ani tajným službám. Máme radši tvoje svaly než tvoje data"
    }
}