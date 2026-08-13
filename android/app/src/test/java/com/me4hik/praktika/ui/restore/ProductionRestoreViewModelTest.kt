package com.me4hik.praktika.ui.restore

import android.net.Uri
import com.me4hik.praktika.data.backup.model.BackupSlotId
import com.me4hik.praktika.data.backup.restore.BackupRestoreCoordinatorResult
import com.me4hik.praktika.data.backup.restore.BackupRestoreFixtures
import com.me4hik.praktika.data.backup.restore.BackupRestorePreview
import com.me4hik.praktika.data.backup.restore.BackupRestorePreviewBuilder
import com.me4hik.praktika.data.backup.restore.BackupRestoreResult
import com.me4hik.praktika.data.backup.restore.BackupRestoreSelectedIdentity
import com.me4hik.praktika.data.backup.restore.PostRestoreVerificationResult
import com.me4hik.praktika.data.backup.restore.RestoreTargetEligibility
import com.me4hik.praktika.data.backup.storage.BackupSlotWriteResult
import com.me4hik.praktika.data.backup.storage.BackupStorageProvider
import com.me4hik.praktika.data.backup.storage.SlotInspectionResult
import com.me4hik.praktika.data.backup.storage.SlotReadResult
import com.me4hik.praktika.data.cycle.CycleResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProductionRestoreViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var gateway: RecordingProductionRestoreGateway
    private lateinit var viewModel: ProductionRestoreViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        gateway = RecordingProductionRestoreGateway()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() {
        viewModel = ProductionRestoreViewModel(
            gateway = gateway,
            previewMapper = testPreviewMapper(),
        )
    }

    private fun testPreviewMapper(): BackupRestorePreviewMapper {
        return BackupRestorePreviewMapper(
            dateFormatter = ProductionRestoreDateFormatter(
                zoneId = java.time.ZoneId.of("UTC"),
            ),
        )
    }

    private fun richPreview(): BackupRestorePreview {
        return BackupRestorePreviewBuilder.fromEnvelope(
            slot = BackupSlotId.B,
            envelope = BackupRestoreFixtures.richEnvelope(),
        )
    }

    @Test
    fun initialState_isHidden() = runTest(testDispatcher) {
        createViewModel()
        assertEquals(ProductionRestoreUiState.Hidden, viewModel.uiState.value)
        assertFalse(viewModel.sessionActive.value)
    }

    @Test
    fun openRestore_eligible_movesToIdleAndActivatesSession() = runTest(testDispatcher) {
        createViewModel()
        viewModel.onOpenRestore()
        advanceUntilIdle()
        assertEquals(ProductionRestoreUiState.Idle, viewModel.uiState.value)
        assertTrue(viewModel.sessionActive.value)
    }

    @Test
    fun openRestore_targetNotEmpty_movesToBlocked() = runTest(testDispatcher) {
        gateway.eligibility = RestoreTargetEligibility.TargetNotEmpty
        createViewModel()
        viewModel.onOpenRestore()
        advanceUntilIdle()
        assertEquals(
            ProductionRestoreUiState.Blocked(ProductionRestoreTargetBlockReason.TargetNotEmpty),
            viewModel.uiState.value,
        )
        assertTrue(viewModel.sessionActive.value)
    }

    @Test
    fun chooseFolder_emitsLaunchFolderPicker() = runTest(testDispatcher) {
        gateway.treeUriHint = "content://hint/tree"
        createViewModel()
        viewModel.onOpenRestore()
        advanceUntilIdle()

        val effectDeferred = async { viewModel.effects.first() }
        viewModel.onChooseFolder()
        assertEquals(
            ProductionRestoreEffect.LaunchFolderPicker(initialUriHint = "content://hint/tree"),
            effectDeferred.await(),
        )
    }

    @Test
    fun pickerCancelled_returnsToIdle() = runTest(testDispatcher) {
        createViewModel()
        viewModel.onOpenRestore()
        advanceUntilIdle()
        viewModel.onChooseFolder()
        advanceUntilIdle()
        viewModel.onFolderPickerResult(uri = null, grantFlags = 0)
        advanceUntilIdle()
        assertEquals(ProductionRestoreUiState.Idle, viewModel.uiState.value)
        assertTrue(viewModel.sessionActive.value)
    }

    @Test
    fun connectSuccess_inspectsBackup() = runTest(testDispatcher) {
        val storage = FakeRestoreStorage()
        gateway.connectOutcome = ProductionRestoreConnectOutcome.Success(storage, TEST_URI)
        gateway.inspectResult = previewReady(richPreview())
        createViewModel()
        openToIdleAndChooseFolder()
        viewModel.onFolderPickerResult(TEST_URI, TEST_FLAGS)
        advanceUntilIdle()
        assertEquals(1, gateway.connectCalls)
        assertEquals(1, gateway.inspectCalls)
    }

    @Test
    fun noValidBackup_releasesRejectedGrant() = runTest(testDispatcher) {
        gateway.connectOutcome = ProductionRestoreConnectOutcome.Success(FakeRestoreStorage(), TEST_URI)
        gateway.inspectResult = BackupRestoreCoordinatorResult.NoValidBackup
        createViewModel()
        openToIdleAndChooseFolder()
        viewModel.onFolderPickerResult(TEST_URI, TEST_FLAGS)
        advanceUntilIdle()
        val state = viewModel.uiState.value as ProductionRestoreUiState.Error
        assertEquals(ProductionRestoreError.NoBackupFound, state.error)
        assertEquals(1, gateway.releaseRejectedGrantCalls)
        assertEquals(0, gateway.clearTreeUriCalls)
        assertEquals(0, gateway.commitActiveBackupFolderCalls)
    }

    @Test
    fun validBackup_mapsToSafePreviewReady() = runTest(testDispatcher) {
        val preview = richPreview()
        gateway.connectOutcome = ProductionRestoreConnectOutcome.Success(FakeRestoreStorage(), TEST_URI)
        gateway.inspectResult = previewReady(preview)
        createViewModel()
        openToIdleAndChooseFolder()
        viewModel.onFolderPickerResult(TEST_URI, TEST_FLAGS)
        advanceUntilIdle()
        val state = viewModel.uiState.value as ProductionRestoreUiState.PreviewReady
        assertEquals(testPreviewMapper().toUiModel(preview).createdAtText, state.preview.createdAtText)
        assertEquals(1, state.preview.answerCount)
        assertEquals(preview.completedSlots, state.preview.completedCount)
        assertTrue(state.preview.practiceStarted)
        assertFalse(state.staleNotice)
        val serialized = state.toString() + state.preview.toString()
        assertTechnicalFieldsAbsent(serialized)
    }

    @Test
    fun restoreConfirmed_movesToRestoringThenRestoreSuccess() = runTest(testDispatcher) {
        val preview = richPreview()
        val storage = FakeRestoreStorage()
        gateway.connectOutcome = ProductionRestoreConnectOutcome.Success(storage, TEST_URI)
        gateway.inspectResult = previewReady(preview)
        gateway.executeResult = BackupRestoreCoordinatorResult.FullSuccess(
            preview = preview,
            verification = PostRestoreVerificationResult.NotRequested,
            reconcileResult = CycleResult.ReconcileChanged,
            notificationSync = com.me4hik.praktika.data.backup.restore.NotificationSyncObservation.Invoked,
        )
        createViewModel()
        openToIdleAndChooseFolder()
        viewModel.onFolderPickerResult(TEST_URI, TEST_FLAGS)
        advanceUntilIdle()
        viewModel.onRestoreConfirmed()
        advanceUntilIdle()
        assertEquals(
            ProductionRestoreUiState.RestoreSuccess(testPreviewMapper().toUiModel(preview)),
            viewModel.uiState.value,
        )
        assertEquals(1, gateway.executeCalls)
    }

    @Test
    fun runtimeSyncWarning_mapsToCommittedWarning() = runTest(testDispatcher) {
        val preview = richPreview()
        gateway.connectOutcome = ProductionRestoreConnectOutcome.Success(FakeRestoreStorage(), TEST_URI)
        gateway.inspectResult = previewReady(preview)
        gateway.executeResult = BackupRestoreCoordinatorResult.RestoreDataSuccessRuntimeSyncWarning(
            preview = preview,
            verification = PostRestoreVerificationResult.NotRequested,
            dataRestoreCommitted = true,
            reconcileFailureClassName = "java.lang.IllegalStateException",
            reconcileFailureMessage = "secret internal path/data",
            notificationSync = null,
        )
        createViewModel()
        openToIdleAndChooseFolder()
        viewModel.onFolderPickerResult(TEST_URI, TEST_FLAGS)
        advanceUntilIdle()
        viewModel.onRestoreConfirmed()
        advanceUntilIdle()
        val state = viewModel.uiState.value as ProductionRestoreUiState.RuntimeSyncWarning
        val serialized = state.toString() + state.preview.toString()
        assertTechnicalFieldsAbsent(serialized)
        assertFalse(serialized.contains("IllegalStateException"))
        assertFalse(serialized.contains("secret internal"))
    }

    @Test
    fun previewStale_reinspectsAndRequiresSecondConfirmation() = runTest(testDispatcher) {
        val preview = richPreview()
        val storage = FakeRestoreStorage()
        gateway.connectOutcome = ProductionRestoreConnectOutcome.Success(storage, TEST_URI)
        gateway.inspectResult = previewReady(preview)
        gateway.executeResults = listOf(
            BackupRestoreCoordinatorResult.PreviewStale(
                previousIdentity = preview.identity,
                currentIdentity = preview.identity.copy(backupSequence = preview.backupSequence + 1),
            ),
            BackupRestoreCoordinatorResult.FullSuccess(
                preview = preview,
                verification = PostRestoreVerificationResult.NotRequested,
                reconcileResult = CycleResult.ReconcileNoChanges,
                notificationSync = com.me4hik.praktika.data.backup.restore.NotificationSyncObservation.Invoked,
            ),
        )
        createViewModel()
        openToIdleAndChooseFolder()
        viewModel.onFolderPickerResult(TEST_URI, TEST_FLAGS)
        advanceUntilIdle()
        viewModel.onRestoreConfirmed()
        advanceUntilIdle()
        val staleState = viewModel.uiState.value as ProductionRestoreUiState.PreviewReady
        assertTrue(staleState.staleNotice)
        assertEquals(1, gateway.executeCalls)
        viewModel.onRestoreConfirmed()
        advanceUntilIdle()
        assertEquals(2, gateway.executeCalls)
        assertTrue(viewModel.uiState.value is ProductionRestoreUiState.RestoreSuccess)
    }

    @Test
    fun targetBecomesNonEmptyBeforeRestore_blocksRestore() = runTest(testDispatcher) {
        val preview = richPreview()
        gateway.connectOutcome = ProductionRestoreConnectOutcome.Success(FakeRestoreStorage(), TEST_URI)
        gateway.inspectResult = previewReady(preview)
        gateway.eligibilityBeforeRestore = RestoreTargetEligibility.TargetNotEmpty
        createViewModel()
        openToIdleAndChooseFolder()
        viewModel.onFolderPickerResult(TEST_URI, TEST_FLAGS)
        advanceUntilIdle()
        viewModel.onRestoreConfirmed()
        advanceUntilIdle()
        assertEquals(
            ProductionRestoreUiState.Blocked(ProductionRestoreTargetBlockReason.TargetNotEmpty),
            viewModel.uiState.value,
        )
        assertEquals(0, gateway.executeCalls)
    }

    @Test
    fun cancellation_isNotMappedToUnexpectedError() = runTest(testDispatcher) {
        gateway.connectAction = { throw CancellationException("picker cancelled") }
        createViewModel()
        openToIdleAndChooseFolder()
        viewModel.onFolderPickerResult(TEST_URI, TEST_FLAGS)
        try {
            advanceUntilIdle()
        } catch (_: CancellationException) {
            // expected propagation from cancelled connect
        }
        assertEquals(ProductionRestoreUiState.Idle, viewModel.uiState.value)
        assertNotEquals(
            ProductionRestoreUiState.Error(ProductionRestoreError.Unexpected),
            viewModel.uiState.value,
        )
    }

    @Test
    fun duplicateRestoreTap_ignoredWhileRestoring() = runTest(testDispatcher) {
        val preview = richPreview()
        gateway.connectOutcome = ProductionRestoreConnectOutcome.Success(FakeRestoreStorage(), TEST_URI)
        gateway.inspectResult = previewReady(preview)
        gateway.executeGate = CompletableDeferred()
        createViewModel()
        openToIdleAndChooseFolder()
        viewModel.onFolderPickerResult(TEST_URI, TEST_FLAGS)
        advanceUntilIdle()
        viewModel.onRestoreConfirmed()
        testScheduler.runCurrent()
        viewModel.onRestoreConfirmed()
        testScheduler.runCurrent()
        assertEquals(1, gateway.executeCalls)
        gateway.executeGate?.complete(Unit)
    }

    @Test
    fun dismissFromPreview_resetsHiddenAndClearsSession() = runTest(testDispatcher) {
        val preview = richPreview()
        gateway.connectOutcome = ProductionRestoreConnectOutcome.Success(FakeRestoreStorage(), TEST_URI)
        gateway.inspectResult = previewReady(preview)
        createViewModel()
        openToIdleAndChooseFolder()
        viewModel.onFolderPickerResult(TEST_URI, TEST_FLAGS)
        advanceUntilIdle()
        viewModel.onDismiss()
        assertEquals(ProductionRestoreUiState.Hidden, viewModel.uiState.value)
        assertFalse(viewModel.sessionActive.value)
    }

    @Test
    fun dismissDuringRestoring_isIgnored() = runTest(testDispatcher) {
        val preview = richPreview()
        gateway.connectOutcome = ProductionRestoreConnectOutcome.Success(FakeRestoreStorage(), TEST_URI)
        gateway.inspectResult = previewReady(preview)
        gateway.executeGate = CompletableDeferred()
        createViewModel()
        openToIdleAndChooseFolder()
        viewModel.onFolderPickerResult(TEST_URI, TEST_FLAGS)
        advanceUntilIdle()
        viewModel.onRestoreConfirmed()
        testScheduler.runCurrent()
        viewModel.onDismiss()
        assertTrue(viewModel.uiState.value is ProductionRestoreUiState.Restoring)
        gateway.executeGate?.complete(Unit)
    }

    @Test
    fun sessionActive_trueThroughoutVisibleSession() = runTest(testDispatcher) {
        val preview = richPreview()
        gateway.connectOutcome = ProductionRestoreConnectOutcome.Success(FakeRestoreStorage(), TEST_URI)
        gateway.inspectResult = previewReady(preview)
        createViewModel()
        assertFalse(viewModel.sessionActive.value)
        viewModel.onOpenRestore()
        advanceUntilIdle()
        assertTrue(viewModel.sessionActive.value)
        viewModel.onChooseFolder()
        advanceUntilIdle()
        assertTrue(viewModel.sessionActive.value)
        viewModel.onFolderPickerResult(TEST_URI, TEST_FLAGS)
        advanceUntilIdle()
        assertTrue(viewModel.sessionActive.value)
        viewModel.onDismiss()
        assertFalse(viewModel.sessionActive.value)
    }

    @Test
    fun successAcknowledged_resetsSession() = runTest(testDispatcher) {
        val preview = richPreview()
        gateway.connectOutcome = ProductionRestoreConnectOutcome.Success(FakeRestoreStorage(), TEST_URI)
        gateway.inspectResult = previewReady(preview)
        gateway.executeResult = BackupRestoreCoordinatorResult.FullSuccess(
            preview = preview,
            verification = PostRestoreVerificationResult.NotRequested,
            reconcileResult = CycleResult.ReconcileNoChanges,
            notificationSync = com.me4hik.praktika.data.backup.restore.NotificationSyncObservation.Invoked,
        )
        createViewModel()
        openToIdleAndChooseFolder()
        viewModel.onFolderPickerResult(TEST_URI, TEST_FLAGS)
        advanceUntilIdle()
        viewModel.onRestoreConfirmed()
        advanceUntilIdle()
        viewModel.onSuccessAcknowledged()
        assertEquals(ProductionRestoreUiState.Hidden, viewModel.uiState.value)
        assertFalse(viewModel.sessionActive.value)
    }

    @Test
    fun runtimeWarningContinue_resetsSession() = runTest(testDispatcher) {
        val preview = richPreview()
        gateway.connectOutcome = ProductionRestoreConnectOutcome.Success(FakeRestoreStorage(), TEST_URI)
        gateway.inspectResult = previewReady(preview)
        gateway.executeResult = BackupRestoreCoordinatorResult.RestoreDataSuccessRuntimeSyncWarning(
            preview = preview,
            verification = PostRestoreVerificationResult.NotRequested,
            dataRestoreCommitted = true,
            reconcileFailureClassName = "java.lang.RuntimeException",
            reconcileFailureMessage = "boom",
            notificationSync = null,
        )
        createViewModel()
        openToIdleAndChooseFolder()
        viewModel.onFolderPickerResult(TEST_URI, TEST_FLAGS)
        advanceUntilIdle()
        viewModel.onRestoreConfirmed()
        advanceUntilIdle()
        viewModel.onRuntimeWarningContinue()
        assertEquals(ProductionRestoreUiState.Hidden, viewModel.uiState.value)
        assertFalse(viewModel.sessionActive.value)
    }

    @Test
    fun technicalIdentity_neverExposedThroughUiState() = runTest(testDispatcher) {
        val preview = richPreview()
        gateway.connectOutcome = ProductionRestoreConnectOutcome.Success(FakeRestoreStorage(), TEST_URI)
        gateway.inspectResult = previewReady(preview)
        createViewModel()
        openToIdleAndChooseFolder()
        viewModel.onFolderPickerResult(TEST_URI, TEST_FLAGS)
        advanceUntilIdle()
        assertTechnicalFieldsAbsent(viewModel.uiState.value.toString())
    }

    @Test
    fun effectDelivery_noReplayOnSecondCollector() = runTest(testDispatcher) {
        createViewModel()
        viewModel.onOpenRestore()
        advanceUntilIdle()

        val firstEffect = async { viewModel.effects.first() }
        viewModel.onChooseFolder()
        assertEquals(
            ProductionRestoreEffect.LaunchFolderPicker(initialUriHint = null),
            firstEffect.await(),
        )

        val secondEffect = async { viewModel.effects.first() }
        advanceUntilIdle()
        assertTrue(secondEffect.isCompleted.not())
        secondEffect.cancel()
    }

    @Test
    fun validBackup_commitsActiveFolderBeforePreviewReady() = runTest(testDispatcher) {
        val preview = richPreview()
        gateway.connectOutcome = ProductionRestoreConnectOutcome.Success(FakeRestoreStorage(), TEST_URI)
        gateway.inspectResult = previewReady(preview)
        createViewModel()
        openToIdleAndChooseFolder()
        viewModel.onFolderPickerResult(TEST_URI, TEST_FLAGS)
        advanceUntilIdle()
        assertEquals(1, gateway.commitActiveBackupFolderCalls)
        assertTrue(viewModel.uiState.value is ProductionRestoreUiState.PreviewReady)
    }

    @Test
    fun invalidReplacement_preservesActiveFolderAndReleasesCandidate() = runTest(testDispatcher) {
        val replacementUri = Uri.parse("content://com.test/tree/replacement")
        gateway.activeTreeUri = TEST_URI.toString()
        gateway.connectOutcome = ProductionRestoreConnectOutcome.Success(
            FakeRestoreStorage(),
            replacementUri,
        )
        gateway.inspectResult = BackupRestoreCoordinatorResult.NoValidBackup
        createViewModel()
        openToIdleAndChooseFolder()
        viewModel.onFolderPickerResult(replacementUri, TEST_FLAGS)
        advanceUntilIdle()
        assertEquals(1, gateway.releaseRejectedGrantCalls)
        assertEquals(0, gateway.commitActiveBackupFolderCalls)
        assertEquals(TEST_URI.toString(), gateway.activeTreeUri)
    }

    @Test
    fun validReplacement_commitsNewActiveFolder() = runTest(testDispatcher) {
        val replacementUri = Uri.parse("content://com.test/tree/replacement")
        gateway.activeTreeUri = TEST_URI.toString()
        gateway.connectOutcome = ProductionRestoreConnectOutcome.Success(FakeRestoreStorage(), replacementUri)
        gateway.inspectResult = previewReady(richPreview())
        createViewModel()
        openToIdleAndChooseFolder()
        viewModel.onFolderPickerResult(replacementUri, TEST_FLAGS)
        advanceUntilIdle()
        assertEquals(1, gateway.commitActiveBackupFolderCalls)
        assertEquals(replacementUri.toString(), gateway.lastCommittedUri)
    }

    @Test
    fun sameActiveFolderRejection_doesNotReleaseActiveGrant() = runTest(testDispatcher) {
        gateway.activeTreeUri = TEST_URI.toString()
        gateway.connectOutcome = ProductionRestoreConnectOutcome.Success(FakeRestoreStorage(), TEST_URI)
        gateway.inspectResult = BackupRestoreCoordinatorResult.NoValidBackup
        createViewModel()
        openToIdleAndChooseFolder()
        viewModel.onFolderPickerResult(TEST_URI, TEST_FLAGS)
        advanceUntilIdle()
        assertEquals(1, gateway.releaseRejectedGrantCalls)
        assertEquals(TEST_URI.toString(), gateway.activeTreeUri)
    }

    @Test
    fun noValidBackup_validPreviewCommitsActiveFolder() = runTest(testDispatcher) {
        gateway.connectOutcome = ProductionRestoreConnectOutcome.Success(FakeRestoreStorage(), TEST_URI)
        gateway.inspectResult = BackupRestoreCoordinatorResult.NoValidBackup
        createViewModel()
        openToIdleAndChooseFolder()
        viewModel.onFolderPickerResult(TEST_URI, TEST_FLAGS)
        advanceUntilIdle()
        assertEquals(1, gateway.releaseRejectedGrantCalls)

        gateway.inspectResult = previewReady(richPreview())
        viewModel.onChooseFolder()
        advanceUntilIdle()
        viewModel.onFolderPickerResult(TEST_URI, TEST_FLAGS)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is ProductionRestoreUiState.PreviewReady)
        assertEquals(1, gateway.commitActiveBackupFolderCalls)
    }

    @Test
    fun connectWriteFailure_mapsToReadFailure() = runTest(testDispatcher) {
        gateway.connectOutcome = ProductionRestoreConnectOutcome.WriteFailure
        createViewModel()
        openToIdleAndChooseFolder()
        viewModel.onFolderPickerResult(TEST_URI, TEST_FLAGS)
        advanceUntilIdle()
        val state = viewModel.uiState.value as ProductionRestoreUiState.Error
        assertEquals(ProductionRestoreError.ReadFailure, state.error)
    }

    @Test
    fun activeFolderCommitFailure_mapsToActiveFolderSaveFailure() = runTest(testDispatcher) {
        gateway.connectOutcome = ProductionRestoreConnectOutcome.Success(FakeRestoreStorage(), TEST_URI)
        gateway.inspectResult = previewReady(richPreview())
        gateway.commitShouldSucceed = false
        createViewModel()
        openToIdleAndChooseFolder()
        viewModel.onFolderPickerResult(TEST_URI, TEST_FLAGS)
        advanceUntilIdle()
        val state = viewModel.uiState.value as ProductionRestoreUiState.Error
        assertEquals(ProductionRestoreError.ActiveFolderSaveFailure, state.error)
    }

    @Test
    fun databaseWriteFailure_mapsToRestoreWriteFailure() = runTest(testDispatcher) {
        val preview = richPreview()
        gateway.connectOutcome = ProductionRestoreConnectOutcome.Success(FakeRestoreStorage(), TEST_URI)
        gateway.inspectResult = previewReady(preview)
        gateway.executeResult = BackupRestoreCoordinatorResult.RestoreCoreFailure(
            restoreResult = BackupRestoreResult.DatabaseWriteFailure("java.io.IOException"),
        )
        createViewModel()
        openToIdleAndChooseFolder()
        viewModel.onFolderPickerResult(TEST_URI, TEST_FLAGS)
        advanceUntilIdle()
        viewModel.onRestoreConfirmed()
        advanceUntilIdle()
        val state = viewModel.uiState.value as ProductionRestoreUiState.Error
        assertEquals(ProductionRestoreError.RestoreWriteFailure, state.error)
    }

    @Test
    fun fullSuccess_emitsRestoreCommittedBeforeTerminalState() = runTest(testDispatcher) {
        val preview = notStartedPreview()
        gateway.connectOutcome = ProductionRestoreConnectOutcome.Success(FakeRestoreStorage(), TEST_URI)
        gateway.inspectResult = previewReady(preview)
        gateway.executeResult = BackupRestoreCoordinatorResult.FullSuccess(
            preview = preview,
            verification = PostRestoreVerificationResult.NotRequested,
            reconcileResult = CycleResult.ReconcileNoChanges,
            notificationSync = com.me4hik.praktika.data.backup.restore.NotificationSyncObservation.Invoked,
        )
        createViewModel()
        val effectDeferred = async { viewModel.effects.first { it is ProductionRestoreEffect.RestoreCommitted } }
        openToIdleAndChooseFolder()
        viewModel.onFolderPickerResult(TEST_URI, TEST_FLAGS)
        advanceUntilIdle()
        viewModel.onRestoreConfirmed()
        advanceUntilIdle()
        val effect = effectDeferred.await() as ProductionRestoreEffect.RestoreCommitted
        assertFalse(effect.practiceStarted)
        assertTrue(viewModel.uiState.value is ProductionRestoreUiState.RestoreSuccess)
    }

    @Test
    fun runtimeWarning_emitsRestoreCommittedWithPracticeStartedFlag() = runTest(testDispatcher) {
        val preview = richPreview()
        gateway.connectOutcome = ProductionRestoreConnectOutcome.Success(FakeRestoreStorage(), TEST_URI)
        gateway.inspectResult = previewReady(preview)
        gateway.executeResult = BackupRestoreCoordinatorResult.RestoreDataSuccessRuntimeSyncWarning(
            preview = preview,
            verification = PostRestoreVerificationResult.NotRequested,
            dataRestoreCommitted = true,
            reconcileFailureClassName = "java.lang.RuntimeException",
            notificationSync = null,
        )
        createViewModel()
        val effectDeferred = async { viewModel.effects.first { it is ProductionRestoreEffect.RestoreCommitted } }
        openToIdleAndChooseFolder()
        viewModel.onFolderPickerResult(TEST_URI, TEST_FLAGS)
        advanceUntilIdle()
        viewModel.onRestoreConfirmed()
        advanceUntilIdle()
        val effect = effectDeferred.await() as ProductionRestoreEffect.RestoreCommitted
        assertTrue(effect.practiceStarted)
        assertTrue(viewModel.uiState.value is ProductionRestoreUiState.RuntimeSyncWarning)
    }

    @Test
    fun duplicateOpenRestore_ignoredWhileSessionActive() = runTest(testDispatcher) {
        createViewModel()
        viewModel.onOpenRestore()
        advanceUntilIdle()
        viewModel.onOpenRestore()
        advanceUntilIdle()
        assertEquals(1, gateway.eligibilityCheckCalls)
    }

    private fun notStartedPreview(): BackupRestorePreview {
        return BackupRestorePreviewBuilder.fromEnvelope(
            slot = BackupSlotId.B,
            envelope = BackupRestoreFixtures.envelope(BackupRestoreFixtures.notStartedPayload()),
        )
    }

    private fun TestScope.openToIdleAndChooseFolder() {
        viewModel.onOpenRestore()
        advanceUntilIdle()
        viewModel.onChooseFolder()
    }

    private fun previewReady(preview: BackupRestorePreview): BackupRestoreCoordinatorResult.PreviewReady {
        return BackupRestoreCoordinatorResult.PreviewReady(
            preview = preview,
            identity = preview.identity,
        )
    }

    private fun assertTechnicalFieldsAbsent(serialized: String) {
        assertFalse(serialized.contains("checksum", ignoreCase = true))
        assertFalse(serialized.contains("BackupSlotId", ignoreCase = true))
        assertFalse(serialized.contains("selectedSlot", ignoreCase = true))
        assertFalse(serialized.contains("backupSequence", ignoreCase = true))
        assertFalse(serialized.contains("content://", ignoreCase = true))
        assertFalse(serialized.contains("answerText", ignoreCase = true))
    }

    private companion object {
        val TEST_URI: Uri = Uri.parse("content://com.test/tree/backup")
        const val TEST_FLAGS = 3
    }
}

