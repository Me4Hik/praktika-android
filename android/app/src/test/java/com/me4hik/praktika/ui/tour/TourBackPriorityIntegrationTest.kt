package com.me4hik.praktika.ui.tour

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import java.util.concurrent.atomic.AtomicInteger
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

/**
 * Catches device bug from Prompt №47: NavHost stole System Back on nested routes
 * while tour stayed active.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TourBackPriorityIntegrationTest {

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
        fun lastResult(): TourRunResult? = resultFlow.value
    }

    private lateinit var navController: NavHostController
    private lateinit var tourController: TourController
    private val store = FakeStore()
    private val stopPreviewCalls = AtomicInteger(0)

    private fun setTourNavContent() {
        stopPreviewCalls.set(0)
        tourController = TourController(store, targetWaitTimeoutMs = 60_000L)
        composeRule.setContent {
            navController = rememberNavController()
            TourHost(
                tourController = tourController,
                navController = navController,
            ) {
                NavHost(
                    navController = navController,
                    startDestination = ROUTE_HOME,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    composable(ROUTE_HOME) { ScreenLabel("home") }
                    composable(ROUTE_SETTINGS) { ScreenLabel("settings") }
                    composable(ROUTE_SOUND) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            ScreenLabel("sound")
                            TourEndStopsPreviewEffect(onStopPreview = { stopPreviewCalls.incrementAndGet() })
                        }
                    }
                    composable(ROUTE_ARCHIVE) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            ScreenLabel("archive")
                            // tourTarget publishes hole so NAV_BACK toolbar click passes structural cutout.
                            TextButton(
                                onClick = { navController.popBackStack() },
                                modifier = Modifier
                                    .testTag(TAG_TOOLBAR_BACK)
                                    .tourTarget(TourTargetId.ARCHIVE_BACK),
                            ) {
                                Text("toolbar_back")
                            }
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Composable
    private fun ScreenLabel(name: String) {
        Text(text = name, modifier = Modifier.testTag("screen_$name"))
    }

    private fun pressSystemBack() {
        composeRule.runOnIdle {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()
    }

    private fun navigateNestedToSound() {
        composeRule.runOnIdle {
            navController.navigate(ROUTE_SETTINGS)
            navController.navigate(ROUTE_SOUND)
        }
        composeRule.waitForIdle()
        assertEquals(ROUTE_SOUND, navController.currentDestination?.route)
    }

    @Test
    fun nestedOrdinarySystemBack_exitsTour_routeUnchanged() = runTest {
        setTourNavContent()
        navigateNestedToSound()
        tourController.start(
            TourDefinition(
                id = "nested_exit",
                steps = listOf(
                    ordinarySoundStep(),
                    finishedStep(),
                ),
            ),
        )
        runCurrent()
        composeRule.waitForIdle()
        assertTrue(tourController.session.value.isActive)

        pressSystemBack()
        runCurrent()

        assertEquals(TourStatus.Exited, tourController.session.value.status)
        assertFalse(tourController.session.value.isActive)
        assertEquals(TourStepId.PREVIEW_SOUND, tourController.session.value.exitStepId)
        assertTrue(tourController.session.value.skippedStepIds.isEmpty())
        assertEquals(ROUTE_SOUND, navController.currentDestination?.route)
        assertEquals(TourStepId.PREVIEW_SOUND.name, store.lastResult()?.exitStepId)
    }

    @Test
    fun navBackSystemBack_onePop_completesViaLeftRoute_noDoublePop() = runTest {
        setTourNavContent()
        composeRule.runOnIdle {
            navController.navigate(ROUTE_ARCHIVE)
        }
        composeRule.waitForIdle()
        assertEquals(ROUTE_ARCHIVE, navController.currentDestination?.route)

        tourController.start(
            TourDefinition(
                id = "nav_back_sys",
                steps = listOf(
                    navBackArchiveStep(),
                    finishedStep(),
                ),
            ),
        )
        runCurrent()
        composeRule.waitForIdle()
        tourController.onRouteChanged(ROUTE_ARCHIVE)
        runCurrent()

        pressSystemBack()
        runCurrent()
        composeRule.waitForIdle()

        assertEquals(ROUTE_HOME, navController.currentDestination?.route)
        assertEquals(TourStatus.Active, tourController.session.value.status)
        assertEquals(TourStepId.FINISHED, tourController.session.value.currentStep?.id)
        assertTrue(tourController.session.value.completedStepIds.contains(TourStepId.BACK_FROM_ARCHIVE))
        assertFalse(tourController.session.value.skippedStepIds.contains(TourStepId.BACK_FROM_ARCHIVE))

        // Next back on ordinary FINISHED exits without further pop.
        pressSystemBack()
        runCurrent()
        assertEquals(TourStatus.Exited, tourController.session.value.status)
        assertEquals(ROUTE_HOME, navController.currentDestination?.route)
    }

    @Test
    fun toolbarNavBack_onePop_sameLeftRouteCompletion() = runTest {
        setTourNavContent()
        composeRule.runOnIdle {
            navController.navigate(ROUTE_ARCHIVE)
        }
        composeRule.waitForIdle()

        tourController.start(
            TourDefinition(
                id = "nav_back_toolbar",
                steps = listOf(
                    navBackArchiveStep(),
                    finishedStep(),
                ),
            ),
        )
        runCurrent()
        composeRule.waitForIdle()
        tourController.onRouteChanged(ROUTE_ARCHIVE)
        runCurrent()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TAG_TOOLBAR_BACK).performClick()
        composeRule.waitForIdle()
        runCurrent()
        composeRule.waitForIdle()

        assertEquals(ROUTE_HOME, navController.currentDestination?.route)
        assertEquals(TourStatus.Active, tourController.session.value.status)
        assertEquals(TourStepId.FINISHED, tourController.session.value.currentStep?.id)
        assertTrue(tourController.session.value.completedStepIds.contains(TourStepId.BACK_FROM_ARCHIVE))
    }

    @Test
    fun soundLibraryPreview_ordinarySystemBack_exitsAndStopsPreview_routeUnchanged() = runTest {
        setTourNavContent()
        navigateNestedToSound()
        tourController.start(
            TourDefinition(
                id = "preview_exit",
                steps = listOf(
                    ordinarySoundStep(),
                    finishedStep(),
                ),
            ),
        )
        runCurrent()
        composeRule.waitForIdle()
        assertEquals(0, stopPreviewCalls.get())

        pressSystemBack()
        runCurrent()
        composeRule.waitForIdle()

        assertEquals(TourStatus.Exited, tourController.session.value.status)
        assertEquals(ROUTE_SOUND, navController.currentDestination?.route)
        assertEquals(1, stopPreviewCalls.get())
    }

    @Test
    fun afterExit_systemBackHandledByNav_again() = runTest {
        setTourNavContent()
        navigateNestedToSound()
        tourController.start(
            TourDefinition(
                id = "after_exit",
                steps = listOf(ordinarySoundStep(), finishedStep()),
            ),
        )
        runCurrent()
        composeRule.waitForIdle()

        pressSystemBack() // Exit tour, stay on sound
        runCurrent()
        assertEquals(TourStatus.Exited, tourController.session.value.status)
        assertEquals(ROUTE_SOUND, navController.currentDestination?.route)

        pressSystemBack() // Nav should pop sound → settings
        runCurrent()
        composeRule.waitForIdle()
        assertEquals(ROUTE_SETTINGS, navController.currentDestination?.route)
        assertFalse(tourController.session.value.isActive)
    }

    @Test
    fun secondRun_disablesNavBackAgain_whileActive() = runTest {
        setTourNavContent()
        navigateNestedToSound()
        val def = TourDefinition(
            id = "second_run",
            steps = listOf(ordinarySoundStep(), finishedStep()),
        )
        tourController.start(def)
        runCurrent()
        pressSystemBack()
        runCurrent()
        assertEquals(TourStatus.Exited, tourController.session.value.status)

        pressSystemBack()
        runCurrent()
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            navController.navigate(ROUTE_SOUND)
        }
        composeRule.waitForIdle()
        assertEquals(ROUTE_SOUND, navController.currentDestination?.route)

        tourController.start(def)
        runCurrent()
        composeRule.waitForIdle()
        assertTrue(tourController.session.value.isActive)

        pressSystemBack()
        runCurrent()
        assertEquals(TourStatus.Exited, tourController.session.value.status)
        assertEquals(ROUTE_SOUND, navController.currentDestination?.route)
        assertTrue(tourController.session.value.skippedStepIds.isEmpty())
    }

    private fun ordinarySoundStep() = TourStep(
        id = TourStepId.PREVIEW_SOUND,
        type = TourStepType.TARGET_CLICK,
        tipResId = android.R.string.ok,
        targetId = TourTargetId.SOUND_LIBRARY_FIRST_VISIBLE_BUILTIN_PLAY,
        expectedRoutePrefix = ROUTE_SOUND,
        completion = TourCompletion.TargetActivation,
    )

    private fun navBackArchiveStep() = TourStep(
        id = TourStepId.BACK_FROM_ARCHIVE,
        type = TourStepType.NAV_BACK,
        tipResId = android.R.string.ok,
        targetId = TourTargetId.ARCHIVE_BACK,
        expectedRoutePrefix = ROUTE_ARCHIVE,
        completion = TourCompletion.LeftRoute(ROUTE_ARCHIVE),
    )

    private fun finishedStep() = TourStep(
        id = TourStepId.FINISHED,
        type = TourStepType.SHOW_ONLY,
        tipResId = android.R.string.ok,
        completion = TourCompletion.ManualAdvance,
    )

    companion object {
        private const val ROUTE_HOME = "home"
        private const val ROUTE_SETTINGS = "settings"
        private const val ROUTE_SOUND = "settings/sound"
        private const val ROUTE_ARCHIVE = "archive"
        private const val TAG_TOOLBAR_BACK = "tour_test_toolbar_back"
    }
}
