package com.me4hik.praktika.ui.restore

import android.net.Uri
import com.me4hik.praktika.data.backup.model.BackupSlotId
import com.me4hik.praktika.data.backup.restore.BackupRestoreCoordinatorResult
import com.me4hik.praktika.data.backup.restore.BackupRestoreFixtures
import com.me4hik.praktika.data.backup.restore.BackupRestorePreviewBuilder
import com.me4hik.praktika.data.backup.restore.BackupRestoreResult
import com.me4hik.praktika.data.backup.restore.NotificationSyncObservation
import com.me4hik.praktika.data.backup.restore.PostRestoreVerificationResult
import com.me4hik.praktika.data.backup.write.CommittedHandoffStartResult
import com.me4hik.praktika.data.cycle.CycleResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class Stage62B2ARestoreViewModelSessionTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var gateway: RecordingProductionRestoreGateway
    private lateinit var session: RecordingRestoreSessionController
    private lateinit var viewModel: ProductionRestoreViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        gateway = RecordingProductionRestoreGateway()
        session = RecordingRestoreSessionController()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() {
        viewModel = ProductionRestoreViewModel(
            gateway = gateway,
            previewMapper = BackupRestorePreviewMapper(
                dateFormatter = ProductionRestoreDateFormatter(
                    zoneId = java.time.ZoneId.of("UTC"),
                ),
            ),
            restoreSessionController = session,
        )
    }

    private fun previewReadyGateway() {
        val preview = BackupRestorePreviewBuilder.fromEnvelope(
            slot = BackupSlotId.B,
            envelope = BackupRestoreFixtures.richEnvelope(),
        )
        gateway.connectOutcome = ProductionRestoreConnectOutcome.Success(
            FakeRestoreStorage(),
            Uri.parse("content://com.test/tree/backup"),
        )
        gateway.inspectResult = BackupRestoreCoordinatorResult.PreviewReady(
            preview = preview,
            identity = preview.identity,
        )
    }

    @Test
    fun pickerNull_doesNotBeginSession() = runTest(testDispatcher) {
        createViewModel()
        viewModel.onOpenRestore()
        advanceUntilIdle()
        viewModel.onChooseFolder()
        advanceUntilIdle()
        viewModel.onFolderPickerResult(uri = null, grantFlags = 0)
        advanceUntilIdle()
        assertEquals(0, session.beginCount)
        assertEquals(ProductionRestoreUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun connectInspect_beginsBeforeIo_andKeepsSessionOnPreview() = runTest(testDispatcher) {
        previewReadyGateway()
        createViewModel()
        viewModel.onOpenRestore()
        advanceUntilIdle()
        viewModel.onChooseFolder()
        advanceUntilIdle()
        viewModel.onFolderPickerResult(Uri.parse("content://com.test/tree/backup"), grantFlags = 3)
        advanceUntilIdle()
        assertEquals(1, session.beginCount)
        assertTrue(viewModel.uiState.value is ProductionRestoreUiState.PreviewReady)
        assertEquals(0, session.endNonCommittedCount)
        assertEquals(0, session.startCommittedCount)
    }

    @Test
    fun connectFailure_endsNonCommitted() = runTest(testDispatcher) {
        gateway.connectOutcome = ProductionRestoreConnectOutcome.PermissionLost
        createViewModel()
        viewModel.onOpenRestore()
        advanceUntilIdle()
        viewModel.onChooseFolder()
        advanceUntilIdle()
        viewModel.onFolderPickerResult(Uri.parse("content://com.test/tree/backup"), grantFlags = 3)
        advanceUntilIdle()
        assertEquals(1, session.beginCount)
        assertEquals(1, session.endNonCommittedCount)
    }

    @Test
    fun dismissPreview_endsNonCommitted() = runTest(testDispatcher) {
        previewReadyGateway()
        createViewModel()
        viewModel.onOpenRestore()
        advanceUntilIdle()
        viewModel.onChooseFolder()
        advanceUntilIdle()
        viewModel.onFolderPickerResult(Uri.parse("content://com.test/tree/backup"), grantFlags = 3)
        advanceUntilIdle()
        viewModel.onDismiss()
        advanceUntilIdle()
        assertEquals(1, session.endNonCommittedCount)
    }

    @Test
    fun fullSuccess_startsCommittedHandoff_beforeTerminal() = runTest(testDispatcher) {
        previewReadyGateway()
        val preview = (gateway.inspectResult as BackupRestoreCoordinatorResult.PreviewReady).preview
        gateway.executeResult = BackupRestoreCoordinatorResult.FullSuccess(
            preview = preview,
            verification = PostRestoreVerificationResult.NotRequested,
            reconcileResult = CycleResult.ReconcileNoChanges,
            notificationSync = NotificationSyncObservation.Invoked,
        )
        createViewModel()
        viewModel.onOpenRestore()
        advanceUntilIdle()
        viewModel.onChooseFolder()
        advanceUntilIdle()
        viewModel.onFolderPickerResult(Uri.parse("content://com.test/tree/backup"), grantFlags = 3)
        advanceUntilIdle()
        viewModel.onRestoreConfirmed()
        advanceUntilIdle()
        assertEquals(1, session.startCommittedCount)
        assertTrue(session.startCommittedBeforeTerminal)
        assertEquals(0, session.endNonCommittedCount)
        assertTrue(viewModel.uiState.value is ProductionRestoreUiState.RestoreSuccess)
    }

    @Test
    fun runtimeWarning_startsCommittedHandoff() = runTest(testDispatcher) {
        previewReadyGateway()
        val preview = (gateway.inspectResult as BackupRestoreCoordinatorResult.PreviewReady).preview
        gateway.executeResult = BackupRestoreCoordinatorResult.RestoreDataSuccessRuntimeSyncWarning(
            preview = preview,
            verification = PostRestoreVerificationResult.NotRequested,
            dataRestoreCommitted = true,
            reconcileFailureClassName = "Boom",
            notificationSync = null,
        )
        createViewModel()
        viewModel.onOpenRestore()
        advanceUntilIdle()
        viewModel.onChooseFolder()
        advanceUntilIdle()
        viewModel.onFolderPickerResult(Uri.parse("content://com.test/tree/backup"), grantFlags = 3)
        advanceUntilIdle()
        viewModel.onRestoreConfirmed()
        advanceUntilIdle()
        assertEquals(1, session.startCommittedCount)
        assertTrue(viewModel.uiState.value is ProductionRestoreUiState.RuntimeSyncWarning)
    }

    @Test
    fun precommitFailure_endsCancelled() = runTest(testDispatcher) {
        previewReadyGateway()
        gateway.executeResult = BackupRestoreCoordinatorResult.RestoreCoreFailure(
            BackupRestoreResult.InvalidBackup(
                com.me4hik.praktika.data.backup.restore.BackupRestoreDomainFailureReason.UNSUPPORTED_SCHEMA,
            ),
        )
        createViewModel()
        viewModel.onOpenRestore()
        advanceUntilIdle()
        viewModel.onChooseFolder()
        advanceUntilIdle()
        viewModel.onFolderPickerResult(Uri.parse("content://com.test/tree/backup"), grantFlags = 3)
        advanceUntilIdle()
        viewModel.onRestoreConfirmed()
        advanceUntilIdle()
        assertEquals(1, session.endNonCommittedCount)
        assertEquals(0, session.startCommittedCount)
    }

    @Test
    fun verificationFailure_stillStartsCommittedHandoff() = runTest(testDispatcher) {
        previewReadyGateway()
        val preview = (gateway.inspectResult as BackupRestoreCoordinatorResult.PreviewReady).preview
        gateway.executeResult = BackupRestoreCoordinatorResult.RestoreDataSuccessVerificationFailure(
            preview = preview,
            verification = PostRestoreVerificationResult.Mismatch("probe"),
        )
        createViewModel()
        viewModel.onOpenRestore()
        advanceUntilIdle()
        viewModel.onChooseFolder()
        advanceUntilIdle()
        viewModel.onFolderPickerResult(Uri.parse("content://com.test/tree/backup"), grantFlags = 3)
        advanceUntilIdle()
        viewModel.onRestoreConfirmed()
        advanceUntilIdle()
        assertEquals(1, session.startCommittedCount)
        assertEquals(0, session.endNonCommittedCount)
        assertTrue(viewModel.uiState.value is ProductionRestoreUiState.Error)
    }

    @Test
    fun onCleared_nonCommitted_endsCancelled_once() = runTest(testDispatcher) {
        previewReadyGateway()
        createViewModel()
        viewModel.onOpenRestore()
        advanceUntilIdle()
        viewModel.onChooseFolder()
        advanceUntilIdle()
        viewModel.onFolderPickerResult(Uri.parse("content://com.test/tree/backup"), grantFlags = 3)
        advanceUntilIdle()
        viewModel.releaseBackupSessionOnAbnormalClearForTests()
        assertEquals(1, session.endNonCommittedCount)
        viewModel.releaseBackupSessionOnAbnormalClearForTests()
        assertEquals(1, session.endNonCommittedCount)
    }

    @Test
    fun onCleared_afterCommittedHandoff_noCancel() = runTest(testDispatcher) {
        previewReadyGateway()
        val preview = (gateway.inspectResult as BackupRestoreCoordinatorResult.PreviewReady).preview
        gateway.executeResult = BackupRestoreCoordinatorResult.FullSuccess(
            preview = preview,
            verification = PostRestoreVerificationResult.NotRequested,
            reconcileResult = CycleResult.ReconcileNoChanges,
            notificationSync = NotificationSyncObservation.Invoked,
        )
        createViewModel()
        viewModel.onOpenRestore()
        advanceUntilIdle()
        viewModel.onChooseFolder()
        advanceUntilIdle()
        viewModel.onFolderPickerResult(Uri.parse("content://com.test/tree/backup"), grantFlags = 3)
        advanceUntilIdle()
        viewModel.onRestoreConfirmed()
        advanceUntilIdle()
        assertEquals(1, session.startCommittedCount)
        viewModel.releaseBackupSessionOnAbnormalClearForTests()
        assertEquals(0, session.endNonCommittedCount)
    }

    @Test
    fun chooseDifferentFolder_keepsSessionWithoutEnd() = runTest(testDispatcher) {
        previewReadyGateway()
        createViewModel()
        viewModel.onOpenRestore()
        advanceUntilIdle()
        viewModel.onChooseFolder()
        advanceUntilIdle()
        viewModel.onFolderPickerResult(Uri.parse("content://com.test/tree/backup"), grantFlags = 3)
        advanceUntilIdle()
        assertEquals(1, session.beginCount)
        viewModel.onChooseFolder()
        advanceUntilIdle()
        assertEquals(0, session.endNonCommittedCount)
        assertEquals(1, session.beginCount)
        viewModel.onFolderPickerResult(uri = null, grantFlags = 0)
        advanceUntilIdle()
        assertEquals(0, session.endNonCommittedCount)
        assertEquals(ProductionRestoreUiState.Idle, viewModel.uiState.value)
    }
}

private class RecordingRestoreSessionController : RestoreBackupRuntimeBridge {
    var beginCount = 0
    var endNonCommittedCount = 0
    var startCommittedCount = 0
    var startCommittedBeforeTerminal = false
    private var active = false
    private var committed = false

    override fun beginRestoreSession() {
        beginCount++
        active = true
    }

    override fun endNonCommittedSession() {
        endNonCommittedCount++
        active = false
    }

    override fun startCommittedHandoff(): CommittedHandoffStartResult {
        startCommittedCount++
        startCommittedBeforeTerminal = true
        committed = true
        active = false
        return CommittedHandoffStartResult.Started
    }

    override fun isRestoreSessionActive(): Boolean = active || committed
}
