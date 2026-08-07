// 04.08.2026 Accelerated Test Mode cursor by Me4Hik START - accelerated runtime factory
// 06.08.2026 Stage 12 Notifications cursor by Me4Hik START - shared notification stack
package com.me4hik.praktika.runtime

import android.content.Context
import com.me4hik.praktika.accelerated.AcceleratedCycleDriver
import com.me4hik.praktika.accelerated.AcceleratedTimeProvider
import com.me4hik.praktika.accelerated.AcceleratedTimeStorage
import com.me4hik.praktika.accelerated.AndroidMonotonicTimeSource
import com.me4hik.praktika.data.cycle.CycleRepository
import com.me4hik.praktika.data.local.PraktikaDatabase
import com.me4hik.praktika.data.preferences.DataStoreSoundPreferenceRepository
import com.me4hik.praktika.data.delete.RoomAnswerDeleteRepository
import com.me4hik.praktika.data.read.RoomArchiveReadRepository
import com.me4hik.praktika.data.read.RoomPracticeReadRepository
import com.me4hik.praktika.data.read.RoomScheduleReadRepository
import com.me4hik.praktika.notification.AndroidAlarmScheduler
import com.me4hik.praktika.notification.AndroidPracticeNotificationPresenter
import com.me4hik.praktika.notification.NotificationOpenRequestStore
import com.me4hik.praktika.notification.NotificationPermissionRepository
import com.me4hik.praktika.notification.NotificationSyncRequester
import com.me4hik.praktika.notification.PracticeNotificationCoordinator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

object RuntimeFactory {
    const val ACCELERATED_DATABASE_NAME = "praktika_accelerated.db"

    fun create(context: Context): PraktikaRuntime {
        val appContext = context.applicationContext
        val database = PraktikaDatabase.getInstance(appContext, ACCELERATED_DATABASE_NAME)
        val monotonic = AndroidMonotonicTimeSource(appContext)
        val storage = AcceleratedTimeStorage(appContext)

        val databaseFloor = runBlocking {
            database.practiceStateDao().get()?.lastProcessedAtEpochMillis ?: 0L
        }

        val loadedState = runBlocking { storage.loadOrCreateInitial(monotonic) }
        val adjustedState = AcceleratedTimeProvider.applyBootPolicy(loadedState, monotonic, databaseFloor)
        if (adjustedState != loadedState) {
            runBlocking { storage.save(adjustedState) }
        }

        val timeProvider = AcceleratedTimeProvider(adjustedState, storage, monotonic)
        val cycleRepository = CycleRepository(database, timeProvider)
        val scheduleReadRepository = RoomScheduleReadRepository(database)
        val practiceReadRepository = RoomPracticeReadRepository(database)
        val archiveReadRepository = RoomArchiveReadRepository(database)
        // 07.08.2026 Stage 18 Delete Answer cursor by Me4Hik START - accelerated delete repository
        val answerDeleteRepository = RoomAnswerDeleteRepository(database)
        // 07.08.2026 Stage 18 Delete Answer cursor by Me4Hik END
        val soundPreferenceRepository = DataStoreSoundPreferenceRepository(appContext)
        val openRequestStore = NotificationOpenRequestStore()
        val alarmScheduler = AndroidAlarmScheduler(appContext)
        val notificationPresenter = AndroidPracticeNotificationPresenter(appContext)
        val permissionRepository = NotificationPermissionRepository(
            context = appContext,
            soundEnabledFlow = soundPreferenceRepository.soundEnabled,
        )

        lateinit var runtimeRef: PraktikaRuntime
        val initializer = PraktikaRuntimeInitializer(appContext) { runtimeRef }
        val coordinator = PracticeNotificationCoordinator(
            initializer = initializer,
            cycleRepository = cycleRepository,
            practiceReadRepository = practiceReadRepository,
            permissionRepository = permissionRepository,
            soundEnabledProvider = { soundPreferenceRepository.soundEnabled.first() },
            alarmScheduler = alarmScheduler,
            notificationPresenter = notificationPresenter,
            openRequestStore = openRequestStore,
            timeProvider = timeProvider,
        )
        val syncRequester = NotificationSyncRequester { reason ->
            coordinator.sync(reason)
        }
        val foregroundDriver = AcceleratedCycleDriver(
            cycleRepository = cycleRepository,
            clockController = timeProvider,
            notificationSyncRequester = syncRequester,
        )

        runtimeRef = PraktikaRuntime(
            mode = RuntimeMode.ACCELERATED,
            databaseName = ACCELERATED_DATABASE_NAME,
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
            notificationPermissionRepository = permissionRepository,
            notificationOpenRequestStore = openRequestStore,
            notificationSyncRequester = syncRequester,
            initializer = initializer,
        )
        return runtimeRef
    }
}
// 06.08.2026 Stage 12 Notifications cursor by Me4Hik END
// 04.08.2026 Accelerated Test Mode cursor by Me4Hik END
