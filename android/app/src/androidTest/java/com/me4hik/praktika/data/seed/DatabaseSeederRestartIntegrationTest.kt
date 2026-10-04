// 05.08.2026 Idempotent Seed Fix cursor by Me4Hik START - restart integration test
package com.me4hik.praktika.data.seed

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.me4hik.praktika.data.cycle.CycleRepository
import com.me4hik.praktika.data.cycle.CycleResult
import com.me4hik.praktika.data.cycle.FakeTimeProvider
import com.me4hik.praktika.data.local.PraktikaDatabase
import com.me4hik.praktika.data.local.entity.AnswerEntity
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseSeederRestartIntegrationTest {
    private var databaseFile: File? = null
    private var database: PraktikaDatabase? = null

    @After
    fun tearDown() {
        database?.let {
            if (it.isOpen) {
                it.close()
            }
        }
        database = null
        databaseFile?.let { file ->
            if (file.exists()) {
                file.delete()
            }
        }
        databaseFile = null
    }

    @Test
    fun seedStartPracticeReseedPreservesUserDataAndReconcileSucceeds() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        databaseFile = File(context.cacheDir, "seeder_restart_integration.db")
        databaseFile!!.delete()

        val db = openDatabase(context, databaseFile!!)
        database = db
        val assetJson = context.assets.open("questions.json").bufferedReader().use { it.readText() }
        val seeder = DatabaseSeeder()

        assertEquals(SeedResult.Inserted, seeder.seedFromJson(assetJson, db, TEST_ZONE_ID))

        val repository = CycleRepository(
            db,
            FakeTimeProvider(epochAt(8, 0, 0), TEST_ZONE_ID),
            com.me4hik.praktika.data.backup.write.NoOpBackupMutationRequestSink,
            context,
        )
        repository.startPractice()

        assertEquals(1, db.questionOccurrenceDao().count())
        val stateBefore = db.practiceStateDao().get()!!
        assertTrue(stateBefore.isPracticeStarted)
        val occurrenceBefore = db.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!

        assertEquals(SeedResult.AlreadyInitialized, seeder.seedFromJson(assetJson, db, TEST_ZONE_ID))
        assertEquals(1, db.questionOccurrenceDao().count())
        assertEquals(stateBefore, db.practiceStateDao().get())
        assertEquals(occurrenceBefore, db.questionOccurrenceDao().getByCycleAndPosition(1, 1))

        val reconcileResult = repository.reconcile()
        assertTrue(
            reconcileResult == CycleResult.ReconcileNoChanges ||
                reconcileResult == CycleResult.ReconcileChanged ||
                reconcileResult == CycleResult.ReconcilePaused,
        )
    }

    @Test
    fun seedStartPracticeAnswerReseedPreservesAnswer() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        databaseFile = File(context.cacheDir, "seeder_restart_with_answer.db")
        databaseFile!!.delete()

        val db = openDatabase(context, databaseFile!!)
        database = db
        val assetJson = context.assets.open("questions.json").bufferedReader().use { it.readText() }
        val seeder = DatabaseSeeder()

        seeder.seedFromJson(assetJson, db, TEST_ZONE_ID)
        val repository = CycleRepository(
            db,
            FakeTimeProvider(epochAt(11, 0, 0), TEST_ZONE_ID),
            com.me4hik.praktika.data.backup.write.NoOpBackupMutationRequestSink,
            context,
        )
        repository.startPractice()

        val occurrenceId = db.questionOccurrenceDao().getByCycleAndPosition(1, 1)!!.id
        val answerText = "Integration answer"
        val answerCreatedAt = epochAt(11, 30, 0)
        db.answerDao().insert(
            AnswerEntity(
                occurrenceId = occurrenceId,
                text = answerText,
                createdAtEpochMillis = answerCreatedAt,
            ),
        )

        assertEquals(SeedResult.AlreadyInitialized, seeder.seedFromJson(assetJson, db, TEST_ZONE_ID))
        val storedAnswer = db.answerDao().getByOccurrenceId(occurrenceId)!!
        assertEquals(occurrenceId, storedAnswer.occurrenceId)
        assertEquals(answerText, storedAnswer.text)
        assertEquals(answerCreatedAt, storedAnswer.createdAtEpochMillis)
        assertEquals(1, db.answerDao().count())
    }

    private fun openDatabase(
        context: android.content.Context,
        file: File,
    ): PraktikaDatabase {
        return Room.databaseBuilder(
            context,
            PraktikaDatabase::class.java,
            file.absolutePath,
        ).build()
    }

    private fun epochAt(hour: Int, minute: Int, second: Int): Long {
        return ZonedDateTime.of(2026, 8, 4, hour, minute, second, 0, ZoneId.of(TEST_ZONE_ID))
            .toInstant()
            .toEpochMilli()
    }

    private companion object {
        const val TEST_ZONE_ID = "Europe/Kiev"
    }
}
// 05.08.2026 Idempotent Seed Fix cursor by Me4Hik END
