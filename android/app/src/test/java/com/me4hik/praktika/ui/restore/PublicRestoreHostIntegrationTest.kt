package com.me4hik.praktika.ui.restore

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.me4hik.praktika.data.backup.model.BackupSlotId
import com.me4hik.praktika.data.backup.restore.BackupRestoreCoordinatorResult
import com.me4hik.praktika.data.backup.restore.BackupRestoreFixtures
import com.me4hik.praktika.data.backup.restore.BackupRestorePreviewBuilder
import com.me4hik.praktika.data.backup.restore.BackupRestoreResult
import com.me4hik.praktika.data.backup.restore.PostRestoreVerificationResult
import com.me4hik.praktika.data.backup.restore.RestoreTargetEligibility
import com.me4hik.praktika.data.cycle.CycleResult
import com.me4hik.praktika.ui.OnboardingScreen
import com.me4hik.praktika.ui.practice.PracticeRootViewModel
import com.me4hik.praktika.ui.practice.PracticeRootViewModelTestSupport
import com.me4hik.praktika.ui.practice.PracticeUiState
import com.me4hik.praktika.ui.theme.PraktikaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PublicRestoreHostIntegrationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val testDispatcher = StandardTestDispatcher()
    private val seedSchedule = listOf(660, 900, 1140)
    private val restoredSchedule = listOf(900, 660, 1140)

    @Test
    fun publicCta_opensRestoreOverlayThroughSameViewModel() = runTest(testDispatcher) {
        Dispatchers.setMain(testDispatcher)
        val gateway = HostIntegrationGateway()
        val restoreViewModel = createRestoreViewModel(gateway)
        val (practiceViewModel, _) = createPracticeViewModel(seedSchedule)
        advanceUntilIdle()
        mountHost(practiceViewModel, restoreViewModel)
        composeRule.onNodeWithTag(ProductionRestoreTestTags.ONBOARDING_RESTORE_CTA).performClick()
        advanceUntilIdle()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ProductionRestoreTestTags.RESTORE_OVERLAY).assertIsDisplayed()
        assertTrue(restoreViewModel.sessionActive.value)
    }

    @Test
    fun startedFalseCommittedRestore_syncsDraftBeforeContinue() {
        val gateway = HostIntegrationGateway()
        val preview = notStartedPreview()
        gateway.connectOutcome = ProductionRestoreConnectOutcome.Success(FakeHostStorage(), TEST_URI)
        gateway.inspectResult = previewReady(preview)
        gateway.executeResult = BackupRestoreCoordinatorResult.FullSuccess(
            preview = preview,
            verification = PostRestoreVerificationResult.NotRequested,
            reconcileResult = CycleResult.ReconcileNoChanges,
            notificationSync = com.me4hik.praktika.data.backup.restore.NotificationSyncObservation.Invoked,
        )
        val restoreViewModel = createRestoreViewModel(gateway)
        val (practiceViewModel, scheduleReadRepository) = createPracticeViewModelBlocking(seedSchedule)
        practiceViewModel.onSlotTimeChanged(1, 630)
        scheduleReadRepository.emit(
            PracticeRootViewModelTestSupport.defaultScheduleSnapshot(restoredSchedule),
        )
        var committedCallbacks = 0
        mountHost(
            practiceViewModel = practiceViewModel,
            restoreViewModel = restoreViewModel,
            onRestoreCommitted = { practiceStarted ->
                committedCallbacks++
                if (!practiceStarted) {
                    practiceViewModel.discardOnboardingDraftFromPersistedSchedule()
                }
            },
        )
        composeRule.runOnIdle {
            openAndRestore(restoreViewModel)
        }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            committedCallbacks > 0 &&
                (practiceViewModel.uiState.value as? PracticeUiState.NotStarted)
                    ?.slots?.firstOrNull { it.slotIndex == 1 }?.timeText == "15:00"
        }
        assertEquals(1, committedCallbacks)
        val beforeContinue = practiceViewModel.uiState.value as PracticeUiState.NotStarted
        assertEquals("15:00", beforeContinue.slots.first { it.slotIndex == 1 }.timeText)
        assertEquals("11:00", beforeContinue.slots.first { it.slotIndex == 2 }.timeText)
        assertFalse(beforeContinue.isScheduleDirty)
        composeRule.onNodeWithTag(ProductionRestoreTestTags.RESTORE_CONTINUE).performClick()
        composeRule.waitForIdle()
        val afterContinue = practiceViewModel.uiState.value as PracticeUiState.NotStarted
        assertEquals("15:00", afterContinue.slots.first { it.slotIndex == 1 }.timeText)
        assertEquals("11:00", afterContinue.slots.first { it.slotIndex == 2 }.timeText)
    }

    @Test
    fun runtimeWarningNotStartedCommitted_syncsDraft() {
        val gateway = HostIntegrationGateway()
        val preview = notStartedPreview()
        gateway.connectOutcome = ProductionRestoreConnectOutcome.Success(FakeHostStorage(), TEST_URI)
        gateway.inspectResult = previewReady(preview)
        gateway.executeResult = BackupRestoreCoordinatorResult.RestoreDataSuccessRuntimeSyncWarning(
            preview = preview,
            verification = PostRestoreVerificationResult.NotRequested,
            dataRestoreCommitted = true,
            reconcileFailureClassName = "java.lang.RuntimeException",
            notificationSync = null,
        )
        val restoreViewModel = createRestoreViewModel(gateway)
        val (practiceViewModel, scheduleReadRepository) = createPracticeViewModelBlocking(seedSchedule)
        scheduleReadRepository.emit(
            PracticeRootViewModelTestSupport.defaultScheduleSnapshot(restoredSchedule),
        )
        var committedCallbacks = 0
        mountHost(
            practiceViewModel = practiceViewModel,
            restoreViewModel = restoreViewModel,
            onRestoreCommitted = { practiceStarted ->
                committedCallbacks++
                if (!practiceStarted) {
                    practiceViewModel.discardOnboardingDraftFromPersistedSchedule()
                }
            },
        )
        composeRule.runOnIdle {
            openAndRestore(restoreViewModel)
        }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            committedCallbacks > 0 &&
                (practiceViewModel.uiState.value as? PracticeUiState.NotStarted)
                    ?.slots?.firstOrNull { it.slotIndex == 1 }?.timeText == "15:00"
        }
        assertEquals(1, committedCallbacks)
        val state = practiceViewModel.uiState.value as PracticeUiState.NotStarted
        assertEquals("15:00", state.slots.first { it.slotIndex == 1 }.timeText)
    }

    @Test
    fun startedTrueCommittedRestore_doesNotResetDraft() = runTest(testDispatcher) {
        Dispatchers.setMain(testDispatcher)
        val gateway = HostIntegrationGateway()
        val preview = startedPreview()
        gateway.connectOutcome = ProductionRestoreConnectOutcome.Success(FakeHostStorage(), TEST_URI)
        gateway.inspectResult = previewReady(preview)
        gateway.executeResult = BackupRestoreCoordinatorResult.FullSuccess(
            preview = preview,
            verification = PostRestoreVerificationResult.NotRequested,
            reconcileResult = CycleResult.ReconcileNoChanges,
            notificationSync = com.me4hik.praktika.data.backup.restore.NotificationSyncObservation.Invoked,
        )
        val restoreViewModel = createRestoreViewModel(gateway)
        val (practiceViewModel, _) = createPracticeViewModel(seedSchedule)
        advanceUntilIdle()
        practiceViewModel.onSlotTimeChanged(1, 630)
        advanceUntilIdle()
        var draftResetCalls = 0
        mountHost(
            practiceViewModel = practiceViewModel,
            restoreViewModel = restoreViewModel,
            onRestoreCommitted = { practiceStarted ->
                if (!practiceStarted) {
                    draftResetCalls++
                    practiceViewModel.discardOnboardingDraftFromPersistedSchedule()
                }
            },
        )
        openAndRestore(restoreViewModel)
        advanceUntilIdle()
        composeRule.waitForIdle()
        assertEquals(0, draftResetCalls)
    }

    @Test
    fun failureBeforeCommit_doesNotEmitDraftReset() = runTest(testDispatcher) {
        Dispatchers.setMain(testDispatcher)
        val gateway = HostIntegrationGateway()
        val preview = notStartedPreview()
        gateway.connectOutcome = ProductionRestoreConnectOutcome.Success(FakeHostStorage(), TEST_URI)
        gateway.inspectResult = previewReady(preview)
        gateway.executeResult = BackupRestoreCoordinatorResult.RestoreCoreFailure(
            restoreResult = BackupRestoreResult.DatabaseWriteFailure("java.io.IOException"),
        )
        val restoreViewModel = createRestoreViewModel(gateway)
        val (practiceViewModel, _) = createPracticeViewModel(seedSchedule)
        advanceUntilIdle()
        practiceViewModel.onSlotTimeChanged(1, 630)
        advanceUntilIdle()
        var draftResetCalls = 0
        mountHost(
            practiceViewModel = practiceViewModel,
            restoreViewModel = restoreViewModel,
            onRestoreCommitted = { practiceStarted ->
                if (!practiceStarted) {
                    draftResetCalls++
                    practiceViewModel.discardOnboardingDraftFromPersistedSchedule()
                }
            },
        )
        openAndRestore(restoreViewModel)
        advanceUntilIdle()
        composeRule.waitForIdle()
        assertEquals(0, draftResetCalls)
        val state = practiceViewModel.uiState.value as PracticeUiState.NotStarted
        assertEquals("10:30", state.slots.first { it.slotIndex == 1 }.timeText)
    }

    @Test
    fun doubleRestoreCtaTap_opensSessionOnce() = runTest(testDispatcher) {
        Dispatchers.setMain(testDispatcher)
        val gateway = HostIntegrationGateway()
        val restoreViewModel = createRestoreViewModel(gateway)
        val (practiceViewModel, _) = createPracticeViewModel(seedSchedule)
        advanceUntilIdle()
        mountHost(practiceViewModel, restoreViewModel)
        composeRule.onNodeWithTag(ProductionRestoreTestTags.ONBOARDING_RESTORE_CTA).performClick()
        composeRule.onNodeWithTag(ProductionRestoreTestTags.ONBOARDING_RESTORE_CTA).performClick()
        advanceUntilIdle()
        assertEquals(1, gateway.eligibilityCheckCalls)
    }

    private fun mountHost(
        practiceViewModel: PracticeRootViewModel,
        restoreViewModel: ProductionRestoreViewModel,
        onRestoreCommitted: (Boolean) -> Unit = { practiceStarted ->
            if (!practiceStarted) {
                practiceViewModel.discardOnboardingDraftFromPersistedSchedule()
            }
        },
    ) {
        composeRule.setContent {
            PraktikaTheme {
                val practiceState by practiceViewModel.uiState.collectAsStateWithLifecycle()
                ProductionRestoreEffects(
                    viewModel = restoreViewModel,
                    onRestoreCommitted = onRestoreCommitted,
                )
                val notStarted = practiceState as? PracticeUiState.NotStarted
                if (notStarted != null) {
                    OnboardingScreen(
                        state = notStarted,
                        onSlotTimeChange = practiceViewModel::onSlotTimeChanged,
                        onStartPractice = practiceViewModel::onStartPracticeClicked,
                        onRestoreBackup = restoreViewModel::onOpenRestore,
                    )
                }
                ProductionRestoreSessionHost(viewModel = restoreViewModel)
            }
        }
        composeRule.waitForIdle()
    }

    private fun openAndRestore(restoreViewModel: ProductionRestoreViewModel) {
        restoreViewModel.onOpenRestore()
        restoreViewModel.onChooseFolder()
        restoreViewModel.onFolderPickerResult(TEST_URI, TEST_FLAGS)
        restoreViewModel.onRestoreConfirmed()
    }

    private fun createRestoreViewModel(gateway: HostIntegrationGateway): ProductionRestoreViewModel {
        return ProductionRestoreViewModel(
            gateway = gateway,
            previewMapper = BackupRestorePreviewMapper(
                dateFormatter = ProductionRestoreDateFormatter(
                    zoneId = java.time.ZoneId.of("UTC"),
                ),
            ),
        )
    }

    private fun createPracticeViewModelBlocking(
        scheduleMinutes: List<Int>,
    ): Pair<PracticeRootViewModel, PracticeRootViewModelTestSupport.FakeScheduleReadRepository> {
        val readRepository = PracticeRootViewModelTestSupport.FakePracticeReadRepository()
        val scheduleReadRepository = PracticeRootViewModelTestSupport.FakeScheduleReadRepository()
        readRepository.emit(PracticeRootViewModelTestSupport.notStartedSnapshot())
        scheduleReadRepository.emit(
            PracticeRootViewModelTestSupport.defaultScheduleSnapshot(scheduleMinutes),
        )
        val viewModel = PracticeRootViewModelTestSupport.createViewModel(
            readRepository = readRepository,
            scheduleReadRepository = scheduleReadRepository,
            commandDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
        )
        composeRule.waitForIdle()
        return viewModel to scheduleReadRepository
    }

    private fun createPracticeViewModel(
        scheduleMinutes: List<Int>,
    ): Pair<PracticeRootViewModel, PracticeRootViewModelTestSupport.FakeScheduleReadRepository> {
        val readRepository = PracticeRootViewModelTestSupport.FakePracticeReadRepository()
        val scheduleReadRepository = PracticeRootViewModelTestSupport.FakeScheduleReadRepository()
        readRepository.emit(PracticeRootViewModelTestSupport.notStartedSnapshot())
        scheduleReadRepository.emit(
            PracticeRootViewModelTestSupport.defaultScheduleSnapshot(scheduleMinutes),
        )
        val viewModel = PracticeRootViewModelTestSupport.createViewModel(
            readRepository = readRepository,
            scheduleReadRepository = scheduleReadRepository,
            commandDispatcher = testDispatcher,
        )
        return viewModel to scheduleReadRepository
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

private class FakeHostStorage : com.me4hik.praktika.data.backup.storage.BackupStorageProvider {
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

private class HostIntegrationGateway : ProductionRestoreGateway {
    var connectOutcome: ProductionRestoreConnectOutcome = ProductionRestoreConnectOutcome.Unexpected
    var inspectResult: BackupRestoreCoordinatorResult = BackupRestoreCoordinatorResult.NoValidBackup
    var executeResult: BackupRestoreCoordinatorResult = BackupRestoreCoordinatorResult.NoValidBackup
    var eligibilityCheckCalls = 0

    override suspend fun checkTargetEligibility(): RestoreTargetEligibility {
        eligibilityCheckCalls++
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
