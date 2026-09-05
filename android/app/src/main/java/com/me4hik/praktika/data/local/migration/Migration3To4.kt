// PROMPT 110 — Room 3→4 defer_events table
package com.me4hik.praktika.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_3_4: Migration = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `defer_events` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `occurrenceId` INTEGER NOT NULL,
                `questionId` INTEGER NOT NULL,
                `occurredAtEpochMillis` INTEGER NOT NULL,
                `deferredUntilEpochMillis` INTEGER NOT NULL,
                `durationMinutes` INTEGER NOT NULL,
                `zoneId` TEXT NOT NULL,
                FOREIGN KEY(`occurrenceId`) REFERENCES `question_occurrences`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_defer_events_occurrenceId` ON `defer_events` (`occurrenceId`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_defer_events_questionId` ON `defer_events` (`questionId`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_defer_events_occurredAtEpochMillis` ON `defer_events` (`occurredAtEpochMillis`)",
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_defer_events_questionId_occurredAtEpochMillis`
            ON `defer_events` (`questionId`, `occurredAtEpochMillis`)
            """.trimIndent(),
        )
    }
}