internal class FakeRestoreStorage : BackupStorageProvider {
    override suspend fun inspectSlots(): SlotInspectionResult {
        return SlotInspectionResult(
            slotA = SlotReadResult.Missing,
            slotB = SlotReadResult.Missing,
        )
    }

    override suspend fun readSlot(slot: BackupSlotId): SlotReadResult = SlotReadResult.Missing

    override suspend fun writeSlot(
        slot: BackupSlotId,
        bytes: ByteArray,
        expectedEnvelope: com.me4hik.praktika.data.backup.model.PraktikaBackupEnvelope,
    ): BackupSlotWriteResult = BackupSlotWriteResult.CreateFailed(slot)
}

internal class RecordingProductionRestoreGateway : ProductionRestoreGateway {
    var eligibility: RestoreTargetEligibility = RestoreTargetEligibility.RestoreAvailable
    var eligibilityBeforeRestore: RestoreTargetEligibility = RestoreTargetEligibility.RestoreAvailable
    var treeUriHint: String? = null
    var connectOutcome: ProductionRestoreConnectOutcome =
        ProductionRestoreConnectOutcome.Success(
            FakeRestoreStorage(),
            Uri.parse("content://com.test/tree/backup"),
        )
    var inspectResult: BackupRestoreCoordinatorResult = BackupRestoreCoordinatorResult.NoValidBackup
    var executeResult: BackupRestoreCoordinatorResult = BackupRestoreCoordinatorResult.NoValidBackup
    var executeResults: List<BackupRestoreCoordinatorResult>? = null
    var executeGate: CompletableDeferred<Unit>? = null
    var connectAction: (suspend () -> ProductionRestoreConnectOutcome)? = null
    var activeTreeUri: String? = null
    var lastCommittedUri: String? = null
    var commitShouldSucceed: Boolean = true

