package com.me4hik.praktika.ui.settings

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.me4hik.praktika.R
import com.me4hik.praktika.data.backup.settings.BackupNowPhysicalResult
import com.me4hik.praktika.data.backup.settings.BackupNowResult
import com.me4hik.praktika.data.backup.settings.BackupReconnectResult
import com.me4hik.praktika.data.backup.settings.BackupSettingsAbandonResult
import com.me4hik.praktika.data.backup.settings.BackupSettingsDisableResult
import com.me4hik.praktika.data.backup.settings.BackupSettingsOperationalState
import com.me4hik.praktika.data.backup.settings.BackupSettingsSetupCommitResult
import com.me4hik.praktika.data.backup.settings.BackupSettingsSetupInspectResult
import com.me4hik.praktika.data.backup.settings.BackupSettingsSetupVerifyResult
import com.me4hik.praktika.data.backup.setup.SetupCandidateClassification
import com.me4hik.praktika.data.cycle.ScheduleUpdateResult
import com.me4hik.praktika.data.local.entity.PracticeStateEntity
import com.me4hik.praktika.data.preferences.DeferDurationOptions
import com.me4hik.praktika.data.preferences.DeferDurationPreferenceRepository
import com.me4hik.praktika.data.preferences.SoundPreferenceRepository
import kotlinx.coroutines.flow.flowOf
import com.me4hik.praktika.data.read.PracticeReadRepository
import com.me4hik.praktika.data.read.PracticeReadSnapshot
import com.me4hik.praktika.data.read.ScheduleReadRepository
import com.me4hik.praktika.data.read.ScheduleReadSnapshot
import com.me4hik.praktika.data.read.ScheduleSlotReadModel
import com.me4hik.praktika.diagnostics.BugReportSendResult
import com.me4hik.praktika.diagnostics.DiagnosticReportSubmitter
import com.me4hik.praktika.notification.NotificationDeliveryCapability
import com.me4hik.praktika.notification.NotificationPermissionPolicy
import com.me4hik.praktika.notification.NotificationPermissionUiState
import com.me4hik.praktika.notification.NotificationSyncReason
import com.me4hik.praktika.notification.NotificationSyncRequester
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsBackupViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var backup: RecordingBackupSettingsActions
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        backup = RecordingBackupSettingsActions()
        viewModel = SettingsViewModel(
            scheduleReadRepository = object : ScheduleReadRepository {
                override fun observeSchedule(): Flow<ScheduleReadSnapshot> = MutableStateFlow(
                    ScheduleReadSnapshot(
                        listOf(
                            ScheduleSlotReadModel(1, 660),
                            ScheduleSlotReadModel(2, 900),
                            ScheduleSlotReadModel(3, 1140),
                        ),
                    ),
                )
            },
            practiceReadRepository = object : PracticeReadRepository {
                private val snapshot = PracticeReadSnapshot(
                    practiceState = PracticeStateEntity(
                        id = 1,
                        isPracticeStarted = true,
                        isPaused = false,
                        practiceStartedAtEpochMillis = 1L,
                        currentCycleNumber = 1,
                        nextCyclePosition = 2,
                        lastProcessedAtEpochMillis = 1L,
                        pausedAtEpochMillis = null,
                        activeZoneId = "UTC",
                        seedVersion = 1,
                    ),
                    incompleteOccurrence = null,
                )
                override fun observeSnapshot(): Flow<PracticeReadSnapshot> = MutableStateFlow(snapshot)
                override suspend fun readSnapshot(): PracticeReadSnapshot = snapshot
            },
            updateScheduleCommand = UpdateScheduleCommand { ScheduleUpdateResult.Success },
            pausePracticeCommand = PausePracticeCommand {
                com.me4hik.praktika.data.cycle.CycleResult.PauseEnabled
            },
            resumePracticeCommand = ResumePracticeCommand {
                com.me4hik.praktika.data.cycle.CycleResult.PracticeResumed
            },
            soundPreferenceRepository = object : SoundPreferenceRepository {
                private val state = MutableStateFlow(true)
                override val soundEnabled: Flow<Boolean> = state
                override suspend fun setSoundEnabled(enabled: Boolean) {
                    state.value = enabled
                }
            },
            deferDurationPreferenceRepository = object : DeferDurationPreferenceRepository {
                override val deferDurationMinutes: Flow<Int> = flowOf(DeferDurationOptions.DEFAULT_MINUTES)
                override suspend fun setDeferDurationMinutes(minutes: Int) = Unit
            },
            notificationPermissionRepository = object : NotificationPermissionPolicy {
                private val revision = MutableStateFlow(0L)
                override val permissionStateRevision: StateFlow<Long> = revision.asStateFlow()
                override fun notifyPermissionStateChanged(source: String) = Unit
                override val permissionRequested = MutableStateFlow(false)
                override suspend fun markPermissionRequested() = Unit
                override fun evaluateUiState(permissionRequested: Boolean, soundEnabled: Boolean) =
                    NotificationPermissionUiState.ENABLED
                override fun toDeliveryCapability(state: NotificationPermissionUiState) =
                    NotificationDeliveryCapability.ENABLED
                override fun createAppNotificationSettingsIntent() = android.content.Intent()
                override fun createChannelSettingsIntent(soundEnabled: Boolean) = android.content.Intent()
                override fun shouldRequestRuntimePermission(): Boolean = false
                override fun hasRuntimePermission(): Boolean = true
                override fun areAppNotificationsEnabled(): Boolean = true
                override fun shouldShowRequestPermissionRationale(): Boolean = false
                override fun isSelectedChannelEnabled(soundEnabled: Boolean): Boolean = true
            },
            notificationSyncRequester = object : NotificationSyncRequester {
                override suspend fun requestSync(reason: NotificationSyncReason) = Unit
            },
            diagnosticReportSubmitter = object : DiagnosticReportSubmitter {
                override suspend fun submitManualReport(testerComment: String?): BugReportSendResult =
                    BugReportSendResult.SAVED_LOCALLY_NO_DSN
            },
            savedStateHandle = SavedStateHandle(),
            backupSettingsActions = backup,
            commandDispatcher = testDispatcher,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun notConfigured_showsIdleSetupReady() = runTest {
        advanceUntilIdle()
        val content = viewModel.uiState.value as SettingsUiState.Content
        assertTrue(content.backup.operationalStatus is BackupSettingsOperationalState.NotConfigured)
        assertEquals(BackupSettingsOperation.Idle, content.backup.operation)
    }

    @Test
    fun healthyStatus_flowsIntoContent() = runTest {
        backup.emitStatus(BackupSettingsOperationalState.Healthy(1_700_000_000_000L))
        advanceUntilIdle()
        val content = viewModel.uiState.value as SettingsUiState.Content
        val healthy = content.backup.operationalStatus as BackupSettingsOperationalState.Healthy
        assertEquals(1_700_000_000_000L, healthy.lastSuccessAtEpochMillis)
    }

    @Test
    fun setupDisclosure_thenPickerEffect() = runTest {
        advanceUntilIdle()
        val effects = mutableListOf<SettingsBackupEffect>()
        val job = launch { viewModel.backupEffects.collect { effects.add(it) } }
        viewModel.onBackupSetupRequested()
        advanceUntilIdle()
        assertEquals(
            BackupSettingsOperation.AwaitingDisclosureSetup,
            (viewModel.uiState.value as SettingsUiState.Content).backup.operation,
        )
        viewModel.onBackupDisclosureConfirmed()
        advanceUntilIdle()
        assertEquals(1, effects.size)
        assertEquals(BackupPickerMode.SETUP, (effects.single() as SettingsBackupEffect.LaunchFolderPicker).mode)
        job.cancel()
    }

    @Test
    fun inspectPermissionLost_noAbandon() = runTest {
        advanceUntilIdle()
        backup.inspectResult = BackupSettingsSetupInspectResult.PermissionLost
        viewModel.onBackupSetupRequested()
        viewModel.onBackupDisclosureConfirmed()
        advanceUntilIdle()
        viewModel.onBackupFolderPickerResult(Uri.parse("content://test/tree/c"), 3)
        advanceUntilIdle()
        assertEquals(0, backup.abandonCount.get())
        assertEquals(0, backup.requestAbandonCount.get())
        assertEquals(
            BackupSettingsOperation.Idle,
            (viewModel.uiState.value as SettingsUiState.Content).backup.operation,
        )
    }

    @Test
    fun readyEmpty_verifiesImmediately() = runTest {
        advanceUntilIdle()
        backup.inspectResult = BackupSettingsSetupInspectResult.Ready(SetupCandidateClassification.EMPTY)
        backup.verifyResult = BackupSettingsSetupVerifyResult.Verified
        backup.commitResult = BackupSettingsSetupCommitResult.Success(1L, true)
        viewModel.onBackupSetupRequested()
        viewModel.onBackupDisclosureConfirmed()
        advanceUntilIdle()
        viewModel.onBackupFolderPickerResult(Uri.parse("content://test/tree/c"), 3)
        advanceUntilIdle()
        assertEquals(1, backup.verifyCount.get())
        assertEquals(1, backup.commitCount.get())
        assertEquals(
            BackupSettingsOperation.Idle,
            (viewModel.uiState.value as SettingsUiState.Content).backup.operation,
        )
    }

    @Test
    fun readyEqual_cancelAbandonsAwait() = runTest {
        advanceUntilIdle()
        backup.inspectResult =
            BackupSettingsSetupInspectResult.Ready(SetupCandidateClassification.VALID_EQUAL)
        viewModel.onBackupSetupRequested()
        viewModel.onBackupDisclosureConfirmed()
        advanceUntilIdle()
        viewModel.onBackupFolderPickerResult(Uri.parse("content://test/tree/c"), 3)
        advanceUntilIdle()
        assertTrue(
            (viewModel.uiState.value as SettingsUiState.Content).backup.pendingConfirmation
                is BackupPendingConfirmation.CandidateWritable,
        )
        viewModel.onBackupCandidateCancelled()
        advanceUntilIdle()
        assertEquals(1, backup.abandonCount.get())
        assertEquals(0, backup.requestAbandonCount.get())
        assertEquals(
            BackupSettingsOperation.Idle,
            (viewModel.uiState.value as SettingsUiState.Content).backup.operation,
        )
    }

    @Test
    fun blockedReady_chooseAnother_abandonsBeforeNewPicker() = runTest {
        advanceUntilIdle()
        val effects = mutableListOf<SettingsBackupEffect>()
        val job = launch { viewModel.backupEffects.collect { effects.add(it) } }
        backup.inspectResult =
            BackupSettingsSetupInspectResult.Ready(SetupCandidateClassification.BOTH_UNTRUSTED)
        viewModel.onBackupSetupRequested()
        viewModel.onBackupDisclosureConfirmed()
        advanceUntilIdle()
        effects.clear()
        viewModel.onBackupFolderPickerResult(Uri.parse("content://test/tree/c"), 3)
        advanceUntilIdle()
        viewModel.onBackupChooseAnotherFolder()
        advanceUntilIdle()
        assertEquals(1, backup.abandonCount.get())
        assertTrue(effects.isEmpty())
        assertEquals(
            BackupSettingsOperation.AwaitingDisclosureSetup,
            (viewModel.uiState.value as SettingsUiState.Content).backup.operation,
        )
        job.cancel()
    }

    @Test
    fun backupNow_written_snackbar() = runTest {
        advanceUntilIdle()
        backup.emitStatus(BackupSettingsOperationalState.Healthy(1L))
        advanceUntilIdle()
        assertTrue(
            (viewModel.uiState.value as SettingsUiState.Content).backup.operationalStatus
                is BackupSettingsOperationalState.Healthy,
        )
        backup.backupNowResult = BackupNowResult.Written
        val events = mutableListOf<SettingsSnackbarEvent>()
        val job = launch { viewModel.snackbar.collect { events.add(it) } }
        advanceUntilIdle()
        viewModel.onBackupNowRequested()
        advanceUntilIdle()
        val msg = events.filterIsInstance<SettingsSnackbarEvent.BackupMessage>().single()
        assertEquals(R.string.settings_backup_snackbar_created, msg.messageResId)
        job.cancel()
    }

    @Test
    fun onCleared_requestsRuntimeAbandon() = runTest {
        advanceUntilIdle()
        viewModel.onClearedForTests()
        assertEquals(1, backup.requestAbandonCount.get())
    }

    @Test
    fun backWithCandidateConfirmation_staysAndAbandons() = runTest {
        advanceUntilIdle()
        backup.inspectResult =
            BackupSettingsSetupInspectResult.Ready(SetupCandidateClassification.VALID_DIFFERENT)
        viewModel.onBackupSetupRequested()
        viewModel.onBackupDisclosureConfirmed()
        advanceUntilIdle()
        viewModel.onBackupFolderPickerResult(Uri.parse("content://test/tree/c"), 3)
        advanceUntilIdle()
        val nav = mutableListOf<SettingsNavigationEvent>()
        val job = launch { viewModel.navigation.collect { nav.add(it) } }
        viewModel.onBackRequested()
        advanceUntilIdle()
        assertTrue(nav.isEmpty())
        assertEquals(1, backup.abandonCount.get())
        job.cancel()
    }

    @Test
    fun configuredNoSuccessCache_doesNotClaimNoBackups() = runTest {
        advanceUntilIdle()
        backup.emitStatus(BackupSettingsOperationalState.ConfiguredNoSuccessCache)
        advanceUntilIdle()
        val status =
            (viewModel.uiState.value as SettingsUiState.Content).backup.operationalStatus
        assertTrue(status is BackupSettingsOperationalState.ConfiguredNoSuccessCache)
    }

    @Test
    fun needsReconnect_transient_attention_dataProblem_flowIntoContent() = runTest {
        advanceUntilIdle()
        backup.emitStatus(BackupSettingsOperationalState.NeedsReconnect(2L))
        advanceUntilIdle()
        assertTrue(
            (viewModel.uiState.value as SettingsUiState.Content).backup.operationalStatus
                is BackupSettingsOperationalState.NeedsReconnect,
        )
        backup.emitStatus(BackupSettingsOperationalState.TransientFailure(3L))
        advanceUntilIdle()
        assertTrue(
            (viewModel.uiState.value as SettingsUiState.Content).backup.operationalStatus
                is BackupSettingsOperationalState.TransientFailure,
        )
        backup.emitStatus(BackupSettingsOperationalState.NeedsAttention(4L))
        advanceUntilIdle()
        assertTrue(
            (viewModel.uiState.value as SettingsUiState.Content).backup.operationalStatus
                is BackupSettingsOperationalState.NeedsAttention,
        )
        backup.emitStatus(BackupSettingsOperationalState.DataProblem(5L))
        advanceUntilIdle()
        assertTrue(
            (viewModel.uiState.value as SettingsUiState.Content).backup.operationalStatus
                is BackupSettingsOperationalState.DataProblem,
        )
    }

    @Test
    fun backupFlowFailure_doesNotFatalSettings() = runTest {
        advanceUntilIdle()
        backup.failStatusFlow.set(true)
        backup.emitStatus(BackupSettingsOperationalState.Healthy(1L))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is SettingsUiState.Content)
        val backupState = (viewModel.uiState.value as SettingsUiState.Content).backup
        assertTrue(backupState.statusUnavailable)
    }

    @Test
    fun cancelDisclosure_noPickerNoSession() = runTest {
        advanceUntilIdle()
        val effects = mutableListOf<SettingsBackupEffect>()
        val job = launch { viewModel.backupEffects.collect { effects.add(it) } }
        viewModel.onBackupSetupRequested()
        viewModel.onBackupDisclosureCancelled()
        advanceUntilIdle()
        assertTrue(effects.isEmpty())
        assertEquals(0, backup.inspectCount.get())
        assertEquals(
            BackupSettingsOperation.Idle,
            (viewModel.uiState.value as SettingsUiState.Content).backup.operation,
        )
        job.cancel()
    }

    @Test
    fun pickerNull_idleNoAbandon() = runTest {
        advanceUntilIdle()
        viewModel.onBackupSetupRequested()
        viewModel.onBackupDisclosureConfirmed()
        advanceUntilIdle()
        viewModel.onBackupFolderPickerResult(null, 0)
        advanceUntilIdle()
        assertEquals(0, backup.abandonCount.get())
        assertEquals(0, backup.inspectCount.get())
        assertEquals(
            BackupSettingsOperation.Idle,
            (viewModel.uiState.value as SettingsUiState.Content).backup.operation,
        )
    }

    @Test
    fun inspectUnavailable_andProviderFailure_noAbandon() = runTest {
        advanceUntilIdle()
        backup.inspectResult = BackupSettingsSetupInspectResult.Unavailable
        driveSetupPicker()
        assertEquals(0, backup.abandonCount.get())
        assertEquals(0, backup.requestAbandonCount.get())
        backup.inspectResult = BackupSettingsSetupInspectResult.ProviderFailure
        driveSetupPicker()
        assertEquals(0, backup.abandonCount.get())
        assertEquals(0, backup.requestAbandonCount.get())
    }

    @Test
    fun readyEqual_andDifferent_andInvalidPlusMissing_confirmations() = runTest {
        advanceUntilIdle()
        backup.inspectResult =
            BackupSettingsSetupInspectResult.Ready(SetupCandidateClassification.VALID_EQUAL)
        driveSetupPicker()
        assertEquals(
            BackupPendingConfirmation.CandidateWritable(SetupCandidateClassification.VALID_EQUAL),
            (viewModel.uiState.value as SettingsUiState.Content).backup.pendingConfirmation,
        )
        viewModel.onBackupCandidateCancelled()
        advanceUntilIdle()

        backup.inspectResult =
            BackupSettingsSetupInspectResult.Ready(SetupCandidateClassification.VALID_DIFFERENT)
        driveSetupPicker()
        assertEquals(
            BackupPendingConfirmation.CandidateWritable(SetupCandidateClassification.VALID_DIFFERENT),
            (viewModel.uiState.value as SettingsUiState.Content).backup.pendingConfirmation,
        )
        viewModel.onBackupCandidateCancelled()
        advanceUntilIdle()

        backup.inspectResult =
            BackupSettingsSetupInspectResult.Ready(SetupCandidateClassification.INVALID_PLUS_MISSING)
        driveSetupPicker()
        assertEquals(
            BackupPendingConfirmation.CandidateWritable(
                SetupCandidateClassification.INVALID_PLUS_MISSING,
            ),
            (viewModel.uiState.value as SettingsUiState.Content).backup.pendingConfirmation,
        )
    }

    @Test
    fun candidateConfirm_callsVerify() = runTest {
        advanceUntilIdle()
        backup.inspectResult =
            BackupSettingsSetupInspectResult.Ready(SetupCandidateClassification.VALID_EQUAL)
        backup.verifyResult = BackupSettingsSetupVerifyResult.Verified
        backup.commitResult = BackupSettingsSetupCommitResult.Success(1L, false)
        driveSetupPicker()
        viewModel.onBackupCandidateConfirmed()
        advanceUntilIdle()
        assertEquals(1, backup.verifyCount.get())
        assertEquals(1, backup.commitCount.get())
    }

    @Test
    fun blockedReady_doesNotVerify_untilUserActs() = runTest {
        advanceUntilIdle()
        backup.inspectResult =
            BackupSettingsSetupInspectResult.Ready(SetupCandidateClassification.AMBIGUOUS)
        driveSetupPicker()
        assertEquals(0, backup.verifyCount.get())
        assertTrue(
            (viewModel.uiState.value as SettingsUiState.Content).backup.pendingConfirmation
                is BackupPendingConfirmation.CandidateBlocked,
        )
    }

    @Test
    fun abandonBusy_keepsSession_noPicker() = runTest {
        advanceUntilIdle()
        val effects = mutableListOf<SettingsBackupEffect>()
        val job = launch { viewModel.backupEffects.collect { effects.add(it) } }
        backup.inspectResult =
            BackupSettingsSetupInspectResult.Ready(SetupCandidateClassification.BOTH_UNTRUSTED)
        driveSetupPicker()
        effects.clear()
        backup.abandonResult = BackupSettingsAbandonResult.Busy
        viewModel.onBackupChooseAnotherFolder()
        advanceUntilIdle()
        assertTrue(effects.isEmpty())
        assertEquals(
            BackupSettingsOperation.AwaitingCandidateConfirmation,
            (viewModel.uiState.value as SettingsUiState.Content).backup.operation,
        )
        assertTrue(
            (viewModel.uiState.value as SettingsUiState.Content).backup.pendingConfirmation
                is BackupPendingConfirmation.CandidateBlocked,
        )
        job.cancel()
    }

    @Test
    fun candidateStaleEqual_showsFreshConfirmation() = runTest {
        advanceUntilIdle()
        backup.inspectResult =
            BackupSettingsSetupInspectResult.Ready(SetupCandidateClassification.VALID_EQUAL)
        backup.verifyResult =
            BackupSettingsSetupVerifyResult.CandidateStale(SetupCandidateClassification.VALID_EQUAL)
        driveSetupPicker()
        viewModel.onBackupCandidateConfirmed()
        advanceUntilIdle()
        assertEquals(
            BackupPendingConfirmation.CandidateWritable(SetupCandidateClassification.VALID_EQUAL),
            (viewModel.uiState.value as SettingsUiState.Content).backup.pendingConfirmation,
        )
        assertEquals(0, backup.commitCount.get())
    }

    @Test
    fun candidateStaleEmpty_verifiesAgain() = runTest {
        advanceUntilIdle()
        backup.inspectResult =
            BackupSettingsSetupInspectResult.Ready(SetupCandidateClassification.VALID_EQUAL)
        var verifyCalls = 0
        backup.verifyResultProvider = {
            verifyCalls++
            if (verifyCalls == 1) {
                BackupSettingsSetupVerifyResult.CandidateStale(SetupCandidateClassification.EMPTY)
            } else {
                BackupSettingsSetupVerifyResult.Verified
            }
        }
        backup.commitResult = BackupSettingsSetupCommitResult.Success(1L, false)
        driveSetupPicker()
        viewModel.onBackupCandidateConfirmed()
        advanceUntilIdle()
        assertEquals(2, backup.verifyCount.get())
        assertEquals(1, backup.commitCount.get())
    }

    @Test
    fun commitFailed_retryCallsCommitOnly() = runTest {
        advanceUntilIdle()
        backup.inspectResult =
            BackupSettingsSetupInspectResult.Ready(SetupCandidateClassification.EMPTY)
        backup.verifyResult = BackupSettingsSetupVerifyResult.Verified
        backup.commitResult = BackupSettingsSetupCommitResult.CommitFailed
        driveSetupPicker()
        assertEquals(
            BackupSettingsOperation.AwaitingCommitRetry,
            (viewModel.uiState.value as SettingsUiState.Content).backup.operation,
        )
        val verifyBefore = backup.verifyCount.get()
        backup.commitResult = BackupSettingsSetupCommitResult.Success(1L, false)
        viewModel.onBackupCommitRetry()
        advanceUntilIdle()
        assertEquals(verifyBefore, backup.verifyCount.get())
        assertEquals(2, backup.commitCount.get())
    }

    @Test
    fun receiptStale_reverify() = runTest {
        advanceUntilIdle()
        backup.inspectResult =
            BackupSettingsSetupInspectResult.Ready(SetupCandidateClassification.EMPTY)
        backup.verifyResult = BackupSettingsSetupVerifyResult.Verified
        var commits = 0
        backup.commitResultProvider = {
            commits++
            if (commits == 1) {
                BackupSettingsSetupCommitResult.ReceiptStale
            } else {
                BackupSettingsSetupCommitResult.Success(1L, false)
            }
        }
        driveSetupPicker()
        assertTrue(backup.verifyCount.get() >= 2)
        assertEquals(
            BackupSettingsOperation.Idle,
            (viewModel.uiState.value as SettingsUiState.Content).backup.operation,
        )
    }

    @Test
    fun replaceSuccess_snackbar() = runTest {
        advanceUntilIdle()
        backup.emitStatus(BackupSettingsOperationalState.Healthy(1L))
        advanceUntilIdle()
        backup.inspectResult =
            BackupSettingsSetupInspectResult.Ready(SetupCandidateClassification.EMPTY)
        backup.verifyResult = BackupSettingsSetupVerifyResult.Verified
        backup.commitResult = BackupSettingsSetupCommitResult.Success(1L, false)
        val events = mutableListOf<SettingsSnackbarEvent>()
        val job = launch { viewModel.snackbar.collect { events.add(it) } }
        advanceUntilIdle()
        viewModel.onBackupReplacementRequested()
        viewModel.onBackupDisclosureConfirmed()
        advanceUntilIdle()
        viewModel.onBackupFolderPickerResult(Uri.parse("content://test/tree/r"), 3)
        advanceUntilIdle()
        assertTrue(
            events.filterIsInstance<SettingsSnackbarEvent.BackupMessage>()
                .any { it.messageResId == R.string.settings_backup_snackbar_folder_changed },
        )
        job.cancel()
    }

    @Test
    fun replaceFailure_configured_claimsStillActive() = runTest {
        advanceUntilIdle()
        backup.emitStatus(BackupSettingsOperationalState.Healthy(1L))
        advanceUntilIdle()
        backup.inspectThrows = true
        val events = mutableListOf<SettingsSnackbarEvent>()
        val job = launch { viewModel.snackbar.collect { events.add(it) } }
        advanceUntilIdle()
        viewModel.onBackupReplacementRequested()
        viewModel.onBackupDisclosureConfirmed()
        advanceUntilIdle()
        viewModel.onBackupFolderPickerResult(Uri.parse("content://test/tree/r"), 3)
        advanceUntilIdle()
        assertTrue(
            events.filterIsInstance<SettingsSnackbarEvent.BackupMessage>()
                .any { it.messageResId == R.string.settings_backup_snackbar_replace_failed_still_active },
        )
        job.cancel()
    }

    @Test
    fun replaceFailure_notConfigured_doesNotClaimActive() = runTest {
        advanceUntilIdle()
        backup.emitStatus(BackupSettingsOperationalState.NotConfigured)
        advanceUntilIdle()
        // Force REPLACE intent via replacement path is blocked when NotConfigured;
        // simulate replace intent through setup then concurrent NotConfigured on failure.
        backup.inspectThrows = true
        backup.emitStatus(BackupSettingsOperationalState.Healthy(1L))
        advanceUntilIdle()
        viewModel.onBackupReplacementRequested()
        viewModel.onBackupDisclosureConfirmed()
        advanceUntilIdle()
        backup.emitStatus(BackupSettingsOperationalState.NotConfigured)
        advanceUntilIdle()
        val events = mutableListOf<SettingsSnackbarEvent>()
        val job = launch { viewModel.snackbar.collect { events.add(it) } }
        advanceUntilIdle()
        viewModel.onBackupFolderPickerResult(Uri.parse("content://test/tree/r"), 3)
        advanceUntilIdle()
        assertTrue(
            events.filterIsInstance<SettingsSnackbarEvent.BackupMessage>()
                .any { it.messageResId == R.string.settings_backup_snackbar_replace_failed },
        )
        assertTrue(
            events.filterIsInstance<SettingsSnackbarEvent.BackupMessage>()
                .none { it.messageResId == R.string.settings_backup_snackbar_replace_failed_still_active },
        )
        job.cancel()
    }

    @Test
    fun reconnect_success_andDifferentFolder() = runTest {
        advanceUntilIdle()
        backup.emitStatus(BackupSettingsOperationalState.NeedsReconnect(1L))
        advanceUntilIdle()
        backup.reconnectResult = BackupReconnectResult.Success
        val events = mutableListOf<SettingsSnackbarEvent>()
        val job = launch { viewModel.snackbar.collect { events.add(it) } }
        advanceUntilIdle()
        viewModel.onBackupReconnectRequested()
        advanceUntilIdle()
        viewModel.onBackupFolderPickerResult(Uri.parse("content://test/tree/c"), 3)
        advanceUntilIdle()
        assertTrue(
            events.filterIsInstance<SettingsSnackbarEvent.BackupMessage>()
                .any { it.messageResId == R.string.settings_backup_snackbar_reconnect_success },
        )
        backup.emitStatus(BackupSettingsOperationalState.NeedsReconnect(1L))
        advanceUntilIdle()
        backup.reconnectResult = BackupReconnectResult.DifferentFolder
        viewModel.onBackupReconnectRequested()
        advanceUntilIdle()
        viewModel.onBackupFolderPickerResult(Uri.parse("content://test/tree/other"), 3)
        advanceUntilIdle()
        assertEquals(
            BackupSettingsOperation.AwaitingReconnectDifferentFolder,
            (viewModel.uiState.value as SettingsUiState.Content).backup.operation,
        )
        viewModel.onBackupReconnectDifferentUseAsNew()
        advanceUntilIdle()
        assertEquals(
            BackupSettingsOperation.AwaitingDisclosureReplace,
            (viewModel.uiState.value as SettingsUiState.Content).backup.operation,
        )
        job.cancel()
    }

    @Test
    fun backupNow_mappings() = runTest {
        advanceUntilIdle()
        backup.emitStatus(BackupSettingsOperationalState.Healthy(1L))
        advanceUntilIdle()
        val events = mutableListOf<SettingsSnackbarEvent>()
        val job = launch { viewModel.snackbar.collect { events.add(it) } }
        advanceUntilIdle()

        suspend fun assertNow(result: BackupNowResult, res: Int) {
            events.clear()
            backup.backupNowResult = result
            viewModel.onBackupNowRequested()
            advanceUntilIdle()
            assertEquals(
                res,
                events.filterIsInstance<SettingsSnackbarEvent.BackupMessage>().single().messageResId,
            )
        }

        assertNow(BackupNowResult.NoChange, R.string.settings_backup_snackbar_already_current)
        assertNow(BackupNowResult.NotConfigured, R.string.settings_backup_snackbar_not_configured)
        assertNow(BackupNowResult.NeedsReconnect, R.string.settings_backup_snackbar_needs_reconnect)
        assertNow(BackupNowResult.TransientFailure, R.string.settings_backup_snackbar_transient)
        assertNow(BackupNowResult.NeedsAttention, R.string.settings_backup_snackbar_needs_attention)
        assertNow(BackupNowResult.DataProblem, R.string.settings_backup_snackbar_data_problem)
        assertNow(
            BackupNowResult.StatusTrackingIncomplete(BackupNowPhysicalResult.WRITTEN),
            R.string.settings_backup_snackbar_tracking_written,
        )
        assertNow(
            BackupNowResult.StatusTrackingIncomplete(BackupNowPhysicalResult.NO_CHANGE),
            R.string.settings_backup_snackbar_tracking_nochange,
        )
        assertNow(
            BackupNowResult.TemporarilyUnavailable,
            R.string.settings_backup_snackbar_temporarily_unavailable,
        )
        job.cancel()
    }

    @Test
    fun backupNow_needsReconnect_noAutoPicker() = runTest {
        advanceUntilIdle()
        val effects = mutableListOf<SettingsBackupEffect>()
        val job = launch { viewModel.backupEffects.collect { effects.add(it) } }
        backup.emitStatus(BackupSettingsOperationalState.Healthy(1L))
        advanceUntilIdle()
        backup.backupNowResult = BackupNowResult.NeedsReconnect
        viewModel.onBackupNowRequested()
        advanceUntilIdle()
        assertTrue(effects.isEmpty())
        job.cancel()
    }

    @Test
    fun busyOperation_disablesBackupActions() = runTest {
        advanceUntilIdle()
        backup.emitStatus(BackupSettingsOperationalState.Healthy(1L))
        advanceUntilIdle()
        backup.backupNowResult = BackupNowResult.Written
        // While inspecting, actions disabled
        backup.inspectResult =
            BackupSettingsSetupInspectResult.Ready(SetupCandidateClassification.VALID_EQUAL)
        viewModel.onBackupSetupRequested()
        viewModel.onBackupDisclosureConfirmed()
        advanceUntilIdle()
        assertTrue(
            !(viewModel.uiState.value as SettingsUiState.Content).backup.backupActionsEnabled,
        )
    }

    @Test
    fun disable_confirm_and_success() = runTest {
        advanceUntilIdle()
        backup.emitStatus(BackupSettingsOperationalState.Healthy(1L))
        advanceUntilIdle()
        viewModel.onBackupDisableRequested()
        assertEquals(
            BackupPendingConfirmation.Disable,
            (viewModel.uiState.value as SettingsUiState.Content).backup.pendingConfirmation,
        )
        viewModel.onBackupDisableCancelled()
        advanceUntilIdle()
        assertEquals(0, backup.disableCount.get())
        viewModel.onBackupDisableRequested()
        val events = mutableListOf<SettingsSnackbarEvent>()
        val job = launch { viewModel.snackbar.collect { events.add(it) } }
        advanceUntilIdle()
        viewModel.onBackupDisableConfirmed()
        advanceUntilIdle()
        assertEquals(1, backup.disableCount.get())
        assertTrue(
            events.filterIsInstance<SettingsSnackbarEvent.BackupMessage>()
                .any { it.messageResId == R.string.settings_backup_snackbar_disabled },
        )
        job.cancel()
    }

    @Test
    fun disable_earlyNotConfigured_staysDisablingUntilFacadeReturns() = runTest {
        advanceUntilIdle()
        backup.emitStatus(BackupSettingsOperationalState.Healthy(1L))
        advanceUntilIdle()
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        backup.disableGate = gate
        backup.disableResult = BackupSettingsDisableResult.Success
        viewModel.onBackupDisableRequested()
        viewModel.onBackupDisableConfirmed()
        advanceUntilIdle()
        backup.emitStatus(BackupSettingsOperationalState.NotConfigured)
        assertEquals(
            BackupSettingsOperation.Disabling,
            (viewModel.uiState.value as SettingsUiState.Content).backup.operation,
        )
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(
            BackupSettingsOperation.Idle,
            (viewModel.uiState.value as SettingsUiState.Content).backup.operation,
        )
    }

    @Test
    fun explicitAbandon_thenOnCleared_safeDuplicateRequest() = runTest {
        advanceUntilIdle()
        backup.inspectResult =
            BackupSettingsSetupInspectResult.Ready(SetupCandidateClassification.VALID_EQUAL)
        driveSetupPicker()
        viewModel.onBackupCandidateCancelled()
        advanceUntilIdle()
        assertEquals(1, backup.abandonCount.get())
        viewModel.onClearedForTests()
        assertEquals(1, backup.requestAbandonCount.get())
    }

    @Test
    fun uiState_hasNoUriTokenReceiptFields() = runTest {
        advanceUntilIdle()
        val backupState = (viewModel.uiState.value as SettingsUiState.Content).backup
        val text = backupState.toString()
        assertTrue(!text.contains("content://"))
        assertTrue(!text.contains("token", ignoreCase = true))
        assertTrue(!text.contains("receipt", ignoreCase = true))
    }

    private fun kotlinx.coroutines.test.TestScope.driveSetupPicker() {
        viewModel.onBackupSetupRequested()
        viewModel.onBackupDisclosureConfirmed()
        advanceUntilIdle()
        viewModel.onBackupFolderPickerResult(Uri.parse("content://test/tree/c"), 3)
        advanceUntilIdle()
    }
}
