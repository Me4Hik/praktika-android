package com.me4hik.praktika.data.local.migration

import android.content.Context
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Host migration proof for 3→4 without device: preserves occurrence/answer rows,
 * creates empty defer_events.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class Migration3To4HostTest {
    @Test
    fun migrate3To4_preservesRowsAndCreatesEmptyDeferEvents() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = File(context.filesDir, "host-migration-3-4.db")
        if (dbFile.exists()) {
            assertTrue(dbFile.delete())
        }

        val openHelper = FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbFile.absolutePath)
                .callback(
                    object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(3) {
                        override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                            createV3Schema(db)
                        }

                        override fun onUpgrade(
                            db: androidx.sqlite.db.SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int,
                        ) = Unit
                    },
                )
                .build(),
        )

        openHelper.writableDatabase.use { db ->
            db.execSQL(
                "INSERT INTO questions (id, cyclePosition, text, isActive) VALUES (1, 1, 'Keep me', 1)",
            )
            db.execSQL("INSERT INTO schedule_slots (slotIndex, timeOfDayMinutes) VALUES (1, 660)")
            db.execSQL(
                """
                INSERT INTO question_occurrences (
                    id, questionId, questionTextSnapshot, cycleNumber, cyclePosition,
                    scheduleSlotIndex, plannedAtEpochMillis, availableUntilEpochMillis,
                    openedAtEpochMillis, completedAtEpochMillis, deferredUntilEpochMillis,
                    status, zoneId
                ) VALUES (1, 1, 'Keep me', 1, 1, 1, 1000, 2000, 1500, NULL, NULL, 'AVAILABLE', 'UTC')
                """.trimIndent(),
            )
            db.execSQL(
                "INSERT INTO answers (id, occurrenceId, text, createdAtEpochMillis) VALUES (1, 1, 'existing answer', 1600)",
            )
            assertEquals(3, db.version)

            MIGRATION_3_4.migrate(db)
            db.version = 4

            assertEquals(4, db.version)
            db.query("SELECT questionTextSnapshot, status FROM question_occurrences WHERE id = 1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Keep me", cursor.getString(0))
                assertEquals("AVAILABLE", cursor.getString(1))
            }
            db.query("SELECT text FROM answers WHERE id = 1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("existing answer", cursor.getString(0))
            }
            db.query("SELECT COUNT(*) FROM defer_events").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
        }
        openHelper.close()
    }

    private fun createV3Schema(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `questions` (`id` INTEGER NOT NULL, `cyclePosition` INTEGER NOT NULL, `text` TEXT NOT NULL, `isActive` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_questions_cyclePosition` ON `questions` (`cyclePosition`)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `schedule_slots` (`slotIndex` INTEGER NOT NULL, `timeOfDayMinutes` INTEGER NOT NULL, PRIMARY KEY(`slotIndex`))",
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `question_occurrences` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `questionId` INTEGER NOT NULL,
                `questionTextSnapshot` TEXT NOT NULL DEFAULT '',
                `cycleNumber` INTEGER NOT NULL,
                `cyclePosition` INTEGER NOT NULL,
                `scheduleSlotIndex` INTEGER NOT NULL,
                `plannedAtEpochMillis` INTEGER NOT NULL,
                `availableUntilEpochMillis` INTEGER NOT NULL,
                `openedAtEpochMillis` INTEGER,
                `completedAtEpochMillis` INTEGER,
                `deferredUntilEpochMillis` INTEGER,
                `status` TEXT NOT NULL,
                `zoneId` TEXT NOT NULL,
                FOREIGN KEY(`questionId`) REFERENCES `questions`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(`scheduleSlotIndex`) REFERENCES `schedule_slots`(`slotIndex`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_question_occurrences_questionId` ON `question_occurrences` (`questionId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_question_occurrences_scheduleSlotIndex` ON `question_occurrences` (`scheduleSlotIndex`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_question_occurrences_status` ON `question_occurrences` (`status`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_question_occurrences_plannedAtEpochMillis` ON `question_occurrences` (`plannedAtEpochMillis`)")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_question_occurrences_cycleNumber_cyclePosition` ON `question_occurrences` (`cycleNumber`, `cyclePosition`)",
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `answers` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `occurrenceId` INTEGER NOT NULL,
                `text` TEXT NOT NULL,
                `createdAtEpochMillis` INTEGER NOT NULL,
                FOREIGN KEY(`occurrenceId`) REFERENCES `question_occurrences`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_answers_createdAtEpochMillis` ON `answers` (`createdAtEpochMillis`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_answers_occurrenceId` ON `answers` (`occurrenceId`)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `practice_state` (
                `id` INTEGER NOT NULL,
                `isPracticeStarted` INTEGER NOT NULL,
                `isPaused` INTEGER NOT NULL,
                `practiceStartedAtEpochMillis` INTEGER,
                `currentCycleNumber` INTEGER NOT NULL,
                `nextCyclePosition` INTEGER NOT NULL,
                `lastProcessedAtEpochMillis` INTEGER,
                `pausedAtEpochMillis` INTEGER,
                `activeZoneId` TEXT NOT NULL,
                `seedVersion` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
    }
}
