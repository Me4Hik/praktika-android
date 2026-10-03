// 04.08.2026 DB Refactoring cursor by Me4Hik START - класс Room Database
// 04.08.2026 Seed Data cursor by Me4Hik START - версия 2 и MIGRATION_1_2
package com.me4hik.praktika.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.me4hik.praktika.data.local.converter.RoomConverters
import com.me4hik.praktika.data.local.dao.AnswerDao
import com.me4hik.praktika.data.local.dao.DeferEventDao
import com.me4hik.praktika.data.local.dao.MoodCheckInDao
import com.me4hik.praktika.data.local.dao.PracticeStateDao
import com.me4hik.praktika.data.local.dao.QuestionDao
import com.me4hik.praktika.data.local.dao.QuestionOccurrenceDao
import com.me4hik.praktika.data.local.dao.ScheduleSlotDao
import com.me4hik.praktika.data.local.entity.AnswerEntity
import com.me4hik.praktika.data.local.entity.DeferEventEntity
import com.me4hik.praktika.data.local.entity.MoodCheckInEntity
import com.me4hik.praktika.data.local.entity.PracticeStateEntity
import com.me4hik.praktika.data.local.entity.QuestionEntity
import com.me4hik.praktika.data.local.entity.QuestionOccurrenceEntity
import com.me4hik.praktika.data.local.entity.ScheduleSlotEntity
import com.me4hik.praktika.data.local.migration.MIGRATION_1_2
import com.me4hik.praktika.data.local.migration.MIGRATION_2_3
import com.me4hik.praktika.data.local.migration.MIGRATION_3_4
import com.me4hik.praktika.data.local.migration.MIGRATION_4_5

@Database(
    entities = [
        QuestionEntity::class,
        ScheduleSlotEntity::class,
        QuestionOccurrenceEntity::class,
        AnswerEntity::class,
        PracticeStateEntity::class,
        DeferEventEntity::class,
        MoodCheckInEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
@TypeConverters(RoomConverters::class)
abstract class PraktikaDatabase : RoomDatabase() {
    abstract fun questionDao(): QuestionDao

    abstract fun scheduleSlotDao(): ScheduleSlotDao

    abstract fun questionOccurrenceDao(): QuestionOccurrenceDao

    abstract fun answerDao(): AnswerDao

    abstract fun practiceStateDao(): PracticeStateDao

    abstract fun deferEventDao(): DeferEventDao

    abstract fun moodCheckInDao(): MoodCheckInDao

    companion object {
        const val DATABASE_NAME = "praktika.db"

        @Volatile
        private var instance: PraktikaDatabase? = null

        @Volatile
        private var openedDatabaseName: String? = null

        fun getInstance(
            context: Context,
            databaseName: String = DATABASE_NAME,
        ): PraktikaDatabase {
            return instance ?: synchronized(this) {
                openedDatabaseName?.let { openedName ->
                    if (openedName != databaseName) {
                        throw IllegalStateException(
                            "Cannot open database '$databaseName': '$openedName' is already open in this process",
                        )
                    }
                }
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    PraktikaDatabase::class.java,
                    databaseName,
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                    .also {
                        instance = it
                        openedDatabaseName = databaseName
                    }
            }
        }

        internal fun resetInstanceForTests() {
            synchronized(this) {
                instance?.close()
                instance = null
                openedDatabaseName = null
            }
        }
    }
}
// 04.08.2026 Seed Data cursor by Me4Hik END
// 04.08.2026 DB Refactoring cursor by Me4Hik END
