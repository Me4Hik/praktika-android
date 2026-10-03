package com.me4hik.praktika.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_4_5: Migration = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `mood_checkins` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `occurrenceId` INTEGER NOT NULL,
                `questionId` INTEGER NOT NULL,
                `level` TEXT NOT NULL,
                `createdAtEpochMillis` INTEGER NOT NULL,
                `updatedAtEpochMillis` INTEGER NOT NULL,
                `zoneId` TEXT NOT NULL,
                FOREIGN KEY(`occurrenceId`) REFERENCES `question_occurrences`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_mood_checkins_occurrenceId` ON `mood_checkins` (`occurrenceId`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_mood_checkins_questionId` ON `mood_checkins` (`questionId`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_mood_checkins_updatedAtEpochMillis` ON `mood_checkins` (`updatedAtEpochMillis`)",
        )
    }
}
