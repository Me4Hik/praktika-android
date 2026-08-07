// 06.08.2026 Settings Schedule cursor by Me4Hik START - accelerated MainActivity Settings flow
package com.me4hik.praktika.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.me4hik.praktika.MainActivity
import com.me4hik.praktika.MainActivityInitGate
import com.me4hik.praktika.accelerated.AcceleratedTimeStorage
import com.me4hik.praktika.accelerated.AcceleratedTimeProvider
import com.me4hik.praktika.data.cycle.ScheduleSlotUpdate
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.data.preferences.DataStoreSoundPreferenceRepository
import com.me4hik.praktika.runtime.PraktikaRuntimeHolder
import com.me4hik.praktika.runtime.RuntimeFactory
import com.me4hik.praktika.ui.practice.PracticeComposeTestSupport
import com.me4hik.praktika.ui.practice.PracticeTestTags
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

@RunWith(AndroidJUnit4::class)
class MainActivitySettingsAcceleratedInstrumentedTest {
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
    fun fullSettingsFlowOnRealMainActivity() {
        // 06.08.2026 Stage 12 Connected Navigation Fix cursor by Me4Hik START - no outer runBlocking (blocks Main navigation)
        runBlocking { MainActivityInitGate.awaitInit() }
        PracticeComposeTestSupport.ensureTestActivityResumed(composeRule)
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            PracticeComposeTestSupport.hasNodeWithTag(composeRule, PracticeTestTags.ONBOARDING_START)
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val runtime = runBlocking { PraktikaRuntimeHolder.get(context) }
        runBlocking { runtime.cycleRepository.startPractice() }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            PracticeComposeTestSupport.hasNodeWithTag(composeRule, PracticeTestTags.HOME_PLANNED_TIME)
        }

