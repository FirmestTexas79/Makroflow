package cz.uhk.macroflow.pokemon

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import cz.uhk.macroflow.common.MainActivity
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.data.FirebaseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.*
import kotlin.random.Random
import cz.uhk.macroflow.pokemon.PokemonBattleView.*

// Část souboje PokemonBattleView (docs/adr/0080) – rozšiřující funkce nad stavem pohledu.

/** Padl celý hráčův tým: trenér vyhrál. Žádná smrt postavy – v aréně se jen prohrává. */
internal fun PokemonBattleView.trainerWon() {
    val t = trainer ?: return
    trainerFinished = true
    Thread { cz.uhk.macroflow.pokemon.trainer.Arena.recordResult(context, t, won = false) }.start()
    val rank = if (t.ranked) rankedOutcome(won = false) else null
    say(gs.player.name, "FAINTED!") {
        val end = {
            gs.phase = BattlePhase.PLAYER_FAINTED
            setText(t.battleName, "WON THE BATTLE!")
            busy = false
            pendingAction = { onCaught?.invoke() }
        }
        if (rank == null) end() else sayChain(rankLines(rank)) { end() }
    }
}

/** Padl Makromon trenéra: nastoupí další, nebo hráč vyhrál. */
internal fun PokemonBattleView.trainerMonFainted() {
    val t = trainer ?: return
    val fallen = gs.enemy
    awardXpToActiveMakromon(10 + fallen.level * 3)
    val next = (enemyIdx + 1 until enemyTeam.size).firstOrNull { enemyTeam[it].first.currentHp > 0 }
    if (next != null) {
        gs.enemyVisible = false; invalidate()
        say(fallen.name, "FAINTED!") {
            enemyIdx = next
            val (mon, shiny) = enemyTeam[next]
            gs.enemy = mon
            gs.isEnemyShiny = shiny
            enemyCond = cz.uhk.macroflow.pokemon.status.Condition()
            loadMakromonSprite(mon, isPlayer = false)
            gs.enemyVisible = true
            say("${t.battleName} SENT", "OUT ${mon.name}!") { showMain() }
        }
        return
    }
    busy = false
    cz.uhk.macroflow.pokemon.audio.GameAudio.sfx(context, cz.uhk.macroflow.pokemon.audio.GameAudio.Sfx.VICTORY)
    gs.enemyVisible = false
    gs.phase = BattlePhase.ENEMY_FAINTED
    setText(fallen.name, "FAINTED!")
    trainerFinished = true
    val rank = if (t.ranked) rankedOutcome(won = true) else null
    Thread {
        val daily = cz.uhk.macroflow.pokemon.trainer.Arena.recordResult(context, t, won = true, pay = rank == null)
        val coins = daily + (rank?.coins ?: 0)
        if (coins > 0) db.coinDao().addCoins(coins)
        handler.post {
            val lines = mutableListOf("YOU DEFEATED" to t.battleName)
            if (rank != null) lines += rankLines(rank)
            if (coins > 0) lines += "YOU GOT" to "$coins MAKRO COINS!"
            lines.forEachIndexed { i, (a, b) -> handler.postDelayed({ setText(a, b) }, 900L + i * 1500L) }
            handler.postDelayed({ onCaught?.invoke() }, 900L + lines.size * 1500L + 400L)
        }
    }.start()
}

/** Hráč odešel z hodnoceného zápasu před koncem → počítá se jako prohra (jinak by šlo utíkat). */
internal fun PokemonBattleView.forfeitIfRanked() {
    val t = trainer ?: return
    if (trainerFinished || !t.ranked) return
    trainerFinished = true
    cz.uhk.macroflow.pokemon.trainer.Arena.recordResult(context, t, won = false)
    rankedOutcome(won = false)
}

/** Hodnocený zápas (docs/adr/0079): body v telefonu hned, do cloudu na pozadí. */
internal fun PokemonBattleView.rankedOutcome(won: Boolean): cz.uhk.macroflow.pokemon.trainer.Ranked.Outcome {
    trainerFinished = true
    val o = cz.uhk.macroflow.pokemon.trainer.Ranked.record(context, won)
    if (FirebaseRepository.isLoggedIn) kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
        runCatching { FirebaseRepository.updateArenaPoints(o.result.after) }
    }
    return o
}

