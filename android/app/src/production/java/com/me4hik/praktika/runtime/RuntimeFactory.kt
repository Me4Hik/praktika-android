// 04.08.2026 Accelerated Test Mode cursor by Me4Hik START - production runtime factory
// 06.08.2026 Stage 12 Notifications cursor by Me4Hik START - notification stack wiring
package com.me4hik.praktika.runtime

import android.content.Context
import com.me4hik.praktika.data.cycle.CycleRepository
import com.me4hik.praktika.data.cycle.SystemTimeProvider
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

object RuntimeFactory {
    private const val PRODUCTION_DATABASE_NAME = "praktika.db"

    fun create(context: Context): PraktikaRuntime {
        val appContext = context.applicationContext
        val database = PraktikaDatabase.getInstance(appContext, PRODUCTION_DATABASE_NAME)
        val timeProvider = SystemTimeProvider()
        val cycleRepository = CycleRepository(database, timeProvider)
        val scheduleReadRepository = RoomScheduleReadRepository(database)
        val practiceReadRepository = RoomPracticeReadRepository(database)
        val archiveReadRepository = RoomArchiveReadRepository(database)
        // 07.08.2026 Stage 18 Delete Answer cursor by Me4Hik START - production delete repository
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
