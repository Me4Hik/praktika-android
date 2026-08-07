// 04.08.2026 Accelerated Test Mode cursor by Me4Hik START - foreground driver contract
package com.me4hik.praktika.runtime

interface RuntimeForegroundDriver {
    suspend fun runWhileForeground()

    suspend fun onForegroundStopped()
}
// 04.08.2026 Accelerated Test Mode cursor by Me4Hik END
