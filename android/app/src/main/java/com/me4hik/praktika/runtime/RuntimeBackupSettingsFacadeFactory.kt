// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.3A2 settings facade factory
package com.me4hik.praktika.runtime

import android.content.Context
import com.me4hik.praktika.data.backup.settings.BackupSettingsFacade
import com.me4hik.praktika.data.backup.settings.SafBackupReconnectAccess
import com.me4hik.praktika.data.backup.setup.BackupFolderSetupCoordinator
import com.me4hik.praktika.data.backup.storage.BackupFolderConnection
import com.me4hik.praktika.data.backup.storage.DataStoreBackupTreeConfigRepository
import com.me4hik.praktika.data.backup.write.AuthorizedBackupService
import com.me4hik.praktika.data.backup.write.BackupIoSessionGate
import com.me4hik.praktika.data.backup.write.BackupWriteStateRepository
import kotlinx.coroutines.CoroutineScope

internal object RuntimeBackupSettingsFacadeFactory {
    fun create(
        appContext: Context,
        writeStateRepository: BackupWriteStateRepository,
        authorizedBackupService: AuthorizedBackupService,
        setupCoordinator: BackupFolderSetupCoordinator,
        sharedIoGate: BackupIoSessionGate,
        backupScope: CoroutineScope,
    ): BackupSettingsFacade {
        val treeConfig = DataStoreBackupTreeConfigRepository(appContext)
        val folderConnection = BackupFolderConnection(
            contentResolver = appContext.contentResolver,
            treeConfigRepository = treeConfig,
        )
        val reconnectAccess = SafBackupReconnectAccess(
            contentResolver = appContext.contentResolver,
            folderConnection = folderConnection,
        )
        return BackupSettingsFacade(
            writeStateRepository = writeStateRepository,
            authorizedBackupService = authorizedBackupService,
            setupCoordinator = setupCoordinator,
            ioGate = sharedIoGate,
            reconnectAccess = reconnectAccess,
            backupScope = backupScope,
        )
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
