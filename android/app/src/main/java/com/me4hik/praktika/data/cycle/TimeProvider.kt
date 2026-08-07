// 04.08.2026 Cycle Engine cursor by Me4Hik START - абстракция системного времени
package com.me4hik.praktika.data.cycle

interface TimeProvider {
    fun nowEpochMillis(): Long

    fun currentZoneId(): String
}
// 04.08.2026 Cycle Engine cursor by Me4Hik END
