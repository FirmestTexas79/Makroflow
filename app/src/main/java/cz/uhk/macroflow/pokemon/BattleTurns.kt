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

internal fun PokemonBattleView.setText(l1: String, l2: String) {
    gs.textLine1 = l1; gs.textLine2 = l2
    if (gs.phase != BattlePhase.CAUGHT && gs.phase != BattlePhase.ESCAPED &&
        gs.phase != BattlePhase.ENEMY_FAINTED && gs.phase != BattlePhase.PLAYER_FAINTED) {
        gs.phase = BattlePhase.TEXT_WAIT
    }
    invalidate()
}

internal fun PokemonBattleView.startIntro() {
    busy = true
    if (trainer != null) {
        setText(trainer.battleName, "WANTS TO BATTLE!")
        pendingAction = { say("${trainer.battleName} SENT", "OUT ${gs.enemy.name}!") { showMain() } }
    }
    else if (special != null) special.appearLines(gs.enemy.name).let { (a, b) -> setText(a, b) }
    else if (gs.isEnemyShiny) setText("*SHINY* ${gs.enemy.name}", "APPEARED!")
    else setText("WILD ${gs.enemy.name}", "APPEARED!")
    val startTime = System.currentTimeMillis()
    val duration  = 1000L

    val introLoop = object : Runnable {
        override fun run() {
            val elapsed  = System.currentTimeMillis() - startTime
            val progress = (elapsed.toFloat() / duration).coerceIn(0f, 1f)
            gs.introOffset = 1.0f - progress
            invalidate()
            if (progress < 1f) handler.postDelayed(this, 16)
            else { if (gs.isEnemyShiny) startShinyAnim() else busy = false }
        }
    }
    handler.post(introLoop)
}

internal fun PokemonBattleView.showMain()    { busy = false; gs.phase = BattlePhase.MAIN_MENU; zones.clear(); invalidate() }

internal fun PokemonBattleView.startFight()  { if (gs.player.moves.all { it.pp <= 0 }) { setText("NO PP LEFT!", ""); return }; gs.phase = BattlePhase.FIGHT_MENU; invalidate() }

internal fun PokemonBattleView.showPkmn() {
    if (party.size <= 1) { setText("ONLY ${gs.player.name}", "IN PARTY!"); return }
    partyForced = false; partyMenu = true; gs.phase = BattlePhase.MAIN_MENU; invalidate()
}

/** Výměna Makromona: dobrovolná stojí tah, po omdlení je zdarma. */
internal fun PokemonBattleView.switchTo(i: Int) {
    val m = party.getOrNull(i) ?: return
    if (i == partyIdx) { if (!partyForced) { partyMenu = false; showMain() }; return }
    if (m.mon.currentHp <= 0) { return }
    val forced = partyForced
    val old = gs.player.name
    partyMenu = false; partyForced = false
    partyIdx = i
    gs.player = m.mon
    gs.isPlayerShiny = m.shiny
    playerCond = m.cond
    loadMakromonSprite(m.mon, isPlayer = true)
    busy = true
    if (forced) say("GO ${m.mon.name}!", "") { showMain() }
    else say("COME BACK $old!", "GO ${m.mon.name}!") { enemyTurn() }
}

internal fun PokemonBattleView.startItem()   { itemPage = 0; gs.phase = BattlePhase.ITEM_MENU; zones.clear(); invalidate() }

internal fun PokemonBattleView.doRun() {
    special?.let { sp -> val (a, b) = sp.noRunLines; say(a, b) { showMain() }; return }
    if (trainer != null) { say("NO RUNNING FROM", "A TRAINER BATTLE!") { showMain() }; return }
    busy = true
    // Paralýza půlí rychlost i při útěku
    val speed = (gs.player.speed * cz.uhk.macroflow.pokemon.status.StatusRules.speedMultiplier(playerCond)).toInt()
    if (BattleEngine.tryEscape(speed, gs.enemy.speed)) {
        gs.phase = BattlePhase.ESCAPED
        setText("GOT AWAY", "SAFELY!")
    } else {
        setText("CANT ESCAPE!", "")
        scheduleAfterText { enemyTurn() }
    }
    invalidate()
}

// ── Tahy se stavy ────────────────────────────────────────────────────────
//
// Kolo: hráč → soupeř → zranění na konci kola (otrava, popálení). Každá hláška čeká na ťuknutí.

