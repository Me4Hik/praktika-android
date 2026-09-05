// 06.08.2026 Stage 12 Notifications cursor by Me4Hik START - isolated test runtime builder
package com.me4hik.praktika.runtime

import android.content.Context
import com.me4hik.praktika.data.cycle.CycleRepository
import com.me4hik.praktika.data.cycle.TimeProvider
import com.me4hik.praktika.data.local.PraktikaDatabase
import com.me4hik.praktika.data.preferences.DeferDurationOptions
import com.me4hik.praktika.data.preferences.DeferDurationPreferenceRepository
import com.me4hik.praktika.data.preferences.SoundPreferenceRepository
import com.me4hik.praktika.data.delete.RoomAnswerDeleteRepository
import com.me4hik.praktika.data.read.RoomArchiveReadRepository
import com.me4hik.praktika.data.read.RoomPracticeReadRepository
import com.me4hik.praktika.data.read.RoomScheduleReadRepository
import com.me4hik.praktika.notification.ExactAlarmCapabilityRepository
import com.me4hik.praktika.notification.NotificationOpenRequestStore
import com.me4hik.praktika.notification.NotificationPermissionPolicy
import com.me4hik.praktika.notification.NotificationPermissionRepository
import com.me4hik.praktika.notification.NotificationSyncRequester
import com.me4hik.praktika.notification.PracticeNotificationCoordinator
import com.me4hik.praktika.notification.PracticeNotificationPresenter
import com.me4hik.praktika.notification.PlatformAlarmScheduler
import com.me4hik.praktika.data.backup.write.BackupIoSessionGate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

object TestPraktikaRuntimeBuilder {
    fun build(
        context: Context,
        database: PraktikaDatabase,
        timeProvider: TimeProvider,
        databaseName: String,
        mode: RuntimeMode,
        foregroundDriver: RuntimeForegroundDriver,
        soundPreferenceRepository: SoundPreferenceRepository,
        deferDurationPreferenceRepository: DeferDurationPreferenceRepository =
            FakeDeferDurationPreferenceRepository(),
        alarmScheduler: PlatformAlarmScheduler = NoOpPlatformAlarmScheduler(),
        notificationPresenter: PracticeNotificationPresenter = NoOpPracticeNotificationPresenter(),
        permissionRepository: NotificationPermissionPolicy? = null,
    ): PraktikaRuntime {
        val appContext = context.applicationContext
        val backupCoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val backupIoSessionGate = BackupIoSessionGate()
        val backupWriteStateRepository =
            com.me4hik.praktika.data.backup.write.DataStoreBackupWriteStateRepository(appContext)
        val authorizedBackupService = RuntimeAuthorizedBackupFactory.create(
            appContext = appContext,
            database = database,
            backupScope = backupCoroutineScope,
            sharedIoGate = backupIoSessionGate,
            writeStateRepository = backupWriteStateRepository,
        )
        val backupMutationRequestSink = com.me4hik.praktika.data.backup.write.AuthorizedBackupMutationRequestSink(
            authorizedBackupService,
        )
        val backupFolderSetupCoordinator = RuntimeBackupSetupFactory.create(
            appContext = appContext,
            database = database,
            backupScope = backupCoroutineScope,
            sharedIoGate = backupIoSessionGate,
            authorizedBackupService = authorizedBackupService,
            writeStateRepository = backupWriteStateRepository,
        )
        val backupSettingsFacade = RuntimeBackupSettingsFacadeFactory.create(
            appContext = appContext,
            writeStateRepository = backupWriteStateRepository,
            authorizedBackupService = authorizedBackupService,
            setupCoordinator = backupFolderSetupCoordinator,
            sharedIoGate = backupIoSessionGate,
            backupScope = backupCoroutineScope,
        )
        val cycleRepository = CycleRepository(database, timeProvider, backupMutationRequestSink)
        val scheduleReadRepository = RoomScheduleReadRepository(database)
        val practiceReadRepository = RoomPracticeReadRepository(database)
        val archiveReadRepository = RoomArchiveReadRepository(database)
        // 07.08.2026 Stage 18 Delete Answer cursor by Me4Hik START - test delete repository
        val answerDeleteRepository = RoomAnswerDeleteRepository(database, backupMutationRequestSink)
        // 07.08.2026 Stage 18 Delete Answer cursor by Me4Hik END
        val openRequestStore = NotificationOpenRequestStore()
        val resolvedPermissionRepository = permissionRepository ?: NotificationPermissionRepository(
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
            permissionRepository = resolvedPermissionRepository,
            soundEnabledProvider = { soundPreferenceRepository.soundEnabled.first() },
            selectedSoundIdProvider = { soundPreferenceRepository.selectedSoundId.first() },
            alarmScheduler = alarmScheduler,
            notificationPresenter = notificationPresenter,
            openRequestStore = openRequestStore,
            timeProvider = timeProvider,
        )
        val syncRequester = NotificationSyncRequester { reason ->
            coordinator.sync(reason)
        }
        runtimeRef = PraktikaRuntime(
            mode = mode,
            databaseName = databaseName,
            database = database,
            timeProvider = timeProvider,
            cycleRepository = cycleRepository,
            foregroundDriver = foregroundDriver,
            scheduleReadRepository = scheduleReadRepository,
            soundPreferenceRepository = soundPreferenceRepository,
            deferDurationPreferenceRepository = deferDurationPreferenceRepository,
            practiceReadRepository = practiceReadRepository,
            archiveReadRepository = archiveReadRepository,
            answerDeleteRepository = answerDeleteRepository,
            notificationCoordinator = coordinator,
            notificationPermissionRepository = resolvedPermissionRepository,
            exactAlarmCapabilityRepository = exactAlarmCapabilityRepository,
            notificationOpenRequestStore = openRequestStore,
            notificationSyncRequester = syncRequester,
            initializer = initializer,
            backupCoroutineScope = backupCoroutineScope,
            backupIoSessionGate = backupIoSessionGate,
            authorizedBackupService = authorizedBackupService,
            backupMutationRequestSink = backupMutationRequestSink,
            backupFolderSetupCoordinator = backupFolderSetupCoordinator,
            backupSettingsFacade = backupSettingsFacade,
        )
        return runtimeRef
    }
}

class FakeDeferDurationPreferenceRepository(
    initialMinutes: Int = DeferDurationOptions.DEFAULT_MINUTES,
) : DeferDurationPreferenceRepository {
    private val state = MutableStateFlow(DeferDurationOptions.sanitize(initialMinutes))
    override val deferDurationMinutes: Flow<Int> = state
    override suspend fun setDeferDurationMinutes(minutes: Int) {
        state.value = DeferDurationOptions.sanitize(minutes)
    }
}
// 06.08.2026 Stage 12 Notifications cursor by Me4Hik END
