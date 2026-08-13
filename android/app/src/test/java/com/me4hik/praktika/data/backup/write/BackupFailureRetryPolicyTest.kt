package com.me4hik.praktika.data.backup.write

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupFailureRetryPolicyTest {
    @Test
    fun transientCategories_allowAllTriggers() {
        val categories = listOf(
            BackupFailureStatus.WRITE_FAILED,
            BackupFailureStatus.EXPORT_FAILED,
            BackupFailureStatus.STORAGE_UNAVAILABLE,
            BackupFailureStatus.STORAGE_READ_FAILED,
            BackupFailureStatus.STORAGE_ACCESS_UNRELIABLE,
            BackupFailureStatus.POST_WRITE_VALIDATION_FAILED,
        )
        for (category in categories) {
            assertEquals(BackupFailureRetryGroup.TRANSIENT, BackupFailureRetryPolicy.groupOf(category))
            for (trigger in BackupAttemptTrigger.entries) {
                assertTrue(
                    "$category $trigger",
                    BackupFailureRetryPolicy.allowsAttempt(category, trigger, needsReconnect = false),
                )
            }
        }
    }

    @Test
    fun databaseState_allowsAllTriggers() {
        assertEquals(
            BackupFailureRetryGroup.DATABASE_STATE,
            BackupFailureRetryPolicy.groupOf(BackupFailureStatus.UNSAFE_DATABASE),
        )
        for (trigger in BackupAttemptTrigger.entries) {
            assertTrue(
                BackupFailureRetryPolicy.allowsAttempt(
                    BackupFailureStatus.UNSAFE_DATABASE,
                    trigger,
                    needsReconnect = false,
                ),
            )
        }
    }

    @Test
    fun unknown_behavesAsTransient() {
        assertEquals(
            BackupFailureRetryGroup.UNKNOWN,
            BackupFailureRetryPolicy.groupOf(BackupFailureStatus.UNKNOWN),
        )
        for (trigger in BackupAttemptTrigger.entries) {
            assertTrue(
                BackupFailureRetryPolicy.allowsAttempt(
                    BackupFailureStatus.UNKNOWN,
                    trigger,
                    needsReconnect = false,
                ),
            )
        }
    }

    @Test
    fun userActionRequired_suppressesMutationOnly() {
        val categories = listOf(
            BackupFailureStatus.AMBIGUOUS_SLOT,
            BackupFailureStatus.UNTRUSTED_ARTIFACTS,
            BackupFailureStatus.SEQUENCE_EXHAUSTED,
        )
        for (category in categories) {
            assertEquals(
                BackupFailureRetryGroup.USER_ACTION_REQUIRED,
                BackupFailureRetryPolicy.groupOf(category),
            )
            assertFalse(
                BackupFailureRetryPolicy.allowsAttempt(
                    category,
                    BackupAttemptTrigger.MUTATION,
                    needsReconnect = false,
                ),
            )
            assertTrue(
                BackupFailureRetryPolicy.allowsAttempt(
                    category,
                    BackupAttemptTrigger.STARTUP,
                    needsReconnect = false,
                ),
            )
            assertTrue(
                BackupFailureRetryPolicy.allowsAttempt(
                    category,
                    BackupAttemptTrigger.MANUAL,
                    needsReconnect = false,
                ),
            )
            assertTrue(
                BackupFailureRetryPolicy.allowsAttempt(
                    category,
                    BackupAttemptTrigger.RESTORE_CATCHUP,
                    needsReconnect = false,
                ),
            )
        }
    }

    @Test
    fun permissionLost_blocksAllTriggers() {
        assertEquals(
            BackupFailureRetryGroup.RECONNECT_REQUIRED,
            BackupFailureRetryPolicy.groupOf(BackupFailureStatus.PERMISSION_LOST),
        )
        for (trigger in BackupAttemptTrigger.entries) {
            assertFalse(
                BackupFailureRetryPolicy.allowsAttempt(
                    BackupFailureStatus.PERMISSION_LOST,
                    trigger,
                    needsReconnect = false,
                ),
            )
            assertFalse(
                BackupFailureRetryPolicy.allowsAttempt(
                    null,
                    trigger,
                    needsReconnect = true,
                ),
            )
        }
    }

    @Test
    fun fromPersistedName_unknownMapsSafely() = runTest {
        assertEquals(
            BackupFailureStatus.UNKNOWN,
            BackupFailureStatus.fromPersistedName("FUTURE_CATEGORY_XYZ"),
        )
        assertEquals(null, BackupFailureStatus.fromPersistedName(null))
        assertEquals(null, BackupFailureStatus.fromPersistedName(""))
        assertEquals(
            BackupFailureStatus.WRITE_FAILED,
            BackupFailureStatus.fromPersistedName("WRITE_FAILED"),
        )
    }
}