internal fun PokemonBattleView.typeOf(m: Makromon) = m.speciesType

internal fun PokemonBattleView.say(l1: String, l2: String, then: () -> Unit) { setText(l1, l2); scheduleAfterText(then) }

internal fun PokemonBattleView.condOf(isPlayer: Boolean) = if (isPlayer) playerCond else enemyCond

internal fun PokemonBattleView.monOf(isPlayer: Boolean) = if (isPlayer) gs.player else gs.enemy

/** Zmatený Makromon zasáhne sám sebe útokem bez typu o síle 40. */
internal fun PokemonBattleView.selfHitDamage(isPlayer: Boolean): Int {
    val m = monOf(isPlayer)
    val atk = (m.attack * cz.uhk.macroflow.pokemon.status.StatusRules.attackMultiplier(condOf(isPlayer))).toInt().coerceAtLeast(1)
    return BattleEngine.calcDamage(m.level, cz.uhk.macroflow.pokemon.status.StatusRules.CONFUSION_SELF_POWER, atk, m.defense,
        MakromonType.NORMAL, MakromonType.NORMAL)
}

internal fun PokemonBattleView.playerMove(idx: Int) {
    val mv = gs.player.moves[idx]
    if (mv.pp <= 0) { setText("NO PP LEFT!", ""); return }
    busy = true; gs.phase = BattlePhase.ANIMATING
    val pre = cz.uhk.macroflow.pokemon.status.StatusRules.beforeMove(playerCond, rng) { selfHitDamage(true) }
    handleBeforeMove(true, pre, act = { useMove(true, mv) }, skip = { enemyTurn() })
}

internal fun PokemonBattleView.enemyTurn() {
    if (gs.enemy.currentHp <= 0 || gs.player.currentHp <= 0) { busy = false; showMain(); return }
    busy = true
    val mv = if (trainer != null) cz.uhk.macroflow.pokemon.trainer.TrainerAi.chooseMove(gs.enemy, gs.player, playerCond, enemyCond)
        else BattleEngine.enemyChooseMove(gs.enemy, playerCond, enemyCond)
    val pre = cz.uhk.macroflow.pokemon.status.StatusRules.beforeMove(enemyCond, rng) { selfHitDamage(false) }
    handleBeforeMove(false, pre, act = { useMove(false, mv) }, skip = { endOfRound() })
}

/** Reakce na stav před tahem: spánek, paralýza, zmatení, omráčení. */
internal fun PokemonBattleView.handleBeforeMove(isPlayer: Boolean, pre: cz.uhk.macroflow.pokemon.status.BeforeMove, act: () -> Unit, skip: () -> Unit) {
    val name = monOf(isPlayer).name
    when (pre) {
        is cz.uhk.macroflow.pokemon.status.BeforeMove.CanAct -> act()
        is cz.uhk.macroflow.pokemon.status.BeforeMove.Flinched -> say(name, "FLINCHED!", skip)
        is cz.uhk.macroflow.pokemon.status.BeforeMove.StillAsleep -> { statusFx(isPlayer, cz.uhk.macroflow.pokemon.status.EffectKind.SLEEP); say(name, "IS FAST ASLEEP.", skip) }
        is cz.uhk.macroflow.pokemon.status.BeforeMove.WokeUp -> say(name, "WOKE UP!", act)
        is cz.uhk.macroflow.pokemon.status.BeforeMove.FullyParalyzed -> { statusFx(isPlayer, cz.uhk.macroflow.pokemon.status.EffectKind.PARALYZE); say(name, "IS PARALYZED!", skip) }
        is cz.uhk.macroflow.pokemon.status.BeforeMove.SnappedOut -> say(name, "SNAPPED OUT OF IT!", act)
        is cz.uhk.macroflow.pokemon.status.BeforeMove.ConfusedButActs -> { statusFx(isPlayer, cz.uhk.macroflow.pokemon.status.EffectKind.CONFUSE); say(name, "IS CONFUSED!", act) }
        is cz.uhk.macroflow.pokemon.status.BeforeMove.HurtItself -> {
            statusFx(isPlayer, cz.uhk.macroflow.pokemon.status.EffectKind.CONFUSE)
            say(name, "IS CONFUSED!") {
                doFlash {
                    val m = monOf(isPlayer)
                    hurt(isPlayer, pre.damage); invalidate()
                    say("IT HURT ITSELF", "IN CONFUSION!") {
                        if (m.currentHp <= 0) { if (isPlayer) playerFainted() else enemyFainted() } else skip()
                    }
                }
            }
        }
    }
}

