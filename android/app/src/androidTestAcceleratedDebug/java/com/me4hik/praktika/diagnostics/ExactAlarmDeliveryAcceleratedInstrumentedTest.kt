// 10.08.2026 Post-release fixes cursor by Me4Hik START - Prompt127 exact alarm accelerated tests
package com.me4hik.praktika.diagnostics

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.me4hik.praktika.MainActivity
import com.me4hik.praktika.MainActivityInitGate
import androidx.lifecycle.Lifecycle
import com.me4hik.praktika.runtime.PraktikaRuntimeHolder
import com.me4hik.praktika.ui.practice.PracticeTestTags
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.model.Statement

/**
 * Run order B → C → A in one instrumentation pass.
 * Preflight (outside instrumentation): `appops set <pkg> SCHEDULE_EXACT_ALARM deny`
 * Do not call appops deny inside this class — Android 15 kills the in-process host.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ExactAlarmDeliveryAcceleratedInstrumentedTest {
    private val sandboxReset = org.junit.rules.TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                ExactAlarmDeliveryExperimentSupport.resetSandbox()
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

    @Test(timeout = 600_000)
    fun test01_exactAccessDeniedUsesFallbackWithoutCrash() {
        assertFalse(
            "Preflight: appops deny exact alarm before instrumentation",
            ExactAlarmDeliveryExperimentSupport.canScheduleExactAlarms(),
        )
        val ctx = ExactAlarmDeliveryExperimentSupport.prepareScheduledAlarm(composeRule)
        assertEquals("setAndAllowWhileIdle", ctx.schedulerApi)
        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(PracticeTestTags.HOME_EXACT_ALARM_CARD).assertIsDisplayed()
        val firedAt = ExactAlarmDeliveryExperimentSupport.backgroundAndWaitForAlarm(
            composeRule = composeRule,
            ctx = ctx,
            waitUntilMs = ctx.triggerEpochMs + TimeUnit.MINUTES.toMillis(12),
        )
        ExactAlarmDeliveryExperimentSupport.exportResult(
            "fallback_proof",
            JSONObject().apply {
                put("scheduler_api", ctx.schedulerApi)
                put("fired_at", firedAt ?: JSONObject.NULL)
                put("delay_ms", firedAt?.minus(ctx.triggerEpochMs) ?: JSONObject.NULL)
                put("exact_alarm_card_visible", true)
            },
        )
    }

    @Test(timeout = 120_000)
    fun test02_grantWhileInstalledReschedulesExact() {
        assertFalse(ExactAlarmDeliveryExperimentSupport.canScheduleExactAlarms())
        val ctxDenied = ExactAlarmDeliveryExperimentSupport.prepareScheduledAlarm(composeRule)
        assertEquals("setAndAllowWhileIdle", ctxDenied.schedulerApi)
        ExactAlarmDeliveryExperimentSupport.grantExactAlarmAccess()
        assertTrue(ExactAlarmDeliveryExperimentSupport.canScheduleExactAlarms())
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val rescheduled = TargetedTraceTestSupport.waitForEventAfter(
            context,
            "notification_alarm_scheduled",
            ctxDenied.seqBefore,
            timeoutMillis = 30_000,
        ) {
            it.metadata["alarm_type"] == "planned" &&
                it.metadata["scheduler_api"] == "setExactAndAllowWhileIdle"
        }
        assertNotNull(rescheduled)
        ExactAlarmDeliveryExperimentSupport.exportResult(
            "grant_while_installed_proof",
            JSONObject().apply {
                put("scheduler_api", rescheduled!!.metadata["scheduler_api"])
                put("exact_alarm_capability", rescheduled.metadata["exact_alarm_capability"])
            },
        )
    }

    @Test(timeout = 600_000)
    fun test03_exactAccessGrantedBackgroundDeliveryNearTrigger() {
        ExactAlarmDeliveryExperimentSupport.grantExactAlarmAccess()
        assertTrue(ExactAlarmDeliveryExperimentSupport.canScheduleExactAlarms())
        val ctx = ExactAlarmDeliveryExperimentSupport.prepareScheduledAlarm(composeRule)
        assertEquals("setExactAndAllowWhileIdle", ctx.schedulerApi)
        val firedAt = ExactAlarmDeliveryExperimentSupport.backgroundAndWaitForAlarm(
            composeRule = composeRule,
            ctx = ctx,
            waitUntilMs = ctx.triggerEpochMs + TimeUnit.MINUTES.toMillis(3),
        )
        assertNotNull(firedAt)
        val delayMs = firedAt!! - ctx.triggerEpochMs
        assertTrue("delay_ms=$delayMs", delayMs < TimeUnit.MINUTES.toMillis(2))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(ExactAlarmDeliveryExperimentSupport.hasActiveNotification(context, ctx.occurrenceId))
        ExactAlarmDeliveryExperimentSupport.exportResult(
            "exact_granted_proof",
            JSONObject().apply {
                put("scheduler_api", ctx.schedulerApi)
                put("delay_ms", delayMs)
                put("trigger", ctx.triggerEpochMs)
                put("fired_at", firedAt)
            },
        )
    }
}
