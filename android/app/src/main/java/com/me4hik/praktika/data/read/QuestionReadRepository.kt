// 05.08.2026 Question And Skip cursor by Me4Hik START - read-слой конкретного occurrence
package com.me4hik.praktika.data.read

import androidx.room.withTransaction
import com.me4hik.praktika.data.cycle.CycleCorruptionException
import com.me4hik.praktika.data.local.PraktikaDatabase
import com.me4hik.praktika.data.local.entity.PracticeStateEntity
import com.me4hik.praktika.data.local.entity.QuestionOccurrenceEntity
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest

@OptIn(ExperimentalCoroutinesApi::class)
interface QuestionReadRepository {
    fun observeOccurrence(occurrenceId: Long): Flow<QuestionOccurrenceReadResult>
}

sealed interface QuestionOccurrenceReadResult {
    data class Found(val snapshot: QuestionReadSnapshot) : QuestionOccurrenceReadResult

    data object Missing : QuestionOccurrenceReadResult
}

data class QuestionReadSnapshot(
    val occurrenceId: Long,
    val questionId: Int,
    val questionTextSnapshot: String,
    val cyclePosition: Int,
    val status: QuestionOccurrenceStatus,
    val isPaused: Boolean,
    val isCurrent: Boolean,
)

@OptIn(ExperimentalCoroutinesApi::class)
class RoomQuestionReadRepository(
    private val database: PraktikaDatabase,
) : QuestionReadRepository {

    override fun observeOccurrence(occurrenceId: Long): Flow<QuestionOccurrenceReadResult> {
        return database.invalidationTracker
            .createFlow(
                PRACTICE_STATE_TABLE,
                QUESTION_OCCURRENCES_TABLE,
                emitInitialState = true,
            )
            .mapLatest { readOccurrenceSnapshot(occurrenceId) }
            .distinctUntilChanged()
    }

    private suspend fun readOccurrenceSnapshot(occurrenceId: Long): QuestionOccurrenceReadResult {
        return database.withTransaction {
            val state = database.practiceStateDao().get()
                ?: throw CycleCorruptionException("PracticeState row id=1 is missing after seed")

            val incompleteList = database.questionOccurrenceDao().getIncompleteOrdered()
            if (incompleteList.size > 1) {
                throw CycleCorruptionException(
                    "Expected at most one incomplete occurrence, found ${incompleteList.size}",
                )
            }

            val occurrence = database.questionOccurrenceDao().getById(occurrenceId)
                ?: return@withTransaction QuestionOccurrenceReadResult.Missing

            validateStartedPracticeState(state, incompleteList, occurrence)

            val currentIncomplete = incompleteList.singleOrNull()
            QuestionOccurrenceReadResult.Found(
                QuestionReadSnapshot(
                    occurrenceId = occurrence.id,
                    questionId = occurrence.questionId,
                    questionTextSnapshot = occurrence.questionTextSnapshot,
                    cyclePosition = occurrence.cyclePosition,
                    status = occurrence.status,
                    isPaused = state.isPaused,
                    isCurrent = currentIncomplete?.id == occurrence.id,
                ),
            )
        }
    }

    private fun validateStartedPracticeState(
        state: PracticeStateEntity,
        incompleteList: List<QuestionOccurrenceEntity>,
        occurrence: QuestionOccurrenceEntity,
    ) {
        if (!state.isPracticeStarted) {
            if (incompleteList.isNotEmpty()) {
                throw CycleCorruptionException(
                    "Incomplete occurrence exists while practice is not started",
                )
            }
            return
        }
        val isIncomplete = occurrence.status == QuestionOccurrenceStatus.SCHEDULED ||
            occurrence.status == QuestionOccurrenceStatus.AVAILABLE
        if (isIncomplete && incompleteList.isEmpty()) {
            throw CycleCorruptionException(
                "Practice is started but no incomplete occurrence exists",
            )
        }
    }

    private companion object {
        const val PRACTICE_STATE_TABLE = "practice_state"
        const val QUESTION_OCCURRENCES_TABLE = "question_occurrences"
    }
}
// 05.08.2026 Question And Skip cursor by Me4Hik END
