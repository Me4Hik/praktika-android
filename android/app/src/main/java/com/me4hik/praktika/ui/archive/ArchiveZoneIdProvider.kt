// 07.08.2026 Stage 15 Archive By Date cursor by Me4Hik START - ZoneId для отображения архива
package com.me4hik.praktika.ui.archive

import com.me4hik.praktika.data.cycle.TimeProvider
import java.time.ZoneId

interface ArchiveZoneIdProvider {
    fun currentZoneId(): ZoneId
}

class TimeProviderArchiveZoneIdProvider(
    private val timeProvider: TimeProvider,
) : ArchiveZoneIdProvider {
    override fun currentZoneId(): ZoneId = ZoneId.of(timeProvider.currentZoneId())
}
// 07.08.2026 Stage 15 Archive By Date cursor by Me4Hik END
