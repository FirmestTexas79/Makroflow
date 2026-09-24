package cz.uhk.macroflow.pokemon

import android.util.Log
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.data.GameEventType
import cz.uhk.macroflow.energy.Adherence
import cz.uhk.macroflow.pokemon.quests.QuestDefinition
import cz.uhk.macroflow.pokemon.quests.QuestProgression
import cz.uhk.macroflow.pokemon.quests.QuestRegistry
import cz.uhk.macroflow.pokemon.quests.QuestStage
import cz.uhk.macroflow.pokemon.quests.RequirementType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Řídí aktivní quest na mapě.
 *
 * Zdroje postupu:
 *  - **Herní akce** (návštěva uzlu, souboj, chycení) – volá mapa/souboj přímo přes on*().
 *  - **Data z funkční části** (jídla, kalorie, makra, kroky, skeny kódů) – quest je NEPOČÍTÁ sám,
 *    ale odvozuje z Room DB ([syncDerivedProgress]). Díky tomu se započte i to,
 *    co uživatel udělal, když mapa nebyla otevřená.
 *
 * Pravidla vyhodnocení jsou v [QuestProgression] (čistá logika, pokrytá testy).
 */
class QuestManager(
    private val db: AppDatabase,
    private val dialogManager: QuestDialogManager,
    private val scope: CoroutineScope,
    /** Dnešní OSOBNÍ cíle (volá se na IO vlákně); null = fáze HIT_TARGET se nevyhodnocují. */
    private val targetsProvider: (() -> Adherence.Targets)? = null,
    /** Kde si pamatujeme, které úvodní dialogy už hráč slyšel. */
    private val introPrefs: android.content.SharedPreferences? = null
) {
    private var activeQuest: QuestDefinition? = null
    private var currentProgress: QuestProgressEntity? = null

    var onProgressChanged: ((QuestProgressEntity) -> Unit)? = null
    private val progressMutex = Mutex()

    fun getCurrentProgress(): QuestProgressEntity? = currentProgress
    fun getActiveQuestId(): String? = activeQuest?.id

    fun loadQuest(questId: String) {
        scope.launch(Dispatchers.Main) {
            progressMutex.withLock {
                if (activeQuest?.id == questId && currentProgress != null) return@withLock

                val quest = QuestRegistry.byId(questId)
                if (quest == null) {
                    Log.w(TAG, "Neznámý quest '$questId'")
                    return@withLock
                }
                activeQuest = quest

                val stored = withContext(Dispatchers.IO) { db.questDao().getQuestById(questId) }
                currentProgress = stored ?: QuestProgressEntity(
                    questId = questId,
                    metadata = "0",
                    stageStartedAt = System.currentTimeMillis()
                ).also { fresh -> withContext(Dispatchers.IO) { db.questDao().saveQuestProgress(fresh) } }

                // Dohnat vše, co se stalo mimo mapu (jídla, kroky, skeny) – bez dialogu.
                syncDerivedProgressLocked(silent = true)
                currentProgress?.let { onProgressChanged?.invoke(it) }
            }
        }
    }

    /**
     * Sleduje data funkční části a průběžně přepočítává odvozené fáze.
     * Suspenduje, dokud není zrušena – volat uvnitř `repeatOnLifecycle(STARTED)`,
     * takže na pozadí nic neběží a po návratu na mapu se vše přepočítá.
     *
     * @param onStepsToday každá změna dnešních kroků (pro UI – progress bar, zámek hor)
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun observeGameData(onStepsToday: (Int) -> Unit) = coroutineScope {
        val today = todayFlow()

        launch {
            today.flatMapLatest { date -> db.stepsDao().getStepsForDateFlow(date) }
                .map { it?.count ?: 0 }
                .distinctUntilChanged()
                .collect { steps ->
                    withContext(Dispatchers.Main) { onStepsToday(steps) }
                    syncDerivedProgress()
                }
        }
        launch {
            // Celý seznam (ne jen počet) – změna gramáže/smazání mění součty maker
            today.flatMapLatest { date -> db.consumedSnackDao().getConsumedByDate(date) }
                .distinctUntilChanged()
                .collect { syncDerivedProgress() }
        }
        launch {
            db.gameEventDao().observeCount(GameEventType.BARCODE_SCANNED.name)
                .distinctUntilChanged()
                .collect { syncDerivedProgress() }
        }
    }

    // --- HERNÍ AKCE (volané z mapy / souboje) ---

    /**
     * @param enemyType typ poraženého Makromona (MakromonType.name)
     * @param biome biom, ve kterém souboj proběhl (BiomeType.name), pokud je znám
     */
    fun onBattleWon(enemyType: String, biome: String? = null) = mutate { progress, stage ->
        val counts = when (stage.requirementType) {
            RequirementType.BATTLE_TYPE -> stage.targetId.equals(enemyType, ignoreCase = true)
            RequirementType.BATTLE_BIOME -> biome != null && stage.targetId.equals(biome, ignoreCase = true)
            else -> false
        }
        if (counts) {
            val next = QuestProgression.currentValue(stage, progress.metadata) + 1
            Log.d(TAG, "Souboj započten: $next / ${stage.targetValue}")
            next.toString()
        } else null
    }

    fun onNodeVisited(nodeId: String) = mutate { progress, stage ->
        if (stage.requirementType != RequirementType.VISIT_NODE) return@mutate null
        val targets = stage.targetId?.split(",")?.map { it.trim() } ?: emptyList()
        if (nodeId !in targets) return@mutate null
        val visited = QuestProgression.visitedNodes(progress.metadata)
        if (nodeId in visited) null else (visited + nodeId).joinToString(",")
    }

    fun onMakromonCaught(sourceId: String) = mutate { progress, stage ->
        if (stage.requirementType == RequirementType.CAPTURE_SPECIFIC && stage.targetId == sourceId) {
            (QuestProgression.currentValue(stage, progress.metadata) + 1).toString()
        } else null
    }

    // --- ODVOZENÝ POSTUP (data z funkční části) ---

    private fun syncDerivedProgress() {
        scope.launch(Dispatchers.Main) {
            progressMutex.withLock { syncDerivedProgressLocked(silent = false) }
        }
    }

    /** Musí být voláno pod [progressMutex]. */
    private suspend fun syncDerivedProgressLocked(silent: Boolean) {
        val progress = currentProgress ?: return
        val stage = getActiveStage() ?: return
        if (progress.isCompleted) return

        val derived: Int = withContext(Dispatchers.IO) {
            when (stage.requirementType) {
                RequirementType.LOG_MEAL ->
                    db.consumedSnackDao().getConsumedByDateSync(today()).size
                RequirementType.WALK_STEPS ->
                    db.stepsDao().getStepsForDateSync(today())?.count ?: 0
                RequirementType.SCAN_BARCODE ->
                    db.gameEventDao().countSince(GameEventType.BARCODE_SCANNED.name, progress.stageStartedAt)
                RequirementType.HIT_TARGET -> {
                    val targets = targetsProvider?.invoke() ?: return@withContext null
                    val meals = db.consumedSnackDao().getConsumedByDateSync(today())
                    QuestProgression.targetPercent(
                        stage,
                        Adherence.Eaten(
                            kcal = meals.sumOf { it.calories.toDouble() },
                            protein = meals.sumOf { it.p.toDouble() },
                            carbs = meals.sumOf { it.s.toDouble() },
                            fat = meals.sumOf { it.t.toDouble() }
                        ),
                        targets
                    )
                }
                else -> null
            }
        } ?: return

        if (derived.toString() != progress.metadata || QuestProgression.isStageSatisfied(stage, derived.toString())) {
            commit(progress, derived.toString(), silent)
        }
    }

    // --- INTERNÍ ---

    /** Pod zámkem spočítá nová metadata (null = beze změny) a uloží je. */
    private fun mutate(block: (QuestProgressEntity, QuestStage) -> String?) {
        scope.launch(Dispatchers.Main) {
            progressMutex.withLock {
                val progress = currentProgress ?: return@withLock
                val stage = getActiveStage() ?: return@withLock
                if (progress.isCompleted) return@withLock
                val newMetadata = block(progress, stage) ?: return@withLock
                commit(progress, newMetadata, silent = false)
            }
        }
    }

    private suspend fun commit(progress: QuestProgressEntity, newMetadata: String, silent: Boolean) {
        val quest = activeQuest ?: return
        val result = QuestProgression.apply(progress, quest, newMetadata, System.currentTimeMillis())

        currentProgress = result.progress
        withContext(Dispatchers.IO) { db.questDao().saveQuestProgress(result.progress) }
        onProgressChanged?.invoke(result.progress)

        // Po splnění fáze NPC sám představí další úkol (nebo se rozloučí)
        if (result.stageCompleted && !silent) checkNpcInteraction(playerInitiated = false)
    }

    /**
     * Rozhovor s NPC:
     *  - úvod fáze se přehraje jen poprvé (nebo když ho NPC sám ohlásí po splnění předchozí),
     *  - při dalším oslovení NPC řekne jen krátkou připomínku s aktuálním postupem,
     *  - po dokončení questu se rozloučí.
     */
    fun checkNpcInteraction(playerInitiated: Boolean = true) {
        val quest = activeQuest ?: return
        val progress = currentProgress ?: return
        val lastStage = quest.stages.last()

        if (progress.isCompleted) {
            if (!playerInitiated) {
                // Právě dokončeno – rozlučka hned, bez čekání na kliknutí
                showLine(quest, lastStage, "Splněno!", quest.farewell, quest.stages.size)
            } else {
                showLine(quest, lastStage, "Hotovo", quest.farewell, quest.stages.size)
            }
            return
        }

        val stage = quest.stages.getOrNull(progress.currentStageIndex) ?: return
        val introSeen = introSeenIndex(quest.id) >= progress.currentStageIndex
        val text = if (!playerInitiated || !introSeen) stage.text
            else QuestProgression.reminder(stage, progress.metadata)
        markIntroSeen(quest.id, progress.currentStageIndex)
        showLine(quest, stage, stage.title, text, progress.currentStageIndex)
    }

    private fun showLine(quest: QuestDefinition, speaker: QuestStage, title: String, text: String, stepIndex: Int) {
        dialogManager.showQuestDialog(
            speakerResource = speaker.speakerResId,
            speakerName = speaker.speakerName,
            stageName = title,
            text = text,
            totalSteps = quest.stages.size,
            currentStepIndex = stepIndex
        )
    }

    private fun introSeenIndex(questId: String): Int = introPrefs?.getInt("intro_seen_$questId", -1) ?: -1

    private fun markIntroSeen(questId: String, index: Int) {
        if (index > introSeenIndex(questId)) introPrefs?.edit()?.putInt("intro_seen_$questId", index)?.apply()
    }

    fun getActiveStage(): QuestStage? {
        val index = currentProgress?.currentStageIndex ?: return null
        return activeQuest?.stages?.getOrNull(index)
    }

    fun isIntroQuestFinished(): Boolean = currentProgress?.isCompleted ?: false

    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    /** Aktuální den; změní se o půlnoci, takže flow jídel/kroků se přepne na nový den. */
    private fun todayFlow(): Flow<String> = flow {
        while (true) {
            emit(today())
            delay(60_000)
        }
    }.distinctUntilChanged()

    companion object {
        private const val TAG = "QuestFlow"
    }
}
