// 05.08.2026 Answer Save cursor by Me4Hik START - команда save для ViewModel
package com.me4hik.praktika.ui.practice

import com.me4hik.praktika.data.cycle.CycleResult

fun interface SaveAnswerCommand {
    suspend fun save(
        expectedOccurrenceId: Long,
        answerText: String,
    ): CycleResult
}
// 05.08.2026 Answer Save cursor by Me4Hik END
