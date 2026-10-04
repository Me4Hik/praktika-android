package com.me4hik.praktika.ui.practice

import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.notification.NotificationPermissionUiState
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PracticeRootViewModelUiEventTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var readRepository: PracticeRootViewModelTestSupport.FakePracticeReadRepository
    private lateinit var scheduleReadRepository: PracticeRootViewModelTestSupport.FakeScheduleReadRepository
    private lateinit var permissionPolicy: PracticeRootViewModelTestSupport.FakeNotificationPermissionPolicy
    private lateinit var viewModel: PracticeRootViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        readRepository = PracticeRootViewModelTestSupport.FakePracticeReadRepository()
        scheduleReadRepository = PracticeRootViewModelTestSupport.FakeScheduleReadRepository()
        permissionPolicy = PracticeRootViewModelTestSupport.FakeNotificationPermissionPolicy()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun notificationCta_notRequested_emitsRequestPostNotifications() = runTest {
        permissionPolicy.setUiState(NotificationPermissionUiState.NOT_REQUESTED)
        val events = seedAndCollect()

        viewModel.onNotificationCardActionClicked()
        advanceUntilIdle()

        assertEquals(listOf(PracticeRootUiEvent.RequestPostNotifications), events)
    }

    @Test
    fun notificationCta_runtimeRequired_emitsRequestPostNotifications() = runTest {
        permissionPolicy.setUiState(NotificationPermissionUiState.RUNTIME_PERMISSION_REQUIRED)
        val events = seedAndCollect()

        viewModel.onNotificationCardActionClicked()
        advanceUntilIdle()

        assertEquals(listOf(PracticeRootUiEvent.RequestPostNotifications), events)
    }

    @Test
    fun notificationCta_appDisabled_emitsOpenAppNotificationSettings() = runTest {
        permissionPolicy.setUiState(NotificationPermissionUiState.APP_NOTIFICATIONS_DISABLED)
        val events = seedAndCollect()

        viewModel.onNotificationCardActionClicked()
        advanceUntilIdle()

        assertEquals(listOf(PracticeRootUiEvent.OpenAppNotificationSettings), events)
    }

    @Test
    fun notificationCta_channelDisabled_emitsOpenChannelSettings() = runTest {
        permissionPolicy.setUiState(NotificationPermissionUiState.SELECTED_CHANNEL_DISABLED)
        val events = seedAndCollect()

        viewModel.onNotificationCardActionClicked()
        advanceUntilIdle()

        assertEquals(listOf(PracticeRootUiEvent.OpenChannelSettings), events)
    }

    @Test
    fun exactAlarmCta_emitsOpenExactAlarmSettings() = runTest {
        val events = seedAndCollect()

        viewModel.onExactAlarmCardActionClicked()
        advanceUntilIdle()

        assertEquals(listOf(PracticeRootUiEvent.OpenExactAlarmSettings), events)
    }

    @Test
    fun afterCollectorSwap_ctaUsesNewHandler_notStale() = runTest {
        permissionPolicy.setUiState(NotificationPermissionUiState.NOT_REQUESTED)
        seedStartedHome()
        advanceUntilIdle()

        val staleHits = AtomicInteger(0)
        val currentHits = AtomicInteger(0)
        val staleJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiEvents.collect {
                if (it is PracticeRootUiEvent.RequestPostNotifications) {
                    staleHits.incrementAndGet()
                }
            }
        }
        staleJob.cancel()

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiEvents.collect {
                if (it is PracticeRootUiEvent.RequestPostNotifications) {
                    currentHits.incrementAndGet()
                }
            }
        }

        viewModel.onNotificationCardActionClicked()
        advanceUntilIdle()

        assertEquals(0, staleHits.get())
        assertEquals(1, currentHits.get())
    }

    @Test
    fun afterCollectorSwap_settingsIntentsUseNewHandler() = runTest {
        permissionPolicy.setUiState(NotificationPermissionUiState.APP_NOTIFICATIONS_DISABLED)
        seedStartedHome()
        advanceUntilIdle()

        val staleHits = AtomicInteger(0)
        val currentHits = AtomicInteger(0)
        val staleJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiEvents.collect {
                if (it is PracticeRootUiEvent.OpenAppNotificationSettings) {
                    staleHits.incrementAndGet()
                }
            }
        }
        staleJob.cancel()

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiEvents.collect {
                if (it is PracticeRootUiEvent.OpenAppNotificationSettings) {
                    currentHits.incrementAndGet()
                }
            }
        }

        viewModel.onNotificationCardActionClicked()
        advanceUntilIdle()

        assertEquals(0, staleHits.get())
        assertEquals(1, currentHits.get())
    }

    @Test
    fun denialThenRepeatCta_emitsAgain() = runTest {
        permissionPolicy.setUiState(NotificationPermissionUiState.RUNTIME_PERMISSION_REQUIRED)
        seedStartedHome()
        advanceUntilIdle()
        val events = mutableListOf<PracticeRootUiEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiEvents.collect { events.add(it) }
        }

        viewModel.onNotificationCardActionClicked()
        advanceUntilIdle()
        viewModel.onNotificationCardActionClicked()
        advanceUntilIdle()

        assertEquals(
            listOf(
                PracticeRootUiEvent.RequestPostNotifications,
                PracticeRootUiEvent.RequestPostNotifications,
            ),
            events,
        )
    }

    @Test
    fun viewModelAndFactory_haveNoActivityBoundCallbackFields() {
        val vmFieldNames = PracticeRootViewModel::class.java.declaredFields.map { it.name }
        val factoryFieldNames = PracticeRootViewModelFactory::class.java.declaredFields.map { it.name }
        assertFalse(vmFieldNames.any { it.startsWith("onRequest") || it.startsWith("onOpen") })
        assertFalse(factoryFieldNames.any { it.startsWith("onRequest") || it.startsWith("onOpen") })
    }

    private fun seedStartedHome() {
        readRepository.emit(
            PracticeRootViewModelTestSupport.startedSnapshot(status = QuestionOccurrenceStatus.AVAILABLE),
        )
        scheduleReadRepository.emit(PracticeRootViewModelTestSupport.defaultScheduleSnapshot())
        viewModel = PracticeRootViewModelTestSupport.createViewModel(
            readRepository = readRepository,
            scheduleReadRepository = scheduleReadRepository,
            commandDispatcher = testDispatcher,
            notificationPermissionRepository = permissionPolicy,
        )
    }

    private fun kotlinx.coroutines.test.TestScope.seedAndCollect(): MutableList<PracticeRootUiEvent> {
        seedStartedHome()
        val events = mutableListOf<PracticeRootUiEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiEvents.collect { events.add(it) }
        }
        return events
    }
}
