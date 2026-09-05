// 03.09.2026 Case2 init internal observability cursor by Me4Hik START - runtime_init_failed host proofs
package com.me4hik.praktika.runtime

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import com.me4hik.praktika.data.backup.metadata.BackupSnapshotMetadataFactory
import com.me4hik.praktika.data.backup.setup.BackupFolderSetupCoordinator
import com.me4hik.praktika.data.backup.write.AuthorizedBackupMutationRequestSink
import com.me4hik.praktika.data.backup.write.AuthorizedBackupService
import com.me4hik.praktika.data.backup.write.AuthorizedBackupStorageResolver
import com.me4hik.praktika.data.backup.write.AuthorizedStorageResolveResult
import com.me4hik.praktika.data.backup.write.BackupIoSessionGate
import com.me4hik.praktika.data.backup.write.BackupWriteState
import com.me4hik.praktika.data.backup.write.CountingBackupClock
import com.me4hik.praktika.data.backup.write.FixedBackupAppMetadataProvider
import com.me4hik.praktika.data.backup.write.InMemoryBackupWriteStateRepository
import com.me4hik.praktika.data.backup.write.ProductionBackupCoordinatorFactory
import com.me4hik.praktika.data.cycle.CycleRepository
import com.me4hik.praktika.data.cycle.SystemTimeProvider
import com.me4hik.praktika.data.delete.RoomAnswerDeleteRepository
import com.me4hik.praktika.data.local.PraktikaDatabase
import com.me4hik.praktika.data.preferences.DeferDurationOptions
import com.me4hik.praktika.data.preferences.DeferDurationPreferenceRepository
import com.me4hik.praktika.data.preferences.SoundPreferenceRepository
import com.me4hik.praktika.data.read.RoomAnalyticsReadRepository
import com.me4hik.praktika.data.read.RoomArchiveReadRepository
import com.me4hik.praktika.data.read.RoomPracticeReadRepository
import com.me4hik.praktika.data.read.RoomScheduleReadRepository
import com.me4hik.praktika.diagnostics.DiagnosticsRecorder
import com.me4hik.praktika.notification.BoundaryAlarmPlan
import com.me4hik.praktika.notification.ExactAlarmCapability
import com.me4hik.praktika.notification.ExactAlarmCapabilityPolicy
import com.me4hik.praktika.notification.NotificationDeliveryCapability
import com.me4hik.praktika.notification.NotificationOpenRequestStore
import com.me4hik.praktika.notification.NotificationPermissionPolicy
import com.me4hik.praktika.notification.NotificationPermissionUiState
import com.me4hik.praktika.notification.NotificationPlan
import com.me4hik.praktika.notification.NotificationShowPlan
import com.me4hik.praktika.notification.NotificationSyncRequester
import com.me4hik.praktika.notification.PlatformAlarmScheduler
import com.me4hik.praktika.notification.PracticeNotificationCoordinator
import com.me4hik.praktika.notification.PracticeNotificationKind
import com.me4hik.praktika.notification.PracticeNotificationPresenter
import com.me4hik.praktika.notification.ResidualPlannedRecoveryResult
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Host proofs for runtime_init_failed observability.
 *
 * Must NOT call [PraktikaRuntimeHolder.get] / flavor [RuntimeFactory]: accelerated create eagerly
 * opens file Room+WAL and breaks Robolectric sqlite4java. Use Stage62-style in-memory runtime.
 *
 * Also force a plain [Application]: [PraktikaApplication] onCreate initializes the real Holder
 * and would open the flavor file DB before the test seams run.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [28])
