package cz.uhk.macroflow.pokemon

import android.util.Log
import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.pokemon.quests.QuestDefinition
import cz.uhk.macroflow.pokemon.quests.QuestRegistry
import cz.uhk.macroflow.pokemon.quests.QuestStage
import cz.uhk.macroflow.pokemon.quests.RequirementType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class QuestManager(
    private val db: AppDatabase,
    private val dialogManager: QuestDialogManager,
    private val scope: CoroutineScope
) {
    private var activeQuest: QuestDefinition? = null
    private var currentProgress: QuestProgressEntity? = null

    var onProgressChanged: ((QuestProgressEntity) -> Unit)? = null
    private val progressMutex = Mutex()

    fun getCurrentProgress(): QuestProgressEntity? = currentProgress

    /**
     * Načte quest a okamžitě synchronizuje stav s aktuálními daty v DB (např. jídly).
     */
    fun loadQuest(questId: String) {
        scope.launch(Dispatchers.Main) {
            progressMutex.withLock {
                if (activeQuest?.id == questId && currentProgress != null) return@withLock

                activeQuest = when (questId) {
                    QuestRegistry.TOWN_INTRO_QUEST.id -> QuestRegistry.TOWN_INTRO_QUEST
                    QuestRegistry.MEADOW_QUEST.id -> QuestRegistry.MEADOW_QUEST
                    else -> null
                }

                val progress = withContext(Dispatchers.IO) {
                    db.questDao().getQuestById(questId)
                }

                if (progress == null) {
                    val newProgress = QuestProgressEntity(questId = questId, metadata = "0")
                    currentProgress = newProgress
                    withContext(Dispatchers.IO) { db.questDao().saveQuestProgress(newProgress) }
                } else {
                    currentProgress = progress
                }

                // --- SYNCHRONIZACE A AUTOMATICKÝ POSUN ---
                val stage = getActiveStage()
                if (stage?.requirementType == RequirementType.LOG_MEAL) {
                    val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                    val meals = withContext(Dispatchers.IO) {
                        db.consumedSnackDao().getConsumedByDate(today).first()
                    }

                    val dbCount = meals.size
                    // OPRAVA: Kontrolujeme >= targetValue, aby se quest posunul i při loadu,
                    // pokud je podmínka splněna, ale stage index je starý.
                    if (dbCount >= stage.targetValue || dbCount.toString() != currentProgress?.metadata) {
                        updateProgressInternal(currentProgress!!.copy(metadata = dbCount.toString()), stage, silent = true)
                    }
                }

                onProgressChanged?.invoke(currentProgress!!)
            }
        }
    }

    /**
     * Sleduje změny v databázi jídel v reálném čase.
     */
    fun startObservingMeals() {
        scope.launch(Dispatchers.IO) {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

            db.consumedSnackDao().getConsumedByDate(today).collect { meals ->
                withContext(Dispatchers.Main) {
                    syncMealWithQuest(meals.size)
                }
            }
        }
    }

    private fun syncMealWithQuest(actualCount: Int) {
        scope.launch(Dispatchers.Main) {
            progressMutex.withLock {
                val progress = currentProgress ?: return@withLock
                val stage = getActiveStage() ?: return@withLock

                if (stage.requirementType == RequirementType.LOG_MEAL) {
                    // Oprava: update vyvoláme vždy, když se stav změní nebo je dosaženo cíle
                    if (progress.metadata.toIntOrNull() != actualCount || actualCount >= stage.targetValue) {
                        updateProgressInternal(
                            progress.copy(metadata = actualCount.toString()),
                            stage
                        )
                    }
                }
            }
        }
    }

    // --- OSTATNÍ EVENTY ---

    // V QuestManager.kt uprav metodu onBattleWon:

    // Změň parametr z MakromonType na String
    // V QuestManager.kt
    fun onBattleWon(biomeOrType: String) {
        Log.d("QuestFlow", "Pokus o zápočet: $biomeOrType") // Tady uvidíš, co BattleView posílá

        scope.launch(Dispatchers.Main) {
            progressMutex.withLock {
                val stage = getActiveStage()

                // Kontrola ignoreCase zajistí, že "WATER" i "water" projde
                if (stage?.requirementType == RequirementType.BATTLE_TYPE &&
                    stage.targetId.equals(biomeOrType, ignoreCase = true)) {

                    val currentCount = currentProgress?.metadata?.toIntOrNull() ?: 0
                    val nextCount = currentCount + 1

                    Log.d("QuestFlow", "Započteno! Nový stav: $nextCount / ${stage.targetValue}")

                    updateProgressInternal(
                        currentProgress!!.copy(metadata = nextCount.toString()),
                        stage
                    )
                }
            }
        }
    }

    fun onBarcodeScanned() {
        incrementMetadataProgress(RequirementType.SCAN_BARCODE)
    }

    fun onMealLogged() {
        incrementMetadataProgress(RequirementType.LOG_MEAL)
    }

    fun onNodeVisited(nodeId: String) {
        scope.launch(Dispatchers.Main) {
            progressMutex.withLock {
                val progress = currentProgress ?: return@withLock
                val stage = getActiveStage() ?: return@withLock
                if (progress.isCompleted) return@withLock

                if (stage.requirementType == RequirementType.VISIT_NODE) {
                    val targets = stage.targetId?.split(",") ?: emptyList()
                    if (targets.contains(nodeId)) {
                        val visitedNodes = progress.metadata.split(",").filter { it.isNotBlank() }.toMutableSet()
                        if (visitedNodes.add(nodeId)) {
                            updateProgressInternal(progress.copy(metadata = visitedNodes.joinToString(",")), stage)
                        }
                    }
                }
            }
        }
    }

    fun onStepsChanged(steps: Int) {
        scope.launch(Dispatchers.Main) {
            progressMutex.withLock {
                val progress = currentProgress ?: return@withLock
                val stage = getActiveStage() ?: return@withLock

                if (stage.requirementType == RequirementType.WALK_STEPS) {
                    updateProgressInternal(progress.copy(metadata = steps.toString()), stage)
                }
            }
        }
    }

    fun onMakromonCaught(sourceId: String) {
        scope.launch(Dispatchers.Main) {
            progressMutex.withLock {
                val progress = currentProgress ?: return@withLock
                val stage = getActiveStage() ?: return@withLock

                if (stage.requirementType == RequirementType.CAPTURE_SPECIFIC && stage.targetId == sourceId) {
                    incrementMetadataProgress(RequirementType.CAPTURE_SPECIFIC)
                }
            }
        }
    }

    // --- INTERNÍ LOGIKA ---

    private fun incrementMetadataProgress(type: RequirementType) {
        scope.launch(Dispatchers.Main) {
            progressMutex.withLock {
                val progress = currentProgress ?: return@withLock
                val stage = getActiveStage() ?: return@withLock

                if (stage.requirementType == type) {
                    val currentCount = progress.metadata.toIntOrNull() ?: 0
                    updateProgressInternal(progress.copy(metadata = (currentCount + 1).toString()), stage)
                }
            }
        }
    }

    private suspend fun updateProgressInternal(newProgress: QuestProgressEntity, stage: QuestStage, silent: Boolean = false) {
        var updated = newProgress
        val quest = activeQuest ?: return

        val isFinished = when (stage.requirementType) {
            RequirementType.VISIT_NODE -> {
                val visitedCount = updated.metadata.split(",").filter { it.isNotBlank() }.size
                visitedCount >= stage.targetValue
            }
            else -> {
                val currentCount = updated.metadata.toIntOrNull() ?: 0
                currentCount >= stage.targetValue
            }
        }

        if (isFinished) {
            val nextIndex = updated.currentStageIndex + 1
            val isAllDone = nextIndex >= quest.stages.size
            updated = updated.copy(
                currentStageIndex = nextIndex,
                isCompleted = isAllDone,
                metadata = "0"
            )
        }

        currentProgress = updated
        withContext(Dispatchers.IO) {
            db.questDao().saveQuestProgress(updated)
        }

        onProgressChanged?.invoke(updated)

        if (isFinished && !silent) {
            checkNpcInteraction()
        }
    }

    fun checkNpcInteraction() {
        val quest = activeQuest ?: return
        val progress = currentProgress ?: return
        if (progress.isCompleted) return

        val stage = quest.stages.getOrNull(progress.currentStageIndex) ?: return

        dialogManager.showQuestDialog(
            speakerResource = stage.speakerResId,
            speakerName = "Gudwin",
            stageName = stage.title,
            text = stage.text,
            totalSteps = quest.stages.size,
            currentStepIndex = progress.currentStageIndex
        )
    }

    fun getActiveStage(): QuestStage? {
        val index = currentProgress?.currentStageIndex ?: return null
        return activeQuest?.stages?.getOrNull(index)
    }

    fun isIntroQuestFinished(): Boolean = currentProgress?.isCompleted ?: false
}