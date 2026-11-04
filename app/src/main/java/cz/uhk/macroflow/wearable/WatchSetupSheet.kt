package cz.uhk.macroflow.wearable

import android.content.Intent
import android.content.DialogInterface
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import cz.uhk.macroflow.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Průvodce připojením hodinek: „Jaké máš hodinky?“ → stručný postup pro danou značku → povolení Health Connect →
 * co skutečně dorazilo. Funguje pro jakékoli hodinky, které umí zapisovat do Health Connect.
 */
class WatchSetupSheet : BottomSheetDialogFragment() {

    private class Brand(val emoji: String, val name: String, val steps: List<String>, val note: String? = null)

    private val brands = listOf(
        Brand("🟦", "Garmin", listOf("Otevři aplikaci Garmin Connect.", "Více → Nastavení → Připojené aplikace → Health Connect.", "Zapni zápis spánku, kroků, tepu a aktivit.")),
        Brand("🔵", "Samsung Galaxy Watch", listOf("Otevři aplikaci Samsung Health.", "Nastavení → Health Connect.", "Povol zápis spánku, kroků, tepu a cvičení.")),
        Brand("🟢", "Pixel Watch / Fitbit", listOf("Otevři aplikaci Fitbit (Pixel Watch ji používá).", "Profil → Nastavení → Health Connect.", "Zapni sdílení dat se systémem.")),
        Brand("🔴", "Polar", listOf("Otevři aplikaci Polar Flow.", "V nastavení najdi Health Connect.", "Zapni sdílení spánku, kroků a tréninků.")),
        Brand("⚪", "Withings", listOf("Otevři aplikaci Health Mate.", "V nastavení najdi Health Connect.", "Zapni sdílení dat.")),
        Brand("🟠", "Xiaomi / Amazfit / Zepp", listOf("Otevři aplikaci Zepp nebo Mi Fitness.", "V profilu nebo nastavení najdi Health Connect.", "Zapni zápis spánku, kroků, tepu a cvičení.")),
        Brand("🟥", "Huawei", listOf("Huawei Health zatím do Health Connect nezapisuje."),
            "Jde to přes pomocnou aplikaci, která data z Huawei přepíše do Health Connect (např. Health Sync). Aplikace funguje i bez hodinek."),
        Brand("⌚", "Jiné hodinky", listOf("Otevři aplikaci, kterou používáš s hodinkami.", "V nastavení hledej Health Connect (nebo Zdraví Connect).", "Zapni zápis spánku, kroků, tepu a cvičení."))
    )

    private lateinit var root: LinearLayout
    private var brand: Brand? = null
    private val d get() = resources.displayMetrics.density
    private fun dp(v: Int) = (v * d).toInt()

