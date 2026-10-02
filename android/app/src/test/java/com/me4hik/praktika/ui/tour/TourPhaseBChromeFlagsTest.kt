package com.me4hik.praktika.ui.tour

import androidx.test.core.app.ApplicationProvider
import com.me4hik.praktika.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase B + FINAL chrome flags and ManualAdvance cue contract.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TourPhaseBChromeFlagsTest {
    private val definition = ExperimentalCoreTourV1.definition
    private val byId = definition.steps.associateBy { it.id }

    @Test
    fun overviewB1toB4_showNext_notDone() {
        definition.overviewSteps.forEach { step ->
            assertEquals(TourCompletion.ManualAdvance, step.completion)
            assertEquals(TourUiPhase.OVERVIEW, step.uiPhase())
            assertTrue(step.showsManualAdvanceCue())
        }
        assertEquals(4, definition.overviewSteps.size)
        assertEquals(TourStepId.OVERVIEW_PAUSE_BACKUP, definition.overviewSteps.last().id)
        assertFalse(definition.steps.any { it.id == TourStepId.OVERVIEW_NOTIFICATIONS })
    }

    @Test
    fun b4_isNotTerminal_nextGoesToCompletion() {
        val b4Index = definition.steps.indexOfFirst { it.id == TourStepId.OVERVIEW_PAUSE_BACKUP }
        val next = definition.steps[b4Index + 1]
        assertEquals(TourStepId.TOUR_COMPLETION, next.id)
        assertEquals(TourUiPhase.COMPLETION, next.uiPhase())
    }

    @Test
    fun finalCompletion_progressHidden_onlyDoneCue_noSkip() {
        val final = byId.getValue(TourStepId.TOUR_COMPLETION)
        assertEquals(TourCompletion.ManualAdvance, final.completion)
        assertFalse(final.showsChromeSkip())
        assertTrue(final.showsManualAdvanceCue())
        val session = TourSessionState(
            status = TourStatus.Active,
            definitionId = definition.id,
            steps = definition.steps,
            stepIndex = definition.steps.indexOfFirst { it.id == TourStepId.TOUR_COMPLETION },
            actionReady = true,
        )
        assertEquals(TourProgressDisplay.Hidden, session.progressDisplay)
    }

    @Test
    fun finalExactCopy() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertEquals(
            "Готово. Вы познакомились с основными возможностями приложения. Удачной практики!",
            ctx.getString(R.string.tour_completion_farewell),
        )
        assertEquals(
            R.string.tour_completion_farewell,
            byId.getValue(TourStepId.TOUR_COMPLETION).tipResId,
        )
    }

    @Test
    fun task1Info_andOverviews_haveManualAdvanceCue_introDoesNot() {
        assertTrue(byId.getValue(TourStepId.ARCHIVE_DAYS_CONTENT).showsManualAdvanceCue())
        assertFalse(byId.getValue(TourStepId.START_INTRO).showsManualAdvanceCue())
        assertFalse(byId.getValue(TourStepId.PHASE_GATE).showsManualAdvanceCue())
        assertFalse(byId.getValue(TourStepId.CLICK_ARCHIVE).showsManualAdvanceCue())
        assertFalse(byId.getValue(TourStepId.SCHEDULE_INFO).showsManualAdvanceCue())
    }

    @Test
    fun task1Info_showOnly_hidesSkip_keepsManualAdvance() {
        val step = byId.getValue(TourStepId.ARCHIVE_DAYS_CONTENT)
        assertFalse(step.showsChromeSkip())
        assertEquals(TourCompletion.ManualAdvance, step.completion)
        assertEquals(TourUiPhase.ACTION, step.uiPhase())
    }

    @Test
    fun task1ActionSteps_stillShowSkip() {
        assertTrue(byId.getValue(TourStepId.CLICK_ARCHIVE).showsChromeSkip())
        assertTrue(byId.getValue(TourStepId.CLICK_ARCHIVE_DAYS).showsChromeSkip())
        assertTrue(byId.getValue(TourStepId.START_INTRO).showsChromeSkip())
    }

    @Test
    fun overviewOrdinal_b4IsFourOfFour() {
        val session = TourSessionState(
            status = TourStatus.Active,
            definitionId = definition.id,
            steps = definition.steps,
            stepIndex = definition.steps.indexOfFirst { it.id == TourStepId.OVERVIEW_PAUSE_BACKUP },
        )
        assertEquals(TourProgressDisplay.Overview(4, 4), session.progressDisplay)
    }
}
