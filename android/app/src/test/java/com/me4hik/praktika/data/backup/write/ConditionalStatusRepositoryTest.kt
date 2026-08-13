package com.me4hik.praktika.data.backup.write

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
class ConditionalStatusRepositoryTest {
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
    fun successIfAuthorized_same_applied() = runTest {
        val store = harness.createDataStore(temporaryFolder)
        val repo = harness.writeStateRepository(store)
        val tree = harness.treeConfigRepository(store)
        tree.saveTreeUri(BackupWriteStateTestHarness.FOLDER_A)
        repo.authorizeCurrentTreeUriHint()

        assertEquals(
            ConditionalStatusUpdateResult.Applied,
            repo.markBackupSuccessIfAuthorized(
                BackupWriteStateTestHarness.FOLDER_A,
                BackupWriteStateTestHarness.TA,
            ),
        )
        assertEquals(BackupWriteStateTestHarness.TA, repo.snapshot().lastSuccessfulBackupAtEpochMillis)
        assertNull(repo.snapshot().lastFailureCategory)
    }

    @Test
    fun successIfAuthorized_mismatch_staleZeroMutation() = runTest {
        val store = harness.createDataStore(temporaryFolder)
        val repo = harness.writeStateRepository(store)
        val tree = harness.treeConfigRepository(store)
        tree.saveTreeUri(BackupWriteStateTestHarness.FOLDER_A)
        repo.authorizeCurrentTreeUriHint()
        repo.markBackupSuccess(BackupWriteStateTestHarness.TA)
        tree.saveTreeUri(BackupWriteStateTestHarness.FOLDER_B)
        repo.authorizeCurrentTreeUriHint()

        assertEquals(
            ConditionalStatusUpdateResult.StaleAttempt,
            repo.markBackupSuccessIfAuthorized(
                BackupWriteStateTestHarness.FOLDER_A,
                BackupWriteStateTestHarness.TB,
            ),
        )
        assertNull(repo.snapshot().lastSuccessfulBackupAtEpochMillis)
        assertEquals(BackupWriteStateTestHarness.FOLDER_B, repo.snapshot().authorizedTreeUri)
    }

    @Test
    fun noChangeIfAuthorized_sameAndMismatch() = runTest {
        val store = harness.createDataStore(temporaryFolder)
        val repo = harness.writeStateRepository(store)
        val tree = harness.treeConfigRepository(store)
        tree.saveTreeUri(BackupWriteStateTestHarness.FOLDER_A)
        repo.authorizeCurrentTreeUriHint()

        assertEquals(
            ConditionalStatusUpdateResult.Applied,
            repo.markNoChangeHealthyIfAuthorized(
                BackupWriteStateTestHarness.FOLDER_A,
                BackupWriteStateTestHarness.T2,
            ),
        )
        assertEquals(BackupWriteStateTestHarness.T2, repo.snapshot().lastSuccessfulBackupAtEpochMillis)

        assertEquals(
            ConditionalStatusUpdateResult.StaleAttempt,
            repo.markNoChangeHealthyIfAuthorized(
                BackupWriteStateTestHarness.FOLDER_B,
                BackupWriteStateTestHarness.TB,
            ),
        )
        assertEquals(BackupWriteStateTestHarness.T2, repo.snapshot().lastSuccessfulBackupAtEpochMillis)
    }

    @Test
    fun failureIfAuthorized_sameAndMismatch() = runTest {
        val store = harness.createDataStore(temporaryFolder)
        val repo = harness.writeStateRepository(store)
        val tree = harness.treeConfigRepository(store)
        tree.saveTreeUri(BackupWriteStateTestHarness.FOLDER_A)
        repo.authorizeCurrentTreeUriHint()
        repo.markBackupSuccess(BackupWriteStateTestHarness.TA)

        assertEquals(
            ConditionalStatusUpdateResult.Applied,
            repo.markBackupFailureIfAuthorized(
                BackupWriteStateTestHarness.FOLDER_A,
                BackupFailureStatus.WRITE_FAILED,
            ),
        )
        assertEquals(BackupFailureStatus.WRITE_FAILED, repo.snapshot().lastFailureCategory)
        assertEquals(BackupWriteStateTestHarness.TA, repo.snapshot().lastSuccessfulBackupAtEpochMillis)
        assertFalse(repo.snapshot().needsReconnect)

        tree.saveTreeUri(BackupWriteStateTestHarness.FOLDER_B)
        repo.authorizeCurrentTreeUriHint()
        assertEquals(
            ConditionalStatusUpdateResult.StaleAttempt,
            repo.markBackupFailureIfAuthorized(
                BackupWriteStateTestHarness.FOLDER_A,
                BackupFailureStatus.AMBIGUOUS_SLOT,
            ),
        )
        assertNull(repo.snapshot().lastFailureCategory)
    }

    @Test
    fun needsReconnectIfAuthorized_sameAndMismatch() = runTest {
        val store = harness.createDataStore(temporaryFolder)
        val repo = harness.writeStateRepository(store)
        val tree = harness.treeConfigRepository(store)
        tree.saveTreeUri(BackupWriteStateTestHarness.FOLDER_A)
        repo.authorizeCurrentTreeUriHint()

        assertEquals(
            ConditionalStatusUpdateResult.Applied,
            repo.markNeedsReconnectIfAuthorized(BackupWriteStateTestHarness.FOLDER_A),
        )
        assertTrue(repo.snapshot().needsReconnect)
        assertEquals(BackupFailureStatus.PERMISSION_LOST, repo.snapshot().lastFailureCategory)

        tree.saveTreeUri(BackupWriteStateTestHarness.FOLDER_B)
        repo.authorizeCurrentTreeUriHint()
        assertEquals(
            ConditionalStatusUpdateResult.StaleAttempt,
            repo.markNeedsReconnectIfAuthorized(BackupWriteStateTestHarness.FOLDER_A),
        )
        assertFalse(repo.snapshot().needsReconnect)
        assertNull(repo.snapshot().lastFailureCategory)
    }

    @Test
    fun disableRace_successIfAuthorized_stale() = runTest {
        val store = harness.createDataStore(temporaryFolder)
        val repo = harness.writeStateRepository(store)
        val tree = harness.treeConfigRepository(store)
        tree.saveTreeUri(BackupWriteStateTestHarness.FOLDER_A)
        repo.authorizeCurrentTreeUriHint()
        repo.disableAutomaticBackup()

        assertEquals(
            ConditionalStatusUpdateResult.StaleAttempt,
            repo.markBackupSuccessIfAuthorized(
                BackupWriteStateTestHarness.FOLDER_A,
                BackupWriteStateTestHarness.TA,
            ),
        )
        assertNull(repo.snapshot().authorizedTreeUri)
        assertNull(repo.snapshot().lastSuccessfulBackupAtEpochMillis)
    }
}
