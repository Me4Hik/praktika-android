// 04.08.2026 Accelerated Test Mode cursor by Me4Hik START - monotonic time source
package com.me4hik.praktika.accelerated

interface MonotonicTimeSource {
    fun elapsedRealtimeMillis(): Long

    fun wallClockEpochMillis(): Long

    fun bootCount(): Int

    fun currentZoneId(): String
}
// 04.08.2026 Accelerated Test Mode cursor by Me4Hik END
