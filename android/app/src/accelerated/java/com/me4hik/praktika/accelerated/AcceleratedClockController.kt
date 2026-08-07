// 04.08.2026 Accelerated Test Mode cursor by Me4Hik START - clock controller contract
package com.me4hik.praktika.accelerated

interface AcceleratedClockController {
    fun pauseVirtualClock()

    fun resumeVirtualClock()

    fun setSpeedMultiplier(multiplier: Int)

    fun advanceByVirtualMinutes(minutes: Long)

    fun advanceToVirtualEpochMillis(target: Long)

    fun checkpoint()

    fun currentVirtualNow(): Long

    fun currentState(): AcceleratedClockState
}
// 04.08.2026 Accelerated Test Mode cursor by Me4Hik END
