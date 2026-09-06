// 04.08.2026 Accelerated Test Mode cursor by Me4Hik START - production runtime factory
// 06.08.2026 Stage 12 Notifications cursor by Me4Hik START - notification stack wiring
package com.me4hik.praktika.runtime

import android.content.Context
import com.me4hik.praktika.data.cycle.CycleRepository
import com.me4hik.praktika.data.cycle.SystemTimeProvider
import com.me4hik.praktika.data.local.PraktikaDatabase
import com.me4hik.praktika.data.preferences.DataStoreDeferDurationPreferenceRepository
import com.me4hik.praktika.data.preferences.DataStoreQuestionWordingPreferenceRepository
import com.me4hik.praktika.data.preferences.DataStoreSoundPreferenceRepository
import com.me4hik.praktika.data.preferences.QuestionWordingModeSource
import com.me4hik.praktika.data.delete.RoomAnswerDeleteRepository
import com.me4hik.praktika.data.read.RoomAnalyticsReadRepository
import com.me4hik.praktika.data.read.RoomArchiveReadRepository
import com.me4hik.praktika.data.read.RoomPracticeReadRepository
import com.me4hik.praktika.data.read.RoomScheduleReadRepository
import com.me4hik.praktika.notification.AndroidAlarmScheduler
import com.me4hik.praktika.notification.AndroidPracticeNotificationPresenter
import com.me4hik.praktika.notification.ExactAlarmCapabilityRepository
import com.me4hik.praktika.notification.NotificationOpenRequestStore
import com.me4hik.praktika.notification.NotificationPermissionRepository
import com.me4hik.praktika.notification.NotificationSyncRequester
import com.me4hik.praktika.notification.PracticeNotificationCoordinator
import com.me4hik.praktika.data.backup.write.BackupIoSessionGate
import com.me4hik.praktika.data.backup.write.AuthorizedBackupMutationRequestSink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

object RuntimeFactory {
    private const val PRODUCTION_DATABASE_NAME = "praktika.db"