/** Provedení útoku: přesnost → zranění → vedlejší efekt (stav, snížení statistiky). */
internal fun PokemonBattleView.useMove(isPlayer: Boolean, mv: Move) {
    mv.pp = maxOf(0, mv.pp - 1)
    val atk = monOf(isPlayer); val def = monOf(!isPlayer)
    val defCond = condOf(!isPlayer)
    busy = true; gs.phase = BattlePhase.ANIMATING
    setText(atk.name, "USED ${mv.name}!"); invalidate()
    handler.postDelayed({
        if (Random.nextInt(100) >= mv.accuracy) {
            say(atk.name, "MISSED!") { afterAction(isPlayer) }; return@postDelayed
        }
        when {
            mv.power > 0 -> {
                // HEX má dvojnásobnou sílu proti cíli se stavem
                val power = if (mv.name == "HEX" && defCond.major != null) mv.power * 2 else mv.power
                // Stupně útoku a obrany (−6 … +6) + popálení půlí útok
                val atkStat = (atk.attack * cz.uhk.macroflow.pokemon.status.StatStages.multiplier(condOf(isPlayer).atkStage) *
                    cz.uhk.macroflow.pokemon.status.StatusRules.attackMultiplier(condOf(isPlayer))).toInt().coerceAtLeast(1)
                val defStat = (def.defense * cz.uhk.macroflow.pokemon.status.StatStages.multiplier(defCond.defStage)).toInt().coerceAtLeast(1)
                val dmg = BattleEngine.calcDamage(atk.level, power, atkStat, defStat, mv.type, typeOf(def))
                playMoveAnim(isPlayer, mv) {
                    hurt(!isPlayer, dmg); invalidate()
                    handler.postDelayed({
                        if (def.currentHp <= 0) { if (isPlayer) enemyFainted() else playerFainted() }
                        else {
                            // účinnost typu (docs/adr/0080): super / málo účinný / bez účinku
                            val eff = BattleEngine.getTypeEffectiveness(mv.type, typeOf(def))
                            when {
                                eff == 0f -> say("IT DOESNT AFFECT", def.name) { afterAction(isPlayer) }
                                eff > 1f -> say("SUPER EFFECTIVE!", "$dmg DAMAGE!") { applyMoveEffect(isPlayer, mv, statusOnly = false) }
                                eff < 1f -> say("NOT VERY EFFECTIVE", "$dmg DAMAGE...") { applyMoveEffect(isPlayer, mv, statusOnly = false) }
                                else -> say("IT DEALT", "$dmg DAMAGE!") { applyMoveEffect(isPlayer, mv, statusOnly = false) }
                            }
                        }
                    }, 400)
                }
            }
            else -> playMoveAnim(isPlayer, mv) { handler.postDelayed({ applyMoveEffect(isPlayer, mv, statusOnly = true) }, 350) }
        }
    }, 800)
}

internal fun PokemonBattleView.applyMoveEffect(isPlayer: Boolean, mv: Move, statusOnly: Boolean) {
    val eff = mv.fullEffect
    if (eff == null) {
        if (statusOnly) say("BUT NOTHING", "HAPPENED!") { afterAction(isPlayer) } else afterAction(isPlayer)
        return
    }
    if (eff.kind.isStatChange) { applyStatChange(isPlayer, eff, statusOnly); return }
    val def = monOf(!isPlayer)
    when (cz.uhk.macroflow.pokemon.status.StatusRules.tryInflict(condOf(!isPlayer), typeOf(def), eff, rng)) {
        cz.uhk.macroflow.pokemon.status.InflictResult.APPLIED -> {
            if (eff.kind == cz.uhk.macroflow.pokemon.status.EffectKind.FLINCH) { afterAction(isPlayer); return }
            statusFx(!isPlayer, eff.kind)
            val text = when (eff.kind) {
                cz.uhk.macroflow.pokemon.status.EffectKind.SLEEP -> "FELL ASLEEP!"
                cz.uhk.macroflow.pokemon.status.EffectKind.PARALYZE -> "IS PARALYZED!"
                cz.uhk.macroflow.pokemon.status.EffectKind.POISON -> "WAS POISONED!"
                cz.uhk.macroflow.pokemon.status.EffectKind.BURN -> "WAS BURNED!"
                else -> "BECAME CONFUSED!"
            }
            invalidate()
            say(def.name, text) { afterAction(isPlayer) }
        }
        cz.uhk.macroflow.pokemon.status.InflictResult.IMMUNE ->
            if (statusOnly) say("IT DOESNT AFFECT", def.name) { afterAction(isPlayer) } else afterAction(isPlayer)
        cz.uhk.macroflow.pokemon.status.InflictResult.ALREADY ->
            if (statusOnly) say("BUT IT", "FAILED!") { afterAction(isPlayer) } else afterAction(isPlayer)
        cz.uhk.macroflow.pokemon.status.InflictResult.FAILED_CHANCE -> afterAction(isPlayer)
    }
}

