// 06.08.2026 Stage 10 Blackview E2E cursor by Me4Hik START - automated Stage 10 on real MainActivity
package com.me4hik.praktika

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.me4hik.praktika.accelerated.AcceleratedTimeStorage
import com.me4hik.praktika.accelerated.AcceleratedTimeProvider
import com.me4hik.praktika.data.cycle.ScheduleCalculator
import com.me4hik.praktika.data.local.entity.QuestionOccurrenceEntity
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.data.preferences.DataStoreSoundPreferenceRepository
import com.me4hik.praktika.runtime.PraktikaRuntimeHolder
import com.me4hik.praktika.runtime.RuntimeFactory
import com.me4hik.praktika.ui.practice.PracticeComposeTestSupport
import com.me4hik.praktika.ui.practice.PracticeTestTags
import com.me4hik.praktika.ui.settings.SettingsComposeTestSupport
import com.me4hik.praktika.ui.settings.SettingsTestTags
import com.me4hik.praktika.ui.settings.formatTimeOfDayMinutes
import kotlinx.coroutines.flow.first
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
class MainActivityStage10BlackviewE2EInstrumentedTest {
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

    @Test
    fun stage10BlackviewAutomatedE2E() {
        runBlocking {
            MainActivityInitGate.awaitInit()
            PracticeComposeTestSupport.ensureTestActivityResumed(composeRule)
            composeRule.waitForIdle()
            PracticeComposeTestSupport.waitUntilOnboarding(composeRule)

            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val runtime = PraktikaRuntimeHolder.get(context)

            Stage10BlackviewE2ESupport.startPractice(runtime)
            composeRule.waitUntil(timeoutMillis = 15_000) {
                PracticeComposeTestSupport.hasNodeWithTag(composeRule, PracticeTestTags.HOME_PLANNED_TIME)
            }
            Stage10BlackviewE2ESupport.assertDefaultInitialState(runtime)

            openSettings()
            assertSlotAffordance()
            assertDefaultSlotTimesOnUi()

            val scheduledBefore = Stage10BlackviewE2ESupport.incompleteOccurrence(runtime)
            val scheduledId = scheduledBefore.id
            val scheduledQuestionId = scheduledBefore.questionId
            val scheduledPosition = scheduledBefore.cyclePosition
            val scheduledPlannedAtBefore = scheduledBefore.plannedAtEpochMillis
            val cursorBefore = runtime.database.practiceStateDao().get()!!.nextCyclePosition

            changeScheduleViaUi(630, 860, 1210)
            assertUiShowsTimes(630, 860, 1210)
            Stage10BlackviewE2ESupport.assertSlotMinutes(runtime, 630, 860, 1210)

            val scheduledAfter = runtime.database.questionOccurrenceDao().getById(scheduledId)!!
            assertEquals(scheduledId, scheduledAfter.id)
            assertEquals(scheduledQuestionId, scheduledAfter.questionId)
            assertEquals(scheduledPosition, scheduledAfter.cyclePosition)
            assertEquals(QuestionOccurrenceStatus.SCHEDULED, scheduledAfter.status)
            assertNotEquals(scheduledPlannedAtBefore, scheduledAfter.plannedAtEpochMillis)
            assertNotNull(scheduledAfter.availableUntilEpochMillis)
            assertEquals(cursorBefore, runtime.database.practiceStateDao().get()!!.nextCyclePosition)
            assertEquals(1, runtime.database.questionOccurrenceDao().getIncompleteOrdered().size)

            backToHome()
            Stage10BlackviewE2ESupport.stepEvent(runtime)
            composeRule.waitUntil(timeoutMillis = 15_000) {
                PracticeComposeTestSupport.hasNodeWithTag(composeRule, PracticeTestTags.HOME_ANSWER)
            }

            val availableBefore = runtime.database.questionOccurrenceDao().getById(scheduledId)!!
            assertEquals(scheduledId, availableBefore.id)
            assertEquals(scheduledQuestionId, availableBefore.questionId)
            assertEquals(scheduledPosition, availableBefore.cyclePosition)
            assertEquals(QuestionOccurrenceStatus.AVAILABLE, availableBefore.status)
            val availablePlannedAt = availableBefore.plannedAtEpochMillis
            val availableSlotIndex = availableBefore.scheduleSlotIndex
            val availableUntilBefore = availableBefore.availableUntilEpochMillis

            openSettings()
            changeScheduleViaUi(555, 790, 1300)
            Stage10BlackviewE2ESupport.assertSlotMinutes(runtime, 555, 790, 1300)

            val availableAfter = runtime.database.questionOccurrenceDao().getById(scheduledId)!!
            assertEquals(scheduledId, availableAfter.id)
            assertEquals(scheduledQuestionId, availableAfter.questionId)
            assertEquals(scheduledPosition, availableAfter.cyclePosition)
            assertEquals(availablePlannedAt, availableAfter.plannedAtEpochMillis)
            assertEquals(availableSlotIndex, availableAfter.scheduleSlotIndex)
            assertNotEquals(availableUntilBefore, availableAfter.availableUntilEpochMillis)
            val strictNextUntil = expectedStrictNextAvailableUntil(runtime, availableAfter)
            assertEquals(strictNextUntil, availableAfter.availableUntilEpochMillis)
            assertEquals(1, runtime.database.questionOccurrenceDao().getIncompleteOrdered().size)
            assertEquals(cursorBefore, runtime.database.practiceStateDao().get()!!.nextCyclePosition)

            composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_PAUSE_RESUME).performClick()
            SettingsComposeTestSupport.waitForSnackbarText(composeRule, "Практика приостановлена")
            composeRule.waitUntil(timeoutMillis = 10_000) {
                runBlocking { runtime.database.practiceStateDao().get()?.isPaused == true }
            }

            val frozen = runtime.database.questionOccurrenceDao().getById(scheduledId)!!
            val pausedAt = runtime.database.practiceStateDao().get()!!.pausedAtEpochMillis!!
            val oldAvailableUntil = frozen.availableUntilEpochMillis
            val remainingAtPause = oldAvailableUntil - pausedAt

            changeScheduleViaUi(505, 995, 1365)
            Stage10BlackviewE2ESupport.assertSlotMinutes(runtime, 505, 995, 1365)
            assertEquals(frozen, runtime.database.questionOccurrenceDao().getById(scheduledId)!!)

            Stage10BlackviewE2ESupport.advanceVirtualMinutes(runtime, 5)
            composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_PAUSE_RESUME).performClick()
            SettingsComposeTestSupport.waitForSnackbarText(composeRule, "Практика продолжена")

