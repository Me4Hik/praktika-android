// 11.08.2026 DATA VAULT Stage 2 cursor by Me4Hik START - concurrent export stress proof
package com.me4hik.praktika.data.backup.export

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.me4hik.praktika.data.cycle.CycleRepository
import com.me4hik.praktika.data.cycle.FakeTimeProvider
import com.me4hik.praktika.data.local.PraktikaDatabase
import com.me4hik.praktika.data.model.QuestionOccurrenceStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomBackupExporterConcurrencyTest {
    private lateinit var database: PraktikaDatabase
    private lateinit var exporter: RoomBackupExporter
    private lateinit var repository: CycleRepository
    private lateinit var timeProvider: FakeTimeProvider

    @Before
    fun setUp() {
        runBlocking {
            database = RoomBackupExporterTestSupport.createInMemoryDatabase()
            RoomBackupExporterTestSupport.seedBaseData(database)
            exporter = RoomBackupExporter(database)
            timeProvider = FakeTimeProvider(
                RoomBackupExporterTestSupport.epochAt(2026, 8, 4, 11, 0),
                RoomBackupExporterTestSupport.ZONE_KIEV,
            )
            repository = CycleRepository(database, timeProvider, com.me4hik.praktika.data.backup.write.NoOpBackupMutationRequestSink, ApplicationProvider.getApplicationContext())
            repository.startPractice()
        }
    }

    @After
    fun tearDown() {
        if (::database.isInitialized && database.isOpen) {
            database.close()
        }
    }

    @Test
    fun concurrentSaveAnswerAndExport_neverProducesTornSuccessPayload() {
        runBlocking {
            val iterations = 100
            coroutineScope {
                val writer = async(Dispatchers.Default) {
                    repeat(iterations) {
                        val available = database.questionOccurrenceDao().getByCycleAndPosition(1, 1)
                        if (available?.status == QuestionOccurrenceStatus.AVAILABLE) {
                            runCatching {
                                repository.saveAnswer(available.id, "answer-$it")
                            }
                        }
                    }
                }
                val reader = async(Dispatchers.Default) {
                    repeat(iterations) {
                        when (val result = exporter.export()) {
                            is BackupExportResult.Success -> assertSuccessPayloadConsistent(result.payload)
                            is BackupExportResult.DatabaseUnsafe -> Unit
                            is BackupExportResult.ReadFailure -> error("Unexpected ReadFailure: ${result.exceptionClass}")
                        }
                    }
                }
                awaitAll(writer, reader)
            }
        }
    }

    @Test
    fun concurrentReconcileAndExport_neverProducesTornSuccessPayload() {
        runBlocking {
            val iterations = 100
            coroutineScope {
                val writer = async(Dispatchers.Default) {
                    repeat(iterations) {
                        timeProvider.setEpochMillis(
                            RoomBackupExporterTestSupport.epochAt(2026, 8, 4, 11, 0) + (it * 60_000L),
                        )
                        runCatching { repository.reconcile() }
                    }
                }
                val reader = async(Dispatchers.Default) {
                    repeat(iterations) {
                        when (val result = exporter.export()) {
                            is BackupExportResult.Success -> assertSuccessPayloadConsistent(result.payload)
                            is BackupExportResult.DatabaseUnsafe -> Unit
                            is BackupExportResult.ReadFailure -> error("Unexpected ReadFailure: ${result.exceptionClass}")
                        }
                    }
                }
                awaitAll(writer, reader)
            }
        }
    }

    private suspend fun assertSuccessPayloadConsistent(
        payload: com.me4hik.praktika.data.backup.model.PraktikaBackupPayload,
    ) = withContext(Dispatchers.Default) {
        assertNull(BackupExportDomainValidator.validate(payload))

        val occurrenceByKey = payload.occurrences.associateBy { it.cycleNumber to it.cyclePosition }
        payload.answers.forEach { answer ->
            val occurrence = occurrenceByKey[answer.cycleNumber to answer.cyclePosition]
            assertTrue(occurrence != null)
            assertTrue(occurrence!!.status == QuestionOccurrenceStatus.ANSWERED.name)
        }

        val incompleteCount = payload.occurrences.count {
            it.status == QuestionOccurrenceStatus.SCHEDULED.name ||
                it.status == QuestionOccurrenceStatus.AVAILABLE.name
        }
        assertTrue(incompleteCount <= 1)

        val stableKeys = payload.occurrences.map { it.cycleNumber to it.cyclePosition }
        assertTrue(stableKeys.size == stableKeys.toSet().size)
    }
}
// 11.08.2026 DATA VAULT Stage 2 cursor by Me4Hik END
