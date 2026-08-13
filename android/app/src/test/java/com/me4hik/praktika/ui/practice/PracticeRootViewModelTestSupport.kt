// 05.08.2026 Stage 6 Boundary cursor by Me4Hik START - shared fakes for ViewModel tests
// 06.08.2026 Stage 11 Onboarding cursor by Me4Hik START - schedule/update fakes
// 06.08.2026 Stage 12 Notifications cursor by Me4Hik START - notification fakes
package com.me4hik.praktika.ui.practice

import androidx.lifecycle.SavedStateHandle
import com.me4hik.praktika.notification.NotificationDeliveryCapability
import com.me4hik.praktika.notification.ExactAlarmCapability
import com.me4hik.praktika.notification.ExactAlarmCapabilityPolicy
import com.me4hik.praktika.notification.NotificationPermissionPolicy
import com.me4hik.praktika.notification.NotificationPermissionUiState
import com.me4hik.praktika.data.cycle.CycleResult
import com.me4hik.praktika.data.cycle.ScheduleSlotUpdate
import com.me4hik.praktika.data.cycle.ScheduleUpdateResult
import com.me4hik.praktika.data.local.entity.PracticeStateEntity
import com.me4hik.praktika.data.local.entity.QuestionOccurrenceEntity
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.data.read.PracticeReadRepository
import com.me4hik.praktika.data.read.PracticeReadSnapshot
import com.me4hik.praktika.data.read.ScheduleReadRepository
import com.me4hik.praktika.data.read.ScheduleReadSnapshot
import com.me4hik.praktika.data.read.ScheduleSlotReadModel
import com.me4hik.praktika.data.preferences.SoundPreferenceRepository
import com.me4hik.praktika.ui.settings.UpdateScheduleCommand
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow

internal object PracticeRootViewModelTestSupport {
    const val ZONE = "Europe/Kiev"
    val DEFAULT_SCHEDULE_MINUTES = listOf(660, 900, 1140)

    fun notStartedSnapshot() = PracticeReadSnapshot(
        practiceState = baseState(isPracticeStarted = false),
        incompleteOccurrence = null,
    )

    fun startedSnapshot(
        status: QuestionOccurrenceStatus,
        paused: Boolean = false,
        cycleNumber: Int = 1,
    ): PracticeReadSnapshot {
        val planned = epochAt(11, 0)
        val until = epochAt(15, 0)
        return PracticeReadSnapshot(
            practiceState = baseState(isPracticeStarted = true, isPaused = paused),
            incompleteOccurrence = QuestionOccurrenceEntity(
                id = 10L,
                questionId = 1,
                questionTextSnapshot = "Question 1 text",
                cycleNumber = cycleNumber,
                cyclePosition = 1,
                scheduleSlotIndex = 1,
                plannedAtEpochMillis = planned,
                availableUntilEpochMillis = until,
                status = status,
                zoneId = ZONE,
            ),
        )
    }

    fun defaultScheduleSnapshot(
        minutes: List<Int> = DEFAULT_SCHEDULE_MINUTES,
    ): ScheduleReadSnapshot {
        return ScheduleReadSnapshot(
            slots = listOf(
                ScheduleSlotReadModel(1, minutes[0]),
                ScheduleSlotReadModel(2, minutes[1]),
                ScheduleSlotReadModel(3, minutes[2]),
            ),
        )
    }

    private fun baseState(isPracticeStarted: Boolean, isPaused: Boolean = false) = PracticeStateEntity(
        id = 1,
        isPracticeStarted = isPracticeStarted,
        isPaused = isPaused,
        practiceStartedAtEpochMillis = if (isPracticeStarted) epochAt(8, 0) else null,
        currentCycleNumber = if (isPracticeStarted) 1 else 0,
        nextCyclePosition = if (isPracticeStarted) 2 else 1,
        lastProcessedAtEpochMillis = null,
        pausedAtEpochMillis = null,
        activeZoneId = ZONE,
        seedVersion = 1,
    )

    private fun epochAt(hour: Int, minute: Int): Long {
        return ZonedDateTime.of(2026, 8, 5, hour, minute, 0, 0, ZoneId.of(ZONE))
            .toInstant()
            .toEpochMilli()
    }

    class FakePracticeReadRepository : PracticeReadRepository {
        private val snapshots = MutableSharedFlow<PracticeReadSnapshot>(replay = 1, extraBufferCapacity = 1)
        private var failure: Throwable? = null

        override fun observeSnapshot(): Flow<PracticeReadSnapshot> {
            return flow {
                failure?.let { throw it }
                snapshots.collect { emit(it) }
            }
        }

        fun emit(snapshot: PracticeReadSnapshot) {
            failure = null
            snapshots.tryEmit(snapshot)
        }

        fun failWith(exception: Throwable) {
            failure = exception
        }
    }

    class FakeScheduleReadRepository : ScheduleReadRepository {
        private val snapshots = MutableSharedFlow<ScheduleReadSnapshot>(replay = 1, extraBufferCapacity = 1)
        private var failure: Throwable? = null

        override fun observeSchedule(): Flow<ScheduleReadSnapshot> {
            return flow {
                failure?.let { throw it }
                snapshots.collect { emit(it) }
            }
        }

        fun emit(snapshot: ScheduleReadSnapshot) {
            failure = null
            snapshots.tryEmit(snapshot)
        }

        fun failWith(exception: Throwable) {
            failure = exception
        }
    }

    class RecordingStartPracticeCommand : StartPracticeCommand {
        var invocations = 0
        var exception: Exception? = null

