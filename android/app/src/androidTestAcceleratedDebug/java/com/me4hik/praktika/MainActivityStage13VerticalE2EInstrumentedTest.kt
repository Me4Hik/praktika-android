// 06.08.2026 Stage 13 Vertical E2E cursor by Me4Hik START - single end-to-end user journey
package com.me4hik.praktika

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import com.me4hik.praktika.accelerated.AcceleratedTimeStorage
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.notification.NotificationSyncReason
import com.me4hik.praktika.runtime.PraktikaRuntimeHolder
import com.me4hik.praktika.runtime.RuntimeFactory
import com.me4hik.praktika.ui.practice.OnboardingComposeTestSupport
import com.me4hik.praktika.ui.practice.PracticeComposeTestSupport
import com.me4hik.praktika.ui.practice.PracticeTestTags
import com.me4hik.praktika.ui.settings.SettingsComposeTestSupport
import com.me4hik.praktika.ui.settings.SettingsTestTags
import java.io.FileInputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

@RunWith(AndroidJUnit4::class)
class MainActivityStage13VerticalE2EInstrumentedTest {
    private val answerText = """
        Сегодня я чувствую опору спокойнее.
        Стопы уверенно касаются земли.
    """.trimIndent()

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
                        composeRule.activityRule.scenario.onActivity { activity ->
                            if (!activity.isFinishing && !activity.isDestroyed) {
                                activity.finishAffinity()
                            }
                        }
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
    fun stage13VerticalE2E() {
        Stage13VerticalE2ESupport.assumeOptIn()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        runBlocking { MainActivityInitGate.awaitInit() }
        PracticeComposeTestSupport.ensureTestActivityResumed(composeRule)
        composeRule.waitForIdle()
        PracticeComposeTestSupport.waitUntilOnboarding(composeRule)
        Stage13VerticalE2ESupport.writeState(context, "onboarding_started")

        val runtime = runBlocking { PraktikaRuntimeHolder.get(context) }
        Stage12MilestoneDiagnostics.prepareWallAlignedRunningClock(runtime)
        val (slot1, slot2, slot3) = Stage13VerticalE2ESupport.scheduleSlotTriplet(
            Stage12MilestoneDiagnostics.nearSlotMinutesForMilestone(runtime),
        )
        OnboardingComposeTestSupport.changeSlotTime(composeRule, 1, slot1)
        OnboardingComposeTestSupport.changeSlotTime(composeRule, 2, slot2)
        OnboardingComposeTestSupport.changeSlotTime(composeRule, 3, slot3)
        OnboardingComposeTestSupport.startPractice(composeRule)

        composeRule.waitUntil(timeoutMillis = 20_000) {
            PracticeComposeTestSupport.hasNodeWithTag(composeRule, PracticeTestTags.HOME_POSITION)
        }
        runBlocking {
            val practiceState = runtime.database.practiceStateDao().get()!!
            assertTrue(practiceState.isPracticeStarted)
            val first = runtime.database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
            assertEquals(1, first.cyclePosition)
            assertEquals(1, runtime.database.questionOccurrenceDao().getIncompleteOrdered().size)
            Stage13VerticalE2ESupport.writeState(
                context,
                "onboarding_complete",
                mapOf(
                    "occurrenceId" to first.id,
                    "slot1" to slot1,
                    "slot2" to slot2,
                    "slot3" to slot3,
                ),
            )
        }

        composeRule.onNodeWithTag(PracticeTestTags.HOME_NOTIFICATION_ACTION).performClick()
        Stage12BlackviewE2ESupport.tapAllowNotificationPermissionDialog()
        composeRule.waitForIdle()

        val firstOccurrenceId: Long
        val firstQuestionText: String
        runBlocking {
            runtime.notificationCoordinator.sync(NotificationSyncReason.PERMISSION_CHANGED)
            Stage12BlackviewE2ESupport.ensureChannels(runtime)
            val firstOccurrence = runtime.database.questionOccurrenceDao().getIncompleteOrdered().single()
            firstOccurrenceId = firstOccurrence.id
            firstQuestionText = firstOccurrence.questionTextSnapshot
            Stage12MilestoneDiagnostics.logPreScheduleTiming(runtime, firstOccurrence)
            Stage13VerticalE2ESupport.writeState(
                context,
                "notification_prepared",
                mapOf(
                    "occurrenceId" to firstOccurrenceId,
                    "plannedAtEpochMillis" to firstOccurrence.plannedAtEpochMillis,
                ),
            )

            Stage13VerticalE2ESupport.waitForBackgroundNotificationDelivery(
                runtime = runtime,
                occurrenceId = firstOccurrenceId,
                plannedAtEpochMillis = firstOccurrence.plannedAtEpochMillis,
            )
        }

        Stage12NotificationTapProofSupport.tapNotificationInShade(device, firstQuestionText)
        Thread.sleep(2_000)
        runCatching { device.pressBack() }
        Thread.sleep(1_000)
        runCatching { PracticeComposeTestSupport.ensureTestActivityResumed(composeRule) }
        composeRule.waitForIdle()

        runBlocking {
            val tapped = runtime.database.questionOccurrenceDao().getById(firstOccurrenceId)!!
            assertEquals(firstQuestionText, tapped.questionTextSnapshot)
            assertNotNull(tapped.openedAtEpochMillis)
            Stage13VerticalE2ESupport.writeState(
                context,
                "notification_tap_complete",
                mapOf(
                    "occurrenceId" to firstOccurrenceId,
                    "openedAtEpochMillis" to tapped.openedAtEpochMillis,
                ),
            )
        }

        openAnswerScreen(device)
        enterAndSaveAnswer(device, answerText)

        val answeredOccurrenceId: Long
        runBlocking {
            answeredOccurrenceId = firstOccurrenceId
            val saved = runtime.database.questionOccurrenceDao().getById(answeredOccurrenceId)!!
            val answer = runtime.database.answerDao().getByOccurrenceId(answeredOccurrenceId)!!
            assertEquals(QuestionOccurrenceStatus.ANSWERED, saved.status)
            assertEquals(answerText, answer.text)
            assertEquals(1, runtime.database.answerDao().count())
            assertTrue(runtime.database.practiceStateDao().get()!!.nextCyclePosition >= 2)
            Stage13VerticalE2ESupport.writeState(
                context,
                "answer_saved",
                mapOf(
                    "occurrenceId" to answeredOccurrenceId,
                    "answerId" to answer.id,
                ),
            )
        }

        Stage12NotificationTapProofSupport.pressHome()
        Stage12NotificationTapProofSupport.killAppProcess()
        Thread.sleep(1_500)
        bringMainActivityToFront()
        runBlocking { MainActivityInitGate.awaitInit() }
        runCatching { PracticeComposeTestSupport.ensureTestActivityResumed(composeRule) }
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            PracticeComposeTestSupport.hasNodeWithTag(composeRule, PracticeTestTags.HOME_POSITION) ||
                !PracticeComposeTestSupport.hasNodeWithTag(composeRule, PracticeTestTags.ONBOARDING_START)
        }

