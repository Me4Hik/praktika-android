package com.me4hik.praktika.ui.tour

import com.me4hik.praktika.navigation.Routes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
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
import org.junit.Test

/**
 * ActionTour-09 regression gate for the Settings-start / timeout / notifications crash chain.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TourReliabilityRegressionTest {

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
        private val unlockedFlow = MutableStateFlow(false)
        private val resultFlow = MutableStateFlow<TourRunResult?>(null)
        val saved = mutableListOf<TourRunResult>()
        override val unlocked: Flow<Boolean> = unlockedFlow
        override val lastTourResult: Flow<TourRunResult?> = resultFlow
        override suspend fun setUnlocked(unlocked: Boolean) {
            unlockedFlow.value = unlocked
        }
        override suspend fun saveTourResult(result: TourRunResult) {
            saved += result
            resultFlow.value = result
        }
    }

    private fun controller(store: FakeStore = FakeStore()) =
        TourController(store, clock = { 1_000L }, targetWaitTimeoutMs = 1_000L)

    @Test
    fun startFromSettings_clickArchiveNotArmedUntilHome() = runTest {
        val c = controller()
        val nav = mutableListOf<TourNavAction>()
        val job = launch { c.navCommands.collect { nav.add(it.action) } }
        runCurrent()
        c.start(ExperimentalCoreTourV1.definition)
        c.onRouteChanged(Routes.SETTINGS)
        runCurrent()
        c.onNext()
        runCurrent()
        assertEquals(TourStepId.CLICK_ARCHIVE, c.session.value.currentStep?.id)
        assertFalse(c.session.value.actionReady)
        assertTrue(nav.contains(TourNavAction.POP_TO_HOME))
        assertEquals(TourProgressDisplay.Action(1, 5), c.session.value.progressDisplay)
        // Still on settings — must not arm / timeout-skip.
        advanceTimeBy(2_000L)
        runCurrent()
        assertEquals(TourStepId.CLICK_ARCHIVE, c.session.value.currentStep?.id)
        assertFalse(c.session.value.actionReady)
        assertEquals(TourTaskOutcome.Pending, c.session.value.taskOutcomes[TourTaskId.ARCHIVE_DAYS])
        // Settle Home → arm.
        c.onRouteChanged(Routes.HOME)
        runCurrent()
        assertTrue(c.session.value.actionReady)
        assertEquals(TourStepId.CLICK_ARCHIVE, c.session.value.currentStep?.id)
        job.cancel()
    }

    @Test
    fun technicalTimeout_doesNotAdvanceTask_orCallSkipNav() = runTest {
        val c = controller()
        val nav = mutableListOf<TourNavAction>()
        val job = launch { c.navCommands.collect { nav.add(it.action) } }
        runCurrent()
        c.start(
            TourDefinition(
                id = "timeout",
                steps = listOf(
                    TourStep(
                        id = TourStepId.CLICK_ARCHIVE,
                        type = TourStepType.TARGET_CLICK,
                        tipResId = android.R.string.ok,
                        targetId = TourTargetId.HOME_ARCHIVE,
                        taskId = TourTaskId.ARCHIVE_DAYS,
                        expectedRoutePrefix = Routes.HOME,
                        completion = TourCompletion.RouteMatch(Routes.ARCHIVE),
                        onSkipNav = TourNavAction.POP_TO_HOME,
                    ),
                    TourStep(
                        id = TourStepId.CLICK_SETTINGS,
                        type = TourStepType.TARGET_CLICK,
                        tipResId = android.R.string.ok,
                        targetId = TourTargetId.HOME_SETTINGS,
                        taskId = TourTaskId.SCHEDULE,
                        expectedRoutePrefix = Routes.HOME,
                        completion = TourCompletion.RouteMatch(Routes.SETTINGS),
                    ),
                ),
            ),
        )
        c.onRouteChanged(Routes.HOME)
        runCurrent()
        nav.clear()
        advanceTimeBy(1_500L)
        runCurrent()
        assertEquals(TourStepId.CLICK_ARCHIVE, c.session.value.currentStep?.id)
        assertEquals(TourTaskOutcome.Pending, c.session.value.taskOutcomes[TourTaskId.ARCHIVE_DAYS])
        assertFalse(c.session.value.skippedStepIds.contains(TourStepId.CLICK_ARCHIVE))
        assertTrue(nav.isEmpty())
        assertFalse(c.session.value.waitingForTarget)
        assertEquals(
            TourSkipReasons.TARGET_UNAVAILABLE,
            c.session.value.skipReasons[TourStepId.CLICK_ARCHIVE],
        )
        job.cancel()
    }

    @Test
    fun staleSettingsRoute_doesNotAutoCompleteClickSettings() = runTest {
        val c = controller()
        val job = launch { c.navCommands.collect { } }
        runCurrent()
        c.start(
            TourDefinition(
                id = "stale",
                steps = listOf(
                    TourStep(
                        id = TourStepId.CLICK_ARCHIVE,
                        type = TourStepType.TARGET_CLICK,
                        tipResId = android.R.string.ok,
                        targetId = TourTargetId.HOME_ARCHIVE,
                        taskId = TourTaskId.ARCHIVE_DAYS,
                        expectedRoutePrefix = Routes.HOME,
                        completion = TourCompletion.RouteMatch(Routes.ARCHIVE),
                        onSkipNav = TourNavAction.POP_TO_HOME,
                    ),
                    TourStep(
                        id = TourStepId.CLICK_SETTINGS,
                        type = TourStepType.TARGET_CLICK,
                        tipResId = android.R.string.ok,
                        targetId = TourTargetId.HOME_SETTINGS,
                        taskId = TourTaskId.SCHEDULE,
                        expectedRoutePrefix = Routes.HOME,
                        completion = TourCompletion.RouteMatch(Routes.SETTINGS),
                    ),
                ),
            ),
        )
        // Tour still on Settings: Archive not armed; explicit Skip still allowed.
        c.onRouteChanged(Routes.SETTINGS)
        c.onNext()
        runCurrent()
        assertEquals(TourStepId.CLICK_ARCHIVE, c.session.value.currentStep?.id)
        assertFalse(c.session.value.actionReady)
        c.onSkip()
        runCurrent()
        assertEquals(TourStepId.CLICK_SETTINGS, c.session.value.currentStep?.id)
        // Stale settings must not arm/complete CLICK_SETTINGS (canonical = home).
        c.onRouteChanged(Routes.SETTINGS)
        runCurrent()
        assertEquals(TourStepId.CLICK_SETTINGS, c.session.value.currentStep?.id)
        assertFalse(c.session.value.actionReady)
        c.onRouteChanged(Routes.HOME)
        runCurrent()
        assertTrue(c.session.value.actionReady)
        assertEquals(TourStepId.CLICK_SETTINGS, c.session.value.currentStep?.id)
        c.onRouteChanged(Routes.SETTINGS)
        runCurrent()
        assertTrue(c.session.value.completedStepIds.contains(TourStepId.CLICK_SETTINGS))
        job.cancel()
    }

    @Test
    fun planNotifications_fromHome_includesSettingsParent() {
        assertEquals(
            listOf(TourNavAction.NAVIGATE_SETTINGS, TourNavAction.NAVIGATE_NOTIFICATIONS),
            TourNavPlanner.planNavigateToNotifications(Routes.HOME),
        )
        assertEquals(
            listOf(TourNavAction.NAVIGATE_NOTIFICATIONS),
            TourNavPlanner.planNavigateToNotifications(Routes.SETTINGS),
        )
        assertEquals(
            emptyList<TourNavAction>(),
            TourNavPlanner.planNavigateToNotifications(Routes.SETTINGS_NOTIFICATIONS),
        )
        assertEquals(
            listOf(TourNavAction.POP_ONCE),
            TourNavPlanner.planNavigateToNotifications(Routes.SOUND_LIBRARY),
        )
    }

    @Test
    fun expandNavigateNotifications_neverEmitsBareJumpFromHome() {
        val expanded = TourNavPlanner.expandNavAction(
            TourNavAction.NAVIGATE_NOTIFICATIONS,
            Routes.HOME,
        )
        assertEquals(
            listOf(TourNavAction.NAVIGATE_SETTINGS, TourNavAction.NAVIGATE_NOTIFICATIONS),
            expanded,
        )
        assertFalse(expanded == listOf(TourNavAction.NAVIGATE_NOTIFICATIONS))
    }

    @Test
    fun timeoutCascade_neverReachesGate_orPersistsCore() = runTest {
        val store = FakeStore()
        val c = controller(store)
        c.start(ExperimentalCoreTourV1.definition)
        c.onRouteChanged(Routes.SETTINGS)
        c.onNext()
        // Stay off-home: archive never arms; timeouts cannot advance.
        repeat(20) {
            advanceTimeBy(1_000L)
            runCurrent()
        }
        assertEquals(TourStepId.CLICK_ARCHIVE, c.session.value.currentStep?.id)
        assertFalse(c.session.value.coreCompleted)
        assertTrue(store.saved.none { it.completedTour })
        assertFalse(c.session.value.allActionTasksTerminal())
    }

    @Test
    fun explicitSkipAllTasks_allowsGateAndCore() = runTest {
        val store = FakeStore()
        val c = controller(store)
        val job = launch { c.navCommands.collect { } }
        runCurrent()
        c.start(ExperimentalCoreTourV1.definition)
        c.onRouteChanged(Routes.HOME)
        c.onNext()
        runCurrent()
        // Skip each action task with route settle to next canonical where needed.
        fun skipArmed(route: String) {
            c.onRouteChanged(route)
            runCurrent()
            assertTrue(c.session.value.actionReady)
            c.onSkip()
            runCurrent()
        }
        skipArmed(Routes.HOME) // Archive
        // After archive skip → CLICK_SETTINGS needs home
        skipArmed(Routes.HOME) // Schedule (whole task)
        // Wording on settings
        c.onRouteChanged(Routes.SETTINGS)
        runCurrent()
        c.onSkip()
        runCurrent()
        // Sound — start at settings for CLICK_NOTIFICATIONS
        c.onRouteChanged(Routes.SETTINGS)
        runCurrent()
        c.onSkip()
        runCurrent()
        // Defer on notifications
        c.onRouteChanged(Routes.SETTINGS_NOTIFICATIONS)
        runCurrent()
        c.onSkip()
        runCurrent()
        assertEquals(TourStepId.PHASE_GATE, c.session.value.currentStep?.id)
        assertTrue(c.session.value.allActionTasksTerminal())
        assertTrue(c.session.value.coreCompleted)
        assertTrue(store.saved.any { it.completedTour })
        job.cancel()
    }

    @Test
    fun actionCue_requiresActionReady() {
        val def = ExperimentalCoreTourV1.definition
        val idx = def.steps.indexOfFirst { it.id == TourStepId.CLICK_ARCHIVE }
        val disarmed = TourSessionState(
            status = TourStatus.Active,
            definitionId = def.id,
            steps = def.steps,
            stepIndex = idx,
            actionReady = false,
        )
        val armed = disarmed.copy(actionReady = true)
        assertFalse(disarmed.showActionCue)
        assertTrue(armed.showActionCue)
    }

    @Test
    fun phaseA_canonicalRoutes_matchContract() {
        val byId = ExperimentalCoreTourV1.definition.steps.associateBy { it.id }
        assertEquals(Routes.HOME, byId.getValue(TourStepId.CLICK_ARCHIVE).expectedRoutePrefix)
        assertEquals(Routes.ARCHIVE, byId.getValue(TourStepId.CLICK_ARCHIVE_DAYS).expectedRoutePrefix)
        assertEquals(
            Routes.ARCHIVE_DAYS,
            byId.getValue(TourStepId.ARCHIVE_DAYS_CONTENT).expectedRoutePrefix,
        )
        assertEquals(TourNavAction.NONE, byId.getValue(TourStepId.CLICK_ARCHIVE_DAYS).onCompleteNav)
        assertEquals(
            TourNavAction.POP_TO_HOME,
            byId.getValue(TourStepId.ARCHIVE_DAYS_CONTENT).onCompleteNav,
        )
        assertEquals(Routes.HOME, byId.getValue(TourStepId.CLICK_SETTINGS).expectedRoutePrefix)
        assertEquals(Routes.SETTINGS, byId.getValue(TourStepId.SCHEDULE_INFO).expectedRoutePrefix)
        assertEquals(Routes.SETTINGS, byId.getValue(TourStepId.CHOOSE_WORDING).expectedRoutePrefix)
        assertEquals(Routes.SETTINGS, byId.getValue(TourStepId.CLICK_NOTIFICATIONS).expectedRoutePrefix)
        assertEquals(
            Routes.SETTINGS_NOTIFICATIONS,
            byId.getValue(TourStepId.CLICK_SOUND_LIBRARY).expectedRoutePrefix,
        )
        assertEquals(Routes.SOUND_LIBRARY, byId.getValue(TourStepId.CHOOSE_SOUND).expectedRoutePrefix)
        assertEquals(
            Routes.SETTINGS_NOTIFICATIONS,
            byId.getValue(TourStepId.CHOOSE_DEFER).expectedRoutePrefix,
        )
        assertEquals(TourNavAction.POP_TO_HOME, byId.getValue(TourStepId.CLICK_ARCHIVE).onEnterNav)
    }
}
