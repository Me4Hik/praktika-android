package com.me4hik.praktika.ui.restore

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.me4hik.praktika.data.backup.model.BackupSlotId
import com.me4hik.praktika.data.backup.restore.BackupRestoreCoordinatorResult
import com.me4hik.praktika.data.backup.restore.BackupRestoreFixtures
import com.me4hik.praktika.data.backup.restore.BackupRestorePreviewBuilder
import com.me4hik.praktika.data.backup.restore.PostRestoreVerificationResult
import com.me4hik.praktika.data.backup.restore.RestoreTargetEligibility
import com.me4hik.praktika.data.cycle.CycleResult
import com.me4hik.praktika.ui.theme.PraktikaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProductionRestoreEffectsHostTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val testDispatcher = StandardTestDispatcher()

    @Test
    fun restoreCommittedFalse_invokesHostCallbackOnce() = runTest(testDispatcher) {
        Dispatchers.setMain(testDispatcher)
        val gateway = RecordingEffectsGateway()
        val preview = notStartedPreview()
        gateway.connectOutcome = ProductionRestoreConnectOutcome.Success(FakeEffectsStorage(), TEST_URI)
        gateway.inspectResult = previewReady(preview)
        gateway.executeResult = BackupRestoreCoordinatorResult.FullSuccess(
            preview = preview,
            verification = PostRestoreVerificationResult.NotRequested,
            reconcileResult = CycleResult.ReconcileNoChanges,
            notificationSync = com.me4hik.praktika.data.backup.restore.NotificationSyncObservation.Invoked,
        )
        val viewModel = createViewModel(gateway)
        var callbackCount = 0
        var lastPracticeStarted: Boolean? = null
        composeRule.setContent {
            PraktikaTheme {
                ProductionRestoreEffects(
                    viewModel = viewModel,
                    onRestoreCommitted = { practiceStarted ->
                        callbackCount++
                        lastPracticeStarted = practiceStarted
                    },
                )
            }
        }
        composeRule.waitForIdle()
        viewModel.onOpenRestore()
        advanceUntilIdle()
        viewModel.onChooseFolder()
        advanceUntilIdle()
        viewModel.onFolderPickerResult(TEST_URI, TEST_FLAGS)
        advanceUntilIdle()
        viewModel.onRestoreConfirmed()
        advanceUntilIdle()
        composeRule.waitForIdle()
        assertEquals(1, callbackCount)
        assertEquals(false, lastPracticeStarted)
    }

    @Test
    fun restoreCommittedTrue_invokesHostCallbackOnce() = runTest(testDispatcher) {
        Dispatchers.setMain(testDispatcher)
        val gateway = RecordingEffectsGateway()
        val preview = startedPreview()
        gateway.connectOutcome = ProductionRestoreConnectOutcome.Success(FakeEffectsStorage(), TEST_URI)
        gateway.inspectResult = previewReady(preview)
        gateway.executeResult = BackupRestoreCoordinatorResult.FullSuccess(
            preview = preview,
            verification = PostRestoreVerificationResult.NotRequested,
            reconcileResult = CycleResult.ReconcileNoChanges,
            notificationSync = com.me4hik.praktika.data.backup.restore.NotificationSyncObservation.Invoked,
        )
        val viewModel = createViewModel(gateway)
        var callbackCount = 0
        var lastPracticeStarted: Boolean? = null
        composeRule.setContent {
            PraktikaTheme {
                ProductionRestoreEffects(
                    viewModel = viewModel,
                    onRestoreCommitted = { practiceStarted ->
                        callbackCount++
                        lastPracticeStarted = practiceStarted
                    },
                )
            }
        }
        composeRule.waitForIdle()
        viewModel.onOpenRestore()
        advanceUntilIdle()
        viewModel.onChooseFolder()
        advanceUntilIdle()
        viewModel.onFolderPickerResult(TEST_URI, TEST_FLAGS)
        advanceUntilIdle()
        viewModel.onRestoreConfirmed()
        advanceUntilIdle()
        composeRule.waitForIdle()
        assertEquals(1, callbackCount)
        assertEquals(true, lastPracticeStarted)
    }

    @Test
    fun restoreCommittedEffect_containsOnlyPracticeStartedBoolean() {
        val effect = ProductionRestoreEffect.RestoreCommitted(practiceStarted = false)
        val serialized = effect.toString()
        assertFalse(serialized.contains("content://", ignoreCase = true))
        assertFalse(serialized.contains("checksum", ignoreCase = true))
        assertFalse(serialized.contains("BackupSlotId", ignoreCase = true))
        assertFalse(serialized.contains("answer", ignoreCase = true))
    }

    private fun createViewModel(gateway: RecordingEffectsGateway): ProductionRestoreViewModel {
        return ProductionRestoreViewModel(
            gateway = gateway,
            previewMapper = BackupRestorePreviewMapper(
                dateFormatter = ProductionRestoreDateFormatter(
                    zoneId = java.time.ZoneId.of("UTC"),
                ),
            ),
        )
    }

    private fun startedPreview() = BackupRestorePreviewBuilder.fromEnvelope(
        slot = BackupSlotId.B,
        envelope = BackupRestoreFixtures.richEnvelope(),
    )

    private fun notStartedPreview() = BackupRestorePreviewBuilder.fromEnvelope(
        slot = BackupSlotId.B,
        envelope = BackupRestoreFixtures.envelope(BackupRestoreFixtures.notStartedPayload()),
    )

    private fun previewReady(
        preview: com.me4hik.praktika.data.backup.restore.BackupRestorePreview,
    ): BackupRestoreCoordinatorResult.PreviewReady {
        return BackupRestoreCoordinatorResult.PreviewReady(
            preview = preview,
            identity = preview.identity,
        )
    }

    private companion object {
        val TEST_URI: android.net.Uri = android.net.Uri.parse("content://com.test/tree/backup")
        const val TEST_FLAGS = 3
    }
}

