// 05.08.2026 Main Screen cursor by Me4Hik START - root ViewModel практики
// 06.08.2026 Stage 11 Onboarding cursor by Me4Hik START - schedule draft и start-flow
// 06.08.2026 Stage 12 Notifications cursor by Me4Hik START - Home permission card
// 10.08.2026 Post-release fixes cursor by Me4Hik START - permission flow diagnostics
package com.me4hik.praktika.ui.practice

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.me4hik.praktika.data.cycle.CycleAlreadyStartedException
import com.me4hik.praktika.data.cycle.CycleCorruptionException
import com.me4hik.praktika.data.cycle.CycleInvalidScheduleException
import com.me4hik.praktika.data.cycle.ScheduleSlotUpdate
import com.me4hik.praktika.data.cycle.ScheduleValidationException
import com.me4hik.praktika.data.local.entity.QuestionOccurrenceEntity
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import com.me4hik.praktika.data.preferences.SoundPreferenceRepository
import com.me4hik.praktika.data.read.PracticeReadRepository
import com.me4hik.praktika.data.read.PracticeReadSnapshot
import com.me4hik.praktika.data.read.ScheduleReadRepository
import com.me4hik.praktika.data.read.ScheduleReadSnapshot
import com.me4hik.praktika.data.read.ScheduleSlotReadModel
import com.me4hik.praktika.data.seed.SeedCorruptionException
import com.me4hik.praktika.diagnostics.DiagnosticCategory
import com.me4hik.praktika.diagnostics.DiagnosticPermissionRecorder
import com.me4hik.praktika.diagnostics.DiagnosticsRecorder
import com.me4hik.praktika.diagnostics.NavigationRouteTracker
import com.me4hik.praktika.notification.ExactAlarmCapability
import com.me4hik.praktika.notification.ExactAlarmCapabilityPolicy
import com.me4hik.praktika.notification.NotificationPermissionPolicy
import com.me4hik.praktika.notification.NotificationPermissionUiState
import com.me4hik.praktika.ui.settings.UpdateScheduleCommand
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PracticeRootViewModel(
    private val readRepository: PracticeReadRepository,
    private val scheduleReadRepository: ScheduleReadRepository,
    private val startPracticeCommand: StartPracticeCommand,
    private val updateScheduleCommand: UpdateScheduleCommand,
    private val notificationPermissionRepository: NotificationPermissionPolicy,
    private val exactAlarmCapabilityRepository: ExactAlarmCapabilityPolicy,
    private val soundPreferenceRepository: SoundPreferenceRepository,
    private val onRequestPostNotifications: () -> Unit,
    private val onOpenAppNotificationSettings: () -> Unit,
    private val onOpenChannelSettings: () -> Unit,
    private val onOpenExactAlarmSettings: () -> Unit,
    private val savedStateHandle: SavedStateHandle,
    private val timeFormatter: PracticeTimeFormatter,
    private val commandDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val readGeneration = MutableStateFlow(0)
    private val draftRevision = MutableStateFlow(0)
    private val commandState = MutableStateFlow(CommandState())
    private var persistedMinutes: List<Int>? = null

    private val _uiState = MutableStateFlow<PracticeUiState>(PracticeUiState.Loading)
    val uiState: StateFlow<PracticeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            readGeneration
                .flatMapLatest {
                    combine(
                        observePracticeReadResults(),
                        observeScheduleReadResults(),
                        commandState,
                        combine(
                            draftRevision,
                            notificationPermissionRepository.permissionRequested,
                            soundPreferenceRepository.soundEnabled,
                            notificationPermissionRepository.permissionStateRevision,
                            exactAlarmCapabilityRepository.capabilityStateRevision,
                        ) { _, permissionRequested, soundEnabled, _, _ ->
                            permissionRequested to soundEnabled
                        },
                    ) { practiceResult, scheduleResult, command, permissionAndSound ->
                        val (permissionRequested, soundEnabled) = permissionAndSound
                        mapCombined(
                            practiceResult = practiceResult,
                            scheduleResult = scheduleResult,
                            command = command,
                            permissionRequested = permissionRequested,
                            soundEnabled = soundEnabled,
                        )
                    }
                }
                .collect { state ->
                    _uiState.value = state
                }
        }
    }

    fun onSlotTimeChanged(slotIndex: Int, timeOfDayMinutes: Int) {
        savedStateHandle[draftKeyForSlot(slotIndex)] = timeOfDayMinutes
        draftRevision.value += 1
    }

    fun onStartPracticeClicked() {
        if (commandState.value.isStarting) {
            return
        }
        val draftMinutes = currentDraftMinutesOrNull() ?: return
        if (!isDraftValid(draftMinutes)) {
            return
        }
        commandState.value = commandState.value.copy(
            isStarting = true,
            startError = null,
        )
        viewModelScope.launch {
            var startError: PracticeUiError? = null
            var fatalError: PracticeUiError? = null
            try {
                withContext(commandDispatcher) {
                    val persisted = persistedMinutes
                    if (persisted != null && draftMinutes != persisted) {
                        updateScheduleCommand.updateSchedule(buildScheduleUpdates(draftMinutes))
                        persistedMinutes = draftMinutes.toList()
                        markDraftCleanFromMinutes(draftMinutes)
                    }
                    startPracticeCommand.startPractice()
                }
            } catch (_: CycleAlreadyStartedException) {
                // idempotent — Room remains source of truth
            } catch (exception: ScheduleValidationException) {
                Log.e(TAG, "Start schedule validation failed", exception)
                startError = PracticeUiError.START_FAILED
            } catch (exception: CycleCorruptionException) {
                Log.e(TAG, "Start practice corruption", exception)
                fatalError = PracticeUiError.CORRUPTION
            } catch (exception: SeedCorruptionException) {
                Log.e(TAG, "Start practice seed corruption", exception)
                fatalError = PracticeUiError.CORRUPTION
            } catch (exception: CycleInvalidScheduleException) {
                Log.e(TAG, "Start practice invalid schedule", exception)
                startError = PracticeUiError.START_FAILED
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Log.e(TAG, "Start practice failed", exception)
                startError = PracticeUiError.START_FAILED
            } finally {
                val current = commandState.value
                commandState.value = current.copy(
                    isStarting = false,
                    startError = startError ?: current.startError,
                    fatalError = fatalError ?: current.fatalError,
                )
            }
        }
    }

    fun onNotificationCardActionClicked() {
        viewModelScope.launch {
            val soundEnabled = soundPreferenceRepository.soundEnabled.first()
            val permissionRequested = notificationPermissionRepository.permissionRequested.first()
            val uiState = notificationPermissionRepository.evaluateUiState(
                permissionRequested = permissionRequested,
                soundEnabled = soundEnabled,
            )
            if (DiagnosticsRecorder.isInitialized()) {
                DiagnosticsRecorder.get().record(
                    category = DiagnosticCategory.USER,
                    name = "button_tap",
                    metadata = mapOf(
                        "screen" to (NavigationRouteTracker.currentRoute ?: "home"),
                        "control_id" to "HOME_NOTIFICATION_ACTION",
                    ),
                )
                DiagnosticsRecorder.get().record(
                    category = DiagnosticCategory.PERMISSION,
                    name = "notification_permission_cta",
                    metadata = mapOf(
                        "screen" to (NavigationRouteTracker.currentRoute ?: "home"),
                        "card_state" to uiState.name,
                    ),
                )
            }
            when (uiState) {
                NotificationPermissionUiState.NOT_REQUESTED -> {
                    withContext(commandDispatcher) {
                        notificationPermissionRepository.markPermissionRequested()
                    }
                    recordPermissionRequestStarted(soundEnabled)
                    onRequestPostNotifications()
                }
                NotificationPermissionUiState.RUNTIME_PERMISSION_REQUIRED -> {
                    recordPermissionRequestStarted(soundEnabled)
                    onRequestPostNotifications()
                }
                NotificationPermissionUiState.APP_NOTIFICATIONS_DISABLED -> {
                    onOpenAppNotificationSettings()
                }
                NotificationPermissionUiState.SELECTED_CHANNEL_DISABLED -> {
                    onOpenChannelSettings()
                }
                NotificationPermissionUiState.ENABLED -> Unit
            }
        }
    }

    fun onExactAlarmCardActionClicked() {
        exactAlarmCapabilityRepository.recordSettingsCta("home_card")
        onOpenExactAlarmSettings()
    }

    fun retryRead() {
        readGeneration.value += 1
    }

    // 10.08.2026 Post-release fixes cursor by Me4Hik START - restore not-started draft authority
    fun discardOnboardingDraftFromPersistedSchedule() {
        val persisted = persistedMinutes ?: return
        syncDraftFromMinutes(persisted)
        draftRevision.value += 1
        val current = _uiState.value as? PracticeUiState.NotStarted ?: return
        val draftMinutes = currentDraftMinutesOrNull() ?: return
        val duplicateError = duplicateDraftError(draftMinutes)
        _uiState.value = current.copy(
            slots = buildOnboardingSlotUiModels(
                mapOf(
                    1 to draftMinutes[0],
                    2 to draftMinutes[1],
                    3 to draftMinutes[2],
                ),
            ),
            isScheduleDirty = false,
            isScheduleValid = duplicateError == null && isDraftValid(draftMinutes),
            scheduleError = duplicateError,
        )
    }
    // 10.08.2026 Post-release fixes cursor by Me4Hik END

    private fun observePracticeReadResults(): Flow<ReadSnapshotResult> {
        return flow {
            emit(ReadSnapshotResult.Loading)
            try {
                readRepository.observeSnapshot().collect { snapshot ->
                    emit(ReadSnapshotResult.Success(snapshot))
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                emit(ReadSnapshotResult.Failure(exception))
            }
        }
    }

    private fun observeScheduleReadResults(): Flow<ScheduleReadResult> {
        return flow {
            emit(ScheduleReadResult.Loading)
            try {
                scheduleReadRepository.observeSchedule().collect { snapshot ->
                    emit(ScheduleReadResult.Success(snapshot))
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                emit(ScheduleReadResult.Failure(exception))
            }
        }
    }

    private fun mapCombined(
        practiceResult: ReadSnapshotResult,
        scheduleResult: ScheduleReadResult,
        command: CommandState,
        permissionRequested: Boolean,
        soundEnabled: Boolean,
    ): PracticeUiState {
        command.fatalError?.let { return PracticeUiState.FatalError(it) }

        when (practiceResult) {
            ReadSnapshotResult.Loading -> return PracticeUiState.Loading
            is ReadSnapshotResult.Failure -> return mapFailure(practiceResult.exception)
            is ReadSnapshotResult.Success -> {
                if (practiceResult.snapshot.practiceState.isPracticeStarted) {
                    return mapStartedSnapshot(
                        snapshot = practiceResult.snapshot,
                        permissionRequested = permissionRequested,
                        soundEnabled = soundEnabled,
                    )
                }
            }
        }

        return when (scheduleResult) {
            ScheduleReadResult.Loading -> loadingNotStarted(command)
            is ScheduleReadResult.Failure -> mapFailure(scheduleResult.exception)
            is ScheduleReadResult.Success -> mapNotStarted(scheduleResult.snapshot, command)
        }
    }

    private fun loadingNotStarted(command: CommandState): PracticeUiState.NotStarted {
        return PracticeUiState.NotStarted(
            slots = emptyList(),
            isScheduleLoading = true,
            isScheduleDirty = false,
            isScheduleValid = false,
            scheduleError = null,
            isStarting = command.isStarting,
            startError = command.startError,
        )
    }

    private fun mapNotStarted(
        scheduleSnapshot: ScheduleReadSnapshot,
        command: CommandState,
    ): PracticeUiState {
        val persisted = extractPersistedMinutes(scheduleSnapshot.slots)
            ?: return PracticeUiState.FatalError(PracticeUiError.CORRUPTION)

        persistedMinutes = persisted

        if (!isDraftInitialized()) {
            initializeDraftFromMinutes(persisted)
        } else if (!isDraftDirty()) {
            syncDraftFromMinutes(persisted)
        }

        val draftMinutes = currentDraftMinutesOrNull()
            ?: return loadingNotStarted(command)

        val duplicateError = duplicateDraftError(draftMinutes)
        val isScheduleValid = duplicateError == null && isDraftValid(draftMinutes)
        val isScheduleDirty = draftMinutes != persisted

        return PracticeUiState.NotStarted(
            slots = buildOnboardingSlotUiModels(
                mapOf(
                    1 to draftMinutes[0],
                    2 to draftMinutes[1],
                    3 to draftMinutes[2],
                ),
            ),
            isScheduleLoading = false,
            isScheduleDirty = isScheduleDirty,
            isScheduleValid = isScheduleValid,
            scheduleError = duplicateError,
            isStarting = command.isStarting,
            startError = command.startError,
        )
    }

    private fun mapStartedSnapshot(
        snapshot: PracticeReadSnapshot,
        permissionRequested: Boolean,
        soundEnabled: Boolean,
    ): PracticeUiState {
        val occurrence = snapshot.incompleteOccurrence
            ?: return PracticeUiState.FatalError(PracticeUiError.CORRUPTION)
        return try {
            val occurrenceUi = toOccurrenceUiModel(occurrence)
            val content = when {
                snapshot.practiceState.isPaused && occurrence.status == QuestionOccurrenceStatus.SCHEDULED ->
                    MainContentUiState.PausedScheduled(occurrenceUi)
                snapshot.practiceState.isPaused && occurrence.status == QuestionOccurrenceStatus.AVAILABLE ->
                    MainContentUiState.PausedAvailable(occurrenceUi)
                occurrence.status == QuestionOccurrenceStatus.SCHEDULED ->
                    MainContentUiState.Scheduled(occurrenceUi)
                occurrence.status == QuestionOccurrenceStatus.AVAILABLE ->
                    MainContentUiState.Available(occurrenceUi)
                else -> return PracticeUiState.FatalError(PracticeUiError.CORRUPTION)
            }
            val permissionState = notificationPermissionRepository.evaluateUiState(
                permissionRequested = permissionRequested,
                soundEnabled = soundEnabled,
            )
            val notificationCard = when (permissionState) {
                NotificationPermissionUiState.ENABLED -> null
                NotificationPermissionUiState.NOT_REQUESTED,
                NotificationPermissionUiState.RUNTIME_PERMISSION_REQUIRED,
                -> HomeNotificationCardState.REQUEST_RUNTIME_PERMISSION
                NotificationPermissionUiState.APP_NOTIFICATIONS_DISABLED ->
                    HomeNotificationCardState.OPEN_APP_NOTIFICATION_SETTINGS
                NotificationPermissionUiState.SELECTED_CHANNEL_DISABLED ->
                    HomeNotificationCardState.OPEN_CHANNEL_SETTINGS
            }
            val exactAlarmCard = if (
                permissionState == NotificationPermissionUiState.ENABLED &&
                exactAlarmCapabilityRepository.currentCapability() == ExactAlarmCapability.SPECIAL_ACCESS_REQUIRED
            ) {
                HomeExactAlarmCardState.OPEN_EXACT_ALARM_SETTINGS
            } else {
                null
            }
            PracticeUiState.Started(
                content = content,
                notificationCard = notificationCard,
                exactAlarmCard = exactAlarmCard,
            )
        } catch (exception: PracticeTimeFormatException) {
            Log.e(TAG, "Time format failed", exception)
            PracticeUiState.FatalError(PracticeUiError.LOAD_FAILED)
        }
    }

    private fun mapFailure(exception: Throwable): PracticeUiState {
        return when (exception) {
            is CycleCorruptionException -> {
                Log.e(TAG, "Read corruption", exception)
                PracticeUiState.FatalError(PracticeUiError.CORRUPTION)
            }
            is SeedCorruptionException -> {
                Log.e(TAG, "Read seed corruption", exception)
                PracticeUiState.FatalError(PracticeUiError.CORRUPTION)
            }
            else -> {
                Log.e(TAG, "Read failed", exception)
                PracticeUiState.FatalError(PracticeUiError.LOAD_FAILED)
            }
        }
    }

    private fun extractPersistedMinutes(slots: List<ScheduleSlotReadModel>): List<Int>? {
        if (slots.size != SLOT_COUNT) {
            return null
        }
        val byIndex = slots.associateBy { it.slotIndex }
        val ordered = (1..SLOT_COUNT).map { index ->
            val slot = byIndex[index] ?: return null
            if (slot.timeOfDayMinutes !in MINUTES_RANGE) {
                return null
            }
            slot.timeOfDayMinutes
        }
        if (ordered.toSet().size != SLOT_COUNT) {
            return null
        }
        return ordered
    }

    private fun duplicateDraftError(draftMinutes: List<Int>): OnboardingScheduleError? {
        return if (draftMinutes.toSet().size != SLOT_COUNT) {
            OnboardingScheduleError.DUPLICATE_TIME
        } else {
            null
        }
    }

    private fun isDraftValid(draftMinutes: List<Int>): Boolean {
        if (draftMinutes.size != SLOT_COUNT) {
            return false
        }
        if (draftMinutes.any { it !in MINUTES_RANGE }) {
            return false
        }
        return draftMinutes.toSet().size == SLOT_COUNT
    }

    private fun isDraftDirty(): Boolean {
        val draft = currentDraftMinutesOrNull() ?: return false
        val persisted = persistedMinutes ?: return false
        return draft != persisted
    }

    private fun isDraftInitialized(): Boolean {
        return savedStateHandle.get<Boolean>(OnboardingSavedStateKeys.DRAFT_INITIALIZED) == true
    }

    private fun initializeDraftFromMinutes(minutes: List<Int>) {
        savedStateHandle[OnboardingSavedStateKeys.DRAFT_SLOT_1] = minutes[0]
        savedStateHandle[OnboardingSavedStateKeys.DRAFT_SLOT_2] = minutes[1]
        savedStateHandle[OnboardingSavedStateKeys.DRAFT_SLOT_3] = minutes[2]
        savedStateHandle[OnboardingSavedStateKeys.DRAFT_INITIALIZED] = true
    }

    private fun syncDraftFromMinutes(minutes: List<Int>) {
        savedStateHandle[OnboardingSavedStateKeys.DRAFT_SLOT_1] = minutes[0]
        savedStateHandle[OnboardingSavedStateKeys.DRAFT_SLOT_2] = minutes[1]
        savedStateHandle[OnboardingSavedStateKeys.DRAFT_SLOT_3] = minutes[2]
    }

    private fun markDraftCleanFromMinutes(minutes: List<Int>) {
        syncDraftFromMinutes(minutes)
    }

    private fun currentDraftMinutesOrNull(): List<Int>? {
        if (!isDraftInitialized()) {
            return null
        }
        return listOf(
            savedStateHandle.get<Int>(OnboardingSavedStateKeys.DRAFT_SLOT_1),
            savedStateHandle.get<Int>(OnboardingSavedStateKeys.DRAFT_SLOT_2),
            savedStateHandle.get<Int>(OnboardingSavedStateKeys.DRAFT_SLOT_3),
        ).map { value -> value ?: return null }
    }

    private fun buildScheduleUpdates(draftMinutes: List<Int>): List<ScheduleSlotUpdate> {
        return listOf(
            ScheduleSlotUpdate(1, draftMinutes[0]),
            ScheduleSlotUpdate(2, draftMinutes[1]),
            ScheduleSlotUpdate(3, draftMinutes[2]),
        )
    }

    private fun draftKeyForSlot(slotIndex: Int): String {
        return when (slotIndex) {
            1 -> OnboardingSavedStateKeys.DRAFT_SLOT_1
            2 -> OnboardingSavedStateKeys.DRAFT_SLOT_2
            3 -> OnboardingSavedStateKeys.DRAFT_SLOT_3
            else -> OnboardingSavedStateKeys.DRAFT_SLOT_1
        }
    }

    private fun toOccurrenceUiModel(occurrence: QuestionOccurrenceEntity): CurrentOccurrenceUiModel {
        return CurrentOccurrenceUiModel(
            occurrenceId = occurrence.id,
            questionId = occurrence.questionId,
            cyclePosition = occurrence.cyclePosition,
            questionText = occurrence.questionTextSnapshot,
            status = occurrence.status,
            plannedAtText = timeFormatter.format(occurrence.plannedAtEpochMillis, occurrence.zoneId),
            availableUntilText = timeFormatter.format(occurrence.availableUntilEpochMillis, occurrence.zoneId),
        )
    }

    private suspend fun recordPermissionRequestStarted(soundEnabled: Boolean) {
        if (!DiagnosticsRecorder.isInitialized()) {
            return
        }
        DiagnosticsRecorder.get().record(
            category = DiagnosticCategory.PERMISSION,
            name = "permission_request_started",
            metadata = mapOf("permission" to "POST_NOTIFICATIONS"),
        )
        DiagnosticPermissionRecorder.recordState(
            permissionRepository = notificationPermissionRepository,
            soundEnabled = soundEnabled,
            source = "permission_request_started",
        )
    }

    private data class CommandState(
        val isStarting: Boolean = false,
        val startError: PracticeUiError? = null,
        val fatalError: PracticeUiError? = null,
    )

    private sealed interface ReadSnapshotResult {
        data object Loading : ReadSnapshotResult
        data class Success(val snapshot: PracticeReadSnapshot) : ReadSnapshotResult
        data class Failure(val exception: Throwable) : ReadSnapshotResult
    }

    private sealed interface ScheduleReadResult {
        data object Loading : ScheduleReadResult
        data class Success(val snapshot: ScheduleReadSnapshot) : ScheduleReadResult
        data class Failure(val exception: Throwable) : ScheduleReadResult
    }

    private companion object {
        const val TAG = "PracticeRootViewModel"
        const val SLOT_COUNT = 3
        val MINUTES_RANGE = 0..1439
    }
}
// 06.08.2026 Stage 11 Onboarding cursor by Me4Hik END
// 05.08.2026 Main Screen cursor by Me4Hik END