        runBlocking {
            runtime.cycleRepository.updateSchedule(scheduleUpdates(630, 860, 1210))
            assertSlotMinutes(runtime, 630, 860, 1210)
            advanceToAvailable(runtime)
        }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            PracticeComposeTestSupport.hasNodeWithTag(composeRule, PracticeTestTags.HOME_ANSWER)
        }

        val availableBefore = runBlocking {
            runtime.database.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!
        }
        val historicalId = availableBefore.id
        val historicalPlannedAt = availableBefore.plannedAtEpochMillis
        val historicalSlotIndex = availableBefore.scheduleSlotIndex

        runBlocking {
            runtime.cycleRepository.updateSchedule(scheduleUpdates(555, 790, 1300))
        }
        val availableAfter = runBlocking {
            runtime.database.questionOccurrenceDao().getById(historicalId)!!
        }
        assertEquals(historicalId, availableAfter.id)
        assertEquals(historicalPlannedAt, availableAfter.plannedAtEpochMillis)
        assertEquals(historicalSlotIndex, availableAfter.scheduleSlotIndex)
        assertTrue(availableAfter.availableUntilEpochMillis != availableBefore.availableUntilEpochMillis)

        openSettings()
        SettingsComposeTestSupport.clickPauseResumeControl(composeRule, expectPaused = false)
        SettingsComposeTestSupport.waitForSnackbarText(composeRule, "Практика приостановлена")
        SettingsComposeTestSupport.waitForSnackbarDismiss(composeRule, "Практика приостановлена")
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runBlocking { runtime.database.practiceStateDao().get()?.isPaused == true }
        }
        SettingsComposeTestSupport.waitUntilPracticePauseUi(composeRule, paused = true)
        val frozen = runBlocking { runtime.database.questionOccurrenceDao().getById(historicalId)!! }
        val pausedAt = runBlocking { runtime.database.practiceStateDao().get()!!.pausedAtEpochMillis!! }
        val frozenUntil = frozen.availableUntilEpochMillis

        runBlocking {
            runtime.cycleRepository.updateSchedule(scheduleUpdates(505, 995, 1365))
            assertEquals(frozen, runtime.database.questionOccurrenceDao().getById(historicalId)!!)
            assertSlotMinutes(runtime, 505, 995, 1365)
        }

        val clock = runtime.timeProvider as AcceleratedTimeProvider
        runBlocking {
            clock.advanceByVirtualMinutes(5)
            runtime.cycleRepository.reconcile()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_SCREEN).assertIsDisplayed()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runBlocking { runtime.database.practiceStateDao().get()?.isPaused == true }
        }
        SettingsComposeTestSupport.waitUntilPracticePauseUi(composeRule, paused = true)
        SettingsComposeTestSupport.clickPauseResumeControl(composeRule, expectPaused = true)
        SettingsComposeTestSupport.waitForSnackbarText(composeRule, "Практика продолжена")
        val resumed = runBlocking { runtime.database.questionOccurrenceDao().getById(historicalId)!! }
        assertEquals(frozenUntil + (clock.currentVirtualNow() - pausedAt), resumed.availableUntilEpochMillis)

        runBlocking { runtime.cycleRepository.skipAvailableByUser(historicalId) }
        val second = runBlocking { runtime.database.questionOccurrenceDao().getByCycleAndPosition(1, 2)!! }
        assertTrue(second.plannedAtEpochMillis > resumed.plannedAtEpochMillis)
        runBlocking { assertSlotMinutes(runtime, 505, 995, 1365) }

        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_SOUND_SWITCH)
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        assertFalse(runBlocking { DataStoreSoundPreferenceRepository(context).soundEnabled.first() })

        PracticeComposeTestSupport.navigateBackFromSettings(composeRule)
        composeRule.waitUntil(timeoutMillis = 10_000) {
            PracticeComposeTestSupport.hasNodeWithTag(composeRule, PracticeTestTags.HOME_SETTINGS)
        }

        composeRule.activityRule.scenario.recreate()
        runBlocking { MainActivityInitGate.awaitInit() }
        PracticeComposeTestSupport.ensureTestActivityResumed(composeRule)
        composeRule.waitUntil(timeoutMillis = 10_000) {
            PracticeComposeTestSupport.hasNodeWithTag(composeRule, PracticeTestTags.HOME_SETTINGS)
        }
        openSettings()
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_SCREEN).assertIsDisplayed()
        runBlocking { assertSlotMinutes(runtime, 505, 995, 1365) }
        // 06.08.2026 Stage 12 Connected Navigation Fix cursor by Me4Hik END
    }

    private fun openSettings() {
        PracticeComposeTestSupport.clickHomeSettings(composeRule)
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_SCREEN).assertIsDisplayed()
    }

    private suspend fun advanceToAvailable(runtime: com.me4hik.praktika.runtime.PraktikaRuntime) {
        val clock = runtime.timeProvider as AcceleratedTimeProvider
        val incomplete = runtime.database.questionOccurrenceDao().getIncompleteOrdered().single()
        clock.advanceToVirtualEpochMillis(incomplete.plannedAtEpochMillis)
        runtime.cycleRepository.reconcile()
        clock.checkpoint()
    }

    private fun scheduleUpdates(first: Int, second: Int, third: Int): List<ScheduleSlotUpdate> {
        return listOf(
            ScheduleSlotUpdate(1, first),
            ScheduleSlotUpdate(2, second),
            ScheduleSlotUpdate(3, third),
        )
    }

    private suspend fun assertSlotMinutes(
        runtime: com.me4hik.praktika.runtime.PraktikaRuntime,
        first: Int,
        second: Int,
        third: Int,
    ) {
        val byIndex = runtime.database.scheduleSlotDao().getAllOrderedByTime().associateBy { it.slotIndex }
        assertEquals(first, byIndex.getValue(1).timeOfDayMinutes)
        assertEquals(second, byIndex.getValue(2).timeOfDayMinutes)
        assertEquals(third, byIndex.getValue(3).timeOfDayMinutes)
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
// 06.08.2026 Settings Schedule cursor by Me4Hik END
