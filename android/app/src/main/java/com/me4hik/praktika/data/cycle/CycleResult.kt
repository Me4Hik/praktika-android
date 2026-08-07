// 04.08.2026 Cycle Engine cursor by Me4Hik START - результаты операций цикла
package com.me4hik.praktika.data.cycle

sealed class CycleResult {
    data object PracticeStarted : CycleResult()

    data object ReconcileNotStarted : CycleResult()

    data object ReconcilePaused : CycleResult()

    data object ReconcileNoChanges : CycleResult()

    data object ReconcileChanged : CycleResult()

    data object SkipCompleted : CycleResult()

    data object PauseEnabled : CycleResult()

    data object PracticeResumed : CycleResult()

    // 05.08.2026 Answer Save cursor by Me4Hik START - результат сохранения ответа
    data object AnswerSaved : CycleResult()
    // 05.08.2026 Answer Save cursor by Me4Hik END
}
// 04.08.2026 Cycle Engine cursor by Me4Hik END