    var connectCalls = 0
    var inspectCalls = 0
    var executeCalls = 0
    var clearTreeUriCalls = 0
    var commitActiveBackupFolderCalls = 0
    var releaseRejectedGrantCalls = 0
    var eligibilityCheckCalls = 0

    override suspend fun checkTargetEligibility(): RestoreTargetEligibility {
        eligibilityCheckCalls++
        return if (eligibilityCheckCalls == 1) {
            eligibility
        } else {
            eligibilityBeforeRestore
        }
    }

    override suspend fun readTreeUriHint(): String? = activeTreeUri ?: treeUriHint

    override suspend fun readActiveTreeUri(): String? = activeTreeUri

    override suspend fun clearTreeUri() {
        clearTreeUriCalls++
        activeTreeUri = null
    }

    override suspend fun commitActiveBackupFolder(uri: Uri): Boolean {
        commitActiveBackupFolderCalls++
        if (!commitShouldSucceed) {
            return false
        }
        lastCommittedUri = uri.toString()
        activeTreeUri = uri.toString()
        return true
    }

    override suspend fun releaseRejectedTransientGrant(
        uri: Uri,
        grantFlags: Int,
    ) {
        releaseRejectedGrantCalls++
        if (activeTreeUri != null && Uri.parse(activeTreeUri!!) == uri) {
            return
        }
    }

