// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.3A2 reconnect access
package com.me4hik.praktika.data.backup.settings

/**
 * Narrow reconnect primitives: take persistable grant, validate, optional release.
 * Does not create setup sessions or write backups.
 */
interface BackupReconnectAccess {
    /**
     * Take persisted READ|WRITE then validate tree usability.
     * On validation failure after a successful take, the grant remains for ownership policy.
     */
    suspend fun acquireAndValidate(
        uriString: String,
        grantFlags: Int,
    ): BackupReconnectAcquireResult

    fun releasePersistedGrant(uriString: String, grantFlags: Int)

    fun hasPersistedGrant(uriString: String): Boolean
}

sealed interface BackupReconnectAcquireResult {
    data object AcquiredAndValid : BackupReconnectAcquireResult

    /** takePersistable failed; no successful new grant acquisition. */
    data object PermissionAcquireFailed : BackupReconnectAcquireResult

    /** take succeeded; tree/RW validation failed. Grant may be present. */
    data object ValidationFailed : BackupReconnectAcquireResult
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