        override suspend fun startPractice(): CycleResult {
            invocations += 1
            exception?.let { throw it }
            return CycleResult.PracticeStarted
        }
    }

    class RecordingUpdateScheduleCommand : UpdateScheduleCommand {
        var invocations = 0
        var lastUpdates: List<ScheduleSlotUpdate>? = null
        var exception: Exception? = null

        override suspend fun updateSchedule(updates: List<ScheduleSlotUpdate>): ScheduleUpdateResult {
            invocations += 1
            lastUpdates = updates
            exception?.let { throw it }
            return ScheduleUpdateResult.Success
        }
    }

    class FakeSoundPreferenceRepository : SoundPreferenceRepository {
        override val soundEnabled = MutableStateFlow(true)

        override suspend fun setSoundEnabled(enabled: Boolean) {
            soundEnabled.value = enabled
        }
    }

    class FakeNotificationPermissionPolicy(
        private val requested: MutableStateFlow<Boolean> = MutableStateFlow(false),
        private var uiState: NotificationPermissionUiState = NotificationPermissionUiState.NOT_REQUESTED,
    ) : NotificationPermissionPolicy {
        private val _permissionStateRevision = MutableStateFlow(0L)
        override val permissionStateRevision: StateFlow<Long> = _permissionStateRevision.asStateFlow()

        override fun notifyPermissionStateChanged(source: String) {
            _permissionStateRevision.value = _permissionStateRevision.value + 1L
        }

        override val permissionRequested: Flow<Boolean> = requested

        override suspend fun markPermissionRequested() {
            requested.value = true
        }

        override fun evaluateUiState(
            permissionRequested: Boolean,
            soundEnabled: Boolean,
        ): NotificationPermissionUiState = uiState

        fun setUiState(state: NotificationPermissionUiState) {
            uiState = state
        }

        override fun toDeliveryCapability(state: NotificationPermissionUiState): NotificationDeliveryCapability {
            return if (state == NotificationPermissionUiState.ENABLED) {
                NotificationDeliveryCapability.ENABLED
            } else {
                NotificationDeliveryCapability.DISABLED
            }
        }

        override fun createAppNotificationSettingsIntent() = android.content.Intent()

        override fun createChannelSettingsIntent(soundEnabled: Boolean) = android.content.Intent()

        override fun shouldRequestRuntimePermission(): Boolean = true

        override fun hasRuntimePermission(): Boolean = false

        override fun areAppNotificationsEnabled(): Boolean = false

        override fun shouldShowRequestPermissionRationale(): Boolean = false

        override fun isSelectedChannelEnabled(soundEnabled: Boolean): Boolean = true
    }

    class FakeExactAlarmCapabilityPolicy(
        private var capability: ExactAlarmCapability = ExactAlarmCapability.NOT_REQUIRED,
    ) : ExactAlarmCapabilityPolicy {
        private val _revision = MutableStateFlow(0L)
        override val capabilityStateRevision: StateFlow<Long> = _revision.asStateFlow()

        override fun currentCapability(): ExactAlarmCapability = capability

        fun setCapability(value: ExactAlarmCapability) {
            capability = value
            _revision.value += 1L
        }

        override fun notifyCapabilityChanged(source: String): Boolean = false

        override fun seedInitialCapability(source: String) = Unit

        override fun createRequestExactAlarmIntent() = android.content.Intent()

        override fun recordSettingsCta(source: String) = Unit
    }

    fun createViewModel(
        readRepository: FakePracticeReadRepository,
        scheduleReadRepository: FakeScheduleReadRepository,
        startCommand: RecordingStartPracticeCommand = RecordingStartPracticeCommand(),
        updateCommand: RecordingUpdateScheduleCommand = RecordingUpdateScheduleCommand(),
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
        commandDispatcher: kotlinx.coroutines.CoroutineDispatcher,
        notificationPermissionRepository: NotificationPermissionPolicy = FakeNotificationPermissionPolicy(),
        exactAlarmCapabilityRepository: ExactAlarmCapabilityPolicy = FakeExactAlarmCapabilityPolicy(),
        soundPreferenceRepository: FakeSoundPreferenceRepository = FakeSoundPreferenceRepository(),
        onRequestPostNotifications: () -> Unit = {},
        onOpenAppNotificationSettings: () -> Unit = {},
        onOpenChannelSettings: () -> Unit = {},
        onOpenExactAlarmSettings: () -> Unit = {},
    ): PracticeRootViewModel {
        return PracticeRootViewModel(
            readRepository = readRepository,
            scheduleReadRepository = scheduleReadRepository,
            startPracticeCommand = startCommand,
            updateScheduleCommand = updateCommand,
            notificationPermissionRepository = notificationPermissionRepository,
            exactAlarmCapabilityRepository = exactAlarmCapabilityRepository,
            soundPreferenceRepository = soundPreferenceRepository,
            onRequestPostNotifications = onRequestPostNotifications,
            onOpenAppNotificationSettings = onOpenAppNotificationSettings,
            onOpenChannelSettings = onOpenChannelSettings,
            onOpenExactAlarmSettings = onOpenExactAlarmSettings,
            savedStateHandle = savedStateHandle,
            timeFormatter = PracticeTimeFormatter(),
            commandDispatcher = commandDispatcher,
        )
    }
}
// 06.08.2026 Stage 12 Notifications cursor by Me4Hik END
// 06.08.2026 Stage 11 Onboarding cursor by Me4Hik END
// 05.08.2026 Stage 6 Boundary cursor by Me4Hik END
