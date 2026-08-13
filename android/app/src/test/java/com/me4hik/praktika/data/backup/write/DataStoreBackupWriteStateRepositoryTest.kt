package com.me4hik.praktika.data.backup.write

import androidx.datastore.preferences.core.edit
import com.me4hik.praktika.data.backup.storage.BackupTreePreferencesKeys
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class DataStoreBackupWriteStateRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var harness: BackupWriteStateTestHarness

    @Before
    fun setUp() {
        harness = BackupWriteStateTestHarness()
    }

    @After
    fun tearDown() {
        harness.close()
    }

    @Test
    fun legacyHintOnly_notAuthorized() = runTest {
        val store = harness.createDataStore(temporaryFolder)
        harness.seedHintOnly(store, BackupWriteStateTestHarness.FOLDER_B)
        val repo = harness.writeStateRepository(store)

        val snapshot = repo.snapshot()
        assertEquals(BackupWriteStateTestHarness.FOLDER_B, snapshot.treeUriHint)
        assertNull(snapshot.authorizedTreeUri)
        assertNull(snapshot.lastSuccessfulBackupAtEpochMillis)
        assertNull(snapshot.lastFailureCategory)
        assertFalse(snapshot.needsReconnect)
        assertEquals(
            BackupOperationalState.RestoreFolderSelectedOnly,
            BackupOperationalStateMapper.map(snapshot),
        )
        assertNull(BackupOperationalStateMapper.automaticWriteTargetUri(snapshot))
    }

    @Test
    fun previewCancel_doesNotTransferAuthorization() = runTest {
        val store = harness.createDataStore(temporaryFolder)
        val writeRepo = harness.writeStateRepository(store)
        val treeRepo = harness.treeConfigRepository(store)

        treeRepo.saveTreeUri(BackupWriteStateTestHarness.FOLDER_A)
        assertEquals(
            AuthorizeCurrentHintResult.Success,
            writeRepo.authorizeCurrentTreeUriHint(),
        )
        writeRepo.markBackupSuccess(BackupWriteStateTestHarness.TA)

        // Stage5 PreviewReady persists hint B only
        treeRepo.saveTreeUri(BackupWriteStateTestHarness.FOLDER_B)

        val snapshot = writeRepo.snapshot()
        assertEquals(BackupWriteStateTestHarness.FOLDER_B, snapshot.treeUriHint)
        assertEquals(BackupWriteStateTestHarness.FOLDER_A, snapshot.authorizedTreeUri)
        assertEquals(BackupWriteStateTestHarness.TA, snapshot.lastSuccessfulBackupAtEpochMillis)
        assertEquals(
            BackupWriteStateTestHarness.FOLDER_A,
            BackupOperationalStateMapper.automaticWriteTargetUri(snapshot),
        )
    }

    @Test
    fun restoreAuthSwitch_AtoB_clearsTimestampAndFailureAtomically() = runTest {
        val store = harness.createDataStore(temporaryFolder)
        val writeRepo = harness.writeStateRepository(store)
        val treeRepo = harness.treeConfigRepository(store)

        treeRepo.saveTreeUri(BackupWriteStateTestHarness.FOLDER_A)
        writeRepo.authorizeCurrentTreeUriHint()
        writeRepo.markBackupSuccess(BackupWriteStateTestHarness.TA)
        writeRepo.markBackupFailure(BackupFailureStatus.WRITE_FAILED)
        treeRepo.saveTreeUri(BackupWriteStateTestHarness.FOLDER_B)

        assertEquals(
            AuthorizeCurrentHintResult.Success,
            writeRepo.authorizeCurrentTreeUriHint(),
        )

        val snapshot = writeRepo.snapshot()
        assertEquals(BackupWriteStateTestHarness.FOLDER_B, snapshot.treeUriHint)
        assertEquals(BackupWriteStateTestHarness.FOLDER_B, snapshot.authorizedTreeUri)
        assertNull(snapshot.lastSuccessfulBackupAtEpochMillis)
        assertNull(snapshot.lastFailureCategory)
        assertFalse(snapshot.needsReconnect)
    }

    @Test
    fun restoreAuthSameUri_preservesTimestampClearsFailureAndReconnect() = runTest {
        val store = harness.createDataStore(temporaryFolder)
        val writeRepo = harness.writeStateRepository(store)
        val treeRepo = harness.treeConfigRepository(store)

        treeRepo.saveTreeUri(BackupWriteStateTestHarness.FOLDER_A)
        writeRepo.authorizeCurrentTreeUriHint()
        writeRepo.markBackupSuccess(BackupWriteStateTestHarness.TA)
        writeRepo.markBackupFailure(BackupFailureStatus.WRITE_FAILED)
        writeRepo.markNeedsReconnect()

        assertEquals(
            AuthorizeCurrentHintResult.Success,
            writeRepo.authorizeCurrentTreeUriHint(),
        )

        val snapshot = writeRepo.snapshot()
        assertEquals(BackupWriteStateTestHarness.FOLDER_A, snapshot.authorizedTreeUri)
        assertEquals(BackupWriteStateTestHarness.TA, snapshot.lastSuccessfulBackupAtEpochMillis)
        assertNull(snapshot.lastFailureCategory)
        assertFalse(snapshot.needsReconnect)
    }

    @Test
    fun firstAuth_nullToB_lastSuccessNull() = runTest {
        val store = harness.createDataStore(temporaryFolder)
        val writeRepo = harness.writeStateRepository(store)
        harness.seedHintOnly(store, BackupWriteStateTestHarness.FOLDER_B)

        assertEquals(
            AuthorizeCurrentHintResult.Success,
            writeRepo.authorizeCurrentTreeUriHint(),
        )

        val snapshot = writeRepo.snapshot()
        assertEquals(BackupWriteStateTestHarness.FOLDER_B, snapshot.authorizedTreeUri)
        assertNull(snapshot.lastSuccessfulBackupAtEpochMillis)
        assertNull(snapshot.lastFailureCategory)
        assertFalse(snapshot.needsReconnect)
    }

    @Test
    fun authorizeWithNoUsableHint_preservesPriorState() = runTest {
        val store = harness.createDataStore(temporaryFolder)
        val writeRepo = harness.writeStateRepository(store)
        val treeRepo = harness.treeConfigRepository(store)

        treeRepo.saveTreeUri(BackupWriteStateTestHarness.FOLDER_A)
        writeRepo.authorizeCurrentTreeUriHint()
        writeRepo.markBackupSuccess(BackupWriteStateTestHarness.TA)
        treeRepo.clearTreeUri()

        assertEquals(
            AuthorizeCurrentHintResult.NoUsableHint,
            writeRepo.authorizeCurrentTreeUriHint(),
        )

        val afterNull = writeRepo.snapshot()
        assertEquals(BackupWriteStateTestHarness.FOLDER_A, afterNull.authorizedTreeUri)
        assertEquals(BackupWriteStateTestHarness.TA, afterNull.lastSuccessfulBackupAtEpochMillis)

        treeRepo.saveTreeUri(BackupWriteStateTestHarness.MALFORMED)
        assertEquals(
            AuthorizeCurrentHintResult.NoUsableHint,
            writeRepo.authorizeCurrentTreeUriHint(),
        )
        val afterBlank = writeRepo.snapshot()
        assertEquals(BackupWriteStateTestHarness.FOLDER_A, afterBlank.authorizedTreeUri)
        assertEquals(BackupWriteStateTestHarness.TA, afterBlank.lastSuccessfulBackupAtEpochMillis)
    }

    @Test
    fun clearTreeHint_doesNotDisarmAuthorization() = runTest {
        val store = harness.createDataStore(temporaryFolder)
        val writeRepo = harness.writeStateRepository(store)
        val treeRepo = harness.treeConfigRepository(store)

        treeRepo.saveTreeUri(BackupWriteStateTestHarness.FOLDER_A)
        writeRepo.authorizeCurrentTreeUriHint()
        writeRepo.markBackupSuccess(BackupWriteStateTestHarness.TA)
        treeRepo.clearTreeUri()

        val snapshot = writeRepo.snapshot()
        assertNull(snapshot.treeUriHint)
        assertEquals(BackupWriteStateTestHarness.FOLDER_A, snapshot.authorizedTreeUri)
        assertEquals(
            BackupWriteStateTestHarness.FOLDER_A,
            BackupOperationalStateMapper.automaticWriteTargetUri(snapshot),
        )
    }

    @Test
    fun verifiedSetupCommit_atomicSwitch() = runTest {
        val store = harness.createDataStore(temporaryFolder)
        val writeRepo = harness.writeStateRepository(store)
        val treeRepo = harness.treeConfigRepository(store)

        treeRepo.saveTreeUri(BackupWriteStateTestHarness.FOLDER_A)
        writeRepo.authorizeCurrentTreeUriHint()
        writeRepo.markBackupSuccess(BackupWriteStateTestHarness.TA)
        writeRepo.markBackupFailure(BackupFailureStatus.EXPORT_FAILED)

        assertEquals(
            CommitAuthorizedFolderResult.Success,
            writeRepo.commitAuthorizedFolderAfterVerifiedWrite(
                BackupWriteStateTestHarness.FOLDER_B,
                BackupWriteStateTestHarness.TB,
            ),
        )

        val snapshot = writeRepo.snapshot()
        assertEquals(BackupWriteStateTestHarness.FOLDER_B, snapshot.treeUriHint)
        assertEquals(BackupWriteStateTestHarness.FOLDER_B, snapshot.authorizedTreeUri)
        assertEquals(BackupWriteStateTestHarness.TB, snapshot.lastSuccessfulBackupAtEpochMillis)
        assertNull(snapshot.lastFailureCategory)
        assertFalse(snapshot.needsReconnect)
    }

    @Test
    fun verifiedSetupCommit_invalidUri_refused() = runTest {
        val store = harness.createDataStore(temporaryFolder)
        val writeRepo = harness.writeStateRepository(store)
        val treeRepo = harness.treeConfigRepository(store)

        treeRepo.saveTreeUri(BackupWriteStateTestHarness.FOLDER_A)
        writeRepo.authorizeCurrentTreeUriHint()

        assertEquals(
            CommitAuthorizedFolderResult.InvalidUri,
            writeRepo.commitAuthorizedFolderAfterVerifiedWrite(
                BackupWriteStateTestHarness.MALFORMED,
                BackupWriteStateTestHarness.TB,
            ),
        )
        assertEquals(BackupWriteStateTestHarness.FOLDER_A, writeRepo.snapshot().authorizedTreeUri)
    }

    @Test
    fun replacementFailure_withoutCommit_leavesOldAuth() = runTest {
        val store = harness.createDataStore(temporaryFolder)
        val writeRepo = harness.writeStateRepository(store)
        val treeRepo = harness.treeConfigRepository(store)

        treeRepo.saveTreeUri(BackupWriteStateTestHarness.FOLDER_A)
        writeRepo.authorizeCurrentTreeUriHint()
        writeRepo.markBackupSuccess(BackupWriteStateTestHarness.TA)

        // Physical write failed → commit NOT called
        val snapshot = writeRepo.snapshot()
        assertEquals(BackupWriteStateTestHarness.FOLDER_A, snapshot.authorizedTreeUri)
        assertEquals(BackupWriteStateTestHarness.FOLDER_A, snapshot.treeUriHint)
        assertEquals(BackupWriteStateTestHarness.TA, snapshot.lastSuccessfulBackupAtEpochMillis)
    }

    @Test
    fun disableAutomaticBackup_clearsAuthPreservesHint() = runTest {
        val store = harness.createDataStore(temporaryFolder)
        val writeRepo = harness.writeStateRepository(store)
        val treeRepo = harness.treeConfigRepository(store)

        treeRepo.saveTreeUri(BackupWriteStateTestHarness.FOLDER_A)
        writeRepo.authorizeCurrentTreeUriHint()
        writeRepo.markBackupSuccess(BackupWriteStateTestHarness.TA)
        writeRepo.markBackupFailure(BackupFailureStatus.WRITE_FAILED)
        writeRepo.markNeedsReconnect()

        writeRepo.disableAutomaticBackup()

        val snapshot = writeRepo.snapshot()
        assertEquals(BackupWriteStateTestHarness.FOLDER_A, snapshot.treeUriHint)
        assertNull(snapshot.authorizedTreeUri)
        assertNull(snapshot.lastSuccessfulBackupAtEpochMillis)
        assertNull(snapshot.lastFailureCategory)
        assertFalse(snapshot.needsReconnect)
    }

    @Test
    fun reconnectSameUri_clearsReconnectPreservesAuthAndSuccess() = runTest {
        val store = harness.createDataStore(temporaryFolder)
        val writeRepo = harness.writeStateRepository(store)
        val treeRepo = harness.treeConfigRepository(store)

        treeRepo.saveTreeUri(BackupWriteStateTestHarness.FOLDER_A)
        writeRepo.authorizeCurrentTreeUriHint()
        writeRepo.markBackupSuccess(BackupWriteStateTestHarness.TA)
        writeRepo.markNeedsReconnect()

        assertEquals(
            ReconnectResult.Success,
            writeRepo.markReconnectSucceeded(BackupWriteStateTestHarness.FOLDER_A),
        )

        val snapshot = writeRepo.snapshot()
        assertEquals(BackupWriteStateTestHarness.FOLDER_A, snapshot.authorizedTreeUri)
        assertEquals(BackupWriteStateTestHarness.TA, snapshot.lastSuccessfulBackupAtEpochMillis)
        assertFalse(snapshot.needsReconnect)
        assertNull(snapshot.lastFailureCategory)
    }

    @Test
    fun reconnectDifferentUri_refusedWithZeroMutation() = runTest {
        val store = harness.createDataStore(temporaryFolder)
        val writeRepo = harness.writeStateRepository(store)
        val treeRepo = harness.treeConfigRepository(store)

        treeRepo.saveTreeUri(BackupWriteStateTestHarness.FOLDER_A)
        writeRepo.authorizeCurrentTreeUriHint()
        writeRepo.markBackupSuccess(BackupWriteStateTestHarness.TA)
        writeRepo.markNeedsReconnect()
        treeRepo.saveTreeUri(BackupWriteStateTestHarness.FOLDER_B)

        assertEquals(
            ReconnectResult.UriMismatch,
            writeRepo.markReconnectSucceeded(BackupWriteStateTestHarness.FOLDER_B),
        )

        val snapshot = writeRepo.snapshot()
        assertEquals(BackupWriteStateTestHarness.FOLDER_A, snapshot.authorizedTreeUri)
        assertEquals(BackupWriteStateTestHarness.FOLDER_B, snapshot.treeUriHint)
        assertTrue(snapshot.needsReconnect)
        assertEquals(BackupFailureStatus.PERMISSION_LOST, snapshot.lastFailureCategory)
        assertEquals(BackupWriteStateTestHarness.TA, snapshot.lastSuccessfulBackupAtEpochMillis)
    }

    @Test
    fun failureNeverClearsAuthorization_forEveryCategory() = runTest {
        val store = harness.createDataStore(temporaryFolder)
        val writeRepo = harness.writeStateRepository(store)
        val treeRepo = harness.treeConfigRepository(store)

        treeRepo.saveTreeUri(BackupWriteStateTestHarness.FOLDER_A)
        writeRepo.authorizeCurrentTreeUriHint()

        for (category in BackupFailureStatus.entries) {
            writeRepo.markBackupFailure(category)
            val snapshot = writeRepo.snapshot()
            assertEquals(
                "auth cleared by $category",
                BackupWriteStateTestHarness.FOLDER_A,
                snapshot.authorizedTreeUri,
            )
            assertEquals(category, snapshot.lastFailureCategory)
            if (category == BackupFailureStatus.PERMISSION_LOST) {
                assertTrue(snapshot.needsReconnect)
            }
        }
    }

    @Test
    fun noChangeHealthy_recoversTrustedTimestampWithoutClock() = runTest {
        val store = harness.createDataStore(temporaryFolder)
        val writeRepo = harness.writeStateRepository(store)
        val treeRepo = harness.treeConfigRepository(store)

        treeRepo.saveTreeUri(BackupWriteStateTestHarness.FOLDER_A)
        writeRepo.authorizeCurrentTreeUriHint()
        writeRepo.markBackupSuccess(BackupWriteStateTestHarness.TA)
        writeRepo.markBackupFailure(BackupFailureStatus.WRITE_FAILED)

        writeRepo.markNoChangeHealthy(BackupWriteStateTestHarness.TA)
        assertEquals(BackupWriteStateTestHarness.TA, writeRepo.snapshot().lastSuccessfulBackupAtEpochMillis)
        assertNull(writeRepo.snapshot().lastFailureCategory)

        store.edit { it.remove(BackupTreePreferencesKeys.LAST_SUCCESSFUL_BACKUP_AT) }
        writeRepo.markNoChangeHealthy(BackupWriteStateTestHarness.T2)
        assertEquals(BackupWriteStateTestHarness.T2, writeRepo.snapshot().lastSuccessfulBackupAtEpochMillis)

        writeRepo.markNoChangeHealthy(BackupWriteStateTestHarness.TB)
        val snapshot = writeRepo.snapshot()
        assertEquals(BackupWriteStateTestHarness.TB, snapshot.lastSuccessfulBackupAtEpochMillis)
        assertEquals(BackupWriteStateTestHarness.FOLDER_A, snapshot.authorizedTreeUri)
        assertNull(snapshot.lastFailureCategory)
    }

    @Test
    fun authFolderTimestampBinding_clearsOnIdentityChange() = runTest {
        val store = harness.createDataStore(temporaryFolder)
        val writeRepo = harness.writeStateRepository(store)
        val treeRepo = harness.treeConfigRepository(store)

        treeRepo.saveTreeUri(BackupWriteStateTestHarness.FOLDER_A)
        writeRepo.authorizeCurrentTreeUriHint()
        writeRepo.markBackupSuccess(BackupWriteStateTestHarness.TA)

        treeRepo.saveTreeUri(BackupWriteStateTestHarness.FOLDER_B)
        writeRepo.authorizeCurrentTreeUriHint()
        assertNull(writeRepo.snapshot().lastSuccessfulBackupAtEpochMillis)

        writeRepo.markNoChangeHealthy(BackupWriteStateTestHarness.TB)
        assertEquals(BackupWriteStateTestHarness.TB, writeRepo.snapshot().lastSuccessfulBackupAtEpochMillis)

        treeRepo.saveTreeUri(BackupWriteStateTestHarness.FOLDER_A)
        writeRepo.authorizeCurrentTreeUriHint()
        assertNull(writeRepo.snapshot().lastSuccessfulBackupAtEpochMillis)
    }

    @Test
    fun sharedDataStore_treeConfigAndWriteStateSeeSameHint() = runTest {
        val store = harness.createDataStore(temporaryFolder)
        val writeRepo = harness.writeStateRepository(store)
        val treeRepo = harness.treeConfigRepository(store)

        treeRepo.saveTreeUri(BackupWriteStateTestHarness.FOLDER_B)
        assertEquals(BackupWriteStateTestHarness.FOLDER_B, writeRepo.snapshot().treeUriHint)

        writeRepo.authorizeCurrentTreeUriHint()
        assertEquals(BackupWriteStateTestHarness.FOLDER_B, treeRepo.treeUriHint.first())
        assertEquals(BackupWriteStateTestHarness.FOLDER_B, writeRepo.snapshot().authorizedTreeUri)
    }

    @Test
    fun unknownPersistedFailure_mapsToUnknownWithoutClearingAuth() = runTest {
        val store = harness.createDataStore(temporaryFolder)
        val writeRepo = harness.writeStateRepository(store)
        val treeRepo = harness.treeConfigRepository(store)

        treeRepo.saveTreeUri(BackupWriteStateTestHarness.FOLDER_A)
        writeRepo.authorizeCurrentTreeUriHint()
        store.edit {
            it[BackupTreePreferencesKeys.LAST_FAILURE_CATEGORY] = "FUTURE_CATEGORY_XYZ"
        }

        val snapshot = writeRepo.snapshot()
        assertEquals(BackupFailureStatus.UNKNOWN, snapshot.lastFailureCategory)
        assertEquals(BackupWriteStateTestHarness.FOLDER_A, snapshot.authorizedTreeUri)
        assertTrue(
            BackupOperationalStateMapper.canAttemptBackup(snapshot, BackupAttemptTrigger.MUTATION),
        )
    }

    @Test
    fun markBackupSuccess_preservesNeedsReconnect() = runTest {
        val store = harness.createDataStore(temporaryFolder)
        val writeRepo = harness.writeStateRepository(store)
        val treeRepo = harness.treeConfigRepository(store)

        treeRepo.saveTreeUri(BackupWriteStateTestHarness.FOLDER_A)
        writeRepo.authorizeCurrentTreeUriHint()
        writeRepo.markNeedsReconnect()
        writeRepo.markBackupSuccess(BackupWriteStateTestHarness.TA)

        val snapshot = writeRepo.snapshot()
        assertTrue(snapshot.needsReconnect)
        assertEquals(BackupWriteStateTestHarness.TA, snapshot.lastSuccessfulBackupAtEpochMillis)
        assertNull(snapshot.lastFailureCategory)
    }

    @Test
    fun authorizeEditFailure_leavesPriorDurableState() = runTest {
        var shouldFail = false
        val store = harness.failingEditDataStore(temporaryFolder) { shouldFail }
        val writeRepo = harness.writeStateRepository(store)
        val treeRepo = harness.treeConfigRepository(store)

        treeRepo.saveTreeUri(BackupWriteStateTestHarness.FOLDER_A)
        writeRepo.authorizeCurrentTreeUriHint()
        writeRepo.markBackupSuccess(BackupWriteStateTestHarness.TA)
        treeRepo.saveTreeUri(BackupWriteStateTestHarness.FOLDER_B)

        shouldFail = true
        try {
            writeRepo.authorizeCurrentTreeUriHint()
            throw AssertionError("expected injected DataStore failure")
        } catch (_: IllegalStateException) {
            // expected
        }

        shouldFail = false
        val snapshot = writeRepo.snapshot()
        assertEquals(BackupWriteStateTestHarness.FOLDER_B, snapshot.treeUriHint)
        assertEquals(BackupWriteStateTestHarness.FOLDER_A, snapshot.authorizedTreeUri)
        assertEquals(BackupWriteStateTestHarness.TA, snapshot.lastSuccessfulBackupAtEpochMillis)
    }

    @Test
    fun commitEditFailure_leavesPriorDurableState() = runTest {
        var shouldFail = false
        val store = harness.failingEditDataStore(temporaryFolder) { shouldFail }
        val writeRepo = harness.writeStateRepository(store)
        val treeRepo = harness.treeConfigRepository(store)

        treeRepo.saveTreeUri(BackupWriteStateTestHarness.FOLDER_A)
        writeRepo.authorizeCurrentTreeUriHint()
        writeRepo.markBackupSuccess(BackupWriteStateTestHarness.TA)

        shouldFail = true
        try {
            writeRepo.commitAuthorizedFolderAfterVerifiedWrite(
                BackupWriteStateTestHarness.FOLDER_B,
                BackupWriteStateTestHarness.TB,
            )
            throw AssertionError("expected injected DataStore failure")
        } catch (_: IllegalStateException) {
            // expected
        }

        shouldFail = false
        val snapshot = writeRepo.snapshot()
        assertEquals(BackupWriteStateTestHarness.FOLDER_A, snapshot.authorizedTreeUri)
        assertEquals(BackupWriteStateTestHarness.TA, snapshot.lastSuccessfulBackupAtEpochMillis)
    }

    @Test
    fun disableEditFailure_leavesPriorDurableState() = runTest {
        var shouldFail = false
        val store = harness.failingEditDataStore(temporaryFolder) { shouldFail }
        val writeRepo = harness.writeStateRepository(store)
        val treeRepo = harness.treeConfigRepository(store)

        treeRepo.saveTreeUri(BackupWriteStateTestHarness.FOLDER_A)
        writeRepo.authorizeCurrentTreeUriHint()
        writeRepo.markBackupSuccess(BackupWriteStateTestHarness.TA)

        shouldFail = true
        try {
            writeRepo.disableAutomaticBackup()
            throw AssertionError("expected injected DataStore failure")
        } catch (_: IllegalStateException) {
            // expected
        }

        shouldFail = false
        val snapshot = writeRepo.snapshot()
        assertEquals(BackupWriteStateTestHarness.FOLDER_A, snapshot.authorizedTreeUri)
        assertEquals(BackupWriteStateTestHarness.TA, snapshot.lastSuccessfulBackupAtEpochMillis)
    }

    @Test
    fun reconnectEditFailure_leavesPriorDurableState() = runTest {
        var shouldFail = false
        val store = harness.failingEditDataStore(temporaryFolder) { shouldFail }
        val writeRepo = harness.writeStateRepository(store)
        val treeRepo = harness.treeConfigRepository(store)

        treeRepo.saveTreeUri(BackupWriteStateTestHarness.FOLDER_A)
        writeRepo.authorizeCurrentTreeUriHint()
        writeRepo.markNeedsReconnect()

        shouldFail = true
        try {
            writeRepo.markReconnectSucceeded(BackupWriteStateTestHarness.FOLDER_A)
            throw AssertionError("expected injected DataStore failure")
        } catch (_: IllegalStateException) {
            // expected
        }

        shouldFail = false
        val snapshot = writeRepo.snapshot()
        assertTrue(snapshot.needsReconnect)
        assertEquals(BackupFailureStatus.PERMISSION_LOST, snapshot.lastFailureCategory)
    }
}
