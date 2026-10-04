// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 5.0 restore dependency wiring
package com.me4hik.praktika.ui.restore

import android.content.Context
import com.me4hik.praktika.data.backup.restore.BackupRestoreCoordinator
import com.me4hik.praktika.data.backup.restore.RoomBackupRestorer
import com.me4hik.praktika.data.backup.storage.BackupFolderConnection
import com.me4hik.praktika.data.backup.storage.DataStoreBackupTreeConfigRepository
import com.me4hik.praktika.data.backup.write.BackupIoSessionGate
import com.me4hik.praktika.runtime.PraktikaRuntime

class ProductionRestoreDependencies private constructor(
    val gateway: ProductionRestoreGateway,
    val previewMapper: BackupRestorePreviewMapper,
    // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2B2-A session controller
    val restoreSessionController: RestoreBackupRuntimeBridge,
    // 10.08.2026 Post-release fixes cursor by Me4Hik END
) {
    companion object {
        fun create(
            context: Context,
            runtime: PraktikaRuntime,
        ): ProductionRestoreDependencies {
            val appContext = context.applicationContext
            val treeConfigRepository = DataStoreBackupTreeConfigRepository(appContext)
            val folderConnection = BackupFolderConnection(
                contentResolver = appContext.contentResolver,
                treeConfigRepository = treeConfigRepository,
            )
            val coordinator = BackupRestoreCoordinator(
                database = runtime.database,
                restorer = RoomBackupRestorer(runtime.database),
                cycleRepository = runtime.cycleRepository,
                notificationCoordinator = runtime.notificationCoordinator,
                analyticsTracker = runtime.analyticsTracker,
            )
            // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2B2-A shared gate + session
            val ioGate: BackupIoSessionGate = runtime.backupIoSessionGate
            val gateway = DefaultProductionRestoreGateway(
                coordinator = coordinator,
                folderConnection = folderConnection,
                contentResolver = appContext.contentResolver,
                treeConfigRepository = treeConfigRepository,
                ioGate = ioGate,
            )
            val restoreSessionController = AuthorizedBackupRestoreSessionController(
                service = runtime.authorizedBackupService,
            )
            // 10.08.2026 Post-release fixes cursor by Me4Hik END
            return ProductionRestoreDependencies(
                gateway = gateway,
                previewMapper = BackupRestorePreviewMapper(
                    dateFormatter = ProductionRestoreDateFormatter(),
                ),
                restoreSessionController = restoreSessionController,
            )
        }
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