    override suspend fun connectFolder(
        uri: Uri,
        grantFlags: Int,
    ): ProductionRestoreConnectOutcome {
        connectCalls++
        connectAction?.let { return it() }
        return connectOutcome
    }

    override suspend fun connectAndInspectCandidate(
        uri: Uri,
        grantFlags: Int,
    ): ProductionRestoreCandidateIoResult {
        return when (val connect = connectFolder(uri, grantFlags)) {
            is ProductionRestoreConnectOutcome.Success -> {
                ProductionRestoreCandidateIoResult.Connected(
                    storage = connect.storage,
                    treeUri = connect.treeUri,
                    inspect = inspectLatest(connect.storage),
                )
            }
            ProductionRestoreConnectOutcome.PermissionLost ->
                ProductionRestoreCandidateIoResult.PermissionLost
            ProductionRestoreConnectOutcome.ReadFailure ->
                ProductionRestoreCandidateIoResult.ReadFailure
            ProductionRestoreConnectOutcome.WriteFailure ->
                ProductionRestoreCandidateIoResult.WriteFailure
            ProductionRestoreConnectOutcome.Unexpected ->
                ProductionRestoreCandidateIoResult.Unexpected
        }
    }

    override suspend fun inspectLatest(
        storage: BackupStorageProvider,
    ): BackupRestoreCoordinatorResult {
        inspectCalls++
        return inspectResult
    }

    override suspend fun executeRestore(
        storage: BackupStorageProvider,
        previewIdentity: BackupRestoreSelectedIdentity,
        preview: BackupRestorePreview?,
    ): BackupRestoreCoordinatorResult {
        executeCalls++
        executeGate?.await()
        val queued = executeResults
        if (queued != null && executeCalls <= queued.size) {
            return queued[executeCalls - 1]
        }
        return executeResult
    }
}
