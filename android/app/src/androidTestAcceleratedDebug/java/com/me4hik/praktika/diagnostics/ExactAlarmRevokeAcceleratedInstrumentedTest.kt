// 10.08.2026 Post-release fixes cursor by Me4Hik START - Prompt127 exact alarm revoke (isolated runs)
package com.me4hik.praktika.diagnostics

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.me4hik.praktika.MainActivity
import com.me4hik.praktika.MainActivityInitGate
import androidx.lifecycle.Lifecycle
import com.me4hik.praktika.notification.NotificationSyncReason
import com.me4hik.praktika.runtime.PraktikaRuntimeHolder
import com.me4hik.praktika.ui.practice.PracticeComposeTestSupport
import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

/**
 * Revoke proof uses two separate instrumentation invocations because appops deny
 * kills the in-process host on Android 15.
 *
 * Phase 1: grant + schedule exact, persist seq/occurrence.
 * Shell: appops set <pkg> SCHEDULE_EXACT_ALARM deny
 * Phase 2: verify fallback reschedule without crash.
 */
@RunWith(AndroidJUnit4::class)
class ExactAlarmRevokeAcceleratedInstrumentedTest {
    private val sandboxReset = org.junit.rules.TestRule { base, description ->
        object : Statement() {
            override fun evaluate() {
                if (description.methodName == "phase2_fallbackAfterExternalRevoke") {
                    ExactAlarmDeliveryExperimentSupport.resetRuntimeOnly()
                } else {
                    ExactAlarmDeliveryExperimentSupport.resetSandbox()
                }
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
    val ruleChain: RuleChain = RuleChain.outerRule(sandboxReset).around(composeRule)

    @Test(timeout = 120_000)
    fun phase1_scheduleExactBeforeExternalRevoke() {
        ExactAlarmDeliveryExperimentSupport.grantExactAlarmAccess()
        assertTrue(ExactAlarmDeliveryExperimentSupport.canScheduleExactAlarms())
        val ctx = ExactAlarmDeliveryExperimentSupport.prepareScheduledAlarm(composeRule)
        assertEquals("setExactAndAllowWhileIdle", ctx.schedulerApi)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = AcceleratedDeviceTestHarness.prompt131Root(context)
        File(root, "revoke_phase_state.json").writeText(
            JSONObject().apply {
                put("seq_before", ctx.seqBefore)
                put("occurrence_id", ctx.occurrenceId)
                put("scheduler_api", ctx.schedulerApi)
            }.toString(2),
        )
    }

    @Test(timeout = 120_000)
    fun phase2_fallbackAfterExternalRevoke() {
        assertFalse(
            "Preflight: appops deny exact alarm before this instrumentation run",
            ExactAlarmDeliveryExperimentSupport.canScheduleExactAlarms(),
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val stateFile = File(AcceleratedDeviceTestHarness.prompt131Root(context), "revoke_phase_state.json")
        assertTrue("Run phase1 first", stateFile.exists())
        val state = JSONObject(stateFile.readText())
        val seqBefore = state.getLong("seq_before")
        runBlocking { MainActivityInitGate.awaitInit() }
        PracticeComposeTestSupport.ensureTestActivityResumed(composeRule)
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.waitForIdle()
        val runtime = runBlocking { PraktikaRuntimeHolder.get(context) }
        runBlocking {
            runtime.notificationCoordinator.sync(NotificationSyncReason.EXACT_ALARM_PERMISSION_CHANGED)
        }
        val fallback = TargetedTraceTestSupport.waitForEventAfter(
            context,
            "notification_alarm_scheduled",
            seqBefore,
            timeoutMillis = 30_000,
        ) {
            it.metadata["alarm_type"] == "planned" &&
                it.metadata["scheduler_api"] == "setAndAllowWhileIdle"
        }
        assertNotNull(fallback)
        AcceleratedDeviceTestHarness.exportPrompt131(
            "revoke_fallback_proof",
            JSONObject().apply {
                put("scheduler_api", fallback!!.metadata["scheduler_api"])
                put("exact_alarm_capability", fallback.metadata["exact_alarm_capability"])
                put("can_schedule_exact_alarms", false)
            },
        )
    }
}
