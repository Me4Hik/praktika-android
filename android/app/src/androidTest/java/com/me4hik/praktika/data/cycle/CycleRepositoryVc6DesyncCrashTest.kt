// 10.08.2026 Post-release fixes cursor by Me4Hik START - vc6 desync historical crash proof
package com.me4hik.praktika.data.cycle

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.me4hik.praktika.data.local.PraktikaDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CycleRepositoryVc6DesyncCrashTest {
    private lateinit var database: PraktikaDatabase
    private lateinit var timeProvider: FakeTimeProvider
    private lateinit var repository: CycleRepository

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, PraktikaDatabase::class.java).build()
        Vc6CursorDesyncFixtureSupport.seedBaseQuestionsAndSlots(database)
        Vc6CursorDesyncFixtureSupport.installProductionVc6DesyncFingerprint(database)
        timeProvider = FakeTimeProvider(
            Vc6CursorDesyncFixtureSupport.reconcileNowEpochMillis(),
            Vc6CursorDesyncFixtureSupport.ZONE_KIEV,
        )
        repository = CycleRepository(database, timeProvider, com.me4hik.praktika.data.backup.write.NoOpBackupMutationRequestSink)
    }

    @After
    fun tearDown() {
        if (::database.isInitialized && database.isOpen) {
            database.close()
        }
    }

    @Test
    fun productionVc6Fingerprint_hasStaleCursorShape() = runBlocking {
        val stateBefore = database.practiceStateDao().get()!!
        assertEquals(6, stateBefore.nextCyclePosition)
        assertNotNull(
            database.questionOccurrenceDao().getByCycleAndPosition(
                Vc6CursorDesyncFixtureSupport.DESYNC_CYCLE,
                Vc6CursorDesyncFixtureSupport.DESYNC_CURSOR_POSITION,
            ),
        )
    }

    @Test
    fun productionVc6Fingerprint_reconcileRecoversInsteadOfDuplicateCrash() = runBlocking {
        val result = runCatching {
            repository.syncEnvironmentAndReconcile()
        }
        assertFalse(
            result.exceptionOrNull()?.message?.contains(
                "Occurrence already exists for cycle=1 position=6",
            ) ?: false,
        )
        assertTrue(result.isSuccess)
        assertTrue(database.practiceStateDao().get()!!.nextCyclePosition >= 7)
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