internal fun PokemonBattleView.rankLines(o: cz.uhk.macroflow.pokemon.trainer.Ranked.Outcome): List<Pair<String, String>> {
    val R = cz.uhk.macroflow.pokemon.trainer.Ranked
    val r = o.result
    val tier = R.tierOf(r.after)
    val sign = if (r.delta >= 0) "+" else ""
    val out = mutableListOf("RANKED $sign${r.delta} PTS" to "${tier.ascii} ${r.after} PTS")
    if (r.promoted) out += "PROMOTED TO" to "${tier.ascii} RANK!"
    if (r.demoted) out += "DROPPED TO" to "${tier.ascii} RANK"
    return out
}

internal fun PokemonBattleView.enemyFainted() {
    if (trainer != null) { trainerMonFainted(); return }
    busy = false
    cz.uhk.macroflow.pokemon.audio.GameAudio.sfx(context, cz.uhk.macroflow.pokemon.audio.GameAudio.Sfx.VICTORY)
    // Poražený strážce jeskyně uvolní svůj krystal
    special?.crystal?.let { c ->
        cz.uhk.macroflow.pokemon.story.StoryFlags.set(context, cz.uhk.macroflow.pokemon.legend.LegendProgress.bossKey(c))
    }
    special?.winKey?.let { cz.uhk.macroflow.pokemon.story.StoryFlags.set(context, it) }
    gs.enemyVisible = false
    gs.phase = BattlePhase.ENEMY_FAINTED
    setText("${gs.enemy.name}", "FAINTED!")

    // --- QUEST SYSTÉM: OZNÁMENÍ VÝHRY ---
    // Zjistíme typ nepřítele (bereme typ prvního útoku, jak to máš v dmg výpočtu)
    val enemyType = gs.enemy.speciesType
    val coins = if (special == null) cz.uhk.macroflow.pokemon.wild.BattleRewards.coinsForWin() else 0

    // Informujeme QuestManager o výhře nad konkrétním typem
    (context as? MakromonMapActivity)?.let { map ->
        map.questManager.onBattleWon(enemyType.name, biome = map.getCurrentBiome().wildBiome.name,
            location = map.getCurrentBiome().name)
        cz.uhk.macroflow.pokemon.daily.DailyQuestStore.recordWin(context, enemyType.name, map.getCurrentBiome().name)
    }

    Thread {
        // Logika pro XP a Makrodex
        val mId = BattleFactory.makrodexId(gs.enemy)
        val spawnEntry = SpawnManager.allEntries.find { it.id == mId }
        val rarity = spawnEntry?.rarity ?: Rarity.COMMON

        val baseScore = when (rarity) {
            Rarity.COMMON    -> 15
            Rarity.RARE      -> 30
            Rarity.EPIC      -> 60
            Rarity.LEGENDARY -> 120
            Rarity.MYTHIC    -> 250
        }
        val totalBattleXp = baseScore + (gs.enemy.level * 3)
        awardXpToActiveMakromon(totalBattleXp)
        if (coins > 0) db.coinDao().addCoins(coins)
        // Kořist (docs/adr/0034): fragment energie, z travních i semínko
        val drops = if (special == null) grantDrops(caught = false) else emptyList()

        handler.post {
            // Makro penízky za výhru (1–5) a kořist postupně, pak zpět na mapu
            val lines = mutableListOf<Pair<String, String>>()
            if (coins > 0) lines += "YOU GOT" to (if (coins == 1) "1 MAKRO COIN!" else "$coins MAKRO COINS!")
            lines += dropLines(drops)
            lines.forEachIndexed { i, (a, b) -> handler.postDelayed({ setText(a, b) }, 900L + i * 1500L) }
            handler.postDelayed({
                onCaught?.invoke()
            }, if (lines.isEmpty()) 2000L else 900L + lines.size * 1500L + 400L)
        }
    }.start()
}

// ── Efekty stavů ─────────────────────────────────────────────────────────
//
// Částice žijí v souřadnicích herního plátna relativně ke středu Makromona a kreslí se
// v rozlišení displeje. Dokud stav trvá, občas se krátce zopakují (Zzz, jiskry, bublinky…).

