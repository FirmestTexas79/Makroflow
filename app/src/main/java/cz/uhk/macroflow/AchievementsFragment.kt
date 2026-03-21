package cz.uhk.macroflow

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment

class AchievementsFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_placeholder, container, false).also {
        it.findViewById<TextView>(R.id.tvPlaceholderTitle).text = "🏆 ACHIEVEMENTS"
        it.findViewById<TextView>(R.id.tvPlaceholderDesc).text = "Tvoje úspěchy a odznaky brzy zde!"
    }
}