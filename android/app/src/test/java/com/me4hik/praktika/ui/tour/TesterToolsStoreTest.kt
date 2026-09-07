package com.me4hik.praktika.ui.tour

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TesterToolsStoreTest {
    @Test
    fun unlock_and_tourResult_persist() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = DataStoreTesterToolsStore(context)
        assertFalse(store.unlocked.first())
        store.setUnlocked(true)
        assertTrue(store.unlocked.first())

        val result = TourRunResult(
            runId = "r1",
            startedAtEpochMs = 10L,
            endedAtEpochMs = 20L,
            completedStepIds = listOf("START_INTRO"),
            skippedStepIds = listOf("CLICK_ARCHIVE"),
            exitStepId = "CHOOSE_WORDING",
            completedTour = false,
        )
        store.saveTourResult(result)
        val loaded = store.lastTourResult.first()!!
        assertEquals("r1", loaded.runId)
        assertEquals(listOf("CLICK_ARCHIVE"), loaded.skippedStepIds)
        assertEquals("CHOOSE_WORDING", loaded.exitStepId)
        assertFalse(loaded.completedTour)
    }

    @Test
    fun serialize_roundTrip() {
        val result = TourRunResult(
            runId = "x",
            startedAtEpochMs = 1L,
            endedAtEpochMs = 2L,
            completedStepIds = listOf("A", "B"),
            skippedStepIds = emptyList(),
            exitStepId = null,
            completedTour = true,
        )
        val json = DataStoreTesterToolsStore.serializeResult(result)
        val parsed = DataStoreTesterToolsStore.parseResult(json)!!
        assertEquals(result, parsed)
    }
}
