package com.me4hik.praktika.runtime

import android.content.Context
import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.me4hik.praktika.data.backup.BackupGoldenFixtures
import com.me4hik.praktika.data.backup.export.BackupExportResult
import com.me4hik.praktika.data.backup.metadata.BackupSnapshotMetadataFactory
import com.me4hik.praktika.data.backup.model.PraktikaBackupPayload
import com.me4hik.praktika.data.backup.model.BackupSlotId
import com.me4hik.praktika.data.backup.storage.BackupSlotWriteResult
import com.me4hik.praktika.data.backup.setup.BackupFolderSetupCoordinator
import com.me4hik.praktika.data.backup.write.AuthorizedBackupService
import com.me4hik.praktika.data.backup.write.AuthorizedBackupStorageResolver
import com.me4hik.praktika.data.backup.write.AuthorizedStorageResolveResult
import com.me4hik.praktika.data.backup.write.BackupAttemptTrigger
import com.me4hik.praktika.data.backup.write.BackupIoSessionGate
import com.me4hik.praktika.data.backup.write.BackupRequestReason
import com.me4hik.praktika.data.backup.write.BackupWriteState
import com.me4hik.praktika.data.backup.write.CountingBackupClock
import com.me4hik.praktika.data.backup.write.FixedBackupAppMetadataProvider
import com.me4hik.praktika.data.backup.write.InMemoryBackupWriteStateRepository
import com.me4hik.praktika.data.backup.write.ProductionBackupCoordinator
import com.me4hik.praktika.data.backup.write.ProductionBackupCoordinatorFactory
import com.me4hik.praktika.data.backup.write.RecordingBackupStorage
import com.me4hik.praktika.data.backup.write.practiceStateWithNextCyclePosition
import com.me4hik.praktika.data.cycle.CycleRepository
import com.me4hik.praktika.data.cycle.SystemTimeProvider
import com.me4hik.praktika.data.delete.RoomAnswerDeleteRepository
import com.me4hik.praktika.data.local.PraktikaDatabase
import com.me4hik.praktika.data.preferences.SoundPreferenceRepository
import com.me4hik.praktika.data.read.RoomArchiveReadRepository
import com.me4hik.praktika.data.read.RoomPracticeReadRepository
import com.me4hik.praktika.data.read.RoomScheduleReadRepository
import com.me4hik.praktika.notification.ExactAlarmCapability
import com.me4hik.praktika.notification.ExactAlarmCapabilityPolicy
import com.me4hik.praktika.notification.NotificationDeliveryCapability
import com.me4hik.praktika.notification.NotificationOpenRequestStore
import com.me4hik.praktika.notification.NotificationPermissionPolicy
import com.me4hik.praktika.notification.NotificationPermissionUiState
import com.me4hik.praktika.notification.NotificationPlan
import com.me4hik.praktika.notification.NotificationShowPlan
import com.me4hik.praktika.notification.NotificationSyncRequester
import com.me4hik.praktika.notification.BoundaryAlarmPlan
import com.me4hik.praktika.notification.PlatformAlarmScheduler
import com.me4hik.praktika.notification.PracticeNotificationCoordinator
import com.me4hik.praktika.notification.PracticeNotificationPresenter
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class Stage62B1RuntimeStartupWiringTest {
    private lateinit var context: Context
    private lateinit var database: PraktikaDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PraktikaRuntimeHolder.resetForTests()
        database = Room.inMemoryDatabaseBuilder(context, PraktikaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        if (::database.isInitialized && database.isOpen) {
            database.close()
        }
        PraktikaRuntimeHolder.resetForTests()
    }

    @Test
    fun runtimeFactory_ownsSingleServiceAndSharedGate() {
        val runtime = RuntimeFactory.create(context)
        assertSame(runtime.backupIoSessionGate, runtime.authorizedBackupService.sharedIoGate)
        val job = checkNotNull(runtime.backupCoroutineScope.coroutineContext[Job])
        assertTrue(job.isActive)
        // SupervisorJob: one failed child must not cancel the process backup scope
        runBlocking {
            val child = runtime.backupCoroutineScope.async {
                error("child boom")
            }
            runCatching { child.await() }
        }
        assertTrue(job.isActive)
        PraktikaDatabase.resetInstanceForTests()
        runtime.database.close()
    }

    @Test
    fun holder_reusesServiceAndGateAcrossGets() {
        val first = PraktikaRuntimeHolder.get(context)
        val second = PraktikaRuntimeHolder.get(context)
        assertSame(first, second)
        assertSame(first.authorizedBackupService, second.authorizedBackupService)
        assertSame(first.backupIoSessionGate, second.backupIoSessionGate)
        assertSame(first.backupIoSessionGate, first.authorizedBackupService.sharedIoGate)
        assertSame(first.backupCoroutineScope, second.backupCoroutineScope)
    }

    @Test
    fun startupCatchup_afterAppStartSync_exportsFinalState() = runTest {
        val golden = BackupGoldenFixtures.goldenEnvelopeWithChecksum().payload
        val initial = practiceStateWithNextCyclePosition(golden, nextCyclePosition = 1)
        val finalState = practiceStateWithNextCyclePosition(golden, nextCyclePosition = 7)
        val exportState = AtomicReference(initial)
        val exported = CopyOnWriteArrayList<PraktikaBackupPayload>()
        val requests = CopyOnWriteArrayList<Pair<BackupRequestReason, BackupAttemptTrigger>>()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val backupScope = CoroutineScope(SupervisorJob() + dispatcher)
        val gate = BackupIoSessionGate()
        val storage = RecordingBackupStorage()
        storage.writeResult = BackupSlotWriteResult.Success(BackupSlotId.A, 1L, "deadbeef")
        val folderA = "content://com.test/tree/folder-a"
        val repo = InMemoryBackupWriteStateRepository(
            BackupWriteState(
                treeUriHint = folderA,
                authorizedTreeUri = folderA,
                lastSuccessfulBackupAtEpochMillis = null,
                lastFailureCategory = null,
                needsReconnect = false,
            ),
        )
        val resolver = object : AuthorizedBackupStorageResolver {
            override suspend fun resolve(authorizedUriString: String): AuthorizedStorageResolveResult {
                return AuthorizedStorageResolveResult.Ready(storage)
            }
        }
        val factory = ProductionBackupCoordinatorFactory {
            ProductionBackupCoordinator(
                storage = it,
                exportAction = {
                    val payload = exportState.get()
                    exported += payload
                    BackupExportResult.Success(payload)
                },
                metadataFactory = BackupSnapshotMetadataFactory(
                    clock = CountingBackupClock(1_800_000_000_000L),
                    appMetadataProvider = FixedBackupAppMetadataProvider(),
                ),
                scope = backupScope,
                ioDispatcher = dispatcher,
            )
        }
        val service = AuthorizedBackupService(
            writeStateRepository = repo,
            storageResolver = resolver,
            coordinatorFactory = factory,
            scope = backupScope,
            ioDispatcher = dispatcher,
            ioGate = gate,
        )
        service.onRequestBackupForTests = { reason, trigger -> requests += reason to trigger }

        val fixture = buildRuntimeFixture(
            backupScope = backupScope,
            gate = gate,
            service = service,
        )
        fixture.initializer.appStartSyncOverrideForTests = {
            exportState.set(finalState)
        }

        assertTrue(fixture.initializer.ensureInitialized())
        assertFalse(service.isInitializationActive())
        assertEquals(1, requests.size)
        assertEquals(BackupRequestReason.STARTUP_CATCHUP, requests[0].first)
        assertEquals(BackupAttemptTrigger.STARTUP, requests[0].second)

        advanceUntilIdle()
        assertTrue(exported.isNotEmpty())
        assertEquals(7, exported.last().practiceState.nextCyclePosition)
        assertEquals(finalState.practiceState.nextCyclePosition, exported.last().practiceState.nextCyclePosition)
    }

    @Test
    fun startupCatchup_doesNotBlockEnsureInitialized() = runTest {
        val block = CompletableDeferred<Unit>()
        val requests = CopyOnWriteArrayList<Pair<BackupRequestReason, BackupAttemptTrigger>>()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val backupScope = CoroutineScope(SupervisorJob() + dispatcher)
        val gate = BackupIoSessionGate()
        val folderA = "content://com.test/tree/folder-a"
        val repo = InMemoryBackupWriteStateRepository(
            BackupWriteState(
                treeUriHint = folderA,
                authorizedTreeUri = folderA,
                lastSuccessfulBackupAtEpochMillis = null,
                lastFailureCategory = null,
                needsReconnect = false,
            ),
        )
        val storage = RecordingBackupStorage()
        storage.writeResult = BackupSlotWriteResult.Success(BackupSlotId.A, 1L, "deadbeef")
        val service = AuthorizedBackupService(
            writeStateRepository = repo,
            storageResolver = object : AuthorizedBackupStorageResolver {
                override suspend fun resolve(authorizedUriString: String): AuthorizedStorageResolveResult {
                    return AuthorizedStorageResolveResult.Ready(storage)
                }
            },
            coordinatorFactory = ProductionBackupCoordinatorFactory {
                ProductionBackupCoordinator(
                    storage = it,
                    exportAction = {
                        block.await()
                        BackupExportResult.Success(BackupGoldenFixtures.goldenEnvelopeWithChecksum().payload)
                    },
                    metadataFactory = BackupSnapshotMetadataFactory(
                        clock = CountingBackupClock(1_800_000_000_000L),
                        appMetadataProvider = FixedBackupAppMetadataProvider(),
                    ),
                    scope = backupScope,
                    ioDispatcher = dispatcher,
                )
            },
            scope = backupScope,
            ioDispatcher = dispatcher,
            ioGate = gate,
        )
        service.onRequestBackupForTests = { reason, trigger -> requests += reason to trigger }

        val fixture = buildRuntimeFixture(backupScope, gate, service)
        fixture.initializer.appStartSyncOverrideForTests = {}

        assertTrue(fixture.initializer.ensureInitialized())
        assertEquals(1, requests.size)
        assertTrue(storage.writeCalls.isEmpty())

        block.complete(Unit)
        advanceUntilIdle()
        assertTrue(storage.writeCalls.isNotEmpty() || requests.size == 1)
    }

    @Test
    fun startupCatchup_exactlyOnce_acrossRepeatedEnsureInitialized() = runTest {
        val requests = CopyOnWriteArrayList<Pair<BackupRequestReason, BackupAttemptTrigger>>()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val backupScope = CoroutineScope(SupervisorJob() + dispatcher)
        val gate = BackupIoSessionGate()
        val service = notConfiguredService(backupScope, dispatcher, gate)
        service.onRequestBackupForTests = { reason, trigger -> requests += reason to trigger }

        val fixture = buildRuntimeFixture(backupScope, gate, service)
        fixture.initializer.appStartSyncOverrideForTests = {}

        assertTrue(fixture.initializer.ensureInitialized())
        assertTrue(fixture.initializer.ensureInitialized())
        fixture.initializer.beginActivityInit()
        assertTrue(fixture.initializer.ensureInitialized())

        assertEquals(1, requests.count { it.first == BackupRequestReason.STARTUP_CATCHUP })
    }

    @Test
    fun failedInit_doesNotConsumeOnceGuard_retrySucceeds() = runTest {
        val requests = CopyOnWriteArrayList<Pair<BackupRequestReason, BackupAttemptTrigger>>()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val backupScope = CoroutineScope(SupervisorJob() + dispatcher)
        val gate = BackupIoSessionGate()
        val service = notConfiguredService(backupScope, dispatcher, gate)
        service.onRequestBackupForTests = { reason, trigger -> requests += reason to trigger }

        val fixture = buildRuntimeFixture(backupScope, gate, service)
        fixture.initializer.appStartSyncOverrideForTests = {
            error("app start sync failed")
        }

        assertFalse(fixture.initializer.ensureInitialized())
        assertFalse(service.isInitializationActive())
        assertFalse(fixture.runtime.startupCatchupOnce.get())
        assertEquals(0, requests.size)

        fixture.initializer.resetForTests()
        fixture.initializer.appStartSyncOverrideForTests = {}
        assertTrue(fixture.initializer.ensureInitialized())
        assertEquals(1, requests.size)
        assertEquals(BackupRequestReason.STARTUP_CATCHUP, requests[0].first)
    }

    @Test
    fun concurrentEnsureInitialized_enqueuesStartupOnce() = runTest {
        val requests = CopyOnWriteArrayList<Pair<BackupRequestReason, BackupAttemptTrigger>>()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val backupScope = CoroutineScope(SupervisorJob() + dispatcher)
        val gate = BackupIoSessionGate()
        val service = notConfiguredService(backupScope, dispatcher, gate)
        service.onRequestBackupForTests = { reason, trigger -> requests += reason to trigger }

        val fixture = buildRuntimeFixture(backupScope, gate, service)
        fixture.initializer.appStartSyncOverrideForTests = {}

        val jobs = List(8) {
            async { fixture.initializer.ensureInitialized() }
        }
        assertTrue(jobs.all { it.await() })
        assertEquals(1, requests.size)
    }

    private fun notConfiguredService(
        backupScope: CoroutineScope,
        dispatcher: kotlinx.coroutines.CoroutineDispatcher,
        gate: BackupIoSessionGate,
    ): AuthorizedBackupService {
        return AuthorizedBackupService(
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
                    error("resolver must not be called for not-configured startup")
                }
            },
            coordinatorFactory = ProductionBackupCoordinatorFactory {
                error("coordinator must not be created")
            },
            scope = backupScope,
            ioDispatcher = dispatcher,
            ioGate = gate,
        )
    }

    private data class RuntimeFixture(
        val runtime: PraktikaRuntime,
        val initializer: PraktikaRuntimeInitializer,
    )

    private fun buildRuntimeFixture(
        backupScope: CoroutineScope,
        gate: BackupIoSessionGate,
        service: AuthorizedBackupService,
    ): RuntimeFixture {
        val timeProvider = SystemTimeProvider()
        val backupMutationRequestSink = com.me4hik.praktika.data.backup.write.AuthorizedBackupMutationRequestSink(service)
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
        val answerDeleteRepository = RoomAnswerDeleteRepository(database, backupMutationRequestSink)
        val soundPreferenceRepository = object : SoundPreferenceRepository {
            override val soundEnabled: Flow<Boolean> = flowOf(true)
            override suspend fun setSoundEnabled(enabled: Boolean) = Unit
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
            ): NotificationPermissionUiState = NotificationPermissionUiState.ENABLED
            override fun toDeliveryCapability(
                state: NotificationPermissionUiState,
            ): NotificationDeliveryCapability = NotificationDeliveryCapability.ENABLED
            override fun createAppNotificationSettingsIntent(): Intent = Intent()
            override fun createChannelSettingsIntent(soundEnabled: Boolean): Intent = Intent()
            override fun shouldRequestRuntimePermission(): Boolean = false
            override fun hasRuntimePermission(): Boolean = true
            override fun areAppNotificationsEnabled(): Boolean = true
            override fun shouldShowRequestPermissionRationale(): Boolean = false
            override fun isSelectedChannelEnabled(soundEnabled: Boolean): Boolean = true
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
            },
            notificationPresenter = object : PracticeNotificationPresenter {
                override fun ensureChannelsCreated() = Unit
                override fun findActivePracticeNotificationOccurrenceId(): Long? = null
                override fun showNotification(plan: NotificationShowPlan) = Unit
                override fun cancelPracticeNotification(occurrenceId: Long) = Unit
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
            databaseName = "stage62b1-test.db",
            database = database,
            timeProvider = timeProvider,
            cycleRepository = cycleRepository,
            foregroundDriver = object : RuntimeForegroundDriver {
                override suspend fun runWhileForeground() = Unit
                override suspend fun onForegroundStopped() = Unit
            },
            scheduleReadRepository = scheduleReadRepository,
            soundPreferenceRepository = soundPreferenceRepository,
            practiceReadRepository = practiceReadRepository,
            archiveReadRepository = archiveReadRepository,
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
        return RuntimeFixture(runtimeRef, initializer)
    }
}
