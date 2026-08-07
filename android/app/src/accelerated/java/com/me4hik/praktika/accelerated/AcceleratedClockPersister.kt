// 04.08.2026 Accelerated Test Mode cursor by Me4Hik START - clock persistence contract
package com.me4hik.praktika.accelerated

fun interface AcceleratedClockPersister {
    suspend fun save(state: AcceleratedClockState)
}
// 04.08.2026 Accelerated Test Mode cursor by Me4Hik END
