// 13.08.2026 DATA VAULT Stage 6.3C1 cursor by Me4Hik START - harness routing / stash / revoke host tests
package com.me4hik.praktika.ui.acceptance

import android.content.Intent
import android.net.Uri
import com.me4hik.praktika.data.backup.write.InMemoryBackupWriteStateRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class DeviceAcceptanceHarnessControllerTest {
    private val authUri = "content://com.android.externalstorage.documents/tree/primary%3APraktika_stage6_3c_B"
    private lateinit var stash: InMemoryDeviceAcceptanceUriStash
    private lateinit var treeReader: RecordingTreeReader
    private lateinit var releasedUris: MutableList<Uri>
    private lateinit var grantPresent: MutableSet<Uri>
    private lateinit var revoker: DeviceAcceptanceGrantRevoker
    private lateinit var writeState: InMemoryBackupWriteStateRepository

    @Before
    fun setUp() {
        stash = InMemoryDeviceAcceptanceUriStash()
        treeReader = RecordingTreeReader(
            DeviceAcceptanceFolderReadResult.Ok(
                slotA = DeviceAcceptanceSlotBytes.Missing,
                slotB = DeviceAcceptanceSlotBytes.Missing,
            ),
        )
        releasedUris = mutableListOf()
        grantPresent = mutableSetOf(Uri.parse(authUri))
        revoker = DeviceAcceptanceGrantRevoker(
            permissionReleaser = DeviceAcceptancePermissionReleaser { uri, flags ->
                assertEquals(DeviceAcceptanceGrantRevoker.RELEASE_FLAGS, flags)
                assertEquals(0, flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                releasedUris += uri
                grantPresent.remove(uri)
            },
            grantProbe = DeviceAcceptancePersistedGrantProbe { uri -> grantPresent.contains(uri) },
        )
        writeState = InMemoryBackupWriteStateRepository()
        AcceleratedBackupWriteStateAccess.clearForTests()
        AcceleratedBackupWriteStateAccess.bind(writeState)
    }

    @Test
    fun actionParse_symbolicOnly_rejectsUriShellArgs() {
        assertEquals(
            DeviceAcceptanceHarnessAction.INSPECT_CURRENT,
            DeviceAcceptanceHarnessAction.parseSymbolic("INSPECT_CURRENT"),
        )
        assertNull(DeviceAcceptanceHarnessAction.parseSymbolic("content://evil"))
        assertNull(DeviceAcceptanceHarnessAction.parseSymbolic("INSPECT_CURRENT content://x"))
        assertNull(DeviceAcceptanceHarnessAction.parseSymbolic(null))
    }

    @Test
    fun unknownAction_safeFailure() = runTest {
        val controller = controller()
        val report = controller.unknownActionReport("BOGUS")
        assertEquals(DeviceAcceptanceResultStatus.UNKNOWN_ACTION, report.resultStatus)
        assertFalse(DeviceAcceptanceReportSerializer.toJson(report).contains("content://"))
    }

    @Test
    fun inspectCurrent_notConfigured_whenAuthNull() = runTest {
        val controller = controller()
        val result = controller.execute(DeviceAcceptanceHarnessAction.INSPECT_CURRENT)
        val report = (result as DeviceAcceptanceControllerResult.Completed).report
        assertEquals(DeviceAcceptanceResultStatus.NOT_CONFIGURED, report.resultStatus)
        assertEquals(0, treeReader.calls)
    }

    @Test
    fun inspectCurrent_readOnly_doesNotMutateWriteState() = runTest {
        writeState.setAuthorizedForTests(authUri)
        writeState.markBackupSuccess(99L)
        val before = writeState.snapshot()
        val result = controller().execute(DeviceAcceptanceHarnessAction.INSPECT_CURRENT)
        assertTrue(result is DeviceAcceptanceControllerResult.Completed)
        val after = writeState.snapshot()
        assertEquals(before, after)
        assertEquals(1, treeReader.calls)
        assertTrue(treeReader.requirePersistedFlags.single())
    }

    @Test
    fun stashCurrentAsA_storesUriPrivately_andDoesNotMutateWriteState_andReportOmitsUri() = runTest {
        writeState.setAuthorizedForTests(authUri)
        val before = writeState.snapshot()
        val report = (
            controller().execute(DeviceAcceptanceHarnessAction.STASH_CURRENT_AS_A)
                as DeviceAcceptanceControllerResult.Completed
            ).report
        assertEquals(DeviceAcceptanceResultStatus.STASHED_A, report.resultStatus)
        assertEquals(authUri, stash.loadA())
        assertEquals(before, writeState.snapshot())
        val json = DeviceAcceptanceReportSerializer.toJson(report)
        assertFalse(json.contains(authUri))
        assertFalse(json.contains("content://"))
    }

    @Test
    fun stashOverwrite_andClear() {
        stash.stashA("content://a1")
        stash.stashA("content://a2")
        assertEquals("content://a2", stash.loadA())
        stash.stashB("content://b1")
        assertEquals("content://b1", stash.loadB())
        stash.clear()
        assertNull(stash.loadA())
        assertNull(stash.loadB())
    }

    @Test
    fun inspectStashed_doesNotReacquire_andUsesPersistedGate() = runTest {
        stash.stashA(authUri)
        writeState.setAuthorizedForTests("content://other")
        val before = writeState.snapshot()
        controller().execute(DeviceAcceptanceHarnessAction.INSPECT_STASHED_A)
        assertEquals(before, writeState.snapshot())
        assertTrue(treeReader.requirePersistedFlags.single())
    }

    @Test
    fun pickAndInspectOnce_routesToTemporaryPicker_withoutTreeReadYet() = runTest {
        val result = controller().execute(DeviceAcceptanceHarnessAction.PICK_AND_INSPECT_ONCE)
        assertEquals(DeviceAcceptanceControllerResult.NeedsTemporaryPicker, result)
        assertEquals(0, treeReader.calls)
        assertEquals(0, releasedUris.size)
    }

    @Test
    fun pickOnceTemporaryInspect_usesNonPersistedGate_andDoesNotMutateAuth() = runTest {
        writeState.setAuthorizedForTests(authUri)
        val before = writeState.snapshot()
        val report = controller().inspectTemporaryTreeUri(Uri.parse(authUri))
        assertEquals(DeviceAcceptanceResultStatus.OK, report.resultStatus)
        assertEquals(before, writeState.snapshot())
        assertEquals(listOf(false), treeReader.requirePersistedFlags)
        assertFalse(DeviceAcceptanceReportSerializer.toJson(report).contains(authUri))
    }

    @Test
    fun revoke_currentAuth_releasesOnce_stateUnchanged() = runTest {
        writeState.setAuthorizedForTests(authUri)
        writeState.markBackupSuccess(55L)
        val before = writeState.snapshot()
        val report = (
            controller().execute(DeviceAcceptanceHarnessAction.REVOKE_CURRENT_AUTH_GRANT)
                as DeviceAcceptanceControllerResult.Completed
            ).report
        assertEquals(DeviceAcceptanceResultStatus.REVOKED, report.resultStatus)
        assertEquals(listOf(Uri.parse(authUri)), releasedUris)
        assertEquals(before, writeState.snapshot())
        assertFalse(writeState.snapshot().needsReconnect)
        assertNull(writeState.snapshot().lastFailureCategory)
    }

    @Test
    fun revoke_noAuth_noRelease() = runTest {
        val report = (
            controller().execute(DeviceAcceptanceHarnessAction.REVOKE_CURRENT_AUTH_GRANT)
                as DeviceAcceptanceControllerResult.Completed
            ).report
        assertEquals(DeviceAcceptanceResultStatus.NO_AUTH, report.resultStatus)
        assertTrue(releasedUris.isEmpty())
    }

    @Test
    fun revoke_alreadyAbsent_noStateMutation() = runTest {
        writeState.setAuthorizedForTests(authUri)
        grantPresent.clear()
        val before = writeState.snapshot()
        val report = (
            controller().execute(DeviceAcceptanceHarnessAction.REVOKE_CURRENT_AUTH_GRANT)
                as DeviceAcceptanceControllerResult.Completed
            ).report
        assertEquals(DeviceAcceptanceResultStatus.ALREADY_ABSENT, report.resultStatus)
        assertTrue(releasedUris.isEmpty())
        assertEquals(before, writeState.snapshot())
    }

    @Test
    fun revoke_releaseFailed_stateUnchanged() = runTest {
        writeState.setAuthorizedForTests(authUri)
        val failing = DeviceAcceptanceGrantRevoker(
            permissionReleaser = DeviceAcceptancePermissionReleaser { _, _ ->
                // leave grant present
            },
            grantProbe = DeviceAcceptancePersistedGrantProbe { true },
        )
        val before = writeState.snapshot()
        val report = (
            DeviceAcceptanceHarnessController(
                uriStash = stash,
                treeReader = treeReader,
                grantRevoker = failing,
                clock = { 1L },
            ).execute(DeviceAcceptanceHarnessAction.REVOKE_CURRENT_AUTH_GRANT)
                as DeviceAcceptanceControllerResult.Completed
            ).report
        assertEquals(DeviceAcceptanceResultStatus.RELEASE_FAILED, report.resultStatus)
        assertEquals(before, writeState.snapshot())
    }

    @Test
    fun releaseFlags_readWriteOnly() {
        val flags = DeviceAcceptanceGrantRevoker.RELEASE_FLAGS
        assertEquals(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            flags,
        )
        assertEquals(0, flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
    }

    @Test
    fun openDocumentTreeContract_requestsReadOnly() {
        val intent = DeviceAcceptanceOpenDocumentTreeReadOnlyContract()
            .createIntent(RuntimeEnvironment.getApplication(), null)
        assertEquals(Intent.ACTION_OPEN_DOCUMENT_TREE, intent.action)
        val flags = intent.flags
        assertTrue(flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(0, flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        assertEquals(0, flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
    }

    // 13.08.2026 DATA VAULT Stage 6.4A cursor by Me4Hik START - INSPECT_ROOM_STATE routing tests
    @Test
    fun inspectRoomState_routesToReader_noTreeReaderCall() = runTest {
        val summary = DeviceAcceptanceRoomStateSummary(
            practiceStarted = true,
            isPaused = true,
            currentCycleNumber = 1,
            nextCyclePosition = 2,
            scheduleMinutes = listOf(660, 900, 1140),
            occurrenceCount = 1,
            answerCount = 0,
            deletedTextCount = 0,
            answeredCount = 0,
            skippedCount = 0,
            scheduledCount = 1,
            missedCount = 0,
            availableCount = 0,
        )
        val roomReader = FakeRoomReader(DeviceAcceptanceRoomReadResult.Ok(summary))
        val report = (
            controller(roomReader).execute(DeviceAcceptanceHarnessAction.INSPECT_ROOM_STATE)
                as DeviceAcceptanceControllerResult.Completed
            ).report
        assertEquals(DeviceAcceptanceResultStatus.OK, report.resultStatus)
        assertEquals(summary, report.roomSummary)
        assertEquals(1, roomReader.calls)
        assertEquals(0, treeReader.calls)
        val json = DeviceAcceptanceReportSerializer.toJson(report)
        assertFalse(DeviceAcceptanceReportSerializer.containsForbiddenLeak(json))
    }

    @Test
    fun inspectRoomState_unavailableWithoutReader() = runTest {
        val report = (
            controller(roomReader = null).execute(DeviceAcceptanceHarnessAction.INSPECT_ROOM_STATE)
                as DeviceAcceptanceControllerResult.Completed
            ).report
        assertEquals(DeviceAcceptanceResultStatus.ROOM_UNAVAILABLE, report.resultStatus)
        assertNull(report.roomSummary)
    }

    @Test
    fun inspectRoomState_mapsReadFailedAndUnsafe() = runTest {
        val failed = (
            controller(FakeRoomReader(DeviceAcceptanceRoomReadResult.ReadFailed))
                .execute(DeviceAcceptanceHarnessAction.INSPECT_ROOM_STATE)
                as DeviceAcceptanceControllerResult.Completed
            ).report
        assertEquals(DeviceAcceptanceResultStatus.ROOM_READ_FAILED, failed.resultStatus)
        assertNull(failed.roomSummary)

        val unsafe = (
            controller(FakeRoomReader(DeviceAcceptanceRoomReadResult.Unsafe))
                .execute(DeviceAcceptanceHarnessAction.INSPECT_ROOM_STATE)
                as DeviceAcceptanceControllerResult.Completed
            ).report
        assertEquals(DeviceAcceptanceResultStatus.ROOM_UNSAFE, unsafe.resultStatus)
        assertNull(unsafe.roomSummary)
    }

    @Test
    fun inspectRoomState_doesNotMutateWriteState_orTouchTree() = runTest {
        writeState.setAuthorizedForTests(authUri)
        writeState.markBackupSuccess(55L)
        val before = writeState.snapshot()
        controller(
            FakeRoomReader(
                DeviceAcceptanceRoomReadResult.Ok(
                    DeviceAcceptanceRoomStateSummary(
                        practiceStarted = false,
                        isPaused = false,
                        currentCycleNumber = 1,
                        nextCyclePosition = 1,
                        scheduleMinutes = listOf(660, 900, 1140),
                        occurrenceCount = 0,
                        answerCount = 0,
                        deletedTextCount = 0,
                        answeredCount = 0,
                        skippedCount = 0,
                        scheduledCount = 0,
                        missedCount = 0,
                        availableCount = 0,
                    ),
                ),
            ),
        ).execute(DeviceAcceptanceHarnessAction.INSPECT_ROOM_STATE)
        assertWriteStateEquals(before, writeState.snapshot())
        assertEquals(0, treeReader.calls)
        assertEquals(0, releasedUris.size)
    }

    @Test
    fun actionParse_includesInspectRoomState() {
        assertEquals(
            DeviceAcceptanceHarnessAction.INSPECT_ROOM_STATE,
            DeviceAcceptanceHarnessAction.parseSymbolic("INSPECT_ROOM_STATE"),
        )
    }
    // 13.08.2026 DATA VAULT Stage 6.4A cursor by Me4Hik END

    private fun controller(
        roomReader: DeviceAcceptanceRoomStateReader? = null,
    ): DeviceAcceptanceHarnessController {
        return DeviceAcceptanceHarnessController(
            uriStash = stash,
            treeReader = treeReader,
            grantRevoker = revoker,
            clock = { 123L },
            roomStateReader = roomReader,
        )
    }

    private class FakeRoomReader(
        private val result: DeviceAcceptanceRoomReadResult,
    ) : DeviceAcceptanceRoomStateReader {
        var calls: Int = 0
        override suspend fun read(): DeviceAcceptanceRoomReadResult {
            calls += 1
            return result
        }
    }

    private class RecordingTreeReader(
        private val result: DeviceAcceptanceFolderReadResult,
    ) : DeviceAcceptanceTreeReader {
        var calls: Int = 0
        val requirePersistedFlags = mutableListOf<Boolean>()

        override suspend fun readAuthorizedOrStashed(
            uriString: String,
            requirePersistedGrant: Boolean,
        ): DeviceAcceptanceFolderReadResult {
            calls += 1
            requirePersistedFlags += requirePersistedGrant
            return result
        }
    }
}

private suspend fun InMemoryBackupWriteStateRepository.setAuthorizedForTests(uri: String) {
    commitAuthorizedFolderAfterVerifiedWrite(uri, 1L)
}

private fun assertWriteStateEquals(
    expected: com.me4hik.praktika.data.backup.write.BackupWriteState,
    actual: com.me4hik.praktika.data.backup.write.BackupWriteState,
) {
    assertEquals(expected.treeUriHint, actual.treeUriHint)
    assertEquals(expected.authorizedTreeUri, actual.authorizedTreeUri)
    assertEquals(
        expected.lastSuccessfulBackupAtEpochMillis,
        actual.lastSuccessfulBackupAtEpochMillis,
    )
    assertEquals(expected.lastFailureCategory, actual.lastFailureCategory)
    assertEquals(expected.needsReconnect, actual.needsReconnect)
}
// 13.08.2026 DATA VAULT Stage 6.3C1 cursor by Me4Hik END
