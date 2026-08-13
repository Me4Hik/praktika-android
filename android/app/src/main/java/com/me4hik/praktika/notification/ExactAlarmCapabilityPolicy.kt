// 10.08.2026 Post-release fixes cursor by Me4Hik START - exact scheduled reminder delivery
package com.me4hik.praktika.notification

import android.content.Intent
import kotlinx.coroutines.flow.StateFlow

interface ExactAlarmCapabilityPolicy {
    val capabilityStateRevision: StateFlow<Long>
    fun currentCapability(): ExactAlarmCapability
    fun notifyCapabilityChanged(source: String): Boolean
    fun seedInitialCapability(source: String)
    fun createRequestExactAlarmIntent(): Intent
    fun recordSettingsCta(source: String)
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