    private val perm = registerForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        if (granted.isEmpty()) Toast.makeText(requireContext(), "Nic nebylo povoleno. Můžeš to zkusit znovu nebo povolit přímo v Health Connect.", Toast.LENGTH_LONG).show()
        else { HealthConnectSource.setEnabled(requireContext(), true); loadResult() }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        root = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(20), dp(20), dp(28)) }
        showBrands()
        return ScrollView(requireContext()).apply { setBackgroundResource(R.drawable.bg_sheet_cream); addView(root) }
    }

    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)?.apply {
            findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.setBackgroundColor(Color.TRANSPARENT)
            behavior.skipCollapsed = true; behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        runCatching { parentFragmentManager.setFragmentResult("watch_setup", Bundle()) }
    }

    private fun color(id: Int) = ContextCompat.getColor(requireContext(), id)

    private fun title(t: String) = TextView(requireContext()).apply { text = t; textSize = 22f; setTypeface(null, Typeface.BOLD); setTextColor(color(R.color.brand_dark)) }
    private fun body(t: String, top: Int = 8) = TextView(requireContext()).apply {
        text = t; textSize = 15f; setTextColor(color(R.color.brand_dark)); setLineSpacing(0f, 1.15f); setPadding(0, dp(top), 0, 0)
    }

    private fun card(vararg views: View, tint: Int = R.color.brand_primary_alpha10, onClick: (() -> Unit)? = null) = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(14))
        background = GradientDrawable().apply { cornerRadius = dp(20).toFloat(); setColor(color(tint)) }
        views.forEach { addView(it) }
        if (onClick != null) { isClickable = true; setOnClickListener { onClick() } }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) }
    }

    private fun button(t: String, primary: Boolean = true, onClick: () -> Unit) = MaterialButton(requireContext()).apply {
        text = t; isAllCaps = false; cornerRadius = dp(18)
        if (primary) { setBackgroundColor(color(R.color.brand_primary)); setTextColor(Color.WHITE) }
        else { setBackgroundColor(Color.TRANSPARENT); setTextColor(color(R.color.brand_primary)); strokeWidth = dp(1); strokeColor = android.content.res.ColorStateList.valueOf(color(R.color.brand_primary)) }
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) }
    }

    private fun reset(t: String, sub: String? = null) { root.removeAllViews(); root.addView(title(t)); sub?.let { root.addView(body(it)) } }

    // ── Obrazovky ───────────────────────────────────────────────────────────

    private fun showBrands() {
        reset("Jaké máš hodinky?", "Nevadí, když to není Garmin. Většinu hodinek jde připojit přes Health Connect, společný sklad zdravotních dat v telefonu.")
        val ctx = requireContext()
        if (HealthConnectSource.isEnabled(ctx)) {
            root.addView(card(body("Health Connect je připojený ✓", 0).apply { setTypeface(null, Typeface.BOLD) },
                MaterialButton(ctx).apply { text = "Odpojit"; isAllCaps = false; setBackgroundColor(Color.TRANSPARENT); setTextColor(color(R.color.brand_primary))
                    setOnClickListener { HealthConnectSource.setEnabled(ctx, false); showBrands() } }, tint = R.color.brand_cream))
        }
        brands.forEach { b ->
            root.addView(card(LinearLayout(ctx).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(TextView(ctx).apply { text = b.emoji; textSize = 22f; setPadding(0, 0, dp(14), 0) })
                addView(TextView(ctx).apply { text = b.name; textSize = 16f; setTypeface(null, Typeface.BOLD); setTextColor(color(R.color.brand_dark)) })
            }) { brand = b; showBrand(b) })
        }
        root.addView(button("Nemám hodinky", primary = false) { dismiss() })
        root.addView(body("Bez hodinek aplikace funguje dál. Hodinky ji hlavně zpřesní: výdej energie, spánek a doporučení ke tréninku.", 12))
    }

    private fun showBrand(b: Brand) {
        reset("${b.emoji} ${b.name}")
        val ctx = requireContext()
        root.addView(card(*b.steps.mapIndexed { i, s -> body(if (b.steps.size > 1) "${i + 1}. $s" else s, if (i == 0) 0 else 6) }.toTypedArray()))
        b.note?.let { root.addView(body(it, 12)) }
        if (b.name == "Huawei") { root.addView(button("Zpět", primary = false) { showBrands() }); return }
        root.addView(body("Až to máš zapnuté, povol aplikaci číst tyto údaje.", 14))
        when (HealthConnectSource.status(ctx)) {
            HealthConnectSource.Status.AVAILABLE -> {
                root.addView(button("Povolit čtení dat") { perm.launch(HealthConnectSource.permissions) })
                root.addView(button("Otevřít Health Connect", primary = false) { open(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)) })
                root.addView(body("Kdyby se okno s povolením neukázalo (Android ho po dvou odmítnutích přestane zobrazovat), povol čtení ručně v Health Connect → Přístup aplikací → Makroflow.", 10))
            }
            else -> {
                val update = HealthConnectSource.status(ctx) == HealthConnectSource.Status.NEEDS_UPDATE
                root.addView(card(body(if (update) "Health Connect v telefonu potřebuje aktualizaci." else "V telefonu chybí Health Connect. Je to bezplatná aplikace od Googlu (od Androidu 14 je přímo v systému).", 0),
                    tint = R.color.brand_cream))
                root.addView(button(if (update) "Aktualizovat v Google Play" else "Nainstalovat z Google Play") {
                    open(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${HealthConnectSource.PROVIDER_PACKAGE}")).setPackage("com.android.vending"))
                })
            }
        }
        root.addView(button("Zpět", primary = false) { showBrands() })
    }

    private fun open(i: Intent) {
        runCatching { startActivity(i) }.onFailure { Toast.makeText(requireContext(), "Tohle se nepodařilo otevřít.", Toast.LENGTH_SHORT).show() }
    }

    private fun loadResult() {
        reset("Načítám tvoje data…", "Může to chvíli trvat, poprvé se stahuje posledních 30 dní.")
        val app = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { WearableHub.run(app, days = 30); HealthConnectSource.summary(app) } }
            if (!isAdded) return@launch
            r.fold({ showResult(it) }, { reset("Něco se nepovedlo", it.message ?: "Zkus to prosím znovu za chvíli."); root.addView(button("Zkusit znovu") { loadResult() }) })
        }
    }

    private fun showResult(s: HealthConnectMap.Summary) {
        reset(if (s.any) "Hotovo, hodinky jsou připojené" else "Zatím nic nedorazilo")
        fun row(ok: Boolean, t: String) = body("${if (ok) "✓" else "○"}  $t", 6)
        root.addView(card(row(s.nights > 0, if (s.nights > 0) "Spánek: ${s.nights} nocí" else "Spánek: zatím nic"),
            row(s.stepDays > 0, if (s.stepDays > 0) "Kroky a spálené kalorie: ${s.stepDays} dní" else "Kroky: zatím nic"),
            row(s.restHrDays > 0, if (s.restHrDays > 0) "Tep v klidu: ${s.restHrDays} dní" else "Tep v klidu: zatím nic"),
            row(s.workouts > 0, if (s.workouts > 0) "Tréninky: ${s.workouts}" else "Tréninky: zatím žádný")))
        root.addView(body(if (s.any) "Co chybí, hodinky nebo jejich aplikace do Health Connect nezapisují. Aplikace si poradí i s tím, co má, a zbytek si dopočítá sama."
            else "Hodinky se nejdřív musí synchronizovat se svou aplikací a ta pak zapíše do Health Connect. Otevři aplikaci hodinek, počkej chvíli a zkus to znovu.", 12))
        root.addView(button(if (s.any) "Hotovo" else "Zkusit znovu") { if (s.any) dismiss() else loadResult() })
        if (!s.any) root.addView(button("Zpět na výběr hodinek", primary = false) { showBrands() })
    }

    companion object {
        fun show(fm: androidx.fragment.app.FragmentManager) { WatchSetupSheet().show(fm, "watch_setup") }
    }
}
