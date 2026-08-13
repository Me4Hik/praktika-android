// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.3A setup coordinator factory
package com.me4hik.praktika.runtime

import android.content.Context
import com.me4hik.praktika.data.backup.export.RoomBackupExporter
import com.me4hik.praktika.data.backup.metadata.BackupSnapshotMetadataFactory
import com.me4hik.praktika.data.backup.metadata.BuildConfigBackupAppMetadataProvider
import com.me4hik.praktika.data.backup.metadata.SystemBackupClock
import com.me4hik.praktika.data.backup.setup.BackupFolderSetupCoordinator
import com.me4hik.praktika.data.backup.setup.SafSetupCandidateAccess
import com.me4hik.praktika.data.backup.storage.BackupFolderConnection
import com.me4hik.praktika.data.backup.storage.DataStoreBackupTreeConfigRepository
import com.me4hik.praktika.data.backup.write.AuthorizedBackupService
import com.me4hik.praktika.data.backup.write.BackupIoSessionGate
import com.me4hik.praktika.data.backup.write.BackupWriteStateRepository
import com.me4hik.praktika.data.local.PraktikaDatabase
import kotlinx.coroutines.CoroutineScope

internal object RuntimeBackupSetupFactory {
    fun create(
        appContext: Context,
        database: PraktikaDatabase,
        backupScope: CoroutineScope,
        sharedIoGate: BackupIoSessionGate,
        authorizedBackupService: AuthorizedBackupService,
        // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.3A2 shared write-state
        writeStateRepository: BackupWriteStateRepository,
        // 10.08.2026 Post-release fixes cursor by Me4Hik END
    ): BackupFolderSetupCoordinator {
        val treeConfig = DataStoreBackupTreeConfigRepository(appContext)
        val folderConnection = BackupFolderConnection(
            contentResolver = appContext.contentResolver,
            treeConfigRepository = treeConfig,
        )
        val candidateAccess = SafSetupCandidateAccess(
            contentResolver = appContext.contentResolver,
            folderConnection = folderConnection,
        )
        val exporter = RoomBackupExporter(database)
        val metadataFactory = BackupSnapshotMetadataFactory(
            clock = SystemBackupClock,
            appMetadataProvider = BuildConfigBackupAppMetadataProvider(),
        )
        return BackupFolderSetupCoordinator(
            writeStateRepository = writeStateRepository,
            ioGate = sharedIoGate,
            authorizedBackupService = authorizedBackupService,
            candidateAccess = candidateAccess,
            exportAction = { exporter.export() },
            metadataFactory = metadataFactory,
        )
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
