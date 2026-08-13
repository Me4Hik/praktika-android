// 13.08.2026 DATA VAULT Stage 6.3C1 cursor by Me4Hik START - harness action controller
package com.me4hik.praktika.ui.acceptance

import android.net.Uri

sealed class DeviceAcceptanceControllerResult {
    data class Completed(val report: DeviceAcceptanceEvidenceReport) : DeviceAcceptanceControllerResult()

    /** Activity must launch temporary READ picker; no report yet. */
    data object NeedsTemporaryPicker : DeviceAcceptanceControllerResult()
}

/**
 * Symbolic action router. Never accepts URI/path from shell. Never mutates BackupWriteState
 * except via separate revoke helper that only releases grants.
 */
class DeviceAcceptanceHarnessController(
    private val uriStash: DeviceAcceptanceUriStashStore,
    private val treeReader: DeviceAcceptanceTreeReader,
    private val grantRevoker: DeviceAcceptanceGrantRevoker,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val authUriProvider: suspend () -> String? = {
        AcceleratedBackupWriteStateAccess.currentAuthorizedTreeUri()
    },
    private val writeStateBound: () -> Boolean = {
        AcceleratedBackupWriteStateAccess.isBound()
    },
    // 13.08.2026 DATA VAULT Stage 6.4A cursor by Me4Hik START - optional Room reader
    private val roomStateReader: DeviceAcceptanceRoomStateReader? = null,
    // 13.08.2026 DATA VAULT Stage 6.4A cursor by Me4Hik END
) {
    suspend fun execute(action: DeviceAcceptanceHarnessAction): DeviceAcceptanceControllerResult {
        return when (action) {
            DeviceAcceptanceHarnessAction.INSPECT_CURRENT ->
                DeviceAcceptanceControllerResult.Completed(inspectCurrent())

            DeviceAcceptanceHarnessAction.STASH_CURRENT_AS_A ->
                DeviceAcceptanceControllerResult.Completed(stashCurrent(asA = true))

            DeviceAcceptanceHarnessAction.STASH_CURRENT_AS_B ->
                DeviceAcceptanceControllerResult.Completed(stashCurrent(asA = false))

            DeviceAcceptanceHarnessAction.INSPECT_STASHED_A ->
                DeviceAcceptanceControllerResult.Completed(
                    inspectStashed(uriStash.loadA(), DeviceAcceptanceHarnessAction.INSPECT_STASHED_A),
                )

            DeviceAcceptanceHarnessAction.INSPECT_STASHED_B ->
                DeviceAcceptanceControllerResult.Completed(
                    inspectStashed(uriStash.loadB(), DeviceAcceptanceHarnessAction.INSPECT_STASHED_B),
                )

            DeviceAcceptanceHarnessAction.PICK_AND_INSPECT_ONCE ->
                DeviceAcceptanceControllerResult.NeedsTemporaryPicker

            DeviceAcceptanceHarnessAction.REVOKE_CURRENT_AUTH_GRANT ->
                DeviceAcceptanceControllerResult.Completed(revokeCurrent())

            // 13.08.2026 DATA VAULT Stage 6.4A cursor by Me4Hik START - Room evidence route
            DeviceAcceptanceHarnessAction.INSPECT_ROOM_STATE ->
                DeviceAcceptanceControllerResult.Completed(inspectRoomState())
            // 13.08.2026 DATA VAULT Stage 6.4A cursor by Me4Hik END
        }
    }

    fun unknownActionReport(rawAction: String?): DeviceAcceptanceEvidenceReport {
        return statusOnly(
            action = rawAction?.takeIf { it.isNotBlank() } ?: "UNKNOWN",
            status = DeviceAcceptanceResultStatus.UNKNOWN_ACTION,
        )
    }

    suspend fun inspectTemporaryTreeUri(treeUri: Uri): DeviceAcceptanceEvidenceReport {
        val read = treeReader.readAuthorizedOrStashed(
            uriString = treeUri.toString(),
            requirePersistedGrant = false,
        )
        return folderReadToReport(
            action = DeviceAcceptanceHarnessAction.PICK_AND_INSPECT_ONCE.name,
            read = read,
            okStatus = DeviceAcceptanceResultStatus.OK,
        )
    }

    fun cancelledPickerReport(): DeviceAcceptanceEvidenceReport {
        return statusOnly(
            action = DeviceAcceptanceHarnessAction.PICK_AND_INSPECT_ONCE.name,
            status = DeviceAcceptanceResultStatus.CANCELLED,
        )
    }

    private suspend fun inspectCurrent(): DeviceAcceptanceEvidenceReport {
        if (!writeStateBound()) {
            return statusOnly(
                DeviceAcceptanceHarnessAction.INSPECT_CURRENT.name,
                DeviceAcceptanceResultStatus.RUNTIME_UNBOUND,
            )
        }
        val auth = authUriProvider()
            ?: return statusOnly(
                DeviceAcceptanceHarnessAction.INSPECT_CURRENT.name,
                DeviceAcceptanceResultStatus.NOT_CONFIGURED,
            )
        val read = treeReader.readAuthorizedOrStashed(auth, requirePersistedGrant = true)
        return folderReadToReport(
            action = DeviceAcceptanceHarnessAction.INSPECT_CURRENT.name,
            read = read,
            okStatus = DeviceAcceptanceResultStatus.OK,
        )
    }

    private suspend fun stashCurrent(asA: Boolean): DeviceAcceptanceEvidenceReport {
        val action = if (asA) {
            DeviceAcceptanceHarnessAction.STASH_CURRENT_AS_A
        } else {
            DeviceAcceptanceHarnessAction.STASH_CURRENT_AS_B
        }
        if (!writeStateBound()) {
            return statusOnly(action.name, DeviceAcceptanceResultStatus.RUNTIME_UNBOUND)
        }
        val auth = authUriProvider()
            ?: return statusOnly(action.name, DeviceAcceptanceResultStatus.NOT_CONFIGURED)
        if (asA) {
            uriStash.stashA(auth)
        } else {
            uriStash.stashB(auth)
        }
        val read = treeReader.readAuthorizedOrStashed(auth, requirePersistedGrant = true)
        return folderReadToReport(
            action = action.name,
            read = read,
            okStatus = if (asA) {
                DeviceAcceptanceResultStatus.STASHED_A
            } else {
                DeviceAcceptanceResultStatus.STASHED_B
            },
        )
    }

    private suspend fun inspectStashed(
        stashedUri: String?,
        action: DeviceAcceptanceHarnessAction,
    ): DeviceAcceptanceEvidenceReport {
        if (stashedUri.isNullOrBlank()) {
            return statusOnly(action.name, DeviceAcceptanceResultStatus.ACCESS_UNAVAILABLE)
        }
        val read = treeReader.readAuthorizedOrStashed(stashedUri, requirePersistedGrant = true)
        return folderReadToReport(
            action = action.name,
            read = read,
            okStatus = DeviceAcceptanceResultStatus.OK,
        )
    }

    private suspend fun revokeCurrent(): DeviceAcceptanceEvidenceReport {
        if (!writeStateBound()) {
            return statusOnly(
                DeviceAcceptanceHarnessAction.REVOKE_CURRENT_AUTH_GRANT.name,
                DeviceAcceptanceResultStatus.RUNTIME_UNBOUND,
            )
        }
        val auth = authUriProvider()
        val outcome = grantRevoker.revokeExactAuthorizedUri(auth)
        val status = when (outcome) {
            DeviceAcceptanceRevokeOutcome.NO_AUTH -> DeviceAcceptanceResultStatus.NO_AUTH
            DeviceAcceptanceRevokeOutcome.REVOKED -> DeviceAcceptanceResultStatus.REVOKED
            DeviceAcceptanceRevokeOutcome.ALREADY_ABSENT -> DeviceAcceptanceResultStatus.ALREADY_ABSENT
            DeviceAcceptanceRevokeOutcome.RELEASE_FAILED -> DeviceAcceptanceResultStatus.RELEASE_FAILED
        }
        return statusOnly(
            DeviceAcceptanceHarnessAction.REVOKE_CURRENT_AUTH_GRANT.name,
            status,
        )
    }

    // 13.08.2026 DATA VAULT Stage 6.4A cursor by Me4Hik START - Room inspect (no SAF/auth/backup)
    private suspend fun inspectRoomState(): DeviceAcceptanceEvidenceReport {
        val reader = roomStateReader
            ?: return statusOnly(
                DeviceAcceptanceHarnessAction.INSPECT_ROOM_STATE.name,
                DeviceAcceptanceResultStatus.ROOM_UNAVAILABLE,
            )
        return when (val read = reader.read()) {
            is DeviceAcceptanceRoomReadResult.Ok ->
                DeviceAcceptanceEvidenceReport(
                    action = DeviceAcceptanceHarnessAction.INSPECT_ROOM_STATE.name,
                    resultStatus = DeviceAcceptanceResultStatus.OK,
                    harnessExecutedAtEpochMillis = clock(),
                    slotA = null,
                    slotB = null,
                    latestValidSlot = null,
                    latestValidSequence = null,
                    latestValidSemantics = null,
                    folderFingerprintSha256 = null,
                    roomSummary = read.summary,
                )

            DeviceAcceptanceRoomReadResult.Unavailable ->
                statusOnly(
                    DeviceAcceptanceHarnessAction.INSPECT_ROOM_STATE.name,
                    DeviceAcceptanceResultStatus.ROOM_UNAVAILABLE,
                )

            DeviceAcceptanceRoomReadResult.Unsafe ->
                statusOnly(
                    DeviceAcceptanceHarnessAction.INSPECT_ROOM_STATE.name,
                    DeviceAcceptanceResultStatus.ROOM_UNSAFE,
                )

            DeviceAcceptanceRoomReadResult.ReadFailed ->
                statusOnly(
                    DeviceAcceptanceHarnessAction.INSPECT_ROOM_STATE.name,
                    DeviceAcceptanceResultStatus.ROOM_READ_FAILED,
                )
        }
    }
    // 13.08.2026 DATA VAULT Stage 6.4A cursor by Me4Hik END

    private fun folderReadToReport(
        action: String,
        read: DeviceAcceptanceFolderReadResult,
        okStatus: DeviceAcceptanceResultStatus,
    ): DeviceAcceptanceEvidenceReport {
        return when (read) {
            is DeviceAcceptanceFolderReadResult.Ok ->
                DeviceAcceptanceEvidenceBuilder.buildFolderEvidence(
                    action = action,
                    resultStatus = okStatus,
                    harnessExecutedAtEpochMillis = clock(),
                    slotABytes = read.slotA,
                    slotBBytes = read.slotB,
                )

            DeviceAcceptanceFolderReadResult.AccessUnavailable ->
                statusOnly(action, DeviceAcceptanceResultStatus.ACCESS_UNAVAILABLE)

            DeviceAcceptanceFolderReadResult.PermissionDenied ->
                statusOnly(action, DeviceAcceptanceResultStatus.PERMISSION_DENIED)

            DeviceAcceptanceFolderReadResult.ProviderFailure ->
                statusOnly(action, DeviceAcceptanceResultStatus.PROVIDER_FAILURE)
        }
    }

    private fun statusOnly(
        action: String,
        status: DeviceAcceptanceResultStatus,
    ): DeviceAcceptanceEvidenceReport {
        return DeviceAcceptanceEvidenceReport(
            action = action,
            resultStatus = status,
            harnessExecutedAtEpochMillis = clock(),
            slotA = null,
            slotB = null,
            latestValidSlot = null,
            latestValidSequence = null,
            latestValidSemantics = null,
            folderFingerprintSha256 = null,
        )
    }
}
// 13.08.2026 DATA VAULT Stage 6.3C1 cursor by Me4Hik END
