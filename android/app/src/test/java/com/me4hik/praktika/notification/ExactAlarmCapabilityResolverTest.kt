// 10.08.2026 Post-release fixes cursor by Me4Hik START - exact alarm capability resolver tests
package com.me4hik.praktika.notification

import org.junit.Assert.assertEquals
import org.junit.Test

class ExactAlarmCapabilityResolverTest {
    @Test
    fun diagnosticLabel_availableForNotRequiredAndAvailable() {
        assertEquals("available", ExactAlarmCapabilityResolver.diagnosticLabel(ExactAlarmCapability.NOT_REQUIRED))
        assertEquals("available", ExactAlarmCapabilityResolver.diagnosticLabel(ExactAlarmCapability.AVAILABLE))
    }

    @Test
    fun diagnosticLabel_requiredForSpecialAccessRequired() {
        assertEquals("required", ExactAlarmCapabilityResolver.diagnosticLabel(ExactAlarmCapability.SPECIAL_ACCESS_REQUIRED))
    }
}