class PraktikaRuntimeInitializerInitFailureObservabilityHostTest {
    private lateinit var context: Context
    private lateinit var database: PraktikaDatabase
    private lateinit var eventsFile: File
    private lateinit var backupScope: CoroutineScope

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        DiagnosticsRecorder.resetForTests()
        PraktikaRuntimeHolder.resetForTests()
        eventsFile = File(context.filesDir, "diagnostics/case2-runtime-init-failed-events.jsonl")
        eventsFile.parentFile?.mkdirs()
        if (eventsFile.exists()) {
            eventsFile.delete()
        }
        DiagnosticsRecorder.installForTests(eventsFile)
        database = Room.inMemoryDatabaseBuilder(context, PraktikaDatabase::class.java)
            .allowMainThreadQueries()
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .build()
        backupScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        if (::database.isInitialized && database.isOpen) {
            database.close()
        }
        PraktikaRuntimeHolder.resetForTests()
        DiagnosticsRecorder.resetForTests()
        if (::eventsFile.isInitialized && eventsFile.exists()) {
            eventsFile.delete()
        }
    }

    @Test
    fun seedFailure_recordsRuntimeInitFailed_stageSeed_stickyFalse() = runBlocking {
        val runtime = buildIsolatedRuntime()
        val seedCalls = AtomicInteger(0)
        runtime.initializer.appStartSyncOverrideForTests = {}
        runtime.initializer.seedFromAssetsOverrideForTests = {
            seedCalls.incrementAndGet()
            error("forced_seed_failure")
        }

        assertFalse(runtime.initializer.ensureInitialized())
        assertEquals(1, seedCalls.get())

        val failed = requireRuntimeInitFailed()
        assertEquals("SEED", failed.metadata["failure_stage"])
        assertEquals(IllegalStateException::class.java.name, failed.metadata["exception_class"])
        assertTrue(failed.metadata["wall_clock_ms"].orEmpty().isNotBlank())
        assertTrue(failed.metadata["elapsed_realtime_ms"].orEmpty().isNotBlank())
        assertFalse(failed.metadata.containsKey("exception_message"))

        assertFalse(runtime.initializer.ensureInitialized())
        assertEquals(1, seedCalls.get())
    }

    @Test
    fun reconcileFailure_recordsRuntimeInitFailed_stageReconcile_stickyFalse() = runBlocking {
        val runtime = buildIsolatedRuntime()
        val reconcileCalls = AtomicInteger(0)
        runtime.initializer.appStartSyncOverrideForTests = {}
        runtime.initializer.seedFromAssetsOverrideForTests = {}
        runtime.initializer.syncEnvironmentAndReconcileOverrideForTests = {
            reconcileCalls.incrementAndGet()
            throw IllegalStateException("forced_reconcile_failure")
        }

        assertFalse(runtime.initializer.ensureInitialized())
        assertEquals(1, reconcileCalls.get())

        val failed = requireRuntimeInitFailed()
        assertEquals("RECONCILE", failed.metadata["failure_stage"])
        assertEquals(IllegalStateException::class.java.name, failed.metadata["exception_class"])

        assertFalse(runtime.initializer.ensureInitialized())
        assertEquals(1, reconcileCalls.get())
    }

    @Test
    fun cancellationFailure_recordsRuntimeInitFailed_stageCancelled_stickyFalse() = runBlocking {
        val runtime = buildIsolatedRuntime()
        runtime.initializer.appStartSyncOverrideForTests = {}
        runtime.initializer.seedFromAssetsOverrideForTests = {
            throw CancellationException("forced_init_cancellation")
        }

        assertFalse(runtime.initializer.ensureInitialized())

        val failed = requireRuntimeInitFailed()
        assertEquals("CANCELLED", failed.metadata["failure_stage"])
        assertEquals(CancellationException::class.java.name, failed.metadata["exception_class"])

        assertFalse(runtime.initializer.ensureInitialized())
    }

    @Test
    fun successPath_emitsNoRuntimeInitFailed() = runBlocking {
        val runtime = buildIsolatedRuntime()
        // Isolate init stages with no-op seams — assert initializer success/event semantics only.
        runtime.initializer.seedFromAssetsOverrideForTests = {}
        runtime.initializer.syncEnvironmentAndReconcileOverrideForTests = {}
        runtime.initializer.appStartSyncOverrideForTests = {}

        assertTrue(runtime.initializer.ensureInitialized())
        assertTrue(runtime.initializer.ensureInitialized())

        assertFalse(
            DiagnosticsRecorder.get().getBufferedEvents().any { it.name == "runtime_init_failed" },
        )
    }

    @Test
    fun freshReset_afterStickyFalse_allowsSuccessfulInit() = runBlocking {
        val runtime = buildIsolatedRuntime()
        runtime.initializer.appStartSyncOverrideForTests = {}
        runtime.initializer.seedFromAssetsOverrideForTests = {
            error("forced_seed_failure")
        }
        assertFalse(runtime.initializer.ensureInitialized())
        requireRuntimeInitFailed()

        runtime.initializer.resetForTests()
        runtime.initializer.seedFromAssetsOverrideForTests = {}
        runtime.initializer.syncEnvironmentAndReconcileOverrideForTests = {}
        runtime.initializer.appStartSyncOverrideForTests = {}
        assertTrue(runtime.initializer.ensureInitialized())
        assertEquals(
            1,
            DiagnosticsRecorder.get().getBufferedEvents().count { it.name == "runtime_init_failed" },
        )
    }

    private fun requireRuntimeInitFailed() =
        DiagnosticsRecorder.get().getBufferedEvents().last { it.name == "runtime_init_failed" }

    private fun buildIsolatedRuntime(): PraktikaRuntime {
        val gate = BackupIoSessionGate()
        val service = AuthorizedBackupService(
            writeStateRepository = InMemoryBackupWriteStateRepository(
                BackupWriteState(
                    treeUriHint = "content://hint",
                    authorizedTreeUri = null,
                    lastSuccessfulBackupAtEpochMillis = null,
                    lastFailureCategory = null,
                    needsReconnect = false,
                ),
            ),
            storageResolver = object : AuthorizedBackupStorageResolver {
                override suspend fun resolve(authorizedUriString: String): AuthorizedStorageResolveResult {
                    error("resolver must not be called for init-failure host proofs")
                }
            },
            coordinatorFactory = ProductionBackupCoordinatorFactory {
                error("coordinator must not be created")
            },
            scope = backupScope,
            ioDispatcher = Dispatchers.Unconfined,
            ioGate = gate,
        )
        val timeProvider = SystemTimeProvider()
        val backupMutationRequestSink = AuthorizedBackupMutationRequestSink(service)
        val setupWriteState = InMemoryBackupWriteStateRepository()
        val backupFolderSetupCoordinator = BackupFolderSetupCoordinator(
            writeStateRepository = setupWriteState,
            ioGate = gate,
            authorizedBackupService = service,
            candidateAccess = object : com.me4hik.praktika.data.backup.setup.SetupCandidateAccess {
                override suspend fun connectTransient(
                    uriString: String,
                    grantFlags: Int,
                ) = com.me4hik.praktika.data.backup.setup.SetupCandidateConnectResult.Success

                override fun createStorage(uriString: String) = null
                override fun releasePersistedGrant(uriString: String, grantFlags: Int) = Unit
                override fun hasPersistedGrant(uriString: String) = false
            },
            exportAction = {
                com.me4hik.praktika.data.backup.export.BackupExportResult.ReadFailure("unused")
            },
            metadataFactory = BackupSnapshotMetadataFactory(
                clock = CountingBackupClock(),
                appMetadataProvider = FixedBackupAppMetadataProvider(),
            ),
        )
        val backupSettingsFacade = com.me4hik.praktika.data.backup.settings.BackupSettingsFacade(
            writeStateRepository = setupWriteState,
            authorizedBackupService = service,
            setupCoordinator = backupFolderSetupCoordinator,
            ioGate = gate,
            reconnectAccess = object : com.me4hik.praktika.data.backup.settings.BackupReconnectAccess {
                override suspend fun acquireAndValidate(
                    uriString: String,
                    grantFlags: Int,
                ) = com.me4hik.praktika.data.backup.settings.BackupReconnectAcquireResult.PermissionAcquireFailed

                override fun releasePersistedGrant(uriString: String, grantFlags: Int) = Unit
                override fun hasPersistedGrant(uriString: String) = false
            },
            backupScope = backupScope,
        )
        val cycleRepository = CycleRepository(database, timeProvider, backupMutationRequestSink)
        val scheduleReadRepository = RoomScheduleReadRepository(database)
        val practiceReadRepository = RoomPracticeReadRepository(database)
        val archiveReadRepository = RoomArchiveReadRepository(database)
        val analyticsReadRepository = RoomAnalyticsReadRepository(database)
        val answerDeleteRepository = RoomAnswerDeleteRepository(database, backupMutationRequestSink)
        val soundPreferenceRepository = object : SoundPreferenceRepository {
            override val soundEnabled: Flow<Boolean> = flowOf(true)
            override val selectedSoundId: Flow<String> =
                flowOf(com.me4hik.praktika.sound.SoundAssetIds.SYSTEM_DEFAULT)
            override val hiddenBuiltinIds: Flow<Set<String>> = flowOf(emptySet())
            override suspend fun setSoundEnabled(enabled: Boolean) = Unit
            override suspend fun selectSound(id: String) = Unit
            override suspend fun hideBuiltin(id: String) = Unit
            override suspend fun restoreBuiltin(id: String) = Unit
            override suspend fun restoreAllHidden() = Unit
        }
        val deferDurationPreferenceRepository = object : DeferDurationPreferenceRepository {
            override val deferDurationMinutes: Flow<Int> = flowOf(DeferDurationOptions.DEFAULT_MINUTES)
            override suspend fun setDeferDurationMinutes(minutes: Int) = Unit
        }
        val openRequestStore = NotificationOpenRequestStore()
        lateinit var runtimeRef: PraktikaRuntime
        val initializer = PraktikaRuntimeInitializer(context) { runtimeRef }
        val permissionPolicy = object : NotificationPermissionPolicy {
            private val revision = MutableStateFlow(0L)
            override val permissionStateRevision: StateFlow<Long> = revision.asStateFlow()
            override fun notifyPermissionStateChanged(source: String) {
                revision.value += 1
            }
            override val permissionRequested: Flow<Boolean> = flowOf(true)
            override suspend fun markPermissionRequested() = Unit
            override fun evaluateUiState(
                permissionRequested: Boolean,
                soundEnabled: Boolean,
                selectedSoundId: String,
            ): NotificationPermissionUiState = NotificationPermissionUiState.ENABLED
            override fun toDeliveryCapability(
                state: NotificationPermissionUiState,
            ): NotificationDeliveryCapability = NotificationDeliveryCapability.ENABLED
            override fun createAppNotificationSettingsIntent(): Intent = Intent()
            override fun createChannelSettingsIntent(
                soundEnabled: Boolean,
                selectedSoundId: String,
            ): Intent = Intent()
            override fun shouldRequestRuntimePermission(): Boolean = false
            override fun hasRuntimePermission(): Boolean = true
            override fun areAppNotificationsEnabled(): Boolean = true
            override fun shouldShowRequestPermissionRationale(): Boolean = false
            override fun isSelectedChannelEnabled(
                soundEnabled: Boolean,
                selectedSoundId: String,
            ): Boolean = true
        }
        val coordinator = PracticeNotificationCoordinator(
            initializer = initializer,
            cycleRepository = cycleRepository,
            practiceReadRepository = practiceReadRepository,
            permissionRepository = permissionPolicy,
            soundEnabledProvider = { true },
            alarmScheduler = object : PlatformAlarmScheduler {
                override fun scheduleAlarms(
                    plan: NotificationPlan,
                    previousAlarms: List<BoundaryAlarmPlan>,
                ) = Unit
                override fun cancelAlarms(alarms: List<BoundaryAlarmPlan>) = Unit
                override fun scheduleResidualPlannedRecovery(
                    recoveryPlan: BoundaryAlarmPlan,
                    useAlarmClock: Boolean,
                ) = ResidualPlannedRecoveryResult.ALARM_CLOCK_SCHEDULED
            },
            notificationPresenter = object : PracticeNotificationPresenter {
                override fun ensureChannelsCreated() = Unit
                override fun findActivePracticeNotificationOccurrenceId(): Long? = null
                override fun findActivePracticeNotificationKind(): PracticeNotificationKind? = null
                override fun showNotification(plan: NotificationShowPlan) = Unit
                override fun cancelCurrentPracticeNotification() = Unit
                override fun cancelLegacyPracticeNotifications(
                    currentOccurrenceId: Long?,
                    syncReason: String?,
                ) = Unit
                override fun cancelAllPracticeNotifications() = Unit
            },
            openRequestStore = openRequestStore,
            timeProvider = timeProvider,
        )
        val exactAlarm = object : ExactAlarmCapabilityPolicy {
            private val revision = MutableStateFlow(0L)
            override val capabilityStateRevision: StateFlow<Long> = revision.asStateFlow()
            override fun currentCapability(): ExactAlarmCapability = ExactAlarmCapability.AVAILABLE
            override fun notifyCapabilityChanged(source: String): Boolean = false
            override fun seedInitialCapability(source: String) = Unit
            override fun createRequestExactAlarmIntent(): Intent = Intent()
            override fun recordSettingsCta(source: String) = Unit
        }
        runtimeRef = PraktikaRuntime(
            mode = RuntimeMode.PRODUCTION,
            databaseName = "init-failure-observability-host.db",
            database = database,
            timeProvider = timeProvider,
            cycleRepository = cycleRepository,
            foregroundDriver = object : RuntimeForegroundDriver {
                override suspend fun runWhileForeground() = Unit
                override suspend fun onForegroundStopped() = Unit
            },
            scheduleReadRepository = scheduleReadRepository,
            soundPreferenceRepository = soundPreferenceRepository,
            deferDurationPreferenceRepository = deferDurationPreferenceRepository,
            practiceReadRepository = practiceReadRepository,
            archiveReadRepository = archiveReadRepository,
            analyticsReadRepository = analyticsReadRepository,
            answerDeleteRepository = answerDeleteRepository,
            notificationCoordinator = coordinator,
            notificationPermissionRepository = permissionPolicy,
            exactAlarmCapabilityRepository = exactAlarm,
            notificationOpenRequestStore = openRequestStore,
            notificationSyncRequester = NotificationSyncRequester { },
            initializer = initializer,
            backupCoroutineScope = backupScope,
            backupIoSessionGate = gate,
            authorizedBackupService = service,
            backupMutationRequestSink = backupMutationRequestSink,
            backupFolderSetupCoordinator = backupFolderSetupCoordinator,
            backupSettingsFacade = backupSettingsFacade,
        )
        return runtimeRef
    }
}
// 03.09.2026 Case2 init internal observability cursor by Me4Hik END
