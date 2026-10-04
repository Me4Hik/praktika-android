package com.me4hik.praktika.measurement

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveOpenedTrackingTest {

    @Test
    fun firstCall_tracksOnce_andSetsFlag() {
        val handle = SavedStateHandle()
        var tracks = 0

        ArchiveOpenedTracking.trackOnce(handle) { tracks++ }

        assertEquals(1, tracks)
        assertTrue(handle.get<Boolean>(ArchiveOpenedTracking.SAVED_STATE_KEY) == true)
    }

    @Test
    fun secondCall_sameHandle_doesNotTrack() {
        val handle = SavedStateHandle()
        var tracks = 0

        ArchiveOpenedTracking.trackOnce(handle) { tracks++ }
        ArchiveOpenedTracking.trackOnce(handle) { tracks++ }

        assertEquals(1, tracks)
    }

    @Test
    fun newHandle_tracksAgain() {
        var tracks = 0

        ArchiveOpenedTracking.trackOnce(SavedStateHandle()) { tracks++ }
        ArchiveOpenedTracking.trackOnce(SavedStateHandle()) { tracks++ }

        assertEquals(2, tracks)
    }
}
