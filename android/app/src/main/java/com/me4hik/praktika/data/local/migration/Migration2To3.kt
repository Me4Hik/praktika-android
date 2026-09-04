// QUESTION_DEFER_FINAL_V1 — Room 2→3 deferredUntilEpochMillis
package com.me4hik.praktika.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            ALTER TABLE question_occurrences
            ADD COLUMN deferredUntilEpochMillis INTEGER DEFAULT NULL
            """.trimIndent(),
        )
    }
}
