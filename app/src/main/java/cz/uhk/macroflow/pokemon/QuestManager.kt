package cz.uhk.macroflow.pokemon

import cz.uhk.macroflow.data.AppDatabase
import cz.uhk.macroflow.pokemon.quests.QuestDefinition
import cz.uhk.macroflow.pokemon.quests.QuestRegistry
import cz.uhk.macroflow.pokemon.quests.QuestStage
import cz.uhk.macroflow.pokemon.quests.RequirementType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class QuestManager(
    private val db: AppDatabase,
    private val dialogManager: QuestDialogManager,
    private val scope: CoroutineScope
) {
    private var activeQuest: QuestDefinition? = null
    private var currentProgress: QuestProgressEntity? = null
    private val progressMutex = Mutex() // Zajišťuje, že se data nepřebijí

    fun getCurrentProgress(): QuestProgressEntity? = currentProgress

    fun loadQuest(questId: String) {
        scope.launch(Dispatchers.Main) {
            progressMutex.withLock {
                activeQuest = when (questId) {
                    QuestRegistry.TOWN_INTRO_QUEST.id -> QuestRegistry.TOWN_INTRO_QUEST
                    QuestRegistry.MEADOW_QUEST.id -> QuestRegistry.MEADOW_QUEST
                    else -> null
                }

                val progress = withContext(Dispatchers.IO) {
                    db.questDao().getQuestById(questId)
                }

                if (progress == null) {
                    val newProgress = QuestProgressEntity(questId = questId)
                    currentProgress = newProgress
                    withContext(Dispatchers.IO) {
                        db.questDao().saveQuestProgress(newProgress)
                    }
                } else {
                    currentProgress = progress
                }

                // Po načtení ukážeme dialog pro aktuální stage, pokud quest není hotový
                checkNpcInteraction()
            }
        }
    }

    // --- LOGIKA PRO UDÁLOSTI ---

    fun onMealLogged() {
        incrementMetadataProgress(RequirementType.LOG_MEAL)
    }

    fun onBattleWon(type: String) {
        val stage = getActiveStage()
        if (stage?.requirementType == RequirementType.BATTLE_TYPE && stage.targetId == type) {
            incrementMetadataProgress(RequirementType.BATTLE_TYPE)
        }
    }

    fun onBarcodeScanned() {
        incrementMetadataProgress(RequirementType.SCAN_BARCODE)
    }

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

    fun checkNpcInteraction() {
        val quest = activeQuest ?: return
        val progress = currentProgress ?: return
        if (progress.isCompleted) return

        val stage = quest.stages.getOrNull(progress.currentStageIndex) ?: return

        dialogManager.showQuestDialog(
            speakerResource = stage.speakerResId,
            speakerName = "",
            stageName = stage.title,
            text = stage.text,
            totalSteps = quest.stages.size,
            currentStepIndex = progress.currentStageIndex
        )
    }

    fun onNodeVisited(nodeId: String) {
        scope.launch(Dispatchers.Main) {
            progressMutex.withLock {
                val quest = activeQuest ?: return@withLock
                val progress = currentProgress ?: return@withLock
                if (progress.isCompleted) return@withLock

                val stage = quest.stages.getOrNull(progress.currentStageIndex) ?: return@withLock

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

    fun onMakromonCaught(source: String) {
        scope.launch(Dispatchers.Main) {
            progressMutex.withLock {
                val progress = currentProgress ?: return@withLock
                val stage = getActiveStage() ?: return@withLock

                if (stage.requirementType == RequirementType.CAPTURE_SPECIFIC && stage.targetId == source) {
                    val currentCount = progress.metadata.toIntOrNull() ?: 0
                    updateProgressInternal(progress.copy(metadata = (currentCount + 1).toString()), stage)
                }
            }
        }
    }

    fun onStepsChanged(steps: Int) {
        scope.launch(Dispatchers.Main) {
            progressMutex.withLock {
                val progress = currentProgress ?: return@withLock
                val stage = getActiveStage() ?: return@withLock

                if (stage.requirementType == RequirementType.WALK_STEPS && steps >= stage.targetValue) {
                    // U kroků jen zkontrolujeme, zda jsme dosáhli cíle
                    updateProgressInternal(progress.copy(metadata = steps.toString()), stage)
                }
            }
        }
    }

    // Pozor: Tato metoda už běží uvnitř Mutexu z volajících funkcí
    private suspend fun updateProgressInternal(newProgress: QuestProgressEntity, stage: QuestStage) {
        var updated = newProgress
        val quest = activeQuest ?: return

        val isFinished = when (stage.requirementType) {
            RequirementType.VISIT_NODE -> {
                val visitedCount = updated.metadata.split(",").filter { it.isNotBlank() }.size
                visitedCount >= stage.targetValue
            }
            RequirementType.WALK_STEPS,
            RequirementType.CAPTURE_SPECIFIC,
            RequirementType.LOG_MEAL,
            RequirementType.BATTLE_TYPE,
            RequirementType.SCAN_BARCODE -> {
                val currentCount = updated.metadata.toIntOrNull() ?: 0
                currentCount >= stage.targetValue
            }
            RequirementType.TALK_TO_NPC -> true
        }

        if (isFinished) {
            val nextIndex = updated.currentStageIndex + 1
            val isAllDone = nextIndex >= quest.stages.size
            updated = updated.copy(currentStageIndex = nextIndex, isCompleted = isAllDone, metadata = "")
        }

        currentProgress = updated
        withContext(Dispatchers.IO) { db.questDao().saveQuestProgress(updated) }

        if (isFinished) {
            val nextStage = quest.stages.getOrNull(updated.currentStageIndex)
            if (nextStage != null) {
                dialogManager.showQuestDialog(
                    speakerResource = nextStage.speakerResId,
                    speakerName = "",
                    stageName = nextStage.title,
                    text = nextStage.text,
                    totalSteps = quest.stages.size,
                    currentStepIndex = updated.currentStageIndex
                )
            } else if (updated.isCompleted) {
                dialogManager.showQuestUpdate(null)
            }
        }
    }

    fun getActiveStage(): QuestStage? {
        val index = currentProgress?.currentStageIndex ?: return null
        return activeQuest?.stages?.getOrNull(index)
    }

    fun isIntroQuestFinished(): Boolean = currentProgress?.isCompleted ?: false
}