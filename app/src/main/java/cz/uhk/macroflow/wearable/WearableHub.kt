package cz.uhk.macroflow.wearable

import android.content.Context
import kotlinx.coroutines.runBlocking
import java.time.LocalDate

/** Jediné místo, které ví, odkud hodinková data jdou (Health Connect, Garmin klíč, nebo obojí). Volat mimo hlavní vlákno. */
object WearableHub {
    private const val PREFS = "WearablePrefs"

    fun hasAny(ctx: Context) = HealthConnectSource.isEnabled(ctx) || GarminKeyStore.hasKey(ctx)

    /** Krátký popis pro Nastavení. */
    fun describe(ctx: Context): String {
        val hc = HealthConnectSource.isEnabled(ctx); val g = GarminKeyStore.hasKey(ctx)
        return when {
            hc && g -> "Hodinky jsou připojené přes Health Connect a Garmin. Garmin má přednost, protože umí i stres a Body Battery."
            hc -> "Hodinky jsou připojené přes Health Connect. Bez hodinek aplikace funguje dál po svém."
            g -> "Garmin připojen. Bez hodinek aplikace funguje dál po svém."
            else -> "Zatím bez hodinek. Aplikace funguje i tak, hodinky ji jen zpřesní."
        }
    }

    /** Stáhne data ze všech zapnutých zdrojů. Chyba Health Connect nezastaví Garmin a naopak. Vrací počet dní. */
    fun run(ctx: Context, days: Int? = null, today: LocalDate = LocalDate.now()): Int {
        var n = 0; var err: Throwable? = null
        if (HealthConnectSource.isEnabled(ctx)) runCatching { runBlocking { HealthConnectSource.sync(ctx, days, today) } }
            .onSuccess { n += it }.onFailure { err = it }
        if (GarminKeyStore.hasKey(ctx)) runCatching { WearableSync.run(ctx, days, today) }
            .onSuccess { n += it }.onFailure { err = it }
        if (n == 0) err?.let { throw it }
        return n
    }

    fun runIfStale(ctx: Context, maxAgeMs: Long = 2 * 3600_000L): Int {
        val p = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!hasAny(ctx) || System.currentTimeMillis() - p.getLong("hub_sync", 0L) < maxAgeMs) return 0
        return run(ctx).also { p.edit().putLong("hub_sync", System.currentTimeMillis()).apply() }
    }
}
