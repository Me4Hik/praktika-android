// 04.08.2026 Seed Data cursor by Me4Hik START - тест миграции Room 1→2
package com.me4hik.praktika.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.me4hik.praktika.data.local.migration.MIGRATION_1_2
import com.me4hik.praktika.data.local.migration.MIGRATION_2_3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class PraktikaMigrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        instrumentation,
        PraktikaDatabase::class.java,
    )

    @Test
    @Throws(IOException::class)
    fun migrate1To2_restoresQuestionTextSnapshot() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                "INSERT INTO questions (id, cyclePosition, text, isActive) VALUES (1, 1, 'Snapshot source', 1)",
            )
            execSQL("INSERT INTO schedule_slots (slotIndex, timeOfDayMinutes) VALUES (1, 660)")
            execSQL(
                """
                INSERT INTO question_occurrences (
                    questionId,
                    cycleNumber,
                    cyclePosition,
                    scheduleSlotIndex,
                    plannedAtEpochMillis,
                    availableUntilEpochMillis,
                    status,
                    zoneId
                ) VALUES (1, 1, 1, 1, 1000, 2000, 'SCHEDULED', 'UTC')
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2).apply {
            query("SELECT questionTextSnapshot, questionId, status FROM question_occurrences WHERE id = 1")
                .use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("Snapshot source", cursor.getString(0))
                    assertEquals(1, cursor.getInt(1))
                    assertEquals("SCHEDULED", cursor.getString(2))
                }
            close()
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate2To3_addsNullableDeferredUntilWithoutDataLoss() {
        helper.createDatabase(TEST_DB, 2).apply {
            execSQL(
                "INSERT INTO questions (id, cyclePosition, text, isActive) VALUES (1, 1, 'Keep me', 1)",
            )
            execSQL("INSERT INTO schedule_slots (slotIndex, timeOfDayMinutes) VALUES (1, 660)")
            execSQL(
                """
                INSERT INTO question_occurrences (
                    questionId,
                    questionTextSnapshot,
                    cycleNumber,
                    cyclePosition,
                    scheduleSlotIndex,
                    plannedAtEpochMillis,
                    availableUntilEpochMillis,
                    openedAtEpochMillis,
                    completedAtEpochMillis,
                    status,
                    zoneId
                ) VALUES (1, 'Keep me', 1, 1, 1, 1000, 2000, 1500, NULL, 'AVAILABLE', 'UTC')
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_2_3).apply {
            query(
                """
                SELECT questionTextSnapshot, status, openedAtEpochMillis, deferredUntilEpochMillis
                FROM question_occurrences WHERE id = 1
                """.trimIndent(),
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Keep me", cursor.getString(0))
                assertEquals("AVAILABLE", cursor.getString(1))
                assertEquals(1500L, cursor.getLong(2))
                assertTrue(cursor.isNull(3))
            }
            close()
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrateFromVersion1RequiresExplicitMigration() {
        helper.createDatabase(TEST_DB, 1).close()

        val context = instrumentation.targetContext
        val databasePath = context.getDatabasePath(TEST_DB).absolutePath

        val migratedDatabase = Room.databaseBuilder(
            context,
            PraktikaDatabase::class.java,
            databasePath,
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()

        migratedDatabase.openHelper.writableDatabase.use { db ->
            assertEquals(3, db.version)
        }
        migratedDatabase.close()
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
// 04.08.2026 Seed Data cursor by Me4Hik END
