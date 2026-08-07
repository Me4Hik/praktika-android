// 04.08.2026 Seed Data cursor by Me4Hik START - миграция Room 1→2 questionTextSnapshot
package com.me4hik.praktika.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

private class MigrationSnapshotRestoreException(message: String) : IllegalStateException(message)

val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            ALTER TABLE question_occurrences
            ADD COLUMN questionTextSnapshot TEXT NOT NULL DEFAULT ''
            """.trimIndent(),
        )
        db.execSQL(
            """
            UPDATE question_occurrences
            SET questionTextSnapshot = (
                SELECT questions.text
                FROM questions
                WHERE questions.id = question_occurrences.questionId
            )
            WHERE questionTextSnapshot = ''
            """.trimIndent(),
        )

        db.query("SELECT COUNT(*) FROM question_occurrences WHERE questionTextSnapshot = ''").use { cursor ->
            if (cursor.moveToFirst() && cursor.getInt(0) > 0) {
                throw MigrationSnapshotRestoreException(
                    "Migration 1→2 failed: ${cursor.getInt(0)} occurrence(s) have empty questionTextSnapshot",
                )
            }
        }
    }
}
// 04.08.2026 Seed Data cursor by Me4Hik END
