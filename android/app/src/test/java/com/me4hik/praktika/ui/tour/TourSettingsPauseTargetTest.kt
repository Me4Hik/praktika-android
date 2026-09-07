package com.me4hik.praktika.ui.tour

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * D3: SETTINGS_PAUSE target lives on the pause/resume control;
 * registry tracks the active Settings target (no stale prior section).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TourSettingsPauseTargetTest {

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

    private fun settingsSteps() = TourDefinition(
        id = "settings_pause_target",
        steps = listOf(
            TourStep(
                id = TourStepId.SCHEDULE_INFO,
                type = TourStepType.SHOW_ONLY,
                tipResId = android.R.string.ok,
                targetId = TourTargetId.SETTINGS_SCHEDULE,
                completion = TourCompletion.ManualAdvance,
                allowScroll = true,
                bringIntoView = true,
            ),
            TourStep(
                id = TourStepId.CHOOSE_WORDING,
                type = TourStepType.USER_CHOICE,
                tipResId = android.R.string.ok,
                targetId = TourTargetId.SETTINGS_WORDING,
                completion = TourCompletion.TargetActivation,
                allowScroll = true,
                bringIntoView = true,
            ),
            TourStep(
                id = TourStepId.PAUSE_INFO,
                type = TourStepType.SHOW_ONLY,
                tipResId = android.R.string.ok,
                targetId = TourTargetId.SETTINGS_PAUSE,
                completion = TourCompletion.ManualAdvance,
                allowScroll = true,
                bringIntoView = true,
            ),
            TourStep(
                id = TourStepId.BACKUP_INFO,
                type = TourStepType.SHOW_ONLY,
                tipResId = android.R.string.ok,
                targetId = TourTargetId.SETTINGS_BACKUP,
                completion = TourCompletion.ManualAdvance,
                allowScroll = true,
                bringIntoView = true,
            ),
        ),
    )

    @Test
    fun pauseTarget_isOnPauseResumeRow_andRegistryTracksActiveStep() = runTest {
        val store = FakeStore()
        val controller = TourController(store, clock = { 1L }, targetWaitTimeoutMs = 5_000L)
        val registry = TourTargetRegistry()

        composeRule.setContent {
            PraktikaTheme {
                CompositionLocalProvider(
                    LocalTourController provides controller,
                    LocalTourTargetRegistry provides registry,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(400.dp)
                                .testTag("schedule_block")
                                .tourTarget(TourTargetId.SETTINGS_SCHEDULE),
                        ) {
                            Text("schedule")
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .testTag("wording_block")
                                .tourTarget(TourTargetId.SETTINGS_WORDING),
                        ) {
                            Text("wording")
                        }
                        // Pause control row — production attaches SETTINGS_PAUSE here.
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(72.dp)
                                .testTag(SettingsTestTags.SETTINGS_PAUSE_RESUME)
                                .tourTarget(TourTargetId.SETTINGS_PAUSE),
                        ) {
                            Text("pause-resume")
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp)
                                .testTag("backup_block")
                                .tourTarget(TourTargetId.SETTINGS_BACKUP),
                        ) {
                            Text("backup")
                        }
                    }
                }
            }
        }

        controller.start(settingsSteps())
        runCurrent()
        composeRule.waitForIdle()
        runCurrent()

        assertEquals(TourStepId.SCHEDULE_INFO, controller.session.value.currentStep?.id)
        composeRule.onNodeWithTag("schedule_block").assertExists()
        val scheduleBounds = registry.bounds(TourTargetId.SETTINGS_SCHEDULE)
        assertNotNull(scheduleBounds)

        controller.onNext()
        runCurrent()
        // Wording is USER_CHOICE — still on wording until activation; advance via notify.
        assertEquals(TourStepId.CHOOSE_WORDING, controller.session.value.currentStep?.id)
        controller.onTargetActivated(TourTargetId.SETTINGS_WORDING)
        runCurrent()
        composeRule.waitForIdle()
        runCurrent()

        assertEquals(TourStepId.PAUSE_INFO, controller.session.value.currentStep?.id)
        composeRule.onNodeWithTag(SettingsTestTags.SETTINGS_PAUSE_RESUME).assertExists()
        val pauseBounds = registry.bounds(TourTargetId.SETTINGS_PAUSE)
        assertNotNull(pauseBounds)
        assertTrue(pauseBounds!!.height > 0f)

        // Active step target must be PAUSE, not leftover schedule-only spotlight identity.
        assertEquals(TourTargetId.SETTINGS_PAUSE, controller.session.value.currentStep?.targetId)
        val scheduleStill = registry.bounds(TourTargetId.SETTINGS_SCHEDULE)
        assertNotNull(scheduleStill)
        // Distinct sections → distinct bounds (not identical stale rect reused as active hole).
        assertNotEquals(scheduleStill, pauseBounds)

        // Simulate post-scroll settle: update pause coords and ensure registry accepts new rect.
        val refreshed = Rect(
            pauseBounds.left,
            pauseBounds.top + 40f,
            pauseBounds.right,
            pauseBounds.bottom + 40f,
        )
        registry.update(TourTargetId.SETTINGS_PAUSE, refreshed)
        assertEquals(refreshed, registry.bounds(TourTargetId.SETTINGS_PAUSE))
    }
}
