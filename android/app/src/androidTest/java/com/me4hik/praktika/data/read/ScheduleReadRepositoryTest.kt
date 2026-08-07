// 06.08.2026 Settings Schedule cursor by Me4Hik START - instrumented tests ScheduleReadRepository
package com.me4hik.praktika.data.read

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.me4hik.praktika.data.cycle.CycleCorruptionException
import com.me4hik.praktika.data.local.PraktikaDatabase
import com.me4hik.praktika.data.local.entity.ScheduleSlotEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScheduleReadRepositoryTest {
    private lateinit var database: PraktikaDatabase
    private lateinit var repository: RoomScheduleReadRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, PraktikaDatabase::class.java).build()
        repository = RoomScheduleReadRepository(database)
    }

    @After
    fun tearDown() {
        if (::database.isInitialized && database.isOpen) {
            database.close()
        }
    }

    @Test
    fun initialThreeSlotsOrderedByTime() = runBlocking {
        insertDefaultSlots()
        val snapshot = repository.observeSchedule().first()
        assertEquals(listOf(660, 900, 1140), snapshot.slots.map { it.timeOfDayMinutes })
        assertEquals(listOf(1, 2, 3), snapshot.slots.map { it.slotIndex })
    }

    @Test
    fun invalidCountThrowsCorruption() = runBlocking {
        database.scheduleSlotDao().insertAll(
            listOf(
                ScheduleSlotEntity(1, 660),
                ScheduleSlotEntity(2, 900),
            ),
        )
        assertCorruption {
            repository.observeSchedule().first()
        }
    }

    @Test
    fun missingSlotIndexThrowsCorruption() = runBlocking {
        database.scheduleSlotDao().insertAll(
            listOf(
                ScheduleSlotEntity(1, 660),
                ScheduleSlotEntity(2, 900),
                ScheduleSlotEntity(4, 1140),
            ),
        )
        assertCorruption {
            repository.observeSchedule().first()
        }
    }

    @Test
    fun duplicateTimeProtectedByUniqueIndex() = runBlocking {
        insertDefaultSlots()
        try {
            database.openHelper.writableDatabase.execSQL(
                "UPDATE schedule_slots SET timeOfDayMinutes = 660 WHERE slotIndex = 2",
            )
            fail("Expected SQLiteConstraintException")
        } catch (_: android.database.sqlite.SQLiteConstraintException) {
        }
    }

    @Test
    fun outOfRangeTimeThrowsCorruption() = runBlocking {
        insertDefaultSlots()
        database.openHelper.writableDatabase.execSQL(
            "UPDATE schedule_slots SET timeOfDayMinutes = 1500 WHERE slotIndex = 1",
        )
        assertCorruption {
            repository.observeSchedule().first()
        }
    }

    @Test
    fun snapshotModelsAreNotRoomEntities() = runBlocking {
        insertDefaultSlots()
        val snapshot = repository.observeSchedule().first()
        assertTrue(snapshot.slots.all { it is ScheduleSlotReadModel })
        assertNotEquals("ScheduleSlotEntity", snapshot.slots.first()::class.simpleName)
    }

    @Test
    fun roomInvalidationEmitsUpdatedSnapshot() = runBlocking {
        insertDefaultSlots()
        repository.observeSchedule().first()
        database.scheduleSlotDao().updateTime(1, 630)
        val updated = repository.observeSchedule().first {
            it.slots.first { slot -> slot.slotIndex == 1 }.timeOfDayMinutes == 630
        }
        assertEquals(630, updated.slots.first { it.slotIndex == 1 }.timeOfDayMinutes)
    }

    @Test
    fun duplicateSlotIndexProtectedByPrimaryKey() = runBlocking {
        insertDefaultSlots()
        try {
            database.scheduleSlotDao().insertAll(listOf(ScheduleSlotEntity(1, 500)))
            fail("Expected duplicate primary key failure")
        } catch (_: Exception) {
        }
    }

    private suspend fun assertCorruption(block: suspend () -> Unit) {
        try {
            block()
            fail("Expected CycleCorruptionException")
        } catch (_: CycleCorruptionException) {
        }
    }

    private suspend fun insertDefaultSlots() {
        database.scheduleSlotDao().insertAll(
            listOf(
                ScheduleSlotEntity(1, 660),
                ScheduleSlotEntity(2, 900),
                ScheduleSlotEntity(3, 1140),
            ),
        )
    }
}
// 06.08.2026 Settings Schedule cursor by Me4Hik END
