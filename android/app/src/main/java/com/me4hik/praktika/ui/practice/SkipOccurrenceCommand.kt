// 05.08.2026 Question And Skip cursor by Me4Hik START - команда skip для ViewModel
package com.me4hik.praktika.ui.practice

import com.me4hik.praktika.data.cycle.CycleResult

fun interface SkipOccurrenceCommand {
    suspend fun skip(expectedOccurrenceId: Long): CycleResult
}
// 05.08.2026 Question And Skip cursor by Me4Hik END