internal fun PokemonBattleView.grantDrops(caught: Boolean): List<cz.uhk.macroflow.pokemon.skills.Drops.Drop> {
    val SS = cz.uhk.macroflow.pokemon.skills.SkillStore
    val dropRate = runCatching { SS.state(context).dropRate }.getOrDefault(1.0)
    val drops = cz.uhk.macroflow.pokemon.skills.Drops.roll(gs.enemy.name, gs.enemy.level, caught, dropRate = dropRate)
        // artefakty z Gudwina jen jednou
        .filter { d -> cz.uhk.macroflow.pokemon.skills.Gear.from(d.itemId) == null || runCatching { SS.count(context, d.itemId) == 0 }.getOrDefault(false) }
    drops.forEach { runCatching { SS.add(context, it.itemId, it.amount) } }
    return drops
}

/** Název kořisti pro pixelové písmo (bez diakritiky, velkými). */
internal fun PokemonBattleView.dropLabel(id: String): String {
    val label = cz.uhk.macroflow.pokemon.skills.Resource.from(id)?.label
        ?: cz.uhk.macroflow.pokemon.skills.Gear.from(id)?.label ?: id.replace('_', ' ')
    return java.text.Normalizer.normalize(label, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").uppercase()
}

internal fun PokemonBattleView.dropLines(drops: List<cz.uhk.macroflow.pokemon.skills.Drops.Drop>) =
    drops.map { d ->
        val legendary = cz.uhk.macroflow.pokemon.skills.Gear.from(d.itemId)?.legendary == true
        (if (legendary) "* LEGENDARY *" else "FOUND") to ((if (d.amount > 1) "${d.amount}X " else "") + dropLabel(d.itemId) + "!")
    }

internal fun PokemonBattleView.skillLines(r: cz.uhk.macroflow.pokemon.skills.SkillStore.XpResult?): List<Pair<String, String>> {
    if (r == null || r.gained <= 0) return emptyList()
    val name = r.skill.name
    val out = mutableListOf("$name SKILL" to "GAINED ${r.gained} XP!")
    if (r.leveledUp) out += "$name LV ${r.newLevel}!" to (if (r.newPoints > 0) "GOT A SKILL POINT!" else "")
    return out
}

/** Postupně ukáže hlášky (každá čeká na ťuknutí), pak [end]. */
internal fun PokemonBattleView.sayChain(lines: List<Pair<String, String>>, end: () -> Unit) {
    if (lines.isEmpty()) { end(); return }
    gs.phase = BattlePhase.TEXT_WAIT
    val (a, b) = lines.first()
    setText(a, b)
    busy = false
    pendingAction = { sayChain(lines.drop(1), end) }
}

internal fun PokemonBattleView.caught() {
    gs.phase = BattlePhase.ANIMATING
    busy = true
    cz.uhk.macroflow.pokemon.audio.GameAudio.sfx(context, cz.uhk.macroflow.pokemon.audio.GameAudio.Sfx.CATCH)
    tween(650, { p -> clickStars = p }) { clickStars = -1f }

    val mId = BattleFactory.makrodexId(gs.enemy)

    // Chycený Makromon si nese level i útoky z divočiny (docs/adr/0029)
    val entity = CapturedMakromonEntity(
        makromonId = mId,
        name       = gs.enemy.name,
        level      = gs.enemy.level,
        xp         = PokemonLevelCalc.xpForLevel(gs.enemy.level),
        moveListStr = cz.uhk.macroflow.pokemon.wild.MovePool.namesOf(gs.enemy.moves),
        isShiny    = gs.isEnemyShiny,
        caughtDate = System.currentTimeMillis()
    )

    Thread {
        db.capturedMakromonDao().insertMakromon(entity)
        cz.uhk.macroflow.pokemon.daily.DailyQuestStore.recordCatch(context)
        runCatching { cz.uhk.macroflow.pokemon.skills.AwardStore.recordCatch(context, mId, gs.isEnemyShiny) }

        if (FirebaseRepository.isLoggedIn) {
            kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                FirebaseRepository.uploadCapturedMakromon(entity)
                FirebaseRepository.uploadMakrodexStatus(mId)
            }
        }

        val prefs = context.getSharedPreferences("GamePrefs", Context.MODE_PRIVATE)
        val isAcquired = prefs.getBoolean("makromonAcquired", false)

        // Chytání: XP podle levelu chyceného (shiny ×2) + kořist
        val skillXp = runCatching {
            val st = cz.uhk.macroflow.pokemon.skills.SkillStore.state(context)
            val gain = st.gain(cz.uhk.macroflow.pokemon.skills.Skill.CATCHING,
                cz.uhk.macroflow.pokemon.skills.CatchRules.baseXp(gs.enemy.level),
                cz.uhk.macroflow.pokemon.skills.CatchRules.multipliers(gs.isEnemyShiny))
            cz.uhk.macroflow.pokemon.skills.SkillStore.addXp(context, cz.uhk.macroflow.pokemon.skills.Skill.CATCHING, gain)
        }.getOrNull()
        val drops = grantDrops(caught = true)

        handler.post {
            val caughtLine = (if (gs.isEnemyShiny) "CAUGHT *" else "CAUGHT ") + "${gs.enemy.name.take(7)}!" to
                (if (gs.isEnemyShiny) "SHINY!" else "")
            busy = false

            if (isAcquired) {
                // --- 🏆 XP ODMĚNA PODLE RARITY Z NOVÉHO POOLU ---
                val spawnEntry = SpawnManager.allEntries.find { it.id == mId }
                val rarity = spawnEntry?.rarity ?: Rarity.COMMON

                val xpReward = when (rarity) {
                    Rarity.COMMON    -> 20
                    Rarity.RARE      -> 50
                    Rarity.EPIC      -> 100
                    Rarity.LEGENDARY -> 250
                    Rarity.MYTHIC    -> 500
                } * (if (gs.isEnemyShiny) 2 else 1)   // shiny = dvojnásobná odměna

                awardXpToActiveMakromon(xpReward)
            }
            sayChain(listOf(caughtLine) + skillLines(skillXp) + dropLines(drops)) {
                gs.phase = BattlePhase.CAUGHT
                onCaught?.invoke()
            }
        }
    }.start()
}

