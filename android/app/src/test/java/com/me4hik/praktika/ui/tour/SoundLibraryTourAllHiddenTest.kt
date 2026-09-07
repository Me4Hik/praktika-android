package com.me4hik.praktika.ui.tour

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.me4hik.praktika.R
import com.me4hik.praktika.sound.BuiltinSoundCatalog
import com.me4hik.praktika.ui.settings.SoundLibraryItemUi
import com.me4hik.praktika.ui.settings.SoundLibraryScreen
import com.me4hik.praktika.ui.settings.SoundLibraryTestTags
import com.me4hik.praktika.ui.settings.SoundLibraryUiState
import com.me4hik.praktika.ui.theme.PraktikaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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
class SoundLibraryTourAllHiddenTest {

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

    private fun soundStepsDefinition() = TourDefinition(
        id = "sound_all_hidden",
        steps = listOf(
            TourStep(
                id = TourStepId.PREVIEW_SOUND,
                type = TourStepType.TARGET_CLICK,
                tipResId = android.R.string.ok,
                targetId = TourTargetId.SOUND_LIBRARY_FIRST_VISIBLE_BUILTIN_PLAY,
                completion = TourCompletion.TargetActivation,
            ),
            TourStep(
                id = TourStepId.CHOOSE_SOUND,
                type = TourStepType.USER_CHOICE,
                tipResId = android.R.string.ok,
                targetId = TourTargetId.SOUND_LIBRARY_FIRST_VISIBLE_BUILTIN_ITEM,
                completion = TourCompletion.TargetActivation,
            ),
            TourStep(
                id = TourStepId.HIDE_RESTORE_INFO,
                type = TourStepType.SHOW_ONLY,
                tipResId = R.string.tour_step_hide_restore_info,
                targetId = TourTargetId.SOUND_HIDE_RESTORE_INFO,
                completion = TourCompletion.ManualAdvance,
            ),
            TourStep(
                id = TourStepId.FINISHED,
                type = TourStepType.SHOW_ONLY,
                tipResId = android.R.string.ok,
                completion = TourCompletion.ManualAdvance,
            ),
        ),
    )

    private fun allHiddenUiState(selectedId: String = BuiltinSoundCatalog.SYSTEM_DEFAULT.id) =
        SoundLibraryUiState(
            selectedSoundId = selectedId,
            selectedDisplayName = "System",
            soundEnabled = true,
            items = listOf(
                SoundLibraryItemUi(
                    asset = BuiltinSoundCatalog.SYSTEM_DEFAULT,
                    displayName = "System",
                    selected = true,
                    canHide = false,
                    previewAvailable = true,
                ),
            ),
            hiddenCount = BuiltinSoundCatalog.BUILTINS.size,
            currentlyPreviewingId = null,
            isListReady = true,
        )

    @Test
    fun allHidden_previewAndSelect_skipImmediately_hideTargetsRestore() = runTest {
        val store = FakeStore()
        val controller = TourController(store, clock = { 1L }, targetWaitTimeoutMs = 5_000L)
        var restoreCalls = 0
        var selectCalls = 0
        var hideCalls = 0
        val selectedBefore = BuiltinSoundCatalog.SYSTEM_DEFAULT.id

        composeRule.setContent {
            PraktikaTheme {
                CompositionLocalProvider(LocalTourController provides controller) {
                    SoundLibraryScreen(
                        uiState = allHiddenUiState(selectedBefore),
                        onSelect = { selectCalls++ },
                        onTogglePreview = {},
                        onHide = { hideCalls++ },
                        onRestoreAllHidden = { restoreCalls++ },
                        onStopPreview = {},
                        onBack = {},
                    )
                }
            }
        }

        controller.start(soundStepsDefinition())
        runCurrent()
        composeRule.waitForIdle()
        runCurrent()
        composeRule.waitForIdle()

        // Must not burn the 5s timeout path.
        advanceTimeBy(1_000L)
        runCurrent()

        assertEquals(TourStepId.HIDE_RESTORE_INFO, controller.session.value.currentStep?.id)
        assertEquals(
            listOf(TourStepId.PREVIEW_SOUND, TourStepId.CHOOSE_SOUND),
            controller.session.value.skippedStepIds,
        )
        assertEquals(
            TourSkipReasons.NO_VISIBLE_BUILTIN,
            controller.session.value.skipReasons[TourStepId.PREVIEW_SOUND],
        )
        assertEquals(
            TourSkipReasons.NO_VISIBLE_BUILTIN,
            controller.session.value.skipReasons[TourStepId.CHOOSE_SOUND],
        )
        assertEquals(
            R.string.tour_step_hide_restore_all_hidden,
            controller.session.value.tipOverrideResId,
        )

        composeRule.onNodeWithTag(SoundLibraryTestTags.SOUND_RESTORE_HIDDEN).performClick()
        assertEquals(0, restoreCalls)
        assertEquals(0, selectCalls)
        assertEquals(0, hideCalls)
        assertEquals(selectedBefore, allHiddenUiState(selectedBefore).selectedSoundId)

        // Second run must not keep stale tip override after restart.
        controller.start(soundStepsDefinition())
        runCurrent()
        composeRule.waitForIdle()
        runCurrent()
        assertEquals(TourStepId.HIDE_RESTORE_INFO, controller.session.value.currentStep?.id)
        assertFalse(
            controller.session.value.skippedStepIds.contains(TourStepId.HIDE_RESTORE_INFO),
        )
        assertTrue(controller.session.value.waitingForTarget.not() ||
            controller.session.value.currentStep?.id == TourStepId.HIDE_RESTORE_INFO)
    }
}
