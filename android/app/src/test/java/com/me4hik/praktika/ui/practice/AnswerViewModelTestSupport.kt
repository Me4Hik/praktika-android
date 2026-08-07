// 05.08.2026 Answer Save cursor by Me4Hik START - shared fakes for AnswerViewModel tests
package com.me4hik.praktika.ui.practice

import com.me4hik.praktika.data.cycle.CycleAnswerNotAllowedException
import com.me4hik.praktika.data.cycle.CycleAnswerNotAllowedReason
import com.me4hik.praktika.data.cycle.CycleResult
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.data.read.AnswerOccurrenceReadModel
import com.me4hik.praktika.data.read.AnswerReadRepository
import com.me4hik.praktika.data.read.AnswerReadResult
import com.me4hik.praktika.data.read.AnswerReadSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow

internal object AnswerViewModelTestSupport {
    const val OCCURRENCE_ID = 10L

    fun availableSnapshot(
        isCurrent: Boolean = true,
        isPaused: Boolean = false,
        hasExistingAnswer: Boolean = false,
    ): AnswerReadSnapshot {
        return AnswerReadSnapshot(
            occurrence = AnswerOccurrenceReadModel(
                occurrenceId = OCCURRENCE_ID,
                questionId = 1,
                questionTextSnapshot = "Question 1 text",
                cyclePosition = 1,
                status = QuestionOccurrenceStatus.AVAILABLE,
            ),
            isPaused = isPaused,
            isCurrent = isCurrent,
            hasExistingAnswer = hasExistingAnswer,
        )
    }

    fun scheduledSnapshot(isCurrent: Boolean = true): AnswerReadSnapshot {
        return AnswerReadSnapshot(
            occurrence = AnswerOccurrenceReadModel(
                occurrenceId = OCCURRENCE_ID,
                questionId = 1,
                questionTextSnapshot = "Question 1 text",
                cyclePosition = 1,
                status = QuestionOccurrenceStatus.SCHEDULED,
            ),
            isPaused = false,
            isCurrent = isCurrent,
            hasExistingAnswer = false,
        )
    }

    fun completedSnapshot(status: QuestionOccurrenceStatus): AnswerReadSnapshot {
        return AnswerReadSnapshot(
            occurrence = AnswerOccurrenceReadModel(
                occurrenceId = OCCURRENCE_ID,
                questionId = 1,
                questionTextSnapshot = "Question 1 text",
                cyclePosition = 1,
                status = status,
            ),
            isPaused = false,
            isCurrent = false,
            hasExistingAnswer = status == QuestionOccurrenceStatus.ANSWERED,
        )
    }

    class FakeAnswerReadRepository : AnswerReadRepository {
        private val results = MutableSharedFlow<AnswerReadResult>(replay = 1, extraBufferCapacity = 1)
        private var failure: Throwable? = null
        // 07.08.2026 Stage 17 Repeat Answer History Offer cursor by Me4Hik START - prior answers fake
        var hasPriorAnswers = false
        var hasPriorAnswersException: Exception? = null
        var lastPriorCheckQuestionId: Int? = null
        // 07.08.2026 Stage 17 Repeat Answer History Offer cursor by Me4Hik END

        override fun observeSnapshot(occurrenceId: Long): Flow<AnswerReadResult> {
            return flow {
                failure?.let { throw it }
                results.collect { emit(it) }
            }
        }

        // 07.08.2026 Stage 17 Repeat Answer History Offer cursor by Me4Hik START - prior answers fake
        override suspend fun hasPriorAnswersForQuestion(questionId: Int): Boolean {
            lastPriorCheckQuestionId = questionId
            hasPriorAnswersException?.let { throw it }
            return hasPriorAnswers
        }
        // 07.08.2026 Stage 17 Repeat Answer History Offer cursor by Me4Hik END

        fun emit(result: AnswerReadResult) {
            failure = null
            results.tryEmit(result)
        }

        fun failWith(exception: Throwable) {
            failure = exception
        }
    }

    class RecordingSaveAnswerCommand : SaveAnswerCommand {
        var invocations = 0
        var lastExpectedId: Long? = null
        var lastText: String? = null
        var exception: Exception? = null

        override suspend fun save(expectedOccurrenceId: Long, answerText: String): CycleResult {
            invocations += 1
            lastExpectedId = expectedOccurrenceId
            lastText = answerText
            exception?.let { throw it }
            return CycleResult.AnswerSaved
        }
    }
}
// 05.08.2026 Answer Save cursor by Me4Hik END
