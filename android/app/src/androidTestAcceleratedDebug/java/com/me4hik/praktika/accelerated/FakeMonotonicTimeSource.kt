// 04.08.2026 Accelerated Test Mode cursor by Me4Hik START - fake monotonic source for instrumented tests
package com.me4hik.praktika.accelerated

class FakeMonotonicTimeSource(
    private var elapsedRealtimeMillis: Long,
    private var wallClockEpochMillis: Long,
    private var bootCount: Int,
    private var zoneId: String,
) : MonotonicTimeSource {
    override fun elapsedRealtimeMillis(): Long = elapsedRealtimeMillis

    override fun wallClockEpochMillis(): Long = wallClockEpochMillis

    override fun bootCount(): Int = bootCount

    override fun currentZoneId(): String = zoneId

    fun setElapsedRealtimeMillis(value: Long) {
        elapsedRealtimeMillis = value
    }

    fun setBootCount(value: Int) {
        bootCount = value
    }

    fun setZoneId(value: String) {
        zoneId = value
    }
}
// 04.08.2026 Accelerated Test Mode cursor by Me4Hik END
