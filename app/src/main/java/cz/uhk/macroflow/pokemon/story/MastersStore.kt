package cz.uhk.macroflow.pokemon.story

import android.content.Context
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.data.GameEventType
import cz.uhk.macroflow.pokemon.skills.SkillStore
import cz.uhk.macroflow.wearable.ActivityGroups
import cz.uhk.macroflow.wearable.SportGroup
import java.time.LocalDate

/** Data pro Pět Mistrů (docs/adr/0102). Čte DB – volat mimo hlavní vlákno. */
object MastersStore {
    /** Dny s tréninkem za posledních 7 dní včetně dneška (stejné pravidlo jako týdenní report). */
    fun trainingsLast7(ctx: Context, today: LocalDate = LocalDate.now()): Int {
        val db = AppDatabase.getDatabase(ctx)
        val days = (0..6).map { today.minusDays(it.toLong()).toString() }
        val from = days.last(); val to = days.first()
        val setDays = db.workoutDao().getAllSync().map { it.date }.filter { it in from..to }.toSet()
        val events = db.gameEventDao()
        val acts = db.wearableDao().getActivitiesBetweenSync(from, to)
        return days.count { d ->
            d in setDays || events.countOnDate(GameEventType.WORKOUT_DONE.name, d) > 0 ||
                events.countOnDate(GameEventType.CARDIO_DONE.name, d) > 0 ||
                acts.any { a -> a.date == d && ((ActivityGroups.of(a.typeKey) == SportGroup.STRENGTH && a.durationSec >= 20 * 60) ||
                    (ActivityGroups.of(a.typeKey) == SportGroup.CARDIO && a.durationSec >= 10 * 60)) }
        }
    }

    /** Levely hráčova týmu (aktivní + tým, jako v Aréně). */
    fun teamLevels(ctx: Context): List<Int> {
        val dao = AppDatabase.getDatabase(ctx).capturedMakromonDao()
        val ids = (listOfNotNull(SkillStore.activeId(ctx)) + SkillStore.team(ctx)).distinct()
        return ids.mapNotNull { dao.getMakromonById(it) }.take(6).map { it.level }
    }
}
