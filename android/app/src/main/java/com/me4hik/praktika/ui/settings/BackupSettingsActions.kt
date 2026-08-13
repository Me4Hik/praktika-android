// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.3B Settings backup UI
package com.me4hik.praktika.ui.settings

import com.me4hik.praktika.data.backup.settings.BackupNowResult
import com.me4hik.praktika.data.backup.settings.BackupReconnectResult
import com.me4hik.praktika.data.backup.settings.BackupSettingsAbandonResult
import com.me4hik.praktika.data.backup.settings.BackupSettingsDisableResult
import com.me4hik.praktika.data.backup.settings.BackupSettingsFacade
import com.me4hik.praktika.data.backup.settings.BackupSettingsOperationalState
import com.me4hik.praktika.data.backup.settings.BackupSettingsSetupCommitResult
import com.me4hik.praktika.data.backup.settings.BackupSettingsSetupInspectResult
import com.me4hik.praktika.data.backup.settings.BackupSettingsSetupVerifyResult
import kotlinx.coroutines.flow.Flow

/** UI-facing backup API; production wraps [BackupSettingsFacade]. */
interface BackupSettingsActions {
    fun observeOperationalStatus(): Flow<BackupSettingsOperationalState>
    suspend fun inspectCandidate(uriString: String, grantFlags: Int): BackupSettingsSetupInspectResult
    suspend fun verifyCurrentCandidate(): BackupSettingsSetupVerifyResult
    suspend fun commitVerifiedCandidate(): BackupSettingsSetupCommitResult
    suspend fun abandonSetupSession(): BackupSettingsAbandonResult
    fun requestAbandonSetupSession()
    suspend fun reconnectFolderAccess(selectedUriString: String, grantFlags: Int): BackupReconnectResult
    suspend fun runBackupNow(): BackupNowResult
    suspend fun disableAutomaticBackup(): BackupSettingsDisableResult
}

class FacadeBackupSettingsActions(
    private val facade: BackupSettingsFacade,
) : BackupSettingsActions {
    override fun observeOperationalStatus() = facade.observeOperationalStatus()
    override suspend fun inspectCandidate(uriString: String, grantFlags: Int) =
        facade.inspectCandidate(uriString, grantFlags)
    override suspend fun verifyCurrentCandidate() = facade.verifyCurrentCandidate()
    override suspend fun commitVerifiedCandidate() = facade.commitVerifiedCandidate()
    override suspend fun abandonSetupSession() = facade.abandonSetupSession()
    override fun requestAbandonSetupSession() = facade.requestAbandonSetupSession()
    override suspend fun reconnectFolderAccess(selectedUriString: String, grantFlags: Int) =
        facade.reconnectFolderAccess(selectedUriString, grantFlags)
    override suspend fun runBackupNow() = facade.runBackupNow()
    override suspend fun disableAutomaticBackup() = facade.disableAutomaticBackup()
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
