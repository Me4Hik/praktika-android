// 06.08.2026 Stage 12 Notifications cursor by Me4Hik START - automated Stage 12 Blackview E2E
package com.me4hik.praktika

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.me4hik.praktika.accelerated.AcceleratedTimeStorage
import com.me4hik.praktika.runtime.PraktikaRuntimeHolder
import com.me4hik.praktika.ui.practice.OnboardingComposeTestSupport
import com.me4hik.praktika.ui.practice.PracticeComposeTestSupport
import com.me4hik.praktika.ui.practice.PracticeTestTags
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

@RunWith(AndroidJUnit4::class)
class MainActivityStage12BlackviewE2EInstrumentedTest {
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
                resetAcceleratedSandbox()
                Stage12MilestoneDiagnostics.resetReceiverDeliveryCount()
                try {
                    base.evaluate()
                } finally {
                    runCatching {
                        PracticeComposeTestSupport.ensureTestActivityResumed(composeRule)
                        composeRule.waitForIdle()
                    }
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

    @Test
    fun stage12BlackviewNotificationE2E() {
        // 06.08.2026 Stage 12 Milestone E2E cursor by Me4Hik START - opt-in only via dedicated script
        Stage12BlackviewE2ESupport.assumeMilestoneOptIn()
        // 06.08.2026 Stage 12 Milestone E2E cursor by Me4Hik END
        // 06.08.2026 Stage 12 Connected Navigation Fix cursor by Me4Hik START - UI steps outside runBlocking
        runBlocking { MainActivityInitGate.awaitInit() }
        PracticeComposeTestSupport.ensureTestActivityResumed(composeRule)
        composeRule.waitForIdle()
        PracticeComposeTestSupport.waitUntilOnboarding(composeRule)

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val runtime = runBlocking { PraktikaRuntimeHolder.get(context) }
        // 06.08.2026 Stage 12 Milestone E2E cursor by Me4Hik START - align accelerated clock to wall before AlarmManager scheduling
        Stage12MilestoneDiagnostics.prepareWallAlignedRunningClock(runtime)
        val nearSlotMinutes = Stage12MilestoneDiagnostics.nearSlotMinutesForMilestone(runtime)
        // 06.08.2026 Stage 12 Milestone E2E cursor by Me4Hik END
        OnboardingComposeTestSupport.changeSlotTime(composeRule, 1, nearSlotMinutes)
        OnboardingComposeTestSupport.startPractice(composeRule)

        composeRule.waitUntil(timeoutMillis = 20_000) {
            PracticeComposeTestSupport.hasHomeStartedContent(composeRule)
        }
        composeRule.onNodeWithTag(PracticeTestTags.HOME_NOTIFICATION_CARD).assertIsDisplayed()
        composeRule.onNodeWithTag(PracticeTestTags.HOME_NOTIFICATION_ACTION).performClick()
        Stage12BlackviewE2ESupport.tapAllowNotificationPermissionDialog()
        composeRule.waitForIdle()

        runBlocking {
            runtime.notificationCoordinator.sync(
                com.me4hik.praktika.notification.NotificationSyncReason.PERMISSION_CHANGED,
            )
            Stage12BlackviewE2ESupport.ensureChannels(runtime)

            val incompleteBeforeStop = runtime.database.questionOccurrenceDao().getIncompleteOrdered().single()
            Stage12MilestoneDiagnostics.logPreScheduleTiming(runtime, incompleteBeforeStop)

            // 06.08.2026 Stage 12 Production Defect Fix cursor by Me4Hik START - HOME instead of force-stop (force-stop kills in-process instrumentation)
            Stage12BlackviewE2ESupport.backgroundAppForAlarmDelivery(composeRule)
            // 06.08.2026 Stage 12 Production Defect Fix cursor by Me4Hik END
            Thread.sleep(1_000)

            val alarmDump = Stage12BlackviewE2ESupport.dumpsysAlarmContainsPackage()
            assertTrue(alarmDump.contains(incompleteBeforeStop.id.toString()))

            // 06.08.2026 Stage 12 Production Defect Fix cursor by Me4Hik START - relaunch after backgrounding
            Stage12BlackviewE2ESupport.bringMainActivityToForeground(composeRule)
            // 06.08.2026 Stage 12 Production Defect Fix cursor by Me4Hik END
            MainActivityInitGate.awaitInit()
            composeRule.waitForIdle()

            val delivered = Stage12MilestoneDiagnostics.waitForAvailableAndNotification(
                runtime = runtime,
                occurrenceId = incompleteBeforeStop.id,
                plannedAtEpochMillis = incompleteBeforeStop.plannedAtEpochMillis,
            )
            assertEquals(
                incompleteBeforeStop.questionTextSnapshot,
                delivered.questionTextSnapshot,
            )

            Stage10BlackviewE2ESupport.sendAcceleratedCommand("pause_practice")
            Stage10BlackviewE2ESupport.awaitAcceleratedCommand(runtime) {
                runtime.database.practiceStateDao().get()?.isPaused == true
            }
            runtime.notificationCoordinator.sync(
                com.me4hik.praktika.notification.NotificationSyncReason.MUTATION,
            )
            assertTrue(runtime.database.practiceStateDao().get()!!.isPaused)

            Stage10BlackviewE2ESupport.sendAcceleratedCommand("resume_practice")
            Stage10BlackviewE2ESupport.awaitAcceleratedCommand(runtime) {
                runtime.database.practiceStateDao().get()?.isPaused == false
            }
            assertEquals(false, runtime.database.practiceStateDao().get()!!.isPaused)

            Stage12BlackviewE2ESupport.assertQuickCheckOk(runtime)
        }
        // 06.08.2026 Stage 12 Connected Navigation Fix cursor by Me4Hik END
    }

    private fun resetAcceleratedSandbox() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        MainActivityInitGate.resetForTests()
        PraktikaRuntimeHolder.resetForTests()
        context.filesDir.listFiles()
            ?.filter { it.name == AcceleratedTimeStorage.STATE_FILE_NAME }
            ?.forEach { it.delete() }
        context.getDatabasePath(com.me4hik.praktika.runtime.RuntimeFactory.ACCELERATED_DATABASE_NAME).delete()
        // 06.08.2026 Stage 12 Milestone E2E cursor by Me4Hik START - clear stale permission flags (revoke only in script preflight; in-test revoke kills instrumentation process)
        Stage12BlackviewE2ESupport.clearNotificationPermissionFlag()
        // 06.08.2026 Stage 12 Milestone E2E cursor by Me4Hik END
    }
}
// 06.08.2026 Stage 12 Notifications cursor by Me4Hik END
