package cz.uhk.macroflow.training.log

import android.content.Context
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.data.FirebaseRepository
import cz.uhk.macroflow.data.WorkoutSetEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Zápis deníku a šablon lokálně i do cloudu (docs/adr/0023, 0024). */
object WorkoutRepository {

    private fun dao(ctx: Context) = AppDatabase.getDatabase(ctx).workoutDao()

    /** Cviky šablony: uložené uživatelem, jinak výchozí. */
    suspend fun templateIds(ctx: Context, key: String): List<String> = withContext(Dispatchers.IO) {
        dao(ctx).templateSync(key).map { it.exerciseId }.ifEmpty { WorkoutTemplates.DEFAULTS[key].orEmpty() }
    }

    suspend fun saveTemplate(ctx: Context, key: String, ids: List<String>) = withContext(Dispatchers.IO) {
        dao(ctx).replaceTemplate(key, ids)
        runCatching { FirebaseRepository.uploadWorkoutTemplate(key, ids) }
    }

    suspend fun log(ctx: Context, set: WorkoutSetEntity) = withContext(Dispatchers.IO) {
        dao(ctx).insert(set)
        runCatching { FirebaseRepository.uploadWorkoutSet(set) }
    }

    suspend fun delete(ctx: Context, set: WorkoutSetEntity) = withContext(Dispatchers.IO) {
        dao(ctx).delete(set)
        runCatching { FirebaseRepository.deleteWorkoutSet(set.createdAt) }
    }

    /** Všechny série jako čistá data, s pořadím v rámci dne. */
    fun toLogged(sets: List<WorkoutSetEntity>): List<LoggedSet> =
        sets.groupBy { it.date }.flatMap { (_, day) -> day.sortedBy { it.createdAt }.mapIndexed { i, s -> s.toLogged(i) } }
}
