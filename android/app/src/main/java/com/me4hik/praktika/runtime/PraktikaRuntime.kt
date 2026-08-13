// 04.08.2026 Accelerated Test Mode cursor by Me4Hik START - runtime container
// 06.08.2026 Stage 12 Notifications cursor by Me4Hik START - notification runtime wiring
// 07.08.2026 Stage 15 Archive By Date cursor by Me4Hik START - archive read repository wiring
package com.me4hik.praktika.runtime

import com.me4hik.praktika.data.backup.write.AuthorizedBackupService
import com.me4hik.praktika.data.backup.write.BackupIoSessionGate
import com.me4hik.praktika.data.backup.write.BackupMutationRequestSink
import com.me4hik.praktika.data.cycle.CycleRepository
import com.me4hik.praktika.data.cycle.TimeProvider
import com.me4hik.praktika.data.local.PraktikaDatabase
import com.me4hik.praktika.data.preferences.SoundPreferenceRepository
import com.me4hik.praktika.data.delete.AnswerDeleteRepository
import com.me4hik.praktika.data.read.ArchiveReadRepository
import com.me4hik.praktika.data.read.PracticeReadRepository
import com.me4hik.praktika.data.read.ScheduleReadRepository
import com.me4hik.praktika.notification.ExactAlarmCapabilityPolicy
import com.me4hik.praktika.notification.NotificationOpenRequestStore
import com.me4hik.praktika.notification.NotificationPermissionPolicy
import com.me4hik.praktika.notification.NotificationSyncRequester
import com.me4hik.praktika.notification.PracticeNotificationCoordinator
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope

data class PraktikaRuntime(
    val mode: RuntimeMode,
    val databaseName: String,
    val database: PraktikaDatabase,
    val timeProvider: TimeProvider,
    val cycleRepository: CycleRepository,
    val foregroundDriver: RuntimeForegroundDriver,
    val scheduleReadRepository: ScheduleReadRepository,
    val soundPreferenceRepository: SoundPreferenceRepository,
    val practiceReadRepository: PracticeReadRepository,
    val archiveReadRepository: ArchiveReadRepository,
    // 07.08.2026 Stage 18 Delete Answer cursor by Me4Hik START - delete repository wiring
    val answerDeleteRepository: AnswerDeleteRepository,
    // 07.08.2026 Stage 18 Delete Answer cursor by Me4Hik END
    val notificationCoordinator: PracticeNotificationCoordinator,
    val notificationPermissionRepository: NotificationPermissionPolicy,
    val exactAlarmCapabilityRepository: ExactAlarmCapabilityPolicy,
    val notificationOpenRequestStore: NotificationOpenRequestStore,
    val notificationSyncRequester: NotificationSyncRequester,
    val initializer: PraktikaRuntimeInitializer,
    // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2B1 runtime backup ownership
    val backupCoroutineScope: CoroutineScope,
    val backupIoSessionGate: BackupIoSessionGate,
    val authorizedBackupService: AuthorizedBackupService,
    val startupCatchupOnce: AtomicBoolean = AtomicBoolean(false),
    // 10.08.2026 Post-release fixes cursor by Me4Hik END
    // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2C mutation sink identity
    val backupMutationRequestSink: BackupMutationRequestSink,
    // 10.08.2026 Post-release fixes cursor by Me4Hik END
    // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.3A setup core
    val backupFolderSetupCoordinator: com.me4hik.praktika.data.backup.setup.BackupFolderSetupCoordinator,
    // 10.08.2026 Post-release fixes cursor by Me4Hik END
    // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.3A2 settings facade
    val backupSettingsFacade: com.me4hik.praktika.data.backup.settings.BackupSettingsFacade,
    // 10.08.2026 Post-release fixes cursor by Me4Hik END
)
// 06.08.2026 Stage 12 Notifications cursor by Me4Hik END
// 07.08.2026 Stage 15 Archive By Date cursor by Me4Hik END
// 04.08.2026 Accelerated Test Mode cursor by Me4Hik END