/**
 * Snížení / zvýšení útoku či obrany o stupně. RAISE_* míří na útočníka (HARDEN, DRAGON DANCE),
 * ostatní na soupeře. Na −6 / +6 už to nejde – u čistě stavového útoku to hráč uvidí.
 */
internal fun PokemonBattleView.applyStatChange(isPlayer: Boolean, eff: cz.uhk.macroflow.pokemon.status.MoveEffect, statusOnly: Boolean) {
    val onSelf = eff.kind.targetsSelf
    val targetIsPlayer = if (onSelf) isPlayer else !isPlayer
    val target = monOf(targetIsPlayer)
    val stat = if (eff.kind == cz.uhk.macroflow.pokemon.status.EffectKind.LOWER_ATK || eff.kind == cz.uhk.macroflow.pokemon.status.EffectKind.RAISE_ATK) "ATTACK" else "DEFENSE"
    val up = onSelf
    when (cz.uhk.macroflow.pokemon.status.StatusRules.tryInflict(condOf(targetIsPlayer), typeOf(target), eff, rng)) {
        cz.uhk.macroflow.pokemon.status.InflictResult.APPLIED -> {
            statFx(targetIsPlayer, up)
            val text = when {
                up && eff.stages >= 2 -> "$stat SHARPLY ROSE!"
                up -> "$stat ROSE!"
                eff.stages >= 2 -> "$stat HARSHLY FELL!"
                else -> "$stat FELL!"
            }
            invalidate()
            say(target.name, text.take(24)) { afterAction(isPlayer) }
        }
        else ->
            if (statusOnly) say(target.name, if (up) "$stat WONT GO HIGHER!" else "$stat WONT GO LOWER!") { afterAction(isPlayer) }
            else afterAction(isPlayer)
    }
}

internal fun PokemonBattleView.afterAction(isPlayer: Boolean) { if (isPlayer) enemyTurn() else endOfRound() }

/** Konec kola: omráčení vyprší, otrava a popálení zraní (nejdřív hráče, pak soupeře). */
internal fun PokemonBattleView.endOfRound() {
    playerCond.flinched = false; enemyCond.flinched = false
    residual(true) { residual(false) { showMain() } }
}

internal fun PokemonBattleView.residual(isPlayer: Boolean, next: () -> Unit) {
    val m = monOf(isPlayer); val c = condOf(isPlayer)
    val d = cz.uhk.macroflow.pokemon.status.StatusRules.endOfTurnDamage(c, m.maxHp)
    if (d == 0 || m.currentHp <= 0) { next(); return }
    hurt(isPlayer, d)
    statusFx(isPlayer, if (c.major == cz.uhk.macroflow.pokemon.status.StatusKind.POISON) cz.uhk.macroflow.pokemon.status.EffectKind.POISON else cz.uhk.macroflow.pokemon.status.EffectKind.BURN)
    invalidate()
    say(m.name, if (c.major == cz.uhk.macroflow.pokemon.status.StatusKind.POISON) "IS HURT BY POISON!" else "IS HURT BY ITS BURN!") {
        if (m.currentHp <= 0) { if (isPlayer) playerFainted() else enemyFainted() } else next()
    }
}

/** Zranění; legenda z vrcholu neklesne pod 1 HP (nedá se porazit). */
internal fun PokemonBattleView.hurt(isPlayer: Boolean, dmg: Int) {
    val m = monOf(isPlayer)
    m.currentHp = maxOf(0, m.currentHp - dmg)
    if (!isPlayer && special != null) m.currentHp = special.clampEnemyHp(m.currentHp)
}

