// 04.08.2026 Cycle Engine cursor by Me4Hik START - production TimeProvider
package com.me4hik.praktika.data.cycle

import java.util.TimeZone

class SystemTimeProvider : TimeProvider {
    override fun nowEpochMillis(): Long = System.currentTimeMillis()

    override fun currentZoneId(): String = TimeZone.getDefault().id
}
// 04.08.2026 Cycle Engine cursor by Me4Hik END
