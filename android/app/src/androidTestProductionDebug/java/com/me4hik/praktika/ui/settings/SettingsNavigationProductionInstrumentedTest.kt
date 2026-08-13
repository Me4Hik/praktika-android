// 06.08.2026 Settings Schedule cursor by Me4Hik START - production Settings Compose tests
// 09.08.2026 Post-release fixes cursor by Me4Hik START - schedule autosave instrumented tests
package com.me4hik.praktika.ui.settings

import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.me4hik.praktika.navigation.AppNavigation
import com.me4hik.praktika.ui.practice.PracticeComposeTestActivity
import com.me4hik.praktika.ui.practice.PracticeComposeTestHarness
import com.me4hik.praktika.ui.practice.PracticeComposeTestSupport
import com.me4hik.praktika.ui.practice.PracticeRootViewModel
import com.me4hik.praktika.ui.practice.PracticeRootViewModelFactory
import com.me4hik.praktika.ui.practice.PracticeTestTags
import com.me4hik.praktika.ui.practice.PracticeUiTestHarness
import com.me4hik.praktika.ui.theme.PraktikaTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsNavigationProductionInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<PracticeComposeTestActivity>()

    private lateinit var harness: PracticeUiTestHarness

    @Before
    fun setUp() {
        harness = PracticeUiTestHarness(
            InstrumentationRegistry.getInstrumentation().targetContext,
        )
    }

    @After
    fun tearDown() {
        if (::harness.isInitialized) {
            harness.tearDown()
        }
    }

    @Test
    fun homeOpensSettingsWithDefaultTimes() {
        setPracticeNavigationContent {
            setUp(initialHour = 8, initialMinute = 0)
            startPractice()
        }
        PracticeComposeTestSupport.waitForHome(composeRule)
        composeRule.onNodeWithTag(PracticeTestTags.HOME_SETTINGS).performClick()
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_SCREEN).assertIsDisplayed()
        composeRule.onNodeWithText("Время 1").performScrollTo().assertIsDisplayed()
        listOf("11:00", "15:00", "19:00").forEach { time ->
            composeRule.onNodeWithText(time).performScrollTo().assertIsDisplayed()
        }
        composeRule.onAllNodesWithText("Изменить").assertCountEquals(3)
        SettingsComposeTestSupport.assertSaveScheduleButtonAbsent(composeRule)
    }

    @Test
    fun scheduledHomeDoesNotExposeSnapshotAfterSettingsBack() {
        setPracticeNavigationContent {
            setUp(initialHour = 8, initialMinute = 0)
            startPractice()
        }
        PracticeComposeTestSupport.waitForHome(composeRule)
        PracticeComposeTestSupport.clickHomeSettings(composeRule)
        PracticeComposeTestSupport.navigateBackFromSettings(composeRule)
        PracticeComposeTestSupport.assertHomePlannedTimeDisplayed(composeRule)
    }

    @Test
    fun timePickerOkAutosavesOnRealRoute() {
        setPracticeNavigationContent {
            setUp(initialHour = 8, initialMinute = 0)
            startPractice()
        }
        PracticeComposeTestSupport.waitForHome(composeRule)
        composeRule.onNodeWithTag(PracticeTestTags.HOME_SETTINGS).performClick()
        SettingsComposeTestSupport.changeSlotTime(composeRule, 1, 630)
        SettingsComposeTestSupport.waitForScheduleAutosaveIdle(composeRule)
        runBlocking {
            assertEquals(
                630,
                harness.runtime.database.scheduleSlotDao().getByIndex(1)!!.timeOfDayMinutes,
            )
        }
        SettingsComposeTestSupport.assertSaveScheduleButtonAbsent(composeRule)
    }

    @Test
    fun timePickerCancelDoesNotPersist() {
        setPracticeNavigationContent {
            setUp(initialHour = 8, initialMinute = 0)
            startPractice()
        }
        PracticeComposeTestSupport.waitForHome(composeRule)
        composeRule.onNodeWithTag(PracticeTestTags.HOME_SETTINGS).performClick()
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_SLOT_1).performClick()
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_TIME_PICKER).assertIsDisplayed()
        SettingsComposeTestSupport.cancelTimePicker(composeRule)
        runBlocking {
            assertEquals(
                660,
                harness.runtime.database.scheduleSlotDao().getByIndex(1)!!.timeOfDayMinutes,
            )
        }
        composeRule.onNodeWithText("11:00").assertIsDisplayed()
    }

    @Test
    fun dirtyBackDialogStayAndDiscardKeepsRoomSchedule() {
        setDirtySettingsNavigationContent()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            try {
                composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_SCHEDULE_ERROR).assertIsDisplayed()
                true
            } catch (_: AssertionError) {
                false
            }
        }
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_BACK).performClick()
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_DIRTY_DIALOG).assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_STAY).performClick()
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_BACK).performClick()
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_DISCARD).performClick()
        composeRule.onNodeWithTag(PracticeTestTags.HOME_POSITION).assertIsDisplayed()
        runBlocking {
            val slots = harness.runtime.database.scheduleSlotDao().getAllOrderedByTime()
            assertEquals(660, slots.first { it.slotIndex == 1 }.timeOfDayMinutes)
        }
    }

    @Test
    fun validScheduleAutosavesWithoutDiscardDialogOnBack() {
        setPracticeNavigationContent {
            setUp(initialHour = 8, initialMinute = 0)
            startPractice()
        }
        PracticeComposeTestSupport.waitForHome(composeRule)
        composeRule.onNodeWithTag(PracticeTestTags.HOME_SETTINGS).performClick()
        SettingsComposeTestSupport.changeSlotTime(composeRule, 1, 630)
        SettingsComposeTestSupport.waitForScheduleAutosaveIdle(composeRule)
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_BACK).performClick()
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_DIRTY_DIALOG).assertDoesNotExist()
        composeRule.onNodeWithTag(PracticeTestTags.HOME_POSITION).assertIsDisplayed()
    }

    private fun setDirtySettingsNavigationContent(
        setup: suspend PracticeUiTestHarness.() -> Unit = {
            setUp(initialHour = 8, initialMinute = 0)
            startPractice()
        },
    ) {
        PracticeComposeTestSupport.ensureTestActivityResumed(composeRule)
        runBlocking { harness.setup() }
        composeRule.setContent {
            PraktikaTheme {
                SettingsDirtyBackTestNavigation(
                    runtime = harness.runtime,
                    startOnSettings = true,
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun setPracticeNavigationContent(
        setup: suspend PracticeUiTestHarness.() -> Unit = { setUp() },
    ): PracticeRootViewModel {
        PracticeComposeTestSupport.ensureTestActivityResumed(composeRule)
        runBlocking { harness.setup() }
        val holder = arrayOfNulls<PracticeRootViewModel>(1)
        composeRule.setContent {
            PraktikaTheme {
                val factory = remember(harness.runtime, composeRule.activity) {
                    PracticeRootViewModelFactory(
                        owner = composeRule.activity,
                        runtime = harness.runtime,
                        onRequestPostNotifications = {},
                        onOpenAppNotificationSettings = {},
                        onOpenChannelSettings = {},
                    )
                }
                val viewModel: PracticeRootViewModel = viewModel(factory = factory)
                holder[0] = viewModel
                AppNavigation(viewModel = viewModel, runtime = harness.runtime)
            }
        }
        composeRule.waitForIdle()
        runBlocking {
            PracticeComposeTestHarness.awaitInitialUiState(checkNotNull(holder[0]))
        }
        composeRule.waitForIdle()
        return checkNotNull(holder[0])
    }
}
// 09.08.2026 Post-release fixes cursor by Me4Hik END
// 06.08.2026 Settings Schedule cursor by Me4Hik END