            val resumeNow = Stage10BlackviewE2ESupport.resumeNowEpochMillis(runtime)
            val expectedAvailableUntil = resumeNow + remainingAtPause
            val resumed = runtime.database.questionOccurrenceDao().getById(scheduledId)!!
            val actualAvailableUntil = resumed.availableUntilEpochMillis
            val differenceMillis = actualAvailableUntil - expectedAvailableUntil
            val doubleExtension = actualAvailableUntil > expectedAvailableUntil

            assertEquals(scheduledId, resumed.id)
            assertEquals(availablePlannedAt, resumed.plannedAtEpochMillis)
            assertEquals(availableSlotIndex, resumed.scheduleSlotIndex)
            assertEquals(QuestionOccurrenceStatus.AVAILABLE, resumed.status)
            assertEquals(0L, differenceMillis)
            assertFalse(doubleExtension)
            assertEquals(1, runtime.database.questionOccurrenceDao().getIncompleteOrdered().size)

            backToHome()
            composeRule.onNodeWithTag(PracticeTestTags.HOME_ANSWER).performClick()
            composeRule.onNodeWithTag(PracticeTestTags.QUESTION_SKIP).performClick()
            composeRule.waitUntil(timeoutMillis = 10_000) {
                PracticeComposeTestSupport.hasHomeStartedContent(composeRule)
            }

            val skipped = runtime.database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
            assertEquals(QuestionOccurrenceStatus.SKIPPED_BY_USER, skipped.status)
            assertNull(runtime.database.answerDao().getByOccurrenceId(skipped.id))
            assertNotNull(skipped.completedAtEpochMillis)
            assertEquals(1, runtime.database.questionOccurrenceDao().getIncompleteOrdered().size)

            val second = runtime.database.questionOccurrenceDao().getByCycleAndPosition(1, 2)!!
            assertEquals(2, second.cyclePosition)
            assertTrue(second.plannedAtEpochMillis > resumed.plannedAtEpochMillis)
            Stage10BlackviewE2ESupport.assertSlotMinutes(runtime, 505, 995, 1365)
            assertEquals(3, runtime.database.practiceStateDao().get()!!.nextCyclePosition)