private class FakeEffectsStorage : com.me4hik.praktika.data.backup.storage.BackupStorageProvider {
    override suspend fun inspectSlots(): com.me4hik.praktika.data.backup.storage.SlotInspectionResult {
        return com.me4hik.praktika.data.backup.storage.SlotInspectionResult(
            slotA = com.me4hik.praktika.data.backup.storage.SlotReadResult.Missing,
            slotB = com.me4hik.praktika.data.backup.storage.SlotReadResult.Missing,
        )
    }

    override suspend fun readSlot(
        slot: com.me4hik.praktika.data.backup.model.BackupSlotId,
    ): com.me4hik.praktika.data.backup.storage.SlotReadResult {
        return com.me4hik.praktika.data.backup.storage.SlotReadResult.Missing
    }

    override suspend fun writeSlot(
        slot: com.me4hik.praktika.data.backup.model.BackupSlotId,
        bytes: ByteArray,
        expectedEnvelope: com.me4hik.praktika.data.backup.model.PraktikaBackupEnvelope,
    ): com.me4hik.praktika.data.backup.storage.BackupSlotWriteResult {
        return com.me4hik.praktika.data.backup.storage.BackupSlotWriteResult.CreateFailed(slot)
    }
}

private class RecordingEffectsGateway : ProductionRestoreGateway {
    var connectOutcome: ProductionRestoreConnectOutcome =
        ProductionRestoreConnectOutcome.Unexpected
    var inspectResult: BackupRestoreCoordinatorResult = BackupRestoreCoordinatorResult.NoValidBackup
    var executeResult: BackupRestoreCoordinatorResult = BackupRestoreCoordinatorResult.NoValidBackup

    override suspend fun checkTargetEligibility(): RestoreTargetEligibility {
        return RestoreTargetEligibility.RestoreAvailable
    }

    override suspend fun readTreeUriHint(): String? = null

    override suspend fun readActiveTreeUri(): String? = null

    override suspend fun clearTreeUri() = Unit

    override suspend fun connectFolder(
        uri: android.net.Uri,
        grantFlags: Int,
    ): ProductionRestoreConnectOutcome = connectOutcome

    override suspend fun connectAndInspectCandidate(
        uri: android.net.Uri,
        grantFlags: Int,
    ): ProductionRestoreCandidateIoResult {
        return when (val connect = connectFolder(uri, grantFlags)) {
            is ProductionRestoreConnectOutcome.Success ->
                ProductionRestoreCandidateIoResult.Connected(
                    storage = connect.storage,
                    treeUri = connect.treeUri,
                    inspect = inspectLatest(connect.storage),
                )
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

    override suspend fun commitActiveBackupFolder(uri: android.net.Uri): Boolean = true

    override suspend fun releaseRejectedTransientGrant(
        uri: android.net.Uri,
        grantFlags: Int,
    ) = Unit

    override suspend fun inspectLatest(
        storage: com.me4hik.praktika.data.backup.storage.BackupStorageProvider,
    ): BackupRestoreCoordinatorResult = inspectResult

    override suspend fun executeRestore(
        storage: com.me4hik.praktika.data.backup.storage.BackupStorageProvider,
        previewIdentity: com.me4hik.praktika.data.backup.restore.BackupRestoreSelectedIdentity,
        preview: com.me4hik.praktika.data.backup.restore.BackupRestorePreview?,
    ): BackupRestoreCoordinatorResult = executeResult
}
