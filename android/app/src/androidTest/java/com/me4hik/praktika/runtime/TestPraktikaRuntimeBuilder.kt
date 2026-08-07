// 06.08.2026 Stage 12 Notifications cursor by Me4Hik START - isolated test runtime builder
package com.me4hik.praktika.runtime

import android.content.Context
import com.me4hik.praktika.data.cycle.CycleRepository
import com.me4hik.praktika.data.cycle.TimeProvider
import com.me4hik.praktika.data.local.PraktikaDatabase
import com.me4hik.praktika.data.preferences.SoundPreferenceRepository
import com.me4hik.praktika.data.delete.RoomAnswerDeleteRepository
import com.me4hik.praktika.data.read.RoomArchiveReadRepository
import com.me4hik.praktika.data.read.RoomPracticeReadRepository
import com.me4hik.praktika.data.read.RoomScheduleReadRepository
import com.me4hik.praktika.notification.NotificationOpenRequestStore
import com.me4hik.praktika.notification.NotificationPermissionPolicy
import com.me4hik.praktika.notification.NotificationPermissionRepository
import com.me4hik.praktika.notification.NotificationSyncRequester
import com.me4hik.praktika.notification.PracticeNotificationCoordinator
import com.me4hik.praktika.notification.PracticeNotificationPresenter
import com.me4hik.praktika.notification.PlatformAlarmScheduler
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
        alarmScheduler: PlatformAlarmScheduler = NoOpPlatformAlarmScheduler(),
        notificationPresenter: PracticeNotificationPresenter = NoOpPracticeNotificationPresenter(),
        permissionRepository: NotificationPermissionPolicy? = null,
    ): PraktikaRuntime {
        val appContext = context.applicationContext
        val cycleRepository = CycleRepository(database, timeProvider)
        val scheduleReadRepository = RoomScheduleReadRepository(database)
        val practiceReadRepository = RoomPracticeReadRepository(database)
        val archiveReadRepository = RoomArchiveReadRepository(database)
        // 07.08.2026 Stage 18 Delete Answer cursor by Me4Hik START - test delete repository
        val answerDeleteRepository = RoomAnswerDeleteRepository(database)
        // 07.08.2026 Stage 18 Delete Answer cursor by Me4Hik END
        val openRequestStore = NotificationOpenRequestStore()
        val resolvedPermissionRepository = permissionRepository ?: NotificationPermissionRepository(
            context = appContext,
            soundEnabledFlow = soundPreferenceRepository.soundEnabled,
        )
        lateinit var runtimeRef: PraktikaRuntime
        val initializer = PraktikaRuntimeInitializer(appContext) { runtimeRef }
        val coordinator = PracticeNotificationCoordinator(
            initializer = initializer,
            cycleRepository = cycleRepository,
            practiceReadRepository = practiceReadRepository,
            permissionRepository = resolvedPermissionRepository,
            soundEnabledProvider = { soundPreferenceRepository.soundEnabled.first() },
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
            practiceReadRepository = practiceReadRepository,
            archiveReadRepository = archiveReadRepository,
            answerDeleteRepository = answerDeleteRepository,
            notificationCoordinator = coordinator,
            notificationPermissionRepository = resolvedPermissionRepository,
            notificationOpenRequestStore = openRequestStore,
            notificationSyncRequester = syncRequester,
            initializer = initializer,
        )
        return runtimeRef
    }
}
// 06.08.2026 Stage 12 Notifications cursor by Me4Hik END
