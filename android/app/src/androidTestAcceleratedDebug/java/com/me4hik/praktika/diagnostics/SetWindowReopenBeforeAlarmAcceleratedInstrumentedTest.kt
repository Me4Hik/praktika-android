// 10.08.2026 Post-release fixes cursor by Me4Hik START - Prompt126 setWindow reopen symptom
package com.me4hik.praktika.diagnostics

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.me4hik.praktika.MainActivity
import com.me4hik.praktika.MainActivityInitGate
import com.me4hik.praktika.runtime.PraktikaRuntimeHolder
import com.me4hik.praktika.ui.practice.PracticeComposeTestSupport
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.model.Statement

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SetWindowReopenBeforeAlarmAcceleratedInstrumentedTest {
    private val devicePreflightRule = org.junit.rules.TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                PracticeComposeTestSupport.wakeTestDeviceIfNeeded()
                base.evaluate()
            }
        }
    }

    private val sandboxReset = org.junit.rules.TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                ReopenBeforeAlarmExperimentSupport.resetSandbox()
                try {
                    base.evaluate()
                } finally {
                    PraktikaRuntimeHolder.resetForTests()
                    MainActivityInitGate.resetForTests()
                }
            }
        }
    }

    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val ruleChain: RuleChain = RuleChain
        .outerRule(devicePreflightRule)
        .around(sandboxReset)
        .around(composeRule)

    @Test(timeout = 600_000)
    fun reopenAtTPlus2PostsNotificationBeforeSetWindowAlarmFires() {
        val result = ReopenBeforeAlarmExperimentSupport.runReopenExperiment(composeRule)

        assertFalse("alarm should not fire before reopen", result.alarmFiredBeforeReopen)
        assertTrue("alarm should still be pending before reopen", result.alarmStillPendingBeforeReopen)
        assertFalse("no notification before reopen", result.notificationVisibleBeforeReopen)
        assertTrue("notification after foreground reopen", result.notificationVisibleAfterReopen)
        assertTrue("real symptom reproduced", result.realSymptomReproduced)
        assertTrue("foreground reopen posts before alarm", result.foregroundReopenPostsBeforeAlarm)

        android.util.Log.i(
            ReopenBeforeAlarmExperimentSupport.TAG,
            "PASS symptom=${result.realSymptomReproduced} pending_before=${result.alarmStillPendingBeforeReopen}",
        )
    }

    @Test(timeout = 900_000)
    fun controlBackgroundOnlyWaitsForNaturalSetWindowFire() {
        val control = ReopenBeforeAlarmExperimentSupport.runBackgroundOnlyControl(composeRule)
        android.util.Log.i(
            ReopenBeforeAlarmExperimentSupport.TAG,
            "CONTROL background_only delay=${control.opt("delay_ms")} visible=${control.opt("notification_visible")}",
        )
        assertTrue("natural setWindow should fire", control.opt("fired_at") != null)
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
