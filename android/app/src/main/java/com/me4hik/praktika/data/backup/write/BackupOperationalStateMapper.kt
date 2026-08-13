// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.1 operational state mapper
package com.me4hik.praktika.data.backup.write

/**
 * Derived operational state for future Stage 6.2 gates / Settings labels.
 * Does not inspect SAF grants — caller supplies [grantUsable] / [restoreSuppressed].
 */
sealed interface BackupOperationalState {
    data object NotConfigured : BackupOperationalState

    data object RestoreFolderSelectedOnly : BackupOperationalState

    data object InvalidConfig : BackupOperationalState

    data object Active : BackupOperationalState

    data object ActiveButHintDifferent : BackupOperationalState

    data object ActiveWithRetryableFailure : BackupOperationalState

    data object NeedsAttentionRecheckAllowed : BackupOperationalState

    data object NeedsReconnect : BackupOperationalState
}

object BackupOperationalStateMapper {
    fun map(
        state: BackupWriteState,
        grantUsable: Boolean = true,
        restoreSuppressed: Boolean = false,
    ): BackupOperationalState {
        val authorizedRaw = state.authorizedTreeUri
        if (authorizedRaw == null) {
            return if (BackupTreeUriSanitizer.isUsableTreeUriString(state.treeUriHint)) {
                BackupOperationalState.RestoreFolderSelectedOnly
            } else {
                BackupOperationalState.NotConfigured
            }
        }
        if (!BackupTreeUriSanitizer.isUsableTreeUriString(authorizedRaw)) {
            return BackupOperationalState.InvalidConfig
        }
        if (state.needsReconnect || !grantUsable) {
            return BackupOperationalState.NeedsReconnect
        }
        if (restoreSuppressed) {
            // Still "configured active folder"; suppression is a runtime gate, not a config state.
            return when {
                state.treeUriHint != null &&
                    state.treeUriHint != authorizedRaw &&
                    BackupTreeUriSanitizer.isUsableTreeUriString(state.treeUriHint) ->
                    BackupOperationalState.ActiveButHintDifferent
                else -> BackupOperationalState.Active
            }
        }
        val failure = state.lastFailureCategory
        val group = BackupFailureRetryPolicy.groupOf(failure)
        val base = when {
            state.treeUriHint != null &&
                state.treeUriHint != authorizedRaw &&
                BackupTreeUriSanitizer.isUsableTreeUriString(state.treeUriHint) ->
                BackupOperationalState.ActiveButHintDifferent
            else -> BackupOperationalState.Active
        }
        if (failure == null) {
            return base
        }
        return when (group) {
            BackupFailureRetryGroup.USER_ACTION_REQUIRED ->
                BackupOperationalState.NeedsAttentionRecheckAllowed
            BackupFailureRetryGroup.TRANSIENT,
            BackupFailureRetryGroup.DATABASE_STATE,
            BackupFailureRetryGroup.UNKNOWN,
            -> BackupOperationalState.ActiveWithRetryableFailure
            BackupFailureRetryGroup.RECONNECT_REQUIRED ->
                BackupOperationalState.NeedsReconnect
        }
    }

    /**
     * Whether a backup attempt may be started for [trigger].
     * Physical write safety is decided later by Stage 6.0 inspection — not here.
     */
    fun canAttemptBackup(
        state: BackupWriteState,
        trigger: BackupAttemptTrigger,
        grantUsable: Boolean = true,
        restoreSuppressed: Boolean = false,
    ): Boolean {
        if (restoreSuppressed) {
            return false
        }
        if (!grantUsable) {
            return false
        }
        if (state.needsReconnect) {
            return false
        }
        val authorized = state.authorizedTreeUri
        if (!BackupTreeUriSanitizer.isUsableTreeUriString(authorized)) {
            return false
        }
        return BackupFailureRetryPolicy.allowsAttempt(
            status = state.lastFailureCategory,
            trigger = trigger,
            needsReconnect = state.needsReconnect,
        )
    }

    /** Future automatic write target — authorized URI only, never treeUriHint. */
    fun automaticWriteTargetUri(state: BackupWriteState): String? {
        val authorized = state.authorizedTreeUri
        return if (BackupTreeUriSanitizer.isUsableTreeUriString(authorized)) {
            authorized
        } else {
            null
        }
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
