package com.me4hik.praktika.ui.tour

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

    private fun controller(store: FakeStore = FakeStore(), clock: () -> Long = { 1_000L }) =
        TourController(store, clock = clock, targetWaitTimeoutMs = 1_000L)

    @Test
    fun skip_recordsSkipped_notExit() = runTest {
        val store = FakeStore()
        val c = controller(store)
        c.start(shortDefinition())
        c.onSkip()
        val session = c.session.value
        assertTrue(session.skippedStepIds.contains(TourStepId.START_INTRO))
        assertNull(session.exitStepId)
        assertEquals(TourStatus.Active, session.status)
    }

    @Test
    fun exit_recordsExit_notSkipped() = runTest {
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
        assertTrue(saved.skippedStepIds.isEmpty())
    }

    @Test
    fun next_completesShowOnly() = runTest {
        val c = controller()
        c.start(shortDefinition())
        c.onNext()
        assertEquals(TourStepId.CLICK_ARCHIVE, c.session.value.currentStep?.id)
        assertTrue(c.session.value.completedStepIds.contains(TourStepId.START_INTRO))
    }

    @Test
    fun routeMatch_completesTargetClick() = runTest {
        val c = controller()
        c.start(shortDefinition())
        c.onNext()
        c.onRouteChanged("home")
        c.onRouteChanged("archive")
        assertEquals(TourStepId.FINISHED, c.session.value.currentStep?.id)
        assertTrue(c.session.value.completedStepIds.contains(TourStepId.CLICK_ARCHIVE))
    }

    @Test
    fun navBack_leftRoute_completesAndContinues() = runTest {
        val store = FakeStore()
        val c = controller(store)
        c.start(navBackDefinition())
        c.onRouteChanged("archive")
        c.onRouteChanged("home")
        assertEquals(TourStepId.FINISHED, c.session.value.currentStep?.id)
        assertTrue(c.session.value.completedStepIds.contains(TourStepId.BACK_FROM_ARCHIVE))
        assertEquals(TourStatus.Active, c.session.value.status)
    }

    @Test
    fun targetActivation_completesUserChoice() = runTest {
        val c = controller()
        c.start(userChoiceDefinition())
        c.onTargetActivated(TourTargetId.SETTINGS_WORDING)
        assertEquals(TourStepId.FINISHED, c.session.value.currentStep?.id)
    }

    @Test
    fun targetTimeout_skipsWithReason() = runTest {
        val c = controller()
        c.start(userChoiceDefinition())
        advanceTimeBy(1_500L)
        runCurrent()
        assertTrue(c.session.value.skippedStepIds.contains(TourStepId.CHOOSE_WORDING))
        assertEquals(TourSkipReasons.TARGET_UNAVAILABLE, c.session.value.skipReasons[TourStepId.CHOOSE_WORDING])
    }

    @Test
    fun skipStepImmediately_recordsReason_withoutWaitingTimeout() = runTest {
        val c = controller()
        c.start(userChoiceDefinition())
        c.skipStepImmediately(TourSkipReasons.NO_VISIBLE_BUILTIN)
        runCurrent()
        assertEquals(TourStepId.FINISHED, c.session.value.currentStep?.id)
        assertTrue(c.session.value.skippedStepIds.contains(TourStepId.CHOOSE_WORDING))
        assertEquals(
            TourSkipReasons.NO_VISIBLE_BUILTIN,
            c.session.value.skipReasons[TourStepId.CHOOSE_WORDING],
        )
        assertFalse(c.session.value.waitingForTarget)
    }

    @Test
    fun finish_savesCompletedResult() = runTest {
        val store = FakeStore()
        val c = controller(store)
        c.start(shortDefinition())
        c.onNext()
        c.onRouteChanged("archive")
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
    fun skip_clickArchive_emitsNavigateArchive_andDoesNotComplete() = runTest {
        assertSkipEmitsNav(
            skipped = TourStepId.CLICK_ARCHIVE,
            next = TourStepId.ARCHIVE_HUB_INTRO,
            expectedNav = TourNavAction.NAVIGATE_ARCHIVE,
            definition = skipPrepDefinition(
                skipStep = TourStep(
                    id = TourStepId.CLICK_ARCHIVE,
                    type = TourStepType.TARGET_CLICK,
                    tipResId = android.R.string.ok,
                    targetId = TourTargetId.HOME_ARCHIVE,
                    completion = TourCompletion.RouteMatch("archive"),
                    onSkipNav = TourNavAction.NAVIGATE_ARCHIVE,
                ),
                nextStep = TourStep(
                    id = TourStepId.ARCHIVE_HUB_INTRO,
                    type = TourStepType.SHOW_ONLY,
                    tipResId = android.R.string.ok,
                    targetId = TourTargetId.ARCHIVE_HUB,
                    completion = TourCompletion.ManualAdvance,
                ),
            ),
            afterSkipRoute = "archive",
        )
    }

    @Test
    fun skip_clickSettings_emitsNavigateSettings() = runTest {
        assertSkipEmitsNav(
            skipped = TourStepId.CLICK_SETTINGS,
            next = TourStepId.CHOOSE_WORDING,
            expectedNav = TourNavAction.NAVIGATE_SETTINGS,
            definition = skipPrepDefinition(
                skipStep = TourStep(
                    id = TourStepId.CLICK_SETTINGS,
                    type = TourStepType.TARGET_CLICK,
                    tipResId = android.R.string.ok,
                    targetId = TourTargetId.HOME_SETTINGS,
                    completion = TourCompletion.RouteMatch("settings"),
                    onSkipNav = TourNavAction.NAVIGATE_SETTINGS,
                ),
                nextStep = TourStep(
                    id = TourStepId.CHOOSE_WORDING,
                    type = TourStepType.USER_CHOICE,
                    tipResId = android.R.string.ok,
                    targetId = TourTargetId.SETTINGS_WORDING,
                    completion = TourCompletion.TargetActivation,
                ),
            ),
            afterSkipRoute = "settings",
        )
    }

    @Test
    fun skip_clickNotifications_emitsNavigateNotifications() = runTest {
        assertSkipEmitsNav(
            skipped = TourStepId.CLICK_NOTIFICATIONS,
            next = TourStepId.CLICK_SOUND_LIBRARY,
            expectedNav = TourNavAction.NAVIGATE_NOTIFICATIONS,
            definition = skipPrepDefinition(
                skipStep = TourStep(
                    id = TourStepId.CLICK_NOTIFICATIONS,
                    type = TourStepType.TARGET_CLICK,
                    tipResId = android.R.string.ok,
                    targetId = TourTargetId.SETTINGS_NOTIFICATIONS,
                    completion = TourCompletion.RouteMatch("settings/notifications"),
                    onSkipNav = TourNavAction.NAVIGATE_NOTIFICATIONS,
                ),
                nextStep = TourStep(
                    id = TourStepId.CLICK_SOUND_LIBRARY,
                    type = TourStepType.TARGET_CLICK,
                    tipResId = android.R.string.ok,
                    targetId = TourTargetId.NOTIFICATIONS_SOUND_LIBRARY,
                    completion = TourCompletion.RouteMatch("settings/sound"),
                ),
            ),
            afterSkipRoute = "settings/notifications",
        )
    }

    @Test
    fun skip_clickSoundLibrary_emitsNavigateSoundLibrary() = runTest {
        assertSkipEmitsNav(
            skipped = TourStepId.CLICK_SOUND_LIBRARY,
            next = TourStepId.PREVIEW_SOUND,
            expectedNav = TourNavAction.NAVIGATE_SOUND_LIBRARY,
            definition = skipPrepDefinition(
                skipStep = TourStep(
                    id = TourStepId.CLICK_SOUND_LIBRARY,
                    type = TourStepType.TARGET_CLICK,
                    tipResId = android.R.string.ok,
                    targetId = TourTargetId.NOTIFICATIONS_SOUND_LIBRARY,
                    completion = TourCompletion.RouteMatch("settings/sound"),
                    onSkipNav = TourNavAction.NAVIGATE_SOUND_LIBRARY,
                ),
                nextStep = TourStep(
                    id = TourStepId.PREVIEW_SOUND,
                    type = TourStepType.TARGET_CLICK,
                    tipResId = android.R.string.ok,
                    targetId = TourTargetId.SOUND_LIBRARY_FIRST_VISIBLE_BUILTIN_PLAY,
                    completion = TourCompletion.TargetActivation,
                ),
            ),
            afterSkipRoute = "settings/sound",
        )
    }

    @Test
    fun skip_backFromArchive_stillEmitsPopToHome() = runTest {
        assertSkipEmitsNav(
            skipped = TourStepId.BACK_FROM_ARCHIVE,
            next = TourStepId.FINISHED,
            expectedNav = TourNavAction.POP_TO_HOME,
            definition = TourDefinition(
                id = "back_skip",
                steps = listOf(
                    TourStep(
                        id = TourStepId.BACK_FROM_ARCHIVE,
                        type = TourStepType.NAV_BACK,
                        tipResId = android.R.string.ok,
                        targetId = TourTargetId.ARCHIVE_BACK,
                        completion = TourCompletion.LeftRoute("archive"),
                        onSkipNav = TourNavAction.POP_TO_HOME,
                    ),
                    TourStep(
                        id = TourStepId.FINISHED,
                        type = TourStepType.SHOW_ONLY,
                        tipResId = android.R.string.ok,
                        completion = TourCompletion.ManualAdvance,
                    ),
                ),
            ),
            afterSkipRoute = "home",
        )
    }

    @Test
    fun coreTour_routeDependentSteps_haveExplicitSkipNav() {
        val byId = ExperimentalCoreTourV1.definition.steps.associateBy { it.id }
        assertEquals(TourNavAction.NAVIGATE_ARCHIVE, byId.getValue(TourStepId.CLICK_ARCHIVE).onSkipNav)
        assertEquals(
            TourNavAction.NAVIGATE_ARCHIVE_DAYS,
            byId.getValue(TourStepId.CLICK_ARCHIVE_DAYS).onSkipNav,
        )
        assertEquals(TourNavAction.POP_ONCE, byId.getValue(TourStepId.BACK_FROM_DAYS).onSkipNav)
        assertEquals(TourNavAction.NAVIGATE_SETTINGS, byId.getValue(TourStepId.CLICK_SETTINGS).onSkipNav)
        assertEquals(
            TourNavAction.NAVIGATE_NOTIFICATIONS,
            byId.getValue(TourStepId.CLICK_NOTIFICATIONS).onSkipNav,
        )
        assertEquals(
            TourNavAction.NAVIGATE_SOUND_LIBRARY,
            byId.getValue(TourStepId.CLICK_SOUND_LIBRARY).onSkipNav,
        )
        assertEquals(TourNavAction.POP_TO_HOME, byId.getValue(TourStepId.BACK_FROM_ARCHIVE).onSkipNav)
        assertEquals(TourNavAction.POP_ONCE, byId.getValue(TourStepId.BACK_FROM_SOUND).onSkipNav)
    }

    @Test
    fun skip_archiveDays_emitsNavigateArchiveDays() = runTest {
        assertSkipEmitsNav(
            skipped = TourStepId.CLICK_ARCHIVE_DAYS,
            next = TourStepId.ARCHIVE_DAYS_CONTENT,
            expectedNav = TourNavAction.NAVIGATE_ARCHIVE_DAYS,
            definition = skipPrepDefinition(
                TourStep(
                    id = TourStepId.CLICK_ARCHIVE_DAYS,
                    type = TourStepType.TARGET_CLICK,
                    tipResId = android.R.string.ok,
                    targetId = TourTargetId.ARCHIVE_OPEN_DAYS,
                    completion = TourCompletion.RouteMatch("archive/days"),
                    onSkipNav = TourNavAction.NAVIGATE_ARCHIVE_DAYS,
                ),
                TourStep(
                    id = TourStepId.ARCHIVE_DAYS_CONTENT,
                    type = TourStepType.SHOW_ONLY,
                    tipResId = android.R.string.ok,
                    targetId = TourTargetId.ARCHIVE_DAYS_CONTENT,
                    completion = TourCompletion.ManualAdvance,
                ),
            ),
            afterSkipRoute = "archive/days",
        )
    }

    @Test
    fun skip_backFromDays_emitsPopOnce() = runTest {
        assertSkipEmitsNav(
            skipped = TourStepId.BACK_FROM_DAYS,
            next = TourStepId.FINISHED,
            expectedNav = TourNavAction.POP_ONCE,
            definition = TourDefinition(
                id = "days_back_skip",
                steps = listOf(
                    TourStep(
                        id = TourStepId.BACK_FROM_DAYS,
                        type = TourStepType.NAV_BACK,
                        tipResId = android.R.string.ok,
                        targetId = TourTargetId.ARCHIVE_BACK,
                        completion = TourCompletion.LeftRoute("archive/days"),
                        onSkipNav = TourNavAction.POP_ONCE,
                    ),
                    TourStep(
                        id = TourStepId.FINISHED,
                        type = TourStepType.SHOW_ONLY,
                        tipResId = android.R.string.ok,
                        completion = TourCompletion.ManualAdvance,
                    ),
                ),
            ),
            afterSkipRoute = "archive",
        )
    }

    @Test
    fun skip_exportAccordion_advancesWithoutNav_andDoesNotWaitTimeout() = runTest {
        val c = controller()
        c.start(
            TourDefinition(
                id = "export_skip",
                steps = listOf(
                    TourStep(
                        id = TourStepId.CLICK_EXPORT_ACCORDION,
                        type = TourStepType.TARGET_CLICK,
                        tipResId = android.R.string.ok,
                        targetId = TourTargetId.ARCHIVE_EXPORT_ACCORDION,
                        completion = TourCompletion.TargetActivation,
                    ),
                    TourStep(
                        id = TourStepId.EXPORT_INFO,
                        type = TourStepType.SHOW_ONLY,
                        tipResId = android.R.string.ok,
                        targetId = TourTargetId.ARCHIVE_EXPORT_SECTION,
                        completion = TourCompletion.ManualAdvance,
                    ),
                ),
            ),
        )
        c.onSkip()
        assertEquals(TourStepId.EXPORT_INFO, c.session.value.currentStep?.id)
        assertTrue(c.session.value.skippedStepIds.contains(TourStepId.CLICK_EXPORT_ACCORDION))
        assertEquals(TourStatus.Active, c.session.value.status)
    }

    private fun kotlinx.coroutines.test.TestScope.assertSkipEmitsNav(
        skipped: TourStepId,
        next: TourStepId,
        expectedNav: TourNavAction,
        definition: TourDefinition,
        afterSkipRoute: String,
    ) {
        val c = controller()
        val nav = mutableListOf<TourNavAction>()
        val collectJob = launch {
            c.navCommands.collect { nav.add(it) }
        }
        c.start(definition)
        runCurrent()
        c.onSkip()
        runCurrent()
        assertEquals(listOf(skipped), c.session.value.skippedStepIds)
        assertFalse(c.session.value.completedStepIds.contains(skipped))
        assertEquals(next, c.session.value.currentStep?.id)
        assertEquals(listOf(expectedNav), nav.filter { it != TourNavAction.NONE })
        c.onRouteChanged(afterSkipRoute)
        runCurrent()
        assertFalse(c.session.value.completedStepIds.contains(skipped))
        assertEquals(next, c.session.value.currentStep?.id)
        collectJob.cancel()
    }

    private fun skipPrepDefinition(skipStep: TourStep, nextStep: TourStep) = TourDefinition(
        id = "skip_prep",
        steps = listOf(skipStep, nextStep),
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