/**
 * Udělí XP aktivnímu Makromonovi přímo z BattleView.
 * Funguje v jakémkoli Activity kontextu (MakromonMapActivity i MainActivity).
 */
internal fun PokemonBattleView.awardXpToActiveMakromon(xpAmount: Int) {
    val prefs = context.getSharedPreferences("GamePrefs", android.content.Context.MODE_PRIVATE)
    val activeCapturedId = party.getOrNull(partyIdx)?.capturedId?.takeIf { it > 0 }
        ?: prefs.getInt("currentOnBarCapturedId", -1)
    if (activeCapturedId == -1) return

    Thread {
        val localDb  = AppDatabase.getDatabase(context)
        val makromon = localDb.capturedMakromonDao().getMakromonById(activeCapturedId)
            ?: return@Thread

        val oldLevel = makromon.level
        val (gainedXp, gainedLevel) = PokemonLevelCalc.gain(makromon.level, makromon.xp, xpAmount)
        makromon.xp = gainedXp
        makromon.level = gainedLevel

        // 1. Uložíme do lokální DB
        localDb.capturedMakromonDao().updateMakromon(makromon)

        // 2. Synchronizace na pozadí
        if (FirebaseRepository.isLoggedIn) {
            kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                try {
                    FirebaseRepository.uploadCapturedMakromon(makromon)
                } catch (e: Exception) {
                    android.util.Log.e("XP_AWARD", "Firebase upload failed: ${e.message}")
                }
            }
        }

        // 3. Aktualizace UI na hlavním vlákně
        handler.post {
            // Herní oznámení (docs/adr/0080): XP s postupem levelu, při novém levelu ještě jedno
            val GT = cz.uhk.macroflow.pokemon.skills.ui.GameToast
            GT.show(context, cz.uhk.macroflow.pokemon.skills.ui.GameToast.Kind.XP, "+$xpAmount XP", makromon.name, PokemonLevelCalc.progressToNextLevel(makromon.xp))
            if (makromon.level > oldLevel) GT.show(context, cz.uhk.macroflow.pokemon.skills.ui.GameToast.Kind.LEVEL, "LEVEL ${makromon.level}!", "${makromon.name} zesílil")

            // --- KLÍČOVÁ ZMĚNA ---
            // Zavoláme pouze refresh lišty v MainActivity,
            // což aktualizuje texty a progress bar na spodním baru,
            // ale nepřepne fragment (nevyhodí tě na Dashboard).
            (context as? MainActivity)?.updateMakromonVisibility()
        }
    }.start()
}

// ─────────────────────────────────────────────────────────────────────────
// ANIMACE ÚTOKŮ (docs/adr/0048): částice podle MoveAnims, výpad útočníka, otřes cíle
// ─────────────────────────────────────────────────────────────────────────
