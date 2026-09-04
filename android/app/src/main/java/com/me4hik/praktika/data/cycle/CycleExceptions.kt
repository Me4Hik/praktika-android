// 04.08.2026 Cycle Engine cursor by Me4Hik START - исключения цикла
package com.me4hik.praktika.data.cycle

class CycleAlreadyStartedException(message: String) : IllegalStateException(message)

class CycleNotStartedException(message: String) : IllegalStateException(message)

class CycleAlreadyPausedException(message: String) : IllegalStateException(message)

class CycleNotPausedException(message: String) : IllegalStateException(message)

class CycleCorruptionException(message: String) : IllegalStateException(message)

class CycleInvalidScheduleException(message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)

class CycleClockException(message: String) : IllegalStateException(message)

class CycleSkipNotAllowedException(message: String) : IllegalStateException(message)

class CycleDeferNotAllowedException(message: String) : IllegalStateException(message)

// 05.08.2026 Answer Save cursor by Me4Hik START - business-отказ сохранения ответа
class CycleAnswerNotAllowedException(
    val reason: CycleAnswerNotAllowedReason,
    message: String,
) : IllegalStateException(message)
// 05.08.2026 Answer Save cursor by Me4Hik END
// 04.08.2026 Cycle Engine cursor by Me4Hik END
