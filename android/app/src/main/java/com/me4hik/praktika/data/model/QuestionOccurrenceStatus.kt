// 04.08.2026 DB Refactoring cursor by Me4Hik START - enum статусов показа вопроса
package com.me4hik.praktika.data.model

enum class QuestionOccurrenceStatus {
    SCHEDULED,
    AVAILABLE,
    ANSWERED,
    SKIPPED_BY_USER,
    MISSED_BY_TIME,
}
// 04.08.2026 DB Refactoring cursor by Me4Hik END
