// 05.08.2026 Question And Skip cursor by Me4Hik START - shared fakes for QuestionViewModel tests
package com.me4hik.praktika.ui.practice

import com.me4hik.praktika.data.cycle.CycleResult
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.data.read.QuestionOccurrenceReadResult
import com.me4hik.praktika.data.read.QuestionReadRepository
import com.me4hik.praktika.data.read.QuestionReadSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow

internal object QuestionViewModelTestSupport {
    const val OCCURRENCE_ID = 10L

    fun availableSnapshot(
        isCurrent: Boolean = true,
        isPaused: Boolean = false,
        occurrenceId: Long = OCCURRENCE_ID,
    ): QuestionReadSnapshot {
        return QuestionReadSnapshot(
            occurrenceId = occurrenceId,
            questionId = 1,
            questionTextSnapshot = "Question 1 text",
            cyclePosition = 1,
            status = QuestionOccurrenceStatus.AVAILABLE,
            isPaused = isPaused,
            isCurrent = isCurrent,
        )
    }

    fun scheduledSnapshot(isCurrent: Boolean = true): QuestionReadSnapshot {
        return QuestionReadSnapshot(
            occurrenceId = OCCURRENCE_ID,
            questionId = 1,
            questionTextSnapshot = "Question 1 text",
            cyclePosition = 1,
            status = QuestionOccurrenceStatus.SCHEDULED,
            isPaused = false,
            isCurrent = isCurrent,
        )
    }

    fun completedSnapshot(status: QuestionOccurrenceStatus): QuestionReadSnapshot {
        return QuestionReadSnapshot(
            occurrenceId = OCCURRENCE_ID,
            questionId = 1,
            questionTextSnapshot = "Question 1 text",
            cyclePosition = 1,
            status = status,
            isPaused = false,
            isCurrent = false,
        )
    }

    class FakeQuestionReadRepository : QuestionReadRepository {
        private val results = MutableSharedFlow<QuestionOccurrenceReadResult>(replay = 1, extraBufferCapacity = 1)
        private var failure: Throwable? = null

        override fun observeOccurrence(occurrenceId: Long): Flow<QuestionOccurrenceReadResult> {
            return flow {
                failure?.let { throw it }
                results.collect { emit(it) }
            }
        }

        fun emit(result: QuestionOccurrenceReadResult) {
            failure = null
            results.tryEmit(result)
        }

        fun failWith(exception: Throwable) {
            failure = exception
        }
    }

    class RecordingSkipOccurrenceCommand : SkipOccurrenceCommand {
        var invocations = 0
        var lastExpectedId: Long? = null
        var exception: Exception? = null

        override suspend fun skip(expectedOccurrenceId: Long): CycleResult {
            invocations += 1
            lastExpectedId = expectedOccurrenceId
            exception?.let { throw it }
            return CycleResult.SkipCompleted
        }
    }
}
// 05.08.2026 Question And Skip cursor by Me4Hik END
