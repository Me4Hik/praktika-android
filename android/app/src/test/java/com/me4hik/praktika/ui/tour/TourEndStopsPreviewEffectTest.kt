package com.me4hik.praktika.ui.tour

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TourEndStopsPreviewEffectTest {
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

    @Test
    fun tourExit_whileComposed_invokesStopPreview() = runTest {
        val store = object : TesterToolsStore {
            override val unlocked: Flow<Boolean> = MutableStateFlow(true)
            override val lastTourResult: Flow<TourRunResult?> = MutableStateFlow(null)
            override suspend fun setUnlocked(unlocked: Boolean) = Unit
            override suspend fun saveTourResult(result: TourRunResult) = Unit
        }
        val controller = TourController(store, targetWaitTimeoutMs = 60_000L)
        var stopCalls by mutableIntStateOf(0)
        var previewActive by mutableIntStateOf(1)

        composeRule.setContent {
            CompositionLocalProvider(LocalTourController provides controller) {
                TourEndStopsPreviewEffect(
                    onStopPreview = {
                        stopCalls++
                        previewActive = 0
                    },
                )
            }
        }
        composeRule.waitForIdle()
        assertEquals(0, stopCalls)
        assertEquals(1, previewActive)

        controller.start(
            TourDefinition(
                id = "preview_exit",
                steps = listOf(
                    TourStep(
                        id = TourStepId.PREVIEW_SOUND,
                        type = TourStepType.TARGET_CLICK,
                        tipResId = android.R.string.ok,
                        targetId = TourTargetId.SOUND_LIBRARY_FIRST_VISIBLE_BUILTIN_PLAY,
                        completion = TourCompletion.TargetActivation,
                    ),
                ),
            ),
        )
        composeRule.waitForIdle()
        assertEquals(0, stopCalls)
        assertEquals(1, previewActive)

        controller.onExit(TourStepId.PREVIEW_SOUND)
        runCurrent()
        composeRule.waitForIdle()
        assertEquals(1, stopCalls)
        assertEquals(0, previewActive)
    }

    @Test
    fun inactiveTour_doesNotStopOnCompose() = runTest {
        val store = object : TesterToolsStore {
            override val unlocked: Flow<Boolean> = MutableStateFlow(true)
            override val lastTourResult: Flow<TourRunResult?> = MutableStateFlow(null)
            override suspend fun setUnlocked(unlocked: Boolean) = Unit
            override suspend fun saveTourResult(result: TourRunResult) = Unit
        }
        val controller = TourController(store)
        var stopCalls = 0
        composeRule.setContent {
            CompositionLocalProvider(LocalTourController provides controller) {
                TourEndStopsPreviewEffect(onStopPreview = { stopCalls++ })
            }
        }
        composeRule.waitForIdle()
        assertEquals(0, stopCalls)
    }
}
