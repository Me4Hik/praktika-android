// 10.08.2026 Post-release fixes cursor by Me4Hik START - Data Vault Restore Core v1 test support
package com.me4hik.praktika.data.backup.restore

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.me4hik.praktika.data.local.PraktikaDatabase
import com.me4hik.praktika.data.seed.DatabaseSeeder
import com.me4hik.praktika.data.seed.SeedResult
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
abstract class RestoreRoomTestSupport {
    protected lateinit var database: PraktikaDatabase
    protected lateinit var restorer: RoomBackupRestorer
    protected lateinit var assetJson: String

    protected fun setUpDatabase() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, PraktikaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        assetJson = checkNotNull(RestoreRoomTestSupport::class.java.classLoader)
            .getResourceAsStream("questions.json")?.bufferedReader()?.use { it.readText() }
            ?: error("questions.json missing from test resources")
        val seedResult = DatabaseSeeder().seedFromJson(
            json = assetJson,
            database = database,
            activeZoneId = BackupRestoreFixtures.ZONE_MOSCOW,
        )
        check(seedResult == SeedResult.Inserted || seedResult == SeedResult.AlreadyInitialized) {
            "Unexpected seed result: $seedResult"
        }
        restorer = RoomBackupRestorer(database)
    }

    protected fun tearDownDatabase() {
        if (::database.isInitialized && database.isOpen) {
            database.close()
        }
    }

    protected fun scheduleMinutes(): List<Int> = runBlocking {
        database.scheduleSlotDao().getAllOrderedByTime().sortedBy { it.slotIndex }
            .map { it.timeOfDayMinutes }
    }
}

// 10.08.2026 Post-release fixes cursor by Me4Hik END
