// 13.08.2026 DATA VAULT Stage 6.4A cursor by Me4Hik START - Room-only read via existing exporter snapshot
package com.me4hik.praktika.ui.acceptance

import com.me4hik.praktika.data.backup.export.BackupExportResult
import com.me4hik.praktika.data.backup.export.RoomBackupExporter
import com.me4hik.praktika.data.local.PraktikaDatabase
import kotlin.coroutines.cancellation.CancellationException

sealed class DeviceAcceptanceRoomReadResult {
    data class Ok(
        val summary: DeviceAcceptanceRoomStateSummary,
    ) : DeviceAcceptanceRoomReadResult()

    data object Unavailable : DeviceAcceptanceRoomReadResult()

    data object Unsafe : DeviceAcceptanceRoomReadResult()

    data object ReadFailed : DeviceAcceptanceRoomReadResult()
}

fun interface DeviceAcceptanceRoomStateReader {
    suspend fun read(): DeviceAcceptanceRoomReadResult
}

/**
 * Uses the same [RoomBackupExporter] transactional snapshot path as production backup generation,
 * then stops before envelope/SAF/auth/backup scheduling.
 */
class ExporterDeviceAcceptanceRoomStateReader(
    private val exporter: RoomBackupExporter,
) : DeviceAcceptanceRoomStateReader {
    constructor(database: PraktikaDatabase) : this(RoomBackupExporter(database))

    override suspend fun read(): DeviceAcceptanceRoomReadResult {
        return try {
            when (val result = exporter.export()) {
                is BackupExportResult.Success ->
                    DeviceAcceptanceRoomReadResult.Ok(
                        DeviceAcceptanceRoomStateSummaryFactory.fromPayload(result.payload),
                    )

                is BackupExportResult.DatabaseUnsafe ->
                    DeviceAcceptanceRoomReadResult.Unsafe

                is BackupExportResult.ReadFailure ->
                    DeviceAcceptanceRoomReadResult.ReadFailed
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            DeviceAcceptanceRoomReadResult.ReadFailed
        }
    }
}
// 13.08.2026 DATA VAULT Stage 6.4A cursor by Me4Hik END