        runBlocking {
            val practiceState = runtime.database.practiceStateDao().get()!!
            assertTrue(practiceState.isPracticeStarted)
            val first = runtime.database.questionOccurrenceDao().getById(answeredOccurrenceId)!!
            assertEquals(QuestionOccurrenceStatus.ANSWERED, first.status)
            assertEquals(answerText, runtime.database.answerDao().getByOccurrenceId(answeredOccurrenceId)!!.text)
            assertEquals(1, runtime.database.answerDao().count())
            assertEquals(1, runtime.database.questionOccurrenceDao().getIncompleteOrdered().size)
            Stage13VerticalE2ESupport.writeState(context, "restart_verified")
        }

        runBlocking {
            Stage10BlackviewE2ESupport.stepEvent(runtime)
            val second = runtime.database.questionOccurrenceDao().getByCycleAndPosition(1, 2)!!
            if (second.status != QuestionOccurrenceStatus.AVAILABLE) {
                Stage10BlackviewE2ESupport.stepEvent(runtime)
            }
        }
        composeRule.waitUntil(timeoutMillis = 15_000) {
            PracticeComposeTestSupport.hasNodeWithTag(composeRule, PracticeTestTags.HOME_ANSWER)
        }
        val secondOccurrenceId: Long
        runBlocking {
            val second = runtime.database.questionOccurrenceDao().getByCycleAndPosition(1, 2)!!
            secondOccurrenceId = second.id
            assertEquals(QuestionOccurrenceStatus.AVAILABLE, second.status)
        }

