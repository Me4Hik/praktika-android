// QUESTION_DEFER_FINAL_V1 — команда отложить для QuestionViewModel
package com.me4hik.praktika.ui.practice

import com.me4hik.praktika.data.cycle.CycleResult

fun interface DeferOccurrenceCommand {
    suspend fun defer(expectedOccurrenceId: Long, durationMinutes: Int): CycleResult
}
