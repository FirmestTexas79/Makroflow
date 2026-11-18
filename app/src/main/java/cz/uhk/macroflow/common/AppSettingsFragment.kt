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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        buildWearable(view.findViewById(R.id.llWearable))
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
        Reminder.STREAK to R.drawable.ic_line_flame,
        Reminder.WEEKLY to R.drawable.ic_line_repeat,
        Reminder.REST to R.drawable.ic_line_moon
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
        box.addView(divider())
        box.addView(row(box, R.drawable.ic_line_paw, "Skok do Makrosvěta po sérii", "Po zápisu série na chvíli vyskočí bublina „+2 ⚡ · Skok do Makrosvěta“").also { r ->
            switchOf(r, AppSettings.gymBubble(ctx)) { on -> AppSettings.setGymBubble(ctx, on) }
        })
        box.addView(divider())
        val restRow = row(box, R.drawable.ic_line_bell, "Konec pauzy v Makrosvětu", "Odpočet pauzy a výzva „Zpět na trénink“. Délka podle cviku (dřep 4 min, upažování 1 min), klepnutím nastavíš pevnou")
        switchOf(restRow, AppSettings.restReminder(ctx)) { on -> AppSettings.setRestReminder(ctx, on) }
        restRow.findViewById<TextView>(R.id.tvSettingValue).apply {
            fun fmt(s: Int) = if (s == AppSettings.REST_AUTO) "Podle cviku" else "${s / 60}:${String.format(java.util.Locale.US, "%02d", s % 60)}"
            visibility = View.VISIBLE
            text = fmt(AppSettings.restSeconds(ctx))
            contentDescription = "Délka pauzy ${fmt(AppSettings.restSeconds(ctx))}, klepnutím změníš"
            setOnClickListener {
                val opts = AppSettings.REST_OPTIONS
                val next = opts[(opts.indexOf(AppSettings.restSeconds(ctx)) + 1).mod(opts.size)]
                AppSettings.setRestSeconds(ctx, next)
                text = fmt(next); contentDescription = "Délka pauzy ${fmt(next)}, klepnutím změníš"
                performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
            }
        }
        box.addView(restRow)
        box.addView(divider())
        box.addView(row(box, R.drawable.ic_line_shield, "Hlášení pádů", "Když aplikace spadne, odešle se anonymní chybový záznam bez tvých údajů. Pomáhá nám to opravit chyby").also { r ->
            switchOf(r, AppSettings.crashReports(ctx)) { on -> AppSettings.setCrashReports(ctx, on) }
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
        box.addView(row(box, R.drawable.ic_line_gift, "Pozvi kamaráda do testu", "Pošli odkaz na testovací verzi z Google Play").also { r ->
            chevron(r); r.setOnClickListener { inviteTester() }
        })
        box.addView(divider())
        box.addView(row(box, R.drawable.ic_line_pencil, "Napsat zpětnou vazbu", "Chyba, nápad nebo co tě štve, přečtu všechno").also { r ->
            chevron(r); r.setOnClickListener { sendFeedback() }
        })
        box.addView(divider())
        box.addView(row(box, R.drawable.ic_nav_info, "Ukázková data", "Naplní prázdnou aplikaci 14 dny jídla, vody a kroků na vyzkoušení").also { r ->
            chevron(r); r.setOnClickListener { toggleDemo() }
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

    private fun inviteTester() {
        val text = "Ahoj, testuju svoji appku Makroflow (jídlo, trénink a k tomu malá hra s Makromony). " +
            "Pomohl bys mi? Pošli mi svůj Gmail, přidám tě do testu a pak se přihlas tady:\n" +
            "https://play.google.com/apps/testing/${BuildConfig.APPLICATION_ID}\n" +
            "Appku pak stáhneš tady: https://play.google.com/store/apps/details?id=${BuildConfig.APPLICATION_ID}\n" +
            "Stačí ji mít 14 dní nainstalovanou, díky!"
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text)
        }, "Pozvat kamaráda přes…"))
    }

    private fun sendFeedback() {
        val intent = Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:")).apply {
            putExtra(Intent.EXTRA_EMAIL, arrayOf("samuel.matasovsk@gmail.com"))
            putExtra(Intent.EXTRA_SUBJECT, "Makroflow ${BuildConfig.VERSION_NAME} – zpětná vazba")
            putExtra(Intent.EXTRA_TEXT, "\n\n—\nVerze ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}")
        }
        runCatching { startActivity(intent) }.onFailure {
            Toast.makeText(requireContext(), "Nemáš nastavený e-mail. Napiš na samuel.matasovsk@gmail.com", Toast.LENGTH_LONG).show()
        }
    }

    private fun toggleDemo() {
        val ctx = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val active = cz.uhk.macroflow.demo.DemoData.isActive(ctx)
            val empty = withContext(Dispatchers.IO) { cz.uhk.macroflow.demo.DemoData.isEmptyApp(ctx) }
            if (!active && !empty) {
                Toast.makeText(ctx, "Ukázková data jdou zapnout jen do prázdné aplikace.", Toast.LENGTH_LONG).show(); return@launch
            }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(if (active) "Smazat ukázková data?" else "Zapnout ukázková data?")
                .setMessage(if (active) "Odstraní jen data vložená demem, tvoje vlastní záznamy zůstanou." else "Vloží vymyšlených 14 dní, abys viděl grafy a přehledy. Kdykoli je jde smazat.")
                .setNegativeButton("Zrušit", null)
                .setPositiveButton(if (active) "Smazat" else "Zapnout") { _, _ ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        withContext(Dispatchers.IO) {
                            if (active) cz.uhk.macroflow.demo.DemoData.disable(ctx) else cz.uhk.macroflow.demo.DemoData.enable(ctx)
                        }
                        Toast.makeText(ctx, if (active) "Ukázková data smazána." else "Ukázková data vložena.", Toast.LENGTH_SHORT).show()
                    }
                }.show()
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

    // ── Hodinky (Garmin) ────────────────────────────────────────────────────

    private fun buildWearable(box: LinearLayout) {
        val ctx = requireContext()
        val status = TextView(ctx).apply { textSize = 14f }
        val setupBtn = com.google.android.material.button.MaterialButton(ctx)
        val syncBtn = com.google.android.material.button.MaterialButton(ctx).apply { text = "Synchronizovat hodinky" }
        val keyBtn = com.google.android.material.button.MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
        fun refresh() {
            status.text = cz.uhk.macroflow.wearable.WearableHub.describe(ctx)
            syncBtn.isEnabled = cz.uhk.macroflow.wearable.WearableHub.hasAny(ctx)
            setupBtn.text = if (cz.uhk.macroflow.wearable.HealthConnectSource.isEnabled(ctx)) "Změnit hodinky" else "Připojit hodinky"
            keyBtn.text = if (cz.uhk.macroflow.wearable.GarminKeyStore.hasKey(ctx)) "Změnit / smazat Garmin klíč (pro vývojáře)" else "Garmin klíč (pro vývojáře)"
        }
        setupBtn.setOnClickListener { cz.uhk.macroflow.wearable.WatchSetupSheet.show(childFragmentManager) }
        childFragmentManager.setFragmentResultListener("watch_setup", viewLifecycleOwner) { _, _ -> refresh() }
        keyBtn.setOnClickListener {
            val input = TextInputEditText(ctx).apply { hint = "Klíč (prázdný = smazat)"; setSingleLine() }
            MaterialAlertDialogBuilder(ctx).setTitle("Garmin Personal API klíč").setView(input)
                .setMessage("Osobní klíč z connect.garmin.com → Nastavení → Personal API. Slouží jen k tvým vlastním datům, ukládá se šifrovaně v telefonu.")
                .setPositiveButton("Uložit") { _, _ -> cz.uhk.macroflow.wearable.GarminKeyStore.save(ctx, input.text?.toString().orEmpty()); refresh() }
                .setNegativeButton("Zrušit", null).show()
        }
        syncBtn.setOnClickListener {
            syncBtn.isEnabled = false
            viewLifecycleOwner.lifecycleScope.launch {
                val msg = withContext(Dispatchers.IO) {
                    runCatching { cz.uhk.macroflow.wearable.WearableHub.run(ctx) }
                        .fold({ "Načteno dní: $it" }, { "Synchronizace selhala: ${it.message}" })
                }
                Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
                refresh()
            }
        }
        box.addView(status); box.addView(setupBtn); box.addView(syncBtn); box.addView(keyBtn)
        refresh()
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
