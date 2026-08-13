// 10.08.2026 Post-release fixes cursor by Me4Hik START - permission UI refresh trigger
package com.me4hik.praktika.ui.practice

import android.content.Intent
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.notification.NotificationDeliveryCapability
import com.me4hik.praktika.notification.NotificationPermissionPolicy
import com.me4hik.praktika.notification.NotificationPermissionStateResolver
import com.me4hik.praktika.notification.NotificationPermissionUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class PracticeRootViewModelPermissionRefreshTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var readRepository: PracticeRootViewModelTestSupport.FakePracticeReadRepository
    private lateinit var scheduleReadRepository: PracticeRootViewModelTestSupport.FakeScheduleReadRepository
    private lateinit var permissionPolicy: RevisionAwareNotificationPermissionPolicy
    private lateinit var viewModel: PracticeRootViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        readRepository = PracticeRootViewModelTestSupport.FakePracticeReadRepository()
        scheduleReadRepository = PracticeRootViewModelTestSupport.FakeScheduleReadRepository()
        permissionPolicy = RevisionAwareNotificationPermissionPolicy()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun permissionRevision_recomputesNotificationCardFromLiveSystemSignals() = runTest {
        permissionPolicy.runtimeGranted = false
        permissionPolicy.appEnabled = false
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
        advanceUntilIdle()

        val staleState = viewModel.uiState.value as PracticeUiState.Started
        assertNotNull(staleState.notificationCard)

        permissionPolicy.runtimeGranted = true
        permissionPolicy.appEnabled = true
        permissionPolicy.channelEnabled = true
        advanceUntilIdle()

        val stillStale = viewModel.uiState.value as PracticeUiState.Started
        assertNotNull(stillStale.notificationCard)

        permissionPolicy.notifyPermissionStateChanged(source = "unit_test")
        advanceUntilIdle()

        val refreshed = viewModel.uiState.value as PracticeUiState.Started
        assertNull(refreshed.notificationCard)
    }

    private class RevisionAwareNotificationPermissionPolicy : NotificationPermissionPolicy {
        private val requested = MutableStateFlow(false)
        private val _permissionStateRevision = MutableStateFlow(0L)
        override val permissionStateRevision: StateFlow<Long> = _permissionStateRevision.asStateFlow()

        var runtimeGranted = false
        var appEnabled = false
        var channelEnabled = true

        override val permissionRequested: Flow<Boolean> = requested

        override fun notifyPermissionStateChanged(source: String) {
            _permissionStateRevision.value = _permissionStateRevision.value + 1L
        }

        override suspend fun markPermissionRequested() {
            requested.value = true
        }

        override fun evaluateUiState(
            permissionRequested: Boolean,
            soundEnabled: Boolean,
        ): NotificationPermissionUiState {
            return NotificationPermissionStateResolver.resolve(
                supportsRuntimePermission = true,
                runtimeGranted = runtimeGranted,
                permissionEverRequested = permissionRequested,
                shouldShowRequestPermissionRationale = false,
                appNotificationsEnabled = appEnabled,
                selectedChannelBlocked = !channelEnabled,
            )
        }

        override fun toDeliveryCapability(state: NotificationPermissionUiState): NotificationDeliveryCapability {
            return if (state == NotificationPermissionUiState.ENABLED) {
                NotificationDeliveryCapability.ENABLED
            } else {
                NotificationDeliveryCapability.DISABLED
            }
        }

        override fun createAppNotificationSettingsIntent(): Intent = Intent()

        override fun createChannelSettingsIntent(soundEnabled: Boolean): Intent = Intent()

        override fun shouldRequestRuntimePermission(): Boolean = true

        override fun hasRuntimePermission(): Boolean = runtimeGranted

        override fun areAppNotificationsEnabled(): Boolean = appEnabled

        override fun shouldShowRequestPermissionRationale(): Boolean = false

        override fun isSelectedChannelEnabled(soundEnabled: Boolean): Boolean = channelEnabled
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
