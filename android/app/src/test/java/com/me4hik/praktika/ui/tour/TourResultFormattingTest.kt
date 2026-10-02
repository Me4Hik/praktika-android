package com.me4hik.praktika.ui.tour

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TourResultFormattingTest {

    @Test
    fun exitStepNumber_usesDefinitionIndex_notEnumOrdinalAlone() {
        val definition = TourDefinition(
            id = "custom",
            steps = listOf(
                TourStep(
                    id = TourStepId.CHOOSE_DEFER,
                    type = TourStepType.USER_CHOICE,
                    tipResId = android.R.string.ok,
                    shortTitleResId = android.R.string.ok,
                    completion = TourCompletion.TargetActivation,
                ),
                TourStep(
                    id = TourStepId.FINISHED,
                    type = TourStepType.SHOW_ONLY,
                    tipResId = android.R.string.ok,
                    shortTitleResId = android.R.string.ok,
                    completion = TourCompletion.ManualAdvance,
                ),
            ),
        )
        assertEquals(1, exitStepNumberInDefinition("CHOOSE_DEFER", definition))
        assertEquals(2, exitStepNumberInDefinition("FINISHED", definition))
        assertEquals(0, exitStepNumberInDefinition("CLICK_ARCHIVE", definition))
        assertEquals(0, exitStepNumberInDefinition(null, definition))
    }

    @Test
    fun coreTour_exitNumbersMatchStepOrder() {
        val def = ExperimentalCoreTourV1.definition
        assertEquals(1, exitStepNumberInDefinition("START_INTRO", def))
        assertEquals(2, exitStepNumberInDefinition("CLICK_ARCHIVE", def))
        assertEquals(8, exitStepNumberInDefinition("CLICK_NOTIFICATIONS", def))
        assertEquals(10, exitStepNumberInDefinition("CHOOSE_SOUND", def))
        assertEquals(12, exitStepNumberInDefinition("PHASE_GATE", def))
        assertEquals(16, exitStepNumberInDefinition("OVERVIEW_PAUSE_BACKUP", def))
        assertEquals(17, exitStepNumberInDefinition("TOUR_COMPLETION", def))
        assertEquals(0, exitStepNumberInDefinition("OVERVIEW_NOTIFICATIONS", def))
    }

    @Test
    fun exitSummary_includesStepTitleFromDefinition() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val def = ExperimentalCoreTourV1.definition
        val result = TourRunResult(
            runId = "r1",
            startedAtEpochMs = 1L,
            endedAtEpochMs = 2L,
            completedStepIds = emptyList(),
            skippedStepIds = listOf("START_INTRO"),
            exitStepId = "CHOOSE_SOUND",
            completedTour = false,
        )
        val summary = formatTourResultSummary(context, result, def)
        assertTrue(summary.contains("10"))
        assertTrue(summary.contains(context.getString(com.me4hik.praktika.R.string.tour_title_task_sound)))
        assertTrue(summary.contains("пропущено: 1"))
    }
}
