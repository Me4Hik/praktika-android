// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 5.0 default restore gateway
package com.me4hik.praktika.ui.restore

import android.content.ContentResolver
import android.net.Uri
import com.me4hik.praktika.data.backup.restore.BackupRestoreCoordinator
import com.me4hik.praktika.data.backup.restore.BackupRestoreCoordinatorResult
import com.me4hik.praktika.data.backup.restore.BackupRestorePreview
import com.me4hik.praktika.data.backup.restore.BackupRestoreSelectedIdentity
import com.me4hik.praktika.data.backup.restore.PrepareExecuteRestoreResult
import com.me4hik.praktika.data.backup.restore.RestoreTargetEligibility
import com.me4hik.praktika.data.backup.storage.BackupFolderConnection
import com.me4hik.praktika.data.backup.storage.BackupFolderConnectionResult
import com.me4hik.praktika.data.backup.storage.BackupStorageProvider
import com.me4hik.praktika.data.backup.storage.BackupTreeConfigRepository
import com.me4hik.praktika.data.backup.write.BackupIoSessionGate
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.first

class DefaultProductionRestoreGateway(
    private val coordinator: BackupRestoreCoordinator,
    private val folderConnection: BackupFolderConnection,
    private val contentResolver: ContentResolver,
    private val treeConfigRepository: BackupTreeConfigRepository,
    // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2B2-A shared IO gate
    private val ioGate: BackupIoSessionGate,
    // 10.08.2026 Post-release fixes cursor by Me4Hik END
) : ProductionRestoreGateway {
    // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2B2-A gate identity seam
    internal val sharedIoGateForTests: BackupIoSessionGate
        get() = ioGate
    // 10.08.2026 Post-release fixes cursor by Me4Hik END

    override suspend fun checkTargetEligibility(): RestoreTargetEligibility {
        return coordinator.checkTargetEligibility()
    }

    override suspend fun readTreeUriHint(): String? {
        return treeConfigRepository.treeUriHint.first()
    }

    override suspend fun readActiveTreeUri(): String? {
        return treeConfigRepository.treeUriHint.first()
    }

    override suspend fun clearTreeUri() {
        treeConfigRepository.clearTreeUri()
    }

    override suspend fun connectFolder(
        uri: Uri,
        grantFlags: Int,
    ): ProductionRestoreConnectOutcome {
        // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2B2-A gated connect
        return ioGate.withExclusive {
            connectFolderUnlocked(uri, grantFlags)
        }
        // 10.08.2026 Post-release fixes cursor by Me4Hik END
    }

    // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2B2-A composite connect+inspect
    override suspend fun connectAndInspectCandidate(
        uri: Uri,
        grantFlags: Int,
    ): ProductionRestoreCandidateIoResult {
        return ioGate.withExclusive {
            when (val connect = connectFolderUnlocked(uri, grantFlags)) {
                is ProductionRestoreConnectOutcome.Success -> {
                    val inspect = coordinator.inspectLatest(connect.storage)
                    ProductionRestoreCandidateIoResult.Connected(
                        storage = connect.storage,
                        treeUri = connect.treeUri,
                        inspect = inspect,
                    )
                }

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
    }
    // 10.08.2026 Post-release fixes cursor by Me4Hik END

    override suspend fun commitActiveBackupFolder(uri: Uri): Boolean {
        return try {
            treeConfigRepository.saveTreeUri(uri.toString())
            true
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun releaseRejectedTransientGrant(
        uri: Uri,
        grantFlags: Int,
    ) {
        val activeUri = readActiveTreeUri()
        if (activeUri != null && Uri.parse(activeUri) == uri) {
            return
        }
        folderConnection.releasePersistedPermission(uri, grantFlags)
    }

    override suspend fun inspectLatest(
        storage: BackupStorageProvider,
    ): BackupRestoreCoordinatorResult {
        // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2B2-A gated inspect
        return ioGate.withExclusive {
            coordinator.inspectLatest(storage)
        }
        // 10.08.2026 Post-release fixes cursor by Me4Hik END
    }

    override suspend fun executeRestore(
        storage: BackupStorageProvider,
        previewIdentity: BackupRestoreSelectedIdentity,
        preview: BackupRestorePreview?,
    ): BackupRestoreCoordinatorResult {
        // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2B2-A gate only physical prepare
        val prepared = ioGate.withExclusive {
            coordinator.prepareExecuteRestore(
                storage = storage,
                previewIdentity = previewIdentity,
                preview = preview,
            )
        }
        return when (prepared) {
            is PrepareExecuteRestoreResult.Ready -> coordinator.completePreparedRestore(prepared)
            is PrepareExecuteRestoreResult.Terminal -> prepared.result
        }
        // 10.08.2026 Post-release fixes cursor by Me4Hik END
    }

    // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2B2-A unlocked connect helper
    private suspend fun connectFolderUnlocked(
        uri: Uri,
        grantFlags: Int,
    ): ProductionRestoreConnectOutcome {
        return when (val result = folderConnection.connectTransient(uri, grantFlags)) {
            is BackupFolderConnectionResult.Success -> {
                ProductionRestoreConnectOutcome.Success(
                    storage = folderConnection.createStorage(result.treeUri, contentResolver),
                    treeUri = result.treeUri,
                )
            }

            BackupFolderConnectionResult.PermissionLost -> {
                ProductionRestoreConnectOutcome.PermissionLost
            }

            BackupFolderConnectionResult.Unavailable -> {
                ProductionRestoreConnectOutcome.ReadFailure
            }

            is BackupFolderConnectionResult.ProviderError -> {
                ProductionRestoreConnectOutcome.ReadFailure
            }
        }
    }
    // 10.08.2026 Post-release fixes cursor by Me4Hik END
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
