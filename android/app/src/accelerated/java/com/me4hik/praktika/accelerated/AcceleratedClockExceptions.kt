// 04.08.2026 Accelerated Test Mode cursor by Me4Hik START - исключения accelerated clock
package com.me4hik.praktika.accelerated

class AcceleratedClockParseException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

class AcceleratedClockCorruptionException(message: String) : Exception(message)

class AcceleratedClockOverflowException(message: String) : Exception(message)

class AcceleratedClockCommandException(message: String) : Exception(message)
// 04.08.2026 Accelerated Test Mode cursor by Me4Hik END
