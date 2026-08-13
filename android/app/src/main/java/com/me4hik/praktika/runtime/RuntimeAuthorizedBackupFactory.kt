// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2B1 runtime backup factory
package com.me4hik.praktika.runtime

import android.content.Context
import com.me4hik.praktika.data.backup.export.RoomBackupExporter
import com.me4hik.praktika.data.backup.metadata.BackupSnapshotMetadataFactory
import com.me4hik.praktika.data.backup.metadata.BuildConfigBackupAppMetadataProvider
import com.me4hik.praktika.data.backup.metadata.SystemBackupClock
import com.me4hik.praktika.data.backup.write.AuthorizedBackupService
import com.me4hik.praktika.data.backup.write.BackupIoSessionGate
import com.me4hik.praktika.data.backup.write.BackupWriteStateRepository
import com.me4hik.praktika.data.backup.write.ProductionBackupCoordinator
import com.me4hik.praktika.data.backup.write.ProductionBackupCoordinatorFactory
import com.me4hik.praktika.data.backup.write.SafAuthorizedBackupStorageResolver
import com.me4hik.praktika.data.local.PraktikaDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/**
 * Builds the process-lifetime AuthorizedBackupService graph.
 * Callers must inject the SAME [BackupIoSessionGate] instance retained on [PraktikaRuntime].
 */
internal object RuntimeAuthorizedBackupFactory {
    fun create(
        appContext: Context,
        database: PraktikaDatabase,
        backupScope: CoroutineScope,
        sharedIoGate: BackupIoSessionGate,
        // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.3A2 shared write-state
        writeStateRepository: BackupWriteStateRepository,
        // 10.08.2026 Post-release fixes cursor by Me4Hik END
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): AuthorizedBackupService {
        val storageResolver = SafAuthorizedBackupStorageResolver(appContext.contentResolver)
        val exporter = RoomBackupExporter(database)
        val metadataFactory = BackupSnapshotMetadataFactory(
            clock = SystemBackupClock,
            appMetadataProvider = BuildConfigBackupAppMetadataProvider(),
        )
        val coordinatorFactory = ProductionBackupCoordinatorFactory { storage ->
            ProductionBackupCoordinator(
                storage = storage,
                exportAction = { exporter.export() },
                metadataFactory = metadataFactory,
                scope = backupScope,
                ioDispatcher = ioDispatcher,
            )
        }
        return AuthorizedBackupService(
            writeStateRepository = writeStateRepository,
            storageResolver = storageResolver,
            coordinatorFactory = coordinatorFactory,
            scope = backupScope,
            ioDispatcher = ioDispatcher,
            ioGate = sharedIoGate,
        )
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