    fun create(context: Context): PraktikaRuntime {
        val appContext = context.applicationContext
        val database = PraktikaDatabase.getInstance(appContext, PRODUCTION_DATABASE_NAME)
        // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2B1 runtime backup graph
        val backupCoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val backupIoSessionGate = BackupIoSessionGate()
        // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.3A2 shared write-state
        val backupWriteStateRepository =
            com.me4hik.praktika.data.backup.write.DataStoreBackupWriteStateRepository(appContext)
        // 10.08.2026 Post-release fixes cursor by Me4Hik END
        val authorizedBackupService = RuntimeAuthorizedBackupFactory.create(
            appContext = appContext,
            database = database,
            backupScope = backupCoroutineScope,
            sharedIoGate = backupIoSessionGate,
            writeStateRepository = backupWriteStateRepository,
        )
        // 10.08.2026 Post-release fixes cursor by Me4Hik END
        // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2C mutation sink
        val backupMutationRequestSink = AuthorizedBackupMutationRequestSink(authorizedBackupService)
        // 10.08.2026 Post-release fixes cursor by Me4Hik END
        // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.3A setup core
        val backupFolderSetupCoordinator = RuntimeBackupSetupFactory.create(
            appContext = appContext,
            database = database,
            backupScope = backupCoroutineScope,
            sharedIoGate = backupIoSessionGate,
            authorizedBackupService = authorizedBackupService,
            writeStateRepository = backupWriteStateRepository,
        )
        // 10.08.2026 Post-release fixes cursor by Me4Hik END
        // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.3A2 settings facade
        val backupSettingsFacade = RuntimeBackupSettingsFacadeFactory.create(
            appContext = appContext,
            writeStateRepository = backupWriteStateRepository,
            authorizedBackupService = authorizedBackupService,
            setupCoordinator = backupFolderSetupCoordinator,
            sharedIoGate = backupIoSessionGate,
            backupScope = backupCoroutineScope,
        )
        // 10.08.2026 Post-release fixes cursor by Me4Hik END
        val timeProvider = SystemTimeProvider()
        val soundPreferenceRepository = DataStoreSoundPreferenceRepository(appContext)
        val deferDurationPreferenceRepository = DataStoreDeferDurationPreferenceRepository(appContext)
        val questionWordingPreferenceRepository = DataStoreQuestionWordingPreferenceRepository(appContext)
        val cycleRepository = CycleRepository(
            database,
            timeProvider,
            backupMutationRequestSink,
            wordingModeSource = QuestionWordingModeSource {
                questionWordingPreferenceRepository.wordingMode.first()
            },
        )
        val scheduleReadRepository = RoomScheduleReadRepository(database)
        val practiceReadRepository = RoomPracticeReadRepository(database)
        val archiveReadRepository = RoomArchiveReadRepository(database)
        val analyticsReadRepository = RoomAnalyticsReadRepository(database)
        // 07.08.2026 Stage 18 Delete Answer cursor by Me4Hik START - production delete repository
        val answerDeleteRepository = RoomAnswerDeleteRepository(database, backupMutationRequestSink)
        // 07.08.2026 Stage 18 Delete Answer cursor by Me4Hik END
        val openRequestStore = NotificationOpenRequestStore()
        val alarmScheduler = AndroidAlarmScheduler(appContext)
        val notificationPresenter = AndroidPracticeNotificationPresenter(appContext)
        val permissionRepository = NotificationPermissionRepository(
            context = appContext,
            soundEnabledFlow = soundPreferenceRepository.soundEnabled,
        )
        val exactAlarmCapabilityRepository = ExactAlarmCapabilityRepository(appContext)

        lateinit var runtimeRef: PraktikaRuntime
        val initializer = PraktikaRuntimeInitializer(appContext) { runtimeRef }
        val coordinator = PracticeNotificationCoordinator(
            initializer = initializer,
            cycleRepository = cycleRepository,
            practiceReadRepository = practiceReadRepository,
            permissionRepository = permissionRepository,
            soundEnabledProvider = { soundPreferenceRepository.soundEnabled.first() },
            selectedSoundIdProvider = { soundPreferenceRepository.selectedSoundId.first() },
            alarmScheduler = alarmScheduler,
            notificationPresenter = notificationPresenter,
            openRequestStore = openRequestStore,
            timeProvider = timeProvider,
        )
        // 07.08.2026 Stage 26 Release cursor by Me4Hik START - notification sync always on IO
        val syncRequester = NotificationSyncRequester { reason ->
            withContext(Dispatchers.IO) {
                coordinator.sync(reason)
            }
        }
        // 07.08.2026 Stage 26 Release cursor by Me4Hik END
        val foregroundDriver = ProductionForegroundDriver(
            coordinator = coordinator,
            practiceReadRepository = practiceReadRepository,
            nowEpochMillis = { timeProvider.nowEpochMillis() },
        )
        runtimeRef = PraktikaRuntime(
            mode = RuntimeMode.PRODUCTION,
            databaseName = PRODUCTION_DATABASE_NAME,
            database = database,
            timeProvider = timeProvider,
            cycleRepository = cycleRepository,
            foregroundDriver = foregroundDriver,
            scheduleReadRepository = scheduleReadRepository,
            soundPreferenceRepository = soundPreferenceRepository,
            deferDurationPreferenceRepository = deferDurationPreferenceRepository,
            questionWordingPreferenceRepository = questionWordingPreferenceRepository,
            practiceReadRepository = practiceReadRepository,
            archiveReadRepository = archiveReadRepository,
            analyticsReadRepository = analyticsReadRepository,
            answerDeleteRepository = answerDeleteRepository,
            notificationCoordinator = coordinator,
            notificationPermissionRepository = permissionRepository,
            exactAlarmCapabilityRepository = exactAlarmCapabilityRepository,
            notificationOpenRequestStore = openRequestStore,
            notificationSyncRequester = syncRequester,
            initializer = initializer,
            // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2B1 runtime backup ownership
            backupCoroutineScope = backupCoroutineScope,
            backupIoSessionGate = backupIoSessionGate,
            authorizedBackupService = authorizedBackupService,
            // 10.08.2026 Post-release fixes cursor by Me4Hik END
            // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.2C mutation sink
            backupMutationRequestSink = backupMutationRequestSink,
            // 10.08.2026 Post-release fixes cursor by Me4Hik END
            // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.3A setup core
            backupFolderSetupCoordinator = backupFolderSetupCoordinator,
            // 10.08.2026 Post-release fixes cursor by Me4Hik END
            // 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Stage 6.3A2 settings facade
            backupSettingsFacade = backupSettingsFacade,
            // 10.08.2026 Post-release fixes cursor by Me4Hik END
        )
        return runtimeRef
    }
}
// 06.08.2026 Stage 12 Notifications cursor by Me4Hik END
// 04.08.2026 Accelerated Test Mode cursor by Me4Hik END