            openSettings()
            SettingsComposeTestSupport.openNotificationsSettings(composeRule)
            composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_SOUND_SWITCH).performClick()
            composeRule.waitForIdle()
            assertFalse(DataStoreSoundPreferenceRepository(context).soundEnabled.first())

            composeRule.onNodeWithTag(SettingsTestTags.NOTIFICATIONS_SETTINGS_BACK)
                .performScrollTo()
                .performClick()
            composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_SCREEN).assertIsDisplayed()

            SettingsComposeTestSupport.changeSlotTime(composeRule, 1, 510)
            SettingsComposeTestSupport.waitUntilTimeDisplayed(composeRule, formatTimeOfDayMinutes(510))
            composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_BACK)
                .performScrollTo()
                .performClick()
            composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_DIRTY_DIALOG).assertIsDisplayed()
            composeRule.onNodeWithText("Сохранить изменения?").assertIsDisplayed()
            composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_STAY).performClick()
            composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_SCREEN).assertIsDisplayed()
            composeRule.onNodeWithText(formatTimeOfDayMinutes(510)).assertIsDisplayed()
            Stage10BlackviewE2ESupport.assertSlotMinutes(runtime, 505, 995, 1365)

            composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_BACK)
                .performScrollTo()
                .performClick()
            composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_DISCARD).performClick()
            PracticeComposeTestSupport.waitForHomeDisplayed(composeRule)
            Stage10BlackviewE2ESupport.assertSlotMinutes(runtime, 505, 995, 1365)
            assertFalse(DataStoreSoundPreferenceRepository(context).soundEnabled.first())

            Stage10BlackviewE2ESupport.assertQuickCheckOk(runtime)
        }
    }

    private fun openSettings() {
        PracticeComposeTestSupport.clickHomeSettings(composeRule)
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_SCREEN).assertIsDisplayed()
    }

    private fun backToHome() {
        // 06.08.2026 Stage 12 Connected Navigation Fix cursor by Me4Hik START - system back avoids runBlocking/Main deadlock
        pressBack()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            !PracticeComposeTestSupport.hasNodeWithTag(composeRule, SettingsTestTags.SETTINGS_SCREEN)
        }
        PracticeComposeTestSupport.waitForHome(composeRule)
        PracticeComposeTestSupport.assertHomeStartedContentDisplayed(composeRule)
        // 06.08.2026 Stage 12 Connected Navigation Fix cursor by Me4Hik END
    }

    private fun assertSlotAffordance() {
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_SLOT_1).assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_SLOT_2).assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_SLOT_3).assertIsDisplayed().assertHasClickAction()
        composeRule.onAllNodesWithText("Изменить").assertCountEquals(3)
    }

    private fun assertDefaultSlotTimesOnUi() {
        composeRule.onNodeWithText("Время 1").assertIsDisplayed()
        composeRule.onNodeWithText("Время 2").assertIsDisplayed()
        composeRule.onNodeWithText("Время 3").assertIsDisplayed()
        assertUiShowsTimes(660, 900, 1140)
    }

    private fun assertUiShowsTimes(first: Int, second: Int, third: Int) {
        composeRule.onNodeWithText(formatTimeOfDayMinutes(first)).assertIsDisplayed()
        composeRule.onNodeWithText(formatTimeOfDayMinutes(second)).assertIsDisplayed()
        composeRule.onNodeWithText(formatTimeOfDayMinutes(third)).assertIsDisplayed()
    }

    private fun changeScheduleViaUi(first: Int, second: Int, third: Int) {
        SettingsComposeTestSupport.changeSlotTime(composeRule, 1, first)
        SettingsComposeTestSupport.waitForScheduleAutosaveIdle(composeRule)
        SettingsComposeTestSupport.changeSlotTime(composeRule, 2, second)
        SettingsComposeTestSupport.waitForScheduleAutosaveIdle(composeRule)
        SettingsComposeTestSupport.changeSlotTime(composeRule, 3, third)
        SettingsComposeTestSupport.waitForScheduleAutosaveIdle(composeRule)
    }

    private fun saveScheduleAndWaitClean() {
        SettingsComposeTestSupport.waitForScheduleAutosaveIdle(composeRule)
    }

    private suspend fun expectedStrictNextAvailableUntil(
        runtime: com.me4hik.praktika.runtime.PraktikaRuntime,
        occurrence: QuestionOccurrenceEntity,
    ): Long {
        val state = runtime.database.practiceStateDao().get()!!
        val slots = runtime.database.scheduleSlotDao().getAllOrderedByTime()
        val calculator = ScheduleCalculator()
        val now = runtime.timeProvider.nowEpochMillis()
        return calculator.findStrictlyNextSlot(
            afterEpochMillis = now,
            zoneId = state.activeZoneId,
            slots = slots,
        ).plannedAtEpochMillis
    }

    private fun resetAcceleratedSandbox() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        MainActivityInitGate.resetForTests()
        PraktikaRuntimeHolder.resetForTests()
        context.filesDir.listFiles()
            ?.filter { it.name == AcceleratedTimeStorage.STATE_FILE_NAME }
            ?.forEach { it.delete() }
        context.getDatabasePath(RuntimeFactory.ACCELERATED_DATABASE_NAME).delete()
    }
}
// 06.08.2026 Stage 10 Blackview E2E cursor by Me4Hik END
