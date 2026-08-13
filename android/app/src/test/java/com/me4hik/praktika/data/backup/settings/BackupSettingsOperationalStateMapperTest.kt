package com.me4hik.praktika.data.backup.settings

import com.me4hik.praktika.data.backup.write.BackupFailureStatus
import com.me4hik.praktika.data.backup.write.BackupWriteState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupSettingsOperationalStateMapperTest {
    private val folderA = "content://com.test/tree/folder-a"
    private val folderB = "content://com.test/tree/folder-b"

    @Test
    fun authNull_withStaleHintFailureCache_mapsNotConfigured() {
        val state = BackupWriteState(
            treeUriHint = folderB,
            authorizedTreeUri = null,
            lastSuccessfulBackupAtEpochMillis = 99L,
            lastFailureCategory = BackupFailureStatus.WRITE_FAILED,
            needsReconnect = true,
        )
        assertEquals(
            BackupSettingsOperationalState.NotConfigured,
            BackupSettingsOperationalStateMapper.map(state),
        )
    }

    @Test
    fun authBlank_mapsNotConfigured() {
        val state = BackupWriteState(
            treeUriHint = folderA,
            authorizedTreeUri = "   ",
            lastSuccessfulBackupAtEpochMillis = 1L,
            lastFailureCategory = null,
            needsReconnect = false,
        )
        assertEquals(
            BackupSettingsOperationalState.NotConfigured,
            BackupSettingsOperationalStateMapper.map(state),
        )
    }

    @Test
    fun needsReconnect_withOldSuccess() {
        val state = auth(needsReconnect = true, lastSuccess = 42L)
        assertEquals(
            BackupSettingsOperationalState.NeedsReconnect(42L),
            BackupSettingsOperationalStateMapper.map(state),
        )
    }

    @Test
    fun permissionLost_mapsNeedsReconnect() {
        val state = auth(
            needsReconnect = false,
            failure = BackupFailureStatus.PERMISSION_LOST,
            lastSuccess = 7L,
        )
        assertEquals(
            BackupSettingsOperationalState.NeedsReconnect(7L),
            BackupSettingsOperationalStateMapper.map(state),
        )
    }

    @Test
    fun structural_mapsNeedsAttention() {
        for (failure in listOf(
            BackupFailureStatus.AMBIGUOUS_SLOT,
            BackupFailureStatus.UNTRUSTED_ARTIFACTS,
            BackupFailureStatus.SEQUENCE_EXHAUSTED,
        )) {
            assertEquals(
                BackupSettingsOperationalState.NeedsAttention(11L),
                BackupSettingsOperationalStateMapper.map(auth(failure = failure, lastSuccess = 11L)),
            )
        }
    }

    @Test
    fun unsafeDatabase_mapsDataProblem() {
        assertEquals(
            BackupSettingsOperationalState.DataProblem(null),
            BackupSettingsOperationalStateMapper.map(
                auth(failure = BackupFailureStatus.UNSAFE_DATABASE, lastSuccess = null),
            ),
        )
    }

    @Test
    fun transient_eachCategory() {
        for (failure in listOf(
            BackupFailureStatus.WRITE_FAILED,
            BackupFailureStatus.EXPORT_FAILED,
            BackupFailureStatus.STORAGE_UNAVAILABLE,
            BackupFailureStatus.STORAGE_READ_FAILED,
            BackupFailureStatus.STORAGE_ACCESS_UNRELIABLE,
            BackupFailureStatus.POST_WRITE_VALIDATION_FAILED,
            BackupFailureStatus.UNKNOWN,
        )) {
            assertEquals(
                BackupSettingsOperationalState.TransientFailure(3L),
                BackupSettingsOperationalStateMapper.map(auth(failure = failure, lastSuccess = 3L)),
            )
        }
    }

    @Test
    fun healthy_and_configuredNoCache() {
        assertEquals(
            BackupSettingsOperationalState.Healthy(5L),
            BackupSettingsOperationalStateMapper.map(auth(lastSuccess = 5L)),
        )
        assertEquals(
            BackupSettingsOperationalState.ConfiguredNoSuccessCache,
            BackupSettingsOperationalStateMapper.map(auth(lastSuccess = null)),
        )
    }

    @Test
    fun mappedState_containsNoUriStrings() {
        val mapped = BackupSettingsOperationalStateMapper.map(
            auth(needsReconnect = true, lastSuccess = 1L),
        )
        val text = mapped.toString()
        assertFalse(text.contains("content://"))
        assertFalse(text.contains(folderA))
        assertTrue(mapped is BackupSettingsOperationalState.NeedsReconnect)
    }

    private fun auth(
        needsReconnect: Boolean = false,
        failure: BackupFailureStatus? = null,
        lastSuccess: Long? = 1L,
    ): BackupWriteState = BackupWriteState(
        treeUriHint = folderB,
        authorizedTreeUri = folderA,
        lastSuccessfulBackupAtEpochMillis = lastSuccess,
        lastFailureCategory = failure,
        needsReconnect = needsReconnect,
    )
}
