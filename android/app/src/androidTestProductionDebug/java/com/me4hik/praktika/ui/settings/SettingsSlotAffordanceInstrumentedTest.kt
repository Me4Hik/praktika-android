// 06.08.2026 Settings Time Affordance cursor by Me4Hik START - slot affordance Compose tests
// 09.08.2026 Post-release fixes cursor by Me4Hik START - schedule autosave affordance tests
package com.me4hik.praktika.ui.settings

import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.me4hik.praktika.navigation.AppNavigation
import com.me4hik.praktika.ui.SettingsScreen
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
class SettingsSlotAffordanceInstrumentedTest {
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
    fun slotControlsAreDisplayedWithExplicitChangeAffordance() {
        openSettingsViaNavigation()
        composeRule.onNodeWithText("Время 1").assertIsDisplayed()
        composeRule.onNodeWithText("Время 2").assertIsDisplayed()
        composeRule.onNodeWithText("Время 3").assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_SLOT_1).assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_SLOT_2).assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_SLOT_3).assertIsDisplayed()
        composeRule.onAllNodesWithText("Изменить").assertCountEquals(3)
        SettingsComposeTestSupport.assertSaveScheduleButtonAbsent(composeRule)
    }

    @Test
    fun allSlotControlsHaveClickAction() {
        openSettingsViaNavigation()
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_SLOT_1).assertHasClickAction()
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_SLOT_2).assertHasClickAction()
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_SLOT_3).assertHasClickAction()
    }

    @Test
    fun clickingSlotRowOpensPicker() {
        openSettingsViaNavigation()
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_SLOT_1).performClick()
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_TIME_PICKER).assertIsDisplayed()
    }

    @Test
    fun autosaveUpdatesVisibleValueWithoutManualSave() {
        setDraftNavigationContent(
            applyDraftChanges = mapOf(1 to 630),
        )
        composeRule.waitUntil(timeoutMillis = 10_000) {
            try {
                composeRule.onNodeWithText("10:30").assertIsDisplayed()
                true
            } catch (_: AssertionError) {
                false
            }
        }
        SettingsComposeTestSupport.waitForScheduleAutosaveIdle(composeRule)
        SettingsComposeTestSupport.assertSaveScheduleButtonAbsent(composeRule)
        runBlocking {
            assertEquals(
                630,
                harness.runtime.database.scheduleSlotDao().getByIndex(1)!!.timeOfDayMinutes,
            )
        }
    }

    @Test
    fun autosavePersistsSelectedDraftValues() {
        setDraftNavigationContent(
            applyDraftChanges = mapOf(
                1 to 630,
                2 to 860,
                3 to 1210,
            ),
        )
        SettingsComposeTestSupport.waitForScheduleAutosaveIdle(composeRule)
        runBlocking {
            val slots = harness.runtime.database.scheduleSlotDao().getAllOrderedByTime()
                .associateBy { it.slotIndex }
            assertEquals(630, slots.getValue(1).timeOfDayMinutes)
            assertEquals(860, slots.getValue(2).timeOfDayMinutes)
            assertEquals(1210, slots.getValue(3).timeOfDayMinutes)
        }
    }

    @Test
    fun slotsRemainClickableWhileSaving() {
        PracticeComposeTestSupport.ensureTestActivityResumed(composeRule)
        composeRule.setContent {
            PraktikaTheme {
                SettingsScreen(
                    uiState = sampleContent(isSavingSchedule = true),
                    onSlotTimeChange = { _, _ -> },
                    onSoundEnabledChanged = {},
                    onDeferDurationMinutesChanged = {},
                    onOpenNotificationSettings = {},
                    onTogglePauseState = {},
                    onBack = {},
                    onStayOnDirtyBack = {},
                    onDiscardChanges = {},
                    showDirtyDialog = false,
                )
            }
        }
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_SLOT_1).assertHasClickAction()
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_SLOT_2).assertHasClickAction()
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_SLOT_3).assertHasClickAction()
    }

    @Test
    fun duplicateValidationStillWorks() {
        setDraftNavigationContent(applyDraftChanges = mapOf(2 to 660))
        composeRule.waitUntil(timeoutMillis = 10_000) {
            try {
                composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_SCHEDULE_ERROR).assertIsDisplayed()
                true
            } catch (_: AssertionError) {
                false
            }
        }
        SettingsComposeTestSupport.assertSaveScheduleButtonAbsent(composeRule)
        runBlocking {
            assertEquals(
                900,
                harness.runtime.database.scheduleSlotDao().getByIndex(2)!!.timeOfDayMinutes,
            )
        }
    }

    @Test
    fun dirtyBackStillWorksForInvalidDraft() {
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
    }

    @Test
    fun slotTagsHaveNoDuplicateSemanticsNodes() {
        openSettingsViaNavigation()
        composeRule.onAllNodesWithTag(SettingsTestTags.SETTINGS_SLOT_1).assertCountEquals(1)
        composeRule.onAllNodesWithTag(SettingsTestTags.SETTINGS_SLOT_2).assertCountEquals(1)
        composeRule.onAllNodesWithTag(SettingsTestTags.SETTINGS_SLOT_3).assertCountEquals(1)
    }

    private fun setDraftNavigationContent(
        applyDraftChanges: Map<Int, Int> = emptyMap(),
        startOnSettings: Boolean = true,
        setup: suspend PracticeUiTestHarness.() -> Unit = {
            setUp(initialHour = 8, initialMinute = 0)
            startPractice()
        },
    ) {
        PracticeComposeTestSupport.ensureTestActivityResumed(composeRule)
        runBlocking { harness.setup() }
        composeRule.setContent {
            PraktikaTheme {
                SettingsScheduleDraftTestNavigation(
                    runtime = harness.runtime,
                    startOnSettings = startOnSettings,
                    applyDraftChanges = applyDraftChanges,
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun openSettingsViaNavigation() {
        PracticeComposeTestSupport.ensureTestActivityResumed(composeRule)
        runBlocking {
            harness.setUp(initialHour = 8, initialMinute = 0)
            harness.startPractice()
        }
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
        PracticeComposeTestSupport.waitForHome(composeRule)
        composeRule.onNodeWithTag(PracticeTestTags.HOME_SETTINGS).performClick()
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_SCREEN).assertIsDisplayed()
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

    private fun sampleContent(isSavingSchedule: Boolean): SettingsUiState.Content {
        return SettingsUiState.Content(
            slots = listOf(
                SettingsSlotUiModel(1, 660, "11:00"),
                SettingsSlotUiModel(2, 900, "15:00"),
                SettingsSlotUiModel(3, 1140, "19:00"),
            ),
            isDirty = false,
            isScheduleValid = true,
            isSavingSchedule = isSavingSchedule,
            soundEnabled = true,
            isChangingSound = false,
            deferDurationMinutes = 15,
            isChangingDeferDuration = false,
            isPracticePaused = false,
            isChangingPauseState = false,
            scheduleError = null,
            soundError = null,
            deferError = null,
            pauseError = null,
        )
    }
}
// 09.08.2026 Post-release fixes cursor by Me4Hik END
// 06.08.2026 Settings Time Affordance cursor by Me4Hik END
