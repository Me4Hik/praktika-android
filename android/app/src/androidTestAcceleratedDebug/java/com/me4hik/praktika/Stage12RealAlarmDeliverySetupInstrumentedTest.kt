// 06.08.2026 Stage 12 Real Alarm Proof cursor by Me4Hik START - Phase A setup without ActivityScenario wait
package com.me4hik.praktika

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Stage12RealAlarmDeliverySetupInstrumentedTest {
    @Test
    fun prepareRealOsAlarmDeliverySetup() {
        Stage12RealAlarmProofSupport.assumeOptIn()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        Stage12RealAlarmProofSupport.runSetupBlocking(context)
    }
}
// 06.08.2026 Stage 12 Real Alarm Proof cursor by Me4Hik END
