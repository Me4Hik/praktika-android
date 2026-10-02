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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TourControllerTest {

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
        fun lastResult(): TourRunResult? = resultFlow.value
    }

    private fun controller(store: FakeStore = FakeStore(), clock: () -> Long = { 1_000L }) =
        TourController(store, clock = clock, targetWaitTimeoutMs = 1_000L)

    @Test
    fun skip_recordsSkipped_notExit() = runTest {
        val store = FakeStore()
        val c = controller(store)
        c.start(shortDefinition())
        runCurrent()
        c.onSkip()
        val session = c.session.value
        assertTrue(session.skippedStepIds.contains(TourStepId.START_INTRO))
        assertNull(session.exitStepId)
        assertEquals(TourStatus.Active, session.status)
    }

    @Test
    fun exit_beforeCore_recordsCompletedFalse() = runTest {
        val store = FakeStore()
        val c = controller(store)
        c.start(shortDefinition())
        c.onExit(TourStepId.START_INTRO)
        runCurrent()
        val session = c.session.value
        assertEquals(TourStatus.Exited, session.status)
        assertEquals(TourStepId.START_INTRO, session.exitStepId)
        assertTrue(session.skippedStepIds.isEmpty())
        val saved = store.lastResult()!!
        assertEquals("START_INTRO", saved.exitStepId)
        assertFalse(saved.completedTour)
    }

    @Test
    fun next_completesShowOnly() = runTest {
        val c = controller()
        c.start(shortDefinition())
        runCurrent()
        c.onNext()
        runCurrent()
        assertEquals(TourStepId.CLICK_ARCHIVE, c.session.value.currentStep?.id)
        assertTrue(c.session.value.completedStepIds.contains(TourStepId.START_INTRO))
    }

    @Test
    fun routeMatch_completesTargetClick() = runTest {
        val c = controller()
        c.start(shortDefinition())
        runCurrent()
        c.onNext()
        runCurrent()
        assertTrue(c.session.value.actionReady)
        c.onRouteChanged("home")
        c.onRouteChanged("archive")
        runCurrent()
        assertEquals(TourStepId.FINISHED, c.session.value.currentStep?.id)
        assertTrue(c.session.value.completedStepIds.contains(TourStepId.CLICK_ARCHIVE))
    }

    @Test
    fun navBack_leftRoute_completesAndContinues() = runTest {
        val store = FakeStore()
        val c = controller(store)
        c.start(navBackDefinition())
        runCurrent()
        c.onRouteChanged("archive")
        c.onRouteChanged("home")
        runCurrent()
        assertEquals(TourStepId.FINISHED, c.session.value.currentStep?.id)
        assertTrue(c.session.value.completedStepIds.contains(TourStepId.BACK_FROM_ARCHIVE))
        assertEquals(TourStatus.Active, c.session.value.status)
    }

    @Test
    fun targetActivation_completesUserChoice() = runTest {
        val c = controller()
        c.start(userChoiceDefinition())
        runCurrent()
        assertTrue(c.session.value.actionReady)
        c.onTargetActivated(TourTargetId.SETTINGS_WORDING)
        runCurrent()
        assertEquals(TourStepId.FINISHED, c.session.value.currentStep?.id)
    }

    @Test
    fun anySoundActivation_completesChooseSound() = runTest {
        val c = controller()
        val job = launch { c.navCommands.collect { } }
        runCurrent()
        c.start(
            TourDefinition(
                id = "any_sound",
                steps = listOf(
                    TourStep(
                        id = TourStepId.CHOOSE_SOUND,
                        type = TourStepType.USER_CHOICE,
                        tipResId = android.R.string.ok,
                        targetId = TourTargetId.SOUND_LIBRARY_ANY_ITEM,
                        taskId = TourTaskId.SOUND,
                        expectedRoutePrefix = Routes.SOUND_LIBRARY,
                        completion = TourCompletion.TargetActivation,
                        onCompleteNav = TourNavAction.POP_ONCE,
                    ),
                    TourStep(
                        id = TourStepId.CHOOSE_DEFER,
                        type = TourStepType.USER_CHOICE,
                        tipResId = android.R.string.ok,
                        targetId = TourTargetId.NOTIFICATIONS_DEFER,
                        taskId = TourTaskId.DEFER,
                        expectedRoutePrefix = Routes.SETTINGS_NOTIFICATIONS,
                        completion = TourCompletion.TargetActivation,
                    ),
                ),
            ),
        )
        c.onRouteChanged(Routes.SOUND_LIBRARY)
        runCurrent()
        c.onTargetActivated(TourTargetId.SOUND_LIBRARY_FIRST_VISIBLE_BUILTIN_ITEM)
        runCurrent()
        assertEquals(TourStepId.CHOOSE_DEFER, c.session.value.currentStep?.id)
        job.cancel()
    }

    @Test
    fun targetTimeout_doesNotSkip_staysOnStep() = runTest {
        val c = controller()
        c.start(userChoiceDefinition())
        runCurrent()
        advanceTimeBy(1_500L)
        runCurrent()
        assertEquals(TourStepId.CHOOSE_WORDING, c.session.value.currentStep?.id)
        assertFalse(c.session.value.skippedStepIds.contains(TourStepId.CHOOSE_WORDING))
        assertEquals(
            TourSkipReasons.TARGET_UNAVAILABLE,
            c.session.value.skipReasons[TourStepId.CHOOSE_WORDING],
        )
        assertFalse(c.session.value.waitingForTarget)
    }

    @Test
    fun skipStepImmediately_withTaskId_soft_noAdvance() = runTest {
        val c = controller()
        c.start(
            TourDefinition(
                id = "choice_task",
                steps = listOf(
                    TourStep(
                        id = TourStepId.CHOOSE_WORDING,
                        type = TourStepType.USER_CHOICE,
                        tipResId = android.R.string.ok,
                        targetId = TourTargetId.SETTINGS_WORDING,
                        taskId = TourTaskId.WORDING,
                        expectedRoutePrefix = Routes.SETTINGS,
                        completion = TourCompletion.TargetActivation,
                    ),
                    TourStep(
                        id = TourStepId.FINISHED,
                        type = TourStepType.SHOW_ONLY,
                        tipResId = android.R.string.ok,
                        completion = TourCompletion.ManualAdvance,
                    ),
                ),
            ),
        )
        c.onRouteChanged(Routes.SETTINGS)
        runCurrent()
        c.skipStepImmediately(TourSkipReasons.NO_VISIBLE_BUILTIN)
        runCurrent()
        assertEquals(TourStepId.CHOOSE_WORDING, c.session.value.currentStep?.id)
        assertFalse(c.session.value.waitingForTarget)
        assertEquals(
            TourSkipReasons.NO_VISIBLE_BUILTIN,
            c.session.value.skipReasons[TourStepId.CHOOSE_WORDING],
        )
    }

    @Test
    fun finish_savesCompletedResult() = runTest {
        val store = FakeStore()
        val c = controller(store)
        c.start(shortDefinition())
        runCurrent()
        c.onNext()
        runCurrent()
        c.onRouteChanged("archive")
        runCurrent()
        c.onNext()
        runCurrent()
        assertEquals(TourStatus.Completed, c.session.value.status)
        assertTrue(store.lastResult()!!.completedTour)
    }

    @Test
    fun routeEquals_exactOnly() {
        assertTrue(TourController.routeEquals("settings", "settings"))
        assertFalse(TourController.routeEquals("settings/notifications", "settings"))
        assertTrue(TourController.routeInSection("settings/notifications", "settings"))
    }

    @Test
    fun skip_archiveTask_skipsAllThreeInternals_andPopsHome() = runTest {
        val c = controller()
        val nav = mutableListOf<TourNavAction>()
        val job = launch { c.navCommands.collect { nav.add(it.action) } }
        runCurrent()
        c.start(ExperimentalCoreTourV1.definition)
        c.onRouteChanged(Routes.HOME)
        c.onNext()
        runCurrent()
        assertEquals(TourStepId.CLICK_ARCHIVE, c.session.value.currentStep?.id)
        assertTrue(c.session.value.actionReady)
        c.onSkip()
        runCurrent()
        assertEquals(TourStepId.CLICK_SETTINGS, c.session.value.currentStep?.id)
        assertTrue(c.session.value.skippedStepIds.contains(TourStepId.CLICK_ARCHIVE))
        assertTrue(c.session.value.skippedStepIds.contains(TourStepId.CLICK_ARCHIVE_DAYS))
        assertTrue(c.session.value.skippedStepIds.contains(TourStepId.ARCHIVE_DAYS_CONTENT))
        assertEquals(TourTaskOutcome.UserSkipped, c.session.value.taskOutcomes[TourTaskId.ARCHIVE_DAYS])
        assertEquals(TourProgressDisplay.Action(2, 5), c.session.value.progressDisplay)
        assertTrue(nav.contains(TourNavAction.POP_TO_HOME))
        job.cancel()
    }

    @Test
    fun skip_soundTaskFromLibrary_popsAndAdvancesToDefer() = runTest {
        val store = FakeStore()
        val focused = controller(store)
        val nav2 = mutableListOf<TourNavAction>()
        val job2 = launch { focused.navCommands.collect { nav2.add(it.action) } }
        runCurrent()
        focused.start(
            TourDefinition(
                id = "sound_skip",
                steps = listOf(
                    TourStep(
                        id = TourStepId.CHOOSE_SOUND,
                        type = TourStepType.USER_CHOICE,
                        tipResId = android.R.string.ok,
                        targetId = TourTargetId.SOUND_LIBRARY_ANY_ITEM,
                        taskId = TourTaskId.SOUND,
                        expectedRoutePrefix = Routes.SOUND_LIBRARY,
                        completion = TourCompletion.TargetActivation,
                        onSkipNav = TourNavAction.POP_ONCE,
                    ),
                    TourStep(
                        id = TourStepId.CHOOSE_DEFER,
                        type = TourStepType.USER_CHOICE,
                        tipResId = android.R.string.ok,
                        targetId = TourTargetId.NOTIFICATIONS_DEFER,
                        taskId = TourTaskId.DEFER,
                        expectedRoutePrefix = Routes.SETTINGS_NOTIFICATIONS,
                        completion = TourCompletion.TargetActivation,
                    ),
                ),
            ),
        )
        focused.onRouteChanged(Routes.SOUND_LIBRARY)
        runCurrent()
        focused.onSkip()
        runCurrent()
        assertEquals(TourStepId.CHOOSE_DEFER, focused.session.value.currentStep?.id)
        assertEquals(listOf(TourStepId.CHOOSE_SOUND), focused.session.value.skippedStepIds)
        assertTrue(nav2.contains(TourNavAction.POP_ONCE))
        job2.cancel()
    }

    @Test
    fun skip_midArchive_skipsRemainingTaskStepsOnly() = runTest {
        val c = controller()
        val job = launch { c.navCommands.collect { } }
        runCurrent()
        c.start(ExperimentalCoreTourV1.definition)
        c.onRouteChanged(Routes.HOME)
        c.onNext()
        runCurrent()
        assertTrue(c.session.value.actionReady)
        c.onRouteChanged(Routes.ARCHIVE)
        runCurrent()
        assertEquals(TourStepId.CLICK_ARCHIVE_DAYS, c.session.value.currentStep?.id)
        c.onRouteChanged(Routes.ARCHIVE)
        runCurrent()
        assertTrue(c.session.value.actionReady)
        c.onSkip()
        runCurrent()
        assertEquals(TourStepId.CLICK_SETTINGS, c.session.value.currentStep?.id)
        assertTrue(c.session.value.skippedStepIds.contains(TourStepId.CLICK_ARCHIVE_DAYS))
        assertTrue(c.session.value.skippedStepIds.contains(TourStepId.ARCHIVE_DAYS_CONTENT))
        assertFalse(c.session.value.skippedStepIds.contains(TourStepId.CLICK_ARCHIVE))
        assertTrue(c.session.value.completedStepIds.contains(TourStepId.CLICK_ARCHIVE))
        job.cancel()
    }

    @Test
    fun task5Complete_persistsCoreCompleted_whileSessionActiveAtGate() = runTest {
        val store = FakeStore()
        val c = controller(store)
        c.start(coreMilestoneDefinition())
        runCurrent()
        c.onRouteChanged(Routes.SETTINGS_NOTIFICATIONS)
        runCurrent()
        c.onTargetActivated(TourTargetId.NOTIFICATIONS_DEFER)
        runCurrent()
        assertEquals(TourStepId.PHASE_GATE, c.session.value.currentStep?.id)
        assertEquals(TourStatus.Active, c.session.value.status)
        assertTrue(c.session.value.coreCompleted)
        assertTrue(store.saved.any { it.completedTour })
    }

    @Test
    fun gateFinish_jumpsToTourCompletion_thenDoneFinishes() = runTest {
        val store = FakeStore()
        val c = controller(store)
        val job = launch { c.navCommands.collect { } }
        runCurrent()
        c.start(coreMilestoneDefinition())
        runCurrent()
        c.onRouteChanged(Routes.SETTINGS_NOTIFICATIONS)
        runCurrent()
        c.onTargetActivated(TourTargetId.NOTIFICATIONS_DEFER)
        runCurrent()
        assertEquals(TourStepId.PHASE_GATE, c.session.value.currentStep?.id)
        assertTrue(c.session.value.coreCompleted)
        assertTrue(store.saved.any { it.completedTour })
        val savedAfterGate = store.saved.size

        c.onGateFinish()
        runCurrent()
        assertEquals(TourStatus.Active, c.session.value.status)
        assertEquals(TourStepId.TOUR_COMPLETION, c.session.value.currentStep?.id)
        assertEquals(TourUiPhase.COMPLETION, c.session.value.uiPhase)
        assertEquals(TourProgressDisplay.Hidden, c.session.value.progressDisplay)
        assertTrue(c.session.value.completedStepIds.contains(TourStepId.PHASE_GATE))
        assertFalse(c.session.value.completedStepIds.contains(TourStepId.OVERVIEW_HOME))
        assertEquals(savedAfterGate, store.saved.size)

        c.onNext()
        runCurrent()
        assertEquals(TourStatus.Completed, c.session.value.status)
        assertTrue(c.session.value.completedStepIds.contains(TourStepId.TOUR_COMPLETION))
        assertTrue(store.lastResult()!!.completedTour)
        job.cancel()
    }

    @Test
    fun gateOptional_thenFinishOnOverview_keepsCompletedTrue() = runTest {
        val store = FakeStore()
        val c = controller(store)
        val job = launch { c.navCommands.collect { } }
        runCurrent()
        c.start(coreMilestoneDefinition())
        c.onRouteChanged(Routes.SETTINGS_NOTIFICATIONS)
        runCurrent()
        c.onTargetActivated(TourTargetId.NOTIFICATIONS_DEFER)
        runCurrent()
        c.onGateContinueOptional()
        runCurrent()
        assertEquals(TourStepId.OVERVIEW_HOME, c.session.value.currentStep?.id)
        assertTrue(c.session.value.coreCompleted)
        c.onFinishTour()
        runCurrent()
        assertEquals(TourStatus.Completed, c.session.value.status)
        assertTrue(store.lastResult()!!.completedTour)
        job.cancel()
    }

    @Test
    fun gateContinue_overviewPath_reachesFinalThenDone() = runTest {
        val store = FakeStore()
        val c = controller(store)
        val job = launch { c.navCommands.collect { } }
        runCurrent()
        c.start(coreMilestoneDefinition())
        c.onRouteChanged(Routes.SETTINGS_NOTIFICATIONS)
        runCurrent()
        c.onTargetActivated(TourTargetId.NOTIFICATIONS_DEFER)
        runCurrent()
        c.onGateContinueOptional()
        runCurrent()
        assertEquals(TourStepId.OVERVIEW_HOME, c.session.value.currentStep?.id)
        c.onNext()
        runCurrent()
        assertEquals(TourStepId.OVERVIEW_PAUSE_BACKUP, c.session.value.currentStep?.id)
        assertEquals(TourProgressDisplay.Overview(2, 2), c.session.value.progressDisplay)
        assertTrue(c.session.value.currentStep!!.showsManualAdvanceCue())
        c.onNext()
        runCurrent()
        assertEquals(TourStepId.TOUR_COMPLETION, c.session.value.currentStep?.id)
        assertEquals(TourProgressDisplay.Hidden, c.session.value.progressDisplay)
        c.onNext()
        runCurrent()
        assertEquals(TourStatus.Completed, c.session.value.status)
        assertTrue(store.lastResult()!!.completedTour)
        job.cancel()
    }

    @Test
    fun exitAfterCore_doesNotWriteCompletedFalse() = runTest {
        val store = FakeStore()
        val c = controller(store)
        c.start(coreMilestoneDefinition())
        runCurrent()
        c.onRouteChanged(Routes.SETTINGS_NOTIFICATIONS)
        runCurrent()
        c.onTargetActivated(TourTargetId.NOTIFICATIONS_DEFER)
        runCurrent()
        c.onExit(TourStepId.PHASE_GATE)
        runCurrent()
        assertEquals(TourStatus.Exited, c.session.value.status)
        assertTrue(store.lastResult()!!.completedTour)
    }

    @Test
    fun onCompleteNav_emittedAfterArchiveDays() = runTest {
        val c = controller()
        val nav = mutableListOf<TourNavAction>()
        val job = launch { c.navCommands.collect { nav.add(it.action) } }
        runCurrent()
        c.start(
            TourDefinition(
                id = "archive_complete_nav",
                steps = listOf(
                    TourStep(
                        id = TourStepId.CLICK_ARCHIVE_DAYS,
                        type = TourStepType.TARGET_CLICK,
                        tipResId = android.R.string.ok,
                        targetId = TourTargetId.ARCHIVE_OPEN_DAYS,
                        taskId = TourTaskId.ARCHIVE_DAYS,
                        expectedRoutePrefix = Routes.ARCHIVE,
                        completion = TourCompletion.RouteMatch(Routes.ARCHIVE_DAYS),
                    ),
                    TourStep(
                        id = TourStepId.ARCHIVE_DAYS_CONTENT,
                        type = TourStepType.SHOW_ONLY,
                        tipResId = android.R.string.ok,
                        targetId = TourTargetId.ARCHIVE_DAYS_CONTENT,
                        taskId = TourTaskId.ARCHIVE_DAYS,
                        expectedRoutePrefix = Routes.ARCHIVE_DAYS,
                        completion = TourCompletion.ManualAdvance,
                        onCompleteNav = TourNavAction.POP_TO_HOME,
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
        c.onRouteChanged(Routes.ARCHIVE)
        runCurrent()
        assertTrue(c.session.value.actionReady)
        nav.clear()
        c.onRouteChanged(Routes.ARCHIVE_DAYS)
        runCurrent()
        assertEquals(TourStepId.ARCHIVE_DAYS_CONTENT, c.session.value.currentStep?.id)
        assertEquals(TourTaskOutcome.Pending, c.session.value.taskOutcomes[TourTaskId.ARCHIVE_DAYS])
        assertFalse(nav.contains(TourNavAction.POP_TO_HOME))
        // Same route settle arms 1C immediately (no POP yet).
        assertTrue(c.session.value.actionReady)
        assertFalse(c.session.value.currentStep!!.requiresActionCue())
        c.onNext()
        runCurrent()
        assertEquals(TourStepId.CLICK_SETTINGS, c.session.value.currentStep?.id)
        assertEquals(TourTaskOutcome.Completed, c.session.value.taskOutcomes[TourTaskId.ARCHIVE_DAYS])
        assertTrue(nav.contains(TourNavAction.POP_TO_HOME))
        assertFalse(c.session.value.actionReady) // Task2 not armed until Home
        c.onRouteChanged(Routes.HOME)
        runCurrent()
        assertTrue(c.session.value.actionReady)
        job.cancel()
    }

    @Test
    fun overviewEnter_emitsCompositeNav() = runTest {
        val c = controller()
        val nav = mutableListOf<TourNavAction>()
        val job = launch { c.navCommands.collect { nav.add(it.action) } }
        runCurrent()
        c.start(
            TourDefinition(
                id = "composite",
                steps = listOf(
                    TourStep(
                        id = TourStepId.OVERVIEW_PAUSE_BACKUP,
                        type = TourStepType.SHOW_ONLY,
                        tipResId = android.R.string.ok,
                        targetId = TourTargetId.SETTINGS_PAUSE_BACKUP,
                        expectedRoutePrefix = Routes.SETTINGS,
                        onEnterNavActions = listOf(
                            TourNavAction.POP_TO_HOME,
                            TourNavAction.NAVIGATE_SETTINGS,
                        ),
                        completion = TourCompletion.ManualAdvance,
                    ),
                ),
            ),
        )
        runCurrent()
        assertEquals(
            listOf(TourNavAction.POP_TO_HOME, TourNavAction.NAVIGATE_SETTINGS),
            nav.filter { it != TourNavAction.NONE },
        )
        job.cancel()
    }

    @Test
    fun tourCompletion_doneFinishesWithCompletedTour() = runTest {
        val store = FakeStore()
        val c = controller(store)
        val job = launch { c.navCommands.collect { } }
        runCurrent()
        c.start(
            TourDefinition(
                id = "final_only",
                steps = listOf(
                    TourStep(
                        id = TourStepId.TOUR_COMPLETION,
                        type = TourStepType.SHOW_ONLY,
                        tipResId = android.R.string.ok,
                        completion = TourCompletion.ManualAdvance,
                    ),
                ),
            ),
        )
        runCurrent()
        assertEquals(TourStepId.TOUR_COMPLETION, c.session.value.currentStep?.id)
        assertTrue(c.session.value.actionReady)
        assertEquals(TourUiPhase.COMPLETION, c.session.value.uiPhase)
        assertEquals(TourProgressDisplay.Hidden, c.session.value.progressDisplay)
        c.onNext()
        runCurrent()
        assertEquals(TourStatus.Completed, c.session.value.status)
        assertTrue(c.session.value.completedStepIds.contains(TourStepId.TOUR_COMPLETION))
        assertTrue(store.lastResult()!!.completedTour)
        job.cancel()
    }

    private fun coreMilestoneDefinition() = TourDefinition(
        id = "milestone",
        steps = listOf(
            TourStep(
                id = TourStepId.CHOOSE_DEFER,
                type = TourStepType.USER_CHOICE,
                tipResId = android.R.string.ok,
                targetId = TourTargetId.NOTIFICATIONS_DEFER,
                taskId = TourTaskId.DEFER,
                expectedRoutePrefix = Routes.SETTINGS_NOTIFICATIONS,
                completion = TourCompletion.TargetActivation,
            ),
            TourStep(
                id = TourStepId.PHASE_GATE,
                type = TourStepType.GATE,
                tipResId = android.R.string.ok,
                completion = TourCompletion.GateChoice,
            ),
            TourStep(
                id = TourStepId.OVERVIEW_HOME,
                type = TourStepType.SHOW_ONLY,
                tipResId = android.R.string.ok,
                completion = TourCompletion.ManualAdvance,
            ),
            TourStep(
                id = TourStepId.OVERVIEW_PAUSE_BACKUP,
                type = TourStepType.SHOW_ONLY,
                tipResId = android.R.string.ok,
                completion = TourCompletion.ManualAdvance,
            ),
            TourStep(
                id = TourStepId.TOUR_COMPLETION,
                type = TourStepType.SHOW_ONLY,
                tipResId = android.R.string.ok,
                completion = TourCompletion.ManualAdvance,
            ),
        ),
    )

    private fun shortDefinition() = TourDefinition(
        id = "test",
        steps = listOf(
            TourStep(
                id = TourStepId.START_INTRO,
                type = TourStepType.SHOW_ONLY,
                tipResId = android.R.string.ok,
                completion = TourCompletion.ManualAdvance,
            ),
            TourStep(
                id = TourStepId.CLICK_ARCHIVE,
                type = TourStepType.TARGET_CLICK,
                tipResId = android.R.string.ok,
                targetId = TourTargetId.HOME_ARCHIVE,
                completion = TourCompletion.RouteMatch("archive"),
            ),
            TourStep(
                id = TourStepId.FINISHED,
                type = TourStepType.SHOW_ONLY,
                tipResId = android.R.string.ok,
                completion = TourCompletion.ManualAdvance,
            ),
        ),
    )

    private fun navBackDefinition() = TourDefinition(
        id = "nav",
        steps = listOf(
            TourStep(
                id = TourStepId.BACK_FROM_ARCHIVE,
                type = TourStepType.NAV_BACK,
                tipResId = android.R.string.ok,
                targetId = TourTargetId.ARCHIVE_BACK,
                expectedRoutePrefix = "archive",
                completion = TourCompletion.LeftRoute("archive"),
            ),
            TourStep(
                id = TourStepId.FINISHED,
                type = TourStepType.SHOW_ONLY,
                tipResId = android.R.string.ok,
                completion = TourCompletion.ManualAdvance,
            ),
        ),
    )

    private fun userChoiceDefinition() = TourDefinition(
        id = "choice",
        steps = listOf(
            TourStep(
                id = TourStepId.CHOOSE_WORDING,
                type = TourStepType.USER_CHOICE,
                tipResId = android.R.string.ok,
                targetId = TourTargetId.SETTINGS_WORDING,
                completion = TourCompletion.TargetActivation,
            ),
            TourStep(
                id = TourStepId.FINISHED,
                type = TourStepType.SHOW_ONLY,
                tipResId = android.R.string.ok,
                completion = TourCompletion.ManualAdvance,
            ),
        ),
    )
}
