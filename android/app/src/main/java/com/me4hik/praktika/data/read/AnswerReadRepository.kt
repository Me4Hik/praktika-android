// 05.08.2026 Answer Save cursor by Me4Hik START - read-слой экрана ответа
package com.me4hik.praktika.data.read

import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import kotlinx.coroutines.flow.Flow

interface AnswerReadRepository {
    fun observeSnapshot(occurrenceId: Long): Flow<AnswerReadResult>

    // 07.08.2026 Stage 17 Repeat Answer History Offer cursor by Me4Hik START - one-shot prior answers check
    suspend fun hasPriorAnswersForQuestion(questionId: Int): Boolean
    // 07.08.2026 Stage 17 Repeat Answer History Offer cursor by Me4Hik END
}

sealed interface AnswerReadResult {
    data class Found(val snapshot: AnswerReadSnapshot) : AnswerReadResult

    data object Missing : AnswerReadResult
}

data class AnswerReadSnapshot(
    val occurrence: AnswerOccurrenceReadModel?,
    val isPaused: Boolean,
    val isCurrent: Boolean,
    val hasExistingAnswer: Boolean,
)

data class AnswerOccurrenceReadModel(
    val occurrenceId: Long,
    val questionId: Int,
    val questionTextSnapshot: String,
    val cyclePosition: Int,
    val status: QuestionOccurrenceStatus,
)
// 05.08.2026 Answer Save cursor by Me4Hik END
