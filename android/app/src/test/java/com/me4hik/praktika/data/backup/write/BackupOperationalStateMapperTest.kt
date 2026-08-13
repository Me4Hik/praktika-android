package com.me4hik.praktika.data.backup.write

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupOperationalStateMapperTest {
    @Test
    fun notConfigured_whenNoAuthNoHint() {
        val state = emptyState()
        assertEquals(
            BackupOperationalState.NotConfigured,
            BackupOperationalStateMapper.map(state),
        )
        assertFalse(canAttempt(state, BackupAttemptTrigger.MUTATION))
    }

    @Test
    fun restoreFolderSelectedOnly_legacyHintWithoutAuth() {
        val state = emptyState(hint = BackupWriteStateTestHarness.FOLDER_B)
        assertEquals(
            BackupOperationalState.RestoreFolderSelectedOnly,
            BackupOperationalStateMapper.map(state),
        )
        assertNull(BackupOperationalStateMapper.automaticWriteTargetUri(state))
        assertFalse(canAttempt(state, BackupAttemptTrigger.STARTUP))
    }

    @Test
    fun active_whenAuthMatchesHint() {
        val state = emptyState(
            hint = BackupWriteStateTestHarness.FOLDER_A,
            auth = BackupWriteStateTestHarness.FOLDER_A,
        )
        assertEquals(BackupOperationalState.Active, BackupOperationalStateMapper.map(state))
        assertTrue(canAttempt(state, BackupAttemptTrigger.MUTATION))
        assertEquals(
            BackupWriteStateTestHarness.FOLDER_A,
            BackupOperationalStateMapper.automaticWriteTargetUri(state),
        )
    }

    @Test
    fun activeButHintDifferent_targetRemainsAuthorized() {
        val state = emptyState(
            hint = BackupWriteStateTestHarness.FOLDER_B,
            auth = BackupWriteStateTestHarness.FOLDER_A,
        )
        assertEquals(
            BackupOperationalState.ActiveButHintDifferent,
            BackupOperationalStateMapper.map(state),
        )
        assertEquals(
            BackupWriteStateTestHarness.FOLDER_A,
            BackupOperationalStateMapper.automaticWriteTargetUri(state),
        )
        assertTrue(canAttempt(state, BackupAttemptTrigger.MUTATION))
    }

    @Test
    fun activeWithRetryableFailure_allowsAllTriggers() {
        val state = emptyState(
            hint = BackupWriteStateTestHarness.FOLDER_A,
            auth = BackupWriteStateTestHarness.FOLDER_A,
            failure = BackupFailureStatus.WRITE_FAILED,
        )
        assertEquals(
            BackupOperationalState.ActiveWithRetryableFailure,
            BackupOperationalStateMapper.map(state),
        )
        assertTrue(canAttempt(state, BackupAttemptTrigger.MUTATION))
        assertTrue(canAttempt(state, BackupAttemptTrigger.STARTUP))
        assertTrue(canAttempt(state, BackupAttemptTrigger.MANUAL))
    }

    @Test
    fun needsAttention_suppressesMutationAllowsStartupManual() {
        val state = emptyState(
            hint = BackupWriteStateTestHarness.FOLDER_A,
            auth = BackupWriteStateTestHarness.FOLDER_A,
            failure = BackupFailureStatus.AMBIGUOUS_SLOT,
        )
        assertEquals(
            BackupOperationalState.NeedsAttentionRecheckAllowed,
            BackupOperationalStateMapper.map(state),
        )
        assertFalse(canAttempt(state, BackupAttemptTrigger.MUTATION))
        assertTrue(canAttempt(state, BackupAttemptTrigger.STARTUP))
        assertTrue(canAttempt(state, BackupAttemptTrigger.MANUAL))
    }

    @Test
    fun needsReconnect_blocksAllAttempts() {
        val state = emptyState(
            hint = BackupWriteStateTestHarness.FOLDER_A,
            auth = BackupWriteStateTestHarness.FOLDER_A,
            failure = BackupFailureStatus.PERMISSION_LOST,
            needsReconnect = true,
        )
        assertEquals(
            BackupOperationalState.NeedsReconnect,
            BackupOperationalStateMapper.map(state),
        )
        for (trigger in BackupAttemptTrigger.entries) {
            assertFalse(canAttempt(state, trigger))
        }
    }

    @Test
    fun invalidConfig_malformedAuth_noAttempt() {
        val state = emptyState(
            hint = BackupWriteStateTestHarness.FOLDER_A,
            auth = BackupWriteStateTestHarness.MALFORMED,
        )
        assertEquals(
            BackupOperationalState.InvalidConfig,
            BackupOperationalStateMapper.map(state),
        )
        assertNull(BackupOperationalStateMapper.automaticWriteTargetUri(state))
        assertFalse(canAttempt(state, BackupAttemptTrigger.MANUAL))
    }

    @Test
    fun grantUnusable_mapsNeedsReconnectAndBlocks() {
        val state = emptyState(
            auth = BackupWriteStateTestHarness.FOLDER_A,
            hint = BackupWriteStateTestHarness.FOLDER_A,
        )
        assertEquals(
            BackupOperationalState.NeedsReconnect,
            BackupOperationalStateMapper.map(state, grantUsable = false),
        )
        assertFalse(
            BackupOperationalStateMapper.canAttemptBackup(
                state,
                BackupAttemptTrigger.MUTATION,
                grantUsable = false,
            ),
        )
    }

    @Test
    fun restoreSuppressed_blocksAttempt() {
        val state = emptyState(
            auth = BackupWriteStateTestHarness.FOLDER_A,
            hint = BackupWriteStateTestHarness.FOLDER_A,
        )
        assertFalse(
            BackupOperationalStateMapper.canAttemptBackup(
                state,
                BackupAttemptTrigger.MUTATION,
                restoreSuppressed = true,
            ),
        )
    }

    private fun canAttempt(
        state: BackupWriteState,
        trigger: BackupAttemptTrigger,
    ): Boolean = BackupOperationalStateMapper.canAttemptBackup(state, trigger)

    private fun emptyState(
        hint: String? = null,
        auth: String? = null,
        failure: BackupFailureStatus? = null,
        needsReconnect: Boolean = false,
        lastSuccess: Long? = null,
    ) = BackupWriteState(
        treeUriHint = hint,
        authorizedTreeUri = auth,
        lastSuccessfulBackupAtEpochMillis = lastSuccess,
        lastFailureCategory = failure,
        needsReconnect = needsReconnect,
    )
}
