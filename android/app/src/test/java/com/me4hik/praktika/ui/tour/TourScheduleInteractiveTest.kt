package com.me4hik.praktika.ui.tour

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.me4hik.praktika.ui.settings.SettingsTestTags
import com.me4hik.praktika.ui.theme.PraktikaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TourScheduleInteractiveTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeStore : TesterToolsStore {
        private val unlockedFlow = MutableStateFlow(true)
        private val resultFlow = MutableStateFlow<TourRunResult?>(null)
        override val unlocked: Flow<Boolean> = unlockedFlow
        override val lastTourResult: Flow<TourRunResult?> = resultFlow
        override suspend fun setUnlocked(unlocked: Boolean) {
            unlockedFlow.value = unlocked
        }
        override suspend fun saveTourResult(result: TourRunResult) {
            resultFlow.value = result
        }
    }

    @Test
    fun isTourScheduleInteractive_onlyOnScheduleStep() {
        val scheduleStep = TourStep(
            id = TourStepId.SCHEDULE_INFO,
            type = TourStepType.USER_CHOICE,
            tipResId = android.R.string.ok,
            targetId = TourTargetId.SETTINGS_SCHEDULE,
            completion = TourCompletion.TargetActivation,
        )
        val pauseStep = TourStep(
            id = TourStepId.PAUSE_INFO,
            type = TourStepType.SHOW_ONLY,
            tipResId = android.R.string.ok,
            targetId = TourTargetId.SETTINGS_PAUSE,
            completion = TourCompletion.ManualAdvance,
        )
        assertTrue(isTourScheduleInteractive(tourActive = false, step = scheduleStep))
        assertTrue(isTourScheduleInteractive(tourActive = true, step = scheduleStep))
        assertFalse(isTourScheduleInteractive(tourActive = true, step = pauseStep))
        assertFalse(isTourScheduleInteractive(tourActive = true, step = null))
    }

    @Test
    fun scheduleConfirm_sameValue_activatesAndAdvances_cancelDoesNot() = runTest {
        val controller = TourController(FakeStore(), clock = { 1L })
        var slotChanges = 0
        var lastMinutes = -1

        composeRule.setContent {
            PraktikaTheme {
                CompositionLocalProvider(LocalTourController provides controller) {
                    val session by controller.session.collectAsStateWithLifecycle()
                    val tourActive = session.isActive
                    val scheduleInteractive = isTourScheduleInteractive(tourActive, session.currentStep)
                    var pickerOpen by remember { mutableStateOf(false) }
                    var savedMinutes by remember { mutableIntStateOf(12 * 60) }

                    Column(modifier = Modifier.fillMaxSize()) {
                        TextButton(
                            onClick = {
                                if (!tourActive || scheduleInteractive) {
                                    pickerOpen = true
                                }
                            },
                            enabled = !tourActive || scheduleInteractive,
                            modifier = Modifier.testTag(SettingsTestTags.SETTINGS_SLOT_1),
                        ) {
                            Text("change")
                        }
                        if (pickerOpen) {
                            TextButton(
                                onClick = {
                                    // Confirm same minutes as production allows.
                                    slotChanges++
                                    lastMinutes = savedMinutes
                                    savedMinutes = lastMinutes
                                    pickerOpen = false
                                    if (isTourScheduleInteractive(
                                            tourActive = controller.session.value.isActive,
                                            step = controller.session.value.currentStep,
                                        )
                                    ) {
                                        controller.notifyActivation(TourTargetId.SETTINGS_SCHEDULE)
                                    }
                                },
                                modifier = Modifier.testTag(SettingsTestTags.SETTINGS_TIME_PICKER),
                            ) {
                                Text("confirm")
                            }
                            TextButton(
                                onClick = { pickerOpen = false },
                                modifier = Modifier.testTag("picker_cancel"),
                            ) {
                                Text("cancel")
                            }
                        }
                    }
                }
            }
        }

        controller.start(
            TourDefinition(
                id = "schedule_interactive",
                steps = listOf(
                    TourStep(
                        id = TourStepId.SCHEDULE_INFO,
                        type = TourStepType.USER_CHOICE,
                        tipResId = android.R.string.ok,
                        targetId = TourTargetId.SETTINGS_SCHEDULE,
                        completion = TourCompletion.TargetActivation,
                    ),
                    TourStep(
                        id = TourStepId.CHOOSE_WORDING,
                        type = TourStepType.USER_CHOICE,
                        tipResId = android.R.string.ok,
                        targetId = TourTargetId.SETTINGS_WORDING,
                        completion = TourCompletion.TargetActivation,
                    ),
                ),
            ),
        )
        runCurrent()
        composeRule.waitForIdle()

        assertEquals(TourStepId.SCHEDULE_INFO, controller.session.value.currentStep?.id)

        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_SLOT_1).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("picker_cancel").performClick()
        composeRule.waitForIdle()
        assertEquals(0, slotChanges)
        assertEquals(TourStepId.SCHEDULE_INFO, controller.session.value.currentStep?.id)

        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_SLOT_1).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_TIME_PICKER).assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_TIME_PICKER).performClick()
        runCurrent()
        composeRule.waitForIdle()

        assertEquals(1, slotChanges)
        assertEquals(12 * 60, lastMinutes)
        assertEquals(TourStepId.CHOOSE_WORDING, controller.session.value.currentStep?.id)
    }

    @Test
    fun otherTourStep_blocksScheduleClicks() = runTest {
        val controller = TourController(FakeStore(), clock = { 1L })
        var opens = 0

        composeRule.setContent {
            PraktikaTheme {
                CompositionLocalProvider(LocalTourController provides controller) {
                    val session by controller.session.collectAsStateWithLifecycle()
                    val tourActive = session.isActive
                    val scheduleInteractive = isTourScheduleInteractive(tourActive, session.currentStep)
                    TextButton(
                        onClick = {
                            if (!tourActive || scheduleInteractive) opens++
                        },
                        enabled = !tourActive || scheduleInteractive,
                        modifier = Modifier.testTag(SettingsTestTags.SETTINGS_SLOT_1),
                    ) {
                        Text("change")
                    }
                }
            }
        }

        controller.start(
            TourDefinition(
                id = "schedule_blocked",
                steps = listOf(
                    TourStep(
                        id = TourStepId.PAUSE_INFO,
                        type = TourStepType.SHOW_ONLY,
                        tipResId = android.R.string.ok,
                        targetId = TourTargetId.SETTINGS_PAUSE,
                        completion = TourCompletion.ManualAdvance,
                    ),
                ),
            ),
        )
        runCurrent()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_SLOT_1).performClick()
        composeRule.waitForIdle()
        assertEquals(0, opens)
    }
}