        PracticeComposeTestSupport.clickHomeAnswer(composeRule)
        composeRule.onNodeWithTag(PracticeTestTags.QUESTION_SKIP).performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            PracticeComposeTestSupport.hasNodeWithTag(composeRule, PracticeTestTags.HOME_POSITION)
        }

        runBlocking {
            val skipped = runtime.database.questionOccurrenceDao().getById(secondOccurrenceId)!!
            assertEquals(QuestionOccurrenceStatus.SKIPPED_BY_USER, skipped.status)
            assertNull(runtime.database.answerDao().getByOccurrenceId(secondOccurrenceId))
            assertTrue(runtime.database.practiceStateDao().get()!!.nextCyclePosition >= 3)
            assertEquals(1, runtime.database.questionOccurrenceDao().getIncompleteOrdered().size)
            Stage13VerticalE2ESupport.writeState(context, "skip_complete", mapOf("occurrenceId" to secondOccurrenceId))
        }

        val scheduleBefore = runBlocking {
            runtime.database.scheduleSlotDao().getAllOrderedByTime().map { it.timeOfDayMinutes }
        }
        PracticeComposeTestSupport.clickHomeSettings(composeRule)
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_SCREEN).assertIsDisplayed()
        SettingsComposeTestSupport.changeSlotTime(composeRule, 1, 630)
        SettingsComposeTestSupport.changeSlotTime(composeRule, 2, 860)
        SettingsComposeTestSupport.changeSlotTime(composeRule, 3, 1210)
        SettingsComposeTestSupport.saveSchedule(composeRule)
        SettingsComposeTestSupport.waitForSnackbarText(composeRule, "Расписание сохранено")
        runBlocking {
            Stage10BlackviewE2ESupport.assertSlotMinutes(runtime, 630, 860, 1210)
            val alarmDump = readAlarmDump()
            assertTrue(alarmDump.contains(context.packageName))
            assertNotEquals(scheduleBefore, runtime.database.scheduleSlotDao().getAllOrderedByTime().map { it.timeOfDayMinutes })
            Stage13VerticalE2ESupport.writeState(context, "schedule_updated")
        }

        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_PAUSE_RESUME).performClick()
        SettingsComposeTestSupport.waitForSnackbarText(composeRule, "Практика приостановлена")
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runBlocking { runtime.database.practiceStateDao().get()?.isPaused == true }
        }
        val frozenOccurrenceId = runBlocking {
            runtime.database.questionOccurrenceDao().getIncompleteOrdered().single().id
        }
        runBlocking {
            val alarmAfterPause = readAlarmDump()
            assertFalse(hasActiveNotification(context, frozenOccurrenceId))
            assertTrue(
                !alarmAfterPause.contains("PLANNED_BOUNDARY") ||
                    !alarmAfterPause.contains(context.packageName) ||
                    alarmAfterPause.contains("Canceled"),
            )
            Stage13VerticalE2ESupport.writeState(context, "pause_complete", mapOf("occurrenceId" to frozenOccurrenceId))
        }

        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_PAUSE_RESUME).performClick()
        SettingsComposeTestSupport.waitForSnackbarText(composeRule, "Практика продолжена")
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runBlocking { runtime.database.practiceStateDao().get()?.isPaused == false }
        }
        runBlocking {
            val resumed = runtime.database.questionOccurrenceDao().getIncompleteOrdered().single()
            assertEquals(frozenOccurrenceId, resumed.id)
            assertEquals(1, runtime.database.questionOccurrenceDao().getIncompleteOrdered().size)
            Stage13VerticalE2ESupport.writeState(context, "resume_complete")
        }

        pressBack()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            !PracticeComposeTestSupport.hasNodeWithTag(composeRule, SettingsTestTags.SETTINGS_SCREEN)
        }
        PracticeComposeTestSupport.waitForHomeDisplayed(composeRule)

        runBlocking {
            Stage13VerticalE2ESupport.assertFinalDbState(runtime)
            Stage13VerticalE2ESupport.writeState(context, "done")
        }
        Stage13VerticalE2ESupport.logMarker("stage13_vertical_green")
        releaseActivityForTeardown()
    }

    private fun releaseActivityForTeardown() {
        runCatching { bringMainActivityToFront() }
        runCatching { PracticeComposeTestSupport.ensureTestActivityResumed(composeRule) }
        runCatching {
            composeRule.activityRule.scenario.onActivity { activity ->
                if (!activity.isFinishing && !activity.isDestroyed) {
                    activity.finishAffinity()
                }
            }
        }
        Thread.sleep(750)
    }

    private fun bringMainActivityToFront() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val component = "${instrumentation.targetContext.packageName}/com.me4hik.praktika.MainActivity"
        instrumentation.uiAutomation
            .executeShellCommand("am start -n $component -f 0x24000000")
            .close()
        Thread.sleep(2_000)
    }

    private fun openAnswerScreen(device: UiDevice) {
        runCatching { device.pressBack() }
        Thread.sleep(500)
        if (tryOpenAnswerScreenViaCompose()) {
            return
        }
        bringMainActivityToFront()
        runBlocking { MainActivityInitGate.awaitInit() }
        if (tryOpenAnswerScreenViaCompose()) {
            return
        }
        val answerButton = device.findObject(By.text("Ответить"))
            ?: throw AssertionError("Answer button not found via UiAutomator")
        answerButton.click()
        device.waitForIdle()
        Thread.sleep(1_000)
    }

    private fun tryOpenAnswerScreenViaCompose(): Boolean {
        return runCatching {
            runCatching { PracticeComposeTestSupport.ensureTestActivityResumed(composeRule) }
            composeRule.waitUntil(timeoutMillis = 20_000) {
                PracticeComposeTestSupport.hasNodeWithTag(composeRule, PracticeTestTags.HOME_ANSWER) ||
                    PracticeComposeTestSupport.hasNodeWithTag(composeRule, PracticeTestTags.QUESTION_ANSWER) ||
                    PracticeComposeTestSupport.hasNodeWithTag(composeRule, PracticeTestTags.ANSWER_INPUT)
            }
            if (PracticeComposeTestSupport.hasNodeWithTag(composeRule, PracticeTestTags.ANSWER_INPUT)) {
                return@runCatching true
            }
            if (PracticeComposeTestSupport.hasNodeWithTag(composeRule, PracticeTestTags.HOME_ANSWER)) {
                PracticeComposeTestSupport.clickHomeAnswer(composeRule)
            } else {
                PracticeComposeTestSupport.clickQuestionAnswer(composeRule)
            }
            composeRule.waitUntil(timeoutMillis = 10_000) {
                PracticeComposeTestSupport.hasNodeWithTag(composeRule, PracticeTestTags.ANSWER_INPUT)
            }
            true
        }.getOrDefault(false)
    }

    private fun enterAndSaveAnswer(device: UiDevice, text: String) {
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 20_000) {
            PracticeComposeTestSupport.hasNodeWithTag(composeRule, PracticeTestTags.ANSWER_INPUT)
        }
        composeRule.onNodeWithTag(PracticeTestTags.ANSWER_INPUT).performTextInput(text)
        composeRule.onNodeWithTag(PracticeTestTags.ANSWER_SAVE).assertIsEnabled().performClick()
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            PracticeComposeTestSupport.hasNodeWithTag(composeRule, PracticeTestTags.HOME_POSITION)
        }
        composeRule.onNodeWithText("Ответ сохранён").assertIsDisplayed()
    }

    private fun resetAcceleratedSandbox() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        Stage13VerticalE2ESupport.resetSandbox(context)
        MainActivityInitGate.resetForTests()
        PraktikaRuntimeHolder.resetForTests()
        context.filesDir.listFiles()
            ?.filter { it.name == AcceleratedTimeStorage.STATE_FILE_NAME }
            ?.forEach { it.delete() }
        context.getDatabasePath(RuntimeFactory.ACCELERATED_DATABASE_NAME).delete()
    }

    private fun readAlarmDump(): String {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val pipe = instrumentation.uiAutomation.executeShellCommand("dumpsys alarm")
        return FileInputStream(pipe.fileDescriptor).bufferedReader().use { it.readText() }.also { pipe.close() }
    }

    private fun hasActiveNotification(context: android.content.Context, occurrenceId: Long): Boolean {
        return Stage12NotificationTapProofSupport.hasActiveNotification(context, occurrenceId)
    }
}
// 06.08.2026 Stage 13 Vertical E2E cursor by Me4Hik END
