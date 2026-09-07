package com.me4hik.praktika.ui.tour

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.me4hik.praktika.R
import com.me4hik.praktika.sound.BuiltinSoundCatalog
import com.me4hik.praktika.ui.settings.SoundLibraryItemUi
import com.me4hik.praktika.ui.settings.SoundLibraryScreen
import com.me4hik.praktika.ui.settings.SoundLibraryTestTags
import com.me4hik.praktika.ui.settings.SoundLibraryUiState
import com.me4hik.praktika.ui.settings.firstVisibleBuiltinIndex
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

/**
 * D1/D2: readiness must not treat ViewModel placeholder as all-hidden;
 * Skip/open into all-hidden must leave HIDE_INFO active.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SoundLibraryTourReadinessTest {

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
        id = "sound_readiness",
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

    private fun unreadyState() = SoundLibraryUiState(
        selectedSoundId = BuiltinSoundCatalog.SYSTEM_DEFAULT.id,
        selectedDisplayName = "System",
        soundEnabled = true,
        items = emptyList(),
        hiddenCount = 0,
        currentlyPreviewingId = null,
        isListReady = false,
    )

    private fun normalReadyState(): SoundLibraryUiState {
        val builtin = BuiltinSoundCatalog.BUILTINS.first()
        return SoundLibraryUiState(
            selectedSoundId = builtin.id,
            selectedDisplayName = "Builtin",
            soundEnabled = true,
            items = listOf(
                SoundLibraryItemUi(
                    asset = BuiltinSoundCatalog.SYSTEM_DEFAULT,
                    displayName = "System",
                    selected = false,
                    canHide = false,
                    previewAvailable = true,
                ),
                SoundLibraryItemUi(
                    asset = builtin,
                    displayName = "Builtin",
                    selected = true,
                    canHide = true,
                    previewAvailable = true,
                ),
            ),
            hiddenCount = 0,
            currentlyPreviewingId = null,
            isListReady = true,
        )
    }

    private fun allHiddenReadyState() = SoundLibraryUiState(
        selectedSoundId = BuiltinSoundCatalog.SYSTEM_DEFAULT.id,
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
    fun unreadyPlaceholder_doesNotSkipPreview() = runTest {
        val store = FakeStore()
        val controller = TourController(store, clock = { 1L }, targetWaitTimeoutMs = 5_000L)

        composeRule.setContent {
            PraktikaTheme {
                CompositionLocalProvider(LocalTourController provides controller) {
                    SoundLibraryScreen(
                        uiState = unreadyState(),
                        onSelect = {},
                        onTogglePreview = {},
                        onHide = {},
                        onRestoreAllHidden = {},
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
        advanceTimeBy(1_000L)
        runCurrent()

        assertEquals(TourStepId.PREVIEW_SOUND, controller.session.value.currentStep?.id)
        assertTrue(controller.session.value.skippedStepIds.isEmpty())
        assertFalse(controller.session.value.skipReasons.containsKey(TourStepId.PREVIEW_SOUND))
    }

    @Test
    fun unreadyThenNormalPublish_previewStays_onFirstBuiltin() = runTest {
        val store = FakeStore()
        val controller = TourController(store, clock = { 1L }, targetWaitTimeoutMs = 5_000L)
        var ui by mutableStateOf(unreadyState())

        composeRule.setContent {
            PraktikaTheme {
                CompositionLocalProvider(LocalTourController provides controller) {
                    SoundLibraryScreen(
                        uiState = ui,
                        onSelect = {},
                        onTogglePreview = {},
                        onHide = {},
                        onRestoreAllHidden = {},
                        onStopPreview = {},
                        onBack = {},
                    )
                }
            }
        }

        controller.start(soundStepsDefinition())
        runCurrent()
        composeRule.waitForIdle()
        assertEquals(TourStepId.PREVIEW_SOUND, controller.session.value.currentStep?.id)

        ui = normalReadyState()
        composeRule.waitForIdle()
        runCurrent()
        advanceTimeBy(500L)
        runCurrent()

        assertEquals(TourStepId.PREVIEW_SOUND, controller.session.value.currentStep?.id)
        assertTrue(controller.session.value.skippedStepIds.isEmpty())
        val idx = firstVisibleBuiltinIndex(ui.items)
        assertEquals(1, idx)
        assertTrue(ui.items[idx].asset.isBuiltin)
        assertFalse(ui.items[idx].asset.isSystemDefault)
    }

    @Test
    fun readyAllHidden_skipsPreviewAndSelect_hideStays_noTimeout() = runTest {
        val store = FakeStore()
        val controller = TourController(store, clock = { 1L }, targetWaitTimeoutMs = 5_000L)
        var restoreCalls = 0

        composeRule.setContent {
            PraktikaTheme {
                CompositionLocalProvider(LocalTourController provides controller) {
                    SoundLibraryScreen(
                        uiState = allHiddenReadyState(),
                        onSelect = {},
                        onTogglePreview = {},
                        onHide = {},
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
        advanceTimeBy(1_000L)
        runCurrent()

        assertEquals(TourStepId.HIDE_RESTORE_INFO, controller.session.value.currentStep?.id)
        assertEquals(
            listOf(TourStepId.PREVIEW_SOUND, TourStepId.CHOOSE_SOUND),
            controller.session.value.skippedStepIds,
        )
        assertFalse(controller.session.value.skippedStepIds.contains(TourStepId.HIDE_RESTORE_INFO))
        assertEquals(
            R.string.tour_step_hide_restore_all_hidden,
            controller.session.value.tipOverrideResId,
        )

        composeRule.onNodeWithTag(SoundLibraryTestTags.SOUND_RESTORE_HIDDEN).performClick()
        assertEquals(0, restoreCalls)
    }

    @Test
    fun skipNavStyle_unreadyThenAllHidden_hideInfoStays() = runTest {
        // Mimics Skip into library: tour already on PREVIEW before list is ready.
        val store = FakeStore()
        val controller = TourController(store, clock = { 1L }, targetWaitTimeoutMs = 5_000L)
        var ui by mutableStateOf(unreadyState())

        composeRule.setContent {
            PraktikaTheme {
                CompositionLocalProvider(LocalTourController provides controller) {
                    SoundLibraryScreen(
                        uiState = ui,
                        onSelect = {},
                        onTogglePreview = {},
                        onHide = {},
                        onRestoreAllHidden = {},
                        onStopPreview = {},
                        onBack = {},
                    )
                }
            }
        }

        controller.start(soundStepsDefinition())
        runCurrent()
        composeRule.waitForIdle()
        assertEquals(TourStepId.PREVIEW_SOUND, controller.session.value.currentStep?.id)

        // First empty emission must not cascade past HIDE.
        advanceTimeBy(200L)
        runCurrent()
        assertEquals(TourStepId.PREVIEW_SOUND, controller.session.value.currentStep?.id)

        ui = allHiddenReadyState()
        composeRule.waitForIdle()
        runCurrent()
        composeRule.waitForIdle()
        runCurrent()

        assertEquals(TourStepId.HIDE_RESTORE_INFO, controller.session.value.currentStep?.id)
        assertEquals(
            listOf(TourStepId.PREVIEW_SOUND, TourStepId.CHOOSE_SOUND),
            controller.session.value.skippedStepIds,
        )
        assertFalse(controller.session.value.skippedStepIds.contains(TourStepId.HIDE_RESTORE_INFO))
        assertEquals(
            R.string.tour_step_hide_restore_all_hidden,
            controller.session.value.tipOverrideResId,
        )

        // Recomposition must not skip HIDE again.
        ui = allHiddenReadyState().copy(selectedDisplayName = "System again")
        composeRule.waitForIdle()
        runCurrent()
        assertEquals(TourStepId.HIDE_RESTORE_INFO, controller.session.value.currentStep?.id)
        assertFalse(controller.session.value.skippedStepIds.contains(TourStepId.HIDE_RESTORE_INFO))
    }

    @Test
    fun secondRun_clearsStaleTipAndDoesNotInheritSkips() = runTest {
        val store = FakeStore()
        val controller = TourController(store, clock = { 1L }, targetWaitTimeoutMs = 5_000L)

        composeRule.setContent {
            PraktikaTheme {
                CompositionLocalProvider(LocalTourController provides controller) {
                    SoundLibraryScreen(
                        uiState = allHiddenReadyState(),
                        onSelect = {},
                        onTogglePreview = {},
                        onHide = {},
                        onRestoreAllHidden = {},
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
        assertEquals(TourStepId.HIDE_RESTORE_INFO, controller.session.value.currentStep?.id)

        controller.start(soundStepsDefinition())
        runCurrent()
        composeRule.waitForIdle()
        runCurrent()

        assertEquals(TourStepId.HIDE_RESTORE_INFO, controller.session.value.currentStep?.id)
        assertEquals(
            listOf(TourStepId.PREVIEW_SOUND, TourStepId.CHOOSE_SOUND),
            controller.session.value.skippedStepIds,
        )
        assertEquals(
            R.string.tour_step_hide_restore_all_hidden,
            controller.session.value.tipOverrideResId,
        )
    }

    @Test
    fun skipStepImmediately_ignoresSupersededStepId() = runTest {
        val store = FakeStore()
        val controller = TourController(store, clock = { 1L }, targetWaitTimeoutMs = 5_000L)
        controller.start(soundStepsDefinition())
        runCurrent()
        assertEquals(TourStepId.PREVIEW_SOUND, controller.session.value.currentStep?.id)

        controller.skipStepImmediately(
            TourSkipReasons.NO_VISIBLE_BUILTIN,
            expectedStepId = TourStepId.CHOOSE_SOUND,
        )
        assertEquals(TourStepId.PREVIEW_SOUND, controller.session.value.currentStep?.id)

        controller.skipStepImmediately(
            TourSkipReasons.NO_VISIBLE_BUILTIN,
            expectedStepId = TourStepId.PREVIEW_SOUND,
        )
        assertEquals(TourStepId.CHOOSE_SOUND, controller.session.value.currentStep?.id)
    }
}
