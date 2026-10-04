// 06.08.2026 Stage 12 Notifications cursor by Me4Hik START - isolated test runtime builder
package com.me4hik.praktika.runtime

import android.content.Context
import com.me4hik.praktika.data.cycle.CycleRepository
import com.me4hik.praktika.data.cycle.TimeProvider
import com.me4hik.praktika.data.local.PraktikaDatabase
import com.me4hik.praktika.data.preferences.AppLanguage
import com.me4hik.praktika.data.preferences.DeferDurationOptions
import com.me4hik.praktika.data.preferences.DeferDurationPreferenceRepository
import com.me4hik.praktika.data.preferences.LanguagePreferenceRepository
import com.me4hik.praktika.data.preferences.QuestionWordingMode
import com.me4hik.praktika.data.preferences.QuestionWordingModeSource
import com.me4hik.praktika.data.preferences.QuestionWordingPreferenceRepository
import com.me4hik.praktika.data.preferences.SoundPreferenceRepository
import com.me4hik.praktika.data.delete.RoomAnswerDeleteRepository
import com.me4hik.praktika.data.read.RoomAnalyticsReadRepository
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
import com.me4hik.praktika.measurement.MeasurementRuntimeFactory
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
        questionWordingPreferenceRepository: QuestionWordingPreferenceRepository =
            FakeQuestionWordingPreferenceRepository(),
        languagePreferenceRepository: LanguagePreferenceRepository =
            FakeLanguagePreferenceRepository(),
        alarmScheduler: PlatformAlarmScheduler = NoOpPlatformAlarmScheduler(),
        notificationPresenter: PracticeNotificationPresenter = NoOpPracticeNotificationPresenter(),
        permissionRepository: NotificationPermissionPolicy? = null,
    ): PraktikaRuntime {
        val appContext = context.applicationContext
        val measurement = MeasurementRuntimeFactory.createPhase1()
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
            analyticsTracker = measurement.tracker,
        )
        val cycleRepository = CycleRepository(
            database,
            timeProvider,
            backupMutationRequestSink,
            appContext,
            wordingModeSource = QuestionWordingModeSource {
                questionWordingPreferenceRepository.wordingMode.first()
            },
            analyticsTracker = measurement.tracker,
            analyticsFlavor = mode.name.lowercase(),
        )
        val scheduleReadRepository = RoomScheduleReadRepository(database)
        val practiceReadRepository = RoomPracticeReadRepository(database)
        val archiveReadRepository = RoomArchiveReadRepository(database)
        val analyticsReadRepository = RoomAnalyticsReadRepository(database)
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
            analyticsTracker = measurement.tracker,
        )
        val syncRequester = NotificationSyncRequester { reason ->
            coordinator.sync(reason)
        }
        runtimeRef = PraktikaRuntime(
            mode = mode,
            databaseName = databaseName,
            database = database,
            timeProvider = timeProvider,
            analyticsTracker = measurement.tracker,
            debugAnalyticsProvider = measurement.debugProvider,
            cycleRepository = cycleRepository,
            foregroundDriver = foregroundDriver,
            scheduleReadRepository = scheduleReadRepository,
            soundPreferenceRepository = soundPreferenceRepository,
            deferDurationPreferenceRepository = deferDurationPreferenceRepository,
            languagePreferenceRepository = languagePreferenceRepository,
            questionWordingPreferenceRepository = questionWordingPreferenceRepository,
            practiceReadRepository = practiceReadRepository,
            archiveReadRepository = archiveReadRepository,
            analyticsReadRepository = analyticsReadRepository,
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

class FakeQuestionWordingPreferenceRepository(
    initialMode: QuestionWordingMode = QuestionWordingMode.DEFAULT,
) : QuestionWordingPreferenceRepository {
    private val state = MutableStateFlow(initialMode)
    override val wordingMode: Flow<QuestionWordingMode> = state
    override suspend fun setWordingMode(mode: QuestionWordingMode) {
        state.value = mode
    }
}

class FakeLanguagePreferenceRepository(
    initialLanguage: AppLanguage = AppLanguage.DEFAULT,
    initiallySelected: Boolean = true,
) : LanguagePreferenceRepository {
    private val languageState = MutableStateFlow(initialLanguage)
    private val selectedState = MutableStateFlow(initiallySelected)
    override val language: Flow<AppLanguage> = languageState
    override val languageSelected: Flow<Boolean> = selectedState
    override suspend fun setLanguage(language: AppLanguage) {
        languageState.value = language
    }
    override suspend fun markLanguageSelected() {
        selectedState.value = true
    }
    override suspend fun ensureExistingUserDefault(isPracticeStarted: Boolean) {
        if (!selectedState.value && isPracticeStarted) {
            languageState.value = AppLanguage.DEFAULT
            selectedState.value = true
        }
    }
}
// 06.08.2026 Stage 12 Notifications cursor by Me4Hik END
