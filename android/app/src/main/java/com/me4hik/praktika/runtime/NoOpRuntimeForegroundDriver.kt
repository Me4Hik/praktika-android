// 04.08.2026 Accelerated Test Mode cursor by Me4Hik START - no-op foreground driver
package com.me4hik.praktika.runtime

class NoOpRuntimeForegroundDriver : RuntimeForegroundDriver {
    override suspend fun runWhileForeground() = Unit

    override suspend fun onForegroundStopped() = Unit
}
// 04.08.2026 Accelerated Test Mode cursor by Me4Hik END
