// 05.08.2026 Answer Save cursor by Me4Hik START - Room read snapshot для Answer
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
class RoomAnswerReadRepository(
    private val database: PraktikaDatabase,
) : AnswerReadRepository {

    // 07.08.2026 Stage 17 Repeat Answer History Offer cursor by Me4Hik START - prior answers by questionId
    override suspend fun hasPriorAnswersForQuestion(questionId: Int): Boolean {
        return database.answerDao().getAllForQuestion(questionId).isNotEmpty()
    }
    // 07.08.2026 Stage 17 Repeat Answer History Offer cursor by Me4Hik END

    override fun observeSnapshot(occurrenceId: Long): Flow<AnswerReadResult> {
        return database.invalidationTracker
            .createFlow(
                PRACTICE_STATE_TABLE,
                QUESTION_OCCURRENCES_TABLE,
                ANSWERS_TABLE,
                emitInitialState = true,
            )
            .mapLatest { readSnapshot(occurrenceId) }
            .distinctUntilChanged()
    }

    private suspend fun readSnapshot(occurrenceId: Long): AnswerReadResult {
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
                ?: return@withTransaction AnswerReadResult.Missing

            validateStartedPracticeState(state, incompleteList, occurrence)

            val existingAnswer = database.answerDao().getByOccurrenceId(occurrenceId)
            validateAnswerOccurrenceConsistency(occurrence, existingAnswer != null)

            val currentIncomplete = incompleteList.singleOrNull()
            AnswerReadResult.Found(
                AnswerReadSnapshot(
                    occurrence = occurrence.toReadModel(),
                    isPaused = state.isPaused,
                    isCurrent = currentIncomplete?.id == occurrence.id,
                    hasExistingAnswer = existingAnswer != null,
                ),
            )
        }
    }

    private fun validateAnswerOccurrenceConsistency(
        occurrence: QuestionOccurrenceEntity,
        hasExistingAnswer: Boolean,
    ) {
        if (!hasExistingAnswer) {
            return
        }
        if (occurrence.status == QuestionOccurrenceStatus.ANSWERED) {
            return
        }
        throw CycleCorruptionException(
            "Answer exists for occurrence ${occurrence.id} with status ${occurrence.status}",
        )
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

    private fun QuestionOccurrenceEntity.toReadModel(): AnswerOccurrenceReadModel {
        return AnswerOccurrenceReadModel(
            occurrenceId = id,
            questionId = questionId,
            questionTextSnapshot = questionTextSnapshot,
            cyclePosition = cyclePosition,
            status = status,
        )
    }

    private companion object {
        const val PRACTICE_STATE_TABLE = "practice_state"
        const val QUESTION_OCCURRENCES_TABLE = "question_occurrences"
        const val ANSWERS_TABLE = "answers"
    }
}
// 05.08.2026 Answer Save cursor by Me4Hik END
