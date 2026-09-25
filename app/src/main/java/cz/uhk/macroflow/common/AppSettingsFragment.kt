package cz.uhk.macroflow.common

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import cz.uhk.macroflow.BuildConfig
import cz.uhk.macroflow.R
import cz.uhk.macroflow.pokemon.audio.GameAudio

/**
 * Nastavení aplikace (docs/adr/0022). Každé upozornění jde vypnout zvlášť a u denních změnit čas;
 * změna se hned promítne do naplánovaných alarmů. Zvuk Makrosvěta (hudba / efekty zvlášť),
 * parťák na spodní liště, promo kódy (dřív schované ve vývojářských nástrojích) a informace o aplikaci.
 */
class AppSettingsFragment : Fragment() {

    private val reminderRows = mutableMapOf<Reminder, View>()
    private var masterSwitch: MaterialSwitch? = null

    private val requestNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        refreshPermissionCard()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_app_settings, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        buildNotifications(view.findViewById(R.id.llNotifications))
        buildSound(view.findViewById(R.id.llSound))
        buildDisplay(view.findViewById(R.id.llDisplay))
        buildAbout(view.findViewById(R.id.llAbout))
        setupPromo(view)
        view.findViewById<View>(R.id.btnNotifAllow).setOnClickListener { askForNotifications() }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionCard()   // návrat ze systémového nastavení
    }

    // ── Upozornění ──────────────────────────────────────────────────────────

    private val icons = mapOf(
        Reminder.MORNING to R.drawable.ic_line_sun,
        Reminder.WORKOUT to R.drawable.ic_ls_lift,
        Reminder.WATER to R.drawable.ic_line_drop,
        Reminder.EVENING to R.drawable.ic_line_moon,
        Reminder.STREAK to R.drawable.ic_line_flame
    )

    private fun buildNotifications(box: LinearLayout) {
        val ctx = requireContext()
        box.addView(row(box, R.drawable.ic_line_bell, "Upozornění", "Hlavní vypínač všech připomínek").also { r ->
            masterSwitch = switchOf(r, AppSettings.notificationsEnabled(ctx)) { on ->
                AppSettings.setNotificationsEnabled(ctx, on)
                applyEnabledState()
                reschedule()
                if (on) askForNotificationsIfNeeded()
            }
        })
        box.addView(divider())
        Reminder.entries.forEach { rem ->
            val r = row(box, icons.getValue(rem), rem.title, rem.description)
            switchOf(r, AppSettings.reminderSwitch(ctx, rem)) { on ->
                AppSettings.setReminder(ctx, rem, on)
                applyEnabledState()
                reschedule()
            }
            AppSettings.minutes(ctx, rem)?.let { m ->
                val value = r.findViewById<TextView>(R.id.tvSettingValue)
                value.visibility = View.VISIBLE
                value.text = ReminderSchedule.format(m)
                value.contentDescription = "Čas: ${ReminderSchedule.format(m)}, klepnutím změníš"
                value.setOnClickListener { pickTime(rem, value) }
            }
            reminderRows[rem] = r
            box.addView(r)
        }
        applyEnabledState()
    }

    private fun pickTime(rem: Reminder, value: TextView) {
        val ctx = requireContext()
        val current = AppSettings.minutes(ctx, rem) ?: return
        MakroflowTimePicker.show(childFragmentManager, current / 60, current % 60, "Čas: ${rem.title}") { h, m ->
            AppSettings.setMinutes(ctx, rem, h * 60 + m)
            value.text = ReminderSchedule.format(h * 60 + m)
            reschedule()
        }
    }

    /** Při vypnutém hlavním vypínači jsou jednotlivé řádky zašedlé a neaktivní. */
    private fun applyEnabledState() {
        val master = AppSettings.notificationsEnabled(requireContext())
        reminderRows.values.forEach { r ->
            r.alpha = if (master) 1f else 0.45f
            r.findViewById<View>(R.id.swSetting).isEnabled = master
            r.findViewById<View>(R.id.tvSettingValue).isEnabled = master
        }
    }

    private fun reschedule() {
        MakroflowNotifications.scheduleAll(requireContext().applicationContext)
    }

    private fun systemAllowsNotifications() = NotificationManagerCompat.from(requireContext()).areNotificationsEnabled()

    private fun refreshPermissionCard() {
        val v = view ?: return
        val blocked = AppSettings.notificationsEnabled(requireContext()) && !systemAllowsNotifications()
        v.findViewById<View>(R.id.cardNotifBlocked).visibility = if (blocked) View.VISIBLE else View.GONE
    }

    private fun askForNotificationsIfNeeded() {
        if (!systemAllowsNotifications()) askForNotifications()
    }

    /**
     * Android 13+: nejdřív systémový dotaz na oprávnění. Po dvojím odmítnutí už ho systém neukáže,
     * proto se pak otevře systémové nastavení upozornění aplikace (stejně jako při vypnutí v systému).
     */
    private fun askForNotifications() {
        val ctx = requireContext()
        val prefs = ctx.getSharedPreferences("SettingsPrefs", android.content.Context.MODE_PRIVATE)
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        val mayAsk = needsPermission && (!prefs.getBoolean("notif_permission_asked", false) ||
            shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))
        if (mayAsk) {
            prefs.edit().putBoolean("notif_permission_asked", true).apply()
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
        }
    }

    // ── Zvuk ────────────────────────────────────────────────────────────────

    private fun buildSound(box: LinearLayout) {
        val ctx = requireContext()
        box.addView(row(box, R.drawable.ic_line_music, "Hudba v Makrosvětě", "Melodie lokací – město, louka, hory, jeskyně").also { r ->
            switchOf(r, AppSettings.musicEnabled(ctx)) { GameAudio.setMusicEnabled(ctx, it) }
        })
        box.addView(divider())
        box.addView(row(box, R.drawable.ic_line_speaker, "Zvukové efekty", "Začátek souboje, chycení a vítězství").also { r ->
            switchOf(r, AppSettings.sfxEnabled(ctx)) { AppSettings.setSfxEnabled(ctx, it) }
        })
    }

    // ── Zobrazení ───────────────────────────────────────────────────────────

    private fun buildDisplay(box: LinearLayout) {
        val ctx = requireContext()
        box.addView(row(box, R.drawable.ic_line_paw, "Parťák na obrazovce", "Aktivní Makromon se prochází po spodní liště").also { r ->
            switchOf(r, AppSettings.companionOnScreen(ctx)) { on ->
                AppSettings.setCompanionOnScreen(ctx, on)
                (activity as? MainActivity)?.updateMakromonVisibility()
            }
        })
        box.addView(divider())
        box.addView(row(box, R.drawable.ic_ls_lift, "Série z kamery do deníku", "Po sérii naměřené kamerou se sama zapíše váha, opakování, tempo i RIR").also { r ->
            switchOf(r, AppSettings.cameraToDiary(ctx)) { on -> AppSettings.setCameraToDiary(ctx, on) }
        })
    }

    // ── Promo kódy ──────────────────────────────────────────────────────────

    private fun setupPromo(view: View) {
        val et = view.findViewById<TextInputEditText>(R.id.etPromo)
        val apply = {
            val code = et.text?.toString()?.trim().orEmpty()
            if (code.isEmpty()) {
                view.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.tilPromo).error = "Zadej kód"
            } else {
                view.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.tilPromo).error = null
                PromoManager.redeemCode(requireContext(), code, viewLifecycleOwner.lifecycleScope) { et.text?.clear() }
            }
        }
        view.findViewById<View>(R.id.btnPromo).setOnClickListener { apply() }
        et.setOnEditorActionListener { _, _, _ -> apply(); true }
    }

    // ── O aplikaci ──────────────────────────────────────────────────────────

    private fun buildAbout(box: LinearLayout) {
        box.addView(row(box, R.drawable.ic_line_shield, "Soukromí a zdraví", "Jak zacházíme s daty a co aplikace není").also { r ->
            chevron(r); r.setOnClickListener { AboutInfo.show(requireContext()) }
        })
        box.addView(divider())
        box.addView(row(box, R.drawable.ic_nav_info, "Verze", "Makroflow ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"))
        if (BuildConfig.DEBUG) {
            box.addView(divider())
            box.addView(row(box, R.drawable.ic_nav_tools, "Vývojářské nástroje", "Jen v debug buildu").also { r ->
                chevron(r); r.setOnClickListener { (activity as? MainActivity)?.replaceFragment(SettingsFragment()) }
            })
        }
    }

    // ── Pomocné ─────────────────────────────────────────────────────────────

    private fun row(parent: ViewGroup, icon: Int, title: String, sub: String): View =
        layoutInflater.inflate(R.layout.item_setting_row, parent, false).apply {
            findViewById<ImageView>(R.id.ivSettingIcon).setImageResource(icon)
            findViewById<TextView>(R.id.tvSettingTitle).text = title
            findViewById<TextView>(R.id.tvSettingSub).text = sub
        }

    /** Přepínač v řádku; klepnutí kamkoli na řádek ho přepne. */
    private fun switchOf(row: View, checked: Boolean, onChange: (Boolean) -> Unit): MaterialSwitch {
        val sw = row.findViewById<MaterialSwitch>(R.id.swSetting)
        sw.visibility = View.VISIBLE
        sw.isChecked = checked
        sw.contentDescription = row.findViewById<TextView>(R.id.tvSettingTitle).text
        sw.setOnCheckedChangeListener { _, on -> onChange(on) }
        row.setOnClickListener { if (sw.isEnabled) sw.toggle() }
        return sw
    }

    private fun chevron(row: View) { row.findViewById<View>(R.id.ivSettingChevron).visibility = View.VISIBLE }

    private fun divider() = View(requireContext()).apply {
        setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.brand_dark_alpha10))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).apply {
            marginStart = (68 * resources.displayMetrics.density).toInt()
        }
    }
}

/** Soukromí a zdravotní upozornění – z Nastavení i z bočního menu. */
object AboutInfo {
    fun show(ctx: android.content.Context) {
        MaterialAlertDialogBuilder(ctx)
            .setTitle("Soukromí a zdraví")
            .setMessage(
                "Tvoje váha, míry a jídelníček jsou tvoje soukromá věc. Do cloudu je ukládáme jen proto, " +
                "aby ti nezmizely při výměně telefonu – nikomu je neprodáváme ani nesdílíme.\n\n" +
                "Makroflow je pomocník pro trénink a výživu, ne zdravotnický prostředek. Výpočty vycházejí " +
                "z ověřených rovnic a studií, ale nenahrazují lékaře, nutričního terapeuta ani trenéra. " +
                "Při zdravotních potížích, v těhotenství nebo při poruchách příjmu potravy se poraď s odborníkem."
            )
            .setPositiveButton("Rozumím", null)
            .show()
    }
}