internal fun PokemonBattleView.gamePrefs() = context.getSharedPreferences("GamePrefs", Context.MODE_PRIVATE)

internal fun PokemonBattleView.playerFainted() {
    if (special?.kind == cz.uhk.macroflow.pokemon.legend.SpecialBattle.Kind.LEGEND) {
        // Legenda hráče porazí a uletí → brána na vrcholu se otevře
        cz.uhk.macroflow.pokemon.story.StoryFlags.set(context, cz.uhk.macroflow.pokemon.legend.LegendProgress.LEGEND_KEY)
        val (roar, flee) = special.fleeLines(gs.enemy.name)
        say(gs.player.name, "FAINTED!") {
            say(roar.first, roar.second) {
                gs.enemyVisible = false; invalidate()
                gs.phase = BattlePhase.PLAYER_FAINTED
                setText(flee.first, flee.second)
                busy = false
                pendingAction = { onCaught?.invoke() }
            }
        }
        return
    }
    if (trainer != null && party.none { it.mon.currentHp > 0 }) { trainerWon(); return }
    if (party.any { it.mon.currentHp > 0 }) {
        // Další člen týmu nastoupí
        say("${gs.player.name}", "FAINTED!") {
            partyForced = true; partyMenu = true; busy = false
            gs.phase = BattlePhase.MAIN_MENU; invalidate()
        }
        return
    }
    gs.phase = BattlePhase.PLAYER_FAINTED
    setText("${gs.player.name}", "FAINTED!")
    busy = false
    // Padl celý tým → po zavření souboje mapa přehraje smrt postavy (docs/adr/0052)
    context.getSharedPreferences("GamePrefs", android.content.Context.MODE_PRIVATE).edit()
        .putBoolean(cz.uhk.macroflow.pokemon.zone.Whiteout.PENDING_KEY, true).apply()
    pendingAction = { onCaught?.invoke() }
}

// ── Souboj s trenérem (docs/adr/0076) ──

/** Lék z lékárničky: bez účinku se nespotřebuje a tah nepropadne; jinak stojí tah. */
internal fun PokemonBattleView.useMed(m: cz.uhk.macroflow.pokemon.status.MedItem) {
    if (!m.helps(playerCond)) {
        say("IT WONT HAVE", "ANY EFFECT.") { itemPage = 2; gs.phase = BattlePhase.ITEM_MENU; busy = false; invalidate() }
        return
    }
    busy = true
    medCounts[m] = ((medCounts[m] ?: 1) - 1).coerceAtLeast(0)
    Thread { db.userItemDao().consumeItem(m.id, 1) }.start()
    m.apply(playerCond)
    statusFx(true, null)
    val cure = when (m) {
        cz.uhk.macroflow.pokemon.status.MedItem.CAFFEINE -> "WOKE UP!"
        cz.uhk.macroflow.pokemon.status.MedItem.ELECTROLYTE -> "CAN MOVE AGAIN!"
        cz.uhk.macroflow.pokemon.status.MedItem.CHARCOAL -> "POISON IS GONE!"
        cz.uhk.macroflow.pokemon.status.MedItem.ALOE -> "BURN IS HEALED!"
        cz.uhk.macroflow.pokemon.status.MedItem.COLD_SHOWER -> "SNAPPED OUT OF IT!"
        cz.uhk.macroflow.pokemon.status.MedItem.MULTIVITAMIN -> "IS FULLY CURED!"
    }
    invalidate()
    say("USED ${m.short}!", "${gs.player.name} ${cure}".take(24)) { enemyTurn() }
}

internal fun PokemonBattleView.scheduleAfterText(action: () -> Unit) {
    busy = false; gs.phase = BattlePhase.TEXT_WAIT; pendingAction = action; invalidate()
}

internal fun PokemonBattleView.advText() {
    val action = pendingAction
    pendingAction = null
    when (gs.phase) {
        BattlePhase.ESCAPED, BattlePhase.CAUGHT,
        BattlePhase.ENEMY_FAINTED, BattlePhase.PLAYER_FAINTED -> onCaught?.invoke()
        BattlePhase.TEXT_WAIT -> if (action != null) action() else showMain()
        else -> if (action != null) action() else showMain()
    }
}

// ── Hod Makroballem ──────────────────────────────────────────────────────
